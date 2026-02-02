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
    suspend fun unshorten(url: String, resolveHtml: Boolean = true): String = withContext(Dispatchers.IO) {
        var currentUrl = ensureProtocol(url)
        if (!isShortenedUrl(currentUrl)) return@withContext currentUrl
        
        println("Sidestep: Unshortening $url")
        
        try {
            var hops = 0
            val maxHops = 10 // Increase max hops to 10 for more complex redirect chains
            
            while (hops < maxHops) {
                // Efficiency check: if we're already on a full video/post page, don't keep unshortening
                if (currentUrl.contains("/video/") || currentUrl.contains("/reels/") || currentUrl.contains("/watch?v=")) {
                    break
                }

                val userAgent = if (currentUrl.contains("facebook.com")) {
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 14_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Mobile/15E148 Safari/604.1"
                } else {
                    // Standard mobile User-Agent
                    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
                }
                
                val requestBuilder = okhttp3.Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                
                val response = NetworkClient.client.newBuilder()
                    .followRedirects(true)
                    .build()
                    .newCall(requestBuilder.build())
                    .execute()
                
                if (!response.isSuccessful) {
                    response.close()
                    break
                }

                // Update currentUrl to the final one after OkHttp followed redirects
                val finalUrl = response.request.url.toString()
                if (finalUrl != currentUrl) {
                    println("Sidestep: Redirected to $finalUrl")
                    currentUrl = finalUrl
                }

                // If we're at a 200 OK but still look like a shortened URL (interstitial/canonical), resolve HTML
                if (response.code == 200 && isShortenedUrl(currentUrl)) {
                    if (!resolveHtml) {
                         response.close()
                         break
                    }
                    
                    val html = try {
                        response.body?.string() ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    response.close()

                    val resolved = parseHtmlRedirect(html)
                    if (resolved != null && resolved != currentUrl) {
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
            }
            currentUrl
        } catch (e: Exception) {
            println("Sidestep: Unshortening error for $url: ${e.message}")
            currentUrl
        }
    }
    


    /**
     * Parses HTML to find redirection URLs (Meta refresh, JS, etc.)
     */
    internal fun parseHtmlRedirect(html: String): String? {
        // 1. Look for Meta Refresh
        val metaRefreshPatterns = listOf(
            """<meta\s+http-equiv=["']refresh["']\s+content=["']\d+;\s*url=([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """<meta\s+content=["']\d+;\s*url=([^"']+)["']\s+http-equiv=["']refresh["']""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in metaRefreshPatterns) {
            val match = pattern.find(html)
            if (match != null) return match.groupValues[1]
        }

        // 2. Look for og:url meta tag
        val ogUrlPatterns = listOf(
            """<meta\s+property=["']og:url["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """<meta\s+content=["']([^"']+)["']\s+property=["']og:url["']""".toRegex(RegexOption.IGNORE_CASE),
            """<meta\s+name=["']og:url["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """<meta\s+content=["']([^"']+)["']\s+name=["']og:url["']""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in ogUrlPatterns) {
            val match = pattern.find(html)
            if (match != null) return match.groupValues[1]
        }

        // 3. Look for canonical link tag
        val canonicalPatterns = listOf(
            """<link\s+[^>]*rel=["']canonical["'][^>]*href=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """<link\s+[^>]*href=["']([^"']+)["'][^>]*rel=["']canonical["']""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in canonicalPatterns) {
            val match = pattern.find(html)
            if (match != null) return match.groupValues[1]
        }
        
        // 4. Look for JavaScript redirection - handles various syntaxes
        val jsPatterns = listOf(
            """window\.location\.replace\(["']([^"']+)["']\)""".toRegex(RegexOption.IGNORE_CASE),
            """window\.location\.href\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """location\.replace\(["']([^"']+)["']\)""".toRegex(RegexOption.IGNORE_CASE),
            """location\.href\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """window\[["']location["']\]\[["']replace["']\]\(["']([^"']+)["']\)""".toRegex(RegexOption.IGNORE_CASE),
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
        try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            val path = uri.path ?: "/"
            
            // Exclude well-known sites that might have short paths but aren't shorteners
            val excludedDomains = listOf(
                "google.com", "youtube.com", "github.com", "reddit.com",
                "twitter.com", "x.com", "facebook.com", "instagram.com",
                "linkedin.com", "amazon.com", "ebay.com", "wikipedia.org",
                "stackoverflow.com", "medium.com", "nytimes.com"
            )
            
            if (excludedDomains.any { host.contains(it) }) {
                return false
            }
            
            var score = 0
            
            // Criterion 1: Very short path (excluding query/fragment)
            val pathWithoutQuery = path.split('?')[0].split('#')[0]
            val hasShortPath = pathWithoutQuery.length <= 12 && pathWithoutQuery.length > 1
            if (hasShortPath) {
                score++
            }
            
            // Criterion 2: Short domain name
            val domainWithoutTld = host.substringBeforeLast('.')
            val hasShortDomain = domainWithoutTld.length <= 10
            if (hasShortDomain) {
                score++
            }
            
            // Criterion 3: Common shortener TLDs
            val shortenerTlds = listOf(
                ".co", ".ly", ".me", ".io", ".sh", ".gy", ".ee", ".fm",
                ".ink", ".link", ".to", ".gl", ".gd", ".at", ".cc"
            )
            val hasShortenerTld = shortenerTlds.any { host.endsWith(it) }
            if (hasShortenerTld) {
                score++
            }
            
            // Criterion 4: Known shortener patterns
            val shortenerPatterns = listOf("/share/", "/p/", "/d/", "/s/", "/r/", "/t/", "/link/")
            val hasPattern = shortenerPatterns.any { path.startsWith(it) }
            if (hasPattern) {
                score++
            }
            
            // Criterion 5: Subdomain patterns (vm., vt., go., etc.)
            val shortenerSubdomains = listOf("vm.", "vt.", "go.", "link.", "short.")
            if (shortenerSubdomains.any { host.startsWith(it) }) {
                score++
            }
            
            // More strict scoring to avoid false positives:
            // - 3+ criteria = very likely shortened
            // - Short domain + short path + shortener TLD = likely shortened
            // - Pattern match + any other criterion = likely shortened
            return when {
                score >= 3 -> true
                score >= 2 && hasShortDomain && hasShortPath && hasShortenerTld -> true
                score >= 2 && hasPattern -> true
                else -> false
            }
            
        } catch (e: Exception) {
            return false
        }
    }

    private fun ensureProtocol(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }
}
