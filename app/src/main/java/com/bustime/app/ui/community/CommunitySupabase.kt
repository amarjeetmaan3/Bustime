package com.bustime.app.ui.community

import com.bustime.app.utils.Constants
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Public client is intentionally INSERT-only for user-submitted requests. */
object CommunitySupabase {
    fun isConfigured(): Boolean = Constants.SUPABASE_URL.startsWith("https://") &&
        !Constants.SUPABASE_URL.contains("YOUR-") && !Constants.SUPABASE_ANON_KEY.contains("YOUR-")

    fun insert(table: String, body: JSONObject): Boolean {
        if (table !in setOf("correction_submissions", "vehicle_submissions", "feedback_submissions")) return false
        val conn = URL(Constants.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + table).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
            if (Constants.SUPABASE_ANON_KEY.startsWith("eyJ")) {
                conn.setRequestProperty("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
            }
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    fun getPublicOperators(): String? {
        val conn = URL(Constants.SUPABASE_URL.trimEnd('/') + "/rest/v1/bus_operators?select=id,operator_name,service_name,bus_type,seat_type,from_text,to_text,location,phone,whatsapp,details&active=eq.true&order=operator_name.asc")
            .openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
            if (Constants.SUPABASE_ANON_KEY.startsWith("eyJ")) {
                conn.setRequestProperty("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
            }
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
