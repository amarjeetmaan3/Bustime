package com.bustime.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.bustime.app.data.RemoteContentSettings
import com.bustime.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchGateActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE) }
    private var selected = "en"
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = prefs.getString("preferred_language", null) ?: "en"
        if (prefs.getBoolean("terms_accepted_v1", false)) {
            applyLanguage(selected)
            openHome()
            return
        }
        showLanguagePage()
    }

    private fun base(title: String, subtitle: String? = null): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = title; textSize = 26f; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(8))
        })
        if (!subtitle.isNullOrBlank()) root.addView(TextView(this).apply {
            text = subtitle; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(18))
        })
        return root
    }

    private fun showLanguagePage() {
        base("Welcome to Bustime", "Choose your preferred language / अपनी भाषा चुनें / ਆਪਣੀ ਭਾਸ਼ਾ ਚੁਣੋ")
        listOf("en" to "English", "hi" to "हिन्दी", "pa" to "ਪੰਜਾਬੀ").forEach { (code, label) ->
            root.addView(Button(this).apply {
                text = label; isAllCaps = false
                setOnClickListener {
                    selected = code
                    prefs.edit().putString("preferred_language", code).apply()
                    showTermsPage()
                }
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
        }
    }

    private fun showTermsPage() {
        base(when(selected) { "hi" -> "नियम और महत्वपूर्ण सूचना"; "pa" -> "ਨਿਯਮ ਅਤੇ ਮਹੱਤਵਪੂਰਨ ਜਾਣਕਾਰੀ"; else -> "Terms & Conditions" })
        val loading = TextView(this).apply { text = "Loading information…"; textSize = 15f; setPadding(0, dp(8), 0, dp(12)) }
        root.addView(loading)
        val privacyLink = TextView(this).apply {
            text = getString(com.bustime.app.R.string.privacy_policy_link_label)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(21, 101, 192))
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(8), dp(12), dp(8), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@LaunchGateActivity, com.bustime.app.ui.privacy.PrivacyPolicyActivity::class.java))
            }
        }
        root.addView(privacyLink, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        val agree = CheckBox(this).apply {
            text = when(selected) {
                "hi" -> "मैंने दोनों भाषाओं में सूचना पढ़ ली है और सहमत हूँ।"
                "pa" -> "ਮੈਂ ਦੋਵੇਂ ਭਾਸ਼ਾਵਾਂ ਵਿੱਚ ਜਾਣਕਾਰੀ ਪੜ੍ਹ ਲਈ ਹੈ ਅਤੇ ਸਹਿਮਤ ਹਾਂ।"
                else -> "I have read and agree to the information in both languages."
            }
        }
        agree.isEnabled = false
        root.addView(agree)
        val next = Button(this).apply {
            text = when(selected) { "hi" -> "सहमत हूँ और आगे बढ़ें"; "pa" -> "ਸਹਿਮਤ ਹਾਂ ਅਤੇ ਅੱਗੇ ਵਧੋ"; else -> "Agree & Continue" }
            isAllCaps = false; isEnabled = false
            setOnClickListener {
                if (!agree.isChecked) return@setOnClickListener
                prefs.edit().putBoolean("terms_accepted_v1", true).putString("terms_version", "1").apply()
                openHome()
            }
        }
        root.addView(next, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
        lifecycleScope.launch {
            val settings = withContext(Dispatchers.IO) { RemoteContentSettings.refresh() }
            val langs = RemoteContentSettings.pairedLanguages(selected)
            val defaultText = mapOf(
                "en" to "Bustime is for information only. Bus timings, routes, vehicle availability and driver details may change or contain errors. Verify details with the relevant service before travelling. Bustime does not guarantee availability or service and is not responsible for decisions made using this information.",
                "hi" to "Bustime केवल जानकारी के लिए है। बस के समय, रूट, वाहन की उपलब्धता और ड्राइवर की जानकारी बदल सकती है या गलत हो सकती है। यात्रा से पहले संबंधित सेवा से पुष्टि करें। Bustime उपलब्धता या सेवा की गारंटी नहीं देता और इस जानकारी के आधार पर लिए गए निर्णयों की जिम्मेदारी नहीं लेता।",
                "pa" to "Bustime ਸਿਰਫ਼ ਜਾਣਕਾਰੀ ਲਈ ਹੈ। ਬੱਸ ਦੇ ਸਮੇਂ, ਰੂਟ, ਵਾਹਨ ਦੀ ਉਪਲਬਧਤਾ ਅਤੇ ਡਰਾਈਵਰ ਦੀ ਜਾਣਕਾਰੀ ਬਦਲ ਸਕਦੀ ਹੈ ਜਾਂ ਗਲਤ ਹੋ ਸਕਦੀ ਹੈ। ਯਾਤਰਾ ਤੋਂ ਪਹਿਲਾਂ ਸੰਬੰਧਿਤ ਸੇਵਾ ਨਾਲ ਪੁਸ਼ਟੀ ਕਰੋ। Bustime ਉਪਲਬਧਤਾ ਜਾਂ ਸੇਵਾ ਦੀ ਗਾਰੰਟੀ ਨਹੀਂ ਦਿੰਦਾ।"
            )
            val body = if (settings.termsEnabled) langs.joinToString("\n\n━━━━━━━━━━━━\n\n") { lang ->
                val text = settings.terms[lang]?.takeIf { it.isNotBlank() } ?: defaultText.getValue(lang)
                val label = when(lang) { "hi" -> "हिन्दी"; "pa" -> "ਪੰਜਾਬੀ"; else -> "English" }
                "$label\n$text"
            } else langs.joinToString("\n\n━━━━━━━━━━━━\n\n") { lang -> val label = when(lang) { "hi" -> "हिन्दी"; "pa" -> "ਪੰਜਾਬੀ"; else -> "English" }; "$label\n" + defaultText.getValue(lang) }
            loading.text = body
            agree.isEnabled = true
            next.isEnabled = agree.isChecked
            agree.setOnCheckedChangeListener { _, checked -> next.isEnabled = checked }
        }
    }

    private fun applyLanguage(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }
    private fun openHome() { applyLanguage(selected); startActivity(Intent(this, HomeActivity::class.java)); finish() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}