package com.bustime.app.ui.mdm

import com.bustime.app.utils.LanguageHelper

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.widget.ScrollView
import com.bustime.app.ui.updater.GitHubUpdateChecker
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = LanguageHelper.current()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply {
            text = when (language) { "hi" -> "सेटिंग्स"; "pa" -> "ਸੈਟਿੰਗਾਂ"; else -> "Settings" }
            textSize = 24f
        })

        val updater = GitHubUpdateChecker(this)
        val currentVersion = TextView(this).apply {
            textSize = 16f
            text = updaterLabel(language) + updater.currentInstalledVersion()
            setPadding(0, 20, 0, 8)
        }
        val latestStatus = TextView(this).apply {
            textSize = 15f
            text = when (language) { "hi" -> "नया वर्ज़न चेक करने के लिए नीचे दबाएँ।"; "pa" -> "ਨਵਾਂ ਵਰਜਨ ਚੈੱਕ ਕਰਨ ਲਈ ਹੇਠਾਂ ਦਬਾਓ।"; else -> "Check for the latest version below." }
            setPadding(0, 4, 0, 8)
        }
        val releaseNotes = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, 8); visibility = View.GONE }
        var latestInfo: GitHubUpdateChecker.ManualUpdateInfo? = null
        val checkButton = Button(this).apply {
            text = when (language) { "hi" -> "अपडेट चेक करें"; "pa" -> "ਅੱਪਡੇਟ ਚੈੱਕ ਕਰੋ"; else -> "Check for Updates" }
        }
        val downloadButton = Button(this).apply {
            text = when (language) { "hi" -> "अभी डाउनलोड करें"; "pa" -> "ਹੁਣੇ ਡਾਊਨਲੋਡ ਕਰੋ"; else -> "Download Now" }
            visibility = View.GONE
        }
        fun checkUpdates() {
            checkButton.isEnabled = false
            latestStatus.text = when (language) { "hi" -> "नया वर्ज़न चेक हो रहा है…"; "pa" -> "ਨਵਾਂ ਵਰਜਨ ਚੈੱਕ ਹੋ ਰਿਹਾ ਹੈ…"; else -> "Checking for the latest version…" }
            latestInfo = null
            downloadButton.visibility = View.GONE
            releaseNotes.visibility = View.GONE
            updater.checkForUpdatesManually { installed, latest, error ->
                currentVersion.text = updaterLabel(language) + installed
                checkButton.isEnabled = true
                if (error != null) {
                    latestStatus.text = error
                    return@checkForUpdatesManually
                }
                latestInfo = latest
                if (latest == null) {
                    latestStatus.text = when (language) { "hi" -> "अपडेट की जानकारी उपलब्ध नहीं है।"; "pa" -> "ਅੱਪਡੇਟ ਦੀ ਜਾਣਕਾਰੀ ਉਪਲਬਧ ਨਹੀਂ ਹੈ।"; else -> "Update information is unavailable." }
                    return@checkForUpdatesManually
                }
                val newer = isVersionNewer(latest.version, installed)
                latestStatus.text = if (newer) {
                    when (language) { "hi" -> "नया वर्ज़न उपलब्ध है: v${latest.version}"; "pa" -> "ਨਵਾਂ ਵਰਜਨ ਉਪਲਬਧ ਹੈ: v${latest.version}"; else -> "New version available: v${latest.version}" }
                } else {
                    when (language) { "hi" -> "आप नवीनतम वर्ज़न इस्तेमाल कर रहे हैं (v${latest.version})।"; "pa" -> "ਤੁਸੀਂ ਨਵਾਂ ਵਰਜਨ ਵਰਤ ਰਹੇ ਹੋ (v${latest.version})।"; else -> "You're using the latest version (v${latest.version})." }
                }
                if (latest.notes.isNotBlank()) {
                    releaseNotes.text = latest.notes
                    releaseNotes.visibility = View.VISIBLE
                }
                downloadButton.visibility = if (newer) View.VISIBLE else View.GONE
                downloadButton.isEnabled = newer
                if (newer && !latest.hasVerifiedApk) {
                    latestStatus.text = latestStatus.text.toString() + when (language) {
                        "hi" -> "\nइस रिलीज़ में सत्यापित APK सीधे उपलब्ध नहीं है; डाउनलोड के लिए रिलीज़ पेज खुलेगा।"
                        "pa" -> "\nਇਸ ਰਿਲੀਜ਼ ਵਿੱਚ ਤਸਦੀਕ ਕੀਤਾ APK ਸਿੱਧਾ ਉਪਲਬਧ ਨਹੀਂ; ਡਾਊਨਲੋਡ ਲਈ ਰਿਲੀਜ਼ ਪੇਜ ਖੁੱਲ੍ਹੇਗਾ।"
                        else -> "\nA verified direct APK is unavailable; the release page will open."
                    }
                }
            }
        }
        checkButton.setOnClickListener { checkUpdates() }
        downloadButton.setOnClickListener { latestInfo?.let(updater::downloadUpdateManually) }
        root.addView(TextView(this).apply {
            text = when (language) { "hi" -> "ऐप अपडेटर"; "pa" -> "ਐਪ ਅੱਪਡੇਟਰ"; else -> "App Updater" }
            textSize = 20f
            setPadding(0, 20, 0, 4)
        })
        root.addView(currentVersion)
        root.addView(latestStatus)
        root.addView(releaseNotes)
        root.addView(checkButton)
        root.addView(downloadButton)
        root.addView(Button(this).apply {
            text = when (language) { "hi" -> "MDM एडमिन"; "pa" -> "MDM ਐਡਮਿਨ"; else -> "MDM" }
            setOnClickListener { startActivity(android.content.Intent(this@SettingsActivity, MdmPasswordActivity::class.java)) }
        })
        root.addView(Button(this).apply { text = getString(com.bustime.app.R.string.back); setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        checkUpdates()
    }
    private fun updaterLabel(language: String): String = when (language) {
        "hi" -> "वर्तमान वर्ज़न: "
        "pa" -> "ਮੌਜੂਦਾ ਵਰਜਨ: "
        else -> "Current version: "
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
