package com.bustime.app.ui.mdm

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MdmPasswordActivity : AppCompatActivity() {
    private lateinit var passwordInput: EditText
    private lateinit var openButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply { text = "MDM"; textSize = 24f })
        passwordInput = EditText(this).apply {
            hint = "MDM password"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; maxLines = 1
        }
        root.addView(passwordInput)
        openButton = Button(this).apply { text = "Open MDM"; setOnClickListener { verifyPassword() } }
        root.addView(openButton)
        root.addView(Button(this).apply { text = getString(com.bustime.app.R.string.back); setOnClickListener { finish() } })
        setContentView(root)
    }

    private fun verifyPassword() {
        val password = passwordInput.text.toString()
        if (password.isBlank()) { passwordInput.error = "Enter the MDM password"; return }
        if (!Constants.SUPABASE_URL.startsWith("https://") || Constants.SUPABASE_ANON_KEY.isBlank()) {
            Toast.makeText(this, "MDM server is not configured.", Toast.LENGTH_LONG).show(); return
        }
        openButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val endpoint = Constants.SUPABASE_URL.trimEnd('/') + "/functions/v1/mdm-password"
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 15000; readTimeout = 15000; doOutput = true
                        setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Cache-Control", "no-store")
                    }
                    conn.outputStream.use { it.write(JSONObject().put("action", "verify").put("password", password).toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val body = try {
                        (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    } finally { conn.disconnect() }
                    val json = JSONObject(body.ifBlank { "{}" })
                    Triple(code in 200..299 && json.optBoolean("valid"), json.optBoolean("configured"), json.optString("error"))
                } catch (e: Exception) { Triple(false, true, e.message ?: "Connection failed") }
            }
            openButton.isEnabled = true
            when {
                result.first -> startActivity(Intent(this@MdmPasswordActivity, MdmPanelActivity::class.java).putExtra(MdmPanelActivity.EXTRA_PASSWORD, password))
                !result.second -> Toast.makeText(this@MdmPasswordActivity, "Set the MDM password in the web admin Settings tab first.", Toast.LENGTH_LONG).show()
                else -> Toast.makeText(this@MdmPasswordActivity, "Incorrect password or connection error.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
