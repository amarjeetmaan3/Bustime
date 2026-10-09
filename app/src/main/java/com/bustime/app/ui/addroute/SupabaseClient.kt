package com.bustime.app.ui.addroute

import com.bustime.app.utils.Constants
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Supabase को नया रूट भेजता है (सिर्फ़ INSERT, कोई पढ़ना नहीं)। अलग लाइब्रेरी की ज़रूरत नहीं। */
object SupabaseClient {

    fun isConfigured(): Boolean =
        Constants.SUPABASE_URL.startsWith("https://") && !Constants.SUPABASE_URL.contains("YOUR-") &&
            !Constants.SUPABASE_ANON_KEY.contains("YOUR-")

    /** true = भेज दिया गया। नेटवर्क के काम की वजह से इसे बैकग्राउंड थ्रेड पर ही चलाओ। */
    fun insertRouteSubmission(body: JSONObject): Boolean {
        val conn = URL(Constants.SUPABASE_URL.trimEnd('/') + "/rest/v1/route_submissions")
            .openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
            // पुरानी anon key (eyJ... वाली JWT) को Authorization में भी भेजना होता है;
            // नई "sb_publishable_..." key सिर्फ़ apikey में जाती है
            if (Constants.SUPABASE_ANON_KEY.startsWith("eyJ")) {
                conn.setRequestProperty("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
            }
            conn.setRequestProperty("Content-Type", "application/json")
            // पढ़ने की इजाज़त नहीं है, इसलिए जवाब में रिकॉर्ड वापस मत माँगो
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }
}
