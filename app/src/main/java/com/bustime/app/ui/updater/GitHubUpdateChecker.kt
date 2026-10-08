package com.bustime.app.ui.updater

import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.bustime.app.R

class GitHubUpdateChecker(private val context: Context) {
    
    fun checkForLatestUpdate() {
        // फोन में इंस्टॉल हुए ऐप का वर्ज़न पता करना
        val currentVersion = getCurrentAppVersion()
        
        // अभी टेस्टिंग के लिए मान लेते हैं कि गिटहब पर नया वर्ज़न "1.0.5" है
        // (भविष्य में यह "1.0.5" सीधा गिटहब API से आएगा)
        val latestVersionOnGithub = "1.0.5" 

        // अगर हमारा वर्ज़न और गिटहब का वर्ज़न अलग है, तभी पॉप-अप दिखाओ
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
        
        val tvNewVersion = view.findViewById<TextView>(R.id.tvNewVersion)
        val tvCurrentVersion = view.findViewById<TextView>(R.id.tvCurrentVersion)
        val btnDownload = view.findViewById<Button>(R.id.btnDownloadUpdate)

        // टेक्स्ट सेट करना
        tvNewVersion.text = "New Version: v$newVersion"
        tvCurrentVersion.text = "You have: v$currentVersion"

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        btnDownload.setOnClickListener {
            dialog.dismiss() 
        }

        dialog.show()
    }
}
