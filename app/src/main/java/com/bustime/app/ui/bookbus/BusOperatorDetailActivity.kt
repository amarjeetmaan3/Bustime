package com.bustime.app.ui.bookbus

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.RemoteContentSettings
import com.bustime.app.utils.ContactActions
import com.bustime.app.utils.LanguageHelper
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Company details and booking contact, following the same warning/contact flow as Taxi and Auto. */
class BusOperatorDetailActivity : AppCompatActivity() {
    private val bodyFont: Typeface? by lazy { ResourcesCompat.getFont(this, R.font.plus_jakarta_sans) }

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

        val pageBg = ContextCompat.getColor(this, R.color.bgScreen)
        val cardBg = ContextCompat.getColor(this, R.color.surface)
        val primaryText = ContextCompat.getColor(this, R.color.textPrimary)
        val secondaryText = ContextCompat.getColor(this, R.color.textSecondary)
        val borderColor = ContextCompat.getColor(this, R.color.divider)
        val dividerColor = ContextCompat.getColor(this, R.color.divider)
        val primaryColor = ContextCompat.getColor(this, R.color.colorPrimary)
        val heroEnd = ContextCompat.getColor(this, R.color.heroEnd)
        val callBg = ContextCompat.getColor(this, R.color.callBg)
        val whatsappBg = ContextCompat.getColor(this, R.color.whatsappBg)
        val whatsappText = ContextCompat.getColor(this, R.color.whatsappText)
        val tonalBg = ContextCompat.getColor(this, R.color.colorSecondaryContainer)
        val tonalText = ContextCompat.getColor(this, R.color.colorAccent)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }

        // Gradient Top Bar
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@BusOperatorDetailActivity, R.drawable.bg_hero_gradient)
            elevation = dp(4).toFloat()
            setPadding(dp(10), dp(10), dp(14), dp(10))
        }
        headerBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back_arrow)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = null
            contentDescription = getString(R.string.back)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        headerBar.addView(TextView(this).apply {
            text = getString(R.string.bb_detail_title)
            textSize = 20f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        outer.addView(headerBar, LinearLayout.LayoutParams(-1, -2))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
            setBackgroundColor(pageBg)
        }
        outer.addView(ScrollView(this).apply { clipToPadding = false; addView(root) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)

        val initials = serviceName.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "BT" }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(22))
            elevation = dp(3).toFloat()
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), borderColor)
            }
        }
        val monogram = TextView(this).apply {
            text = initials
            textSize = 24f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(primaryColor, heroEnd)
            ).apply { shape = GradientDrawable.OVAL }
        }
        header.addView(monogram, LinearLayout.LayoutParams(dp(68), dp(68)))

        header.addView(TextView(this).apply {
            text = serviceName
            textSize = 22f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
        if (types.isNotEmpty()) header.addView(TextView(this).apply {
            text = types.joinToString(" · ", transform = ::localizedType)
            textSize = 13f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            setTextColor(tonalText)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                setColor(tonalBg)
                cornerRadius = dp(12).toFloat()
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val detailsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(6))
            elevation = dp(2).toFloat()
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), borderColor)
            }
        }
        fun detail(label: String, value: String) {
            if (value.isBlank()) return
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 12f
                typeface = bodyFont
                setTextColor(secondaryText)
            })
            row.addView(TextView(this).apply {
                text = value
                textSize = 16f
                typeface = Typeface.create(bodyFont, Typeface.BOLD)
                setTextColor(primaryText)
                setPadding(0, dp(3), 0, 0)
            })
            detailsCard.addView(row)
            detailsCard.addView(View(this).apply { setBackgroundColor(dividerColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        }

        detail(getString(R.string.bb_service), serviceName)
        detail(getString(R.string.bb_bus_types), types.joinToString(" · ", transform = ::localizedType))
        detail(getString(R.string.bb_location), location)
        detail(getString(R.string.bb_extra_details_label), extraDetails)
        detail(getString(R.string.bb_contact), phone.ifBlank { getString(R.string.bb_contact_unavailable) })
        if (detailsCard.childCount > 0) detailsCard.removeViewAt(detailsCard.childCount - 1)
        root.addView(detailsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        root.addView(MaterialButton(this).apply {
            text = getString(R.string.call)
            isAllCaps = false
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            cornerRadius = dp(14)
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(callBg)
            icon = ContextCompat.getDrawable(this@BusOperatorDetailActivity, R.drawable.ic_call)
            iconTint = ColorStateList.valueOf(Color.WHITE)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            isEnabled = phone.isNotBlank()
            setOnClickListener { ContactActions.dial(this@BusOperatorDetailActivity, phone) }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })

        root.addView(MaterialButton(this).apply {
            text = getString(R.string.whatsapp)
            isAllCaps = false
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            cornerRadius = dp(14)
            setTextColor(whatsappText)
            backgroundTintList = ColorStateList.valueOf(whatsappBg)
            isEnabled = phone.isNotBlank() || whatsapp.isNotBlank()
            setOnClickListener { ContactActions.whatsapp(this@BusOperatorDetailActivity, whatsapp) }
        }, LinearLayout.LayoutParams(-1, dp(54)))

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
