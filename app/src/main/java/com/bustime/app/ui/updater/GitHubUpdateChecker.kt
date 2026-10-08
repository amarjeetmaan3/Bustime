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
import java.net.HttpURLConnection
import java.net.URL

/**
 * 1. ऐप खुलते ही पॉप-अप: पिछली बार देखी नई वर्ज़न की जानकारी फ़ोन में सेव रहती है, इसलिए पॉप-अप तुरंत आता है;
 *    साथ में GitHub से ताज़ा जानकारी भी ली जाती है।
 * 2. "Download" दबाने पर फ़ोन के डाउनलोडर में बैकग्राउंड डाउनलोड शुरू होता है (ऊपर नोटिफिकेशन में दिखता है)।
 * 3. डाउनलोड पूरा होते ही ऐप में "Install" वाला पॉप-अप आता है; दबाते ही इंस्टॉलर खुल जाता है।
 */
class GitHubUpdateChecker(private val activity: AppCompatActivity) {

    private data class LatestRelease(val version: String, val downloadUrl: String)

    private val prefs = activity.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    private var updateDialog: AlertDialog? = null
    private var installDialog: AlertDialog? = null
    private var receiverRegistered = false
    private var promptedThisSession = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id != -1L && id == prefs.getLong(KEY_DL_ID, -1L)) checkPendingInstall()
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            registerReceiverIfNeeded()
            checkPendingInstall()   // डाउनलोड बैकग्राउंड में पूरा हो गया हो, या सेटिंग से लौटे हों
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
        // username भरा नहीं है तो चेक ही मत करो
        if (Constants.GITHUB_OWNER.startsWith("YOUR_")) return

        activity.lifecycle.addObserver(lifecycleObserver)
        val current = currentVersion()

        // तुरंत: पिछली बार जो नया वर्ज़न दिखा था वो फ़ोन में सेव है
        loadCached()?.let { if (isNewer(it.version, current)) onUpdateAvailable(it) }

        // फिर ताज़ा जानकारी (बैकग्राउंड में)
        activity.lifecycleScope.launch {
            val latest = withContext(Dispatchers.IO) { fetchLatestRelease() } ?: return@launch
            saveCached(latest)
            if (isNewer(latest.version, current)) onUpdateAvailable(latest)
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
        view.findViewById<TextView>(R.id.tvNewVersion).text = "New Version: v${latest.version}"
        view.findViewById<TextView>(R.id.tvCurrentVersion).text = "You have: v${currentVersion()}"

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
        if (!latest.downloadUrl.endsWith(".apk")) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.downloadUrl)))
            } catch (e: Exception) {
                Toast.makeText(activity, "Browser not found", Toast.LENGTH_SHORT).show()
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
                .setDescription("Downloading update")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)

            val id = dm.enqueue(request)
            prefs.edit()
                .putLong(KEY_DL_ID, id)
                .putString(KEY_DL_VERSION, latest.version)
                .apply()
            Toast.makeText(activity, "Download started. See the notification.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not start download", Toast.LENGTH_SHORT).show()
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
            DownloadManager.STATUS_SUCCESSFUL -> showInstallDialog(version)
            DownloadManager.STATUS_FAILED -> {
                clearPending()
                Toast.makeText(activity, "Download failed. Please try again.", Toast.LENGTH_LONG).show()
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
            .setTitle("Update ready")
            .setMessage("Bustime v$version is downloaded. Install now?")
            .setCancelable(true)
            .setPositiveButton("Install") { _, _ -> installApk(version) }
            .setNegativeButton("Later", null)
            .create()
        installDialog?.show()
    }

    private fun installApk(version: String) {
        // Android 8+: "इस ऐप से इंस्टॉल की अनुमति" एक बार देनी पड़ती है
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Permission needed")
                .setMessage("Allow Bustime to install updates, then come back to the app.")
                .setPositiveButton("Open settings") { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val file = apkFile(version)
        if (file == null || !file.exists()) {
            clearPending()
            Toast.makeText(activity, "Update file not found. Please download again.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not open installer", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- मदद करने वाले ----------------

    private fun apkFile(version: String): File? {
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return File(dir, "Bustime-v$version.apk")
    }

    private fun clearPending() {
        prefs.edit().remove(KEY_DL_ID).remove(KEY_DL_VERSION).apply()
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
        return LatestRelease(v, u)
    }

    private fun saveCached(latest: LatestRelease) {
        prefs.edit()
            .putString(KEY_LATEST_VERSION, latest.version)
            .putString(KEY_LATEST_URL, latest.downloadUrl)
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

    companion object {
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_LATEST_URL = "latest_url"
        private const val KEY_DL_ID = "download_id"
        private const val KEY_DL_VERSION = "download_version"
    }
}
