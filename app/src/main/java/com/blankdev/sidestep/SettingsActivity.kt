package com.blankdev.sidestep

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import android.widget.TextView
import android.view.Gravity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.text.Html
import android.text.Spannable
import android.text.style.URLSpan
import android.text.style.ClickableSpan
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.widget.NestedScrollView

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_custom)
        
        // Setup Toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Handle Window Insets for Edge-to-Edge
        val root = findViewById<android.view.View>(R.id.settingsRoot)
        val scrollView = findViewById<android.view.View>(R.id.settingsScrollView)
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(0, bars.top, 0, 0)
            v.setPadding(bars.left, 0, bars.right, 0)
            scrollView.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        
        setupAutomationButtons()
        setupNavigationButtons()
        setupAboutButton()
    }

    private fun setupNavigationButtons() {
        // Navigate to Alternative Frontends
        findViewById<android.view.View>(R.id.btnNavigateFrontends)?.setOnClickListener {
            startActivity(Intent(this, SettingsFrontendsActivity::class.java))
        }
        
        // Navigate to Custom Redirects
        findViewById<android.view.View>(R.id.btnNavigateRedirects)?.setOnClickListener {
            startActivity(Intent(this, SettingsCustomRedirectsActivity::class.java))
        }
        
        // Navigate to History & Previews
        findViewById<android.view.View>(R.id.btnNavigatePrivacy)?.setOnClickListener {
            startActivity(Intent(this, SettingsPrivacyActivity::class.java))
        }
        
        // Navigate to Display
        findViewById<android.view.View>(R.id.btnNavigateDisplay)?.setOnClickListener {
            startActivity(Intent(this, SettingsDisplayActivity::class.java))
        }
    }

    private fun setupAutomationButtons() {
        findViewById<android.view.View>(R.id.btnSettingsQuickGuide).setOnClickListener {
            showGuidedTour()
        }
        findViewById<android.view.View>(R.id.btnSettingsCheckStatus).setOnClickListener {
            showDomainStatusDialog()
        }
        findViewById<android.view.View>(R.id.btnSettingsSetDefault).setOnClickListener {
            openDefaultHandlerSettings()
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Unshorten URLs
        val switchUnshorten = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchUnshortenUrls)
        val switchResolveHtml = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchResolveHtml)

        switchUnshorten?.apply {
            isChecked = prefs.getBoolean(KEY_UNSHORTEN_URLS, true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean(KEY_UNSHORTEN_URLS, isChecked) }
                // Disable dependent toggle
                switchResolveHtml?.isEnabled = isChecked
            }
        }

        // Fetch Canonical Links (Resolve HTML)
        switchResolveHtml?.apply {
            val isUnshortenEnabled = prefs.getBoolean(KEY_UNSHORTEN_URLS, true)
            isChecked = prefs.getBoolean(KEY_RESOLVE_HTML_REDIRECTS, true)
            isEnabled = isUnshortenEnabled
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean(KEY_RESOLVE_HTML_REDIRECTS, isChecked) }
            }
        }

        // Remove Tracking Parameters
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchRemoveTracking)?.apply {
            isChecked = prefs.getBoolean(KEY_REMOVE_TRACKING, true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean(KEY_REMOVE_TRACKING, isChecked) }
            }
        }

        val switchImmediate = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchImmediateNavigation)
        
        if (switchImmediate != null) {
            switchImmediate.isChecked = prefs.getBoolean(KEY_IMMEDIATE_NAVIGATION, true)
            switchImmediate.setOnCheckedChangeListener { _, isChecked ->
                val retentionMode = prefs.getString(HistoryManager.KEY_HISTORY_RETENTION, HistoryManager.DEFAULT_RETENTION_MODE)
                if (!isChecked && retentionMode == "never") {
                    switchImmediate.isChecked = true
                    android.widget.Toast.makeText(this, "Immediate navigation must be ON if history is set to Never", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    prefs.edit { putBoolean(KEY_IMMEDIATE_NAVIGATION, isChecked) }
                }
            }
        }
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
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Could not open settings", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDomainStatusDialog() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_domain_status, null)
        bottomSheetDialog.setContentView(view)

        val container = view.findViewById<LinearLayout>(R.id.domainStatusContainer)

        val platforms = listOf(
            "Twitter / X" to "x.com",
            "Reddit" to "reddit.com",
            "YouTube" to "youtube.com",
            "Wikipedia" to "wikipedia.org",
            "IMDb" to "imdb.com",
            "Goodreads" to "goodreads.com",
            "Medium" to "medium.com",
            "Tumblr" to "tumblr.com",
            "Genius" to "genius.com",
            "UrbanDictionary" to "urbandictionary.com",
            "Imgur" to "imgur.com",
            "GitHub" to "github.com",
            "StackOverflow" to "stackoverflow.com",
            "Google Maps" to "maps.google.com"
        )

        platforms.forEach { (name, domain) ->
            val status = SettingsUtils.getDomainHandlingStatus(this, domain)
            
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, SettingsUtils.dp(this@SettingsActivity, 16), 0, SettingsUtils.dp(this@SettingsActivity, 16))
                gravity = Gravity.CENTER_VERTICAL
            }

            val (statusColorId, statusLabel) = when (status) {
                SettingsUtils.DomainStatus.FULL -> R.color.status_green_pastel to "Active"
                SettingsUtils.DomainStatus.PARTIAL -> R.color.status_yellow_pastel to "Partial"
                else -> R.color.status_red_pastel to "Inactive"
            }

            val statusIndicator = TextView(this).apply {
                text = "●"
                textSize = 18f
                setTextColor(androidx.core.content.ContextCompat.getColor(context, statusColorId))
                setPadding(0, 0, SettingsUtils.dp(this@SettingsActivity, 24), 0)
            }
            itemLayout.addView(statusIndicator)

            val nameText = TextView(this).apply {
                text = name
                textSize = 16f
                setTextColor(SettingsUtils.getThemeColor(this@SettingsActivity, android.R.attr.textColorPrimary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            itemLayout.addView(nameText)

            val statusText = TextView(this).apply {
                text = statusLabel
                textSize = 14f
                setTextColor(SettingsUtils.getThemeColor(this@SettingsActivity, if (status != SettingsUtils.DomainStatus.NONE) android.R.attr.textColorPrimary else android.R.attr.textColorSecondary))
            }
            itemLayout.addView(statusText)

            container.addView(itemLayout)
        }

        bottomSheetDialog.show()
    }

    private fun showGuidedTour() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = SettingsUtils.dp(this@SettingsActivity, 24)
            setPadding(padding, 0, padding, 0)
        }
        
        val steps = listOf(
            "1. Tap 'Set as Default'",
            "2. Select ‘Open in app’ - this allows Sidestep to redirect the navigation using your default browser",
            "3. Add links for domains you're interested in sidestepping - or simply of clearing from tracking parameters",
            "4. Done! Now when accessing the selected links, this app redirects automatically"
        )
        
        steps.forEach { stepText ->
            val step = TextView(this).apply {
                text = stepText
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, 0, 0, SettingsUtils.dp(this@SettingsActivity, 16))
                setTextColor(SettingsUtils.getThemeColor(this@SettingsActivity, android.R.attr.textColorSecondary))
                setLineSpacing(0f, 1.4f)
            }
            dialogView.addView(step)
        }
        
        val scrollView = NestedScrollView(this).apply {
            addView(dialogView)
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Automate Sidestep")
            .setView(scrollView)
            .setPositiveButton("Got it!", null)
            .show()
    }



    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupAboutButton() {
        findViewById<android.view.View>(R.id.btnAbout)?.setOnClickListener {
            SettingsUtils.showAboutDialog(this)
        }
    }


    // initializeDefaultRedirects removed as it conflicts with SettingsUtils defaults


    companion object {
        const val KEY_ALTERNATIVE_DOMAIN = "alternative_domain"
        const val DEFAULT_ALTERNATIVE_DOMAIN = "nitter.net" 
        const val KEY_REDDIT_DOMAIN = "reddit_domain"
        const val DEFAULT_REDDIT_DOMAIN = "redlib.catsarch.com"
        const val KEY_YOUTUBE_DOMAIN = "youtube_domain"
        const val KEY_YOUTUBE_DOMAIN_INVIDIOUS = "youtube_domain_invidious"
        const val KEY_YOUTUBE_DOMAIN_PIPED = "youtube_domain_piped"
        const val DEFAULT_YOUTUBE_DOMAIN = "yewtu.be"
        const val DEFAULT_PIPED_DOMAIN = "pipedapi.kavin.rocks"
        const val KEY_YOUTUBE_TYPE = "youtube_frontend_type"
        
        const val KEY_THEME_PREF = "theme_preference"
        const val KEY_TWITTER_CLEAN_ONLY = "twitter_clean_only"
        const val KEY_REDDIT_CLEAN_ONLY = "reddit_clean_only"
        const val KEY_YOUTUBE_CLEAN_ONLY = "youtube_clean_only"
        
        const val KEY_IMDB_DOMAIN = "imdb_domain"
        const val DEFAULT_IMDB_DOMAIN = "libremdb.pussthecat.org"
        const val KEY_IMDB_CLEAN_ONLY = "imdb_clean_only"

        const val KEY_MEDIUM_DOMAIN = "medium_domain"
        const val DEFAULT_MEDIUM_DOMAIN = "scribe.rip"
        const val KEY_MEDIUM_CLEAN_ONLY = "medium_clean_only"

        const val KEY_WIKIPEDIA_DOMAIN = "wikipedia_domain"
        const val DEFAULT_WIKIPEDIA_DOMAIN = "wikiless.tiekoetter.com"
        const val KEY_WIKIPEDIA_CLEAN_ONLY = "wikipedia_clean_only"

        const val KEY_GOODREADS_DOMAIN = "goodreads_domain"
        const val DEFAULT_GOODREADS_DOMAIN = "biblioreads.eu.org"
        const val KEY_GOODREADS_CLEAN_ONLY = "goodreads_clean_only"

        const val KEY_GENIUS_DOMAIN = "genius_domain"
        const val KEY_GENIUS_DOMAIN_DUMB = "genius_domain_dumb"
        const val KEY_GENIUS_DOMAIN_INTELLECTUAL = "genius_domain_intellectual"
        const val DEFAULT_GENIUS_DOMAIN = "dm.vern.cc"
        const val DEFAULT_INTELLECTUAL_DOMAIN = "intellectual.insprill.net"
        const val KEY_GENIUS_CLEAN_ONLY = "genius_clean_only"
        const val KEY_GENIUS_TYPE = "genius_frontend_type"
        
        const val KEY_PREVIEW_FETCH = "enable_preview_fetch"
        const val KEY_IMMEDIATE_NAVIGATION = "immediate_navigation"
        const val KEY_UNSHORTEN_URLS = "unshorten_urls"
        const val KEY_REMOVE_TRACKING = "remove_tracking"

        const val KEY_GITHUB_DOMAIN = "github_domain"
        const val DEFAULT_GITHUB_DOMAIN = "gothub.lunar.icu"
        const val KEY_GITHUB_CLEAN_ONLY = "github_clean_only"

        const val KEY_STACKOVERFLOW_DOMAIN = "stackoverflow_domain"
        const val DEFAULT_STACKOVERFLOW_DOMAIN = "ao.vern.cc"
        const val KEY_STACKOVERFLOW_CLEAN_ONLY = "stackoverflow_clean_only"

        const val KEY_GOOGLE_MAPS_DOMAIN = "google_maps_domain"
        const val DEFAULT_GOOGLE_MAPS_DOMAIN = "www.openstreetmap.org"
        const val KEY_GOOGLE_MAPS_CLEAN_ONLY = "google_maps_clean_only"

        const val KEY_TUMBLR_DOMAIN = "tumblr_domain"
        const val DEFAULT_TUMBLR_DOMAIN = "tb.opnxng.com"
        const val KEY_TUMBLR_CLEAN_ONLY = "tumblr_clean_only"

        const val KEY_RURAL_DICTIONARY_DOMAIN = "rural_dictionary_domain"
        const val DEFAULT_RURAL_DICTIONARY_DOMAIN = "rd.vern.cc"
        const val KEY_RURAL_DICTIONARY_CLEAN_ONLY = "rural_dictionary_clean_only"
        
        const val KEY_RESOLVE_HTML_REDIRECTS = "resolve_html_redirects"

        const val KEY_RIMGO_DOMAIN = "rimgo_domain"
        const val DEFAULT_RIMGO_DOMAIN = "imgur.artemislena.eu"
        const val KEY_RIMGO_CLEAN_ONLY = "rimgo_clean_only"

        const val KEY_CUSTOM_REDIRECTS = "custom_redirects"
    }
}
