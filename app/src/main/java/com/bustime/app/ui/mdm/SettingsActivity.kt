package com.bustime.app.ui.mdm

import android.content.Intent
import android.net.Uri
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.bustime.app.R
import com.bustime.app.ui.common.applyBouncyPress
import com.bustime.app.utils.LanguageHelper
import com.google.android.material.button.MaterialButton

/** Settings landing page. Check Updates opens Bustime's Google Play listing. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = LanguageHelper.current()
        val bodyFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans)
        val pageBg = ContextCompat.getColor(this, R.color.bgScreen)
        val surface = ContextCompat.getColor(this, R.color.surface)
        val divider = ContextCompat.getColor(this, R.color.divider)
        val ripple = ContextCompat.getColor(this, R.color.colorPrimaryContainer)
        val primary = ContextCompat.getColor(this, R.color.colorPrimary)
        val saffron = ContextCompat.getColor(this, R.color.colorAccent)
        val textPrimary = ContextCompat.getColor(this, R.color.textPrimary)
        val textSecondary = ContextCompat.getColor(this, R.color.textSecondary)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }

        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_hero_gradient)
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
            text = when (language) { "hi" -> "सेटिंग्स"; "pa" -> "ਸੈਟਿੰਗਾਂ"; else -> "Settings" }
            textSize = 20f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        outer.addView(headerBar, LinearLayout.LayoutParams(-1, -2))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }

        fun addSettingCard(label: String, iconRes: Int, squircleRes: Int, iconTint: Int, onClick: () -> Unit) {
            val shape = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), divider)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                elevation = dp(2).toFloat()
                background = RippleDrawable(ColorStateList.valueOf(ripple), shape, null)
                isClickable = true
                isFocusable = true
                applyBouncyPress()
                setOnClickListener { onClick() }
            }
            val iconHolder = FrameLayout(this).apply {
                background = ContextCompat.getDrawable(this@SettingsActivity, squircleRes)
            }
            iconHolder.addView(ImageView(this).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(iconTint)
                contentDescription = label
            }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            row.addView(iconHolder, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(14) })

            row.addView(TextView(this).apply {
                text = label
                textSize = 16f
                typeface = Typeface.create(bodyFont, Typeface.BOLD)
                setTextColor(textPrimary)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_chevron_right)
                imageTintList = ColorStateList.valueOf(textSecondary)
                contentDescription = label
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }

        addSettingCard(
            when (language) { "hi" -> "अपडेट चेक करें"; "pa" -> "ਅੱਪਡੇਟ ਚੈੱਕ ਕਰੋ"; else -> "Check Updates" },
            R.drawable.ic_refresh,
            R.drawable.bg_squircle_indigo,
            primary
        ) {
            openPlayStoreListing()
        }
        root.addView(MaterialButton(this).apply {
            text = getString(R.string.back)
            isAllCaps = false
            cornerRadius = dp(28)
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            backgroundTintList = ColorStateList.valueOf(ripple)
            setTextColor(primary)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })

        outer.addView(ScrollView(this).apply { addView(root) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)
    }

    private fun openPlayStoreListing() {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        try {
            startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            startActivity(webIntent)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
