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
        button(uiText("Recycle Bin", "रिसायकल बिन", "ਰੀਸਾਈਕਲ ਬਿਨ")) { showRecycleBin() }
        button(uiText("Review Center", "रिव्यू सेंटर", "ਰੀਵਿਊ ਸੈਂਟਰ")) { showReviewCenter() }
        button(uiText("Add Route", "रूट जोड़ें", "ਰੂਟ ਜੋੜੋ")) { routeEditor(null) }
        button(uiText("Correction Route", "रूट सुधारें", "ਰੂਟ ਵਿੱਚ ਸੋਧ ਕਰੋ")) { showRoutesForCorrection() }
        button(uiText("Add Bus Stop", "बस स्टॉप जोड़ें", "ਬੱਸ ਸਟਾਪ ਜੋੜੋ")) { stopEditor(null) }
        button(uiText("Back", "वापस", "ਵਾਪਸ")) { finish() }
        showRoot()
    }

    private fun callReview(action: String, table: String? = null, id: String? = null, status: String? = null, fields: JSONObject? = null): JSONObject {
        val conn = (URL(Constants.SUPABASE_URL.trimEnd('/') + "/functions/v1/mdm-publish").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 20000; doOutput = true
            setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY); setRequestProperty("Content-Type", "application/json")
        }
        val body = JSONObject().put("action", action).put("password", password)
        if (table != null) body.put("table", table)
        if (id != null) body.put("id", id)
        if (status != null) body.put("status", status)
        if (fields != null) body.put("fields", fields)
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        val result = JSONObject(raw.ifBlank { "{}" })
        if (code !in 200..299 || !result.optBoolean("ok")) error(result.optString("error", "Review Center request failed"))
        return result
    }

    private fun editReviewRoute(table: String, item: JSONObject) {
        lifecycleScope.launch {
            runCatching {
                val places = withContext(Dispatchers.IO) { repo.getAllPlaces() }
                val old = if (table == "correction_submissions") {
                    withContext(Dispatchers.IO) { repo.getRoute(item.optString("route_id")) }
                } else null
                val rawStops = if (table == "route_submissions") item.optJSONArray("stops") else item.optJSONArray("proposed_stops")
                val drafts = mutableListOf<JSONObject>()
                if (rawStops != null) {
                    for (i in 0 until rawStops.length()) {
                        val st = rawStops.optJSONObject(i) ?: continue
                        val pid = st.optString("place").ifBlank { st.optString("place_id") }
                        val place = places.firstOrNull { it.id == pid }
                        val name = st.optString("name").ifBlank { place?.displayName("en") ?: pid }
                        if (name.isNotBlank()) drafts.add(JSONObject()
                            .put("place_id", if (place == null) JSONObject.NULL else place.id)
                            .put("name", name).put("language", "en")
                            .put("time", st.optString("time").takeIf { it.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) } ?: JSONObject.NULL))
                    }
                }
                if (drafts.size < 2 && old != null) {
                    drafts.clear()
                    val oldStops = withContext(Dispatchers.IO) { repo.getRouteStops(old.id) }.sortedBy { it.stopOrder }
                    oldStops.forEach { stop ->
                        val place = places.firstOrNull { it.id == stop.placeId }
                        drafts.add(JSONObject().put("place_id", stop.placeId)
                            .put("name", place?.displayName("en") ?: stop.placeId).put("language", "en")
                            .put("time", stop.time ?: JSONObject.NULL))
                    }
                }
                if (drafts.size < 2) {
                    drafts.clear()
                    val from = item.optString("from_text").ifBlank { item.optString("from_id") }
                    val to = item.optString("to_text").ifBlank { item.optString("to_id") }
                    if (from.isNotBlank()) drafts.add(JSONObject().put("place_id", JSONObject.NULL).put("name", from).put("language", "en").put("time", item.optString("start_time").takeIf { it.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) } ?: JSONObject.NULL))
                    if (to.isNotBlank()) drafts.add(JSONObject().put("place_id", JSONObject.NULL).put("name", to).put("language", "en").put("time", JSONObject.NULL))
                }
                if (drafts.size < 2) error(uiText("This request does not contain enough route details to open the editor.", "इस रिक्वेस्ट में एडिटर खोलने के लिए पर्याप्त रूट जानकारी नहीं है।", "ਇਸ ਬੇਨਤੀ ਵਿੱਚ ਐਡੀਟਰ ਖੋਲ੍ਹਣ ਲਈ ਕਾਫ਼ੀ ਰੂਟ ਜਾਣਕਾਰੀ ਨਹੀਂ ਹੈ।"))
                renderRouteEditor(old, drafts, item.optString("service_name"), places) { showReviewCenter() }
            }.onFailure { toast(it.message ?: uiText("Could not open route editor", "रूट एडिटर नहीं खुला", "ਰੂਟ ਐਡੀਟਰ ਨਹੀਂ ਖੁੱਲ੍ਹਿਆ")) }
        }
    }

    private fun showReviewCenter() {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { callReview("review-list") } }
                .onSuccess { response ->
                    showPage(uiText("Review Center", "रिव्यू सेंटर", "ਰੀਵਿਊ ਸੈਂਟਰ"),
                        uiText("All user requests, grouped by status. Open a request to inspect or edit it.", "सभी रिक्वेस्ट स्टेटस के अनुसार। देखने या एडिट करने के लिए खोलें।", "ਸਾਰੀਆਂ ਬੇਨਤੀਆਂ ਸਟੇਟਸ ਮੁਤਾਬਕ। ਵੇਖਣ ਜਾਂ ਸੋਧਣ ਲਈ ਖੋਲ੍ਹੋ।"))
                    val failed = response.optJSONArray("failed") ?: JSONArray()
                    if (failed.length() > 0) root.addView(TextView(this@MdmPanelActivity).apply {
                        text = "Some queues failed to load: " + (0 until failed.length()).joinToString { failed.optString(it) }
                        setTextColor(getColor(com.bustime.app.R.color.textSecondary))
                    })
                    data class Q(val table:String,val title:String,val kind:String,val rows:JSONArray)
                    val queues = listOf(
                        Q("route_submissions","Bus Route Requests","route",response.optJSONArray("route_submissions")?:JSONArray()),
                        Q("correction_submissions","Route Corrections","correction",response.optJSONArray("correction_submissions")?:JSONArray()),
                        Q("bus_operator_submissions","Add Your Bus / Book a Bus","bus",response.optJSONArray("bus_operator_submissions")?:JSONArray()),
                        Q("feedback_submissions","Feedback","feedback",response.optJSONArray("feedback_submissions")?:JSONArray())
                    )
                    fun v(o:JSONObject,vararg keys:String):String { for(k in keys){val x=o.opt(k);if(x!=null&&x!=JSONObject.NULL){val s=x.toString().trim();if(s.isNotBlank()&&s!="null"&&s!="[]")return s}};return "" }
                    fun stops(a:JSONArray?):String { if(a==null)return "";val out=mutableListOf<String>();for(i in 0 until a.length()){val o=a.optJSONObject(i);val n=if(o!=null)v(o,"name","place_name","place","place_id")else a.optString(i);if(n.isNotBlank()&&n!="null")out.add(n)};return out.joinToString(" → ") }
                    val states=listOf("pending" to "Pending","reviewed" to "Reviewed","approved" to "Approved","rejected" to "Rejected","deleted" to "Deleted")
                    for((status,title) in states){
                        val rows=queues.flatMap{q->(0 until q.rows.length()).mapNotNull{idx->val o=q.rows.optJSONObject(idx)?:return@mapNotNull null;if(v(o,"status").ifBlank{"pending"}.lowercase(Locale.ROOT)==status)q to o else null}}
                        val panel=LinearLayout(this@MdmPanelActivity).apply{orientation=1;setPadding(dp(12),dp(10),dp(12),dp(10));background=android.graphics.drawable.GradientDrawable().apply{setColor(getColor(com.bustime.app.R.color.surface));cornerRadius=dp(12).toFloat();setStroke(dp(1),getColor(com.bustime.app.R.color.divider))}}
                        panel.addView(TextView(this@MdmPanelActivity).apply{text=title+"  ·  "+rows.size;textSize=18f;setTypeface(typeface,android.graphics.Typeface.BOLD)})
                        if(rows.isEmpty())panel.addView(TextView(this@MdmPanelActivity).apply{text="No requests in this section.";setTextColor(getColor(com.bustime.app.R.color.textSecondary));setPadding(0,dp(6),0,dp(6))})
                        for(q in queues){val list=rows.filter{it.first.table==q.table};if(list.isEmpty())continue
                            panel.addView(TextView(this@MdmPanelActivity).apply{text=q.title+"  ·  "+list.size;textSize=14f;setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(0,dp(9),0,dp(4))})
                            for((_,o) in list){
                                val id=v(o,"id");val routeStops=stops(o.optJSONArray("stops"));val from=v(o,"from_text","from_id").ifBlank{routeStops.substringBefore(" → ")};val to=v(o,"to_text","to_id").ifBlank{routeStops.substringAfterLast(" → ")}
                                val heading=when(q.kind){"route"->if(from.isBlank()||to.isBlank())"Incomplete route request" else from+" → "+to;"correction"->v(o,"request_type").replace('_',' ').ifBlank{"Route correction"}+v(o,"route_id").takeIf{it.isNotBlank()}?.let{" · "+it}.orEmpty();"bus"->v(o,"service_name","operator_name").ifBlank{"Add Your Bus request"};else->v(o,"category").ifBlank{"Feedback"}}
                                val parts=when(q.kind){"route"->listOf("From: "+v(o,"from_text","from_id"),"To: "+v(o,"to_text","to_id"),"Stops: "+routeStops,"Time: "+v(o,"start_time"),"Service: "+v(o,"service_name"),"Phone: "+v(o,"phone"));"correction"->listOf("Type: "+v(o,"request_type"),"Route: "+v(o,"route_id"),"Details: "+v(o,"change_summary","notes","missing_place_name","corrected_time"),"Proposed stops: "+stops(o.optJSONArray("proposed_stops")),"Phone: "+v(o,"phone"));"bus"->listOf("Operator: "+v(o,"operator_name"),"Service: "+v(o,"service_name"),"Type: "+v(o,"bus_type","bus_types"),"Location: "+v(o,"location"),"From: "+v(o,"from_text"),"To: "+v(o,"to_text"),"Phone: "+v(o,"phone"),"Details: "+v(o,"details"));else->listOf("Category: "+v(o,"category"),"Message: "+v(o,"message"),"Related route: "+v(o,"related_route"),"Contact: "+v(o,"contact"))}.filter{it.substringAfter(":").isNotBlank()}.joinToString("\n")
                                val card=LinearLayout(this@MdmPanelActivity).apply{orientation=1;setPadding(dp(10),dp(9),dp(10),dp(9));background=android.graphics.drawable.GradientDrawable().apply{setColor(getColor(com.bustime.app.R.color.background));cornerRadius=dp(9).toFloat();setStroke(dp(1),getColor(com.bustime.app.R.color.divider))}}
                                card.addView(TextView(this@MdmPanelActivity).apply{text=heading;textSize=15f;setTypeface(typeface,android.graphics.Typeface.BOLD)})
                                card.addView(TextView(this@MdmPanelActivity).apply{text=v(o,"created_at").replace('T',' ').take(16)+"  ·  "+title.uppercase(Locale.ROOT);textSize=11f;setTextColor(getColor(com.bustime.app.R.color.textSecondary));setPadding(0,dp(3),0,dp(5))})
                                card.addView(TextView(this@MdmPanelActivity).apply{text=parts.ifEmpty{listOf("No request details were supplied.")}.joinToString("\n");textSize=13f})
                                card.addView(Button(this@MdmPanelActivity).apply{text=if(q.kind=="route"||q.kind=="correction")"Open Route Editor" else if(q.kind=="bus")"Edit Bus Request" else "Open Feedback";setOnClickListener{if(q.kind=="route"||q.kind=="correction")editReviewRoute(q.table,o)else editReviewRequest(q.table,o)}},LinearLayout.LayoutParams(-1,dp(44)))
                                val actions=LinearLayout(this@MdmPanelActivity).apply{orientation=0}
                                if(q.kind=="feedback"){if(status!="reviewed"&&status!="deleted")actions.addView(Button(this@MdmPanelActivity).apply{text="Mark Reviewed";setOnClickListener{updateReviewItem(q.table,id,"reviewed")}},LinearLayout.LayoutParams(0,dp(44),1f))}
                                else if(status!="approved"&&status!="deleted")actions.addView(Button(this@MdmPanelActivity).apply{text="Approve";setOnClickListener{updateReviewItem(q.table,id,"approved")}},LinearLayout.LayoutParams(0,dp(44),1f))
                                if(status!="rejected"&&status!="deleted")actions.addView(Button(this@MdmPanelActivity).apply{text="Reject";setOnClickListener{updateReviewItem(q.table,id,"rejected")}},LinearLayout.LayoutParams(0,dp(44),1f))
                                if(status=="deleted")actions.addView(Button(this@MdmPanelActivity).apply{text="Restore";setOnClickListener{updateReviewItem(q.table,id,"pending")}},LinearLayout.LayoutParams(0,dp(44),1f))
                                else if(status!="pending")actions.addView(Button(this@MdmPanelActivity).apply{text="Move to Pending";setOnClickListener{updateReviewItem(q.table,id,"pending")}},LinearLayout.LayoutParams(0,dp(44),1f))
                                if(status!="deleted")actions.addView(Button(this@MdmPanelActivity).apply{text="Delete";setOnClickListener{AlertDialog.Builder(this@MdmPanelActivity).setTitle("Move request to Deleted?").setMessage("The record will be hidden from active sections but can be restored from Deleted.").setNegativeButton("Cancel",null).setPositiveButton("Move to Deleted"){_,_->updateReviewItem(q.table,id,"deleted")}.show()}},LinearLayout.LayoutParams(0,dp(44),1f))
                                if(actions.childCount>0)card.addView(actions,LinearLayout.LayoutParams(-1,-2))
                                panel.addView(card,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4);bottomMargin=dp(6)})
                            }
                        }
                        root.addView(panel,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
                    }
                    root.addView(Button(this@MdmPanelActivity).apply{text="Refresh Review Center";setOnClickListener{showReviewCenter()}},LinearLayout.LayoutParams(-1,-2))
                    showRoot()
                }
                .onFailure { toast(it.message ?: uiText("Could not load Review Center", "रिव्यू सेंटर लोड नहीं हुआ", "ਰੀਵਿਊ ਸੈਂਟਰ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕਿਆ")) }
        }
    }

    private fun editReviewRequest(table: String, item: JSONObject) {
        val id = item.optString("id")
        val isBus = table == "bus_operator_submissions"
        if (!isBus && table != "feedback_submissions") return
        showPage(
            if (isBus) uiText("Edit Add Your Bus Request", "बस रिक्वेस्ट एडिट करें", "ਬੱਸ ਬੇਨਤੀ ਸੋਧੋ") else uiText("Edit Feedback", "फीडबैक एडिट करें", "ਫੀਡਬੈਕ ਸੋਧੋ"),
            uiText("Edit the submitted details, then save the request.", "भेजी गई जानकारी एडिट करके सेव करें।", "ਭੇਜੀ ਜਾਣਕਾਰੀ ਸੋਧ ਕੇ ਸੇਵ ਕਰੋ।")
        )
        pageBackAction = { showReviewCenter() }
        val values = linkedMapOf<String, EditText>()
        fun addField(key: String, label: String, multiline: Boolean = false, phone: Boolean = false) {
            root.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(getColor(com.bustime.app.R.color.textSecondary)); setPadding(0, dp(8), 0, dp(3))
            })
            val edit = EditText(this).apply {
                setText(item.optString(key).takeIf { it != "null" }.orEmpty())
                hint = label
                if (multiline) { minLines = 3; maxLines = 6 } else setSingleLine(true)
                if (phone) inputType = InputType.TYPE_CLASS_PHONE
            }
            root.addView(edit, LinearLayout.LayoutParams(-1, if (multiline) dp(100) else dp(48)))
            values[key] = edit
        }
        if (isBus) {
            addField("operator_name", uiText("Operator / person name", "ऑपरेटर / व्यक्ति का नाम", "ਆਪਰੇਟਰ / ਵਿਅਕਤੀ ਦਾ ਨਾਮ"))
            addField("service_name", uiText("Bus service name", "बस सेवा का नाम", "ਬੱਸ ਸੇਵਾ ਦਾ ਨਾਮ"))
            addField("bus_types", uiText("Bus types (comma separated)", "बस के प्रकार (कॉमा से अलग)", "ਬੱਸ ਦੀਆਂ ਕਿਸਮਾਂ (ਕੌਮੇ ਨਾਲ ਵੱਖ ਕਰੋ)"))
            values["bus_types"]?.setText((item.optJSONArray("bus_types")?.let { a -> (0 until a.length()).map { a.optString(it) }.joinToString(", ") } ?: item.optString("bus_type")).ifBlank { "Ordinary" })
            addField("location", uiText("Location / area", "स्थान / एरिया", "ਥਾਂ / ਇਲਾਕਾ"))
            addField("phone", uiText("Mobile number", "मोबाइल नंबर", "ਮੋਬਾਈਲ ਨੰਬਰ"), phone = true)
            addField("whatsapp", uiText("WhatsApp (optional)", "व्हाट्सऐप (वैकल्पिक)", "ਵਟਸਐਪ (ਵਿਕਲਪਿਕ)"), phone = true)
            addField("details", uiText("Other information", "अन्य जानकारी", "ਹੋਰ ਜਾਣਕਾਰੀ"), multiline = true)
        } else {
            addField("category", uiText("Category", "श्रेणी", "ਸ਼੍ਰੇਣੀ"))
            addField("related_route", uiText("Related route (optional)", "संबंधित रूट (वैकल्पिक)", "ਸੰਬੰਧਿਤ ਰੂਟ (ਵਿਕਲਪਿਕ)"))
            addField("contact", uiText("Mobile number (optional)", "मोबाइल नंबर (वैकल्पिक)", "ਮੋਬਾਈਲ ਨੰਬਰ (ਵਿਕਲਪਿਕ)"), phone = true)
            addField("message", uiText("Feedback / message", "फीडबैक / संदेश", "ਫੀਡਬੈਕ / ਸੁਨੇਹਾ"), multiline = true)
        }
        button(uiText("Save Request", "रिक्वेस्ट सेव करें", "ਬੇਨਤੀ ਸੇਵ ਕਰੋ")) {
            val fields = JSONObject()
            values.forEach { (key, edit) ->
                if (isBus && key == "bus_types") fields.put(key, JSONArray(edit.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()))
                else fields.put(key, edit.text.toString().trim())
            }
            if (isBus && (fields.optString("operator_name").isBlank() || fields.optString("phone").isBlank())) {
                toast(uiText("Name and mobile number are required.", "नाम और मोबाइल नंबर ज़रूरी हैं।", "ਨਾਮ ਅਤੇ ਮੋਬਾਈਲ ਨੰਬਰ ਲਾਜ਼ਮੀ ਹਨ।")); return@button
            }
            if (!isBus && fields.optString("message").length < 4) {
                toast(uiText("Enter at least 4 characters of feedback.", "कम-से-कम 4 अक्षरों का फीडबैक भरें।", "ਘੱਟੋ-ਘੱਟ 4 ਅੱਖਰਾਂ ਦਾ ਫੀਡਬੈਕ ਲਿਖੋ।")); return@button
            }
            lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) { callReview("review-edit", table, id, fields = fields) } }
                    .onSuccess { toast(uiText("Request saved.", "रिक्वेस्ट सेव हो गई।", "ਬੇਨਤੀ ਸੇਵ ਹੋ ਗਈ।")); showReviewCenter() }
                    .onFailure { toast(it.message ?: uiText("Could not save request", "रिक्वेस्ट सेव नहीं हुई", "ਬੇਨਤੀ ਸੇਵ ਨਹੀਂ ਹੋਈ")) }
            }
        }
        showRoot()
    }

    private fun updateReviewItem(table: String, id: String, status: String) {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { callReview("review-status", table, id, status) } }
                .onSuccess { toast(uiText("Request status updated.", "रिक्वेस्ट का स्टेटस अपडेट हो गया।", "ਬੇਨਤੀ ਦਾ ਸਟੇਟਸ ਅੱਪਡੇਟ ਹੋ ਗਿਆ।")); showReviewCenter() }
                .onFailure { toast(it.message ?: uiText("Could not update request", "रिक्वेस्ट अपडेट नहीं हुई", "ਬੇਨਤੀ ਅੱਪਡੇਟ ਨਹੀਂ ਹੋਈ")) }
        }
    }

    private fun callRecycle(action: String, id: Long? = null): JSONObject {
        val conn = (URL(Constants.SUPABASE_URL.trimEnd('/') + "/functions/v1/mdm-publish").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 20000; doOutput = true
            setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
            setRequestProperty("Content-Type", "application/json")
        }
        val body = JSONObject().put("action", action).put("password", password)
        if (id != null) body.put("id", id)
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        val result = JSONObject(raw.ifBlank { "{}" })
        if (code !in 200..299 || !result.optBoolean("ok")) error(result.optString("error", "Recycle Bin request failed"))
        return result
    }

    private fun showRecycleBin() {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { callRecycle("recycle-list") } }
                .onSuccess { response ->
                    val items = response.optJSONArray("items") ?: JSONArray()
                    showPage(uiText("Recycle Bin", "रिसायकल बिन", "ਰੀਸਾਈਕਲ ਬਿਨ"),
                        uiText("Deleted route and stop records can be restored here.", "डिलीट किए गए रूट और स्टॉप यहाँ से वापस लाए जा सकते हैं।", "ਮਿਟਾਏ ਰੂਟ ਅਤੇ ਸਟਾਪ ਇੱਥੋਂ ਵਾਪਸ ਲਿਆ ਸਕਦੇ ਹੋ।"))
                    if (items.length() == 0) {
                        root.addView(TextView(this@MdmPanelActivity).apply {
                            text = uiText("Recycle Bin is empty.", "रिसायकल बिन खाली है।", "ਰੀਸਾਈਕਲ ਬਿਨ ਖਾਲੀ ਹੈ।")
                            textSize = 16f; setPadding(0, dp(12), 0, dp(12))
                        })
                    }
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val id = item.optLong("id")
                        val type = item.optString("entity_type")
                        val entityId = item.optString("entity_id")
                        val payload = item.optJSONObject("payload") ?: JSONObject()
                        val label = if (type == "route") {
                            payload.optString("from", "?") + " → " + payload.optString("to", "?")
                        } else {
                            val names = payload.optJSONObject("name")
                            names?.optString("en")?.takeIf { it.isNotBlank() }
                                ?: names?.optString("hi")?.takeIf { it.isNotBlank() }
                                ?: entityId
                        }
                        root.addView(TextView(this@MdmPanelActivity).apply {
                            text = label + "\n" + type.uppercase(Locale.ROOT) + " · ID: " + entityId + "\n" + item.optString("deleted_at")
                            textSize = 14f; setPadding(0, dp(12), 0, dp(4))
                        })
                        val actions = LinearLayout(this@MdmPanelActivity).apply { orientation = 0 }
                        actions.addView(Button(this@MdmPanelActivity).apply {
                            text = uiText("Restore", "वापस लाएँ", "ਰੀਸਟੋਰ ਕਰੋ")
                            setOnClickListener {
                                lifecycleScope.launch {
                                    runCatching { withContext(Dispatchers.IO) { callRecycle("recycle-restore", id) } }
                                        .onSuccess { runCatching { repo.syncData() }; showRecycleBin() }
                                        .onFailure { toast(it.message ?: "Restore failed") }
                                }
                            }
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        actions.addView(Button(this@MdmPanelActivity).apply {
                            text = uiText("Permanent Delete", "हमेशा के लिए हटाएँ", "ਪੱਕੇ ਤੌਰ 'ਤੇ ਮਿਟਾਓ")
                            setOnClickListener {
                                AlertDialog.Builder(this@MdmPanelActivity)
                                    .setTitle(uiText("Permanently delete?", "हमेशा के लिए हटाएँ?", "ਪੱਕੇ ਤੌਰ 'ਤੇ ਮਿਟਾਉਣਾ ਹੈ?"))
                                    .setMessage(uiText("This removes the recovery copy permanently.", "इससे रिकवरी कॉपी हमेशा के लिए हट जाएगी।", "ਇਸ ਨਾਲ ਰਿਕਵਰੀ ਕਾਪੀ ਪੱਕੇ ਤੌਰ 'ਤੇ ਮਿਟ ਜਾਵੇਗੀ।"))
                                    .setNegativeButton(uiText("Cancel", "रद्द करें", "ਰੱਦ ਕਰੋ"), null)
                                    .setPositiveButton(uiText("Delete", "हटाएँ", "ਮਿਟਾਓ")) { _, _ ->
                                        lifecycleScope.launch {
                                            runCatching { withContext(Dispatchers.IO) { callRecycle("recycle-purge", id) } }
                                                .onSuccess { showRecycleBin() }
                                                .onFailure { toast(it.message ?: "Permanent delete failed") }
                                        }
                                    }.show()
                            }
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        root.addView(actions)
                    }
                    showRoot()
                }
                .onFailure { toast(it.message ?: uiText("Could not load Recycle Bin", "रिसायकल बिन लोड नहीं हुआ", "ਰੀਸਾਈਕਲ ਬਿਨ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕਿਆ")) }
        }
    }

    private suspend fun routePayload(route: Route): JSONObject {
        val stopRows = repo.getRouteStops(route.id).sortedBy { it.stopOrder }
        val stops = JSONArray()
        stopRows.forEach { stop ->
            stops.put(JSONObject().put("place", stop.placeId).put("time", stop.time ?: JSONObject.NULL))
        }
        return JSONObject()
            .put("id", route.id).put("from", route.fromId).put("to", route.toId)
            .put("time", route.time.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("service", route.serviceName ?: JSONObject.NULL)
            .put("routeName", route.routeName ?: JSONObject.NULL)
            .put("contact", route.contact ?: JSONObject.NULL).put("info", route.info ?: JSONObject.NULL)
            .put("longRoute", route.longRoute).put("startDate", route.startDate ?: JSONObject.NULL)
            .put("stops", stops)
    }

    private fun placePayload(place: Place): JSONObject {
        val names = JSONObject()
        place.names.forEach { (lang, name) -> names.put(lang, name) }
        return JSONObject().put("id", place.id).put("kind", place.kind).put("name", names)
            .put("city", place.city ?: JSONObject.NULL).put("district", place.district ?: JSONObject.NULL)
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

    private fun showRoutesForCorrection() {
        lifecycleScope.launch {
            runCatching {
                val routes = withContext(Dispatchers.IO) { repo.getAllRoutes() }
                val places = withContext(Dispatchers.IO) { repo.getAllPlaces() }.associateBy { it.id }
                val entries = routes.map { route ->
                    val from = places[route.fromId]?.displayName("en") ?: route.fromId
                    val to = places[route.toId]?.displayName("en") ?: route.toId
                    Triple(from + " → " + to, uiText("Time: ", "समय: ", "ਸਮਾਂ: ") + route.time, route.id)
                }
                showSearchList(
                    uiText("Correction Route", "रूट सुधारें", "ਰੂਟ ਵਿੱਚ ਸੋਧ ਕਰੋ"),
                    uiText("Select an existing route to open the route editor.", "रूट एडिटर खोलने के लिए मौजूदा रूट चुनें।", "ਰੂਟ ਐਡੀਟਰ ਖੋਲ੍ਹਣ ਲਈ ਮੌਜੂਦਾ ਰੂਟ ਚੁਣੋ।"),
                    entries
                ) { id ->
                    routes.firstOrNull { it.id == id }?.let { route -> routeEditor(route) { showRoutesForCorrection() } }
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
            val problem = validateRouteDrafts(drafts, places)
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
                val problem = validateRouteDrafts(drafts, places)
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

    private fun validateRouteDrafts(drafts: List<JSONObject>, places: List<Place>): String? {
        if (drafts.size < 2) return uiText("A route needs a start and destination.", "रूट में शुरुआत और गंतव्य होना चाहिए।", "ਰੂਟ ਵਿੱਚ ਸ਼ੁਰੂਆਤ ਅਤੇ ਮੰਜ਼ਿਲ ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ।")
        if (drafts.any { it.optString("name").trim().isBlank() }) return uiText("Enter a name for every stop.", "हर स्टॉप का नाम भरें।", "ਹਰ ਸਟਾਪ ਦਾ ਨਾਮ ਭਰੋ।")

        // Resolve selected IDs and manually typed English/Hindi/Punjabi aliases to one canonical key.
        fun normalizeStop(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val seen = mutableMapOf<String, Int>()
        drafts.forEachIndexed { index, draft ->
            val name = draft.optString("name").trim()
            val selectedId = if (draft.isNull("place_id")) "" else draft.optString("place_id").trim()
            val canonical = places.firstOrNull { place ->
                (selectedId.isNotBlank() && place.id == selectedId) ||
                    place.names.values.any { alias -> normalizeStop(alias) == normalizeStop(name) }
            }
            val key = canonical?.let { "id:${it.id}" } ?: "name:${normalizeStop(name)}"
            val previous = seen[key]
            if (previous != null) {
                return uiText(
                    "Stops ${previous} and ${index + 1} refer to the same place. Each stop can appear only once.",
                    "स्टॉप ${previous} और ${index + 1} एक ही जगह हैं। हर स्टॉप केवल एक बार रखें।",
                    "ਸਟਾਪ ${previous} ਅਤੇ ${index + 1} ਇੱਕੋ ਥਾਂ ਹਨ। ਹਰ ਸਟਾਪ ਸਿਰਫ਼ ਇੱਕ ਵਾਰ ਰੱਖੋ।"
                )
            }
            seen[key] = index + 1
        }
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
                // Circular routes may start and end at the same place, but must include at least two distinct places overall.
                if (ids.distinct().size < 2) error(uiText("A route must include at least two different places. Repeated stops along the way are allowed.", "रूट में कम-से-कम दो अलग-अलग स्थान होने चाहिए। रास्ते में कोई स्टॉप दोबारा आ सकता है।", "ਰੂਟ ਵਿੱਚ ਘੱਟੋ-ਘੱਟ ਦੋ ਵੱਖ-ਵੱਖ ਥਾਵਾਂ ਹੋਣੀਆਂ ਚਾਹੀਦੀਆਂ ਹਨ। ਰਸਤੇ ਵਿੱਚ ਕੋਈ ਸਟਾਪ ਦੁਬਾਰਾ ਆ ਸਕਦਾ ਹੈ।"))
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
            .setMessage(uiText("This route will move to the Recycle Bin and can be restored.", "यह रूट रिसायकल बिन में जाएगा और वापस लाया जा सकता है।", "ਇਹ ਰੂਟ ਰੀਸਾਈਕਲ ਬਿਨ ਵਿੱਚ ਜਾਵੇਗਾ ਅਤੇ ਵਾਪਸ ਲਿਆ ਸਕਦੇ ਹੋ।"))
            .setNegativeButton(uiText("Cancel", "रद्द करें", "ਰੱਦ ਕਰੋ"), null)
            .setPositiveButton(uiText("Delete", "हटाएँ", "ਮਿਟਾਓ")) { _, _ ->
                lifecycleScope.launch { runCatching { routePayload(route) }.onSuccess { payload -> publish(JSONArray().put(JSONObject().put("entity_type", "route").put("entity_id", route.id).put("operation", "delete").put("payload", payload))) }.onFailure { toast(it.message ?: "Could not prepare recovery copy") } }
            }.show()
    }

    private fun confirmDeleteStop(stop: Place) {
        lifecycleScope.launch {
            runCatching {
                val routes = withContext(Dispatchers.IO) { repo.getAllRoutes() }
                val usedStopIds = withContext(Dispatchers.IO) {
                    routes.associate { r -> r.id to repo.getRouteStops(r.id).map { it.placeId }.toSet() }
                }
                val using = routes.filter { r ->
                    r.fromId == stop.id || r.toId == stop.id || usedStopIds[r.id].orEmpty().contains(stop.id)
                }
                if (using.isNotEmpty()) {
                    toast(uiText("Cannot delete this stop: it is used by " + using.size + " route(s). Correct those routes first.",
                        "यह स्टॉप नहीं हट सकता: " + using.size + " रूट में इस्तेमाल है। पहले उन रूटों में सुधार करें।",
                        "ਇਹ ਸਟਾਪ ਨਹੀਂ ਮਿਟ ਸਕਦਾ: ਇਹ " + using.size + " ਰੂਟਾਂ ਵਿੱਚ ਵਰਤਿਆ ਗਿਆ ਹੈ। ਪਹਿਲਾਂ ਉਹਨਾਂ ਰੂਟਾਂ ਨੂੰ ਸੋਧੋ।"))
                    return@runCatching
                }
                AlertDialog.Builder(this@MdmPanelActivity)
                    .setTitle(uiText("Delete bus stop?", "बस स्टॉप हटाएँ?", "ਬੱਸ ਸਟਾਪ ਮਿਟਾਓ?"))
                    .setMessage(uiText("This stop will move to the Recycle Bin and can be restored.", "यह स्टॉप रिसायकल बिन में जाएगा और वापस लाया जा सकता है।", "ਇਹ ਸਟਾਪ ਰੀਸਾਈਕਲ ਬਿਨ ਵਿੱਚ ਜਾਵੇਗਾ ਅਤੇ ਵਾਪਸ ਲਿਆ ਸਕਦੇ ਹੋ।"))
                    .setNegativeButton(uiText("Cancel", "रद्द करें", "ਰੱਦ ਕਰੋ"), null)
                    .setPositiveButton(uiText("Delete", "हटाएँ", "ਮਿਟਾਓ")) { _, _ ->
                        publish(JSONArray().put(JSONObject().put("entity_type", "place").put("entity_id", stop.id).put("operation", "delete").put("payload", placePayload(stop))))
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
