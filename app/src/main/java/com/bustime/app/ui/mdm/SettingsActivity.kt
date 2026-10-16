package com.bustime.app.ui.mdm

import com.bustime.app.utils.LanguageHelper

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply { text = when (LanguageHelper.current()) { "hi" -> "सेटिंग्स"; "pa" -> "ਸੈਟਿੰਗਾਂ"; else -> "Settings" }; textSize = 24f })
        root.addView(Button(this).apply {
            text = when (LanguageHelper.current()) { "hi" -> "MDM एडमिन"; "pa" -> "MDM ਐਡਮਿਨ"; else -> "MDM" }
            setOnClickListener { startActivity(android.content.Intent(this@SettingsActivity, MdmPasswordActivity::class.java)) }
        })
        root.addView(Button(this).apply { text = getString(com.bustime.app.R.string.back); setOnClickListener { finish() } })
        setContentView(root)
    }
}
