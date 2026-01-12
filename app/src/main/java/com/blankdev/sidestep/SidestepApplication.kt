package com.blankdev.sidestep

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors

/**
 * Custom Application class to handle app-wide initialization.
 * This ensures all activities have proper theming regardless of how they're launched.
 */
class SidestepApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Apply Material 3 dynamic colors app-wide
        DynamicColors.applyToActivitiesIfAvailable(this)
        
        // Apply theme preference app-wide
        applyTheme()
    }
    
    private fun applyTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themeValue = prefs.getString(SettingsActivity.KEY_THEME_PREF, "system")
        val mode = when (themeValue) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
