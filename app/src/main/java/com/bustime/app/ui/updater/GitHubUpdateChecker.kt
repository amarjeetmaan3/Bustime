package com.bustime.app.ui.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL

/**
 * 1. ऐप खुलते ही पॉप-अप: पिछली बार देखी नई वर्ज़न की जानकारी फ़ोन में सेव रहती है, इसलिए पॉप-अप तुरंत आता है;
 *    साथ में GitHub से ताज़ा जानकारी भी ली जाती है।
 * 2. "Download" दबाने पर फ़ोन के डाउनलोडर में बैकग्राउंड डाउनलोड शुरू होता है (ऊपर नोटिफिकेशन में दिखता है)।
 * 3. डाउनलोड पूरा होते ही ऐप में "Install" वाला पॉप-अप आता है; दबाते ही इंस्टॉलर खुल जाता है।
 */
class GitHubUpdateChecker(private val activity: AppCompatActivity) {

    private data class LatestRelease(val version: String, val downloadUrl: String, val sha256: String)

    private val prefs = activity.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    private var updateDialog: AlertDialog? = null
    private var installDialog: AlertDialog? = null
    private var receiverRegistered = false
    private var promptedThisSession = false
    private var verifyingDownload = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id != -1L && id == prefs.getLong(KEY_DL_ID, -1L)) checkPendingInstall()
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            registerReceiverIfNeeded()
            if (prefs.getBoolean(KEY_UPDATES_ENABLED, false)) checkPendingInstall()   // updater is remotely enabled
        }

        override fun onStop(owner: LifecycleOwner) {
            unregisterReceiverIfNeeded()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            updateDialog?.dismiss()
            installDialog?.dismiss()
        }
    }

    fun checkForLatestUpdate() {
        if (Constants.GITHUB_OWNER.startsWith("YOUR_")) return

        activity.lifecycle.addObserver(lifecycleObserver)
        activity.lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) { fetchUpdateNotificationsEnabled() }
            prefs.edit().putBoolean(KEY_UPDATES_ENABLED, enabled).apply()
            if (!enabled) {
                updateDialog?.dismiss()
                updateDialog = null
                installDialog?.dismiss()
                installDialog = null
                return@launch
            }

            val current = currentVersion()
            loadCached()?.let { if (isNewer(it.version, current)) onUpdateAvailable(it) }

            val latest = withContext(Dispatchers.IO) { fetchLatestRelease() } ?: return@launch
            saveCached(latest)
            if (isNewer(latest.version, current)) onUpdateAvailable(latest)
        }
    }

    /** Public read-only setting from the existing app content settings row. */
    private fun fetchUpdateNotificationsEnabled(): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(Constants.SUPABASE_URL.trimEnd('/') +
                "/rest/v1/app_content_settings?id=eq.1&select=settings")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 6000
                setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
                if (Constants.SUPABASE_ANON_KEY.startsWith("eyJ")) {
                    setRequestProperty("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
                }
            }
            if (conn.responseCode !in 200..299) return prefs.getBoolean(KEY_UPDATES_ENABLED, false)
            val rows = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (rows.length() == 0) return false
            rows.optJSONObject(0)?.optJSONObject("settings")
                ?.optBoolean("update_notifications_enabled", false) ?: false
        } catch (_: Exception) {
            prefs.getBoolean(KEY_UPDATES_ENABLED, false)
        } finally {
            conn?.disconnect()
        }
    }

    // ---------------- नया वर्ज़न मिला ----------------

    private fun onUpdateAvailable(latest: LatestRelease) {
        if (activity.isFinishing || activity.isDestroyed) return

        // इसी वर्ज़न का डाउनलोड चल रहा है/हो चुका है: दोबारा Download वाला पॉप-अप नहीं
        if (prefs.getLong(KEY_DL_ID, -1L) >= 0 && prefs.getString(KEY_DL_VERSION, null) == latest.version) {
            checkPendingInstall()
            return
        }
        if (promptedThisSession || updateDialog?.isShowing == true) return
        promptedThisSession = true
        showUpdateDialog(latest)
    }

    private fun showUpdateDialog(latest: LatestRelease) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_update, null)
        view.findViewById<TextView>(R.id.tvNewVersion).text = activity.getString(R.string.update_new_version, "v${latest.version}")
        view.findViewById<TextView>(R.id.tvCurrentVersion).text = activity.getString(R.string.update_current_version, "v${currentVersion()}")

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()

        view.findViewById<Button>(R.id.btnDownloadUpdate).setOnClickListener {
            startDownload(latest)
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnLater).setOnClickListener { dialog.dismiss() }

        updateDialog = dialog
        dialog.show()
    }

    // ---------------- डाउनलोड (फ़ोन के डाउनलोडर में, नोटिफिकेशन के साथ) ----------------

    private fun startDownload(latest: LatestRelease) {
        // APK का सीधा लिंक न मिले तो पुराना तरीका: ब्राउज़र में रिलीज़ पेज
        if (!latest.downloadUrl.endsWith(".apk") || !latest.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.downloadUrl)))
            } catch (e: Exception) {
                Toast.makeText(activity, activity.getString(R.string.update_browser_not_found), Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // पुराना अधूरा डाउनलोड और फाइल हटाओ
            val oldId = prefs.getLong(KEY_DL_ID, -1L)
            if (oldId >= 0) dm.remove(oldId)
            apkFile(latest.version)?.delete()

            val fileName = "Bustime-v${latest.version}.apk"
            val request = DownloadManager.Request(Uri.parse(latest.downloadUrl))
                .setTitle("Bustime v${latest.version}")
                .setDescription(activity.getString(R.string.update_downloading))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)

            val id = dm.enqueue(request)
            prefs.edit()
                .putLong(KEY_DL_ID, id)
                .putString(KEY_DL_VERSION, latest.version)
                .putString(KEY_DL_SHA256, latest.sha256.lowercase())
                .apply()
            Toast.makeText(activity, activity.getString(R.string.update_download_started), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.update_download_start_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- डाउनलोड पूरा होने के बाद: Install ----------------

    private fun checkPendingInstall() {
        val id = prefs.getLong(KEY_DL_ID, -1L)
        if (id < 0) return
        val version = prefs.getString(KEY_DL_VERSION, null)
        if (version == null) {
            clearPending()
            return
        }

        // अपडेट इंस्टॉल हो चुका (या वर्ज़न पुराना है): सफ़ाई
        if (!isNewer(version, currentVersion())) {
            apkFile(version)?.delete()
            clearPending()
            return
        }

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var status = -1
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (c.moveToFirst()) {
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
        }

        when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                if (verifyingDownload) return
                verifyingDownload = true
                activity.lifecycleScope.launch {
                    val valid = withContext(Dispatchers.IO) { verifyDownloadedApk(version) }
                    verifyingDownload = false
                    if (valid) {
                        showInstallDialog(version)
                    } else {
                        apkFile(version)?.delete()
                        clearPending()
                        Toast.makeText(activity, "Update verification failed. The APK was removed.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            DownloadManager.STATUS_FAILED -> {
                clearPending()
                Toast.makeText(activity, activity.getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
            }
            -1 -> clearPending()   // यूज़र ने डाउनलोड हटा दिया
            else -> Unit           // अभी चल रहा है
        }
    }

    private fun showInstallDialog(version: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (installDialog?.isShowing == true) return
        updateDialog?.dismiss()

        installDialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_ready))
            .setMessage(activity.getString(R.string.update_install_prompt, version))
            .setCancelable(true)
            .setPositiveButton(activity.getString(R.string.install)) { _, _ -> installApk(version) }
            .setNegativeButton(activity.getString(R.string.later), null)
            .create()
        installDialog?.show()
    }

    private fun installApk(version: String) {
        // Android 8+: "इस ऐप से इंस्टॉल की अनुमति" एक बार देनी पड़ती है
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_permission_needed))
                .setMessage(activity.getString(R.string.update_permission_help))
                .setPositiveButton(activity.getString(R.string.open_settings)) { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    )
                }
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show()
            return
        }

        val file = apkFile(version)
        if (file == null || !file.exists()) {
            clearPending()
            Toast.makeText(activity, activity.getString(R.string.update_file_missing), Toast.LENGTH_LONG).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.update_installer_failed), Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- मदद करने वाले ----------------

    private fun apkFile(version: String): File? {
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return File(dir, "Bustime-v$version.apk")
    }

    private fun clearPending() {
        prefs.edit().remove(KEY_DL_ID).remove(KEY_DL_VERSION).remove(KEY_DL_SHA256).apply()
    }

    /** Verify GitHub's published SHA-256 digest before allowing Android's installer to open the APK. */
    private fun verifyDownloadedApk(version: String): Boolean {
        val expected = prefs.getString(KEY_DL_SHA256, null)?.lowercase() ?: return false
        if (!expected.matches(Regex("^[a-f0-9]{64}$"))) return false
        val file = apkFile(version) ?: return false
        if (!file.isFile || file.length() <= 0L) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            activity,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterReceiverIfNeeded() {
        if (!receiverRegistered) return
        try {
            activity.unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // पहले ही हट चुका
        }
        receiverRegistered = false
    }

    private fun loadCached(): LatestRelease? {
        val v = prefs.getString(KEY_LATEST_VERSION, null) ?: return null
        val u = prefs.getString(KEY_LATEST_URL, null) ?: return null
        val digest = prefs.getString(KEY_LATEST_SHA256, null) ?: ""
        return LatestRelease(v, u, digest)
    }

    private fun saveCached(latest: LatestRelease) {
        prefs.edit()
            .putString(KEY_LATEST_VERSION, latest.version)
            .putString(KEY_LATEST_URL, latest.downloadUrl)
            .putString(KEY_LATEST_SHA256, latest.sha256)
            .apply()
    }

    private fun fetchLatestRelease(): LatestRelease? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.github.com/repos/${Constants.GITHUB_OWNER}/${Constants.GITHUB_REPO}/releases/latest")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Bustime-Android")
            }
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            if (tag.isEmpty()) return null

            val releasePage = json.optString("html_url")
            var apkUrl = releasePage
            var sha256 = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        val candidateDigest = asset.optString("digest").removePrefix("sha256:").lowercase()
                        if (candidateDigest.matches(Regex("^[a-f0-9]{64}$"))) {
                            apkUrl = asset.optString("browser_download_url")
                            sha256 = candidateDigest
                        }
                        break
                    }
                }
            }
            // Without a published digest, open the release page instead of installing an unverified APK.
            LatestRelease(tag.removePrefix("v"), apkUrl, sha256)
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

    companion object {
        private const val KEY_UPDATES_ENABLED = "update_notifications_enabled"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_LATEST_URL = "latest_url"
        private const val KEY_LATEST_SHA256 = "latest_sha256"
        private const val KEY_DL_ID = "download_id"
        private const val KEY_DL_VERSION = "download_version"
        private const val KEY_DL_SHA256 = "download_sha256"
    }
}
