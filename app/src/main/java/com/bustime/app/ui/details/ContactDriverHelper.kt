package com.bustime.app.ui.details

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

class ContactDriverHelper(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        // AI Voice (TTS) इंजन को स्टार्ट करना
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // हम इंग्लिश (US) की आवाज़ सेट कर रहे हैं, आप चाहें तो Locale("hi", "IN") से हिंदी भी कर सकते हैं
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
            }
        }
    }

    fun playDisclaimerAndShowNumber(driverName: String, driverNumber: String) {
        // यह वह मैसेज है जो AI बोलकर सुनाएगा
        val disclaimerText = "Connecting you to $driverName. Please note, fares are fixed. Do not negotiate."
        
        if (isInitialized) {
            // आवाज़ प्ले करें
            tts?.speak(disclaimerText, TextToSpeech.QUEUE_FLUSH, null, null)
            Toast.makeText(context, "Playing AI Disclaimer...", Toast.LENGTH_SHORT).show()
            
            // (भविष्य का काम: यहाँ हम आवाज़ खत्म होने के बाद नंबर स्क्रीन पर दिखाएंगे या डायलर खोलेंगे)
            
        } else {
            Toast.makeText(context, "Voice engine is loading, please try again...", Toast.LENGTH_SHORT).show()
        }
    }
    
    // जब ऐप बंद हो तो वॉइस इंजन को रोकना ज़रूरी है
    fun stopVoice() {
        tts?.stop()
        tts?.shutdown()
    }
}
