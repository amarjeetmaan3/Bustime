package com.bustime.app.ui.details

import android.os.Bundle
import android.view.Gravity
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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24)) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply { text = name.ifBlank { if(type=="taxi") "Taxi Details" else "Auto Details" }; textSize=24f; setPadding(0,0,0,dp(16)) })
        fun detail(label: String, value: String) {
            if (value.isBlank()) return
            root.addView(TextView(this).apply { text = "$label\n$value"; textSize=17f; setPadding(0,dp(8),0,dp(8)) })
        }
        detail(vehicleLabel, vehicle)
        if (seats > 0) detail(if(language=="hi") "सीटें" else if(language=="pa") "ਸੀਟਾਂ" else "Seats", seats.toString())
        if (fuel.isNotBlank()) detail(if(language=="hi") "ईंधन" else if(language=="pa") "ਬਾਲਣ" else "Fuel", fuel)
        detail(unionLabel, union)
        detail(if(language=="hi") "अन्य जानकारी" else if(language=="pa") "ਹੋਰ ਜਾਣਕਾਰੀ" else "Other information", info)
        detail(numberLabel, phone)
        root.addView(Button(this).apply {
            text = if(language=="hi") "कॉल करें" else if(language=="pa") "ਕਾਲ ਕਰੋ" else "Call"
            setOnClickListener { ContactActions.dial(this@DriverDetailActivity, phone) }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin=dp(10) })
        root.addView(Button(this).apply {
            text = "WhatsApp"
            setOnClickListener { ContactActions.whatsapp(this@DriverDetailActivity, phone) }
        }, LinearLayout.LayoutParams(-1, dp(50)))
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