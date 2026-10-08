package com.bustime.app.ui.updater

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.bustime.app.R

class GitHubUpdateChecker(private val context: Context) {
    
    fun checkForLatestUpdate() {
        // अभी के लिए हम हर बार ऐप खुलने पर पॉप-अप दिखा रहे हैं ताकि आप टेस्टिंग कर सकें।
        // बाद में हम इसमें GitHub API जोड़ेंगे जो सिर्फ नया वर्ज़न आने पर ही इसे दिखाएगा।
        showUpdateDialog()
    }

    private fun showUpdateDialog() {
        // XML डिज़ाइन को कोड में लोड करना
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_update, null)
        
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false) // इसे false किया है ताकि यूज़र बाहर टच करके इसे हटा न सके
            .create()

        // डाउनलोड बटन पर क्लिक करने का एक्शन
        val btnDownload = view.findViewById<Button>(R.id.btnDownloadUpdate)
        btnDownload.setOnClickListener {
            // अभी के लिए बस डायलॉग बंद कर रहे हैं, बाद में यहाँ डाउनलोड का लिंक खुलेगा
            dialog.dismiss() 
        }

        dialog.show()
    }
}
