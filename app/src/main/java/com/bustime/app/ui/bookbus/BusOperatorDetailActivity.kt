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

/** Company details and booking contact, following the same warning/contact flow as Taxi and Auto. */
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
        val serviceName = intent.getStringExtra("service_name").orEmpty()
            .ifBlank { intent.getStringExtra("operator_name").orEmpty() }
            .ifBlank { getString(R.string.bb_service) }
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
                setColor(cardBg)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), borderColor)
            }
        }
        header.addView(TextView(this).apply {
            text = "🚌"
            textSize = 38f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(54)))
        header.addView(TextView(this).apply {
            text = serviceName
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        if (types.isNotEmpty()) header.addView(TextView(this).apply {
            text = types.joinToString(" · ", transform = ::localizedType)
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val detailsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), borderColor)
            }
        }
        fun detail(label: String, value: String) {
            if (value.isBlank()) return
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(11), 0, dp(11))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(secondaryText)
            })
            row.addView(TextView(this).apply {
                text = value
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(primaryText)
                setPadding(0, dp(3), 0, 0)
            })
            detailsCard.addView(row)
            detailsCard.addView(View(this).apply { setBackgroundColor(borderColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        }

        detail(getString(R.string.bb_service), serviceName)
        detail(getString(R.string.bb_bus_types), types.joinToString(" · ", transform = ::localizedType))
        detail(getString(R.string.bb_location), location)
        detail(getString(R.string.bb_extra_details_label), extraDetails)
        detail(getString(R.string.bb_contact), phone.ifBlank { getString(R.string.bb_contact_unavailable) })
        root.addView(detailsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        root.addView(Button(this).apply {
            text = getString(R.string.call)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(36, 88, 211))
            isEnabled = phone.isNotBlank()
            setOnClickListener { ContactActions.dial(this@BusOperatorDetailActivity, phone) }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        root.addView(Button(this).apply {
            text = getString(R.string.whatsapp)
            setTextColor(if (night) Color.rgb(180, 238, 204) else Color.rgb(24, 111, 68))
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (night) Color.rgb(34, 67, 53) else Color.rgb(225, 245, 234))
            isEnabled = phone.isNotBlank() || whatsapp.isNotBlank()
            setOnClickListener { ContactActions.whatsapp(this@BusOperatorDetailActivity, whatsapp) }
        }, LinearLayout.LayoutParams(-1, dp(52)))

        // Match Taxi/Auto: open the company details first, then show the remotely managed warning
        // automatically over the page. Once it closes, the booking number and contact buttons remain visible.
        lifecycleScope.launch {
            val settings = withContext(Dispatchers.IO) { RemoteContentSettings.refresh() }
            if (!settings.popupEnabled || isFinishing || isDestroyed) return@launch
            val warning = settings.textFor(settings.popup, language).ifBlank {
                when (language) {
                    "hi" -> "Bustime केवल जानकारी उपलब्ध कराता है। बस कंपनी, सेवा और बुकिंग नंबर की पुष्टि स्वयं करें।"
                    "pa" -> "Bustime ਸਿਰਫ਼ ਜਾਣਕਾਰੀ ਦਿੰਦਾ ਹੈ। ਬੱਸ ਕੰਪਨੀ, ਸੇਵਾ ਅਤੇ ਬੁਕਿੰਗ ਨੰਬਰ ਦੀ ਪੁਸ਼ਟੀ ਖੁਦ ਕਰੋ।"
                    else -> "Bustime provides information only. Please independently verify the bus company, service and booking number."
                }
            }
            val titleText = when (language) {
                "hi" -> "महत्वपूर्ण सूचना"
                "pa" -> "ਜ਼ਰੂਰੀ ਸੂਚਨਾ"
                else -> "Important notice"
            }
            val dialog = AlertDialog.Builder(this@BusOperatorDetailActivity)
                .setTitle(titleText)
                .setMessage(warning)
                .setCancelable(false)
                .create()
            dialog.show()
            delay(settings.popupSeconds.coerceAtLeast(5) * 1000L)
            if (!isFinishing && !isDestroyed && dialog.isShowing) dialog.dismiss()
        }
    }
}
