package com.blankdev.sidestep

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.annotation.SuppressLint
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.FrameLayout
import android.view.View
import android.view.Gravity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.content.ClipboardManager
import android.content.ClipData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.OkHttpClient
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import androidx.core.content.edit

/**
 * Shared OkHttpClient for connection pooling
 */
object NetworkClient {
    private const val NETWORK_TIMEOUT_SECONDS = 10L
    
    val client = OkHttpClient.Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}

/**
 * Main activity with URL input and history display
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        // UI Dimensions
        private const val TOOLBAR_HEIGHT_DP = 80
        private const val TOOLBAR_PADDING_START_DP = 8
        private const val TOOLBAR_PADDING_TOP_DP = 16
        private const val CONTENT_PADDING_DP = 16
        private const val INPUT_CONTAINER_PADDING_DP = 8
        private const val INPUT_CONTAINER_VERTICAL_PADDING_DP = 4
        private const val INPUT_CONTAINER_MARGIN_TOP_DP = 8
        private const val INPUT_CONTAINER_MARGIN_BOTTOM_DP = 16
        private const val URL_INPUT_PADDING_START_DP = 12
        private const val URL_INPUT_PADDING_VERTICAL_DP = 8
        private const val PROCESS_BUTTON_SIZE_DP = 48
        private const val PROCESS_BUTTON_PADDING_DP = 12
        private const val WARNING_TEXT_PADDING_HORIZONTAL_DP = 8
        private const val WARNING_TEXT_PADDING_TOP_DP = 4
        private const val WARNING_TEXT_PADDING_BOTTOM_DP = 12
        private const val GUIDE_PADDING_HORIZONTAL_DP = 24
        private const val GUIDE_PADDING_BOTTOM_DP = 16
        private const val GUIDE_LABEL_PADDING_TOP_DP = 32
        private const val GUIDE_LABEL_PADDING_BOTTOM_DP = 24
        private const val STEP_CIRCLE_SIZE_DP = 24
        private const val STEP_TEXT_PADDING_START_DP = 24
        private const val STEP_LINE_WIDTH_DP = 2
        private const val BUTTON_CORNER_RADIUS_DP = 28
        private const val BUTTON_MIN_HEIGHT_DP = 56
        private const val BUTTON_PADDING_TOP_DP = 16
        private const val COUNTER_PADDING_VERTICAL_LARGE_DP = 32
        private const val COUNTER_PADDING_TOP_DP = 16
        private const val COUNTER_PADDING_BOTTOM_DP = 8
        
        // Text Sizes
        private const val TITLE_TEXT_SIZE_SP = 28f
        private const val SUBTITLE_TEXT_SIZE_SP = 14f
        private const val URL_INPUT_TEXT_SIZE_SP = 16f
        private const val WARNING_TEXT_SIZE_SP = 12f
        private const val GUIDE_LABEL_TEXT_SIZE_SP = 12f
        private const val STEP_CIRCLE_TEXT_SIZE_SP = 12f
        private const val STEP_TEXT_SIZE_SP = 16f
        private const val COUNTER_TEXT_SIZE_SP = 14f
        
        // Styling Values
        private const val INPUT_CORNER_RADIUS_DP = 64
        private const val SECONDARY_CORNER_RADIUS_F = 32f
        private const val INPUT_BORDER_WIDTH_DP = 3
        private const val INPUT_BORDER_ALPHA = 120
        private const val SECONDARY_BG_ALPHA = 15
        private const val STEP_CIRCLE_ALPHA = 40
        private const val STEP_LINE_ALPHA = 40
        private const val ELEVATION_DP = 4f
        private const val COUNTER_SCALE_MULTIPLIER = 6f
        private const val LINE_SPACING_MULTIPLIER = 1.2f
        private const val LETTER_SPACING = 0.1f
        
        // Menu Item IDs
        private const val MENU_ITEM_SHARE = 100
        private const val MENU_ITEM_CLEAR = 101
        private const val MENU_ITEM_INFO = 102
    }

    private lateinit var urlInput: EditText
    private lateinit var historyList: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private var validationJob: Job? = null
    private var loadHistoryJob: Job? = null
    private lateinit var warningText: TextView
    private lateinit var sidestepCounter: TextView
    private lateinit var emptyStateGuide: LinearLayout
    private lateinit var contentContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate to ensure it takes effect immediately
        applyTheme()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // setup UI first to avoid crash when launched from intent
        setupUI()
        
        // Apply history retention policy
        lifecycleScope.launch {
            HistoryManager.applyRetentionPolicy(this@MainActivity)
        }
        
        loadHistory()
        

    }


    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val clearAllItem = menu.findItem(R.id.action_clear_all)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val retentionMode = prefs.getString(HistoryManager.KEY_HISTORY_RETENTION, HistoryManager.DEFAULT_RETENTION_MODE)
        
        val hasHistory = if (::historyAdapter.isInitialized && historyAdapter.currentList.isNotEmpty()) {
            true
        } else {
            // Use sync check for menu prep to avoid delay/flashing, acceptable for small file
            HistoryManager.getHistorySync(this).isNotEmpty()
        }
        
        // Only show Clear All if history retention is not "never" AND there are items in history
        clearAllItem?.isVisible = retentionMode != "never" && hasHistory
        
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_clear_all -> {
                clearHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reload history when returning to app (also applies retention)
        if (::historyList.isInitialized) {
             lifecycleScope.launch {
                HistoryManager.applyRetentionPolicy(this@MainActivity)
                loadHistory()
            }
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupUI() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getThemeColor(android.R.attr.colorBackground))
        }

        setupToolbar(rootLayout)
        handleWindowInsets(rootLayout)

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val contentPadding = CONTENT_PADDING_DP.dp()
            setPadding(contentPadding, contentPadding, contentPadding, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootLayout.addView(contentContainer)
        
        setupInputSection(contentContainer)
        setupWarningText(contentContainer)
        setupEmptyStateGuide(contentContainer)
        setupHistoryList(contentContainer)
        setupCounter(contentContainer)
        
        setContentView(rootLayout)
    }

    private fun setupToolbar(rootLayout: LinearLayout) {
        val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                TOOLBAR_HEIGHT_DP.dp()
            )
            setPadding(TOOLBAR_PADDING_START_DP.dp(), TOOLBAR_PADDING_TOP_DP.dp(), 0, 0)
            setBackgroundColor(getThemeColor(android.R.attr.colorBackground))
            
            val titleTextView = TextView(context).apply {
                text = getString(R.string.app_name)
                textSize = TITLE_TEXT_SIZE_SP
                setTypeface(null, Typeface.BOLD)
                setTextColor(getThemeColor(android.R.attr.textColorPrimary))
            }
            addView(titleTextView)
        }
        
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        rootLayout.addView(toolbar)
    }

    private fun handleWindowInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun setupInputSection(container: LinearLayout) {
        val inputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(INPUT_CONTAINER_PADDING_DP.dp(), INPUT_CONTAINER_VERTICAL_PADDING_DP.dp(), INPUT_CONTAINER_PADDING_DP.dp(), INPUT_CONTAINER_VERTICAL_PADDING_DP.dp())
            background = createInputBackground()
            elevation = ELEVATION_DP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, INPUT_CONTAINER_MARGIN_TOP_DP.dp(), 0, INPUT_CONTAINER_MARGIN_BOTTOM_DP.dp())
            }
        }
        
        val textInputLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = getString(R.string.hint_url_input)
            setHintTextColor(android.content.res.ColorStateList.valueOf(getThemeColor(android.R.attr.textColorSecondary)))
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_NONE
            layoutParams = LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
        }

        urlInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                       android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            background = null
            setPadding(URL_INPUT_PADDING_START_DP.dp(), URL_INPUT_PADDING_VERTICAL_DP.dp(), URL_INPUT_PADDING_VERTICAL_DP.dp(), URL_INPUT_PADDING_VERTICAL_DP.dp())
            textSize = URL_INPUT_TEXT_SIZE_SP
            setTextColor(getThemeColor(android.R.attr.textColorPrimary))
            
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    processUrl()
                    true
                } else false
            }
            
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    validateInput(s?.toString())
                }
            })
        }
        textInputLayout.addView(urlInput)
        inputContainer.addView(textInputLayout)
        
        val processButton = android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_go_slick)
            background = null
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            setColorFilter(getThemeColor(com.google.android.material.R.attr.colorPrimary)) 
            layoutParams = LinearLayout.LayoutParams(
                PROCESS_BUTTON_SIZE_DP.dp(),
                PROCESS_BUTTON_SIZE_DP.dp()
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            setOnClickListener { processUrl() }
        }
        inputContainer.addView(processButton)
        
        container.addView(inputContainer)
    }

    private fun setupWarningText(container: LinearLayout) {
        warningText = TextView(this).apply {
            text = ""
            textSize = WARNING_TEXT_SIZE_SP
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorError))
            setPadding(WARNING_TEXT_PADDING_HORIZONTAL_DP.dp(), WARNING_TEXT_PADDING_TOP_DP.dp(), WARNING_TEXT_PADDING_HORIZONTAL_DP.dp(), WARNING_TEXT_PADDING_BOTTOM_DP.dp())
            visibility = android.view.View.GONE
        }
        container.addView(warningText)
    }

    private fun setupEmptyStateGuide(container: LinearLayout) {
        emptyStateGuide = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(GUIDE_PADDING_HORIZONTAL_DP.dp(), 0.dp(), GUIDE_PADDING_HORIZONTAL_DP.dp(), GUIDE_PADDING_BOTTOM_DP.dp())
            visibility = View.GONE
            
            val guideLabel = TextView(this@MainActivity).apply {
                text = getString(R.string.guide_welcome_label)
                textSize = WARNING_TEXT_SIZE_SP
                setTypeface(null, Typeface.BOLD)
                setTextColor(getThemeColor(android.R.attr.textColorSecondary))
                setPadding(0, GUIDE_LABEL_PADDING_TOP_DP.dp(), 0, GUIDE_LABEL_PADDING_BOTTOM_DP.dp())
                letterSpacing = LETTER_SPACING
            }
            addView(guideLabel)

            val stepsList = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            val steps = listOf(
                getString(R.string.guide_step_1),
                getString(R.string.guide_step_2),
                getString(R.string.guide_step_3),
                getString(R.string.guide_step_4)
            )
            
            steps.forEachIndexed { index, stepText ->
                addGuideStep(stepsList, index, stepText, steps.size)
            }
            addView(stepsList)
            
            val buttonContainer = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, BUTTON_PADDING_TOP_DP.dp(), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val defaultButton = com.google.android.material.button.MaterialButton(this@MainActivity).apply {
                text = getString(R.string.btn_set_default)
                cornerRadius = BUTTON_CORNER_RADIUS_DP.dp()
                insetTop = 0
                insetBottom = 0
                minHeight = BUTTON_MIN_HEIGHT_DP.dp()
                setOnClickListener { openDefaultHandlerSettings() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            buttonContainer.addView(defaultButton)
            addView(buttonContainer)
        }
        container.addView(emptyStateGuide)
    }

    private fun addGuideStep(parent: LinearLayout, index: Int, stepText: String, totalSteps: Int) {
        val stepRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val leftColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(STEP_CIRCLE_SIZE_DP.dp(), LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val circleSize = STEP_CIRCLE_SIZE_DP.dp()
        val stepCircle = TextView(this).apply {
            text = (index + 1).toString()
            textSize = WARNING_TEXT_SIZE_SP
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val bgColor = if (index == 0) {
                getThemeColor(com.google.android.material.R.attr.colorPrimary)
            } else {
                androidx.core.graphics.ColorUtils.setAlphaComponent(getThemeColor(android.R.attr.textColorSecondary), STEP_CIRCLE_ALPHA)
            }
            val textColor = if (index == 0) {
                getThemeColor(com.google.android.material.R.attr.colorOnPrimary)
            } else {
                getThemeColor(android.R.attr.textColorSecondary)
            }
            setTextColor(textColor)
            
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(bgColor)
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(circleSize, circleSize)
        }
        leftColumn.addView(stepCircle)

        if (index < totalSteps - 1) {
            val lineSegment = View(this).apply {
                setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(getThemeColor(android.R.attr.textColorSecondary), STEP_LINE_ALPHA))
                layoutParams = LinearLayout.LayoutParams(STEP_LINE_WIDTH_DP.dp(), LinearLayout.LayoutParams.MATCH_PARENT)
            }
            leftColumn.addView(lineSegment)
        }
        stepRow.addView(leftColumn)

        val stepTextView = TextView(this).apply {
            text = stepText
            textSize = 16f
            setPadding(STEP_TEXT_PADDING_START_DP.dp(), 0, 0, 0) 
            setTextColor(if (index == 0) getThemeColor(android.R.attr.textColorPrimary) else getThemeColor(android.R.attr.textColorSecondary))
            setLineSpacing(0f, 1.2f)
        }
        stepRow.addView(stepTextView)

        parent.addView(stepRow)
    }

    private fun setupHistoryList(container: LinearLayout) {
        historyAdapter = HistoryAdapter(
            onItemClick = { entry ->
                processAndNavigate(entry.originalUrl, skipImmediateCheck = true, skipHistoryUpdate = true)
            },
            onMenuClick = { view, entry -> showHistoryMenu(view, entry) },
            themeColorProvider = { attr -> getThemeColor(attr) },
            rippleDrawableProvider = { createRippleBackground() },
            dpProvider = { value -> value.dp() },
            titleProvider = { entry -> getDisplayTitle(entry) },
            subtitleProvider = { entry -> getDisplaySubtitle(entry) }
        )
        
        historyList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
            itemAnimator = null 
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        container.addView(historyList)
    }

    private fun setupCounter(container: LinearLayout) {
        sidestepCounter = TextView(this).apply {
            textSize = SUBTITLE_TEXT_SIZE_SP
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemeColor(android.R.attr.textColorSecondary))
            gravity = Gravity.CENTER
            setPadding(0, 16.dp(), 0, 8.dp())
            visibility = View.GONE
        }
        container.addView(sidestepCounter)
    }
    
    private fun validateInput(text: String?) {
        if (text.isNullOrEmpty()) {
            warningText.text = ""
            warningText.visibility = android.view.View.GONE
            return
        }
        
        val isValid = android.util.Patterns.WEB_URL.matcher(text).matches()
        if (isValid) {
            warningText.text = ""
            warningText.visibility = android.view.View.GONE
        } else {
            warningText.text = getString(R.string.error_invalid_url)
            warningText.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        if (theme.resolveAttribute(attr, typedValue, true)) {
            // Check if it's a color resource or a raw color data
            return if (typedValue.resourceId != 0) {
                try {
                    androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId)
                } catch (e: android.content.res.Resources.NotFoundException) {
                    // Fallback for cases where resourceId is not a color (e.g. a drawable like listDivider)
                    typedValue.data
                }
            } else {
                typedValue.data
            }
        }
        return android.graphics.Color.MAGENTA
    }

    private var isKpiMode: Boolean? = null
    private var lastCountValue: Long? = null

    private fun updateCounterUI(history: List<HistoryManager.HistoryEntry>) {
        if (!::sidestepCounter.isInitialized) return
        
        val count = getSidestepCount()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val retention = prefs.getString(HistoryManager.KEY_HISTORY_RETENTION, HistoryManager.DEFAULT_RETENTION_MODE)
        val historyEmpty = history.isEmpty() || retention == "never"
        val showWelcome = shouldShowWelcomeGuide()
        
        // Hide counter if welcome guide is visible
        if (historyEmpty && showWelcome) {
            sidestepCounter.text = ""
            sidestepCounter.visibility = View.GONE
        } else if (isKpiMode == historyEmpty && lastCountValue == count) {
            // Avoid redundant updates if count and mode haven't changed
            return
        } else {
            sidestepCounter.visibility = View.VISIBLE
            isKpiMode = historyEmpty
            lastCountValue = count
            
            if (historyEmpty) {
                setupKpiCounter(count)
            } else {
                setupStandardCounter(count)
            }
        }
    }

    private fun setupKpiCounter(count: Long) {
        // KPI Format
        val countText = count.toString()
        val label = "\n" + getString(R.string.label_sidesteps_performed)
        val fullText = "$countText$label"
        
        val spannable = android.text.SpannableString(fullText)
        
        // Style the count: Much Larger (6x), Primary Color, BOLD
        val primaryColor = getThemeColor(com.google.android.material.R.attr.colorPrimary)
        
        spannable.setSpan(
            android.text.style.RelativeSizeSpan(COUNTER_SCALE_MULTIPLIER), 
            0, countText.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(primaryColor),
            0, countText.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            android.text.style.StyleSpan(Typeface.BOLD),
            0, countText.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        sidestepCounter.text = spannable
        sidestepCounter.setTypeface(null, Typeface.NORMAL) // Unbold the label
        sidestepCounter.textSize = 14f // Base size for the label
        sidestepCounter.setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
        sidestepCounter.setPadding(0, COUNTER_PADDING_VERTICAL_LARGE_DP.dp(), 0, COUNTER_PADDING_VERTICAL_LARGE_DP.dp())
        sidestepCounter.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun setupStandardCounter(count: Long) {
        // Standard Format
        sidestepCounter.text = resources.getQuantityString(R.plurals.label_sidesteps_performed, count.toInt(), count)
        sidestepCounter.textSize = 14f
        sidestepCounter.setTypeface(null, Typeface.BOLD) // Keep footer bold
        sidestepCounter.setTextColor(getThemeColor(android.R.attr.textColorSecondary))
        sidestepCounter.setPadding(0, COUNTER_PADDING_TOP_DP.dp(), 0, COUNTER_PADDING_BOTTOM_DP.dp())
        
        sidestepCounter.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            0f
        )
    }
    
    private fun createInputBackground(): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        shape.cornerRadius = INPUT_CORNER_RADIUS_DP.dp().toFloat() // Pill shape
        
        // Use theme surface color
        shape.setColor(getThemeColor(com.google.android.material.R.attr.colorSurface))
        
        // Border color based on theme (using textColorSecondary or similar for border)
        val borderColor = getThemeColor(android.R.attr.textColorSecondary)
        // Revert to thicker border (3dp) and higher alpha (120) per user feedback
        val alphaBorder = androidx.core.graphics.ColorUtils.setAlphaComponent(borderColor, INPUT_BORDER_ALPHA)
        shape.setStroke(INPUT_BORDER_WIDTH_DP, alphaBorder)
        
        return shape
    }
    
    private fun createMaterialFabBackground(): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.shape = android.graphics.drawable.GradientDrawable.OVAL
        // Use Primary Color
        shape.setColor(getThemeColor(com.google.android.material.R.attr.colorPrimary))
        return shape
    }

    private fun createSecondaryBackground(): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        shape.cornerRadius = SECONDARY_CORNER_RADIUS_F
        // Very subtle background color related to surface/secondary
        val baseColor = getThemeColor(android.R.attr.textColorSecondary)
        shape.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, SECONDARY_BG_ALPHA))
        return shape
    }

    private fun openDefaultHandlerSettings() {
        try {
            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, 
                    "package:$packageName".toUri())
            } else {
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:$packageName".toUri())
            }
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }



    
    
    


    private fun processUrl() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            return
        }
        if (!android.util.Patterns.WEB_URL.matcher(url).matches()) {
            Toast.makeText(this, "Invalid URL format", Toast.LENGTH_SHORT).show()
            return
        }
        processAndNavigate(url)
    }

    private data class UrlProcessingResult(
        val unshortenedUrl: String,
        val cleanedUrl: String,
        val redirectUrl: String,
        val initialTitle: String?
    )

    private fun processAndNavigate(url: String, skipImmediateCheck: Boolean = false, skipHistoryUpdate: Boolean = false) {
        markUrlProcessed()
        val sanitizedUrl = UrlCleaner.ensureProtocol(url)
        
        lifecycleScope.launch {
            try {
                val result = processUrlInBackground(sanitizedUrl)
                
                handleNavigation(result.redirectUrl, result.unshortenedUrl, skipImmediateCheck)
                
                if (!skipHistoryUpdate) {
                    saveToHistory(url, result)
                }
                
                updateUiAfterNavigation()

            } catch (e: java.io.IOException) {
                if (!isDestroyed && !isFinishing) {
                    Toast.makeText(this@MainActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: java.net.URISyntaxException) {
                if (!isDestroyed && !isFinishing) {
                    Toast.makeText(this@MainActivity, "Invalid URL: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun markUrlProcessed() {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putBoolean("has_processed_url", true)
        }
    }

    private suspend fun processUrlInBackground(url: String): UrlProcessingResult {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Step 1: Unshorten
        val shouldUnshorten = prefs.getBoolean(SettingsActivity.KEY_UNSHORTEN_URLS, true)
        val resolveHtml = prefs.getBoolean(SettingsActivity.KEY_RESOLVE_HTML_REDIRECTS, true)
        val unshortenedUrl = if (shouldUnshorten) {
            try {
                UrlUnshortener.unshorten(url, resolveHtml)
            } catch (e: java.io.IOException) {
                url
            }
        } else {
            url
        }
        
        // Step 2: Extract initial title
        val initialTitle = fetchInitialTitle(unshortenedUrl)

        // Step 3: Determine Redirect URL
        val shouldRemoveTracking = prefs.getBoolean(SettingsActivity.KEY_REMOVE_TRACKING, true)
        val cleanedUrl = if (shouldRemoveTracking) {
            UrlCleaner.cleanUrl(unshortenedUrl)
        } else {
            unshortenedUrl
        }
        val redirectUrl = SettingsUtils.resolveTargetUrl(this, cleanedUrl, unshortenedUrl)
        
        return UrlProcessingResult(unshortenedUrl, cleanedUrl, redirectUrl, initialTitle)
    }

    private fun fetchInitialTitle(url: String): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(SettingsActivity.KEY_PREVIEW_FETCH, true)) {
            return UrlCleaner.getServiceName(url)
        }

        return when {
            UrlCleaner.isTwitterOrXUrl(url) -> {
                val username = getTwitterUsername(url)
                if (username != null) "@$username" else null
            }
            UrlCleaner.isTikTokUrl(url) -> {
                val username = getTikTokUsername(url)
                if (username != null) "@$username" else null
            }
            UrlCleaner.isRedditUrl(url) -> getRedditTitle(url)
            else -> null
        }
    }

    private fun handleNavigation(redirectUrl: String, unshortenedUrl: String, skipImmediateCheck: Boolean) {
        if (isDestroyed || isFinishing) return
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isImmediate = skipImmediateCheck || prefs.getBoolean(SettingsActivity.KEY_IMMEDIATE_NAVIGATION, true)
        
        if (isImmediate) {
            navigateToUrl(redirectUrl, unshortenedUrl)
        } else {
            Toast.makeText(this, "Link processed and added to history", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToUrl(redirectUrl: String, originalHostUrl: String) {
        try {
            val finalRedirectUrl = UrlCleaner.ensureProtocol(redirectUrl)
            
            // Determine if we should use in-app WebView
            if (SettingsUtils.shouldOpenInWebView(this, originalHostUrl)) {
                val webIntent = Intent(this, WebViewActivity::class.java).apply {
                    putExtra(WebViewActivity.EXTRA_URL, finalRedirectUrl)
                }
                startActivity(webIntent)
            } else {
                launchExternalIntent(finalRedirectUrl)
            }
            
            incrementSidestepCount()
        } catch (e: android.content.ActivityNotFoundException) {
            if (!isDestroyed && !isFinishing) {
                Toast.makeText(this, "No app available to open this URL", Toast.LENGTH_LONG).show()
            }
        } catch (e: java.net.URISyntaxException) {
            if (!isDestroyed && !isFinishing) {
                Toast.makeText(this, "Invalid URL format: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchExternalIntent(url: String) {
        val redirectIntent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        val handlers = packageManager.queryIntentActivities(redirectIntent, 0)
        val selfHandler = handlers.find { it.activityInfo.packageName == packageName }
        
        if (selfHandler != null) {
            val otherHandler = handlers.firstOrNull { it.activityInfo.packageName != packageName }
            if (otherHandler != null) {
                redirectIntent.setPackage(otherHandler.activityInfo.packageName)
                startActivity(redirectIntent)
            } else {
                // If we are the only handler, use WebView
                val webIntent = Intent(this, WebViewActivity::class.java).apply {
                    putExtra(WebViewActivity.EXTRA_URL, url)
                }
                startActivity(webIntent)
            }
        } else {
            startActivity(redirectIntent)
        }
    }

    private suspend fun saveToHistory(originalUrl: String, result: UrlProcessingResult) {
        val entry = HistoryManager.HistoryEntry(
            originalUrl = originalUrl,
            cleanedUrl = result.cleanedUrl,
            processedUrl = result.redirectUrl,
            timestamp = System.currentTimeMillis(),
            title = result.initialTitle,
            unshortenedUrl = result.unshortenedUrl
        )
        
        HistoryManager.addToHistory(this, entry)
        HistoryManager.applyRetentionPolicy(this)
        
        // Trigger preview fetch immediately after saving
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getString(HistoryManager.KEY_HISTORY_RETENTION, HistoryManager.DEFAULT_RETENTION_MODE) != "never") {
            val savedEntry = HistoryManager.getHistory(this).firstOrNull { 
                it.originalUrl == originalUrl && it.timestamp == entry.timestamp 
            }
            if (savedEntry != null) {
                fetchPreview(savedEntry)
            }
        }
        
        if (!isDestroyed && !isFinishing) {
            updateCounterUI(HistoryManager.getHistory(this))
        }
    }

    private fun updateUiAfterNavigation() {
        if (!isDestroyed && !isFinishing) {
            urlInput.text.clear()
            loadHistory {
                historyList.smoothScrollToPosition(0)
            }
            urlInput.clearFocus()
        }
    }





    private fun shouldShowWelcomeGuide(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("has_processed_url", false)) return false
        
        val commonDomains = listOf("twitter.com", "reddit.com", "instagram.com", "tiktok.com", "youtube.com", "open.spotify.com")
        return commonDomains.none { SettingsUtils.isAppDefaultHandlerForDomain(this, it) }
    }

    private fun loadHistory(runAfterUpdate: (() -> Unit)? = null) {
        loadHistoryJob?.cancel()
        loadHistoryJob = lifecycleScope.launch {
            val history = HistoryManager.getHistory(this@MainActivity)
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
            val retentionMode = prefs.getString(HistoryManager.KEY_HISTORY_RETENTION, HistoryManager.DEFAULT_RETENTION_MODE)
            
            val sidestepCount = getSidestepCount()
            val showWelcome = shouldShowWelcomeGuide() && history.isEmpty()
            
            if (retentionMode == "never") {
                historyList.visibility = View.GONE
                emptyStateGuide.visibility = if (showWelcome) View.VISIBLE else View.GONE
                
                historyAdapter.submitList(emptyList()) {
                    // Ensure menu is invalidated after list clears
                    invalidateOptionsMenu()
                    runAfterUpdate?.invoke()
                }
                
                // If Welcome Guide is VISIBLE, set weight to 1, else 0 wrapping content
                val params = emptyStateGuide.layoutParams as LinearLayout.LayoutParams
                params.height = if (showWelcome) 0 else LinearLayout.LayoutParams.WRAP_CONTENT
                params.weight = if (showWelcome) 1f else 0f
                emptyStateGuide.layoutParams = params
                
                updateCounterUI(emptyList())
                return@launch
            }
            
            if (history.isEmpty()) {
                historyList.visibility = View.GONE
                emptyStateGuide.visibility = if (showWelcome) View.VISIBLE else View.GONE
                
                // If Welcome Guide is VISIBLE, set weight to 1
                val params = emptyStateGuide.layoutParams as LinearLayout.LayoutParams
                params.height = if (showWelcome) 0 else LinearLayout.LayoutParams.WRAP_CONTENT
                params.weight = if (showWelcome) 1f else 0f
                emptyStateGuide.layoutParams = params
                
                 historyAdapter.submitList(emptyList()) {
                     // Ensure menu is invalidated after list clears
                     invalidateOptionsMenu()
                     runAfterUpdate?.invoke()
                 }
            } else {
                emptyStateGuide.visibility = View.GONE
                historyAdapter.submitList(history) {
                    // Set visible only AFTER the list is applied to avoid flashing old state
                    historyList.visibility = View.VISIBLE
                    // Update menu visibility after the list has been updated and diffed
                    invalidateOptionsMenu()
                    runAfterUpdate?.invoke()
                }
            }
            
            updateCounterUI(history)
        }
    }

    private fun updateHistoryItem(entry: HistoryManager.HistoryEntry) {
        val currentList = historyAdapter.currentList.toMutableList()
        val index = currentList.indexOfFirst { it.originalUrl == entry.originalUrl && it.timestamp == entry.timestamp }
        if (index != -1) {
            currentList[index] = entry
            historyAdapter.submitList(currentList)
        }
    }

    /**
     * RecyclerView Adapter for History
     */

    private fun getDisplaySubtitle(entry: HistoryManager.HistoryEntry): String {
        val contentUrl = entry.unshortenedUrl ?: entry.originalUrl
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(SettingsActivity.KEY_PREVIEW_FETCH, true)) {
             return getFallbackSubtitle(entry, contentUrl)
        }

        val isTwitter = UrlCleaner.isTwitterOrXUrl(contentUrl)
        val isTikTok = UrlCleaner.isTikTokUrl(contentUrl)
        val isReddit = UrlCleaner.isRedditUrl(contentUrl)
        val isYouTube = UrlCleaner.isYouTubeUrl(contentUrl)

        return when {
            isTwitter -> {
                val timestamp = entry.postedTimestamp ?: getTwitterTimestamp(contentUrl)
                formatTimestamp(timestamp) { "Posted on $it" } ?: getFallbackSubtitle(entry, contentUrl)
            }
            isTikTok -> {
                val timestamp = entry.postedTimestamp ?: getTikTokTimestamp(contentUrl)
                formatTimestamp(timestamp) { "Posted on $it" } ?: getFallbackSubtitle(entry, contentUrl)
            }
            isReddit -> {
                formatTimestamp(entry.postedTimestamp) { "Posted on $it" } ?: getFallbackSubtitle(entry, contentUrl)
            }
            isYouTube -> {
                val timestamp = entry.postedTimestamp
                val authorOrUrl = entry.author ?: getFallbackSubtitle(entry, contentUrl)
                if (timestamp != null && timestamp > 0) {
                    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    "$authorOrUrl • ${sdf.format(java.util.Date(timestamp))}"
                } else authorOrUrl
            }
            else -> getFallbackSubtitle(entry, contentUrl)
        }
    }

    private fun formatTimestamp(timestamp: Long?, format: (String) -> String): String? {
        if (timestamp != null && timestamp > 0) {
            val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
            return format(sdf.format(java.util.Date(timestamp)))
        }
        return null
    }


    private fun getFallbackSubtitle(entry: HistoryManager.HistoryEntry, contentUrl: String): String {
        val title = getDisplayTitle(entry)
        val serviceName = UrlCleaner.getServiceName(contentUrl)
        return if (title == serviceName) {
            UrlCleaner.getCleanedPath(contentUrl)
        } else {
            UrlCleaner.getCleanedDisplayUrl(contentUrl)
        }
    }


    private fun getRedditTitle(url: String?): String? {
        if (url == null) return null
        return try {
            val uri = url.toUri()
            val pathSegments = uri.pathSegments
            
            // Patterns: 
            // /r/Subreddit/comments/ID/post_title/
            // /user/Username/comments/ID/post_title/
            // /u/Username/
            
            val rIndex = pathSegments.indexOf("r")
            val uIndex = if (rIndex == -1) pathSegments.indexOf("u").let { if (it == -1) pathSegments.indexOf("user") else it } else -1
            
            if (rIndex != -1 && rIndex + 1 < pathSegments.size) {
                val subreddit = "r/${pathSegments[rIndex + 1]}"
                val commentsIndex = pathSegments.indexOf("comments")
                if (commentsIndex != -1 && commentsIndex + 2 < pathSegments.size) {
                    // We have a post title
                    val postTitle = pathSegments[commentsIndex + 2].replace("_", " ").replace("-", " ")
                    "$postTitle ($subreddit)"
                } else {
                    subreddit
                }
            } else if (uIndex != -1 && uIndex + 1 < pathSegments.size) {
                "u/${pathSegments[uIndex + 1]}"
            } else null
        } catch (e: java.net.URISyntaxException) {
            null
        }
    }

    private fun getRedditTimestamp(url: String?): Long? {
        if (url == null) return null
        return try {
            val uri = url.toUri()
            val pathSegments = uri.pathSegments
            val commentsIndex = pathSegments.indexOf("comments")
            // Return null for now as we don't use idLong
            if (commentsIndex != -1 && commentsIndex + 1 < pathSegments.size) null else null
        } catch (e: java.net.URISyntaxException) {
            null
        }
    }

    private fun getYouTubeTitle(url: String?): String? {
        if (url == null) return null
        return try {
            val uri = url.toUri()
            // youtube.com/watch?v=VIDEO_ID
            // youtu.be/VIDEO_ID
            if (uri.host?.contains("youtube.com") == true) {
                val v = uri.getQueryParameter("v")
                if (v != null) "YouTube Video ($v)" else "YouTube Video"
            } else if (uri.host?.contains("youtu.be") == true) {
                val v = uri.pathSegments.firstOrNull()
                if (v != null) "YouTube Video ($v)" else "YouTube Video"
            } else null
        } catch (e: java.net.URISyntaxException) {
            null
        }
    }

    private fun getYouTubeTimestamp(@Suppress("UNUSED_PARAMETER") url: String?): Long? {
        // Can't easily get timestamp from YouTube URL without API
        return null
    }

    private fun getTikTokTimestamp(url: String?): Long? {
         return try {
             // tiktok.com/@user/video/VIDEO_ID
             url?.toUri()?.let { uri ->
                 val pathSegments = uri.pathSegments
                 val videoIndex = pathSegments.indexOf("video")
                 if (videoIndex != -1 && videoIndex + 1 < pathSegments.size) {
                     pathSegments[videoIndex + 1].toLongOrNull()?.let { videoId ->
                         // TikTok Snowflake: first 32 bits are timestamp in seconds
                         (videoId ushr 32) * 1000L
                     }
                 } else null
             }
         } catch (e: java.net.URISyntaxException) {
             null
         }
    }

    private fun fetchPreview(entry: HistoryManager.HistoryEntry) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(SettingsActivity.KEY_PREVIEW_FETCH, true)) return

        lifecycleScope.launch(Dispatchers.IO) {
            val fetchUrl = entry.cleanedUrl
            val data = PreviewFetcher.fetchPreview(url = fetchUrl)
            
            // Protect manually extracted usernames from being overwritten by generic alternative frontend SEO titles
            val isSocial = isTwitterOrXUrl(fetchUrl) || UrlCleaner.isTikTokUrl(fetchUrl) || 
                          UrlCleaner.isRedditUrl(fetchUrl) || UrlCleaner.isYouTubeUrl(fetchUrl) ||
                          UrlCleaner.isMediumUrl(fetchUrl) || UrlCleaner.isImdbUrl(fetchUrl)
            val updatedTitle = if (isSocial && (entry.title?.startsWith("@") == true || entry.title?.contains("r/") == true)) {
                entry.title // Keep our @username or post title
            } else {
                val fetchedTitle = data?.title
                val validFetchedTitle = if (fetchedTitle.isNullOrBlank()) null else fetchedTitle
                val validEntryTitle = if (entry.title.isNullOrBlank()) null else entry.title

                validFetchedTitle ?: validEntryTitle ?: UrlCleaner.getServiceName(fetchUrl)
            }

            val updatedEntry = entry.copy(
                title = updatedTitle,
                author = data?.author ?: entry.author,
                description = data?.description,
                isPreviewFetched = true,
                postedTimestamp = data?.timestamp ?: entry.postedTimestamp
            )
            
            // Update in storage
            HistoryManager.updateEntry(this@MainActivity, updatedEntry)
            
            // Refresh UI if still valid
            withContext(Dispatchers.Main) {
                if (!isDestroyed && !isFinishing) {
                   updateHistoryItem(updatedEntry)
                }
            }
        }
    }

    private fun getDisplayTitle(entry: HistoryManager.HistoryEntry): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(SettingsActivity.KEY_PREVIEW_FETCH, true)) {
             return entry.title.takeUnless { it.isNullOrBlank() } 
                 ?: UrlCleaner.getServiceName(entry.unshortenedUrl ?: entry.originalUrl)
        }

        val contentUrl = entry.unshortenedUrl ?: entry.originalUrl
        val validTitle = entry.title.takeUnless { it.isNullOrBlank() }

        return validTitle ?: resolveServiceTitle(contentUrl)
    }

    private data class ServiceTitleRule(
        val condition: (String) -> Boolean,
        val titleProvider: (String) -> String?
    )

    private val serviceTitleRules = listOf(
        ServiceTitleRule({ UrlCleaner.isTwitterOrXUrl(it) }, { getTwitterUsername(it) ?: "Twitter" }),
        ServiceTitleRule({ UrlCleaner.isTikTokUrl(it) }, { getTikTokUsername(it) ?: "TikTok" }),
        ServiceTitleRule({ UrlCleaner.isRedditUrl(it) }, { getRedditTitle(it) ?: "Reddit" }),
        ServiceTitleRule({ UrlCleaner.isYouTubeUrl(it) }, { getYouTubeTitle(it) ?: "YouTube" }),
        ServiceTitleRule({ UrlCleaner.isMediumUrl(it) }, { "Medium" }),
        ServiceTitleRule({ UrlCleaner.isImdbUrl(it) }, { "IMDb" }),
        ServiceTitleRule({ UrlCleaner.isWikipediaUrl(it) }, { "Wikipedia" }),
        ServiceTitleRule({ UrlCleaner.isGoodreadsUrl(it) }, { "Goodreads" }),
        ServiceTitleRule({ UrlCleaner.isGoogleMapsUrl(it) }, { "Google Maps" }),
        ServiceTitleRule({ UrlCleaner.isGeniusUrl(it) }, { "Genius" }),
        ServiceTitleRule({ UrlCleaner.isGitHubUrl(it) }, { "GitHub" }),
        ServiceTitleRule({ UrlCleaner.isStackOverflowUrl(it) }, { "StackOverflow" }),
        ServiceTitleRule({ it.contains("nytimes.com") }, { "The New York Times" }),
        ServiceTitleRule({ it.contains("instagram.com") }, { "Instagram" })
    )

    private fun resolveServiceTitle(url: String): String {
        return serviceTitleRules.firstNotNullOfOrNull { rule ->
            if (rule.condition(url)) rule.titleProvider(url) else null
        } ?: UrlCleaner.getServiceName(url)
    }

    private fun extractDomain(url: String): String {
        return try {
            url.toUri().host ?: url
        } catch (e: java.net.URISyntaxException) {
            url
        }
    }
    
    // Check methods
    private fun isTwitterOrXUrl(url: String?): Boolean {
        return UrlCleaner.isTwitterOrXUrl(url)
    }
    
    private fun getTwitterUsername(url: String?): String? {
        if (url == null) return null
        return try {
            val uri = url.toUri()
            val pathSegments = uri.pathSegments
            if (pathSegments.size >= 1) {
                pathSegments[0]
            } else null
        } catch (e: java.io.IOException) {
            null
        }
    }
    
    private fun getTikTokUsername(url: String?): String? {
        if (url == null) return null
        return try {
            val uri = url.toUri()
            val pathSegments = uri.pathSegments
            pathSegments.find { it.startsWith("@") }?.substring(1)
        } catch (e: java.io.IOException) {
            null
        }
    }
    
    private fun getTwitterTimestamp(url: String?): Long? {
        return try {
            url?.let {
                val pattern = java.util.regex.Pattern.compile("status/(\\d+)")
                val matcher = pattern.matcher(it)
                if (matcher.find()) {
                    matcher.group(1)?.toLongOrNull()?.let { tweetId -> 
                       (tweetId shr 22) + 1288834974657L
                    }
                } else null
            }
        } catch (e: java.io.IOException) {
            null
        }
    }
    
    private fun showHistoryMenu(view: android.view.View, entry: HistoryManager.HistoryEntry) {
        val popup = android.widget.PopupMenu(this, view)
        
        // Add menu items with icons
        // Add menu items with icons
        val shareItem = popup.menu.add(0, MENU_ITEM_SHARE, 0, getString(R.string.menu_share)).apply {
            setIcon(R.drawable.ic_share)
        }
        val clearItem = popup.menu.add(0, MENU_ITEM_CLEAR, 1, getString(R.string.menu_clear_item)).apply {
            setIcon(R.drawable.ic_check)
        }
        val infoItem = popup.menu.add(0, MENU_ITEM_INFO, 2, getString(R.string.menu_get_info)).apply {
            setIcon(R.drawable.ic_info)
        }
        
        // Tint all icons to ensure consistency and visibility in all themes
        val itemsToTint = listOf(shareItem, clearItem, infoItem)
        val tintColor = getThemeColor(android.R.attr.textColorPrimary)
        
        itemsToTint.forEach { item ->
            val icon = item.icon
            if (icon != null) {
                val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(icon)
                androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, tintColor)
                item.icon = wrapped
            }
        }
        
        // Force icons to show (Reflection hack for standard PopupMenu)
        // Note: Using DiscouragedPrivateApi to show icons in PopupMenu. 
        // This is documented to work in current Android versions but may break in the future.
        @SuppressLint("DiscouragedPrivateApi")
        try {
            val fieldMPopup = android.widget.PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popup)
            mPopup.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                .invoke(mPopup, true)
        } catch (e: ReflectiveOperationException) {
            // Ignore if fails, icons just won't show
        }
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_ITEM_SHARE -> {
                    shareUrl(entry.processedUrl)
                    true
                }
                MENU_ITEM_CLEAR -> {
                    clearHistoryItem(entry)
                    true
                }
                MENU_ITEM_INFO -> {
                    showUrlInfo(entry)
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    private fun showUrlInfo(entry: HistoryManager.HistoryEntry) {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = 24.dp()
            setPadding(p, 0, p, 16.dp())
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(rootLayout)
        }

        fun addDetailRow(label: String, content: String, actionIcon: Int, onAction: () -> Unit) {
            val labelView = TextView(this).apply {
                text = label
                textSize = WARNING_TEXT_SIZE_SP
                setTextColor(getThemeColor(android.R.attr.textColorSecondary))
                setPadding(0, 12.dp(), 0, 4.dp())
            }
            rootLayout.addView(labelView)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val contentView = TextView(this).apply {
                text = content
                textSize = 15f
                setTextColor(getThemeColor(android.R.attr.textColorPrimary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(contentView)

            val actionBtn = android.widget.ImageButton(this).apply {
                setImageResource(actionIcon)
                background = createRippleBackground()
                setColorFilter(getThemeColor(com.google.android.material.R.attr.colorPrimary))
                setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
                setOnClickListener { onAction() }
            }
            row.addView(actionBtn)
            rootLayout.addView(row)

            val divider = android.view.View(this).apply {
                setBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorOutlineVariant))
                alpha = 0.2f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                    topMargin = 8.dp()
                }
            }
            rootLayout.addView(divider)
        }
        
        val displayTitle = getDisplayTitle(entry)
        addDetailRow(getString(R.string.label_title), displayTitle, R.drawable.ic_content_copy) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Page Title", displayTitle))
            Toast.makeText(this, getString(R.string.toast_title_copied), Toast.LENGTH_SHORT).show()
        }

        val displayOriginal = entry.originalUrl
        addDetailRow(getString(R.string.label_original), displayOriginal, R.drawable.ic_content_copy) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Original URL", displayOriginal))
            Toast.makeText(this, getString(R.string.toast_original_copied), Toast.LENGTH_SHORT).show()
        }

        val displayUnshortened = entry.unshortenedUrl ?: entry.originalUrl
        if (displayUnshortened != displayOriginal) {
            addDetailRow(getString(R.string.label_unshortened), displayUnshortened, R.drawable.ic_content_copy) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Unshortened URL", displayUnshortened))
                Toast.makeText(this, getString(R.string.toast_unshortened_copied), Toast.LENGTH_SHORT).show()
            }
        }

        val displayCleaned = entry.cleanedUrl
        if (displayCleaned != displayOriginal && displayCleaned != displayUnshortened) {
            addDetailRow(getString(R.string.label_cleaned), displayCleaned, R.drawable.ic_content_copy) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Cleaned URL", displayCleaned))
                Toast.makeText(this, getString(R.string.toast_cleaned_copied), Toast.LENGTH_SHORT).show()
            }
        }

        val contentUrl = entry.unshortenedUrl ?: entry.originalUrl
        val effectiveProcessedUrl = SettingsUtils.resolveTargetUrl(this, entry.cleanedUrl, contentUrl)
        if (effectiveProcessedUrl != displayCleaned) {
            addDetailRow(getString(R.string.label_sidestepped), effectiveProcessedUrl, R.drawable.ic_open_in_new) {
                 val intent = Intent(Intent.ACTION_VIEW, effectiveProcessedUrl.toUri())
                 startActivity(intent)
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.menu_details))
            .setView(scrollView)
            .setPositiveButton("Done", null)
            .show()
    }
    
    private fun clearHistory() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_clear_all_title))
            .setMessage(getString(R.string.dialog_clear_all_message))
            .setPositiveButton("Clear") { _, _ ->
                // Immediate UI update to prevent any flashing from background jobs or stale data
                // We hide the list instantly, then submit null to the adapter to clear it immediately
                historyList.visibility = View.GONE
                emptyStateGuide.visibility = View.VISIBLE
                
                // Clear adapter and recycler pool to be absolutely sure nothing is cached
                historyAdapter.submitList(null)
                historyList.recycledViewPool.clear()
                
                invalidateOptionsMenu()
                updateCounterUI(emptyList())
                
                lifecycleScope.launch {
                    HistoryManager.clearHistory(this@MainActivity)
                    loadHistory()
                }
                Toast.makeText(this, getString(R.string.toast_history_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun createRippleBackground(): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.shape = android.graphics.drawable.GradientDrawable.OVAL
        shape.setColor(0x00000000) // Transparent
        return shape
    }
    
    private fun shareUrl(url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(shareIntent, "Share URL"))
    }
    
    private fun clearHistoryItem(entry: HistoryManager.HistoryEntry) {
        lifecycleScope.launch {
            HistoryManager.removeFromHistory(this@MainActivity, entry)
            loadHistory()
        }
        Toast.makeText(this, getString(R.string.toast_item_removed), Toast.LENGTH_SHORT).show()
    }

    
    private fun applyTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themeValue = prefs.getString(SettingsActivity.KEY_THEME_PREF, "system")
        val mode = when (themeValue) {
            "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
    }
    
    private fun getSidestepCount(): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getLong("sidestep_count", 0L)
    }
    
    private fun incrementSidestepCount() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentCount = prefs.getLong("sidestep_count", 0L)
        prefs.edit()
            .putLong("sidestep_count", currentCount + 1)
            .putBoolean("has_processed_url", true)
            .apply()
    }
}
