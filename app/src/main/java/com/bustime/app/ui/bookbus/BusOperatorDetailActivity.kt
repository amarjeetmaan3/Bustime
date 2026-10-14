package com.bustime.app.ui.bookbus

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.RemoteContentSettings
import com.bustime.app.utils.ContactActions
import com.bustime.app.utils.LanguageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Contact-only bus service details. No seat reservation or availability is offered. */
class BusOperatorDetailActivity : AppCompatActivity() {
    private fun localizedType(value: String): String = when (value.trim().lowercase(java.util.Locale.ROOT)) {
        "ordinary" -> getString(R.string.bus_type_ordinary)
        "seater" -> getString(R.string.bus_type_seater)
        "sleeper" -> getString(R.string.bus_type_sleeper)
        "ac bus" -> getString(R.string.bus_type_ac)
        "non-ac bus" -> getString(R.string.bus_type_non_ac)
        "luxury bus", "luxury" -> getString(R.string.bus_type_luxury)
        "volvo bus" -> getString(R.string.bus_type_volvo)
        "mini bus" -> getString(R.string.bus_type_mini)
        "other" -> getString(R.string.bus_type_other)
        else -> value
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = LanguageHelper.current()
        val serviceName = intent.getStringExtra("service_name").orEmpty().ifBlank { intent.getStringExtra("operator_name").orEmpty() }.ifBlank { getString(R.string.bb_service) }
        val types = try {
            val arr = JSONArray(intent.getStringExtra("bus_types").orEmpty())
            (0 until arr.length()).mapNotNull { arr.optString(it).trim().takeIf(String::isNotBlank) }
        } catch (_: Exception) { emptyList() }
        val location = intent.getStringExtra("location").orEmpty().trim()
        val extraDetails = intent.getStringExtra("details").orEmpty().trim()
        val phone = intent.getStringExtra("phone").orEmpty().trim()
        val whatsapp = intent.getStringExtra("whatsapp").orEmpty().ifBlank { phone }.trim()
        title = getString(R.string.bb_detail_title)

        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val pageBg = if (night) Color.rgb(18, 22, 29) else Color.rgb(245, 247, 251)
        val cardBg = if (night) Color.rgb(29, 35, 44) else Color.WHITE
        val primaryText = if (night) Color.rgb(238, 242, 247) else Color.rgb(24, 34, 48)
        val secondaryText = if (night) Color.rgb(174, 185, 200) else Color.rgb(102, 117, 138)
        val borderColor = if (night) Color.rgb(57, 66, 80) else Color.rgb(229, 234, 242)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(pageBg)
        }
        setContentView(ScrollView(this).apply { clipToPadding = false; addView(root) })

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(22))
            background = GradientDrawable().apply {
                setColor(cardBg); cornerRadius = dp(18).toFloat(); setStroke(dp(1), borderColor)
            }
        }
        header.addView(TextView(this).apply { text = "🚌"; textSize = 38f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(54)))
        header.addView(TextView(this).apply {
            text = serviceName; textSize = 23f; typeface = Typeface.DEFAULT_BOLD; setTextColor(primaryText)
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
        })
        if (types.isNotEmpty()) header.addView(TextView(this).apply {
            text = types.joinToString(" · ", transform = ::localizedType); textSize = 14f; setTextColor(secondaryText)
            gravity = Gravity.CENTER; setPadding(0, dp(7), 0, 0)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val detailsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(6), dp(16), dp(6))
            background = GradientDrawable().apply { setColor(cardBg); cornerRadius = dp(16).toFloat(); setStroke(dp(1), borderColor) }
        }
        fun detail(label: String, value: String) {
            if (value.isBlank()) return
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(11), 0, dp(11)) }
            row.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(secondaryText) })
            row.addView(TextView(this).apply {
                text = value; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(primaryText); setPadding(0, dp(3), 0, 0)
            })
            detailsCard.addView(row)
            detailsCard.addView(View(this).apply { setBackgroundColor(borderColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        detail(getString(R.string.bb_service), serviceName)
        detail(getString(R.string.bb_bus_types), types.joinToString(" · ", transform = ::localizedType))
        detail(getString(R.string.bb_location), location)
        detail(getString(R.string.bb_extra_details_label), extraDetails)
        root.addView(detailsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val contactPrompt = TextView(this).apply {
            text = getString(R.string.bb_contact_prompt); textSize = 14f; setTextColor(secondaryText)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        root.addView(contactPrompt)
        val showContact = Button(this).apply {
            text = getString(R.string.bb_contact_reveal); setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(36, 88, 211))
        }
        root.addView(showContact, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        val contactArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(cardBg); cornerRadius = dp(14).toFloat(); setStroke(dp(1), borderColor) }
        }
        contactArea.addView(TextView(this).apply {
            text = phone.ifBlank { getString(R.string.bb_contact_unavailable) }; textSize = 18f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(primaryText); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        if (phone.isNotBlank()) {
            contactArea.addView(Button(this).apply {
                text = getString(R.string.call); setOnClickListener { ContactActions.dial(this@BusOperatorDetailActivity, phone) }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
            contactArea.addView(Button(this).apply {
                text = getString(R.string.whatsapp)
                setOnClickListener { ContactActions.whatsapp(this@BusOperatorDetailActivity, whatsapp) }
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        root.addView(contactArea)
        showContact.setOnClickListener {
            showContact.isEnabled = false
            lifecycleScope.launch {
                val settings = withContext(Dispatchers.IO) { RemoteContentSettings.refresh() }
                if (!settings.popupEnabled) {
                    contactArea.visibility = View.VISIBLE; showContact.visibility = View.GONE; contactPrompt.visibility = View.GONE
                    return@launch
                }
                val warning = settings.textFor(settings.popup, language).ifBlank {
                    when (language) {
                        "hi" -> "Bustime केवल जानकारी उपलब्ध कराता है। बस सेवा और ड्राइवर की जानकारी की पुष्टि स्वयं करें।"
                        "pa" -> "Bustime ਸਿਰਫ਼ ਜਾਣਕਾਰੀ ਦਿੰਦਾ ਹੈ। ਬੱਸ ਸੇਵਾ ਅਤੇ ਡਰਾਈਵਰ ਦੀ ਜਾਣਕਾਰੀ ਦੀ ਪੁਸ਼ਟੀ ਖੁਦ ਕਰੋ।"
                        else -> "Bustime provides information only. Please confirm bus service and driver details independently."
                    }
                }
                val dialog = AlertDialog.Builder(this@BusOperatorDetailActivity)
                    .setTitle(when (language) { "hi" -> "महत्वपूर्ण सूचना"; "pa" -> "ਜ਼ਰੂਰੀ ਸੂਚਨਾ"; else -> "Important notice" })
                    .setMessage(warning).setCancelable(false).create()
                dialog.show()
                delay(settings.popupSeconds.coerceAtLeast(5) * 1000L)
                if (isFinishing || isDestroyed) return@launch
                if (dialog.isShowing) dialog.dismiss()
                contactArea.visibility = View.VISIBLE; showContact.visibility = View.GONE; contactPrompt.visibility = View.GONE
            }
        }
    }
}
