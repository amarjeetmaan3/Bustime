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

    private fun showRoutes() { lifecycleScope.launch {
        val rs=repo.getAllRoutes(); val ps=repo.getAllPlaces().associateBy{it.id}
        val msg=rs.take(250).joinToString("\n"){ "${ps[it.fromId]?.displayName("en") ?: it.fromId} → ${ps[it.toId]?.displayName("en") ?: it.toId} · ${it.time}" }.ifBlank{"No routes found"}
        AlertDialog.Builder(this@MdmPanelActivity).setTitle("Route List").setMessage(msg).setPositiveButton("Close",null).show()
    } }
    private fun showStops() { lifecycleScope.launch {
        val msg=repo.getAllPlaces().take(400).joinToString("\n"){ "${it.displayName("en")} · ${it.id}" }.ifBlank{"No stops found"}
        AlertDialog.Builder(this@MdmPanelActivity).setTitle("Bus Stop List").setMessage(msg).setPositiveButton("Close",null).show()
    } }
    private fun chooseRoute() { lifecycleScope.launch {
        val rs=repo.getAllRoutes(); val ps=repo.getAllPlaces().associateBy{it.id}
        if(rs.isEmpty()){toast("No routes available");return@launch}
        AlertDialog.Builder(this@MdmPanelActivity).setTitle("Select route").setItems(rs.map{"${ps[it.fromId]?.displayName("en") ?: it.fromId} → ${ps[it.toId]?.displayName("en") ?: it.toId} · ${it.time}"}.toTypedArray()){_,i->routeEditor(rs[i])}.show()
    } }
    private fun routeEditor(old:Route?) { lifecycleScope.launch {
        val ps=repo.getAllPlaces(); val byId=ps.associateBy{it.id}
        val oldStops=if(old==null) emptyList() else repo.getRouteStops(old.id).sortedBy{it.stopOrder}
        val content=box()
        content.addView(TextView(this@MdmPanelActivity).apply{text="One stop per line in travel order. Optional time after | (HH:mm). At least one time is required.";textSize=14f})
        val stops=EditText(this@MdmPanelActivity).apply {
            minLines=5; gravity=Gravity.TOP
            hint="Start stand | 06:30\nIntermediate stop\nDestination | 09:10"
            if(old!=null) setText(oldStops.joinToString("\n"){s->(byId[s.placeId]?.displayName("en")?:s.placeId)+(s.time?.let{" | $it"}?:"")})
        }
        val service=EditText(this@MdmPanelActivity).apply{hint="Service name (optional)";setText(old?.serviceName.orEmpty())}
        content.addView(stops);content.addView(service)
        AlertDialog.Builder(this@MdmPanelActivity).setTitle(if(old==null)"Add Route" else "Correction Route").setView(content).setNegativeButton("Cancel",null).setPositiveButton("Publish"){_,_->
            val lines=stops.text.toString().lines().map{it.trim()}.filter{it.isNotBlank()}.map{val p=it.split("|",limit=2);p[0].trim() to p.getOrNull(1)?.trim().orEmpty()}
            val time=Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
            if(lines.size<2||lines.any{it.first.isBlank()||(it.second.isNotBlank()&&!time.matches(it.second))}){toast("Enter at least two stops and use valid HH:mm times");return@setPositiveButton}
            if(lines.none{it.second.isNotBlank()}){toast("At least one stop time is required");return@setPositiveButton}
            lifecycleScope.launch { runCatching {
                val known=ps.associateBy{norm(it.displayName("en"))};val ids=mutableListOf<String>();val changes=JSONArray()
                lines.forEach{(label,t)->val p=known[norm(label)];val id=p?.id?:("mdm_"+slug(label));ids.add(id);if(p==null)changes.put(change("place",id,JSONObject().put("id",id).put("kind","place").put("name",JSONObject().put("en",label))))}
                val id=old?.id?:"mdm_route_"+System.currentTimeMillis().toString(36)
                val route=JSONObject().put("id",id).put("from",ids.first()).put("to",ids.last())
                    .put("time",lines.first().second.ifBlank{JSONObject.NULL}).put("arrive",lines.last().second.ifBlank{JSONObject.NULL})
                    .put("service",service.text.toString().trim().ifBlank{JSONObject.NULL}).put("routeName",JSONObject.NULL)
                    .put("contact",JSONObject.NULL).put("info",JSONObject.NULL).put("longRoute",lines.size>8).put("startDate",JSONObject.NULL).put("hidden",false)
                    .put("stops",JSONArray().also{a->lines.subList(1,lines.lastIndex).forEachIndexed{i,l->a.put(JSONObject().put("place",ids[i+1]).put("time",l.second.ifBlank{JSONObject.NULL}))}})
                changes.put(change("route",id,route));publish(changes)
            }.onFailure{toast(it.message?:"Route publish failed")} }
        }.show()
    } }
    private fun chooseStop() { lifecycleScope.launch {
        val ps=repo.getAllPlaces();if(ps.isEmpty()){toast("No stops available");return@launch}
        AlertDialog.Builder(this@MdmPanelActivity).setTitle("Select bus stop").setItems(ps.map{"${it.displayName("en")} · ${it.id}"}.toTypedArray()){_,i->stopEditor(ps[i])}.show()
    } }
    private fun stopEditor(old:Place?) {
        val content=box()
        val en=EditText(this).apply{hint="English name";setText(old?.names?.get("en").orEmpty())}
        val hi=EditText(this).apply{hint="Hindi name (optional)";setText(old?.names?.get("hi").orEmpty())}
        val pa=EditText(this).apply{hint="Punjabi name (optional)";setText(old?.names?.get("pa").orEmpty())}
        content.addView(en);content.addView(hi);content.addView(pa)
        AlertDialog.Builder(this).setTitle(if(old==null)"Add Bus Stop" else "Correction Bus Stop").setView(content).setNegativeButton("Cancel",null).setPositiveButton("Publish"){_,_->
            val label=en.text.toString().trim();val id=old?.id?:"mdm_"+slug(label)
            val names=JSONObject();if(label.isNotBlank())names.put("en",label);if(hi.text.isNotBlank())names.put("hi",hi.text.toString().trim());if(pa.text.isNotBlank())names.put("pa",pa.text.toString().trim())
            if(names.length()==0){toast("Enter at least one name");return@setPositiveButton}
            val p=JSONObject().put("id",id).put("kind",old?.kind?:"place").put("name",names)
            publish(JSONArray().put(change("place",id,p)))
        }.show()
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
