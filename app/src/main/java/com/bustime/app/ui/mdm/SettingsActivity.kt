package com.bustime.app.ui.mdm

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply { text = "Settings"; textSize = 24f })
        root.addView(TextView(this).apply { text = "App settings and administration"; textSize = 15f; setPadding(0, 8, 0, 20) })
        root.addView(Button(this).apply {
            text = "MDM"
            setOnClickListener { startActivity(android.content.Intent(this@SettingsActivity, MdmPasswordActivity::class.java)) }
        })
        root.addView(Button(this).apply { text = getString(com.bustime.app.R.string.back); setOnClickListener { finish() } })
        setContentView(root)
    }
}
