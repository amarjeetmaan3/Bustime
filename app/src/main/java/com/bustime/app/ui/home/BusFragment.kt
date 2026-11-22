package com.bustime.app.ui.home

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Place
import com.bustime.app.ui.common.applyBouncyPress
import com.bustime.app.ui.search.PlacePickerActivity
import com.bustime.app.ui.search.SearchActivity
import com.bustime.app.utils.LanguageHelper
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * बॉक्स पर टैप करो -> जगह चुनने का पूरा पेज खुलता है (कीबोर्ड के साथ)।
 * बीच का गोल बटन From और To की जगहें आपस में बदल देता है। नतीजे अलग पेज पर खुलते हैं।
 */
class BusFragment : Fragment(R.layout.fragment_bus) {

    private lateinit var repo: AppRepository
    private lateinit var etFrom: EditText
    private lateinit var etTo: EditText
    private lateinit var etStand: EditText

    private var fromPlace: Place? = null
    private var toPlace: Place? = null
    private var standPlace: Place? = null
    private var pickTarget = ""   // "from", "to" या "stand"

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val id = result.data?.getStringExtra("PLACE_ID")
            if (id != null) onPicked(pickTarget, id)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = AppRepository.getInstance(requireContext())

        etFrom = view.findViewById(R.id.etFrom)
        etTo = view.findViewById(R.id.etTo)
        etStand = view.findViewById(R.id.etStand)

        etFrom.setOnClickListener { openPicker("from", getString(R.string.from_hint), false) }
        etTo.setOnClickListener { openPicker("to", getString(R.string.to_hint), false) }
        etStand.setOnClickListener { openPicker("stand", getString(R.string.stand_hint), true) }
        view.findViewById<View>(R.id.boxFrom)?.setOnClickListener { openPicker("from", getString(R.string.from_hint), false) }
        view.findViewById<View>(R.id.boxTo)?.setOnClickListener { openPicker("to", getString(R.string.to_hint), false) }
        view.findViewById<TextView>(R.id.btnCardLang)?.apply {
            text = when (LanguageHelper.current()) {
                "hi" -> "हिन्दी"
                "pa" -> "ਪੰਜਾਬੀ"
                else -> "English"
            }
            applyBouncyPress()
            setOnClickListener { (activity as? HomeActivity)?.showLanguageDialog() }
        }

        // 180° spring-like rotation animation on Swap FAB
        view.findViewById<View>(R.id.btnSwap).apply {
            applyBouncyPress()
            setOnClickListener { button ->
                button.animate().rotationBy(180f).setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.6f)).start()
                val t = fromPlace
                fromPlace = toPlace
                toPlace = t
                showTexts()
            }
        }

        // Staggered entrance animation for Boarding-Pass & Spotlight cards
        listOfNotNull(
            view.findViewById<View>(R.id.cardRouteSearch),
            view.findViewById<View>(R.id.cardStandSearch)
        ).forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 26f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        view.findViewById<View>(R.id.btnSearchRoute).apply {
            applyBouncyPress()
            setOnClickListener { searchRoutes() }
        }
        view.findViewById<TextInputLayout>(R.id.tilStand).setEndIconOnClickListener {
            val lastStand = standPlace
            if (lastStand != null) openStandResults(lastStand)
            else openPicker("stand", getString(R.string.stand_hint), true)
        }

        // Bottom Actions (Add Route + Book Bus)
        view.findViewById<View>(R.id.btnAddBusRoute).apply {
            applyBouncyPress()
            setOnClickListener {
                startActivity(Intent(requireContext(), com.bustime.app.ui.addroute.AddRouteActivity::class.java))
            }
        }
        view.findViewById<View>(R.id.btnBookBus).apply {
            applyBouncyPress()
            setOnClickListener {
                startActivity(Intent(requireContext(), com.bustime.app.ui.bookbus.BookBusActivity::class.java))
            }
        }

        // Populate 1-tap quick-jump Bus Stand chips inside Spotlight card
        populateQuickStandChips(view)

        // भाषा बदलने पर स्क्रीन दोबारा बनती है: चुनी हुई जगहें वापस भरो
        val savedFrom = savedInstanceState?.getString("FROM_ID")
        val savedTo = savedInstanceState?.getString("TO_ID")
        val savedStand = savedInstanceState?.getString("STAND_ID")
        if (savedFrom != null || savedTo != null || savedStand != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val ids = listOfNotNull(savedFrom, savedTo, savedStand).toSet()
                val found = repo.getPlacesById(ids)
                fromPlace = savedFrom?.let { found[it] }
                toPlace = savedTo?.let { found[it] }
                standPlace = savedStand?.let { found[it] }
                standPlace?.let { etStand.setText(it.displayName(LanguageHelper.current())) }
                showTexts()
            }
        }
    }

    private fun populateQuickStandChips(view: View) {
        val scroll = view.findViewById<View>(R.id.standQuickScroll) ?: return
        val container = view.findViewById<LinearLayout>(R.id.standQuickChipsContainer) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val recentIds = loadRecentStandIds()
            if (recentIds.isEmpty() || !isAdded) {
                container.removeAllViews()
                scroll.visibility = View.GONE
                return@launch
            }
            val found = repo.getPlacesById(recentIds.toSet())
            val stands = recentIds.mapNotNull { found[it] }.take(MAX_RECENT_STANDS)
            if (stands.isEmpty() || !isAdded) {
                container.removeAllViews()
                scroll.visibility = View.GONE
                return@launch
            }
            val ctx = requireContext()
            val density = resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()
            val lang = LanguageHelper.current()
            val font = ResourcesCompat.getFont(ctx, R.font.plus_jakarta_sans)
            val chipBg = ContextCompat.getColor(ctx, R.color.surfaceVariant)
            val chipStroke = ContextCompat.getColor(ctx, R.color.cardStroke)
            val chipText = ContextCompat.getColor(ctx, R.color.colorPrimary)
            val ripple = ContextCompat.getColor(ctx, R.color.colorPrimaryContainer)

            container.removeAllViews()
            stands.forEach { place ->
                val shape = GradientDrawable().apply {
                    setColor(chipBg)
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), chipStroke)
                }
                val chip = TextView(ctx).apply {
                    text = place.displayName(lang)
                    textSize = 13f
                    typeface = Typeface.create(font, Typeface.BOLD)
                    setTextColor(chipText)
                    gravity = Gravity.CENTER
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    background = RippleDrawable(ColorStateList.valueOf(ripple), shape, null)
                    isClickable = true
                    isFocusable = true
                    applyBouncyPress()
                    setOnClickListener {
                        standPlace = place
                        etStand.setText(place.displayName(lang))
                        openStandResults(place)
                    }
                }
                container.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            }
            scroll.visibility = View.VISIBLE
        }
    }

    private fun loadRecentStandIds(): List<String> {
        val ctx = context ?: return emptyList()
        val raw = ctx.getSharedPreferences(PREFS_RECENT_STANDS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_RECENT_STAND_IDS, "")
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_RECENT_STANDS)
    }

    private fun saveRecentStandId(placeId: String) {
        val ctx = context ?: return
        val updated = (listOf(placeId) + loadRecentStandIds().filterNot { it == placeId })
            .take(MAX_RECENT_STANDS)
        ctx.getSharedPreferences(PREFS_RECENT_STANDS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_STAND_IDS, updated.joinToString(","))
            .apply()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("FROM_ID", fromPlace?.id)
        outState.putString("TO_ID", toPlace?.id)
        outState.putString("STAND_ID", standPlace?.id)
    }

    private fun openPicker(target: String, hint: String, standsOnly: Boolean) {
        pickTarget = target
        pickLauncher.launch(
            Intent(requireContext(), PlacePickerActivity::class.java)
                .putExtra("HINT", hint)
                .putExtra("STANDS_ONLY", standsOnly)
        )
    }

    private fun onPicked(target: String, placeId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val place = repo.getPlacesById(setOf(placeId))[placeId] ?: return@launch
            when (target) {
                "from" -> fromPlace = place
                "to" -> toPlace = place
                "stand" -> {
                    standPlace = place
                    etStand.setText(place.displayName(LanguageHelper.current()))
                    openStandResults(place)
                }
            }
            showTexts()
        }
    }

    private fun showTexts() {
        val lang = LanguageHelper.current()
        etFrom.setText(fromPlace?.displayName(lang).orEmpty())
        etTo.setText(toPlace?.displayName(lang).orEmpty())
    }

    // From -> To: नतीजे अगले पेज पर
    private fun searchRoutes() {
        val ctx = context ?: return
        val from = fromPlace
        val to = toPlace
        if (from == null || to == null) {
            Toast.makeText(ctx, R.string.select_places, Toast.LENGTH_SHORT).show()
            return
        }
        if (from.id == to.id) {
            Toast.makeText(ctx, R.string.same_place, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(ctx, SearchActivity::class.java)
                .putExtra("MODE", "route")
                .putExtra("FROM_ID", from.id)
                .putExtra("TO_ID", to.id)
        )
    }

    // बस स्टैंड: नतीजे अगले पेज पर
    private fun openStandResults(place: Place) {
        val ctx = context ?: return
        saveRecentStandId(place.id)
        view?.let { populateQuickStandChips(it) }
        startActivity(
            Intent(ctx, SearchActivity::class.java)
                .putExtra("MODE", "stand")
                .putExtra("STAND_ID", place.id)
        )
    }

    companion object {
        private const val PREFS_RECENT_STANDS = "bustime_recent_stands"
        private const val KEY_RECENT_STAND_IDS = "recent_stand_ids"
        private const val MAX_RECENT_STANDS = 5
    }
}
