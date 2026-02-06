package com.blankdev.sidestep

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.TextView
import android.text.Html
import android.text.Spannable
import android.text.style.URLSpan
import android.text.style.ClickableSpan
import android.text.method.LinkMovementMethod
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.util.UUID
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import com.blankdev.sidestep.BuildConfig
import android.util.Log
import org.json.JSONException

data class CustomRedirect(
    val id: String,
    val originalDomain: String,
    val redirectDomain: String,
    val isAppend: Boolean,
    val isEnabled: Boolean = true
)

object SettingsUtils {

    enum class DomainStatus {
        NONE, PARTIAL, FULL
    }

    fun getCustomRedirects(context: Context): List<CustomRedirect> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = prefs.getString(SettingsActivity.KEY_CUSTOM_REDIRECTS, null)

        if (json == null) {
            return listOf(
                CustomRedirect("default_nytimes", "nytimes.com", "https://archive.ph/", true, true),
                CustomRedirect("default_tiktok_swap", "tiktok.com", "offtiktok.com", false, true),
                CustomRedirect("default_tiktok_append", "tiktok.com", "https://anydownloader.com/en/#url=", true, false),
                CustomRedirect("default_instagram", "instagram.com", "https://snapinsta.to/en2?q=", true, false),
                CustomRedirect("default_spotify", "open.spotify.com", "song.link", true, true),
                CustomRedirect("default_apple_podcasts", "podcasts.apple.com", "pods.link", true, true)
            )
        }
        return try {
            val array = org.json.JSONArray(json)
            val list = mutableListOf<CustomRedirect>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(CustomRedirect(
                    obj.getString("id"),
                    obj.getString("original"),
                    obj.getString("redirect"),
                    obj.getBoolean("isAppend"),
                    obj.optBoolean("isEnabled", true)
                ))
            }
            
            // Enforce default order if it matches the default set (hack for stale data)
            if ((list.size == 4 || list.size == 6) && list.any { it.originalDomain == "nytimes.com" } && list.count { it.originalDomain == "tiktok.com" } == 2) {
                val updatedList = if (list.size == 4) {
                    list.toMutableList().apply {
                        if (none { it.id == "default_spotify" }) add(CustomRedirect("default_spotify", "open.spotify.com", "song.link", true, true))
                        if (none { it.id == "default_apple_podcasts" }) add(CustomRedirect("default_apple_podcasts", "podcasts.apple.com", "pods.link", true, true))
                    }
                } else {
                    list
                }

                return updatedList.sortedWith(compareBy<CustomRedirect> { 
                    when {
                        it.originalDomain == "nytimes.com" -> 0
                        it.originalDomain == "tiktok.com" && !it.isAppend -> 1 // Domain Swap
                        it.originalDomain == "tiktok.com" && it.isAppend -> 2 // Append
                        it.originalDomain == "instagram.com" -> 3
                        it.originalDomain == "open.spotify.com" -> 4
                        it.originalDomain == "podcasts.apple.com" -> 5
                        else -> 6
                    }
                })
            }
            
            list
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse custom redirects JSON", e)
            emptyList()
        }
    }

    fun saveRedirectsList(context: Context, list: List<CustomRedirect>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val array = org.json.JSONArray()
        list.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("original", it.originalDomain)
            obj.put("redirect", it.redirectDomain)
            obj.put("isAppend", it.isAppend)
            obj.put("isEnabled", it.isEnabled)
            array.put(obj)
        }
        prefs.edit { putString(SettingsActivity.KEY_CUSTOM_REDIRECTS, array.toString()) }
    }

    fun saveCustomRedirect(context: Context, redirect: CustomRedirect) {
        val redirects = getCustomRedirects(context).toMutableList()
        val index = redirects.indexOfFirst { it.id == redirect.id }
        if (index >= 0) {
            redirects[index] = redirect
        } else {
            redirects.add(redirect)
        }
        saveRedirectsList(context, redirects)
    }

    fun deleteCustomRedirect(context: Context, id: String) {
        val redirects = getCustomRedirects(context).filter { it.id != id }
        saveRedirectsList(context, redirects)
    }

    fun getDomainHandlingStatus(context: Context, domain: String): DomainStatus {
        val domainsToCheck = when(domain) {
            "tiktok.com" -> listOf("tiktok.com", "www.tiktok.com", "v.tiktok.com", "vt.tiktok.com", "vm.tiktok.com")
            "x.com" -> listOf("x.com", "www.x.com", "twitter.com", "www.twitter.com", "t.co")
            "reddit.com" -> listOf("reddit.com", "www.reddit.com", "redd.it")
            "youtube.com" -> listOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
            "imdb.com" -> listOf("imdb.com", "www.imdb.com", "m.imdb.com")
            "medium.com" -> listOf("medium.com", "www.medium.com", "link.medium.com")
            "wikipedia.org" -> listOf("wikipedia.org", "www.wikipedia.org", "en.wikipedia.org")
            "goodreads.com" -> listOf("goodreads.com", "www.goodreads.com")
            "urbandictionary.com" -> listOf("urbandictionary.com", "www.urbandictionary.com")
            "imgur.com" -> listOf("imgur.com", "www.imgur.com")
            "maps.google.com", "google.com" -> listOf("maps.google.com", "www.maps.google.com")
            else -> listOf(domain)
        }

        val verifiedCount = domainsToCheck.count { checkDomain(context, it) }

        return when {
            verifiedCount == 0 -> DomainStatus.NONE
            verifiedCount == domainsToCheck.size -> DomainStatus.FULL
            else -> DomainStatus.PARTIAL
        }
    }

    fun isAppDefaultHandlerForDomain(context: Context, domain: String): Boolean {
        return getDomainHandlingStatus(context, domain) != DomainStatus.NONE
    }

    fun checkDomain(context: Context, urlOrDomain: String): Boolean {
        val uri = if (urlOrDomain.startsWith("http")) {
            urlOrDomain.toUri()
        } else {
            "https://$urlOrDomain".toUri()
        }
        val host = uri.host?.lowercase() ?: ""
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val dvm = context.getSystemService(android.content.pm.verify.domain.DomainVerificationManager::class.java)
            try {
                val userState = dvm.getDomainVerificationUserState(context.packageName)
                if (userState != null && host.isNotEmpty()) {
                    val hostStatus = userState.hostToStateMap[host]
                    if (hostStatus == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED ||
                        hostStatus == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_SELECTED) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check domain verification status", e)
            }
        }
        
        val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun getThemeColor(context: Context, attr: Int): Int {
        val typedValue = android.util.TypedValue()
        if (context.theme.resolveAttribute(attr, typedValue, true)) {
            return if (typedValue.resourceId != 0) {
                try {
                    androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
                } catch (e: Exception) {
                    typedValue.data
                }
            } else {
                typedValue.data
            }
        }
        return android.graphics.Color.MAGENTA
    }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun extractUrl(text: String): String? {
        val pattern = android.util.Patterns.WEB_URL
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group()
        } else null
    }

    fun shouldOpenInWebView(context: Context, unshortenedUrl: String): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        
        val isCleanOnly = when {
            UrlCleaner.isTwitterOrXUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_TWITTER_CLEAN_ONLY, false)
            UrlCleaner.isRedditUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_REDDIT_CLEAN_ONLY, false)
            UrlCleaner.isYouTubeUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_YOUTUBE_CLEAN_ONLY, false)
            UrlCleaner.isImdbUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_IMDB_CLEAN_ONLY, false)
            UrlCleaner.isMediumUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_MEDIUM_CLEAN_ONLY, false)
            UrlCleaner.isWikipediaUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_WIKIPEDIA_CLEAN_ONLY, false)
            UrlCleaner.isGoodreadsUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_GOODREADS_CLEAN_ONLY, false)
            UrlCleaner.isGeniusUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_GENIUS_CLEAN_ONLY, false)
            UrlCleaner.isGitHubUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_GITHUB_CLEAN_ONLY, false)
            UrlCleaner.isStackOverflowUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_STACKOVERFLOW_CLEAN_ONLY, false)
            UrlCleaner.isUrbanDictionaryUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_RURAL_DICTIONARY_CLEAN_ONLY, false)
            UrlCleaner.isGoogleMapsUrl(unshortenedUrl) -> prefs.getBoolean(SettingsActivity.KEY_GOOGLE_MAPS_CLEAN_ONLY, false)
            else -> false
        }
        
        if (!isCleanOnly) return false
        
        // If it's clean only, we only open in WebView if we are the default handler for this domain
        // to avoid the endless redirect loop that would happen if we sent a VIEW intent
        return try {
            checkDomain(context, unshortenedUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking domain for WebView", e)
            false
        }
    }

    fun fetchLatestInstances(activity: AppCompatActivity, type: String, onFetched: (List<AlternativeInstancesFetcher.Instance>) -> Unit) {
        activity.lifecycleScope.launch {
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Fetching instances")
                .setMessage("Getting healthy alternative frontends...")
                .setCancelable(false)
                .create()
            progressDialog.show()

            val fetched = when (type) {
                "twitter" -> AlternativeInstancesFetcher.fetchNitterInstances()
                "youtube" -> AlternativeInstancesFetcher.fetchInvidiousInstances()
                "piped" -> AlternativeInstancesFetcher.fetchPipedInstances()
                "reddit" -> AlternativeInstancesFetcher.fetchRedlibInstances()
                "imdb" -> AlternativeInstancesFetcher.fetchLibremdbInstances()
                "medium" -> AlternativeInstancesFetcher.fetchScribeInstances()
                "wikipedia" -> AlternativeInstancesFetcher.fetchWikilessInstances()
                "goodreads" -> AlternativeInstancesFetcher.fetchBiblioReadsInstances()
                "genius" -> AlternativeInstancesFetcher.fetchDumbInstances()
                "github" -> AlternativeInstancesFetcher.fetchGotHubInstances()
                "stackoverflow" -> AlternativeInstancesFetcher.fetchAnonymousOverflowInstances()
                "rural-dictionary" -> AlternativeInstancesFetcher.fetchRuralDictionaryInstances()
                "rimgo" -> AlternativeInstancesFetcher.fetchRimgoInstances()
                "tumblr" -> AlternativeInstancesFetcher.fetchPriviblurInstances()
                "intellectual" -> AlternativeInstancesFetcher.fetchIntellectualInstances()
                else -> AlternativeInstancesFetcher.fetchRedlibInstances()
            }
            
            progressDialog.dismiss()
            saveCachedInstances(activity, type, fetched)
            onFetched(fetched)
        }
    }

    fun showInstancePicker(activity: AppCompatActivity, type: String, targetInput: com.google.android.material.textfield.TextInputEditText, currentInstances: List<AlternativeInstancesFetcher.Instance>? = null) {
        val instances = (currentInstances ?: when (type) {
            "twitter" -> getCachedInstances(activity, "twitter") ?: AlternativeInstancesFetcher.getNitterDefaults()
            "youtube" -> getCachedInstances(activity, "youtube") ?: AlternativeInstancesFetcher.getInvidiousDefaults()
            "piped" -> getCachedInstances(activity, "piped") ?: AlternativeInstancesFetcher.getPipedDefaults()
            "imdb" -> getCachedInstances(activity, "imdb") ?: AlternativeInstancesFetcher.getImdbDefaults()
            "medium" -> getCachedInstances(activity, "medium") ?: AlternativeInstancesFetcher.getMediumDefaults()
            "wikipedia" -> getCachedInstances(activity, "wikipedia") ?: AlternativeInstancesFetcher.getWikilessDefaults()
            "goodreads" -> getCachedInstances(activity, "goodreads") ?: AlternativeInstancesFetcher.getBiblioReadsDefaults()
            "genius" -> getCachedInstances(activity, "genius") ?: AlternativeInstancesFetcher.getDumbDefaults()
            "github" -> getCachedInstances(activity, "github") ?: AlternativeInstancesFetcher.getGotHubDefaults()
            "stackoverflow" -> getCachedInstances(activity, "stackoverflow") ?: AlternativeInstancesFetcher.getAnonymousOverflowDefaults()
            "rural-dictionary" -> getCachedInstances(activity, "rural-dictionary") ?: AlternativeInstancesFetcher.getRuralDictionaryDefaults()
            "tumblr" -> getCachedInstances(activity, "tumblr") ?: AlternativeInstancesFetcher.getPriviblurDefaults()
            "rimgo" -> getCachedInstances(activity, "rimgo") ?: AlternativeInstancesFetcher.getRimgoDefaults()
            "intellectual" -> getCachedInstances(activity, "intellectual") ?: AlternativeInstancesFetcher.getIntellectualDefaults()
            else -> getCachedInstances(activity, "reddit") ?: AlternativeInstancesFetcher.getRedlibDefaults()
        }).filter { instance ->
            val isDown = instance.health == HEALTH_DOWN || 
                         (instance.health == HEALTH_UNHEALTHY || instance.health == HEALTH_DEAD)
            val isZeroUptime = instance.uptime?.startsWith("0") == true || instance.uptime7d?.startsWith("0") == true
            !(isDown && isZeroUptime)
        }

        val displayList = instances.map { instance ->
            val uptime = instance.uptimeValue
            val (colorId, emoji) = when {
                instance.health == HEALTH_HEALTHY || uptime >= UPTIME_THRESHOLD_HEALTHY -> R.color.status_green_pastel to HEALTH_EMOJI
                instance.health == HEALTH_ISSUES || uptime >= UPTIME_THRESHOLD_ISSUES -> R.color.status_yellow_pastel to HEALTH_EMOJI
                instance.health == HEALTH_DOWN || uptime < UPTIME_THRESHOLD_ISSUES && instance.uptime != null -> R.color.status_red_pastel to HEALTH_EMOJI
                else -> null to null
            }
            
            if (emoji == null || colorId == null) {
                android.text.SpannableString(instance.domain)
            } else {
                val uptimeStr = instance.uptime7d ?: instance.uptime ?: "?"
                val fullText = "${instance.domain} | $uptimeStr | $emoji"
                val spannable = android.text.SpannableString(fullText)
                
                val color = androidx.core.content.ContextCompat.getColor(activity, colorId)
                val start = fullText.lastIndexOf(emoji)
                if (start >= 0) {
                    spannable.setSpan(
                        android.text.style.ForegroundColorSpan(color),
                        start, 
                        start + emoji.length, 
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                spannable
            }
        }.map { it as CharSequence }.toTypedArray()

        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle("Select instance")
            .setItems(displayList) { _, which ->
                val selected = instances[which]
                targetInput.setText(selected.domain)
            }
            .setNegativeButton("Cancel", null)

            .setNegativeButton("Cancel", null)
            
        if (type == "twitter" || type == "youtube" || type == "piped" || type == "reddit" || type == "medium" || type == "goodreads" || type == "genius" || type == "intellectual" || type == "github" || type == "stackoverflow" || type == "wikipedia" || type == "imdb" || type == "tumblr" || type == "rural-dictionary" || type == "rimgo") {
            builder.setNeutralButton("🔄 Pull latest") { _, _ ->
                fetchLatestInstances(activity, type) { fetchedInstances ->
                    showInstancePicker(activity, type, targetInput, fetchedInstances)
                }
            }
        }
            
        builder.show()
    }

    private fun saveCachedInstances(context: Context, type: String, instances: List<AlternativeInstancesFetcher.Instance>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val array = org.json.JSONArray()
        instances.forEach { instance ->
            val obj = org.json.JSONObject()
            obj.put("domain", instance.domain)
            obj.put("uptime", instance.uptime)
            obj.put("uptime7d", instance.uptime7d)
            obj.put("health", instance.health)
            array.put(obj)
        }
        prefs.edit { putString("cached_instances_$type", array.toString()) }
    }

    private fun getCachedInstances(context: Context, type: String): List<AlternativeInstancesFetcher.Instance>? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = prefs.getString("cached_instances_$type", null) ?: return null
        try {
            val array = org.json.JSONArray(json)
            val list = mutableListOf<AlternativeInstancesFetcher.Instance>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(AlternativeInstancesFetcher.Instance(
                    domain = obj.getString("domain"),
                    uptime = obj.optString("uptime", null as String?),
                    uptime7d = obj.optString("uptime7d", null as String?),
                    health = obj.optString("health", null as String?)
                ))
            }
            return list
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse cached instances JSON for $type", e)
            return null
        }
    }

    fun resolveTargetUrl(context: Context, cleanedUrl: String, unshortenedUrl: String): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        var redirectUrl: String = cleanedUrl

        // Apply Custom Redirects
        val redirects = getCustomRedirects(context)
        var appliedCustom = false
        
        for (redirect in redirects) {
            if (!redirect.isEnabled) continue
            
            val originalDomain = redirect.originalDomain.lowercase()
            val redirectDomain = redirect.redirectDomain
            val isAppend = redirect.isAppend
            
            val uri = unshortenedUrl.toUri()
            val host = uri.host?.lowercase() ?: ""
            
            // Match if host contains the original domain (e.g. "www.tiktok.com" contains "tiktok.com")
            if (host.contains(originalDomain) || unshortenedUrl.contains(originalDomain, ignoreCase = true)) {
                if (isAppend) {
                    var base = redirectDomain
                    if (!base.startsWith("http")) base = "https://$base"
                    if (!base.contains("?") && !base.endsWith("/")) base = "$base/"
                    redirectUrl = "$base$cleanedUrl"
                } else {
                    redirectUrl = UrlCleaner.replaceDomain(cleanedUrl, redirectDomain)
                }
                appliedCustom = true
                break
            }
        }

        if (!appliedCustom) {
            val domainMapping = listOf(
                Pair({ UrlCleaner.isTwitterOrXUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_TWITTER_CLEAN_ONLY, Pair(SettingsActivity.KEY_ALTERNATIVE_DOMAIN, SettingsActivity.DEFAULT_ALTERNATIVE_DOMAIN))),
                Pair({ UrlCleaner.isRedditUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_REDDIT_CLEAN_ONLY, Pair(SettingsActivity.KEY_REDDIT_DOMAIN, SettingsActivity.DEFAULT_REDDIT_DOMAIN))),
                Pair({ UrlCleaner.isYouTubeUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_YOUTUBE_CLEAN_ONLY, Pair(SettingsActivity.KEY_YOUTUBE_DOMAIN, SettingsActivity.DEFAULT_YOUTUBE_DOMAIN))),
                Pair({ UrlCleaner.isImdbUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_IMDB_CLEAN_ONLY, Pair(SettingsActivity.KEY_IMDB_DOMAIN, SettingsActivity.DEFAULT_IMDB_DOMAIN))),
                Pair({ UrlCleaner.isMediumUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_MEDIUM_CLEAN_ONLY, Pair(SettingsActivity.KEY_MEDIUM_DOMAIN, SettingsActivity.DEFAULT_MEDIUM_DOMAIN))),
                Pair({ UrlCleaner.isWikipediaUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_WIKIPEDIA_CLEAN_ONLY, Pair(SettingsActivity.KEY_WIKIPEDIA_DOMAIN, SettingsActivity.DEFAULT_WIKIPEDIA_DOMAIN))),
                Pair({ UrlCleaner.isGoodreadsUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_GOODREADS_CLEAN_ONLY, Pair(SettingsActivity.KEY_GOODREADS_DOMAIN, SettingsActivity.DEFAULT_GOODREADS_DOMAIN))),
                Pair({ UrlCleaner.isGeniusUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_GENIUS_CLEAN_ONLY, Pair(SettingsActivity.KEY_GENIUS_DOMAIN, SettingsActivity.DEFAULT_GENIUS_DOMAIN))),
                Pair({ UrlCleaner.isGitHubUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_GITHUB_CLEAN_ONLY, Pair(SettingsActivity.KEY_GITHUB_DOMAIN, SettingsActivity.DEFAULT_GITHUB_DOMAIN))),
                Pair({ UrlCleaner.isStackOverflowUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_STACKOVERFLOW_CLEAN_ONLY, Pair(SettingsActivity.KEY_STACKOVERFLOW_DOMAIN, SettingsActivity.DEFAULT_STACKOVERFLOW_DOMAIN))),
                Pair({ UrlCleaner.isTumblrUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_TUMBLR_CLEAN_ONLY, Pair(SettingsActivity.KEY_TUMBLR_DOMAIN, SettingsActivity.DEFAULT_TUMBLR_DOMAIN))),
                Pair({ UrlCleaner.isUrbanDictionaryUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_RURAL_DICTIONARY_CLEAN_ONLY, Pair(SettingsActivity.KEY_RURAL_DICTIONARY_DOMAIN, SettingsActivity.DEFAULT_RURAL_DICTIONARY_DOMAIN))),
                Pair({ UrlCleaner.isImgurUrl(unshortenedUrl) }, Pair(SettingsActivity.KEY_RIMGO_CLEAN_ONLY, Pair(SettingsActivity.KEY_RIMGO_DOMAIN, SettingsActivity.DEFAULT_RIMGO_DOMAIN)))
            )

            for ((condition, settings) in domainMapping) {
                if (condition()) {
                    val (cleanOnlyKey, domainPrefs) = settings
                    if (!prefs.getBoolean(cleanOnlyKey, false)) {
                        val (domainKey, defaultDomain) = domainPrefs
                        val domain = prefs.getString(domainKey, defaultDomain) ?: defaultDomain
                        redirectUrl = UrlCleaner.replaceDomain(cleanedUrl, domain)
                    }
                    return redirectUrl
                }
            }

            // Special case for Google Maps as it doesn't just replace domain
            if (UrlCleaner.isGoogleMapsUrl(unshortenedUrl)) {
                if (!prefs.getBoolean(SettingsActivity.KEY_GOOGLE_MAPS_CLEAN_ONLY, false)) {
                    redirectUrl = UrlCleaner.convertGoogleMapsToOsm(unshortenedUrl)
                }
                return redirectUrl
            }
        }
        return redirectUrl
    }

    fun showAboutDialog(activity: AppCompatActivity) {
        val context = activity
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.bottom_sheet_about, null)
        bottomSheetDialog.setContentView(view)

        val versionText = view.findViewById<TextView>(R.id.aboutVersion)
        versionText.text = "Version ${BuildConfig.VERSION_NAME}"

        val creditsText = view.findViewById<TextView>(R.id.aboutCredits)
        
        val creditsHtml = """
            Sidestep is built upon the labor of many privacy-focused developers whose work adds a layer of protection from the surveillance industry. Alternative frontends allow for a less cluttered web experience; bypassing closed ecosystems and promoting a more open internet.
            <br><br>
            Special thanks to <a href="https://github.com/zedeus/nitter">Nitter</a>, <a href="https://github.com/redlib-org/redlib">Redlib</a>, <a href="https://github.com/iv-org/invidious">Invidious</a>, <a href="https://github.com/TeamPiped/Piped">Piped</a>, <a href="https://github.com/zyachel/libremdb">LibreMDB</a>, <a href="https://sr.ht/~edwardloveall/Scribe">Scribe</a>, <a href="https://github.com/Metastem/wikiless">Wikiless</a>, <a href="https://github.com/nesaku/BiblioReads">BiblioReads</a>, <a href="https://github.com/rramiachraf/dumb">Dumb</a>, <a href="https://github.com/Insprill/intellectual">Intellectual</a>, <a href="https://github.com/neofelix/gothub">GotHub</a>, <a href="https://github.com/httpjamesm/AnonymousOverflow">AnonymousOverflow</a>, <a href="https://github.com/syeopite/priviblur">Priviblur</a>, <a href="https://codeberg.org/zortazert/rural-dictionary">RuralDictionary</a>, and <a href="https://codeberg.org/rimgo">rimgo</a>.
        """.trimIndent()

        val spanned = Html.fromHtml(creditsHtml, Html.FROM_HTML_MODE_LEGACY) as Spannable
        val urls = spanned.getSpans(0, spanned.length, URLSpan::class.java)
        
        for (urlSpan in urls) {
            val start = spanned.getSpanStart(urlSpan)
            val end = spanned.getSpanEnd(urlSpan)
            val spanFlags = spanned.getSpanFlags(urlSpan)
            val url = urlSpan.url
            
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(view: View) {
                    // Navigate with redirection support
                    val cleaned = UrlCleaner.cleanUrl(url)
                    val redirectUrl = resolveTargetUrl(context, cleaned, url)
                    
                    // Save to history
                    val entry = HistoryManager.HistoryEntry(
                        originalUrl = url,
                        cleanedUrl = cleaned,
                        processedUrl = redirectUrl,
                        timestamp = System.currentTimeMillis()
                    )
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        HistoryManager.addToHistory(context, entry)
                    }

                    // Open
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, redirectUrl.toUri()).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        
                        // Check for loops (copy of logic from RedirectActivity/MainActivity)
                        val handlers = context.packageManager.queryIntentActivities(intent, 0)
                        val otherHandler = handlers.firstOrNull { it.activityInfo.packageName != context.packageName }
                        
                        if (otherHandler != null) {
                            intent.setPackage(otherHandler.activityInfo.packageName)
                            context.startActivity(intent)
                        } else {
                            // Fallback to WebView or MainActivity
                            val webIntent = Intent(context, WebViewActivity::class.java).apply {
                                putExtra(WebViewActivity.EXTRA_URL, redirectUrl)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(webIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open URL: $redirectUrl", e)
                        android.widget.Toast.makeText(context, "Could not open URL", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    bottomSheetDialog.dismiss()
                }
            }
            
            spanned.removeSpan(urlSpan)
            spanned.setSpan(clickableSpan, start, end, spanFlags)
        }

        creditsText.text = spanned
        creditsText.movementMethod = LinkMovementMethod.getInstance()

        view.findViewById<TextView>(R.id.btnAboutGithub).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/blankdotdev".toUri())
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Could not open GitHub", android.widget.Toast.LENGTH_SHORT).show()
            }
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private const val TAG = "SettingsUtils"
    private const val HEALTH_HEALTHY = "Healthy"
    private const val HEALTH_ISSUES = "Issues"
    private const val HEALTH_DOWN = "Down"
    private const val HEALTH_UNHEALTHY = "unhealthy"
    private const val HEALTH_DEAD = "Dead"
    private const val HEALTH_EMOJI = "●"
    private const val UPTIME_THRESHOLD_HEALTHY = 98f
    private const val UPTIME_THRESHOLD_ISSUES = 85f
}
