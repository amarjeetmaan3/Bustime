package com.bustime.app.ui.mdm

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.data.repository.AppRepository
import kotlinx.coroutines.launch

class MdmPanelActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply { text = "MDM Admin"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Choose an operation"; textSize = 15f; setPadding(0, 8, 0, 16) })
        root.addView(Button(this).apply { text = "Route List"; setOnClickListener { showRoutes() } })
        root.addView(Button(this).apply { text = "Bus Stop List"; setOnClickListener { showStops() } })
        root.addView(Button(this).apply { text = "Add Route"; setOnClickListener { explainPublishingDependency() } })
        root.addView(Button(this).apply { text = "Correction Route"; setOnClickListener { explainPublishingDependency() } })
        root.addView(Button(this).apply { text = "Add Bus Stop"; setOnClickListener { explainPublishingDependency() } })
        root.addView(Button(this).apply { text = "Correction Bus Stop"; setOnClickListener { explainPublishingDependency() } })
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(root)
    }

    private fun showRoutes() {
        lifecycleScope.launch {
            try {
                val repo = AppRepository.getInstance(this@MdmPanelActivity)
                val routes = repo.getAllRoutes()
                val places = repo.getPlacesById(routes.flatMap { listOf(it.fromId, it.toId) }.toSet())
                val text = routes.take(250).joinToString("\n") { route ->
                    val from = places[route.fromId]?.displayName("en") ?: route.fromId
                    val to = places[route.toId]?.displayName("en") ?: route.toId
                    "$from → $to" + (route.serviceName?.let { " · $it" } ?: "")
                }.ifBlank { "No routes found on this device." }
                AlertDialog.Builder(this@MdmPanelActivity).setTitle("Route List").setMessage(text).setPositiveButton("Close", null).show()
            } catch (e: Exception) {
                AlertDialog.Builder(this@MdmPanelActivity).setTitle("Route List")
                    .setMessage("Could not load routes: " + (e.message ?: "Please refresh the app.")).setPositiveButton("Close", null).show()
            }
        }
    }

    private fun showStops() {
        lifecycleScope.launch {
            try {
                val places = AppRepository.getInstance(this@MdmPanelActivity).getAllPlaces()
                val text = places.take(350).joinToString("\n") { it.displayName("en").ifBlank { it.id } }
                    .ifBlank { "No bus stops found on this device." }
                AlertDialog.Builder(this@MdmPanelActivity).setTitle("Bus Stop List").setMessage(text).setPositiveButton("Close", null).show()
            } catch (e: Exception) {
                AlertDialog.Builder(this@MdmPanelActivity).setTitle("Bus Stop List")
                    .setMessage("Could not load bus stops: " + (e.message ?: "Please refresh the app.")).setPositiveButton("Close", null).show()
            }
        }
    }

    private fun explainPublishingDependency() {
        AlertDialog.Builder(this).setTitle("MDM publishing setup")
            .setMessage("Route and stop editing plus Publish need a server-side publisher connection before these actions can safely change the live app data.")
            .setPositiveButton("OK", null).show()
    }
}
