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
import android.util.Log

/**
 * A UI-less activity that handles incoming URL intents and performs redirects.
 * This provides a faster experience by avoiding loading the main UI for simple redirects.
 */
class RedirectActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RedirectActivity"
        private const val UNSHORTEN_TIMEOUT_MS = 10000L
    }

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
            Intent.ACTION_SEND == action && sharedText != null -> SettingsUtils.extractUrl(sharedText) ?: sharedText
            else -> null
        }

        if (url != null) {
            val sanitizedUrl = UrlCleaner.ensureProtocol(url)
            if (UrlCleaner.isValidAppUrl(sanitizedUrl)) {
                processAndRedirect(url)
            } else {
                // Not a valid web URL, fall back to MainActivity or handle as text
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    setAction(Intent.ACTION_SEND)
                    putExtra(Intent.EXTRA_TEXT, url)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(mainIntent)
                finish()
            }
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


    private fun processAndRedirect(url: String) {
        val appContext = applicationContext // Capture for coroutine usage

        // Use a detached scope so the job isn't cancelled when Activity finishes
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            try {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
                val isImmediate = prefs.getBoolean(SettingsActivity.KEY_IMMEDIATE_NAVIGATION, true)
                
                // 1. Unshorten
                val unshortenedUrl = unshortenUrl(appContext, url)
                
                // 2. Clean & Resolve Target
                val (cleanedUrl, finalUrl) = resolveEffectiveUrl(appContext, unshortenedUrl)

                // 3. Navigate
                val wasNavigated = if (isImmediate) {
                    handleRedirectNavigation(appContext, unshortenedUrl, finalUrl)
                } else {
                    launchMainHistory(appContext)
                    false
                }

                // 4. Save History
                saveHistoryEntry(appContext, url, cleanedUrl, finalUrl, unshortenedUrl, wasNavigated)

            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "Error processing redirect", e)
                handleRedirectError(appContext, url)
            }
        }
        
        finish()
    }

    private suspend fun unshortenUrl(context: android.content.Context, url: String): String {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val sanitizedUrl = UrlCleaner.ensureProtocol(url)
        val shouldUnshorten = prefs.getBoolean(SettingsActivity.KEY_UNSHORTEN_URLS, true)
        val resolveHtml = prefs.getBoolean(SettingsActivity.KEY_RESOLVE_HTML_REDIRECTS, true)

        return if (shouldUnshorten) {
            try {
                kotlinx.coroutines.withTimeout(UNSHORTEN_TIMEOUT_MS) {
                     UrlUnshortener.unshorten(sanitizedUrl, resolveHtml)
                 }
            } catch (ignored: kotlinx.coroutines.TimeoutCancellationException) {
                 sanitizedUrl
             } catch (e: java.io.IOException) {
                 Log.w(TAG, "Network error during unshorten, using original URL", e)
                 sanitizedUrl
             } catch (e: java.net.URISyntaxException) {
                 Log.w(TAG, "Invalid URI during unshorten, using original URL", e)
                 sanitizedUrl
             }
        } else {
            sanitizedUrl
        }
    }

    private fun resolveEffectiveUrl(context: android.content.Context, unshortenedUrl: String): Pair<String, String> {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val shouldRemoveTracking = prefs.getBoolean(SettingsActivity.KEY_REMOVE_TRACKING, true)
        
        val cleaned = if (shouldRemoveTracking) {
            UrlCleaner.cleanUrl(unshortenedUrl)
        } else {
            unshortenedUrl
        }
        
        val redirectUrl = SettingsUtils.resolveTargetUrl(context, cleaned, unshortenedUrl)
        val finalUrl = UrlCleaner.ensureProtocol(redirectUrl)
        
        return Pair(cleaned, finalUrl)
    }

    private fun handleRedirectNavigation(context: android.content.Context, unshortenedUrl: String, finalUrl: String): Boolean {
        if (SettingsUtils.shouldOpenInWebView(context, unshortenedUrl)) {
            val webIntent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(WebViewActivity.EXTRA_URL, finalUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
            return true
        } else {
            val redirectIntent = Intent(Intent.ACTION_VIEW, finalUrl.toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val handlers = context.packageManager.queryIntentActivities(redirectIntent, 0)
            val selfHandler = handlers.find { it.activityInfo.packageName == context.packageName }
            
            if (selfHandler != null) {
                val otherHandler = handlers.firstOrNull { it.activityInfo.packageName != context.packageName }
                if (otherHandler != null) {
                    redirectIntent.setPackage(otherHandler.activityInfo.packageName)
                    context.startActivity(redirectIntent)
                    return true
                } else {
                    // Fallback to MainActivity
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        data = finalUrl.toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(mainIntent)
                    return true
                }
            } else {
                context.startActivity(redirectIntent)
                return true
            }
        }
    }

    private fun launchMainHistory(context: android.content.Context) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(mainIntent)
    }

    private suspend fun saveHistoryEntry(
        context: android.content.Context, 
        originalUrl: String, 
        cleanedUrl: String, 
        finalUrl: String, 
        unshortenedUrl: String, 
        wasNavigated: Boolean
    ) {
        withContext(Dispatchers.IO) {
            val entry = HistoryManager.HistoryEntry(
                originalUrl = originalUrl,
                cleanedUrl = cleanedUrl,
                processedUrl = finalUrl,
                timestamp = System.currentTimeMillis(),
                unshortenedUrl = unshortenedUrl,
                isPreviewFetched = false
            )
            HistoryManager.addToHistory(context, entry)
            HistoryManager.applyRetentionPolicy(context)
            
            if (wasNavigated) {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                val currentCount = prefs.getLong("sidestep_count", 0L)
                prefs.edit()
                    .putLong("sidestep_count", currentCount + 1)
                    .putBoolean("has_processed_url", true)
                    .apply()
            }
        }
    }

    private suspend fun handleRedirectError(context: android.content.Context, url: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to process link", Toast.LENGTH_SHORT).show()
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("error_url", url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mainIntent)
        }
    }



}
