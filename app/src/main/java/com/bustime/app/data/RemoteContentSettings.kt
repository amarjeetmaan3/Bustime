package com.bustime.app.data

import com.bustime.app.utils.Constants
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteContentSettings(
    val termsEnabled: Boolean = true,
    val terms: Map<String, String> = emptyMap(),
    val popupEnabled: Boolean = true,
    val popup: Map<String, String> = emptyMap(),
    val popupSeconds: Int = 5,
    val tickerEnabled: Boolean = true,
    val ticker: Map<String, String> = emptyMap()
) {
    fun textFor(map: Map<String, String>, language: String): String =
        map[language]?.takeIf { it.isNotBlank() }
            ?: map[if (language == "pa") "hi" else "en"]?.takeIf { it.isNotBlank() }
            ?: map.values.firstOrNull { it.isNotBlank() }.orEmpty()

    companion object {
        @Volatile private var cached: RemoteContentSettings? = null

        fun get(): RemoteContentSettings = cached ?: RemoteContentSettings()

        fun refresh(): RemoteContentSettings {
            var connection: HttpURLConnection? = null
            return try {
                if (!Constants.SUPABASE_URL.startsWith("https://") || Constants.SUPABASE_ANON_KEY.isBlank()) return get()
                connection = URL(Constants.SUPABASE_URL.trimEnd('/') + "/rest/v1/app_content_settings?id=eq.1&select=settings")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 7000
                connection.setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
                if (Constants.SUPABASE_ANON_KEY.startsWith("eyJ")) connection.setRequestProperty("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
                if (connection.responseCode !in 200..299) return get()
                val array = org.json.JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                if (array.length() == 0) return get()
                val root = array.optJSONObject(0)?.optJSONObject("settings") ?: return get()
                fun map(name: String): Map<String, String> {
                    val obj = root.optJSONObject(name) ?: JSONObject()
                    return listOf("en", "hi", "pa").associateWith { obj.optString(it, "") }
                }
                RemoteContentSettings(
                    termsEnabled = root.optBoolean("terms_enabled", true),
                    terms = map("terms"),
                    popupEnabled = root.optBoolean("popup_enabled", true),
                    popup = map("popup"),
                    popupSeconds = root.optInt("popup_seconds", 5).coerceIn(5, 60),
                    tickerEnabled = root.optBoolean("ticker_enabled", true),
                    ticker = map("ticker")
                ).also { cached = it }
            } catch (_: Exception) { get() } finally { connection?.disconnect() }
        }

        fun pairedLanguages(language: String): List<String> =
            when (language) {
                "pa" -> listOf("pa", "hi")
                "hi" -> listOf("hi", "en")
                else -> listOf("en", "hi")
            }
    }
}