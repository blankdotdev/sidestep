package com.blankdev.sidestep

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility to resolve shortened URLs (e.g. vt.tiktok.com)
 */
object UrlUnshortener {
    
    private const val MAX_UNSHORTENING_HOPS = 10
    private const val HTTP_OK = 200
    private const val SHORT_PATH_MAX_LENGTH = 12
    private const val MIN_PATH_LENGTH = 1
    private const val SHORT_DOMAIN_MAX_LENGTH = 10
    private const val MIN_SCORE_FOR_SHORTENER = 3
    
    /**
     * Resolves a shortened URL to its final destination using HEAD requests to follow redirects.
     */
    suspend fun unshorten(url: String, resolveHtml: Boolean = true): String = withContext(Dispatchers.IO) {
        var currentUrl = ensureProtocol(url)
        if (!isShortenedUrl(currentUrl)) return@withContext currentUrl
        
        println("Sidestep: Unshortening $url")
        
        try {
            var hops = 0
            val maxHops = MAX_UNSHORTENING_HOPS
            var shouldContinue = true
            
            while (hops < maxHops && shouldContinue) {
                val result = processUnshorteningHop(currentUrl, resolveHtml)
                
                when {
                    result.shouldStop -> shouldContinue = false
                    result.newUrl != null && result.newUrl != currentUrl -> {
                        currentUrl = result.newUrl
                        if (result.shouldContinue) {
                            hops++
                        } else {
                            shouldContinue = false
                        }
                    }
                    else -> shouldContinue = false
                }
            }
            currentUrl
        } catch (e: java.io.IOException) {
            println("Sidestep: Unshortening network error for $url: ${e.message}")
            currentUrl
        } catch (e: java.net.URISyntaxException) {
            println("Sidestep: Unshortening URI error for $url: ${e.message}")
            currentUrl
        }
    }
    
    private data class UnshorteningResult(
        val newUrl: String?,
        val shouldStop: Boolean,
        val shouldContinue: Boolean
    )
    
    private fun processUnshorteningHop(currentUrl: String, resolveHtml: Boolean): UnshorteningResult {
        if (shouldStopUnshortening(currentUrl)) {
            return UnshorteningResult(null, shouldStop = true, shouldContinue = false)
        }

        return makeRequest(currentUrl)?.use { response ->
            val newUrl = response.request.url.toString()
            if (newUrl != currentUrl) {
                println("Sidestep: Redirected to $newUrl")
            }

            // Check if we need to resolve HTML (interstitial/canonical)
            if (response.code == HTTP_OK && isShortenedUrl(newUrl) && resolveHtml) {
                val html = response.body?.string() ?: ""
                val resolved = parseHtmlRedirect(html)
                
                if (resolved != null && resolved != newUrl) {
                    UnshorteningResult(resolved, shouldStop = false, shouldContinue = true)
                } else {
                    UnshorteningResult(null, shouldStop = true, shouldContinue = false)
                }
            } else {
                UnshorteningResult(newUrl, shouldStop = true, shouldContinue = false)
            }
        } ?: UnshorteningResult(null, shouldStop = true, shouldContinue = false)
    }

    private fun shouldStopUnshortening(url: String): Boolean {
        return url.contains("/video/") || url.contains("/reels/") || url.contains("/watch?v=")
    }

    private fun makeRequest(url: String): okhttp3.Response? {
        val userAgent = if (url.contains("facebook.com")) {
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Mobile/15E148 Safari/604.1"
        } else {
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }
        
        val request = okhttp3.Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .build()
            
        return try {
            val response = NetworkClient.client.newBuilder()
                .followRedirects(true)
                .build()
                .newCall(request)
                .execute()
                
            if (response.isSuccessful) response else {
                response.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    


    /**
     * Parses HTML to find redirection URLs (Meta refresh, JS, etc.)
     */
    internal fun parseHtmlRedirect(html: String): String? {
        val patterns = listOf(
            // 1. Meta Refresh
            """<meta\s+http-equiv=["']refresh["']\s+content=["']\d+;\s*url=([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """<meta\s+content=["']\d+;\s*url=([^"']+)["']\s+http-equiv=["']refresh["']""".toRegex(RegexOption.IGNORE_CASE),
            // 2. og:url
            """<meta\s+property=["']og:url["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """<meta\s+content=["']([^"']+)["']\s+property=["']og:url["']""".toRegex(RegexOption.IGNORE_CASE),
            """<meta\s+name=["']og:url["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """<meta\s+content=["']([^"']+)["']\s+name=["']og:url["']""".toRegex(RegexOption.IGNORE_CASE),
            // 3. Canonical
            """<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            // 4. JS Replace
            """window\.location\.replace\s*\(\s*["']([^"']+)["']\s*\)""".toRegex(RegexOption.IGNORE_CASE),
            """window\.location\.href\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """redirectToUrl\s*\(\s*["']([^"']+)["']\s*\)""".toRegex(RegexOption.IGNORE_CASE),
            """redirectToUrlAfterTimeout\s*\(\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            // 5. Fallback Link
            """<a\s+[^>]*href=["']([^"']+)["'][^>]*>(?:<[^>]+>)*\s*(?:Click|Tap)\s+here\s*(?:<\/[^>]+>)*\s*<\/a>""".toRegex(RegexOption.IGNORE_CASE)
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(html)?.groupValues?.get(1)
        }
    }

    
    private fun isShortenedUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        
        // Fast path: Check known shorteners first
        val knownShorteners = listOf(
            "bit.ly", "bitly.com", "tinyurl.com", "goo.gl",
            "vm.tiktok.com", "vt.tiktok.com", "v.tiktok.com", "youtu.be", "t.co",
            "ow.ly", "is.gd", "rebrandly.com", "shorturl.at",
            "amzn.to", "shopify.link", "y2u.be", "fb.me",
            "apple.co", "lnkd.in", "share.google", "apple.news",
            "tr.ee", "linktr.ee", "t.me", "shorturl.fm",
            "shorten.ly", "tiny.cc", "short.io", "bl.ink",
            "snip.ly", "t.ly", "dub.sh", "rb.gy",
            "facebook.com/share/", "shop.app", "msn.com", "a.co"
        )
        
        if (knownShorteners.any { lowerUrl.contains(it) }) {
            return true
        }
        
        // Always try to unshorten TikTok links regardless of heuristic
        if (lowerUrl.contains("tiktok.com")) return true
        
        // Heuristic detection for unknown shorteners
        return isLikelyShortened(url)
    }
    
    /**
     * Heuristic detection for shortened URLs based on common characteristics.
     * A URL is likely shortened if it meets multiple criteria.
     */
    private fun isLikelyShortened(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            val path = uri.path ?: "/"
            
            if (isExcludedDomain(host)) return false
            
            val score = calculateShortenerScore(host, path)
            isHighProbabilityShortener(score, host, path)
        } catch (ignored: java.net.URISyntaxException) {
            false
        }
    }

    private fun isExcludedDomain(host: String): Boolean {
        val excludedDomains = listOf(
            "google.com", "youtube.com", "github.com", "reddit.com",
            "twitter.com", "x.com", "facebook.com", "instagram.com",
            "linkedin.com", "amazon.com", "ebay.com", "wikipedia.org",
            "stackoverflow.com", "medium.com", "nytimes.com"
        )
        return excludedDomains.any { host.contains(it) }
    }

    private fun calculateShortenerScore(host: String, path: String): Int {
        var score = 0
        
        // Criterion 1: Very short path
        val pathWithoutQuery = path.split('?')[0].split('#')[0]
        if (pathWithoutQuery.length in (MIN_PATH_LENGTH + 1)..SHORT_PATH_MAX_LENGTH) score++
        
        // Criterion 2: Short domain name
        val domainWithoutTld = host.substringBeforeLast('.')
        if (domainWithoutTld.length <= SHORT_DOMAIN_MAX_LENGTH) score++
        
        // Criterion 3: Common shortener TLDs
        val shortenerTlds = listOf(
            ".co", ".ly", ".me", ".io", ".sh", ".gy", ".ee", ".fm",
            ".ink", ".link", ".to", ".gl", ".gd", ".at", ".cc"
        )
        if (shortenerTlds.any { host.endsWith(it) }) score++
        
        // Criterion 4: Known shortener patterns
        val shortenerPatterns = listOf("/share/", "/p/", "/d/", "/s/", "/r/", "/t/", "/link/")
        if (shortenerPatterns.any { path.startsWith(it) }) score++
        
        // Criterion 5: Subdomain patterns
        val shortenerSubdomains = listOf("vm.", "vt.", "go.", "link.", "short.")
        if (shortenerSubdomains.any { host.startsWith(it) }) score++
        
        return score
    }

    private fun isHighProbabilityShortener(score: Int, host: String, path: String): Boolean {
        // - 3+ criteria = very likely shortened
        if (score >= MIN_SCORE_FOR_SHORTENER) return true

        val pathWithoutQuery = path.split('?')[0].split('#')[0]
        val hasShortPath = pathWithoutQuery.length in (MIN_PATH_LENGTH + 1)..SHORT_PATH_MAX_LENGTH
        val domainWithoutTld = host.substringBeforeLast('.')
        val hasShortDomain = domainWithoutTld.length <= SHORT_DOMAIN_MAX_LENGTH
        
        val shortenerTlds = listOf(
            ".co", ".ly", ".me", ".io", ".sh", ".gy", ".ee", ".fm",
            ".ink", ".link", ".to", ".gl", ".gd", ".at", ".cc"
        )
        val hasShortenerTld = shortenerTlds.any { host.endsWith(it) }
        
        val shortenerPatterns = listOf("/share/", "/p/", "/d/", "/s/", "/r/", "/t/", "/link/")
        val hasPattern = shortenerPatterns.any { path.startsWith(it) }

        return (score >= 2 && hasShortDomain && hasShortPath && hasShortenerTld) ||
               (score >= 2 && hasPattern)
    }

    private fun ensureProtocol(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }
}
