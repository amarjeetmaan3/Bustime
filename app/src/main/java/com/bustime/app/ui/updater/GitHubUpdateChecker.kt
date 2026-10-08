package com.bustime.app.ui.updater

import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub की "latest release" से असली वर्ज़न पढ़ता है और नया होने पर ही पॉप-अप दिखाता है।
 * नेटवर्क एरर पर चुपचाप कुछ नहीं करता। पॉप-अप में "Later" भी है (ज़बरदस्ती नहीं)।
 */
class GitHubUpdateChecker(private val activity: AppCompatActivity) {

    private data class LatestRelease(val version: String, val downloadUrl: String)

    fun checkForLatestUpdate() {
        // username अभी भरा नहीं है तो चेक ही मत करो
        if (Constants.GITHUB_OWNER.startsWith("YOUR_")) return

        activity.lifecycleScope.launch {
            val latest = withContext(Dispatchers.IO) { fetchLatestRelease() } ?: return@launch
            val current = currentVersion()
            if (isNewer(latest.version, current) && !activity.isFinishing) {
                showUpdateDialog(current, latest)
            }
        }
    }

    private fun fetchLatestRelease(): LatestRelease? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.github.com/repos/${Constants.GITHUB_OWNER}/${Constants.GITHUB_REPO}/releases/latest")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            if (tag.isEmpty()) return null

            var apkUrl = json.optString("html_url")
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            LatestRelease(tag.removePrefix("v"), apkUrl)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun currentVersion(): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    // "1.0.23" बनाम "1.0.9" को संख्या के हिसाब से तुलना करता है
    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun showUpdateDialog(currentVersion: String, latest: LatestRelease) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_update, null)
        view.findViewById<TextView>(R.id.tvNewVersion).text = "New Version: v${latest.version}"
        view.findViewById<TextView>(R.id.tvCurrentVersion).text = "You have: v$currentVersion"

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()

        view.findViewById<Button>(R.id.btnDownloadUpdate).setOnClickListener {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.downloadUrl)))
            } catch (e: Exception) {
                Toast.makeText(activity, "Browser not found", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnLater).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
