package com.bustime.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.bustime.app.R

object ContactActions {

    // डायलर खोलता है (CALL_PHONE परमिशन नहीं चाहिए)
    fun dial(context: Context, phone: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
        } catch (e: Exception) {
            Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // 10 अंकों का नंबर हो तो भारत का कोड (91) जोड़ देता है
    fun whatsapp(context: Context, phone: String) {
        val digits = phone.filter { it.isDigit() }
        val number = when {
            digits.length == 10 -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91" + digits.substring(1)
            else -> digits
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")))
        } catch (e: Exception) {
            Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
