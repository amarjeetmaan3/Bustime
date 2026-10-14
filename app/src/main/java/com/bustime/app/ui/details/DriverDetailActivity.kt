package com.bustime.app.ui.details

import android.os.Bundle
import android.view.Gravity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.data.RemoteContentSettings
import com.bustime.app.utils.ContactActions
import com.bustime.app.utils.LanguageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriverDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra("driver_type") ?: "taxi"
        val language = LanguageHelper.current()
        val name = intent.getStringExtra("driver_name").orEmpty()
        val vehicle = intent.getStringExtra("driver_vehicle").orEmpty()
        val phone = intent.getStringExtra("driver_phone").orEmpty()
        val seats = intent.getIntExtra("driver_seats", -1)
        val fuel = intent.getStringExtra("driver_fuel").orEmpty()
        val union = intent.getStringExtra("driver_union").orEmpty()
        val info = intent.getStringExtra("driver_info").orEmpty()
        val numberLabel = when(language) { "hi" -> "फ़ोन नंबर"; "pa" -> "ਫ਼ੋਨ ਨੰਬਰ"; else -> "Phone number" }
        val vehicleLabel = when(language) { "hi" -> "वाहन"; "pa" -> "ਵਾਹਨ"; else -> "Vehicle" }
        val unionLabel = when(language) { "hi" -> "यूनियन"; "pa" -> "ਯੂਨੀਅਨ"; else -> "Union" }
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
        val scroll = ScrollView(this).apply { clipToPadding = false; addView(root) }
        setContentView(scroll)

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
        val avatar = TextView(this).apply {
            text = if (type.equals("auto", true)) "🛺" else "🚕"
            textSize = 38f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (night) Color.rgb(42, 54, 76) else Color.rgb(234, 240, 255))
            }
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(84), dp(84)).apply { bottomMargin = dp(12) })
        header.addView(TextView(this).apply {
            text = name.ifBlank { if(type=="taxi") "Taxi Driver" else "Auto Driver" }
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryText)
            gravity = Gravity.CENTER
        })
        header.addView(TextView(this).apply {
            val meta = buildList {
                if (vehicle.isNotBlank()) add(vehicle)
                if (seats > 0) add("$seats " + when(language) { "hi" -> "सीटर"; "pa" -> "ਸੀਟਰ"; else -> "seater" })
                if (fuel.isNotBlank()) add(if (fuel.equals("ev", true)) "EV" else fuel.replaceFirstChar { it.uppercase() })
            }.joinToString("  ·  ")
            text = meta.ifBlank { if(language=="hi") "ड्राइवर विवरण" else if(language=="pa") "ਡਰਾਈਵਰ ਵੇਰਵੇ" else "Driver details" }
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
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
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(11), 0, dp(11))
            }
            item.addView(TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.rgb(102, 117, 138))
            })
            item.addView(TextView(this).apply {
                text = value
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(primaryText)
                setPadding(0, dp(3), 0, 0)
            })
            detailsCard.addView(item)
            val divider = android.view.View(this).apply { setBackgroundColor(borderColor) }
            detailsCard.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        }
        detail(vehicleLabel, vehicle)
        if (seats > 0) detail(if(language=="hi") "सीटें" else if(language=="pa") "ਸੀਟਾਂ" else "Seats", seats.toString())
        if (fuel.isNotBlank()) detail(if(language=="hi") "ईंधन" else if(language=="pa") "ਬਾਲਣ" else "Fuel", fuel.uppercase())
        detail(unionLabel, union)
        detail(if(language=="hi") "अन्य जानकारी" else if(language=="pa") "ਹੋਰ ਜਾਣਕਾਰੀ" else "Additional information", info)
        detail(numberLabel, phone)
        root.addView(detailsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        root.addView(Button(this).apply {
            text = if(language=="hi") "कॉल करें" else if(language=="pa") "ਕਾਲ ਕਰੋ" else "Call driver"
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(36, 88, 211))
            setOnClickListener { ContactActions.dial(this@DriverDetailActivity, phone) }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        root.addView(Button(this).apply {
            text = "WhatsApp"
            setTextColor(if (night) Color.rgb(180, 238, 204) else Color.rgb(24, 111, 68))
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (night) Color.rgb(34, 67, 53) else Color.rgb(225, 245, 234))
            setOnClickListener { ContactActions.whatsapp(this@DriverDetailActivity, phone) }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        lifecycleScope.launch {
            val settings = withContext(Dispatchers.IO) { RemoteContentSettings.refresh() }
            if (!settings.popupEnabled) return@launch
            val text = settings.textFor(settings.popup, language).ifBlank {
                when(language) {
                    "hi" -> "कृपया यात्रा से पहले वाहन और किराये की जानकारी की पुष्टि करें।"
                    "pa" -> "ਯਾਤਰਾ ਤੋਂ ਪਹਿਲਾਂ ਵਾਹਨ ਅਤੇ ਕਿਰਾਏ ਦੀ ਜਾਣਕਾਰੀ ਦੀ ਪੁਸ਼ਟੀ ਕਰੋ।"
                    else -> "Please verify vehicle and fare details before travelling."
                }
            }
            val dialog = AlertDialog.Builder(this@DriverDetailActivity)
                .setTitle(if(language=="hi") "महत्वपूर्ण सूचना" else if(language=="pa") "ਜ਼ਰੂਰੀ ਸੂਚਨਾ" else "Important notice")
                .setMessage(text)
                .setCancelable(false)
                .create()
            dialog.show()
            kotlinx.coroutines.delay(settings.popupSeconds.coerceAtLeast(5) * 1000L)
            if (!isFinishing && dialog.isShowing) dialog.dismiss()
        }
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}