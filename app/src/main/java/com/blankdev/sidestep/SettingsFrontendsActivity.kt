package com.blankdev.sidestep

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import android.view.View
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import androidx.core.net.toUri
import android.content.Intent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class SettingsFrontendsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_frontends)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        setupEdgeToEdge()
        setupFrontends()
    }

    private fun setupEdgeToEdge() {
        val root = findViewById<android.view.View>(R.id.settingsRoot)
        val scrollView = findViewById<android.widget.ScrollView>(R.id.settingsScrollView)
        val toolbar = findViewById<android.view.View>(R.id.settingsToolbar)
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(0, bars.top, 0, 0)
            v.setPadding(bars.left, 0, bars.right, 0)
            scrollView.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun setupFrontends() {
        // Move content from updated activity_settings_custom.xml (which will be cleared)
        // For now, I'll programmatically add the views or inflate sub-layouts. 
        // Better yet, I'll copy the logic for inflating these views.
        // Actually, since the original layout had them hardcoded, I should probably have created a layout file for each or one big one.
        // In this execution plan, I will reuse the logic by inflating the `activity_settings_custom_content` (if I split it) or 
        // just manually reconstructing the views or usage of `activity_settings_frontends.xml` with includes.
        
        // Since I made `activity_settings_frontends.xml` with an empty container, 
        // I should have put the content there.
        // I'll use a pragmatic approach: I will copy the huge chunks of XML from `activity_settings_custom.xml` to `activity_settings_frontends.xml` in a separate step?
        // No, I can't easily multi-step that without reading.
        // I will use `setupDomainInput` logic here, but I need the XML to match.
        // The safest way is to assume `activity_settings_frontends.xml` will be populated with the Views.
        // I'll trigger a tool call to update the XML with the content next.
        
        // For now, initialize the logic assuming the IDs exist.
        setupDomainInput()
        setupToggles()
        setupOrganicMapsNote()
    }
    
    // ... Copy of setupDomainInput, setupToggles, setupPlatformToggle, validateCleanOnlySelection, etc.
    // I will duplicate them here for now, they are cleanly separated.
    
    private fun setupToggles() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Setup specialized YouTube Type toggle (Invidious vs Piped)
        val youtubeTypeToggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleYouTubeType)
        val youtubeType = prefs.getString(SettingsActivity.KEY_YOUTUBE_TYPE, "invidious")
        if (youtubeType == "piped") {
            youtubeTypeToggle.check(R.id.btnYouTubePiped)
        } else {
            youtubeTypeToggle.check(R.id.btnYouTubeInvidious)
        }

        youtubeTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val youtubeInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputYouTubeDomain)
                val currentDomain = youtubeInput.text?.toString()?.trim()
                val oldType = prefs.getString(SettingsActivity.KEY_YOUTUBE_TYPE, "invidious")
                val newType = if (checkedId == R.id.btnYouTubePiped) "piped" else "invidious"
                
                if (oldType != newType) {
                    val oldKey = if (oldType == "piped") SettingsActivity.KEY_YOUTUBE_DOMAIN_PIPED else SettingsActivity.KEY_YOUTUBE_DOMAIN_INVIDIOUS
                    if (!currentDomain.isNullOrEmpty()) {
                        prefs.edit { putString(oldKey, currentDomain) }
                    }
                    
                    prefs.edit { putString(SettingsActivity.KEY_YOUTUBE_TYPE, newType) }
                    
                    val newKey = if (newType == "piped") SettingsActivity.KEY_YOUTUBE_DOMAIN_PIPED else SettingsActivity.KEY_YOUTUBE_DOMAIN_INVIDIOUS
                    val newDefault = if (newType == "piped") SettingsActivity.DEFAULT_PIPED_DOMAIN else SettingsActivity.DEFAULT_YOUTUBE_DOMAIN
                    val savedDomain = prefs.getString(newKey, newDefault)
                    
                    youtubeInput.setText(savedDomain)
                    prefs.edit { putString(SettingsActivity.KEY_YOUTUBE_DOMAIN, savedDomain) }
                }
            }
        }

        // Setup specialized Genius Type toggle (Dumb vs Intellectual)
        val geniusTypeToggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleGeniusType)
        val geniusType = prefs.getString(SettingsActivity.KEY_GENIUS_TYPE, "dumb")
        if (geniusType == "intellectual") {
            geniusTypeToggle.check(R.id.btnGeniusIntellectual)
            findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.inputLayoutGenius).hint = getString(R.string.intellectual_instance)
        } else {
            geniusTypeToggle.check(R.id.btnGeniusDumb)
            findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.inputLayoutGenius).hint = getString(R.string.dumb_instance)
        }

        geniusTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val geniusInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputGeniusDomain)
                val currentDomain = geniusInput.text?.toString()?.trim()
                val oldType = prefs.getString(SettingsActivity.KEY_GENIUS_TYPE, "dumb")
                val newType = if (checkedId == R.id.btnGeniusIntellectual) "intellectual" else "dumb"
                
                if (oldType != newType) {
                    val oldKey = if (oldType == "intellectual") SettingsActivity.KEY_GENIUS_DOMAIN_INTELLECTUAL else SettingsActivity.KEY_GENIUS_DOMAIN_DUMB
                    if (!currentDomain.isNullOrEmpty()) {
                        prefs.edit { putString(oldKey, currentDomain) }
                    }
                    
                    prefs.edit { putString(SettingsActivity.KEY_GENIUS_TYPE, newType) }
                    
                    val newKey = if (newType == "intellectual") SettingsActivity.KEY_GENIUS_DOMAIN_INTELLECTUAL else SettingsActivity.KEY_GENIUS_DOMAIN_DUMB
                    val newDefault = if (newType == "intellectual") SettingsActivity.DEFAULT_INTELLECTUAL_DOMAIN else SettingsActivity.DEFAULT_GENIUS_DOMAIN
                    val savedDomain = prefs.getString(newKey, newDefault)
                    
                    geniusInput.setText(savedDomain)
                    prefs.edit { putString(SettingsActivity.KEY_GENIUS_DOMAIN, savedDomain) }
                    
                    // Update hint
                    findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.inputLayoutGenius).hint = 
                        if (newType == "intellectual") getString(R.string.intellectual_instance) else getString(R.string.dumb_instance)
                }
            }
        }

        // Setup standard platform toggles
        setupPlatformToggle(R.id.toggleTwitterMode, R.id.twitterDomainContainer, SettingsActivity.KEY_TWITTER_CLEAN_ONLY, "x.com", R.id.btnTwitterClean, R.id.btnTwitterRedirect)
        setupPlatformToggle(R.id.toggleRedditMode, R.id.redditDomainContainer, SettingsActivity.KEY_REDDIT_CLEAN_ONLY, "reddit.com", R.id.btnRedditClean, R.id.btnRedditRedirect)
        setupPlatformToggle(R.id.toggleYouTubeMode, R.id.youtubeDomainContainer, SettingsActivity.KEY_YOUTUBE_CLEAN_ONLY, "youtube.com", R.id.btnYouTubeClean, R.id.btnYouTubeRedirect)
        setupPlatformToggle(R.id.toggleImdbMode, R.id.imdbDomainContainer, SettingsActivity.KEY_IMDB_CLEAN_ONLY, "imdb.com", R.id.btnImdbClean, R.id.btnImdbRedirect)
        setupPlatformToggle(R.id.toggleMediumMode, R.id.mediumDomainContainer, SettingsActivity.KEY_MEDIUM_CLEAN_ONLY, "medium.com", R.id.btnMediumClean, R.id.btnMediumRedirect)
        setupPlatformToggle(R.id.toggleWikipediaMode, R.id.wikipediaDomainContainer, SettingsActivity.KEY_WIKIPEDIA_CLEAN_ONLY, "wikipedia.org", R.id.btnWikipediaClean, R.id.btnWikipediaRedirect)
        setupPlatformToggle(R.id.toggleGoodreadsMode, R.id.goodreadsDomainContainer, SettingsActivity.KEY_GOODREADS_CLEAN_ONLY, "goodreads.com", R.id.btnGoodreadsClean, R.id.btnGoodreadsRedirect)
        setupPlatformToggle(R.id.toggleGeniusMode, R.id.geniusDomainContainer, SettingsActivity.KEY_GENIUS_CLEAN_ONLY, "genius.com", R.id.btnGeniusClean, R.id.btnGeniusRedirect)
        setupPlatformToggle(R.id.toggleGitHubMode, R.id.githubDomainContainer, SettingsActivity.KEY_GITHUB_CLEAN_ONLY, "github.com", R.id.btnGitHubClean, R.id.btnGitHubRedirect)
        setupPlatformToggle(R.id.toggleStackOverflowMode, R.id.stackoverflowDomainContainer, SettingsActivity.KEY_STACKOVERFLOW_CLEAN_ONLY, "stackoverflow.com", R.id.btnStackOverflowClean, R.id.btnStackOverflowRedirect)
        setupPlatformToggle(R.id.toggleTumblrMode, R.id.tumblrDomainContainer, SettingsActivity.KEY_TUMBLR_CLEAN_ONLY, "tumblr.com", R.id.btnTumblrClean, R.id.btnTumblrRedirect)
        setupPlatformToggle(R.id.toggleRuralDictionaryMode, R.id.rural_dictionaryDomainContainer, SettingsActivity.KEY_RURAL_DICTIONARY_CLEAN_ONLY, "urbandictionary.com", R.id.btnRuralDictionaryClean, R.id.btnRuralDictionaryRedirect)
        setupPlatformToggle(R.id.toggleRimgoMode, R.id.rimgoDomainContainer, SettingsActivity.KEY_RIMGO_CLEAN_ONLY, "imgur.com", R.id.btnRimgoClean, R.id.btnRimgoRedirect)
        setupPlatformToggle(R.id.toggleGoogleMapsMode, R.id.googlemapsDomainContainer, SettingsActivity.KEY_GOOGLE_MAPS_CLEAN_ONLY, "google.com", R.id.btnGoogleMapsClean, R.id.btnGoogleMapsRedirect)
    }


    private fun setupPlatformToggle(toggleGroupId: Int, containerId: Int, prefKey: String, domain: String, cleanBtnId: Int, redirectBtnId: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val toggle = findViewById<MaterialButtonToggleGroup>(toggleGroupId)
        val container = findViewById<android.view.View>(containerId)
        
        val isCleanOnly = prefs.getBoolean(prefKey, false)
        if (isCleanOnly) {
            toggle.check(cleanBtnId)
            container.visibility = android.view.View.GONE
        } else {
            toggle.check(redirectBtnId)
            container.visibility = android.view.View.VISIBLE
        }

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == cleanBtnId) {
                    if (validateCleanOnlySelection(domain)) {
                        prefs.edit { putBoolean(prefKey, true) }
                        container.visibility = android.view.View.GONE
                    } else {
                        // Re-check redirect if validation failed (though validate currently always returns true)
                        toggle.check(redirectBtnId)
                    }
                } else {
                    prefs.edit { putBoolean(prefKey, false) }
                    container.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun validateCleanOnlySelection(domain: String): Boolean {
        if (SettingsUtils.isAppDefaultHandlerForDomain(this, domain)) {
            AlertDialog.Builder(this)
                .setTitle("In-app view enabled")
                .setMessage("Since Sidestep is the default handler for $domain, selecting 'Clean only' will open these links in a secure in-app browser to prevent redirect loops.")
                .setPositiveButton("Got it", null)
                .show()
        }
        return true
    }

     private fun setupDomainInput() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Specialized YouTube Input
        val youtubeInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputYouTubeDomain)
        val type = prefs.getString(SettingsActivity.KEY_YOUTUBE_TYPE, "invidious")
        val youtubeKey = if (type == "piped") SettingsActivity.KEY_YOUTUBE_DOMAIN_PIPED else SettingsActivity.KEY_YOUTUBE_DOMAIN_INVIDIOUS
        val youtubeDefault = if (type == "piped") SettingsActivity.DEFAULT_PIPED_DOMAIN else SettingsActivity.DEFAULT_YOUTUBE_DOMAIN
        
        loadDomainPref(youtubeInput, youtubeKey, youtubeDefault)
        prefs.edit { putString(SettingsActivity.KEY_YOUTUBE_DOMAIN, youtubeInput.text?.toString()?.trim()) }
        
        youtubeInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s?.toString()?.trim() ?: return
                if (value.isEmpty()) return
                val currentType = prefs.getString(SettingsActivity.KEY_YOUTUBE_TYPE, "invidious")
                val currentKey = if (currentType == "piped") SettingsActivity.KEY_YOUTUBE_DOMAIN_PIPED else SettingsActivity.KEY_YOUTUBE_DOMAIN_INVIDIOUS
                prefs.edit { 
                    putString(currentKey, value)
                    putString(SettingsActivity.KEY_YOUTUBE_DOMAIN, value)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSelectYouTube).setOnClickListener {
            val currentType = prefs.getString(SettingsActivity.KEY_YOUTUBE_TYPE, "invidious")
            val pickerType = if (currentType == "piped") "piped" else "youtube"
            SettingsUtils.fetchLatestInstances(this, pickerType) { showInstancePicker(pickerType, youtubeInput, it) }
        }

        // Specialized Genius Input
        val geniusInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputGeniusDomain)
        val gType = prefs.getString(SettingsActivity.KEY_GENIUS_TYPE, "dumb")
        val geniusKey = if (gType == "intellectual") SettingsActivity.KEY_GENIUS_DOMAIN_INTELLECTUAL else SettingsActivity.KEY_GENIUS_DOMAIN_DUMB
        val geniusDefault = if (gType == "intellectual") SettingsActivity.DEFAULT_INTELLECTUAL_DOMAIN else SettingsActivity.DEFAULT_GENIUS_DOMAIN
        
        loadDomainPref(geniusInput, geniusKey, geniusDefault)
        prefs.edit { putString(SettingsActivity.KEY_GENIUS_DOMAIN, geniusInput.text?.toString()?.trim()) }
        
        geniusInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s?.toString()?.trim() ?: return
                if (value.isEmpty()) return
                val currentType = prefs.getString(SettingsActivity.KEY_GENIUS_TYPE, "dumb")
                val currentKey = if (currentType == "intellectual") SettingsActivity.KEY_GENIUS_DOMAIN_INTELLECTUAL else SettingsActivity.KEY_GENIUS_DOMAIN_DUMB
                prefs.edit { 
                    putString(currentKey, value)
                    putString(SettingsActivity.KEY_GENIUS_DOMAIN, value)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSelectGenius).setOnClickListener {
            val currentType = prefs.getString(SettingsActivity.KEY_GENIUS_TYPE, "dumb")
            val pickerType = if (currentType == "intellectual") "intellectual" else "genius"
            SettingsUtils.fetchLatestInstances(this, pickerType) { showInstancePicker(pickerType, geniusInput, it) }
        }

        // Setup standard platform inputs
        setupPlatformInput(R.id.inputDomain, R.id.btnSelectNitter, SettingsActivity.KEY_ALTERNATIVE_DOMAIN, SettingsActivity.DEFAULT_ALTERNATIVE_DOMAIN, "twitter")
        setupPlatformInput(R.id.inputRedditDomain, R.id.btnSelectRedlib, SettingsActivity.KEY_REDDIT_DOMAIN, SettingsActivity.DEFAULT_REDDIT_DOMAIN, "reddit")
        setupPlatformInput(R.id.inputImdbDomain, R.id.btnSelectImdb, SettingsActivity.KEY_IMDB_DOMAIN, SettingsActivity.DEFAULT_IMDB_DOMAIN, "imdb")
        setupPlatformInput(R.id.inputMediumDomain, R.id.btnSelectMedium, SettingsActivity.KEY_MEDIUM_DOMAIN, SettingsActivity.DEFAULT_MEDIUM_DOMAIN, "medium")
        setupPlatformInput(R.id.inputWikipediaDomain, R.id.btnSelectWikipedia, SettingsActivity.KEY_WIKIPEDIA_DOMAIN, SettingsActivity.DEFAULT_WIKIPEDIA_DOMAIN, "wikipedia")
        setupPlatformInput(R.id.inputGoodreadsDomain, R.id.btnSelectGoodreads, SettingsActivity.KEY_GOODREADS_DOMAIN, SettingsActivity.DEFAULT_GOODREADS_DOMAIN, "goodreads")
        setupPlatformInput(R.id.inputGitHubDomain, R.id.btnSelectGitHub, SettingsActivity.KEY_GITHUB_DOMAIN, SettingsActivity.DEFAULT_GITHUB_DOMAIN, "github")
        setupPlatformInput(R.id.inputStackOverflowDomain, R.id.btnSelectStackOverflow, SettingsActivity.KEY_STACKOVERFLOW_DOMAIN, SettingsActivity.DEFAULT_STACKOVERFLOW_DOMAIN, "stackoverflow")
        setupPlatformInput(R.id.inputTumblrDomain, R.id.btnSelectTumblr, SettingsActivity.KEY_TUMBLR_DOMAIN, SettingsActivity.DEFAULT_TUMBLR_DOMAIN, "tumblr")
        setupPlatformInput(R.id.inputRuralDictionaryDomain, R.id.btnSelectRuralDictionary, SettingsActivity.KEY_RURAL_DICTIONARY_DOMAIN, SettingsActivity.DEFAULT_RURAL_DICTIONARY_DOMAIN, "rural-dictionary")
        setupPlatformInput(R.id.inputRimgoDomain, R.id.btnSelectRimgo, SettingsActivity.KEY_RIMGO_DOMAIN, SettingsActivity.DEFAULT_RIMGO_DOMAIN, "rimgo")
    }

    private fun setupPlatformInput(inputId: Int, buttonId: Int, prefKey: String, default: String, type: String) {
        val input = findViewById<com.google.android.material.textfield.TextInputEditText>(inputId)
        loadDomainPref(input, prefKey, default)
        setupSaveListener(input, prefKey)
        findViewById<com.google.android.material.button.MaterialButton>(buttonId).setOnClickListener {
            val defaults = when(type) {
                "imdb" -> AlternativeInstancesFetcher.getImdbDefaults()
                "medium" -> AlternativeInstancesFetcher.getMediumDefaults()
                "wikipedia" -> AlternativeInstancesFetcher.getWikilessDefaults()
                "goodreads" -> AlternativeInstancesFetcher.getBiblioReadsDefaults()
                "genius" -> AlternativeInstancesFetcher.getDumbDefaults()
                "github" -> AlternativeInstancesFetcher.getGotHubDefaults()
                "stackoverflow" -> AlternativeInstancesFetcher.getAnonymousOverflowDefaults()
                "tumblr" -> AlternativeInstancesFetcher.getPriviblurDefaults()
                "rural-dictionary" -> AlternativeInstancesFetcher.getRuralDictionaryDefaults()
                else -> null
            }
            if (defaults != null) {
                showInstancePicker(type, input, defaults)
            } else {
                SettingsUtils.fetchLatestInstances(this, type) { showInstancePicker(type, input, it) }
            }
        }
    }

    private fun loadDomainPref(input: com.google.android.material.textfield.TextInputEditText, key: String, default: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        input.setText(prefs.getString(key, default))
    }

    private fun setupSaveListener(input: com.google.android.material.textfield.TextInputEditText, key: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s?.toString()?.trim()
                if (!value.isNullOrEmpty()) {
                    prefs.edit { putString(key, value) }
                }
            }
        })
    }

    private fun showInstancePicker(type: String, targetInput: com.google.android.material.textfield.TextInputEditText, currentInstances: List<AlternativeInstancesFetcher.Instance>? = null) {
        SettingsUtils.showInstancePicker(this, type, targetInput, currentInstances)
    }

    private fun setupOrganicMapsNote() {
        val noteView = findViewById<android.widget.TextView>(R.id.textOrganicMapsNote) ?: return
        val text = getString(R.string.organic_maps_note)
        val linkText = "Organic Maps"
        val startIndex = text.indexOf(linkText)
        
        if (startIndex != -1) {
            val spannable = SpannableString(text)
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(view: View) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, "https://organicmaps.app".toUri())
                        startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this@SettingsFrontendsActivity, "Could not open URL", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            spannable.setSpan(clickableSpan, startIndex, startIndex + linkText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            noteView.text = spannable
            noteView.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
