package com.bustime.app.ads

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.util.TypedValue
import com.bustime.app.ui.details.RouteDetailActivity
import com.bustime.app.ui.details.DriverDetailActivity
import com.bustime.app.ui.bookbus.BookBusActivity
import com.bustime.app.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bustime.app.utils.Constants
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Fetches the remotely configured, non-fullscreen owner campaign poster. */
object CustomPopupManager {
    private val main = Handler(Looper.getMainLooper())

    fun showLocalSponsorshipAd(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        Thread {
            val config = try {
                val endpoint = Constants.SUPABASE_URL.trimEnd('/') + "/rest/v1/ad_settings?select=config&id=eq.1"
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer " + Constants.SUPABASE_ANON_KEY)
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val raw = conn.inputStream.bufferedReader().use { it.readText() }
                    val rows = org.json.JSONArray(raw)
                    if (rows.length() == 0) null else rows.getJSONObject(0).optJSONObject("config")?.optJSONObject("my")
                } finally { conn.disconnect() }
            } catch (_: Exception) { null }
            if (config == null || !config.optBoolean("enabled", false)) return@Thread
            val today = LocalDate.now().toString()
            val start = config.optString("start_date")
            val end = config.optString("end_date")
            if (start.isNotBlank() && today < start || end.isNotBlank() && today > end) return@Thread
            val posterUrl = config.optString("poster_url")
            if (!posterUrl.startsWith("https://") && !posterUrl.startsWith("http://")) return@Thread
            val bitmap = try {
                URL(posterUrl).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null } ?: return@Thread
            main.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                showDialog(activity, config, bitmap)
            }
        }.start()
    }

    private fun fetchOperator(id: String): JSONObject? {
        val encodedId = java.net.URLEncoder.encode(id, "UTF-8")
        val endpoint = Constants.SUPABASE_URL.trimEnd('/') +
            "/rest/v1/bus_operators?select=*&id=eq.$encodedId&active=eq.true&limit=1"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer " + Constants.SUPABASE_ANON_KEY)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val rows = org.json.JSONArray(raw)
            if (rows.length() == 0) null else rows.optJSONObject(0)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun showDialog(activity: Activity, config: JSONObject, bitmap: android.graphics.Bitmap) {
        val dp = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(8), px(12), px(12))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val close = Button(activity).apply {
            text = "×"
            textSize = 25f
            isEnabled = false
            contentDescription = "Close advertisement"
            minWidth = px(42)
            minHeight = px(42)
            setPadding(0, 0, 0, 0)
        }
        header.addView(close, LinearLayout.LayoutParams(px(46), px(44)))
        root.addView(header)
        val poster = ImageView(activity).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Sponsored promotion"
        }
        root.addView(poster, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(420)))
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun action(label: String, enabled: Boolean, callback: () -> Unit) {
            if (!enabled) return
            val button = Button(activity).apply {
                text = label
                setOnClickListener { callback() }
            }
            actions.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(px(3), px(6), px(3), 0)
            })
        }
        val dialog = AlertDialog.Builder(activity).setView(root).create()
        val targetKind = config.optString("target_kind", "route").ifBlank { "route" }
        val targetId = config.optString("target_id").ifBlank { config.optString("route_id") }
        val defaultLabel = when (targetKind) {
            "operator" -> "Check Bus"
            "taxi" -> "Book Taxi"
            "auto" -> "Book Auto"
            "route" -> "Check Route"
            else -> "Learn more"
        }
        action(config.optString("cta_label", defaultLabel).ifBlank { defaultLabel },
            config.optBoolean("check_route_enabled", false)) {
            if (targetId.isBlank()) {
                Toast.makeText(activity, "This advertisement has no selected listing.", Toast.LENGTH_SHORT).show()
                return@action
            }
            when (targetKind) {
                "route" -> {
                    activity.startActivity(Intent(activity, RouteDetailActivity::class.java).putExtra("ROUTE_ID", targetId))
                    dialog.dismiss()
                }
                "operator" -> GlobalScope.launch {
                    val operator = withContext(Dispatchers.IO) { fetchOperator(targetId) }
                    main.post {
                        if (activity.isFinishing || activity.isDestroyed) return@post
                        if (operator == null) {
                            Toast.makeText(activity, "Bus listing could not be loaded. Please publish the latest data and try again.", Toast.LENGTH_LONG).show()
                            return@post
                        }
                        activity.startActivity(Intent(activity, com.bustime.app.ui.bookbus.BusOperatorDetailActivity::class.java).apply {
                            putExtra("operator_name", operator.optString("operator_name"))
                            putExtra("service_name", operator.optString("service_name"))
                            putExtra("bus_types", operator.optJSONArray("bus_types")?.toString() ?: "[]")
                            putExtra("location", operator.optString("location"))
                            putExtra("details", operator.optString("details"))
                            putExtra("phone", operator.optString("phone"))
                            putExtra("whatsapp", operator.optString("whatsapp"))
                        })
                        dialog.dismiss()
                    }
                }
                "taxi", "auto" -> GlobalScope.launch {
                    val driver = withContext(Dispatchers.IO) {
                        AppRepository.getInstance(activity).getDriver(targetId)
                    }
                    main.post {
                        if (activity.isFinishing || activity.isDestroyed) return@post
                        if (driver == null) {
                            Toast.makeText(activity, "Vehicle listing not found. Publish the latest Taxi/Auto data and try again.", Toast.LENGTH_LONG).show()
                            return@post
                        }
                        activity.startActivity(Intent(activity, DriverDetailActivity::class.java).apply {
                            putExtra("driver_id", driver.id)
                            putExtra("driver_type", driver.type)
                            putExtra("driver_name", driver.name)
                            putExtra("driver_vehicle", driver.vehicle.orEmpty())
                            putExtra("driver_phone", driver.phone)
                            putExtra("driver_seats", driver.seats ?: -1)
                            putExtra("driver_fuel", driver.fuel.orEmpty())
                            putExtra("driver_info", driver.info.orEmpty())
                            putExtra("driver_area", driver.areaId.orEmpty())
                        })
                        dialog.dismiss()
                    }
                }
                else -> Toast.makeText(activity, "Unsupported advertisement target.", Toast.LENGTH_SHORT).show()
            }
        }
        action("Call", config.optBoolean("call_enabled", false)) {
            val phone = config.optString("phone")
            if (phone.isNotBlank()) activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
        }
        action(config.optString("cta_label", "Learn more").ifBlank { "Learn more" }, config.optBoolean("link_enabled", false)) {
            val url = config.optString("url")
            if (url.startsWith("https://") || url.startsWith("http://")) activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        if (actions.childCount > 0) root.addView(actions)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.90f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        val delay = config.optInt("close_delay", 3).coerceIn(2, 4) * 1000L
        main.postDelayed({ if (dialog.isShowing) close.isEnabled = true }, delay)
        close.setOnClickListener { dialog.dismiss() }
    }
}
