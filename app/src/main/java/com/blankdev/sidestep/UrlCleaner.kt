package com.blankdev.sidestep


import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.regex.Pattern

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
        
        try {
            val uri = URI(inputUrl)
            val query = uri.query ?: return inputUrl
            
            val params = query.split("&")
            val preservedParams = mutableListOf<String>()
            
            for (param in params) {
                val parts = param.split("=", limit = 2)
                val name = parts[0]
                
                val isTracking = TRACKING_PARAMS.contains(name) || 
                               // Amazon
                               name.startsWith("ref_") || 
                               name.startsWith("pf_rd_") ||
                               name.startsWith("pd_rd_") ||
                               name.startsWith("cm_sw_") ||
                               name.startsWith("asc_") ||
                               // UTM variants
                               name.startsWith("utm_") ||
                               // Analytics
                               name.startsWith("_ga") ||
                               name.startsWith("_gl") ||
                               name.startsWith("gaa_") ||
                               name.startsWith("pk_") || // Piwik/Matomo
                               name.startsWith("matomo_") ||
                               name.startsWith("at_") || // Adobe Target
                               name.startsWith("sc_") || // Snapchat/Emarsys
                               // Email marketing
                               name.startsWith("oly_") || // Olytics
                               name.startsWith("vero_") || // Vero
                               name.startsWith("_hs") || // HubSpot
                               name.startsWith("hsa_") || // HubSpot Ads
                               name.startsWith("mkt_") || // Marketo
                               name.startsWith("bsft_") || // Blueshift
                               name.startsWith("dm_") || // Dotdigital
                               // Social media
                               name.startsWith("tw") || // Twitter
                               name.startsWith("fb") || // Facebook
                               name.startsWith("ig_") || // Instagram
                               name.startsWith("li_") || // LinkedIn
                               name.startsWith("trk") || // LinkedIn tracking
                               // Affiliate
                               name.startsWith("aff_") ||
                               name.startsWith("sub") || // sub1, sub2, etc.
                               name.startsWith("af_") || // AppsFlyer
                               // Misc
                               name.startsWith("wicked") || // Wicked Reports
                               name.startsWith("ns_") // Nielsen/Navigation
                
                // UrbanDictionary requires the 'term' parameter to function
                val isPseudoTracking = isTracking && isUrbanDictionaryUrl(inputUrl) && name == "term"
                
                if (!isTracking || isPseudoTracking) {
                    preservedParams.add(param)
                }
            }
            
            val newQuery = if (preservedParams.isEmpty()) null else preservedParams.joinToString("&")
            
            return URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment).toString()
        } catch (e: Exception) {
            return inputUrl
        }
    }

    /**
     * Convert youtu.be/ID or youtube.com/shorts/ID to youtube.com/watch?v=ID
     */
    fun normalizeYouTubeUrl(url: String): String {
        try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return url
            
            if (host.contains("youtu.be")) {
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val videoId = segments.firstOrNull() ?: return url
                
                // Construct new URL with v=ID
                val newQuery = if (uri.query.isNullOrEmpty()) "v=$videoId" else "v=$videoId&${uri.query}"
                return URI("https", "www.youtube.com", "/watch", newQuery, uri.fragment).toString()
            }
            
            if (host.contains("youtube.com") && uri.path?.contains("/shorts/") == true) {
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val videoId = segments.lastOrNull() ?: return url
                
                val newQuery = if (uri.query.isNullOrEmpty()) "v=$videoId" else "v=$videoId&${uri.query}"
                return URI(uri.scheme, uri.authority, "/watch", newQuery, uri.fragment).toString()
            }
        } catch (e: Exception) {}
        
        return url
    }

    /**
     * Convert facebook.com/.../videos/ID or facebook.com/reel/ID to facebook.com/watch?v=ID
     */
    fun normalizeFacebookUrl(url: String): String {
        try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return url
            
            if (host.contains("facebook.com")) {
                val path = uri.path ?: ""
                
                // Pattern 1: /username/videos/ID/ or /username/videos/some-title/ID/
                val videoPattern = """/videos/(?:[^/]+/)?(\d+)/?""".toRegex()
                val videoMatch = videoPattern.find(path)
                if (videoMatch != null) {
                    val videoId = videoMatch.groupValues[1]
                    val newQuery = if (uri.query.isNullOrEmpty()) "v=$videoId" else "v=$videoId&${uri.query}"
                    return URI("https", "www.facebook.com", "/watch", newQuery, uri.fragment).toString()
                }
                
                // Pattern 2: /reel/ID/
                val reelPattern = """/reel/(\d+)/?""".toRegex()
                val reelMatch = reelPattern.find(path)
                if (reelMatch != null) {
                    val videoId = reelMatch.groupValues[1]
                    val newQuery = if (uri.query.isNullOrEmpty()) "v=$videoId" else "v=$videoId&${uri.query}"
                    return URI("https", "www.facebook.com", "/watch", newQuery, uri.fragment).toString()
                }
            }
        } catch (e: Exception) {}
        
        return url
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
        } catch (e: Exception) {
            // Fallback for malformed URLs
            return url
        }
    }
    
    /**
     * Check if a URL is a Twitter or X URL
     */
    fun isTwitterOrXUrl(url: String?): Boolean {
        if (url == null) return false
        val host = getHost(url)
        return host.contains("twitter.com") || host.contains("x.com")
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
        try {
            val uri = URI(url)
            
            // Pattern 1: @lat,long,zoom format (e.g., @37.7749,-122.4194,15z)
            val atPattern = "@([^,]+),([^,]+),([0-9.]+)z?".toRegex()
            val atMatch = atPattern.find(url)
            if (atMatch != null) {
                val lat = atMatch.groupValues[1]
                val long = atMatch.groupValues[2]
                val zoom = atMatch.groupValues[3].replace("z", "")
                return "https://www.openstreetmap.org/?mlat=$lat&mlon=$long#map=$zoom/$lat/$long"
            }
            
            // Pattern 2: /place/ format (e.g., /place/San+Francisco)
            val path = uri.path ?: ""
            if (path.contains("/place/")) {
                val placeName = path.substringAfter("/place/").substringBefore("/")
                val decoded = URLDecoder.decode(placeName, "UTF-8")
                return "https://www.openstreetmap.org/search?query=${URLEncoder.encode(decoded, "UTF-8")}"
            }
            
            // Pattern 3: /dir/ format (e.g., /dir/start/end)
            if (path.contains("/dir/")) {
                val parts = path.substringAfter("/dir/").split("/")
                if (parts.size >= 2) {
                    val from = URLDecoder.decode(parts[0], "UTF-8")
                    val to = URLDecoder.decode(parts[1], "UTF-8")
                    return "https://www.openstreetmap.org/directions?from=${URLEncoder.encode(from, "UTF-8")}&to=${URLEncoder.encode(to, "UTF-8")}"
                }
            }

            // Pattern 4: /search/ format (e.g., /maps/search/San+Francisco)
            if (path.contains("/search/")) {
                val query = path.substringAfter("/search/").substringBefore("/")
                if (query.isNotEmpty()) {
                    val decoded = URLDecoder.decode(query, "UTF-8")
                    return "https://www.openstreetmap.org/search?query=${URLEncoder.encode(decoded, "UTF-8")}"
                }
            }
            
            // Pattern 4: Query parameter 'q' or 'query' (e.g., ?q=San+Francisco or /search?query=San+Francisco)
            val qParam = uri.query?.let { q ->
                val qPattern = "(?:^|&)(?:q|query|search_query)=([^&]+)".toRegex()
                qPattern.find(q)?.groupValues?.get(1)
            }
            if (qParam != null) {
                val decoded = URLDecoder.decode(qParam, "UTF-8")
                return "https://www.openstreetmap.org/search?query=${URLEncoder.encode(decoded, "UTF-8")}"
            }

            // Pattern 5: Query parameter 'll' (lat,long)
            val query = uri.query
            if (query != null && query.contains("ll=")) {
                val llPattern = "ll=([^&]+)".toRegex()
                val llMatch = llPattern.find(query)
                if (llMatch != null) {
                    val coords = llMatch.groupValues[1].split(",")
                    if (coords.size >= 2) {
                        val lat = coords[0]
                        val long = coords[1]
                        return "https://www.openstreetmap.org/?mlat=$lat&mlon=$long#map=15/$lat/$long"
                    }
                }
            }
            
        } catch (e: Exception) {}
        
        // Fallback: just open OpenStreetMap homepage
        return "https://www.openstreetmap.org"
    }

    /**
     * Extracts a nested URL from query parameters if present (e.g., Google redirect links)
     */
    private fun extractNestedUrl(url: String): String? {
        try {
            val uri = URI(url)
            val query = uri.query ?: return null
            
            // Common parameters used for redirection
            val redirectParams = setOf(
                "url", "u", "link", "target", "dest", "destination", 
                "redir", "redirect_url", "adurl", "q"
            )
            
            val params = query.split("&")
            for (param in params) {
                val parts = param.split("=", limit = 2)
                if (parts.size == 2 && redirectParams.contains(parts[0].lowercase())) {
                    val decodedValue = try {
                        URLDecoder.decode(parts[1], "UTF-8")
                    } catch (e: Exception) {
                        null
                    }
                    
                    if (decodedValue != null && (decodedValue.startsWith("http://") || decodedValue.startsWith("https://"))) {
                        // Validate it's a valid URI
                        return try {
                            URI(decodedValue)
                            decodedValue
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun getHost(url: String): String {
        return try {
            URI(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Get the service name from a URL for display Title
     */
    fun getServiceName(url: String?): String {
        if (url == null) return "Link"
        
        val host = getHost(url)
        if (host.isEmpty()) return "Link"
        
        return when {
            host.contains("youtube.com") || host.contains("youtu.be") -> "YouTube"
            host.contains("twitter.com") || host.contains("x.com") -> "Twitter"
            host.contains("tiktok.com") -> "TikTok"
            host.contains("reddit.com") || host.contains("redd.it") -> "Reddit"
            host.contains("imdb.com") -> "IMDb"
            host.contains("medium.com") -> "Medium"
            host.contains("wikipedia.org") -> "Wikipedia"
            host.contains("goodreads.com") -> "Goodreads"
            host.contains("genius.com") -> "Genius"
            host.contains("github.com") -> "GitHub"
            host.contains("stackoverflow.com") || host.contains("stackexchange.com") -> "StackOverflow"
            host.contains("instagram.com") -> "Instagram"
            host.contains("facebook.com") -> "Facebook"
            host.contains("amazon") -> "Amazon"
            host.contains("nytimes.com") -> "NYTimes"
            host.contains("apple.news") || host.contains("news.apple.com") -> "Apple News"
            host.contains("bitly.com") || host.contains("bit.ly") -> "Bitly"
            host.contains("tinyurl.com") -> "TinyURL"
            host.contains("tiny.cc") -> "Tiny.cc"
            host.contains("shorten.ly") -> "Shorten.ly"
            host.contains("shorturl.fm") -> "ShortURL"
            host.contains("short.io") -> "Short.io"
            host.contains("bl.ink") -> "BL.INK"
            host.contains("t.ly") -> "T.ly"
            host.contains("snip.ly") -> "Sniply"
            host.contains("rebrandly.com") -> "Rebrandly"
            host.contains("maps.google.") || (host.contains("google.") && url.contains("/maps")) -> "Google Maps"
            host.contains("tumblr.com") -> "Tumblr"
            host.contains("urbandictionary.com") -> "UrbanDictionary"
            host.contains("imgur.com") -> "Imgur"
            host.contains("msn.com") -> "MSN"
            host.contains("spotify.com") -> "Spotify"
            host.contains("podcasts.apple.com") -> "Apple Podcasts"
            else -> {
                var cleanHost = host
                val prefixes = listOf("www.", "m.", "mobile.")
                for (prefix in prefixes) {
                    if (cleanHost.startsWith(prefix)) {
                        cleanHost = cleanHost.substring(prefix.length)
                    }
                }
                
                val dotIndex = cleanHost.lastIndexOf('.')
                val name = if (dotIndex > 0) cleanHost.substring(0, dotIndex) else cleanHost
                
                if (name.isNotEmpty()) {
                    name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                } else {
                    "Link"
                }
            }
        }
    }

    /**
     * Get a cleaned URL for display Subtitle (stripped of protocol, www, params)
     */
    fun getCleanedDisplayUrl(url: String?): String {
        if (url == null) return ""
        
        val cleanedFull = cleanUrl(url)
        
        try {
            val uri = URI(cleanedFull)
            var host = uri.host ?: return cleanedFull
            val path = uri.path ?: ""
            
            val prefixes = listOf("www.", "m.", "mobile.")
            for (prefix in prefixes) {
                if (host.startsWith(prefix)) {
                    host = host.substring(prefix.length)
                }
            }
            
            var display = "$host$path"
            if (display.endsWith("/")) {
                display = display.substring(0, display.length - 1)
            }
            
            return display
            
        } catch (e: Exception) {
            return cleanedFull
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
            
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            return url
        }
    }
}
