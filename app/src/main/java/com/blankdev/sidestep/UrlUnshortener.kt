package com.blankdev.sidestep

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility to resolve shortened URLs (e.g. vt.tiktok.com)
 */
object UrlUnshortener {
    
    /**
     * Resolves a shortened URL to its final destination using HEAD requests to follow redirects.
     */
    suspend fun unshorten(url: String): String = withContext(Dispatchers.IO) {
        if (!isShortenedUrl(url)) return@withContext url
        
        try {
            var currentUrl = ensureProtocol(url)
            var hops = 0
            val maxHops = 10
            
            while (hops < maxHops) {
                val request = okhttp3.Request.Builder()
                    .url(currentUrl)
                    .get() // Always use GET for better compatibility (e.g. tiny.cc, share.google)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                
                val response = NetworkClient.client.newBuilder()
                    .followRedirects(false)
                    .build()
                    .newCall(request)
                    .execute()
                
                if (response.code in 300..399) {
                    val location = response.header("Location")
                    if (location != null) {
                        // Handle relative redirects
                        currentUrl = if (location.startsWith("/")) {
                            try {
                                val uri = java.net.URI(currentUrl)
                                "${uri.scheme}://${uri.host}$location"
                            } catch (e: Exception) {
                                location
                            }
                        } else {
                            location
                        }
                        hops++
                    } else {
                        response.close()
                        break
                    }
                } else if (response.code == 200 && isShortenedUrl(currentUrl)) {
                    response.close()
                    val resolved = resolveHtmlRedirect(currentUrl)
                    if (resolved != currentUrl) {
                        currentUrl = resolved
                        hops++
                        continue
                    } else {
                        break
                    }
                } else {
                    response.close()
                    break
                }
                response.close()
            }
            currentUrl
        } catch (e: Exception) {
            // If resolution fails, return original or last known URL
            url
        }
    }
    
    private suspend fun resolveHtmlRedirect(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .get() // Need GET to parse HTML
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            val response = NetworkClient.client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            response.close()
            
            parseHtmlRedirect(html) ?: url
        } catch (e: Exception) {
            url
        }
    }

    /**
     * Parses HTML to find redirection URLs (Meta refresh, JS, etc.)
     */
    internal fun parseHtmlRedirect(html: String): String? {
        // 1. Look for Meta Refresh
        val metaRefreshPattern = """<meta\s+http-equiv=["']refresh["']\s+content=["']\d+;\s*url=([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
        val metaMatch = metaRefreshPattern.find(html)
        if (metaMatch != null) return metaMatch.groupValues[1]

        // 2. Look for og:url meta tag
        val ogUrlPatterns = listOf(
            """<meta\s+property=["']og:url["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """<meta\s+content=["']([^"']+)["']\s+property=["']og:url["']""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in ogUrlPatterns) {
            val match = pattern.find(html)
            if (match != null) return match.groupValues[1]
        }

        // 3. Look for canonical link tag
        val canonicalPattern = """<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
        val canonicalMatch = canonicalPattern.find(html)
        if (canonicalMatch != null) return canonicalMatch.groupValues[1]
        
        // 4. Look for JavaScript redirection
        val jsPatterns = listOf(
            """window\.location\.replace\(["']([^"']+)["']\)""".toRegex(RegexOption.IGNORE_CASE),
            """window\.location\.href\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """redirectToUrl\(["']([^"']+)["']\)""".toRegex(RegexOption.IGNORE_CASE),
            """redirectToUrlAfterTimeout\(["']([^"']+)["']\s*,\s*\d+\)""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in jsPatterns) {
            val match = pattern.find(html)
            if (match != null) return match.groupValues[1]
        }

        // 5. Look for fallback links
        val fallbackPattern = """<a\s+href=["']([^"']+)["'][^>]*>(?:[^<]|<(?!/a))*?(?:Click|Tap)\s+here""".toRegex(RegexOption.IGNORE_CASE)
        val fallbackMatch = fallbackPattern.find(html)
        if (fallbackMatch != null) return fallbackMatch.groupValues[1]

        return null
    }

    
    private fun isShortenedUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("bit.ly") || 
               lowerUrl.contains("bitly.com") ||
               lowerUrl.contains("tinyurl.com") || 
               lowerUrl.contains("goo.gl") || 
               lowerUrl.contains("vm.tiktok.com") || 
               lowerUrl.contains("vt.tiktok.com") || 
               lowerUrl.contains("youtu.be") || 
               lowerUrl.contains("t.co") || 
               lowerUrl.contains("ow.ly") || 
               lowerUrl.contains("is.gd") || 
               lowerUrl.contains("rebrandly.com") || 
               lowerUrl.contains("shorturl.at") || 
               lowerUrl.contains("amzn.to") || 
               lowerUrl.contains("shopify.link") ||
               lowerUrl.contains("y2u.be") ||
               lowerUrl.contains("fb.me") ||
               lowerUrl.contains("apple.co") ||
               lowerUrl.contains("lnkd.in") ||
               lowerUrl.contains("share.google") ||
               lowerUrl.contains("apple.news") ||
               lowerUrl.contains("tr.ee") ||
               lowerUrl.contains("linktr.ee") ||
               lowerUrl.contains("t.me") ||
               lowerUrl.contains("shorturl.fm") ||
               lowerUrl.contains("shorten.ly") ||
               lowerUrl.contains("tiny.cc") ||
               lowerUrl.contains("short.io") ||
               lowerUrl.contains("bl.ink") ||
               lowerUrl.contains("snip.ly") ||
               lowerUrl.contains("t.ly") || 
               lowerUrl.contains("dub.sh") || 
               lowerUrl.contains("rb.gy") ||
               lowerUrl.contains("facebook.com/share/")
    }

    private fun ensureProtocol(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }
}
