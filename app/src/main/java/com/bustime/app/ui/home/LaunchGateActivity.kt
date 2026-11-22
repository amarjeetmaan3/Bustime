package com.bustime.app.ui.home

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.RemoteContentSettings
import com.bustime.app.ui.common.applyBouncyPress
import com.bustime.app.utils.Constants
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchGateActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE) }
    private var selected = "en"
    private lateinit var root: LinearLayout

    private val bodyFont: Typeface? by lazy { ResourcesCompat.getFont(this, R.font.plus_jakarta_sans) }

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

    private fun base(title: String, subtitle: String? = null, showHeroIllustration: Boolean = true): LinearLayout {
        val pageBg = ContextCompat.getColor(this, R.color.bgScreen)
        val textPrimary = ContextCompat.getColor(this, R.color.textPrimary)
        val textSecondary = ContextCompat.getColor(this, R.color.textSecondary)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }

        if (showHeroIllustration) {
            val heroCard = MaterialCardView(this).apply {
                radius = dp(24).toFloat()
                cardElevation = dp(5).toFloat()
                strokeColor = ContextCompat.getColor(this@LaunchGateActivity, R.color.divider)
                strokeWidth = dp(1)
            }
            heroCard.addView(ImageView(this).apply {
                setImageResource(R.drawable.img_welcome_hero)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = getString(R.string.app_name)
            }, LinearLayout.LayoutParams(-1, dp(190)))
            container.addView(heroCard, LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin = dp(20) })
        }

        container.addView(TextView(this).apply {
            text = title
            textSize = 26f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            setTextColor(textPrimary)
            gravity = Gravity.CENTER
        })

        if (!subtitle.isNullOrBlank()) {
            container.addView(TextView(this).apply {
                text = subtitle
                textSize = 14f
                typeface = bodyFont
                setTextColor(textSecondary)
                gravity = Gravity.CENTER
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(dp(8), dp(8), dp(8), dp(18))
            })
        } else {
            container.addView(View(this), LinearLayout.LayoutParams(-1, dp(14)))
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0f
            translationY = dp(16).toFloat()
        }
        container.addView(root, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(pageBg)
            isFillViewport = true
            addView(container)
        }
        setContentView(scroll)
        root.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()
        return root
    }

    private fun showLanguagePage() {
        base(
            "Welcome to Bustime",
            "Choose your preferred language\nअपनी भाषा चुनें  •  ਆਪਣੀ ਭਾਸ਼ਾ ਚੁਣੋ",
            showHeroIllustration = true
        )
        val surface = ContextCompat.getColor(this, R.color.surface)
        val divider = ContextCompat.getColor(this, R.color.divider)
        val primary = ContextCompat.getColor(this, R.color.colorPrimary)
        val primaryContainer = ContextCompat.getColor(this, R.color.colorPrimaryContainer)
        val saffron = ContextCompat.getColor(this, R.color.colorAccent)
        val textPrimary = ContextCompat.getColor(this, R.color.textPrimary)
        val textSecondary = ContextCompat.getColor(this, R.color.textSecondary)

        val options = listOf(
            Triple("en", "English", "Aa" to "Continue in English"),
            Triple("hi", "हिन्दी", "अ" to "हिन्दी में जारी रखें"),
            Triple("pa", "ਪੰਜਾਬੀ", "ਅ" to "ਪੰਜਾਬੀ ਵਿੱਚ ਜਾਰੀ ਰੱਖੋ")
        )

        val checkViews = mutableMapOf<String, ImageView>()
        val rowShapes = mutableMapOf<String, GradientDrawable>()

        fun updateSelectionVisuals(animateCode: String? = null) {
            options.forEach { (code, _, _) ->
                val isSelected = code == selected
                rowShapes[code]?.setStroke(if (isSelected) dp(2) else dp(1), if (isSelected) saffron else divider)
                val check = checkViews[code] ?: return@forEach
                if (isSelected) {
                    check.visibility = View.VISIBLE
                    if (animateCode == code) {
                        check.scaleX = 0.2f
                        check.scaleY = 0.2f
                        check.alpha = 0f
                        check.animate().scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(260).setInterpolator(OvershootInterpolator(2.4f)).start()
                    }
                } else {
                    check.visibility = View.INVISIBLE
                }
            }
        }

        options.forEach { (code, label, meta) ->
            val cardShape = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), divider)
            }
            rowShapes[code] = cardShape
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
                background = RippleDrawable(ColorStateList.valueOf(primaryContainer), cardShape, null)
                elevation = dp(2).toFloat()
                isClickable = true
                isFocusable = true
                applyBouncyPress()
                setOnClickListener {
                    selected = code
                    prefs.edit().putString("preferred_language", code).apply()
                    updateSelectionVisuals(animateCode = code)
                    postDelayed({ showTermsPage() }, 210L)
                }
            }

            val scriptBadge = TextView(this).apply {
                text = meta.first
                textSize = 18f
                typeface = Typeface.create(bodyFont, Typeface.BOLD)
                setTextColor(primary)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(primaryContainer)
                    cornerRadius = dp(14).toFloat()
                }
            }
            row.addView(scriptBadge, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(14) })

            val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textCol.addView(TextView(this).apply {
                text = label
                textSize = 18f
                typeface = Typeface.create(bodyFont, Typeface.BOLD)
                setTextColor(textPrimary)
            })
            textCol.addView(TextView(this).apply {
                text = meta.second
                textSize = 13f
                typeface = bodyFont
                setTextColor(textSecondary)
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))

            val checkIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_check_circle)
                imageTintList = ColorStateList.valueOf(saffron)
                contentDescription = label
                visibility = View.INVISIBLE
            }
            checkViews[code] = checkIcon
            row.addView(checkIcon, LinearLayout.LayoutParams(dp(26), dp(26)))

            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }

        updateSelectionVisuals()
    }

    private fun showTermsPage() {
        base(
            when (selected) {
                "hi" -> "नियम और महत्वपूर्ण सूचना"
                "pa" -> "ਨਿਯਮ ਅਤੇ ਮਹੱਤਵਪੂਰਨ ਜਾਣਕਾਰੀ"
                else -> "Terms & Conditions"
            },
            showHeroIllustration = false
        )
        val surface = ContextCompat.getColor(this, R.color.surface)
        val divider = ContextCompat.getColor(this, R.color.divider)
        val textPrimary = ContextCompat.getColor(this, R.color.textPrimary)
        val primary = ContextCompat.getColor(this, R.color.colorPrimary)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), divider)
            }
            elevation = dp(3).toFloat()
        }

        val loading = TextView(this).apply {
            text = "Loading information…"
            textSize = 14.5f
            typeface = bodyFont
            setTextColor(textPrimary)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(2), 0, dp(10))
        }
        card.addView(loading)

        card.addView(View(this).apply { setBackgroundColor(divider) }, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })

        val privacyLink = TextView(this).apply {
            text = getString(R.string.privacy_policy_link_label)
            textSize = 14.5f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(primary)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(8), dp(10), dp(8), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@LaunchGateActivity, com.bustime.app.ui.privacy.PrivacyPolicyActivity::class.java))
            }
        }
        card.addView(privacyLink, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val agreeCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), divider)
            }
        }
        val agree = CheckBox(this).apply {
            text = when (selected) {
                "hi" -> "मैंने दोनों भाषाओं में सूचना पढ़ ली है और सहमत हूँ।"
                "pa" -> "ਮੈਂ ਦੋਵੇਂ ਭਾਸ਼ਾਵਾਂ ਵਿੱਚ ਜਾਣਕਾਰੀ ਪੜ੍ਹ ਲਈ ਹੈ ਅਤੇ ਸਹਿਮਤ ਹਾਂ।"
                else -> "I have read and agree to the information in both languages."
            }
            textSize = 14f
            typeface = bodyFont
            setTextColor(textPrimary)
            buttonTintList = ColorStateList.valueOf(ContextCompat.getColor(this@LaunchGateActivity, R.color.colorAccent))
        }
        agree.isEnabled = false
        agreeCard.addView(agree, LinearLayout.LayoutParams(-1, -2))
        root.addView(agreeCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        // Full-width Saffron Gradient Continue CTA Pill
        val next = TextView(this).apply {
            text = when (selected) {
                "hi" -> "सहमत हूँ और आगे बढ़ें"
                "pa" -> "ਸਹਿਮਤ ਹਾਂ ਅਤੇ ਅੱਗੇ ਵਧੋ"
                else -> "Agree & Continue"
            }
            gravity = Gravity.CENTER
            textSize = 16f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = ContextCompat.getDrawable(this@LaunchGateActivity, R.drawable.bg_saffron_cta)
            elevation = dp(3).toFloat()
            alpha = 0.45f
            isEnabled = false
            isClickable = true
            isFocusable = true
            applyBouncyPress()
            setOnClickListener {
                if (!agree.isChecked) return@setOnClickListener
                prefs.edit().putBoolean("terms_accepted_v1", true).putString("terms_version", "1").apply()
                openHome()
            }
        }
        root.addView(next, LinearLayout.LayoutParams(-1, dp(54)))

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
                val label = when (lang) { "hi" -> "हिन्दी"; "pa" -> "ਪੰਜਾਬੀ"; else -> "English" }
                "$label\n$text"
            } else langs.joinToString("\n\n━━━━━━━━━━━━\n\n") { lang ->
                val label = when (lang) { "hi" -> "हिन्दी"; "pa" -> "ਪੰਜਾਬੀ"; else -> "English" }
                "$label\n" + defaultText.getValue(lang)
            }
            loading.text = body
            agree.isEnabled = true
            next.isEnabled = agree.isChecked
            next.alpha = if (agree.isChecked) 1f else 0.45f
            agree.setOnCheckedChangeListener { _, checked ->
                next.isEnabled = checked
                next.animate().alpha(if (checked) 1f else 0.45f).setDuration(180).start()
            }
        }
    }

    private fun applyLanguage(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    private fun openHome() {
        applyLanguage(selected)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}