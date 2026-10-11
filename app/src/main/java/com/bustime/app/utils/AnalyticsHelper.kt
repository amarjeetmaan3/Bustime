package com.bustime.app.utils

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Privacy-conscious usage analytics. Sends event names and non-identifying properties only.
 * The session identifier is random and stored locally; no phone, name, or message is sent.
 */
object AnalyticsHelper {
    private val executor = Executors.newSingleThreadExecutor()

    fun logEvent(context: Context, event: String, section: String, properties: JSONObject = JSONObject()) {
        val app = context.applicationContext
        if (event.isBlank()) return
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val prefs = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                var session = prefs.getString("analytics_session_id", null)
                if (session.isNullOrBlank()) {
                    session = UUID.randomUUID().toString()
                    prefs.edit().putString("analytics_session_id", session).apply()
                }
                val body = JSONObject()
                    .put("event_name", event.take(80))
                    .put("section", section.take(40))
                    .put("session_id", session)
                    .put("properties", properties)
                    .put("app_version", try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (_: Exception) { null })
                    .put("platform", "android")
                conn = (URL(Constants.SUPABASE_URL.trimEnd('/') + "/rest/v1/analytics_events").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Prefer", "return=minimal")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) Log.w("AnalyticsHelper", "Analytics request failed: HTTP $code")
            } catch (e: Exception) {
                Log.w("AnalyticsHelper", "Analytics event not sent", e)
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun logAppOpened(context: Context) = logEvent(context, "app_open", "app")
    fun logSearchEvent(context: Context, from: String, to: String, vehicleType: String) =
        logEvent(context, "bus_route_search", "bus", JSONObject().put("from_id", from).put("to_id", to).put("vehicle_type", vehicleType))
    fun logStandSearch(context: Context, standId: String) =
        logEvent(context, "bus_stand_search", "bus", JSONObject().put("stand_id", standId))
}
