package com.blankdev.sidestep


import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.regex.Pattern
import android.util.Log
import java.net.URISyntaxException

/**
 * Utility for cleaning URLs by removing tracking parameters and normalizing formats.
 * Refactored to be JVM-compatible for easier unit testing.
 */
object UrlCleaner {
    
    /**
     * Ensures the URL has a protocol (defaults to https)
     */
    fun ensureProtocol(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }

    /**
     * Checks if a URL has an allowed scheme (http or https).
     * This prevents Intent Redirection attacks using file://, javascript:, etc.
     */
    fun isValidAppUrl(url: String?): Boolean {
        if (url == null) return false
        val lowerUrl = url.lowercase(Locale.getDefault())
        return lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://")
    }
    
    // Comprehensive tracking parameters covering all major platforms
    // Updated to be thorough and eliminate need for individual reports
    private val TRACKING_PARAMS = setOf(
        // ===== UTM Parameters (Universal) =====
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "utm_source_platform", "utm_creative_format", "utm_marketing_tactic",
        
        // ===== Platform Click IDs =====
        "fbclid", "fbc", "fbp",  // Facebook/Meta
        "gclid", "gclsrc", "gad_source", "gad_campaignid",  // Google Ads
        "msclkid",  // Microsoft/Bing Ads
        "gbraid", "wbraid",  // Google enhanced conversions
        "dclid",  // DoubleClick
        "yclid",  // Yandex
        "ttclid",  // TikTok
        "twclid",  // Twitter/X
        "li_fat_id",  // LinkedIn
        "mc_cid", "mc_eid",  // Mailchimp
        "soc_src", "soc_trk",  // Social tracking
        
        // ===== Amazon Tracking =====
        "tag", "linkCode", "linkId", "creativeASIN", "creative",
        "ascsubtag", "asc_campaign", "asc_refurl", "asc_source",
        "ref", "ref_", "refRID", "ref_src", "ref_url",
        "pf_rd_i", "pf_rd_m", "pf_rd_p", "pf_rd_r", "pf_rd_s", "pf_rd_t",
        "pd_rd_i", "pd_rd_r", "pd_rd_w", "pd_rd_wg",
        "qid", "sr", "keywords", "dib", "dib_tag", "sp_csd", "psc",
        "th", "_encoding", "smid",
        "social_share", "cm_sw_r_cso_cp_apan_dp",
        
        // ===== TikTok Tracking =====
        "_r", "_t", "checksum", "share_app_id", "share_author_id", "share_link_id",
        "social_sharing", "sec_user_id", "u_code", "timestamp",
        "browser_name", "browser_version", "browser_online", "browser_platform", "browser_language",
        "tt_medium", "tt_content",
        
        // ===== Twitter/X Tracking =====
        "s", "t", "twsrc", "twgr", "tweetid",
        
        // ===== Facebook/Instagram/Meta Tracking =====
        "igshid", "igsh", "ig_rid", "ig_web_copy_link",
        "vanity", "mibextid", "rdid", "share_url",
        "action_object_map", "action_ref_map", "action_type_map",
        
        // ===== LinkedIn Tracking =====
        "trk", "trkCampaign", "trkEmail", "trkInfo",
        "lipi", "licu", "midToken", "midSig",
        
        // ===== Reddit Tracking =====
        "rdt", "ref_source", "app_name", "user_id", "rb_clickid",
        "share_id",
        
        // ===== YouTube Tracking =====
        "si", "pp", "feature", "kw", "app",
        
        // ===== Pinterest Tracking =====
        "epik", "pinterest_share",
        
        // ===== Snapchat Tracking =====
        "ScCid", "sc_referrer", "sc_llid", "sc_uid", "sc_lid", "sc_eh",
        
        // ===== Email Marketing =====
        "vero_id", "vero_conv",  // Vero
        "oly_anon_id", "oly_enc_id",  // Olytics
        "dm_i", "dm_t",  // Dotdigital
        "mkt_tok",  // Marketo
        "_hsenc", "_hsmi",  // HubSpot
        "bsft_clkid", "bsft_eid", "bsft_aaid", "bsft_uid",  // Blueshift
        
        // ===== Analytics & Attribution =====
        "_ga", "_gl", "_gac", "_gid",  // Google Analytics
        "pk_campaign", "pk_kwd", "pk_source", "pk_medium", "pk_content",  // Piwik/Matomo
        "at_custom1", "at_custom2", "at_custom3", "at_custom4",  // Adobe Target
        "s_kwcid",  // Adobe Analytics
        "wickedid", "wickedsource",  // Wicked Reports
        
        // ===== Affiliate & E-commerce =====
        "affiliate", "aff_id", "affid", "aff", "affiliate_id",
        "click_id", "clickid", "transaction_id",
        "link_alias", "variantId", "variant",
        "offer_id", "offer", "sub_id", "sub1", "sub2", "sub3",
        
        // ===== General Tracking =====
        "source", "medium", "campaign", "content", "term",
        "referrer", "referral", "referer",
        "cid", "cmpid", "campaign_id", "ad_id", "adset_id",
        "glid", "ef_id",
        "srsltid",  // Google Shopping
        
        // ===== Apple Tracking =====
        "itscg", "itsct", "ct", "mt", "pt",
        
        // ===== Yahoo/Verizon =====
        "guce_referrer", "guce_referrer_sig", "_guc_consent_skip",
        
        // ===== Shopify =====
        "selling_plan",
        
        // ===== Misc Platform-Specific =====
        "cmplz_region_redirect",  // Complianz
        "age-verified",  // Age verification
        "redirect_log_mongoid", "redirect_mongo_id",  // Redirect tracking
        "nb_sb_ss_i", "nb_sb_ss_c",  // Navigation breadcrumbs
        
        // ===== Mobile App Tracking =====
        "deep_link_value", "deep_link_sub1",
        "af_dp", "af_force_deeplink", "af_sub1", "af_sub2",  // AppsFlyer
        "adjust_campaign", "adjust_adgroup",  // Adjust
        
        // ===== HubSpot Extended =====
        "hsa_acc", "hsa_ad", "hsa_cam", "hsa_grp", "hsa_kw",
        "hsa_mt", "hsa_net", "hsa_src", "hsa_tgt", "hsa_ver",
        
        // ===== Misc Tracking =====
        "_ke", "_kx", "_sm_byp", "_sp", "_szp", "__s",
        "ncid", "spm", "scm", "algo_expid", "algo_pvid",
        "bxid", "chn", "sendId", "oid",
        "ns_campaign", "ns_mchannel", "ns_source", "ns_linkname", "ns_fee"
    )
    
    /**
     * Clean a URL by removing tracking parameters while preserving essential ones
     */
    fun cleanUrl(url: String): String {
        // Handle nested URLs (e.g., Google redirect links)
        val nestedUrl = extractNestedUrl(url)
        if (nestedUrl != null && nestedUrl != url) {
            return cleanUrl(nestedUrl)
        }

        // First normalize if it's a YouTube or Facebook URL to ensure standard format
        var inputUrl = if (isYouTubeUrl(url)) normalizeYouTubeUrl(url) else url
        inputUrl = if (isFacebookUrl(inputUrl)) normalizeFacebookUrl(inputUrl) else inputUrl
        
        // Clean path segments first
        inputUrl = cleanPathSegments(inputUrl)
        
        return try {
            val uri = URI(inputUrl)
            val query = uri.query
            
            if (query == null) {
                inputUrl
            } else {
                val newQuery = filterQueryParams(query, inputUrl)
                URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment).toString()
            }
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to parse URI: $inputUrl", e)
            inputUrl
        }
    }

    private fun filterQueryParams(query: String, url: String): String? {
        val params = query.split("&")
        val preservedParams = params.filter { param ->
            val parts = param.split("=", limit = 2)
            val name = parts[0]
            val isTracking = isTrackingParam(name)
            
            // UrbanDictionary requires the 'term' parameter to function
            val isPseudoTracking = isTracking && isUrbanDictionaryUrl(url) && name == "term"
            
            !isTracking || isPseudoTracking
        }
        return if (preservedParams.isEmpty()) null else preservedParams.joinToString("&")
    }

    private fun isTrackingParam(name: String): Boolean {
         if (TRACKING_PARAMS.contains(name)) return true
         
         val prefixes = listOf(
            // Amazon
            "ref_", "pf_rd_", "pd_rd_", "cm_sw_", "asc_",
            // UTM
            "utm_",
            // Analytics
            "_ga", "_gl", "gaa_", "pk_", "matomo_", "at_", "sc_",
            // Email marketing
            "oly_", "vero_", "_hs", "hsa_", "mkt_", "bsft_", "dm_",
            // Social media
            "tw", "fb", "ig_", "li_", "trk",
            // Affiliate
            "aff_", "sub", "af_",
            // Misc
            "wicked", "ns_"
         )
         return prefixes.any { name.startsWith(it) }
    }

    fun normalizeYouTubeUrl(url: String): String {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase()

            when {
                host?.contains("youtu.be") == true -> {
                    val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                    segments.firstOrNull()?.let { videoId ->
                        val newQuery = if (uri.query.isNullOrEmpty()) "v=$videoId" else "v=$videoId&${uri.query}"
                        URI("https", "www.youtube.com", "/watch", newQuery, uri.fragment).toString()
                    } ?: url
                }
                host?.contains("youtube.com") == true && uri.path?.contains("/shorts/") == true -> {
                    val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                    segments.lastOrNull()?.let { videoId ->
                        val newQuery = if (uri.query.isNullOrEmpty()) "v=$videoId" else "v=$videoId&${uri.query}"
                        URI("https", "www.youtube.com", "/watch", newQuery, uri.fragment).toString()
                    } ?: url
                }
                else -> url
            }
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to normalize YouTube URL: $url", e)
            url
        }
    }

    /**
     * Convert facebook.com/.../videos/ID or facebook.com/reel/ID to facebook.com/watch?v=ID
     */
    fun normalizeFacebookUrl(url: String): String {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase()
            
            if (host?.contains("facebook.com") == true) {
                val path = uri.path ?: ""
                
                // Try video pattern first, then reel pattern
                val videoPattern = """/videos/(?:[^/]+/)?(\d+)/?""".toRegex()
                val reelPattern = """/reel/(\d+)/?""".toRegex()
                
                val videoId = videoPattern.find(path)?.groupValues?.get(1)
                    ?: reelPattern.find(path)?.groupValues?.get(1)
                
                videoId?.let { id ->
                    val newQuery = if (uri.query.isNullOrEmpty()) "v=$id" else "v=$id&${uri.query}"
                    URI("https", "www.facebook.com", "/watch", newQuery, uri.fragment).toString()
                } ?: url
            } else {
                url
            }
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to normalize Facebook URL: $url", e)
            url
        }
    }
    
    /**
     * Replace the domain in a URL with an alternative domain
     */
    fun replaceDomain(url: String, newDomain: String): String {
        var domainToUse = newDomain
        
        // If it's a YouTube URL and target is a Piped API host, convert to frontend domain for browser
        if (isYouTubeUrl(url)) {
            if (domainToUse.startsWith("pipedapi.")) {
                domainToUse = "piped." + domainToUse.removePrefix("pipedapi.")
            } else if (domainToUse.startsWith("api.piped.")) {
                domainToUse = domainToUse.removePrefix("api.") // e.g. api.piped.private.coffee -> piped.private.coffee
            }
        }
        
        try {
            val uri = URI(url)
            return URI(uri.scheme ?: "https", domainToUse, uri.path, uri.query, uri.fragment).toString()
        } catch (e: URISyntaxException) {
            // Fallback for malformed URLs
            Log.e(TAG, "Failed to replace domain in URL: $url", e)
            return url
        }
    }
    
    /**
     * Check if a URL is a Twitter or X URL
     */
    fun isTwitterOrXUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("twitter.com") || host.contains("x.com") || 
               host.contains("vxtwitter.com") || host.contains("fxtwitter.com")
    }

    /**
     * Check if a URL is a TikTok URL
     */
    fun isTikTokUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("tiktok.com")
    }

    /**
     * Check if a URL is a Reddit URL
     */
    fun isRedditUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("reddit.com") || host.contains("redd.it")
    }

    /**
     * Check if a URL is a YouTube URL
     */
    fun isYouTubeUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("youtube.com") || host.contains("youtu.be")
    }

    /**
     * Check if a URL is an IMDb URL
     */
    fun isImdbUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("imdb.com")
    }

    /**
     * Check if a URL is a Medium URL
     */
    fun isMediumUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("medium.com") || host.contains("substituteteacher.ca")
    }

    /**
     * Check if a URL is a Wikipedia URL
     */
    fun isWikipediaUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("wikipedia.org")
    }

    /**
     * Check if a URL is a Goodreads URL
     */
    fun isGoodreadsUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("goodreads.com")
    }

    /**
     * Check if a URL is a Genius URL
     */
    fun isGeniusUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("genius.com")
    }

    /**
     * Check if a URL is a GitHub URL
     */
    fun isGitHubUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("github.com")
    }

    /**
     * Check if a URL is a StackOverflow URL
     */
    fun isStackOverflowUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("stackoverflow.com") || host.contains("stackexchange.com")
    }

    /**
     * Check if a URL is a Google Maps URL
     */
    fun isGoogleMapsUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("maps.google.") || 
               host.contains("google.") && url.contains("/maps") ||
               host.contains("goo.gl") && url.contains("/maps")
    }

    /**
     * Check if a URL is a Tumblr URL
     */
    fun isTumblrUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("tumblr.com")
    }

    /**
     * Check if a URL is an UrbanDictionary URL
     */
    fun isUrbanDictionaryUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("urbandictionary.com")
    }

    /**
     * Check if a URL is a Facebook URL
     */
    fun isFacebookUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("facebook.com")
    }

     /**
      * Check if a URL is an Imgur URL
      */
    fun isImgurUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("imgur.com")
    }
    /**
     * Convert Google Maps URL to OpenStreetMap URL
     */
    fun convertGoogleMapsToOsm(url: String): String {
        return try {
            val uri = URI(url)
            val path = uri.path ?: ""
            val query = uri.query

            val result = matchOpenStreetMapRedirect(url, path, query)
            result ?: "https://www.openstreetmap.org"
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to convert Google Maps URL to OSM: $url", e)
            "https://www.openstreetmap.org"
        }
    }

    /**
     * Tries to find a corresponding OpenStreetMap URL pattern from a Google Maps URL
     */
    private fun matchOpenStreetMapRedirect(url: String, path: String, query: String?): String? {
        // 1. Coordinates via @lat,long,zoom
        val atMatch = "@([^,]+),([^,]+),([0-9.]+)z?".toRegex().find(url)
        if (atMatch != null) {
            val (lat, long, zoom) = atMatch.destructured
            val cleanZoom = zoom.replace("z", "")
            return "https://www.openstreetmap.org/?mlat=$lat&mlon=$long#map=$cleanZoom/$lat/$long"
        }

        // 2. Direct directions /dir/from/to
        if (path.contains("/dir/")) {
            val parts = path.substringAfter("/dir/").split("/").takeIf { it.size >= 2 }
            if (parts != null) {
                val from = URLDecoder.decode(parts[0], "UTF-8")
                val to = URLDecoder.decode(parts[1], "UTF-8")
                return "https://www.openstreetmap.org/directions?from=${URLEncoder.encode(from, "UTF-8")}&to=${URLEncoder.encode(to, "UTF-8")}"
            }
        }

        // 3. Place or Search in path
        val searchFor = when {
            path.contains("/place/") -> path.substringAfter("/place/").substringBefore("/")
            path.contains("/search/") -> path.substringAfter("/search/").substringBefore("/")
            else -> null
        }
        if (!searchFor.isNullOrEmpty()) {
             val decoded = URLDecoder.decode(searchFor, "UTF-8")
             return "https://www.openstreetmap.org/search?query=${URLEncoder.encode(decoded, "UTF-8")}"
        }

        // 4. Query params (ll or q)
        if (query != null) {
            // Lat/Long param
            if (query.contains("ll=")) {
                val coords = "ll=([^&]+)".toRegex().find(query)?.groupValues?.get(1)?.split(",")
                if (coords != null && coords.size >= 2) {
                     val lat = coords[0]
                     val long = coords[1]
                     return "https://www.openstreetmap.org/?mlat=$lat&mlon=$long#map=15/$lat/$long"
                }
            }
            
            // Search param
            val qParam = "(?:^|&)(?:q|query|search_query)=([^&]+)".toRegex().find(query)?.groupValues?.get(1)
            if (qParam != null) {
                val decoded = URLDecoder.decode(qParam, "UTF-8")
                return "https://www.openstreetmap.org/search?query=${URLEncoder.encode(decoded, "UTF-8")}"
            }
        }
        
        return null
    }



    /**
     * Extracts a nested URL from query parameters if present (e.g., Google redirect links)
     */
    private fun extractNestedUrl(url: String): String? {
        return try {
            val uri = URI(url)
            val query = uri.query ?: return null
            
            // Common parameters used for redirection
            val redirectParams = setOf(
                "url", "u", "link", "target", "dest", "destination", 
                "redir", "redirect_url", "adurl", "q"
            )
            
            query.split("&")
                .firstNotNullOfOrNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size != 2 || !redirectParams.contains(parts[0].lowercase())) {
                        return@firstNotNullOfOrNull null
                    }
                    
                    val decodedValue = try {
                        URLDecoder.decode(parts[1], "UTF-8")
                    } catch (e: java.io.UnsupportedEncodingException) {
                        Log.w(TAG, "UTF-8 encoding not supported (should never happen), using null", e)
                        null
                    }
                    
                    decodedValue?.takeIf { 
                        (it.startsWith("http://") || it.startsWith("https://")) && isValidNestedUrl(it)
                    }
                }
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to extract nested URL: $url", e)
            null
        }
    }
    
    private fun isValidNestedUrl(url: String): Boolean {
        return try {
            URI(url)
            true
        } catch (e: URISyntaxException) {
            Log.d(TAG, "Invalid URI in validation: $url", e)
            false
        }
    }


    private fun getHost(url: String): String {
        return try {
            URI(url).host?.lowercase() ?: ""
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to get host from URL: $url", e)
            ""
        }
    }


    /**
     * Calculates the TLD segment count for domain parsing (e.g., .com = 1, .co.uk = 2)
     */
    private fun calculateTldCount(domainParts: List<String>): Int {
        if (domainParts.size < TLD_PARSING_MIN_PARTS) return 1
        if (domainParts.last().length > TLD_MAX_LENGTH) return 1
        
        val secondLast = domainParts[domainParts.size - 2]
        val isDoubleTld = secondLast in listOf("co", "com", "net", "org") || secondLast.length <= 2
        return if (isDoubleTld) 2 else 1
    }

    /**
     * Get the service name from a URL for display Title
     */
    fun getServiceName(url: String?): String {
        if (url == null) return "Link"
        
        val host = getHost(url)
        if (host.isEmpty()) return "Link"
        
        return getKnownPlatformName(host, url) ?: getGenericServiceName(host)
    }

    private data class PlatformRule(val name: String, val identifiers: List<String>)

    private val PLATFORM_RULES = listOf(
        PlatformRule("YouTube", listOf("youtube.com", "youtu.be")),
        PlatformRule("Twitter", listOf("twitter.com", "x.com", "vxtwitter.com", "fxtwitter.com")),
        PlatformRule("TikTok", listOf("tiktok.com")),
        PlatformRule("Reddit", listOf("reddit.com", "redd.it")),
        PlatformRule("IMDb", listOf("imdb.com")),
        PlatformRule("Medium", listOf("medium.com")),
        PlatformRule("Wikipedia", listOf("wikipedia.org")),
        PlatformRule("Goodreads", listOf("goodreads.com")),
        PlatformRule("Genius", listOf("genius.com")),
        PlatformRule("GitHub", listOf("github.com")),
        PlatformRule("StackOverflow", listOf("stackoverflow.com", "stackexchange.com")),
        PlatformRule("Instagram", listOf("instagram.com")),
        PlatformRule("Facebook", listOf("facebook.com")),
        PlatformRule("Amazon", listOf("amazon")),
        PlatformRule("Apple News", listOf("apple.news", "news.apple.com")),
        PlatformRule("Bitly", listOf("bitly.com", "bit.ly")),
        PlatformRule("TinyURL", listOf("tinyurl.com")),
        PlatformRule("Tiny.cc", listOf("tiny.cc")),
        PlatformRule("Shorten.ly", listOf("shorten.ly")),
        PlatformRule("ShortURL", listOf("shorturl.fm")),
        PlatformRule("Short.io", listOf("short.io")),
        PlatformRule("BL.INK", listOf("bl.ink")),
        PlatformRule("T.ly", listOf("t.ly")),
        PlatformRule("Sniply", listOf("snip.ly")),
        PlatformRule("Rebrandly", listOf("rebrandly.com")),
        PlatformRule("Tumblr", listOf("tumblr.com")),
        PlatformRule("UrbanDictionary", listOf("urbandictionary.com")),
        PlatformRule("Imgur", listOf("imgur.com")),
        PlatformRule("Spotify", listOf("spotify.com")),
        PlatformRule("Apple Podcasts", listOf("podcasts.apple.com"))
    )

    private fun getKnownPlatformName(host: String, url: String): String? {
        // Special case for Google Maps which needs URL check
        if (host.contains("maps.google.") || (host.contains("google.") && url.contains("/maps"))) {
            return "Google Maps"
        }

        return PLATFORM_RULES.firstOrNull { rule ->
            rule.identifiers.any { host.contains(it) }
        }?.name
    }

    private fun getGenericServiceName(host: String): String {
        // Smart Generic Fallback
        val cleanHost = stripWwwPrefix(host)
        val parts = cleanHost.split(".")
        
        if (parts.size < 2) {
             return cleanHost.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }

        // Determine TLD length (e.g., .com vs .co.uk)
        val tldCount = calculateTldCount(parts)
        val nameParts = parts.dropLast(tldCount)
        
        return if (nameParts.isEmpty()) {
            "Link"
        } else {
            val mainNameRaw = nameParts.last().lowercase(Locale.getDefault())
            
            // Heuristics
            tryFormatMediaName(mainNameRaw) 
                ?: if (nameParts.size >= 2) {
                    formatSubdomainSwap(nameParts)
                } else {
                    formatFinalName(mainNameRaw)
                }
        }
    }

    private fun stripWwwPrefix(host: String): String {
        var cleanHost = host.lowercase(Locale.getDefault())
        val prefixes = listOf("www.", "m.", "mobile.")
        for (prefix in prefixes) {
            if (cleanHost.startsWith(prefix)) {
                cleanHost = cleanHost.substring(prefix.length)
            }
        }
        return cleanHost
    }

    private fun tryFormatMediaName(mainName: String): String? {
        val mediaSuffixes = listOf(
            "post", "times", "today", "journal", "american", "mechanics", 
            "review", "digest", "gazette", "herald", "tribune", "observer",
            "insider", "weekly", "daily", "monthly", "report", "news", "yorker",
            "atlantic", "guardian", "independent", "standard", "express"
        )
        
        for (suffix in mediaSuffixes) {
            if (mainName.endsWith(suffix) && mainName != suffix) {
                val prefixPart = mainName.substring(0, mainName.length - suffix.length)
                val formattedPrefix = formatAcronymOrName(prefixPart)
                val formattedSuffix = suffix.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                return "$formattedPrefix $formattedSuffix"
            }
        }
        return null
    }

    private fun formatSubdomainSwap(nameParts: List<String>): String {
        return nameParts.reversed().joinToString(" ") { part ->
             formatAcronymOrName(part)
        }.trim()
    }

    private fun formatFinalName(mainName: String): String {
        val finalParts = mainName.split(" ")
        return finalParts.joinToString(" ") { part ->
            formatAcronymOrName(part)
        }.trim()
    }

    private fun formatAcronymOrName(part: String): String {
        val lower = part.lowercase(Locale.getDefault())
        return when {
            lower == "usa" -> "USA"
            lower == "ny" -> "NY"
            lower == "wsj" -> "WSJ"
            lower == "bbc" -> "BBC"
            lower == "npr" -> "NPR"
            lower == "mit" -> "MIT"
            lower.startsWith("the") && lower.length > MIN_THE_DOMAIN_LENGTH -> 
                "The " + lower.substring(THE_PREFIX_LENGTH).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            else -> part.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
    }

    /**
     * Get a cleaned URL for display Subtitle (stripped of protocol, www, params)
     */
    fun getCleanedDisplayUrl(url: String?): String {
        if (url == null) return ""
        
        val cleanedFull = cleanUrl(url)
        
        return try {
            val uri = URI(cleanedFull)
            val host = uri.host
            val path = uri.path ?: ""
            
            host?.let { h ->
                val prefixes = listOf("www.", "m.", "mobile.")
                val cleanHost = prefixes.fold(h) { acc, prefix ->
                    if (acc.startsWith(prefix)) acc.substring(prefix.length) else acc
                }
                
                val display = "$cleanHost$path"
                if (display.endsWith("/")) display.substring(0, display.length - 1) else display
            } ?: cleanedFull
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to get cleaned display URL: $url", e)
            cleanedFull
        }
    }

    /**
     * Get only the path and query part of the URL (e.g. /r/Subreddit/...)
     */
    fun getCleanedPath(url: String?): String {
        if (url == null) return ""
        
        val cleanedFull = cleanUrl(url)
        
        try {
            val uri = URI(cleanedFull)
            val path = uri.path ?: ""
            val query = if (uri.query != null) "?${uri.query}" else ""
            
            val fullPath = "$path$query"
            
            return if (fullPath.isNotEmpty()) fullPath else cleanedFull
            
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to get cleaned path: $url", e)
            return cleanedFull
        }
    }

    /**
     * Clean path segments by removing those that look like tracking parameters.
     * e.g. /dp/B0D1KQKZM2/ref=sr_1_1 -> /dp/B0D1KQKZM2
     */
    private fun cleanPathSegments(url: String): String {
        try {
            val uri = URI(url)
            val path = uri.path ?: return url
            
            // Should loop through segments and filter out tracking ones
            // But standard URI parsing can be tricky with encoded slashes
            // Let's use a simpler string manipulation approach safely
            
            val segments = path.split("/")
            val cleanSegments = segments.filter { segment ->
                val lower = segment.lowercase()
                !lower.startsWith("ref=") && 
                !lower.startsWith("ref_") &&
                !lower.startsWith("source=") &&
                !lower.startsWith("src=") &&
                !lower.startsWith("pf_rd_") &&
                !lower.startsWith("pd_rd_") &&
                !lower.startsWith("cm_sw_")
            }
            
            if (cleanSegments.size == segments.size) {
                return url
            }
            
            val newPath = cleanSegments.joinToString("/")
            return URI(uri.scheme, uri.authority, newPath, uri.query, uri.fragment).toString()
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to clean path segments: $url", e)
            return url
        }
    }

    private const val MAPS_ZOOM_INDEX = 3
    private const val TLD_PARSING_MIN_PARTS = 3
    private const val TLD_MAX_LENGTH = 3
    private const val THE_PREFIX_LENGTH = 3
    private const val MIN_THE_DOMAIN_LENGTH = 5

    private const val TAG = "UrlCleaner"
}
