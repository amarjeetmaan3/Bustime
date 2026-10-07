package com.bustime.app.ui.details

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri

class ContactDriverHelper(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun playDisclaimerAndShowNumber(driverNumber: String, onVoiceFinished: () -> Unit) {
        // AI वॉइस प्ले करें (raw फोल्डर से)
        mediaPlayer = MediaPlayer.create(context, com.bustime.app.R.raw.ai_voice_disclaimer)
        mediaPlayer?.setOnCompletionListener {
            it.release()
            onVoiceFinished() // 3-5 सेकंड बाद नंबर दिखाने के लिए कॉलबैक
        }
        mediaPlayer?.start()
    }

    fun callDriver(number: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$number")
        context.startActivity(intent)
    }

    fun openWhatsApp(number: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://wa.me/$number")
        context.startActivity(intent)
    }
}
