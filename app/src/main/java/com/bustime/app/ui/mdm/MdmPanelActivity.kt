package com.bustime.app.ui.mdm

import com.bustime.app.utils.LanguageHelper

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
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

private fun uiText(en: String, hi: String, pa: String): String = when (LanguageHelper.current()) {
    "hi" -> hi
    "pa" -> pa
    else -> en
}

class MdmPanelActivity : AppCompatActivity() {
    private val password by lazy { intent.getStringExtra(EXTRA_PASSWORD).orEmpty() }
    private lateinit var root: LinearLayout
    private var pageBackAction: (() -> Unit)? = null
    private val repo by lazy { AppRepository.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = 1; setPadding(24, 16, 24, 24) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val action = pageBackAction
                if (action != null) action() else finish()
            }
        })
        showHome()
    }

    private fun showPage(title: String, subtitle: String = "") {
        pageBackAction = { showHome() }
        root.removeAllViews()
        root.addView(TextView(this).apply {
            text = title; textSize = 24f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })
        if (subtitle.isNotBlank()) root.addView(TextView(this).apply {
            text = subtitle; textSize = 14f; setTextColor(getColor(com.bustime.app.R.color.textSecondary))
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(Button(this).apply { text = uiText("← Back", "← वापस", "← ਵਾਪਸ"); setOnClickListener { val action = pageBackAction; if (action != null) action() else finish() } },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        showRoot()
    }

    private fun showHome() {
        pageBackAction = null
        root.removeAllViews()
        root.addView(TextView(this).apply { text = uiText("MDM Admin", "MDM एडमिन", "MDM ਐਡਮਿਨ"); textSize = 24f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        root.addView(TextView(this).apply { text = uiText("Published changes sync to the app.", "प्रकाशित बदलाव ऐप में सिंक होते हैं।", "ਪ੍ਰਕਾਸ਼ਿਤ ਤਬਦੀਲੀਆਂ ਐਪ ਵਿੱਚ ਸਿੰਕ ਹੁੰਦੀਆਂ ਹਨ।"); setPadding(0,8,0,16) })
        button(uiText("Route List", "रूट सूची", "ਰੂਟ ਸੂਚੀ")) { showRoutes() }
        button(uiText("Bus Stop List", "बस स्टॉप सूची", "ਬੱਸ ਸਟਾਪ ਸੂਚੀ")) { showStops() }
        button(uiText("Add Route", "रूट जोड़ें", "ਰੂਟ ਜੋੜੋ")) { routeEditor(null) }
        button(uiText("Add Bus Stop", "बस स्टॉप जोड़ें", "ਬੱਸ ਸਟਾਪ ਜੋੜੋ")) { stopEditor(null) }
        button(uiText("Refresh App Data", "ऐप डेटा रीफ़्रेश करें", "ਐਪ ਡਾਟਾ ਰਿਫ੍ਰੈਸ਼ ਕਰੋ")) { lifecycleScope.launch { runCatching { repo.syncData() }.onSuccess { toast(uiText("Data refreshed", "डेटा रीफ़्रेश हो गया", "ਡਾਟਾ ਰਿਫ੍ਰੈਸ਼ ਹੋ ਗਿਆ")); showHome() }.onFailure { toast(it.message ?: uiText("Refresh failed", "रीफ़्रेश विफल", "ਰਿਫ੍ਰੈਸ਼ ਅਸਫਲ")) } } }
        button(uiText("Back", "वापस", "ਵਾਪਸ")) { finish() }
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
                    Triple(from + " → " + to, uiText("Time: ", "समय: ", "ਸਮਾਂ: ") + r.time + (r.serviceName?.takeIf { it.isNotBlank() }?.let { " · " + it } ?: ""), r.id)
                }
                showSearchList(uiText("Route List", "रूट सूची", "ਰੂਟ ਸੂਚੀ"), uiText("Search a route, then tap it to open the full stop sequence.", "रूट खोजें और पूरा स्टॉप क्रम खोलने के लिए चुनें।", "ਰੂਟ ਖੋਜੋ ਅਤੇ ਸਟਾਪਾਂ ਦਾ ਪੂਰਾ ਕ੍ਰਮ ਖੋਲ੍ਹਣ ਲਈ ਚੁਣੋ।"), entries) { id ->
                    routes.firstOrNull { it.id == id }?.let { showRouteDetails(it, places) }
                }
            }.onFailure { toast(it.message ?: uiText("Could not load routes", "रूट लोड नहीं हो सके", "ਰੂਟ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕੇ")) }
        }
    }

    private fun chooseRoute() = showRoutes()

    private fun showRouteDetails(route: Route, places: Map<String, Place>) {
        lifecycleScope.launch {
            val stops = withContext(Dispatchers.IO) { repo.getRouteStops(route.id) }.sortedBy { it.stopOrder }
            val lines = stops.map { stop ->
                (stop.time to (places[stop.placeId]?.displayName("en") ?: stop.placeId))
            }
            pageBackAction = { showRoutes() }
            showPassengerStyleRoute(
                title = (places[route.fromId]?.displayName("en") ?: route.fromId) + " → " +
                    (places[route.toId]?.displayName("en") ?: route.toId),
                service = route.serviceName?.takeIf { it.isNotBlank() } ?: route.routeName.orEmpty(),
                info = route.info.orEmpty(),
                stops = lines,
                topActionLabel = uiText("Correct this route", "इस रूट में सुधार करें", "ਇਸ ਰੂਟ ਵਿੱਚ ਸੋਧ ਕਰੋ"),
                topAction = { routeEditor(route) { showRouteDetails(route, places) } },
                deleteAction = { confirmDeleteRoute(route) }
            )
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
                showSearchList(uiText("Bus Stop List", "बस स्टॉप सूची", "ਬੱਸ ਸਟਾਪ ਸੂਚੀ"), uiText("Search a stop, then tap it to inspect all names and correct it.", "स्टॉप खोजें, सभी नाम देखने और सुधार करने के लिए चुनें।", "ਸਟਾਪ ਖੋਜੋ, ਸਾਰੇ ਨਾਮ ਵੇਖਣ ਅਤੇ ਸੋਧ ਕਰਨ ਲਈ ਚੁਣੋ।"), entries) { id ->
                    places.firstOrNull { it.id == id }?.let { showStopDetails(it) }
                }
            }.onFailure { toast(it.message ?: uiText("Could not load bus stops", "बस स्टॉप लोड नहीं हो सके", "ਬੱਸ ਸਟਾਪ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕੇ")) }
        }
    }

    private fun chooseStop() = showStops()

    private fun showStopDetails(stop: Place) {
        showPage(stop.displayName("en"), uiText("Bus stop details", "बस स्टॉप विवरण", "ਬੱਸ ਸਟਾਪ ਵੇਰਵੇ"))
        pageBackAction = { showStops() }
        root.addView(TextView(this).apply {
            text = uiText("English: ", "अंग्रेज़ी: ", "ਅੰਗਰੇਜ਼ੀ: ") + stop.names["en"].orEmpty().ifBlank { "—" } +
                "\n" + uiText("Hindi: ", "हिंदी: ", "ਹਿੰਦੀ: ") + stop.names["hi"].orEmpty().ifBlank { "—" } +
                "\n" + uiText("Punjabi: ", "पंजाबी: ", "ਪੰਜਾਬੀ: ") + stop.names["pa"].orEmpty().ifBlank { "—" } +
                "\n\nPlace ID: " + stop.id
            textSize = 16f; setPadding(0, 0, 0, dp(18))
        })
        button(uiText("Correct this stop", "इस स्टॉप में सुधार करें", "ਇਸ ਸਟਾਪ ਵਿੱਚ ਸੋਧ ਕਰੋ")) { stopEditor(stop) }
        button(uiText("Delete this stop", "यह बस स्टॉप हटाएँ", "ਇਹ ਬੱਸ ਸਟਾਪ ਮਿਟਾਓ")) { confirmDeleteStop(stop) }
        showRoot()
    }

    private fun showSearchList(
        title: String,
        hint: String,
        entries: List<Triple<String, String, String>>,
        onSelect: (String) -> Unit
    ) {
        showPage(title, hint)
        val search = EditText(this).apply { this.hint = uiText("Search…", "खोजें…", "ਖੋਜੋ…"); setSingleLine(true); textSize = 15f }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        val count = TextView(this).apply { textSize = 12f; setTextColor(getColor(com.bustime.app.R.color.textSecondary)); setPadding(0, dp(6), 0, dp(8)) }
        root.addView(count)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        fun render(query: String) {
            list.removeAllViews()
            val q = norm(query)
            val filtered = entries.filter { norm(it.first + " " + it.second + " " + it.third).contains(q) }
            count.text = filtered.size.toString() + uiText(" item(s)", " आइटम", " ਆਈਟਮ")
            filtered.take(1000).forEach { item ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(getColor(com.bustime.app.R.color.surface)); cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), 0xFFE0E6ED.toInt())
                    }
                    isClickable = true; isFocusable = true; setOnClickListener { onSelect(item.third) }
                }
                val title = TextView(this).apply {
                    text = item.first; textSize = 15f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(getColor(com.bustime.app.R.color.textPrimary))
                }
                row.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
                val meta = TextView(this).apply {
                    text = item.second; textSize = 12f; maxLines = 3
                    gravity = Gravity.END
                    setTextColor(getColor(com.bustime.app.R.color.textSecondary))
                }
                row.addView(meta, LinearLayout.LayoutParams(dp(112), -2))
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
            if (filtered.isEmpty()) list.addView(TextView(this).apply { text = uiText("No matches", "कोई मेल नहीं मिला", "ਕੋਈ ਮੇਲ ਨਹੀਂ ਮਿਲਿਆ"); setPadding(dp(8), dp(16), dp(8), dp(16)) })
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        render("")
        showRoot()
    }

    private fun routeEditor(old: Route?, returnAction: (() -> Unit)? = null) {
        lifecycleScope.launch {
            val places = withContext(Dispatchers.IO) { repo.getAllPlaces() }
            val oldStops = if (old == null) emptyList() else withContext(Dispatchers.IO) { repo.getRouteStops(old.id) }.sortedBy { it.stopOrder }
            val drafts = mutableListOf<JSONObject>()
            if (oldStops.isNotEmpty()) oldStops.forEach { stop ->
                drafts.add(JSONObject().put("place_id", stop.placeId)
                    .put("name", places.firstOrNull { it.id == stop.placeId }?.displayName("en") ?: stop.placeId)
                    .put("language", "en").put("time", stop.time ?: JSONObject.NULL))
            } else { drafts.add(newDraft()); drafts.add(newDraft()) }
            renderRouteEditor(old, drafts, old?.serviceName.orEmpty(), places, returnAction)
        }
    }

    private fun renderRouteEditor(old: Route?, drafts: MutableList<JSONObject>, serviceValue: String, places: List<Place>, returnAction: (() -> Unit)? = null) {
        showPage(if (old == null) uiText("Add Route", "रूट जोड़ें", "ਰੂਟ ਜੋੜੋ") else uiText("Correction Route", "रूट सुधारें", "ਰੂਟ ਵਿੱਚ ਸੋਧ ਕਰੋ"),
            uiText("Add or correct stops, then open the full-page route preview before publishing.", "स्टॉप जोड़ें या सुधारें, फिर प्रकाशित करने से पहले पूरे पेज पर रूट का पूर्वावलोकन देखें।", "ਸਟਾਪ ਜੋੜੋ ਜਾਂ ਸੋਧੋ, ਫਿਰ ਪ੍ਰਕਾਸ਼ਿਤ ਕਰਨ ਤੋਂ ਪਹਿਲਾਂ ਪੂਰੇ ਪੰਨੇ ਉੱਤੇ ਰੂਟ ਦੀ ਝਲਕ ਵੇਖੋ।"))
        pageBackAction = returnAction ?: { showHome() }
        root.addView(TextView(this).apply {
            text = uiText("Add times where known. At least one stop must have a time.", "जहाँ समय पता हो वहाँ भरें। कम-से-कम एक स्टॉप का समय ज़रूरी है।", "ਜਿੱਥੇ ਸਮਾਂ ਪਤਾ ਹੋਵੇ ਉੱਥੇ ਭਰੋ। ਘੱਟੋ-ਘੱਟ ਇੱਕ ਸਟਾਪ ਦਾ ਸਮਾਂ ਲਾਜ਼ਮੀ ਹੈ।")
            textSize = 13f; setTextColor(getColor(com.bustime.app.R.color.textSecondary)); setPadding(0, 0, 0, dp(10))
        })
        val stopsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(stopsBox)
        val service = EditText(this).apply {
            hint = uiText("Service / bus name (optional)", "सेवा / बस का नाम (वैकल्पिक)", "ਸੇਵਾ / ਬੱਸ ਦਾ ਨਾਮ (ਵਿਕਲਪਿਕ)"); setSingleLine(true); setText(serviceValue)
        }
        root.addView(service, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        StopListEditor(this, stopsBox, places, drafts, minStops = 2).render()
        button(uiText("Preview route", "रूट का पूर्वावलोकन", "ਰੂਟ ਦੀ ਝਲਕ")) {
            val problem = validateRouteDrafts(drafts)
            if (problem != null) { toast(problem); return@button }
            showRoutePreview(old, drafts, service.text.toString().trim(), places, returnAction)
        }
        showRoot()
    }

    private fun showRoutePreview(
        old: Route?,
        drafts: MutableList<JSONObject>,
        service: String,
        places: List<Place>,
        returnAction: (() -> Unit)? = null
    ) {
        pageBackAction = { renderRouteEditor(old, drafts, service, places, returnAction) }
        val lines = drafts.map { draft ->
            (draftTime(draft) to draft.optString("name").trim())
        }
        showPassengerStyleRoute(
            title = (drafts.firstOrNull()?.optString("name").orEmpty().trim()) + " → " +
                drafts.lastOrNull()?.optString("name").orEmpty().trim(),
            service = service,
            info = old?.info.orEmpty(),
            stops = lines,
            topActionLabel = uiText("← Back to edit", "← संपादन पर वापस", "← ਸੋਧ ਵੱਲ ਵਾਪਸ"),
            topAction = { renderRouteEditor(old, drafts, service, places, returnAction) },
            publishAction = {
                val problem = validateRouteDrafts(drafts)
                if (problem != null) { toast(problem); return@showPassengerStyleRoute }
                publishRoute(old, drafts, service, places)
            }
        )
    }

    /**
     * Uses the exact passenger-facing route detail layout and stop-row layout for both
     * correction details and the Add/Correction Route preview.
     */
    private fun showPassengerStyleRoute(
        title: String,
        service: String,
        info: String,
        stops: List<Pair<String?, String>>,
        topActionLabel: String,
        topAction: () -> Unit,
        publishAction: (() -> Unit)? = null,
        deleteAction: (() -> Unit)? = null
    ) {
        root.removeAllViews()
        val page = layoutInflater.inflate(com.bustime.app.R.layout.activity_route_detail, null, false) as ScrollView
        val content = page.getChildAt(0) as LinearLayout

        page.findViewById<Button>(com.bustime.app.R.id.btnBackDetail).visibility = View.GONE
        page.findViewById<TextView>(com.bustime.app.R.id.tvRouteTitle).text = title
        val departureTime = stops.firstOrNull()?.first?.takeIf { !it.isNullOrBlank() }
        val arrivalTime = stops.lastOrNull()?.first?.takeIf { !it.isNullOrBlank() }
        page.findViewById<TextView>(com.bustime.app.R.id.tvRouteTime).apply {
            if (departureTime != null && arrivalTime != null) {
                text = getString(com.bustime.app.R.string.departs_at, formatPreviewTime(departureTime))
                visibility = View.VISIBLE
            } else {
                text = ""
                visibility = View.GONE
            }
        }
        page.findViewById<TextView>(com.bustime.app.R.id.tvRouteService).apply {
            text = service
            visibility = if (service.isBlank()) View.GONE else View.VISIBLE
        }
        page.findViewById<TextView>(com.bustime.app.R.id.tvRouteInfo).apply {
            text = info
            visibility = if (info.isBlank()) View.GONE else View.VISIBLE
        }
        page.findViewById<Button>(com.bustime.app.R.id.btnRouteCall).visibility = View.GONE
        page.findViewById<Button>(com.bustime.app.R.id.btnRouteCorrection).apply {
            text = topActionLabel
            setOnClickListener { topAction() }
        }

        val container = page.findViewById<LinearLayout>(com.bustime.app.R.id.stopsContainer)
        container.removeAllViews()
        val inflater = android.view.LayoutInflater.from(this)
        stops.forEachIndexed { index, stop ->
            val row = inflater.inflate(com.bustime.app.R.layout.item_stop, container, false)
            row.findViewById<TextView>(com.bustime.app.R.id.tvStopTime).text =
                stop.first?.takeIf { it.isNotBlank() }?.let { formatPreviewTime(it) }.orEmpty()
            row.findViewById<TextView>(com.bustime.app.R.id.tvStopName).text = stop.second
            row.findViewById<View>(com.bustime.app.R.id.lineTop).visibility =
                if (index == 0) View.INVISIBLE else View.VISIBLE
            row.findViewById<View>(com.bustime.app.R.id.lineBottom).visibility =
                if (index == stops.lastIndex) View.INVISIBLE else View.VISIBLE
            if (index == 0) {
                row.findViewById<TextView>(com.bustime.app.R.id.tvStopTime).textSize = 18f
                row.findViewById<TextView>(com.bustime.app.R.id.tvStopName).textSize = 18f
                val color = getColor(com.bustime.app.R.color.stopFromHighlight)
                row.findViewById<TextView>(com.bustime.app.R.id.tvStopTime).setTextColor(color)
                row.findViewById<TextView>(com.bustime.app.R.id.tvStopName).setTextColor(color)
                row.findViewById<View>(com.bustime.app.R.id.stopDot).backgroundTintList =
                    android.content.res.ColorStateList.valueOf(color)
            } else if (index == stops.lastIndex) {
                val color = getColor(com.bustime.app.R.color.stopToHighlight)
                row.findViewById<TextView>(com.bustime.app.R.id.tvStopTime).setTextColor(color)
                row.findViewById<TextView>(com.bustime.app.R.id.tvStopName).setTextColor(color)
                row.findViewById<View>(com.bustime.app.R.id.stopDot).backgroundTintList =
                    android.content.res.ColorStateList.valueOf(color)
            }
            container.addView(row)
        }
        if (publishAction != null) {
            content.addView(Button(this).apply {
                text = uiText("Publish route", "रूट प्रकाशित करें", "ਰੂਟ ਪ੍ਰਕਾਸ਼ਿਤ ਕਰੋ")
                setOnClickListener { publishAction() }
            }, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4)
                bottomMargin = dp(18)
            })
        }
        if (deleteAction != null) {
            content.addView(Button(this).apply {
                text = uiText("Delete route", "रूट हटाएँ", "ਰੂਟ ਮਿਟਾਓ")
                setTextColor(0xFFB42318.toInt())
                setOnClickListener { deleteAction() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        }
        setContentView(page)
    }

    private fun formatPreviewTime(value: String): String = try {
        val parts = value.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1]
        val suffix = if (hour < 12) "AM" else "PM"
        ((hour % 12).let { if (it == 0) 12 else it }).toString() + ":" + minute + " " + suffix
    } catch (_: Exception) { value }

    private fun newDraft() = JSONObject().put("place_id", JSONObject.NULL).put("name", "")
        .put("language", "en").put("time", JSONObject.NULL)

    private fun draftTime(d: JSONObject): String? =
        d.optString("time").takeIf { !d.isNull("time") && it.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) }

    private fun validateRouteDrafts(drafts: List<JSONObject>): String? {
        if (drafts.size < 2) return uiText("A route needs a start and destination.", "रूट में शुरुआत और गंतव्य होना चाहिए।", "ਰੂਟ ਵਿੱਚ ਸ਼ੁਰੂਆਤ ਅਤੇ ਮੰਜ਼ਿਲ ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ।")
        if (drafts.any { it.optString("name").trim().isBlank() }) return uiText("Enter a name for every stop.", "हर स्टॉप का नाम भरें।", "ਹਰ ਸਟਾਪ ਦਾ ਨਾਮ ਭਰੋ।")
        if (drafts.none { draftTime(it) != null }) return uiText("Set at least one time anywhere in the route.", "रूट में कम-से-कम एक स्टॉप का समय भरें।", "ਰੂਟ ਵਿੱਚ ਘੱਟੋ-ਘੱਟ ਇੱਕ ਸਟਾਪ ਦਾ ਸਮਾਂ ਭਰੋ।")
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
                if (ids.first() == ids.last()) error(uiText("Start and destination must be different places.", "शुरुआत और गंतव्य अलग-अलग स्थान होने चाहिए।", "ਸ਼ੁਰੂਆਤ ਅਤੇ ਮੰਜ਼ਿਲ ਵੱਖ-ਵੱਖ ਥਾਵਾਂ ਹੋਣੀਆਂ ਚਾਹੀਦੀਆਂ ਹਨ।"))
                val startTime = draftTime(drafts.first())
                val arriveTime = draftTime(drafts.last())
                val anyTime = drafts.mapNotNull { draftTime(it) }.firstOrNull() ?: error(uiText("Set at least one valid HH:mm time.", "कम-से-कम एक सही HH:mm समय भरें।", "ਘੱਟੋ-ਘੱਟ ਇੱਕ ਸਹੀ HH:mm ਸਮਾਂ ਭਰੋ।"))
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
            }.onFailure { toast(it.message ?: uiText("Route publish failed", "रूट प्रकाशित नहीं हो सका", "ਰੂਟ ਪ੍ਰਕਾਸ਼ਿਤ ਨਹੀਂ ਹੋ ਸਕਿਆ")) }
        }
    }

    private fun stopEditor(old: Place?) {
        showPage(if (old == null) uiText("Add Bus Stop", "बस स्टॉप जोड़ें", "ਬੱਸ ਸਟਾਪ ਜੋੜੋ") else uiText("Correction Bus Stop", "बस स्टॉप सुधारें", "ਬੱਸ ਸਟਾਪ ਵਿੱਚ ਸੋਧ ਕਰੋ"),
            uiText("Enter the names you want to save. Existing translations are preserved when left blank.", "वे नाम भरें जिन्हें सेव करना है। खाली छोड़े गए मौजूदा अनुवाद सुरक्षित रहेंगे।", "ਉਹ ਨਾਮ ਭਰੋ ਜੋ ਸੇਵ ਕਰਨੇ ਹਨ। ਖਾਲੀ ਛੱਡੇ ਮੌਜੂਦਾ ਅਨੁਵਾਦ ਸੁਰੱਖਿਅਤ ਰਹਿਣਗੇ।"))
        val kindOptions = listOf(
            "stand" to uiText("Bus Stand", "बस स्टैंड", "ਬੱਸ ਅੱਡਾ"),
            "village" to uiText("Village", "गाँव", "ਪਿੰਡ"),
            "city" to uiText("City", "शहर", "ਸ਼ਹਿਰ"),
            "place" to uiText("Other Place", "अन्य स्थान", "ਹੋਰ ਥਾਂ")
        )
        root.addView(TextView(this).apply {
            text = uiText("Type", "प्रकार", "ਕਿਸਮ"); textSize = 14f; setPadding(0, dp(8), 0, dp(4))
        })
        val kindSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MdmPanelActivity,
                android.R.layout.simple_spinner_item,
                kindOptions.map { it.second }
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            val oldIndex = kindOptions.indexOfFirst { it.first == old?.kind }.takeIf { it >= 0 } ?: 3
            setSelection(oldIndex)
        }
        root.addView(kindSpinner, LinearLayout.LayoutParams(-1, dp(48)))
        val en = EditText(this).apply { hint = uiText("English name", "अंग्रेज़ी नाम", "ਅੰਗਰੇਜ਼ੀ ਨਾਮ"); setSingleLine(true); setText(old?.names?.get("en").orEmpty()) }
        val hi = EditText(this).apply { hint = uiText("Hindi name", "हिंदी नाम", "ਹਿੰਦੀ ਨਾਮ"); setSingleLine(true); setText(old?.names?.get("hi").orEmpty()) }
        val pa = EditText(this).apply { hint = uiText("Punjabi name", "पंजाबी नाम", "ਪੰਜਾਬੀ ਨਾਮ"); setSingleLine(true); setText(old?.names?.get("pa").orEmpty()) }
        root.addView(en); root.addView(hi); root.addView(pa)
        val preview = TextView(this).apply { textSize = 15f; setPadding(0, dp(12), 0, dp(12)); visibility = android.view.View.GONE }
        root.addView(preview)
        fun makeNames() = JSONObject().apply {
            if (en.text.isNotBlank()) put("en", en.text.toString().trim())
            if (hi.text.isNotBlank()) put("hi", hi.text.toString().trim())
            if (pa.text.isNotBlank()) put("pa", pa.text.toString().trim())
        }
        button(uiText("Preview", "पूर्वावलोकन", "ਝਲਕ")) {
            val n = makeNames()
            if (n.length() == 0) toast(uiText("Enter at least one name.", "कम-से-कम एक नाम भरें।", "ਘੱਟੋ-ਘੱਟ ਇੱਕ ਨਾਮ ਭਰੋ।"))
            else {
                preview.text = uiText("English: ", "अंग्रेज़ी: ", "ਅੰਗਰੇਜ਼ੀ: ") + n.optString("en", "—") + "\n" + uiText("Hindi: ", "हिंदी: ", "ਹਿੰਦੀ: ") + n.optString("hi", "—") + "\n" + uiText("Punjabi: ", "पंजाबी: ", "ਪੰਜਾਬੀ: ") + n.optString("pa", "—")
                preview.visibility = android.view.View.VISIBLE
            }
        }
        button(uiText("Publish", "प्रकाशित करें", "ਪ੍ਰਕਾਸ਼ਿਤ ਕਰੋ")) {
            val names = makeNames()
            if (names.length() == 0) { toast(uiText("Enter at least one name.", "कम-से-कम एक नाम भरें।", "ਘੱਟੋ-ਘੱਟ ਇੱਕ ਨਾਮ ਭਰੋ।")); return@button }
            val label = names.optString("en").ifBlank { names.optString("hi").ifBlank { names.optString("pa") } }
            val id = old?.id ?: ("mdm_" + slug(label).ifBlank { "place" } + "_" + Integer.toHexString(norm(label).hashCode()).takeLast(6))
            val mergedNames = JSONObject()
            old?.names?.forEach { (language, value) -> if (value.isNotBlank()) mergedNames.put(language, value) }
            listOf("en", "hi", "pa").forEach { language -> if (names.has(language)) mergedNames.put(language, names.optString(language).trim()) }
            val selectedKind = kindOptions.getOrNull(kindSpinner.selectedItemPosition)?.first ?: "place"
            val payload = JSONObject().put("id", id).put("kind", selectedKind).put("name", mergedNames)
            publish(JSONArray().put(change("place", id, payload)))
        }
        showRoot()
    }

    private fun confirmDeleteRoute(route: Route) {
        AlertDialog.Builder(this)
            .setTitle(uiText("Delete route?", "रूट हटाएँ?", "ਰੂਟ ਮਿਟਾਉਣਾ ਹੈ?"))
            .setMessage(uiText("This route will be permanently removed from the app. This cannot be undone.", "यह रूट ऐप से स्थायी रूप से हट जाएगा। यह वापस नहीं किया जा सकता।", "ਇਹ ਰੂਟ ਐਪ ਤੋਂ ਪੱਕੇ ਤੌਰ 'ਤੇ ਮਿਟ ਜਾਵੇਗਾ। ਇਸਨੂੰ ਵਾਪਸ ਨਹੀਂ ਕੀਤਾ ਜਾ ਸਕਦਾ।"))
            .setNegativeButton(uiText("Cancel", "रद्द करें", "ਰੱਦ ਕਰੋ"), null)
            .setPositiveButton(uiText("Delete", "हटाएँ", "ਮਿਟਾਓ")) { _, _ ->
                publish(JSONArray().put(JSONObject().put("entity_type", "route").put("entity_id", route.id).put("operation", "delete").put("payload", JSONObject.NULL)))
            }.show()
    }

    private fun confirmDeleteStop(stop: Place) {
        lifecycleScope.launch {
            runCatching {
                val routes = withContext(Dispatchers.IO) { repo.getAllRoutes() }
                val using = routes.filter { r ->
                    r.fromId == stop.id || r.toId == stop.id ||
                        withContext(Dispatchers.IO) { repo.getRouteStops(r.id) }.any { it.placeId == stop.id }
                }
                if (using.isNotEmpty()) {
                    toast(uiText("Cannot delete this stop: it is used by " + using.size + " route(s). Correct those routes first.",
                        "यह स्टॉप नहीं हट सकता: " + using.size + " रूट में इस्तेमाल है। पहले उन रूटों में सुधार करें।",
                        "ਇਹ ਸਟਾਪ ਨਹੀਂ ਮਿਟ ਸਕਦਾ: ਇਹ " + using.size + " ਰੂਟਾਂ ਵਿੱਚ ਵਰਤਿਆ ਗਿਆ ਹੈ। ਪਹਿਲਾਂ ਉਹਨਾਂ ਰੂਟਾਂ ਨੂੰ ਸੋਧੋ।"))
                    return@runCatching
                }
                AlertDialog.Builder(this@MdmPanelActivity)
                    .setTitle(uiText("Delete bus stop?", "बस स्टॉप हटाएँ?", "ਬੱਸ ਸਟਾਪ ਮਿਟਾਓ?"))
                    .setMessage(uiText("This stop will be permanently removed. This cannot be undone.", "यह स्टॉप स्थायी रूप से हट जाएगा। यह वापस नहीं किया जा सकता।", "ਇਹ ਸਟਾਪ ਪੱਕੇ ਤੌਰ 'ਤੇ ਮਿਟ ਜਾਵੇਗਾ।"))
                    .setNegativeButton(uiText("Cancel", "रद्द करें", "ਰੱਦ ਕਰੋ"), null)
                    .setPositiveButton(uiText("Delete", "हटाएँ", "ਮਿਟਾਓ")) { _, _ ->
                        publish(JSONArray().put(JSONObject().put("entity_type", "place").put("entity_id", stop.id).put("operation", "delete").put("payload", JSONObject.NULL)))
                    }.show()
            }.onFailure { toast(it.message ?: uiText("Could not check stop usage", "स्टॉप का उपयोग जाँच नहीं सके", "ਸਟਾਪ ਦੀ ਵਰਤੋਂ ਨਹੀਂ ਜਾਂਚ ਸਕੇ")) }
        }
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
            val json=JSONObject(raw.ifBlank{"{}"});if(code !in 200..299||!json.optBoolean("ok"))error(json.optString("error",uiText("Publish failed", "प्रकाशित नहीं हो सका", "ਪ੍ਰਕਾਸ਼ਿਤ ਨਹੀਂ ਹੋ ਸਕਿਆ")))
            json.optLong("revision")
        }}.onSuccess { rev->runCatching{repo.syncData()};toast(uiText("Published successfully · revision $rev", "सफलतापूर्वक प्रकाशित · संशोधन $rev", "ਸਫਲਤਾਪੂਰਵਕ ਪ੍ਰਕਾਸ਼ਿਤ · ਰੀਵਿਜ਼ਨ $rev"));recreate() }.onFailure{toast(it.message?:uiText("Publish failed", "प्रकाशित नहीं हो सका", "ਪ੍ਰਕਾਸ਼ਿਤ ਨਹੀਂ ਹੋ ਸਕਿਆ"))}
    } }
    companion object { const val EXTRA_PASSWORD="mdm_password" }
}
