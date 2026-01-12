package com.blankdev.sidestep

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import androidx.appcompat.app.AppCompatDelegate

class SettingsDisplayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_display)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        setupEdgeToEdge()
        setupThemeToggle()
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

    private fun setupThemeToggle() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themeGroup = findViewById<MaterialButtonToggleGroup>(R.id.themeToggleGroup)
        
        // Set initial state
        val currentTheme = prefs.getString(SettingsActivity.KEY_THEME_PREF, "system")
        when (currentTheme) {
            "light" -> themeGroup.check(R.id.btnThemeLight)
            "dark" -> themeGroup.check(R.id.btnThemeDark)
            else -> themeGroup.check(R.id.btnThemeSystem)
        }
        
        // Listener
        themeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newValue = when (checkedId) {
                    R.id.btnThemeLight -> "light"
                    R.id.btnThemeDark -> "dark"
                    else -> "system"
                }
                prefs.edit { putString(SettingsActivity.KEY_THEME_PREF, newValue) }
                applyTheme(newValue)
            }
        }
    }

    private fun applyTheme(themePref: String) {
        val mode = when (themePref) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
