package com.bustime.app.ui.mdm

import com.bustime.app.utils.LanguageHelper
import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

/** Settings landing page. Update checking opens a dedicated version-details screen. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = LanguageHelper.current()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = when (language) { "hi" -> "सेटिंग्स"; "pa" -> "ਸੈਟਿੰਗਾਂ"; else -> "Settings" }
            textSize = 24f
        })
        root.addView(Button(this).apply {
            text = when (language) { "hi" -> "अपडेट चेक करें"; "pa" -> "ਅੱਪਡੇਟ ਚੈੱਕ ਕਰੋ"; else -> "Check Updates" }
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, UpdateDetailsActivity::class.java))
            }
        })
        root.addView(Button(this).apply {
            text = when (language) { "hi" -> "MDM एडमिन"; "pa" -> "MDM ਐਡਮਿਨ"; else -> "MDM Admin" }
            setOnClickListener { startActivity(Intent(this@SettingsActivity, MdmPasswordActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = getString(com.bustime.app.R.string.back)
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
