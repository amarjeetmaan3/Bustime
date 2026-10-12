package com.bustime.app.ui.home

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** Taxi/Auto area picker backed only by areas entered in driver records. */
object AreaPicker {
    private const val PREFS = "bustime_prefs"
    private const val KEY_AREA = "driver_area_name"

    fun savedId(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_AREA, null)
            ?.takeIf { it.isNotBlank() }

    private fun save(ctx: Context, area: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (area == null) remove(KEY_AREA) else putString(KEY_AREA, area)
        }.apply()
    }

    suspend fun label(ctx: Context, repo: AppRepository): String {
        val areaId = savedId(ctx) ?: return ctx.getString(R.string.select_area)
        val place = repo.getPlacesById(listOf(areaId))[areaId]
        val name = place?.displayName(com.bustime.app.utils.LanguageHelper.current())
            ?.takeIf { it.isNotBlank() }
            ?: areaId.removePrefix("place_").replace('_', ' ').replaceFirstChar { it.uppercase() }
        return "$name  ▾"
    }

    /** No context.getString(R.string.home_all_areas) option: the user must choose a driver service area. */
    fun show(ctx: Context, owner: LifecycleOwner, repo: AppRepository, onChosen: () -> Unit) {
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_area_picker, null)
        val etSearch = view.findViewById<EditText>(R.id.etAreaSearch)
        val list = view.findViewById<ListView>(R.id.listAreas)
        var allAreas: List<Pair<String, String>> = emptyList()
        var shown: List<Pair<String, String>> = emptyList()
        val adapter = ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, ArrayList())
        list.adapter = adapter
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.select_area)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()

        fun filter(text: String) {
            val q = text.trim().lowercase(Locale.ROOT)
            shown = if (q.isEmpty()) allAreas else allAreas.filter {
                it.second.lowercase(Locale.ROOT).contains(q) || it.first.lowercase(Locale.ROOT).contains(q)
            }
            adapter.clear()
            adapter.addAll(shown.map { it.second })
            adapter.notifyDataSetChanged()
        }
        var job: Job? = null
        job = owner.lifecycleScope.launch {
            val areaIds = repo.getDriverAreas().distinctBy { it.trim().lowercase(Locale.ROOT) }
            val places = repo.getPlacesById(areaIds)
            allAreas = areaIds.map { id ->
                val display = places[id]?.displayName(com.bustime.app.utils.LanguageHelper.current())
                    ?.takeIf { it.isNotBlank() }
                    ?: id.removePrefix("place_").replace('_', ' ').replaceFirstChar { it.uppercase() }
                id to display
            }
            filter(etSearch.text?.toString().orEmpty())
            if (allAreas.isEmpty()) android.widget.Toast.makeText(ctx, R.string.no_areas_yet, android.widget.Toast.LENGTH_LONG).show()
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filter(s?.toString().orEmpty()) }
        })
        list.setOnItemClickListener { _, _, position, _ ->
            save(ctx, shown.getOrNull(position)?.first)
            dialog.dismiss()
            onChosen()
        }
        dialog.setOnDismissListener { job?.cancel() }
        dialog.show()
    }
}
