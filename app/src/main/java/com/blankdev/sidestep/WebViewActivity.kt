package com.blankdev.sidestep

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.util.Log

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var currentUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply theme after super.onCreate to ensure context is fully initialized
        applyTheme()
        
        enableEdgeToEdge()
        
        webView = WebView(this).apply {
            settings.apply {
                @SuppressLint("SetJavaScriptEnabled")
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            // Use strict WebViewClient
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (isFinishing || isDestroyed) return true
                    
                    val url = request?.url?.toString() ?: return false
                    // Keep http/https in WebView
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        return false 
                    }
                    // Let system handle other schemes (mailto, intent, etc.)
                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, request?.url)
                        startActivity(intent)
                        true
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "No activity found to handle URL: $url", e)
                        true // Prevent crash if no handler
                    }
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (isFinishing || isDestroyed) return
                    
                    currentUrl = url
                    this@WebViewActivity.title = if (url != null) {
                        try { 
                            url.toUri().host ?: DEFAULT_TITLE 
                        } catch (e: IllegalArgumentException) { 
                            Log.e(TAG, "Failed to parse host from URL: $url", e)
                            DEFAULT_TITLE 
                        }
                    } else DEFAULT_TITLE
                }
            }
        }
        
        // Create Root Layout
        val rootLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(getThemeColor(android.R.attr.colorBackground))
        }

        // Add explicit MaterialToolbar
        val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
            title = DEFAULT_TITLE
            setTitleTextColor(getThemeColor(android.R.attr.textColorPrimary))
            setBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorSurface))
            elevation = TOOLBAR_ELEVATION
        }
        rootLayout.addView(toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rootLayout.addView(webView, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            LAYOUT_WEIGHT_MATCH_PARENT
        ))
        
        setContentView(rootLayout)
        
        // Handle Window Insets for Edge-to-Edge
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(0, bars.top, 0, 0)
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        
        currentUrl = intent?.getStringExtra(EXTRA_URL)
        if (currentUrl != null && UrlCleaner.isValidAppUrl(currentUrl)) {
            val headers = mapOf("X-Requested-With" to packageName)
            webView.loadUrl(currentUrl!!, headers)
        } else {
            finish()
        }
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        if (theme.resolveAttribute(attr, typedValue, true)) {
            // Check if it's a color resource or a raw color data
            return if (typedValue.resourceId != 0) {
                try {
                    androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId)
                } catch (e: Exception) {
                    // Fallback for cases where resourceId is not a color
                    typedValue.data
                }
            } else {
                typedValue.data
            }
        }
        return android.graphics.Color.MAGENTA
    }

    private fun handleOpenInBrowser() {
        try {
            currentUrl?.let {
                val browserIntent = Intent(Intent.ACTION_VIEW, it.toUri())
                startActivity(browserIntent)
            }
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No browser found to handle URL: $currentUrl", e)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_SHARE, Menu.NONE, "Share").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setIcon(R.drawable.ic_share)
        }
        menu.add(Menu.NONE, MENU_OPEN_IN_BROWSER, 1, "Open in Browser").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
                true
            }
            MENU_SHARE -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, currentUrl ?: "")
                }
                startActivity(Intent.createChooser(shareIntent, "Share URL"))
                true
            }
            MENU_OPEN_IN_BROWSER -> {
                handleOpenInBrowser()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val TAG = "WebViewActivity"
        private const val DEFAULT_TITLE = "Sidestep"
        private const val TOOLBAR_ELEVATION = 4f
        private const val LAYOUT_WEIGHT_MATCH_PARENT = 1f
        
        const val EXTRA_URL = "extra_url"
        private const val MENU_SHARE = 101
        private const val MENU_OPEN_IN_BROWSER = 102
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
}
