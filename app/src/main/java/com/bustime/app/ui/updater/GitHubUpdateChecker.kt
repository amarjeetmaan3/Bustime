package com.bustime.app.ui.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.bustime.app.R

class GitHubUpdateChecker(private val context: Context) {
    
    fun checkForLatestUpdate() {
        val currentVersion = getCurrentAppVersion()
        // अभी के लिए हम इसे अलग रख रहे हैं ताकि पॉप-अप हमेशा दिखे और तुम बटन टेस्ट कर सको।
        val latestVersionOnGithub = "1.0.18" 

        if (currentVersion != latestVersionOnGithub) {
            showUpdateDialog(currentVersion, latestVersionOnGithub)
        }
    }

    private fun getCurrentAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }

    private fun showUpdateDialog(currentVersion: String, newVersion: String) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_update, null)
        
        view.findViewById<TextView>(R.id.tvNewVersion).text = "New Version: v$newVersion"
        view.findViewById<TextView>(R.id.tvCurrentVersion).text = "You have: v$currentVersion"

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<Button>(R.id.btnDownloadUpdate).setOnClickListener {
            // यह कोड तुम्हारे फोन के ब्राउज़र में सीधा गिटहब का डाउनलोड पेज खोल देगा
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/YOUR_GITHUB_USERNAME/Bustime/releases/latest"))
            context.startActivity(intent)
            dialog.dismiss() 
        }

        dialog.show()
    }
}
