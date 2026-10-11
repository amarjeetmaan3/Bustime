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
import com.bustime.app.models.Place
import com.bustime.app.utils.LanguageHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * "Select your city and area": खोजने लायक सूची वाला डायलॉग (Taxi और Auto दोनों में एक ही)।
 * चुना हुआ इलाका फ़ोन में याद रहता है, ताकि हर बार दोबारा न चुनना पड़े।
 */
object AreaPicker {

    private const val PREFS = "bustime_prefs"
    private const val KEY_AREA = "area_id"

    fun savedId(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_AREA, null)

    private fun save(ctx: Context, id: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (id == null) remove(KEY_AREA) else putString(KEY_AREA, id)
        }.apply()
    }

    /** बटन पर दिखने वाला नाम: चुना हो तो इलाके का नाम, वरना "Select your city and area" */
    suspend fun label(ctx: Context, repo: AppRepository): String {
        val id = savedId(ctx) ?: return ctx.getString(R.string.select_area)
        val place = repo.getPlacesById(setOf(id))[id] ?: return ctx.getString(R.string.select_area)
        return place.displayName(LanguageHelper.current()) + "  ▾"
    }

    /** onChosen: चुनने (या "सारे इलाके") के बाद चलता है */
    fun show(ctx: Context, owner: LifecycleOwner, repo: AppRepository, onChosen: () -> Unit) {
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_area_picker, null)
        val etSearch = view.findViewById<EditText>(R.id.etAreaSearch)
        val list = view.findViewById<ListView>(R.id.listAreas)
        val lang = LanguageHelper.current()

        var shown: List<Place> = emptyList()
        val adapter = ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, ArrayList())
        list.adapter = adapter

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.select_area)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()

        var job: Job? = null
        fun load(text: String) {
            job?.cancel()
            job = owner.lifecycleScope.launch {
                shown = repo.searchPlaces(text, false, 80, allowEmpty = true)
                adapter.clear()
                adapter.add(ctx.getString(R.string.all_areas))
                adapter.addAll(shown.map { it.displayName(lang) })
                adapter.notifyDataSetChanged()
            }
        }
        load("")
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { load(s?.toString().orEmpty()) }
        })
        list.setOnItemClickListener { _, _, position, _ ->
            save(ctx, if (position == 0) null else shown.getOrNull(position - 1)?.id)
            dialog.dismiss()
            onChosen()
        }
        dialog.show()
    }
}
