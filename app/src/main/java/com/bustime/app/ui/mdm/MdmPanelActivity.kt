package com.bustime.app.ui.mdm

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.ui.common.StopListEditor
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Place
import com.bustime.app.models.Route
import com.bustime.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MdmPanelActivity : AppCompatActivity() {
    private val password by lazy { intent.getStringExtra(EXTRA_PASSWORD).orEmpty() }
    private lateinit var root: LinearLayout
    private val repo by lazy { AppRepository.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = 1; setPadding(24, 16, 24, 24) }
        showHome()
    }

    private fun showPage(title: String, subtitle: String = "") {
        root.removeAllViews()
        root.addView(TextView(this).apply {
            text = title; textSize = 24f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })
        if (subtitle.isNotBlank()) root.addView(TextView(this).apply {
            text = subtitle; textSize = 14f; setTextColor(getColor(com.bustime.app.R.color.textSecondary))
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(Button(this).apply { text = "← Back"; setOnClickListener { showHome() } },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        showRoot()
    }

    private fun showHome() {
        root.removeAllViews()
        root.addView(TextView(this).apply { text = "MDM Admin"; textSize = 24f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "Published changes sync to the app."; setPadding(0,8,0,16) })
        button("Route List") { showRoutes() }
        button("Bus Stop List") { showStops() }
        button("Add Route") { routeEditor(null) }
        button("Correction Route") { chooseRoute() }
        button("Add Bus Stop") { stopEditor(null) }
        button("Correction Bus Stop") { chooseStop() }
        button("Refresh App Data") { lifecycleScope.launch { runCatching { repo.syncData() }.onSuccess { toast("Data refreshed"); showHome() }.onFailure { toast(it.message ?: "Refresh failed") } } }
        button("Back") { finish() }
        showRoot()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // Reuse the page root safely when navigating between full-page MDM screens.
    // Without detaching it from the previous ScrollView Android throws:
    // "The specified child already has a parent."
    private fun showRoot() {
        (root.parent as? ViewGroup)?.removeView(root)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(-1, -2))
        }
        setContentView(scroll)
    }

    private fun button(label:String, action:()->Unit) { root.addView(Button(this).apply { text=label; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1,-2)) }
    private fun toast(s:String) = Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun norm(s:String)=s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    private fun slug(s:String)=s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"),"_").trim('_').take(65).ifBlank{"stop"}
    private fun box()=LinearLayout(this).apply { orientation=1; setPadding(16,8,16,8) }

    private fun showRoutes() {
        lifecycleScope.launch {
            runCatching {
                val routes = withContext(Dispatchers.IO) { repo.getAllRoutes() }
                val places = withContext(Dispatchers.IO) { repo.getAllPlaces() }.associateBy { it.id }
                val entries = routes.map { r ->
                    val from = places[r.fromId]?.displayName("en") ?: r.fromId
                    val to = places[r.toId]?.displayName("en") ?: r.toId
                    Triple(from + " → " + to, "Time: " + r.time + (r.serviceName?.takeIf { it.isNotBlank() }?.let { " · " + it } ?: ""), r.id)
                }
                showSearchList("Route List", "Search a route, then tap it to open the full stop sequence.", entries) { id ->
                    routes.firstOrNull { it.id == id }?.let { showRouteDetails(it, places) }
                }
            }.onFailure { toast(it.message ?: "Could not load routes") }
        }
    }

    private fun chooseRoute() = showRoutes()

    private fun showRouteDetails(route: Route, places: Map<String, Place>) {
        lifecycleScope.launch {
            val stops = withContext(Dispatchers.IO) { repo.getRouteStops(route.id) }.sortedBy { it.stopOrder }
            val lines = stops.mapIndexed { i, stop ->
                val place = places[stop.placeId]
                (i + 1).toString() + ". " + (place?.displayName("en") ?: stop.placeId) +
                    (stop.time?.takeIf { it.isNotBlank() }?.let { "  ·  " + it } ?: "")
            }
            showPage("Route details", places[route.fromId]?.displayName("en").orEmpty() + " → " +
                places[route.toId]?.displayName("en").orEmpty())
            root.addView(TextView(this@MdmPanelActivity).apply {
                text = "Service: " + (route.serviceName?.takeIf { it.isNotBlank() } ?: "—") +
                    "\nRoute time: " + route.time + "\n\n" +
                    lines.joinToString("\n").ifBlank { "No stop details found." }
                textSize = 15f; setPadding(0, 0, 0, dp(18))
            })
            button("Correct this route") { routeEditor(route) }
            showRoot()
        }
    }

    private fun showStops() {
        lifecycleScope.launch {
            runCatching {
                val places = withContext(Dispatchers.IO) { repo.getAllPlaces() }
                val entries = places.map { p ->
                    val extra = listOf(p.names["hi"], p.names["pa"]).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                    Triple(p.displayName("en"), (if (extra.isBlank()) "" else extra + "\n") + "ID: " + p.id, p.id)
                }
                showSearchList("Bus Stop List", "Search a stop, then tap it to inspect all names and correct it.", entries) { id ->
                    places.firstOrNull { it.id == id }?.let { showStopDetails(it) }
                }
            }.onFailure { toast(it.message ?: "Could not load bus stops") }
        }
    }

    private fun chooseStop() = showStops()

    private fun showStopDetails(stop: Place) {
        showPage(stop.displayName("en"), "Bus stop details")
        root.addView(TextView(this).apply {
            text = "English: " + stop.names["en"].orEmpty().ifBlank { "—" } +
                "\nHindi: " + stop.names["hi"].orEmpty().ifBlank { "—" } +
                "\nPunjabi: " + stop.names["pa"].orEmpty().ifBlank { "—" } +
                "\n\nPlace ID: " + stop.id
            textSize = 16f; setPadding(0, 0, 0, dp(18))
        })
        button("Correct this stop") { stopEditor(stop) }
        showRoot()
    }

    private fun showSearchList(
        title: String,
        hint: String,
        entries: List<Triple<String, String, String>>,
        onSelect: (String) -> Unit
    ) {
        showPage(title, hint)
        val search = EditText(this).apply { this.hint = "Search…"; setSingleLine(true); textSize = 15f }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        val count = TextView(this).apply { textSize = 12f; setTextColor(getColor(com.bustime.app.R.color.textSecondary)); setPadding(0, dp(6), 0, dp(8)) }
        root.addView(count)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        fun render(query: String) {
            list.removeAllViews()
            val q = norm(query)
            val filtered = entries.filter { norm(it.first + " " + it.second + " " + it.third).contains(q) }
            count.text = filtered.size.toString() + " item(s)"
            filtered.take(1000).forEach { item ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(getColor(com.bustime.app.R.color.surface)); cornerRadius = dp(9).toFloat()
                        setStroke(dp(1), 0xFFE0E6ED.toInt())
                    }
                    isClickable = true; isFocusable = true; setOnClickListener { onSelect(item.third) }
                }
                row.addView(TextView(this).apply { text = item.first; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(getColor(com.bustime.app.R.color.textPrimary)) })
                row.addView(TextView(this).apply { text = item.second; textSize = 13f; setTextColor(getColor(com.bustime.app.R.color.textSecondary)); setPadding(0, dp(4), 0, 0) })
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
            if (filtered.isEmpty()) list.addView(TextView(this).apply { text = "No matches"; setPadding(dp(8), dp(16), dp(8), dp(16)) })
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        render("")
        showRoot()
    }

    private fun routeEditor(old: Route?) {
        lifecycleScope.launch {
            val places = withContext(Dispatchers.IO) { repo.getAllPlaces() }
            val byId = places.associateBy { it.id }
            val oldStops = if (old == null) emptyList() else withContext(Dispatchers.IO) { repo.getRouteStops(old.id) }.sortedBy { it.stopOrder }
            val drafts = mutableListOf<JSONObject>()
            if (oldStops.isNotEmpty()) oldStops.forEach { s ->
                drafts.add(JSONObject().put("place_id", s.placeId)
                    .put("name", byId[s.placeId]?.displayName("en") ?: s.placeId)
                    .put("language", "en").put("time", s.time ?: JSONObject.NULL))
            } else { drafts.add(newDraft()); drafts.add(newDraft()) }

            showPage(if (old == null) "Add Route" else "Correction Route",
                "Edit the full route below. Preview and Publish are on this page.")
            root.addView(TextView(this@MdmPanelActivity).apply {
                text = "Numbered stop editor: set times, + to add a stop, and ⋮ to move or remove."
                textSize = 13f; setTextColor(getColor(com.bustime.app.R.color.textSecondary)); setPadding(0, 0, 0, dp(10))
            })
            val stopsBox = LinearLayout(this@MdmPanelActivity).apply { orientation = LinearLayout.VERTICAL }
            root.addView(stopsBox)
            val service = EditText(this@MdmPanelActivity).apply {
                hint = "Service / bus name (optional)"; setSingleLine(true); setText(old?.serviceName.orEmpty())
            }
            root.addView(service, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
            StopListEditor(this@MdmPanelActivity, stopsBox, places, drafts, minStops = 2).render()
            val preview = TextView(this@MdmPanelActivity).apply {
                textSize = 14f; setPadding(0, dp(12), 0, dp(12)); visibility = android.view.View.GONE
            }
            root.addView(preview)
            button("Preview") {
                val problem = validateRouteDrafts(drafts)
                if (problem != null) { toast(problem); return@button }
                preview.text = (if (old == null) "New route preview" else "Route correction preview") +
                    (if (service.text.toString().trim().isBlank()) "" else "\nService: " + service.text.toString().trim()) +
                    "\nStops: " + drafts.size + "\n\n" + drafts.mapIndexed { i, d ->
                        (if (i == 0) "START" else if (i == drafts.lastIndex) "DESTINATION" else (i + 1).toString()) +
                            "   " + d.optString("name").trim() + (draftTime(d)?.let { "   ·   " + it } ?: "")
                    }.joinToString("\n")
                preview.visibility = android.view.View.VISIBLE
            }
            button("Publish") {
                val problem = validateRouteDrafts(drafts)
                if (problem != null) { toast(problem); return@button }
                publishRoute(old, drafts, service.text.toString().trim(), places)
            }
            showRoot()
        }
    }

    private fun newDraft() = JSONObject().put("place_id", JSONObject.NULL).put("name", "")
        .put("language", "en").put("time", JSONObject.NULL)

    private fun draftTime(d: JSONObject): String? =
        d.optString("time").takeIf { !d.isNull("time") && it.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) }

    private fun validateRouteDrafts(drafts: List<JSONObject>): String? {
        if (drafts.size < 2) return "A route needs a start and destination."
        if (drafts.any { it.optString("name").trim().isBlank() }) return "Enter a name for every stop."
        if (drafts.none { draftTime(it) != null }) return "Set at least one time anywhere in the route."
        return null
    }

    private fun publishRoute(old: Route?, drafts: List<JSONObject>, service: String, places: List<Place>) {
        lifecycleScope.launch {
            runCatching {
                val known = mutableMapOf<String, Place>()
                places.forEach { p -> p.names.values.forEach { known[norm(it)] = p } }
                val ids = mutableListOf<String>()
                val changes = JSONArray()
                val newlyCreated = mutableSetOf<String>()
                drafts.forEach { d ->
                    val label = d.optString("name").trim()
                    val lang = d.optString("language", "en").takeIf { it == "en" || it == "hi" || it == "pa" } ?: "en"
                    val found = if (!d.isNull("place_id")) places.firstOrNull { it.id == d.optString("place_id") } else known[norm(label)]
                    if (found != null) ids.add(found.id) else {
                        val key = norm(label)
                        val id = "mdm_" + slug(label).ifBlank { "place" } + "_" + Integer.toHexString(key.hashCode()).takeLast(6)
                        ids.add(id)
                        if (newlyCreated.add(id)) {
                            val payload = JSONObject().put("id", id).put("kind", "place").put("name", JSONObject().put(lang, label))
                            changes.put(change("place", id, payload))
                            known[key] = Place(id, "place", mapOf(lang to label), null, null, label)
                        }
                    }
                }
                if (ids.first() == ids.last()) error("Start and destination must be different places.")
                val startTime = draftTime(drafts.first())
                val arriveTime = draftTime(drafts.last())
                val anyTime = drafts.mapNotNull { draftTime(it) }.firstOrNull() ?: error("Set at least one valid HH:mm time.")
                val routeId = old?.id ?: "mdm_route_" + System.currentTimeMillis().toString(36)
                val route = JSONObject().put("id", routeId).put("from", ids.first()).put("to", ids.last())
                    .put("time", startTime ?: JSONObject.NULL).put("arrive", arriveTime ?: JSONObject.NULL)
                    .put("service", service.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                    .put("routeName", old?.routeName ?: JSONObject.NULL).put("contact", old?.contact ?: JSONObject.NULL)
                    .put("info", old?.info ?: JSONObject.NULL).put("longRoute", drafts.size > 8)
                    .put("startDate", old?.startDate ?: JSONObject.NULL).put("hidden", false)
                    .put("stops", JSONArray().also { arr ->
                        drafts.subList(1, drafts.lastIndex).forEachIndexed { index, d ->
                            arr.put(JSONObject().put("place", ids[index + 1]).put("time", draftTime(d) ?: JSONObject.NULL))
                        }
                    })
                changes.put(change("route", routeId, route))
                publish(changes)
            }.onFailure { toast(it.message ?: "Route publish failed") }
        }
    }

    private fun stopEditor(old: Place?) {
        showPage(if (old == null) "Add Bus Stop" else "Correction Bus Stop",
            "Enter the names you want to save. Existing translations are preserved when left blank.")
        val en = EditText(this).apply { hint = "English name"; setSingleLine(true); setText(old?.names?.get("en").orEmpty()) }
        val hi = EditText(this).apply { hint = "Hindi name"; setSingleLine(true); setText(old?.names?.get("hi").orEmpty()) }
        val pa = EditText(this).apply { hint = "Punjabi name"; setSingleLine(true); setText(old?.names?.get("pa").orEmpty()) }
        root.addView(en); root.addView(hi); root.addView(pa)
        val preview = TextView(this).apply { textSize = 15f; setPadding(0, dp(12), 0, dp(12)); visibility = android.view.View.GONE }
        root.addView(preview)
        fun makeNames() = JSONObject().apply {
            if (en.text.isNotBlank()) put("en", en.text.toString().trim())
            if (hi.text.isNotBlank()) put("hi", hi.text.toString().trim())
            if (pa.text.isNotBlank()) put("pa", pa.text.toString().trim())
        }
        button("Preview") {
            val n = makeNames()
            if (n.length() == 0) toast("Enter at least one name.")
            else {
                preview.text = "English: " + n.optString("en", "—") + "\nHindi: " + n.optString("hi", "—") + "\nPunjabi: " + n.optString("pa", "—")
                preview.visibility = android.view.View.VISIBLE
            }
        }
        button("Publish") {
            val names = makeNames()
            if (names.length() == 0) { toast("Enter at least one name."); return@button }
            val label = names.optString("en").ifBlank { names.optString("hi").ifBlank { names.optString("pa") } }
            val id = old?.id ?: ("mdm_" + slug(label).ifBlank { "place" } + "_" + Integer.toHexString(norm(label).hashCode()).takeLast(6))
            val mergedNames = JSONObject()
            old?.names?.forEach { (language, value) -> if (value.isNotBlank()) mergedNames.put(language, value) }
            listOf("en", "hi", "pa").forEach { language -> if (names.has(language)) mergedNames.put(language, names.optString(language).trim()) }
            val payload = JSONObject().put("id", id).put("kind", old?.kind ?: "stand").put("name", mergedNames)
            publish(JSONArray().put(change("place", id, payload)))
        }
        showRoot()
    }

    private fun change(type:String,id:String,payload:JSONObject)=JSONObject().put("entity_type",type).put("entity_id",id).put("operation","upsert").put("payload",payload)
    private fun publish(changes:JSONArray) { lifecycleScope.launch {
        runCatching { withContext(Dispatchers.IO) {
            val conn=(URL(Constants.SUPABASE_URL.trimEnd('/')+"/functions/v1/mdm-publish").openConnection() as HttpURLConnection).apply {
                requestMethod="POST";connectTimeout=20000;readTimeout=20000;doOutput=true
                setRequestProperty("apikey",Constants.SUPABASE_ANON_KEY);setRequestProperty("Content-Type","application/json")
            }
            val body=JSONObject().put("action","publish").put("password",password).put("changes",changes)
            conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val code=conn.responseCode;val raw=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty();conn.disconnect()
            val json=JSONObject(raw.ifBlank{"{}"});if(code !in 200..299||!json.optBoolean("ok"))error(json.optString("error","Publish failed"))
            json.optLong("revision")
        }}.onSuccess { rev->runCatching{repo.syncData()};toast("Published successfully · revision $rev");recreate() }.onFailure{toast(it.message?:"Publish failed")}
    } }
    companion object { const val EXTRA_PASSWORD="mdm_password" }
}
