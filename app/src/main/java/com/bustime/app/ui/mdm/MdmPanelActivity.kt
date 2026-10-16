package com.bustime.app.ui.mdm

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
        root = LinearLayout(this).apply { orientation = 1; setPadding(24,24,24,24) }
        root.addView(TextView(this).apply { text = "MDM Admin"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Published changes sync to the app."; setPadding(0,8,0,16) })
        button("Route List") { showRoutes() }
        button("Bus Stop List") { showStops() }
        button("Add Route") { routeEditor(null) }
        button("Correction Route") { chooseRoute() }
        button("Add Bus Stop") { stopEditor(null) }
        button("Correction Bus Stop") { chooseStop() }
        button("Refresh App Data") { lifecycleScope.launch { runCatching { repo.syncData() }.onSuccess { toast("Data refreshed") }.onFailure { toast(it.message ?: "Refresh failed") } } }
        button("Back") { finish() }
        setContentView(ScrollView(this).apply { addView(root) })
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
                    val selected = routes.firstOrNull { it.id == id }
                    if (selected != null) showRouteDetails(selected, places)
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
            val from = places[route.fromId]?.displayName("en") ?: route.fromId
            val to = places[route.toId]?.displayName("en") ?: route.toId
            val message = from + " → " + to + "\n\nService: " +
                (route.serviceName?.takeIf { it.isNotBlank() } ?: "—") +
                "\nRoute time: " + route.time + "\n\n" +
                lines.joinToString("\n").ifBlank { "No stop details found." }
            AlertDialog.Builder(this@MdmPanelActivity).setTitle("Route details").setMessage(message)
                .setNegativeButton("Close", null)
                .setPositiveButton("Correct this route") { _, _ -> routeEditor(route) }.show()
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
                    val stop = places.firstOrNull { it.id == id }
                    if (stop != null) {
                        val msg = "English: " + (stop.names["en"].orEmpty().ifBlank { "—" }) +
                            "\nHindi: " + (stop.names["hi"].orEmpty().ifBlank { "—" }) +
                            "\nPunjabi: " + (stop.names["pa"].orEmpty().ifBlank { "—" }) +
                            "\n\nPlace ID: " + stop.id
                        AlertDialog.Builder(this@MdmPanelActivity).setTitle(stop.displayName("en")).setMessage(msg)
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Correct this stop") { _, _ -> stopEditor(stop) }.show()
                    }
                }
            }.onFailure { toast(it.message ?: "Could not load bus stops") }
        }
    }

    private fun chooseStop() = showStops()

    private fun showSearchList(
        title: String,
        hint: String,
        entries: List<Triple<String, String, String>>,
        onSelect: (String) -> Unit
    ) {
        val content = box()
        val search = EditText(this).apply { this.hint = "Search…"; setSingleLine(true); textSize = 15f }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(TextView(this).apply {
            text = hint; textSize = 12f; setTextColor(getColor(com.bustime.app.R.color.textSecondary))
            setPadding(0, dp(3), 0, dp(8))
        })
        val count = TextView(this).apply { textSize = 12f; setTextColor(getColor(com.bustime.app.R.color.textSecondary)) }
        content.addView(count)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, dp(420)))
        lateinit var dialog: AlertDialog
        fun render(query: String) {
            list.removeAllViews()
            val q = norm(query)
            val filtered = entries.filter { norm(it.first + " " + it.second + " " + it.third).contains(q) }
            count.text = filtered.size.toString() + " item(s)"
            filtered.take(500).forEach { item ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(11), dp(10), dp(11), dp(10))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(getColor(com.bustime.app.R.color.bgCard)); cornerRadius = dp(9).toFloat()
                        setStroke(dp(1), 0xFFE0E6ED.toInt())
                    }
                    isClickable = true; isFocusable = true
                    setOnClickListener { dialog.dismiss(); onSelect(item.third) }
                }
                row.addView(TextView(this).apply {
                    text = item.first; textSize = 15f
                    setTextColor(getColor(com.bustime.app.R.color.textPrimary))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                row.addView(TextView(this).apply {
                    text = item.second; textSize = 12f
                    setTextColor(getColor(com.bustime.app.R.color.textSecondary)); setPadding(0, dp(3), 0, 0)
                })
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
            if (filtered.isEmpty()) list.addView(TextView(this).apply { text = "No matches"; setPadding(dp(8), dp(16), dp(8), dp(16)) })
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        dialog = AlertDialog.Builder(this).setTitle(title).setView(content).setNegativeButton("Close", null).create()
        render("")
        dialog.show()
    }

    private fun routeEditor(old: Route?) {
        lifecycleScope.launch {
            val places = withContext(Dispatchers.IO) { repo.getAllPlaces() }
            val byId = places.associateBy { it.id }
            val oldStops = if (old == null) emptyList() else withContext(Dispatchers.IO) { repo.getRouteStops(old.id) }.sortedBy { it.stopOrder }
            val drafts = mutableListOf<JSONObject>()
            if (oldStops.isNotEmpty()) {
                oldStops.forEach { s ->
                    drafts.add(JSONObject().put("place_id", s.placeId)
                        .put("name", byId[s.placeId]?.displayName("en") ?: s.placeId)
                        .put("language", "en").put("time", s.time ?: JSONObject.NULL))
                }
            } else {
                drafts.add(newDraft()); drafts.add(newDraft())
            }
            val content = box()
            content.addView(TextView(this@MdmPanelActivity).apply {
                text = "Numbered stop editor: tap a time to set it, + to add a stop, and ⋮ to move or remove. Preview before publishing."
                textSize = 13f; setTextColor(getColor(com.bustime.app.R.color.textSecondary))
                setPadding(0, 0, 0, dp(10))
            })
            val stopsBox = LinearLayout(this@MdmPanelActivity).apply { orientation = LinearLayout.VERTICAL }
            content.addView(stopsBox)
            val service = EditText(this@MdmPanelActivity).apply {
                hint = "Service / bus name (optional)"; setSingleLine(true); setText(old?.serviceName.orEmpty())
            }
            content.addView(service, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
            StopListEditor(this@MdmPanelActivity, stopsBox, places, drafts, minStops = 2).render()
            val scroll = ScrollView(this@MdmPanelActivity).apply { addView(content) }
            val dialog = AlertDialog.Builder(this@MdmPanelActivity)
                .setTitle(if (old == null) "Add Route" else "Correction Route")
                .setView(scroll).setNegativeButton("Cancel", null).setNeutralButton("Preview", null)
                .setPositiveButton("Publish", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    val problem = validateRouteDrafts(drafts)
                    if (problem != null) { toast(problem); return@setOnClickListener }
                    showRoutePreview(drafts, service.text.toString().trim(), old)
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val problem = validateRouteDrafts(drafts)
                    if (problem != null) { toast(problem); return@setOnClickListener }
                    dialog.dismiss()
                    publishRoute(old, drafts, service.text.toString().trim(), places)
                }
            }
            dialog.show()
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

    private fun showRoutePreview(drafts: List<JSONObject>, service: String, old: Route?) {
        val lines = drafts.mapIndexed { i, d ->
            val label = if (i == 0) "START" else if (i == drafts.lastIndex) "DESTINATION" else (i + 1).toString()
            label + "   " + d.optString("name").trim() + (draftTime(d)?.let { "   ·   " + it } ?: "")
        }
        val message = (if (old == null) "New route preview" else "Route correction preview") +
            (if (service.isBlank()) "" else "\nService: " + service) +
            "\nStops: " + drafts.size + "\n\n" + lines.joinToString("\n")
        AlertDialog.Builder(this).setTitle("Preview before publish").setMessage(message)
            .setNegativeButton("Back to edit", null)
            .setPositiveButton("Looks good") { _, _ -> toast("Preview checked. Press Publish in the editor when ready.") }.show()
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
                    .put("time", startTime ?: anyTime).put("arrive", arriveTime ?: JSONObject.NULL)
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
        val content = box()
        content.addView(TextView(this).apply {
            text = "Enter names in English, Hindi and/or Punjabi. Preview the changes before publishing."
            textSize = 13f; setTextColor(getColor(com.bustime.app.R.color.textSecondary))
        })
        val en = EditText(this).apply { hint = "English name"; setSingleLine(true); setText(old?.names?.get("en").orEmpty()) }
        val hi = EditText(this).apply { hint = "Hindi name"; setSingleLine(true); setText(old?.names?.get("hi").orEmpty()) }
        val pa = EditText(this).apply { hint = "Punjabi name"; setSingleLine(true); setText(old?.names?.get("pa").orEmpty()) }
        content.addView(en); content.addView(hi); content.addView(pa)
        val dialog = AlertDialog.Builder(this).setTitle(if (old == null) "Add Bus Stop" else "Correction Bus Stop")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Cancel", null).setNeutralButton("Preview", null).setPositiveButton("Publish", null).create()
        dialog.setOnShowListener {
            fun makeNames(): JSONObject = JSONObject().apply {
                if (en.text.isNotBlank()) put("en", en.text.toString().trim())
                if (hi.text.isNotBlank()) put("hi", hi.text.toString().trim())
                if (pa.text.isNotBlank()) put("pa", pa.text.toString().trim())
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val n = makeNames()
                if (n.length() == 0) toast("Enter at least one name.")
                else AlertDialog.Builder(this).setTitle("Bus stop preview")
                    .setMessage("English: " + n.optString("en", "—") + "\nHindi: " + n.optString("hi", "—") + "\nPunjabi: " + n.optString("pa", "—"))
                    .setPositiveButton("Looks good", null).show()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val names = makeNames()
                if (names.length() == 0) { toast("Enter at least one name."); return@setOnClickListener }
                val label = names.optString("en").ifBlank { names.optString("hi").ifBlank { names.optString("pa") } }
                val id = old?.id ?: ("mdm_" + slug(label).ifBlank { "place" } + "_" + Integer.toHexString(norm(label).hashCode()).takeLast(6))
                val payload = JSONObject().put("id", id).put("kind", old?.kind ?: "place").put("name", names)
                dialog.dismiss()
                publish(JSONArray().put(change("place", id, payload)))
            }
        }
        dialog.show()
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
