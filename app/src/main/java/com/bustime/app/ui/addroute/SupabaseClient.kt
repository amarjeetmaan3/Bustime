package com.bustime.app.ui.addroute

import com.bustime.app.utils.Constants
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Sends route submissions to Supabase and preserves the real server error for the UI. */
object SupabaseClient {
    @Volatile var lastError: String = ""
        private set

    fun isConfigured(): Boolean =
        Constants.SUPABASE_URL.startsWith("https://") &&
            !Constants.SUPABASE_URL.contains("YOUR-") &&
            Constants.SUPABASE_ANON_KEY.isNotBlank() &&
            !Constants.SUPABASE_ANON_KEY.contains("YOUR-")

    /** Call from Dispatchers.IO; true only when Supabase returns a 2xx status. */
    fun insertRouteSubmission(body: JSONObject): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val endpoint = Constants.SUPABASE_URL.trimEnd('/') + "/rest/v1/route_submissions"
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
                // Legacy anon keys are JWTs; publishable sb_* keys are sent via apikey.
                if (Constants.SUPABASE_ANON_KEY.startsWith("eyJ")) {
                    setRequestProperty("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
                }
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Prefer", "return=minimal")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                lastError = ""
                true
            } else {
                val response = try {
                    (conn.errorStream ?: conn.inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } catch (_: Exception) { "" }
                lastError = "HTTP $code" + if (response.isNotBlank()) ": ${response.take(700)}" else ""
                false
            }
        } catch (e: UnknownHostException) {
            lastError = "Server host not found. Verify Supabase URL and internet connection."
            false
        } catch (e: SocketTimeoutException) {
            lastError = "Request timed out while contacting Supabase."
            false
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: "Request failed"}"
            false
        } finally {
            conn?.disconnect()
        }
    }
}
