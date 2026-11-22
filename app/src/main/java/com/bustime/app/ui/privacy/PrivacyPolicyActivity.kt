package com.bustime.app.ui.privacy

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.bustime.app.R

class PrivacyPolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val displayFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@PrivacyPolicyActivity, R.drawable.bg_hero_gradient)
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
            text = getString(R.string.privacy_policy_title)
            textSize = 19f
            typeface = Typeface.create(displayFont, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(headerBar, LinearLayout.LayoutParams(-1, -2))

        val webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/privacy_policy.html")
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }
}
