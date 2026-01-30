package com.blankdev.sidestep

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

/**
 * A UI-less activity that handles incoming URL intents and performs redirects.
 * This provides a faster experience by avoiding loading the main UI for simple redirects.
 */
class RedirectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle the intent and then finish
        if (intent != null) {
            handleIntent(intent)
        } else {
            finish()
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data = intent.dataString
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        val url = when {
            Intent.ACTION_VIEW == action && data != null -> data
            Intent.ACTION_SEND == action && sharedText != null -> extractUrl(sharedText) ?: sharedText
            else -> null
        }

        if (url != null) {
            processAndRedirect(url)
        } else {
            // If we can't find a URL, fall back to MainActivity
            val mainIntent = Intent(intent).apply {
                setClass(this@RedirectActivity, MainActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(mainIntent)
            finish()
        }
    }

    private fun extractUrl(text: String): String? {
        val pattern = android.util.Patterns.WEB_URL
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group()
        } else null
    }

    private fun processAndRedirect(url: String) {
        val appContext = applicationContext // Capture for coroutine usage

        // Use a detached scope so the job isn't cancelled when Activity finishes
        // This ensures the redirect happens even if the transparent activity is destroyed immediately
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            try {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
                val sanitizedUrl = UrlCleaner.ensureProtocol(url)

                // Step 1: Unshorten with timeout (Network call) - Respect setting
                val shouldUnshorten = prefs.getBoolean(SettingsActivity.KEY_UNSHORTEN_URLS, true)
                val resolveHtml = prefs.getBoolean(SettingsActivity.KEY_RESOLVE_HTML_REDIRECTS, true)

                val unshortenedUrl = if (shouldUnshorten) {
                    try {
                        kotlinx.coroutines.withTimeout(5000) {  // 5 second timeout
                            UrlUnshortener.unshorten(sanitizedUrl, resolveHtml)
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        sanitizedUrl  // Use original URL if timeout
                    }
                } else {
                    sanitizedUrl
                }
                
                // Step 2: Clean and resolve target - Respect tracking setting
                val shouldRemoveTracking = prefs.getBoolean(SettingsActivity.KEY_REMOVE_TRACKING, true)
                val cleaned = if (shouldRemoveTracking) {
                    UrlCleaner.cleanUrl(unshortenedUrl)
                } else {
                    unshortenedUrl
                }
                val redirectUrl = SettingsUtils.resolveTargetUrl(appContext, cleaned, unshortenedUrl)
                val finalUrl = UrlCleaner.ensureProtocol(redirectUrl)

                // Step 3: Save to History (Background)
                withContext(Dispatchers.IO) {
                    val entry = HistoryManager.HistoryEntry(
                        originalUrl = url,
                        cleanedUrl = cleaned,
                        processedUrl = finalUrl,
                        timestamp = System.currentTimeMillis(),
                        unshortenedUrl = unshortenedUrl,
                        isPreviewFetched = false
                    )
                    HistoryManager.addToHistory(appContext, entry)
                    HistoryManager.applyRetentionPolicy(appContext)
                    
                    // Update global count
                    val currentCount = prefs.getLong("sidestep_count", 0L)
                    prefs.edit()
                        .putLong("sidestep_count", currentCount + 1)
                        .putBoolean("has_processed_url", true)
                        .apply()
                }

                // Step 4: Perform Redirect
                val isImmediate = prefs.getBoolean(SettingsActivity.KEY_IMMEDIATE_NAVIGATION, true)
                
                if (isImmediate) {
                    if (shouldOpenInWebView(appContext, unshortenedUrl)) {
                        val webIntent = Intent(appContext, WebViewActivity::class.java).apply {
                            putExtra(WebViewActivity.EXTRA_URL, finalUrl)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        appContext.startActivity(webIntent)
                    } else {
                        val redirectIntent = Intent(Intent.ACTION_VIEW, finalUrl.toUri()).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        
                        // Detection for loops - ONLY set package if we are one of the handlers
                        val handlers = appContext.packageManager.queryIntentActivities(redirectIntent, 0)
                        val selfHandler = handlers.find { it.activityInfo.packageName == appContext.packageName }
                        
                        if (selfHandler != null) {
                            val otherHandler = handlers.firstOrNull { it.activityInfo.packageName != appContext.packageName }
                            if (otherHandler != null) {
                                redirectIntent.setPackage(otherHandler.activityInfo.packageName)
                                appContext.startActivity(redirectIntent)
                            } else {
                                // Fallback to MainActivity if no other handler found or to show error
                                val mainIntent = Intent(appContext, MainActivity::class.java).apply {
                                    data = finalUrl.toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                appContext.startActivity(mainIntent)
                            }
                        } else {
                            // We are not a handler, just let the system handle it
                            appContext.startActivity(redirectIntent)
                        }
                    }
                } else {
                    // Start MainActivity to show history
                    val mainIntent = Intent(appContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    appContext.startActivity(mainIntent)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Fallback to MainActivity on error
                    val mainIntent = Intent(appContext, MainActivity::class.java).apply {
                        putExtra("error_url", url)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(mainIntent)
                }
            }
        }
        
        // Finish immediately for seamless experience
        finish()
    }


    private fun shouldOpenInWebView(context: android.content.Context, unshortenedUrl: String): Boolean {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        
        // Match domain handling in MainActivity/UrlCleaner
        val isTwitter = UrlCleaner.isTwitterOrXUrl(unshortenedUrl)
        val isReddit = UrlCleaner.isRedditUrl(unshortenedUrl)
        val isYouTube = UrlCleaner.isYouTubeUrl(unshortenedUrl)
        val isImdb = UrlCleaner.isImdbUrl(unshortenedUrl)
        val isMedium = UrlCleaner.isMediumUrl(unshortenedUrl)
        val isWikipedia = UrlCleaner.isWikipediaUrl(unshortenedUrl)
        val isGoodreads = UrlCleaner.isGoodreadsUrl(unshortenedUrl)
        val isGenius = UrlCleaner.isGeniusUrl(unshortenedUrl)
        val isGitHub = UrlCleaner.isGitHubUrl(unshortenedUrl)
        val isStackOverflow = UrlCleaner.isStackOverflowUrl(unshortenedUrl)

        val isCleanOnly = when {
            isTwitter -> prefs.getBoolean(SettingsActivity.KEY_TWITTER_CLEAN_ONLY, false)
            isReddit -> prefs.getBoolean(SettingsActivity.KEY_REDDIT_CLEAN_ONLY, false)
            isYouTube -> prefs.getBoolean(SettingsActivity.KEY_YOUTUBE_CLEAN_ONLY, false)
            isImdb -> prefs.getBoolean(SettingsActivity.KEY_IMDB_CLEAN_ONLY, false)
            isMedium -> prefs.getBoolean(SettingsActivity.KEY_MEDIUM_CLEAN_ONLY, false)
            isWikipedia -> prefs.getBoolean(SettingsActivity.KEY_WIKIPEDIA_CLEAN_ONLY, false)
            isGoodreads -> prefs.getBoolean(SettingsActivity.KEY_GOODREADS_CLEAN_ONLY, false)
            isGenius -> prefs.getBoolean(SettingsActivity.KEY_GENIUS_CLEAN_ONLY, false)
            isGitHub -> prefs.getBoolean(SettingsActivity.KEY_GITHUB_CLEAN_ONLY, false)
            isStackOverflow -> prefs.getBoolean(SettingsActivity.KEY_STACKOVERFLOW_CLEAN_ONLY, false)
            UrlCleaner.isUrbanDictionaryUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_RURAL_DICTIONARY_CLEAN_ONLY, false)
            UrlCleaner.isGoogleMapsUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_GOOGLE_MAPS_CLEAN_ONLY, false)
            else -> false
        }

        if (!isCleanOnly) return false

        // Only open in WebView if we are the default handler for this domain
        // to avoid the endless redirect loop that would happen if we sent a VIEW intent
        return try {
            val host = unshortenedUrl.toUri().host?.lowercase() ?: ""
            isAppDefaultHandlerForDomain(context, unshortenedUrl)
        } catch (e: Exception) {
            false
        }
    }

    private fun isAppDefaultHandlerForDomain(context: android.content.Context, url: String): Boolean {
        return SettingsUtils.checkDomain(context, url)
    }

}
