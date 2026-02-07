package com.blankdev.sidestep

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors

class SettingsPrivacyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_privacy)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        setupEdgeToEdge()
        setupHistoryToggle()
        setupPreviewFetchToggle()
    }

    private fun setupEdgeToEdge() {
        val root = findViewById<android.view.View>(R.id.settingsRoot)
        val scrollView = findViewById<android.view.View>(R.id.settingsScrollView)
        val toolbar = findViewById<android.view.View>(R.id.settingsToolbar)
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(0, bars.top, 0, 0)
            v.setPadding(bars.left, 0, bars.right, 0)
            scrollView.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun setupHistoryToggle() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val historyGroup = findViewById<MaterialButtonToggleGroup>(R.id.historyToggleGroup)
        val customContainer = findViewById<android.view.View>(R.id.customHistoryContainer)
        val privacyCard = findViewById<android.view.View>(R.id.cardPrivacyData)
        
        initializeHistoryState(prefs, historyGroup, customContainer, privacyCard)
        setupHistorySliders(prefs)
        setupHistoryListeners(prefs, historyGroup, customContainer, privacyCard)
    }

    private fun initializeHistoryState(
        prefs: android.content.SharedPreferences,
        historyGroup: MaterialButtonToggleGroup,
        customContainer: android.view.View,
        privacyCard: android.view.View
    ) {
        val currentMode = prefs.getString(HistoryManager.KEY_HISTORY_RETENTION, HistoryManager.DEFAULT_RETENTION_MODE)
        when (currentMode) {
            "never" -> historyGroup.check(R.id.btnHistoryNever)
            "forever" -> historyGroup.check(R.id.btnHistoryForever)
            else -> historyGroup.check(R.id.btnHistoryAuto)
        }
        
        customContainer.visibility = if (currentMode == HistoryManager.DEFAULT_RETENTION_MODE) android.view.View.VISIBLE else android.view.View.GONE
        privacyCard.visibility = if (currentMode == "never") android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun setupHistorySliders(prefs: android.content.SharedPreferences) {
        val sliderDays = findViewById<Slider>(R.id.sliderDays)
        val sliderItems = findViewById<Slider>(R.id.sliderItems)
        
        val daysOptions = HISTORY_DAYS_OPTIONS
        val itemsOptions = HISTORY_ITEMS_OPTIONS

        val savedDays = prefs.getString(HistoryManager.KEY_HISTORY_DAYS, HistoryManager.DEFAULT_HISTORY_DAYS.toString())?.toIntOrNull() ?: HistoryManager.DEFAULT_HISTORY_DAYS
        val savedItems = prefs.getString(HistoryManager.KEY_HISTORY_ITEMS, HistoryManager.DEFAULT_HISTORY_ITEMS.toString())?.toIntOrNull() ?: HistoryManager.DEFAULT_HISTORY_ITEMS

        val daysIndex = daysOptions.indexOf(savedDays).takeIf { it >= 0 } 
            ?: daysOptions.indices.minByOrNull { kotlin.math.abs(daysOptions[it] - savedDays) } ?: 2
        
        val itemsIndex = itemsOptions.indexOf(savedItems).takeIf { it >= 0 }
            ?: itemsOptions.indices.minByOrNull { kotlin.math.abs(itemsOptions[it] - savedItems) } ?: 2

        sliderDays.value = daysIndex.toFloat()
        sliderItems.value = itemsIndex.toFloat()
        
        sliderDays.addOnChangeListener { _, value, _ ->
            val index = value.toInt().coerceIn(0, daysOptions.lastIndex)
            prefs.edit { putString(HistoryManager.KEY_HISTORY_DAYS, daysOptions[index].toString()) }
        }
        
        sliderItems.addOnChangeListener { _, value, _ ->
            val index = value.toInt().coerceIn(0, itemsOptions.lastIndex)
            prefs.edit { putString(HistoryManager.KEY_HISTORY_ITEMS, itemsOptions[index].toString()) }
        }
    }

    private fun setupHistoryListeners(
        prefs: android.content.SharedPreferences,
        historyGroup: MaterialButtonToggleGroup,
        customContainer: android.view.View,
        privacyCard: android.view.View
    ) {
        historyGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                handlehistoryToggle(prefs, historyGroup, checkedId, customContainer, privacyCard)
            }
        }
    }

    private fun handlehistoryToggle(
        prefs: android.content.SharedPreferences,
        historyGroup: MaterialButtonToggleGroup,
        checkedId: Int,
        customContainer: android.view.View,
        privacyCard: android.view.View
    ) {
        val newMode = when (checkedId) {
            R.id.btnHistoryNever -> "never"
            R.id.btnHistoryForever -> "forever"
            else -> HistoryManager.DEFAULT_RETENTION_MODE
        }
        
        if (newMode == "never" && !prefs.getBoolean(SettingsActivity.KEY_IMMEDIATE_NAVIGATION, true)) {
            // Revert UI if needed logic
            val current = prefs.getString(HistoryManager.KEY_HISTORY_RETENTION, HistoryManager.DEFAULT_RETENTION_MODE)
            if (current == "forever") historyGroup.check(R.id.btnHistoryForever) else historyGroup.check(R.id.btnHistoryAuto)
            
            android.widget.Toast.makeText(this, "History must be enabled if Immediate Navigation is OFF", android.widget.Toast.LENGTH_LONG).show()
        } else {
            prefs.edit { putString(HistoryManager.KEY_HISTORY_RETENTION, newMode) }
            customContainer.visibility = if (newMode == HistoryManager.DEFAULT_RETENTION_MODE) android.view.View.VISIBLE else android.view.View.GONE
            privacyCard.visibility = if (newMode == "never") android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun setupPreviewFetchToggle() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val switch = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchPreviewFetch)
        
        // Default to true (safe preview enabled)
        switch.isChecked = prefs.getBoolean(SettingsActivity.KEY_PREVIEW_FETCH, true)
        
        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(SettingsActivity.KEY_PREVIEW_FETCH, isChecked) }
        }
    }

    companion object {
        // History Retention Options
        private val HISTORY_DAYS_OPTIONS = listOf(1, 7, 14, 30, 365)
        private val HISTORY_ITEMS_OPTIONS = listOf(5, 10, 25, 50, 100)
    }

}
