package com.bustime.app.ui.community

import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Place
import com.bustime.app.models.Route
import com.bustime.app.models.RouteStop
import com.bustime.app.ui.common.StopListEditor
import com.bustime.app.ui.common.applyBouncyPress
import com.bustime.app.utils.LanguageHelper
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Community request forms; route corrections submit a complete proposed ordered stop list. */
class CommunityRequestActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var tvTopSubtitle: TextView
    private var fontUi: Typeface? = null
    private var fontMono: Typeface? = null

    private val fields = linkedMapOf<String, EditText>()
    private val spinners = linkedMapOf<String, Spinner>()
    private var routes: List<Route> = emptyList()
    private var routeStops: List<RouteStop> = emptyList()
    private var placeNames: Map<String, String> = emptyMap()
    private var allPlaces: List<Place> = emptyList()
    private var routeSpinner: Spinner? = null
    private var stopOne: Spinner? = null
    private var stopTwo: Spinner? = null
    private var correctionType: Spinner? = null
    private var missingExisting: Spinner? = null
    private var correctedStop: Spinner? = null
    private var missingCheck: CheckBox? = null
    private var timeCheck: CheckBox? = null
    private var longRouteName = ""
    private var missingSection: LinearLayout? = null
    private var timeSection: LinearLayout? = null
    private var missingPosition: Spinner? = null
    private val missingPlaceFields = mutableListOf<AutoCompleteTextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fontUi = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans)
        fontMono = ResourcesCompat.getFont(this, R.font.jetbrains_mono)

        val mode = intent.getStringExtra("mode") ?: "feedback"
        val screenTitle = when (mode) {
            "correction" -> getString(R.string.community_title_correction)
            "bus_operator" -> getString(R.string.community_title_bus)
            else -> getString(R.string.community_title_feedback)
        }
        title = screenTitle

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bgScreen))
        }

        // Clean 2-Line Transit Top Bar (matching Search & Route Detail screens)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(getColor(R.color.topBarBg))
            setPadding(dp(8), dp(10), dp(12), dp(10))
        }
        val backBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back_arrow)
            imageTintList = ColorStateList.valueOf(getColor(R.color.topBarText))
            background = ContextCompat.getDrawable(this@CommunityRequestActivity, android.R.drawable.list_selector_background)
            contentDescription = getString(R.string.back)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        }
        topBar.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

        val titleStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleStack.addView(TextView(this).apply {
            text = screenTitle
            textSize = 18f
            typeface = Typeface.create(fontUi, Typeface.BOLD)
            setTextColor(getColor(R.color.topBarText))
            maxLines = 1
        })
        tvTopSubtitle = TextView(this).apply {
            text = getString(R.string.community_review_notice)
            textSize = 12f
            typeface = fontUi
            setTextColor(getColor(R.color.topBarSubtext))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = if (mode == "correction") View.GONE else View.VISIBLE
        }
        titleStack.addView(tvTopSubtitle)
        topBar.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        page.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        // Hairline bottom divider under top bar
        page.addView(View(this).apply { setBackgroundColor(getColor(R.color.divider)) }, LinearLayout.LayoutParams(-1, dp(1)))

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(root, FrameLayout.LayoutParams(-1, -2))
        }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)

        when (mode) {
            "correction" -> loadCorrectionForm()
            "bus_operator" -> busOperatorForm()
            else -> feedbackForm()
        }
    }

    private var correctionEditor: LinearLayout? = null
    private var correctionRoute: Route? = null
    private val correctionDrafts = mutableListOf<JSONObject>()

    private fun loadCorrectionForm() {
        if (!CommunitySupabase.isConfigured()) {
            addNoticeCard(getString(R.string.community_online_not_configured))
            return
        }
        lifecycleScope.launch {
            val repo = AppRepository.getInstance(this@CommunityRequestActivity)
            routes = repo.getAllRoutes()
            if (routes.isEmpty()) {
                addNoticeCard(getString(R.string.community_no_routes))
                return@launch
            }
            allPlaces = repo.getAllPlaces()
            placeNames = allPlaces.associate { it.id to it.displayName(LanguageHelper.current()) }

            // The route is passed from the selected route's details page; keep it fixed here.
            val requested = intent.getStringExtra("route_id")
            val route = routes.firstOrNull { it.id == requested }
            if (route == null) {
                addNoticeCard(getString(R.string.community_open_route_details))
                return@launch
            }

            val label = routeLabel(route)
            tvTopSubtitle.text = label
            tvTopSubtitle.visibility = View.VISIBLE

            // Floating Royal Indigo Hero Summary Card
            addHeroCard(
                iconRes = R.drawable.ic_wrench,
                title = label,
                subtitle = getString(R.string.community_complete_route_help),
                badgeText = getString(R.string.community_complete_route_correction)
            )

            // Section Header: Complete Stop Sequence
            addSectionHeader(R.drawable.ic_route_sequence, getString(R.string.complete_stop_sequence))

            // Elevated 20dp StopListEditor Card
            val editorCard = createSurfaceCard()
            correctionEditor = LinearLayout(this@CommunityRequestActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }
            editorCard.addView(correctionEditor!!)
            root.addView(editorCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

            // Notes & Mobile Verification Card
            val detailsCard = createSurfaceCard()
            val detailsInner = LinearLayout(this@CommunityRequestActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            fieldInCard(
                target = detailsInner,
                key = "correction_note",
                label = getString(R.string.community_extra_admin_note),
                hint = getString(R.string.community_extra_admin_note),
                labelColor = getColor(R.color.colorPrimary),
                multiLine = true
            )
            fieldInCard(
                target = detailsInner,
                key = "phone",
                label = getString(R.string.community_mobile_required),
                hint = getString(R.string.ar_phone_hint),
                labelColor = getColor(R.color.colorAccent),
                isPhone = true
            )
            detailsInner.addView(TextView(this@CommunityRequestActivity).apply {
                text = getString(R.string.community_review_notice)
                textSize = 12.5f
                typeface = fontUi
                setTextColor(getColor(R.color.textSecondary))
                setPadding(dp(2), dp(6), dp(2), 0)
            })
            detailsCard.addView(detailsInner)
            root.addView(detailsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

            button(getString(R.string.community_submit_route_correction)) { submitCorrection() }
            loadStopsFor(route)
        }
    }

    private suspend fun loadStopsFor(route: Route) {
        correctionRoute = route
        routeStops = AppRepository.getInstance(this).getRouteStops(route.id).sortedBy { it.stopOrder }
        placeNames = allPlaces.associate { it.id to it.displayName(LanguageHelper.current()) }
        correctionDrafts.clear()
        routeStops.forEach { stop ->
            correctionDrafts.add(
                JSONObject()
                    .put("place_id", stop.placeId)
                    .put("name", placeNames[stop.placeId] ?: stop.placeId)
                    .put("language", LanguageHelper.current())
                    .put("time", stop.time ?: JSONObject.NULL)
            )
        }
        renderCorrectionList()
    }

    private var correctionList: StopListEditor? = null

    private fun renderCorrectionList() {
        val box = correctionEditor ?: return
        val list = correctionList ?: StopListEditor(
            this, box, allPlaces, correctionDrafts,
            removeWarning = getString(R.string.community_remove_stop_warning)
        ).also { correctionList = it }
        list.render()
    }

    private fun submitCorrection() {
        val route = correctionRoute ?: routes.getOrNull(routeSpinner?.selectedItemPosition ?: -1) ?: return
        val phone = digits(value("phone"))
        if (phone.length !in 10..13) { toast(getString(R.string.community_enter_valid_mobile)); return }
        if (correctionDrafts.size < 2) { toast(getString(R.string.community_route_two_stops)); return }
        val duplicate = correctionList?.duplicateStopIndexes()
        if (duplicate != null) {
            val message = when (LanguageHelper.current()) {
                "hi" -> "स्टॉप ${duplicate.first} और ${duplicate.second} एक ही जगह हैं। हर स्टॉप केवल एक बार रखें।"
                "pa" -> "ਸਟਾਪ ${duplicate.first} ਅਤੇ ${duplicate.second} ਇੱਕੋ ਥਾਂ ਹਨ। ਹਰ ਸਟਾਪ ਸਿਰਫ਼ ਇੱਕ ਵਾਰ ਰੱਖੋ।"
                else -> "Stops ${duplicate.first} and ${duplicate.second} refer to the same place. Each stop can appear only once."
            }
            toast(message)
            return
        }
        val proposed = JSONArray()
        correctionDrafts.forEachIndexed { index, d ->
            val name = d.optString("name").trim()
            if (name.isBlank()) { toast(getString(R.string.community_enter_stop_name, index + 1)); return }
            // JSONObject.NULL can stringify to "null"; treat it as an empty optional time.
            val t = if (d.isNull("time")) "" else d.optString("time").trim().takeUnless { it == "null" }.orEmpty()
            if (t.isNotBlank() && !t.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { toast(getString(R.string.community_stop_invalid_time, index + 1)); return }
            var id = if (d.isNull("place_id")) "" else d.optString("place_id")
            // Resolve a manually typed alias to the canonical place record, regardless
            // of whether the user typed its English, Hindi, or Punjabi name.
            val normalizedName = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
            val matchingPlace = allPlaces.firstOrNull { place ->
                place.names.values.any { alias ->
                    alias.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") == normalizedName
                }
            }
            if (id.isBlank() && matchingPlace != null) id = matchingPlace.id
            proposed.put(
                JSONObject()
                    .put("place_id", id.ifBlank { JSONObject.NULL })
                    .put("name", name)
                    .put("language", d.optString("language", LanguageHelper.current()))
                    .put("time", t.ifBlank { JSONObject.NULL })
                    .put("position", index)
            )
        }
        sendRouteEdit(route, proposed, phone)
    }

    private fun sendRouteEdit(route: Route, proposed: JSONArray, phone: String) {
        val original = JSONArray()
        routeStops.sortedBy { it.stopOrder }.forEachIndexed { index, stop ->
            original.put(JSONObject().put("place_id", stop.placeId).put("time", stop.time ?: JSONObject.NULL).put("position", index))
        }
        val body = JSONObject().put("route_id", route.id).put("request_type", "route_edit")
            .put("original_stops", original).put("proposed_stops", proposed)
            .put("change_summary", "Complete route list edited by user")
            .put("notes", value("correction_note").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("phone", phone)
        sendRequests(listOf(body), "correction_submissions", getString(R.string.community_route_correction_sent))
    }

    private fun busCategoryLabel(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "ordinary" -> getString(R.string.bus_category_ordinary)
        "luxury" -> getString(R.string.bus_category_luxury)
        "sleeper" -> getString(R.string.bus_category_sleeper)
        "sleeper plus" -> getString(R.string.bus_category_sleeper_plus)
        "seater plus" -> getString(R.string.bus_category_seater_plus)
        "seater" -> getString(R.string.bus_category_seater)
        "sleeper + seater" -> getString(R.string.bus_category_sleeper_seater)
        "custom" -> getString(R.string.bus_category_custom)
        else -> value
    }

    private fun busOperatorForm() {
        addHeroCard(
            iconRes = R.drawable.ic_bus_modern,
            title = getString(R.string.community_title_bus),
            subtitle = getString(R.string.community_bus_form_intro)
        )

        val infoCard = createSurfaceCard()
        val infoInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        fieldInCard(infoInner, "service_name", getString(R.string.community_service_name), getString(R.string.community_service_name), getColor(R.color.colorPrimary))
        fieldInCard(infoInner, "location", getString(R.string.bb_location), getString(R.string.bb_location), getColor(R.color.colorPrimary))
        infoCard.addView(infoInner)
        root.addView(infoCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val typesCard = createSurfaceCard()
        val typesInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        typesInner.addView(TextView(this).apply {
            text = getString(R.string.bus_types_select_label)
            textSize = 14.5f
            typeface = Typeface.create(fontUi, Typeface.BOLD)
            setTextColor(getColor(R.color.textPrimary))
            setPadding(0, 0, 0, dp(8))
        })
        val typeOptions = listOf(
            "Ordinary" to getString(R.string.bus_type_ordinary),
            "Seater" to getString(R.string.bus_type_seater),
            "Sleeper" to getString(R.string.bus_type_sleeper),
            "AC Bus" to getString(R.string.bus_type_ac),
            "Non-AC Bus" to getString(R.string.bus_type_non_ac),
            "Luxury Bus" to getString(R.string.bus_type_luxury),
            "Volvo Bus" to getString(R.string.bus_type_volvo),
            "Mini Bus" to getString(R.string.bus_type_mini),
            "Other" to getString(R.string.bus_type_other)
        )
        val typeChecks = linkedMapOf<String, CheckBox>()
        typeOptions.forEach { (type, label) ->
            val check = CheckBox(this).apply {
                text = label
                textSize = 14.5f
                typeface = fontUi
                setTextColor(getColor(R.color.textPrimary))
                buttonTintList = ColorStateList.valueOf(getColor(R.color.colorAccent))
            }
            typeChecks[type] = check
            typesInner.addView(check)
        }
        fieldInCard(typesInner, "bus_other_type", getString(R.string.bus_type_other), getString(R.string.bus_type_other_hint), getColor(R.color.textSecondary)).apply {
            isSingleLine = true
        }
        typesCard.addView(typesInner)
        root.addView(typesCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val contactCard = createSurfaceCard()
        val contactInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        fieldInCard(contactInner, "phone", getString(R.string.community_operator_mobile), getString(R.string.community_operator_mobile), getColor(R.color.colorAccent), isPhone = true)
        fieldInCard(contactInner, "whatsapp", getString(R.string.community_whatsapp_optional), getString(R.string.community_whatsapp_optional), getColor(R.color.colorTertiary), isPhone = true)
        fieldInCard(contactInner, "details", getString(R.string.community_other_info), getString(R.string.community_other_info), getColor(R.color.colorPrimary), multiLine = true)
        contactCard.addView(contactInner)
        root.addView(contactCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        button(getString(R.string.community_add_bus_submit)) {
            val service = value("service_name")
            val location = value("location")
            val phone = digits(value("phone"))
            val selected = typeChecks.filterValues { it.isChecked }.keys.toMutableList()
            val other = value("bus_other_type").trim()
            if ("Other" in selected) {
                if (other.isBlank()) { toast(getString(R.string.bus_type_required_error)); return@button }
                selected.remove("Other")
                selected.add(other)
            }
            if (service.isBlank() || location.isBlank() || phone.length !in 10..13 || selected.isEmpty()) {
                toast(getString(R.string.community_fill_bus_required))
                return@button
            }
            val types = JSONArray()
            selected.forEach { types.put(it) }
            val body = JSONObject()
                .put("operator_name", service)
                .put("service_name", service)
                .put("bus_types", types)
                .put("location", location)
                .put("phone", phone)
                .put("whatsapp", digits(value("whatsapp")).takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("details", value("details").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            sendRequests(listOf(body), "bus_operator_submissions", getString(R.string.community_bus_submitted))
        }
    }

    private fun feedbackForm() {
        val language = LanguageHelper.current()
        addHeroCard(
            iconRes = R.drawable.ic_feedback_chat,
            title = getString(R.string.community_title_feedback),
            subtitle = getString(R.string.community_review_notice)
        )

        val card = createSurfaceCard()
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val categoryLabels = when (language) {
            "hi" -> listOf("सुझाव", "गलत जानकारी", "ऐप की समस्या", "अन्य", "मेरा डेटा मिटाएँ")
            "pa" -> listOf("ਸੁਝਾਅ", "ਗਲਤ ਜਾਣਕਾਰੀ", "ਐਪ ਦੀ ਸਮੱਸਿਆ", "ਹੋਰ", "ਮੇਰਾ ਡਾਟਾ ਮਿਟਾਓ")
            else -> listOf("Suggestion", "Incorrect information", "App problem", "Other", "Delete my data")
        }
        spinnerInCard(inner, getString(R.string.community_feedback_category), categoryLabels, "feedback_category")
        fieldInCard(inner, "related_route", getString(R.string.community_related_route), getString(R.string.community_related_route), getColor(R.color.colorPrimary))
        fieldInCard(inner, "feedback_text", getString(R.string.community_describe_feedback), getString(R.string.community_describe_feedback), getColor(R.color.colorPrimary), multiLine = true)
        val mobileLabel = when (language) {
            "hi" -> "मोबाइल नंबर (अनिवार्य)"
            "pa" -> "ਮੋਬਾਈਲ ਨੰਬਰ (ਲਾਜ਼ਮੀ)"
            else -> "Mobile number (required)"
        }
        fieldInCard(inner, "contact", mobileLabel, mobileLabel, getColor(R.color.colorAccent), isPhone = true)
        card.addView(inner)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        button(getString(R.string.community_send_feedback)) {
            val message = value("feedback_text")
            if (message.length < 4) { toast(getString(R.string.community_please_feedback)); return@button }
            val phone = digits(value("contact"))
            if (phone.length !in 10..13) {
                toast(
                    when (language) {
                        "hi" -> "कृपया 10 से 13 अंकों का मोबाइल नंबर भरें।"
                        "pa" -> "ਕਿਰਪਾ ਕਰਕੇ 10 ਤੋਂ 13 ਅੰਕਾਂ ਦਾ ਮੋਬਾਈਲ ਨੰਬਰ ਭਰੋ।"
                        else -> "Enter a valid mobile number (10–13 digits)."
                    }
                )
                return@button
            }
            val feedbackCategories = listOf("Suggestion", "Incorrect information", "App problem", "Other", "Delete my data")
            val body = JSONObject().put("category", feedbackCategories.getOrNull(spinners["feedback_category"]?.selectedItemPosition ?: -1) ?: "Other")
                .put("related_route", value("related_route").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("message", message).put("contact", phone)
            sendRequests(listOf(body), "feedback_submissions", getString(R.string.community_thanks_feedback))
        }
    }

    private fun sendRequests(bodies: List<JSONObject>, table: String, success: String) {
        if (!CommunitySupabase.isConfigured()) { toast(getString(R.string.community_online_not_configured)); return }
        root.isEnabled = false
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { bodies.map { CommunitySupabase.insert(table, it) } }
            root.isEnabled = true
            if (results.all { it }) { toast(success); finish() }
            else toast(getString(R.string.community_send_failed) + CommunitySupabase.lastError)
        }
    }

    // ---- Modern Sunlit Regional Transit / Midnight Highway Asphalt UI Builders ----

    private fun createSurfaceCard(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        cardElevation = dp(2).toFloat()
        setCardBackgroundColor(getColor(R.color.surface))
        strokeColor = getColor(R.color.divider)
        strokeWidth = dp(1)
    }

    private fun addHeroCard(iconRes: Int, title: String, subtitle: String, badgeText: String? = null) {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@CommunityRequestActivity, R.drawable.bg_detail_hero_card)
            elevation = dp(4).toFloat()
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconFrame = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@CommunityRequestActivity, R.drawable.bg_squircle_saffron)
        }
        iconFrame.addView(ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
        }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        topRow.addView(iconFrame, LinearLayout.LayoutParams(dp(44), dp(44)))

        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(this).apply {
            text = title
            textSize = 17.5f
            typeface = Typeface.create(fontUi, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        if (!badgeText.isNullOrBlank()) {
            textCol.addView(TextView(this).apply {
                text = badgeText
                textSize = 11.5f
                typeface = Typeface.create(fontUi, Typeface.BOLD)
                setTextColor(0xFFFFDF9B.toInt())
                setPadding(0, dp(2), 0, 0)
            })
        }
        topRow.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        hero.addView(topRow)

        hero.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            typeface = fontUi
            setTextColor(0xFFDCE9F8.toInt())
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }

    private fun addSectionHeader(iconRes: Int, title: String) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(10))
        }
        header.addView(ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(getColor(R.color.colorPrimary))
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        header.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = Typeface.create(fontUi, Typeface.BOLD)
            setTextColor(getColor(R.color.textPrimary))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(9) })
        root.addView(header)
    }

    private fun addNoticeCard(message: String) {
        val card = createSurfaceCard()
        card.addView(TextView(this).apply {
            text = message
            textSize = 14.5f
            typeface = fontUi
            setTextColor(getColor(R.color.textSecondary))
            setPadding(dp(18), dp(18), dp(18), dp(18))
        })
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun fieldInCard(
        target: LinearLayout,
        key: String,
        label: String,
        hint: String,
        labelColor: Int,
        isPhone: Boolean = false,
        multiLine: Boolean = false
    ): EditText {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@CommunityRequestActivity, R.drawable.bg_stop_field)
            setPadding(dp(14), dp(10), dp(14), dp(11))
        }
        box.addView(TextView(this).apply {
            text = label.uppercase(Locale.getDefault())
            textSize = 10.5f
            letterSpacing = 0.06f
            typeface = Typeface.create(fontUi, Typeface.BOLD)
            setTextColor(labelColor)
        })
        val e = EditText(this).apply {
            this.hint = hint
            textSize = 15f
            typeface = Typeface.create(if (isPhone) fontMono else fontUi, Typeface.BOLD)
            setTextColor(getColor(R.color.textPrimary))
            setHintTextColor(getColor(R.color.textMuted))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, dp(3), 0, 0)
            if (isPhone) inputType = InputType.TYPE_CLASS_PHONE
            if (multiLine) {
                minLines = 2
                maxLines = 4
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
        }
        fields[key] = e
        box.addView(e, LinearLayout.LayoutParams(-1, -2))
        target.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return e
    }

    private fun spinnerInCard(target: LinearLayout, label: String, options: List<String>, key: String? = null): Spinner {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@CommunityRequestActivity, R.drawable.bg_stop_field)
            setPadding(dp(14), dp(10), dp(10), dp(6))
        }
        box.addView(TextView(this).apply {
            text = label.uppercase(Locale.getDefault())
            textSize = 10.5f
            letterSpacing = 0.06f
            typeface = Typeface.create(fontUi, Typeface.BOLD)
            setTextColor(getColor(R.color.colorPrimary))
        })
        val s = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@CommunityRequestActivity,
                android.R.layout.simple_spinner_dropdown_item,
                if (options.isEmpty()) listOf(getString(R.string.community_loading_stops)) else options
            )
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                    v.clearFocus()
                }
                false
            }
        }
        box.addView(s, LinearLayout.LayoutParams(-1, dp(44)))
        target.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        if (key != null) spinners[key] = s
        return s
    }

    private fun addMissingPlaceField(target: LinearLayout) {
        val field = AutoCompleteTextView(this).apply {
            hint = getString(R.string.community_missing_stop_name)
            threshold = 1
            textSize = 15f
            typeface = fontUi
            setTextColor(getColor(R.color.textPrimary))
            setHintTextColor(getColor(R.color.textMuted))
            background = ContextCompat.getDrawable(this@CommunityRequestActivity, R.drawable.bg_stop_field)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        fun languageFor(query: String): String {
            val pa = query.count { it.code in 0x0A00..0x0A7F }
            val hi = query.count { it.code in 0x0900..0x097F }
            val en = query.count { it in 'a'..'z' || it in 'A'..'Z' }
            return when {
                pa > hi && pa > en -> "pa"
                hi > pa && hi > en -> "hi"
                en > 0 && en >= pa && en >= hi -> "en"
                else -> LanguageHelper.current()
            }
        }
        fun labelFor(place: Place, lang: String): String {
            return place.displayName(lang).trim().ifBlank {
                place.names.values.firstOrNull { it.isNotBlank() }.orEmpty()
            }
        }
        var activeLanguage = LanguageHelper.current()
        var shownPlaces = allPlaces
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        fun refreshSuggestions(query: String) {
            activeLanguage = languageFor(query)
            shownPlaces = allPlaces.filter { place ->
                query.isBlank() || place.names.values.any { it.contains(query, ignoreCase = true) } ||
                    place.displayName(activeLanguage).contains(query, ignoreCase = true)
            }
            adapter.clear()
            adapter.addAll(shownPlaces.map { labelFor(it, activeLanguage) })
            adapter.notifyDataSetChanged()
        }
        refreshSuggestions("")
        field.setAdapter(adapter)
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshSuggestions(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        field.setOnItemClickListener { _, _, position, _ ->
            val place = shownPlaces.getOrNull(position)
            if (place != null) {
                field.setText(place.displayName(activeLanguage), false)
                field.setSelection(field.text.length)
            }
        }
        target.addView(field)
        missingPlaceFields.add(field)
    }

    private fun buttonIn(target: LinearLayout, label: String, action: () -> Unit) {
        target.addView(Button(this).apply { text = label; setOnClickListener { action() } })
    }
    private fun headingIn(target: LinearLayout, s: String) { textIn(target, s, 16f, true) }
    private fun textIn(target: LinearLayout, s: String, size: Float, bold: Boolean = false) {
        target.addView(TextView(this).apply {
            text = s
            textSize = size
            typeface = if (bold) Typeface.create(fontUi, Typeface.BOLD) else fontUi
            setTextColor(getColor(if (bold) R.color.textPrimary else R.color.textSecondary))
            setPadding(0, dp(10), 0, dp(5))
        })
    }
    private fun fieldIn(target: LinearLayout, key: String, hint: String): EditText {
        return fieldInCard(target, key, hint, hint, getColor(R.color.colorPrimary))
    }
    private fun spinnerIn(target: LinearLayout, hint: String, options: List<String>): Spinner {
        return spinnerInCard(target, hint, options)
    }
    private fun timePickerFieldIn(target: LinearLayout, key: String, hint: String): EditText {
        val e = fieldIn(target, key, hint)
        e.isFocusable = false; e.isClickable = true
        e.setOnClickListener {
            val now = java.util.Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                e.setText(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(e.windowToken, 0)
            }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true).show()
        }
        return e
    }

    private fun routeLabel(route: Route): String {
        fun label(id: String): String = (placeNames[id] ?: id)
            .removeSuffix("_stand")
            .replace('_', ' ')
        return "${label(route.fromId)} → ${label(route.toId)}"
    }
    private fun heading(s: String) { text(s, 16f, true) }
    private fun text(s: String, size: Float, bold: Boolean = false) { textIn(root, s, size, bold) }
    private fun field(key: String, hint: String): EditText = fieldIn(root, key, hint)
    private fun spinner(hint: String, options: List<String>, key: String? = null): Spinner = spinnerInCard(root, hint, options, key)

    private fun button(label: String, action: () -> Unit) {
        val btn = TextView(this).apply {
            text = label
            textSize = 15.5f
            typeface = Typeface.create(fontUi, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(this@CommunityRequestActivity, R.drawable.bg_saffron_cta)
            elevation = dp(3).toFloat()
            isClickable = true
            isFocusable = true
            setPadding(dp(20), dp(14), dp(20), dp(14))
            applyBouncyPress()
            setOnClickListener { action() }
        }
        root.addView(btn, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(2) })
    }

    private fun value(key: String) = fields[key]?.text?.toString()?.trim().orEmpty()
    private fun digits(s: String) = s.filter(Char::isDigit)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
}
