package com.blankdev.sidestep

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Fetches URL preview metadata using privacy-focused methods (DuckDuckGo search)
 */
object PreviewFetcher {
    
    private const val CACHE_INITIAL_CAPACITY = 16
    private const val CACHE_LOAD_FACTOR = 0.75f
    private const val CACHE_MAX_SIZE = 50
    private const val MIN_HTML_DEBUG_SIZE = 1000
    private const val DEBUG_SNIPPET_LENGTH = 500
    private const val HOURS_PER_DAY = 24
    private const val MINUTES_PER_HOUR = 60
    private const val SECONDS_PER_MINUTE = 60
    private const val MS_PER_SECOND = 1000L
    private const val TIMESTAMP_MS_THRESHOLD = 1_000_000_000_000L
    private const val TIMESTAMP_SECONDS_THRESHOLD = 100_000_000L

    data class PreviewData(
        val title: String? = null,
        val author: String? = null,
        val description: String? = null,
        val timestamp: Long? = null
    )

    private val cache = object : LinkedHashMap<String, PreviewData>(CACHE_INITIAL_CAPACITY, CACHE_LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreviewData>?): Boolean {
            return size > CACHE_MAX_SIZE // Keep last 50 previews
        }
    }

    /**
     * Fetches preview data for a URL using DuckDuckGo search results.
     */
    suspend fun fetchPreview(url: String): PreviewData? = withContext(Dispatchers.IO) {
        cache[url]?.let { return@withContext it }
        
        val data = fetchViaDuckDuckGo(url)
        if (data != null) {
            cache[url] = data
        }
        return@withContext data
    }
    
    private fun fetchViaDuckDuckGo(targetUrl: String): PreviewData? {
        return try {
            // Searching and matching of exact URLs is better without quotes for many domains like Yahoo and Grayzone
            val query = java.net.URLEncoder.encode(targetUrl, "UTF-8")
            val searchUrl = "https://html.duckduckgo.com/html?q=$query"
            
            // Mobile-style headers to avoid bot detection and potentially get Lite version classes
            val userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
            val request = okhttp3.Request.Builder()
                .url(searchUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", "https://html.duckduckgo.com/")
                .build()
            
            println("Sidestep: Fetching preview from $searchUrl")
            val response = NetworkClient.client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                println("Sidestep: Preview fetch failed with code ${response.code}")
                response.close()
                null
            } else {
                val html = response.body?.string() ?: ""
                response.close()
                
                // Parse first result - resilient to both HTML version (result__a) and Lite version (result-link)
                val titlePattern = Pattern.compile("class=[\"']result(?:__a|-link)[\"'][^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE)
                val titleMatcher = titlePattern.matcher(html)
                
                var title: String? = null
                var author: String? = null
                
                if (titleMatcher.find()) {
                    title = decodeHtml(titleMatcher.group(1))
                    // Clean YouTube titles from DDG
                    if (title?.endsWith(" - YouTube") == true) {
                        title = title.removeSuffix(" - YouTube")
                    }
                    println("Sidestep: Preview title found: $title")
                } else {
                    println("Sidestep: No title found in DDG response (HTML length: ${html.length})")
                    // Fallback debug: print first 500 chars of HTML if it looks tiny (potential bot block)
                    if (html.length < MIN_HTML_DEBUG_SIZE) println("Sidestep: DDG response snippet: ${html.take(DEBUG_SNIPPET_LENGTH)}")
                }
                
                // Extract Snippet for Date - resilient to both HTML version (result__snippet) and Lite version (result-snippet)
                val snippetPattern = Pattern.compile("class=[\"']result(?:__snippet|-snippet)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                val snippetMatcher = snippetPattern.matcher(html)
                var timestamp: Long? = null
                var description: String? = null
                
                if (snippetMatcher.find()) {
                    val snippet = decodeHtml(snippetMatcher.group(1)) ?: ""
                    val snippetData = parseSnippetData(snippet, targetUrl)
                    description = snippetData.description
                    author = snippetData.author ?: author
                    timestamp = snippetData.timestamp
                }
                
                if (title != null) {
                    PreviewData(title = title, author = author, timestamp = timestamp, description = description)
                } else {
                    null
                }
            }
        } catch (e: java.io.IOException) {
            println("Sidestep: Preview fetch network error: ${e.message}")
            null
        } catch (e: java.net.URISyntaxException) {
            println("Sidestep: Preview fetch URI error: ${e.message}")
            null
        }
    }
    
    private data class SnippetData(
        val description: String,
        val author: String?,
        val timestamp: Long?
    )
    
    private fun parseSnippetData(snippet: String, targetUrl: String): SnippetData {
        var author: String? = null
        var timestamp: Long? = null
        
        // Improved author/channel extraction for YouTube/X/TikTok
        if (targetUrl.contains("youtube.com") || targetUrl.contains("youtu.be")) {
            val authorPattern = Pattern.compile("^([^·•\\|]+)[·•\\|]", Pattern.CASE_INSENSITIVE)
            val authorMatcher = authorPattern.matcher(snippet)
            if (authorMatcher.find()) {
                author = authorMatcher.group(1)?.trim()
            }
        } else if (targetUrl.contains("twitter.com") || targetUrl.contains("x.com")) {
            val authorPattern = Pattern.compile("^([^\\(]+)\\s+\\(@[^\\)]+\\)", Pattern.CASE_INSENSITIVE)
            val authorMatcher = authorPattern.matcher(snippet)
            if (authorMatcher.find()) {
                author = authorMatcher.group(1)?.trim()
            }
        }

        // Look for date at start of snippet
        val dateRegex = Pattern.compile("([A-Z][a-z]{2}\\s+\\d{1,2},?\\s+\\d{4})")
        val dateMatch = dateRegex.matcher(snippet)
        if (dateMatch.find()) {
            timestamp = parseIsoDate(dateMatch.group(1))
        } else {
            val relativeRegex = Pattern.compile("(\\d+)\\s+(day|hour|minute)s?\\s+ago", Pattern.CASE_INSENSITIVE)
            val relMatch = relativeRegex.matcher(snippet)
            if (relMatch.find()) {
                val amount = relMatch.group(1)?.toLongOrNull() ?: 0L
                val unit = relMatch.group(2)?.lowercase()
                val now = System.currentTimeMillis()
                timestamp = when(unit) {
                    "day" -> now - (amount * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MS_PER_SECOND)
                    "hour" -> now - (amount * MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MS_PER_SECOND)
                    "minute" -> now - (amount * SECONDS_PER_MINUTE * MS_PER_SECOND)
                    else -> null
                }
            }
        }
        
        return SnippetData(snippet, author, timestamp)
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return try {
            val host = java.net.URI(url).host?.lowercase() ?: ""
            host.contains("youtube.com") || host.contains("youtu.be")
        } catch (e: java.net.URISyntaxException) {
            android.util.Log.d("PreviewFetcher", "Invalid URI in YouTube check: $url", e)
            false
        }
    }

    private fun extractNitterDate(html: String, url: String): String? {
         if (!url.contains("nitter")) return null 
         // Look for <span class="tweet-date"><a ... title="Apr 2, 2024 · 5:30 PM UTC">
         val pattern = Pattern.compile("class=\"tweet-date\"[^>]*>\\s*<a[^>]+title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
         val matcher = pattern.matcher(html)
         return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractRedlibDate(@Suppress("UnusedParameter") html: String, url: String): String? {
        if (!url.contains("reddit") && !url.contains("libreddit") && !url.contains("redlib")) return null
        // Redlib often uses <time datetime="..."> which is caught by extractTimeTag, but just in case
        return null
    }

    private fun extractMetaTag(html: String, attrValue: String): String? {
        // Simple regex to extract <meta property="..." content="..."> or <meta name="..." content="...">
        val patterns = listOf(
            Pattern.compile("<meta[^>]+(?:property|name)=\"$attrValue\"[^>]+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+content=\"([^\"]+)\"[^>]+(?:property|name)=\"$attrValue\"", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) return matcher.group(1)
        }
        
        // Fallback for <title> tag
        if (attrValue == "title") {
            val titlePattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val titleMatcher = titlePattern.matcher(html)
            if (titleMatcher.find()) return titleMatcher.group(1)
        }
        
        return null
    }

    private fun decodeHtml(input: String?): String? {
        if (input == null) return null
        
        // Handle common entities manually for speed and efficiency
        var result = input.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&#x27;", "'") // Specifically added as requested by screenshot
            .replace("&#039;", "'")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            
        // If it still looks like it has entities, use a more comprehensive approach
        if (result.contains("&#") || result.contains("&")) {
            try {
                // androidx.core.text.HtmlCompat is the best way to handle this in modern Android
                // but since we want to avoid complex dependencies in this utility if possible, 
                // we'll stick to basic decoding or use the standard Html.fromHtml if available.
                result = android.text.Html.fromHtml(result, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Fallback to simpler Html.fromHtml for older APIs or if it fails
                android.util.Log.w("PreviewFetcher", "HTML parsing failed, trying deprecated method", e)
                try {
                    @Suppress("DEPRECATION")
                    result = android.text.Html.fromHtml(result).toString()
                } catch (e: Exception) {
                    android.util.Log.d("PreviewFetcher", "HTML decoding fallback failed, keeping original", e)
                    // Keep original if HTML parsing fails
                }
            }
        }
        
        return result.trim()
    }

    private fun parseIsoDate(dateStr: String?): Long? {
        if (dateStr == null) return null
        
        // Try parsing as ISO 8601
        val isoFormats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd",
            "MMM d, yyyy · h:mm a z", // Nitter format (e.g., Apr 2, 2024 · 5:30 PM UTC)
            "MMM d, yyyy · h:mm a",
            "MMM d, yyyy"
        )
        
        // Sequence to find first valid parse
        return isoFormats.asSequence().mapNotNull { format ->
            try {
                val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                if (format.endsWith("'Z'")) sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(dateStr)?.time
            } catch (e: Exception) {
                android.util.Log.d("PreviewFetcher", "Date parsing failed for format: $format", e)
                null
            }
        }.firstOrNull() ?: tryParseNumericTimestamp(dateStr)
    }

    private fun tryParseNumericTimestamp(dateStr: String): Long? {
         return try {
            val longVal = dateStr.trim().toLong()
            if (longVal > TIMESTAMP_MS_THRESHOLD) {
                longVal // milliseconds
            } else if (longVal > TIMESTAMP_SECONDS_THRESHOLD) {
                longVal * MS_PER_SECOND // seconds
            } else null
        } catch (e: NumberFormatException) {
            android.util.Log.d("PreviewFetcher", "Invalid numeric timestamp: $dateStr", e)
            null
        }
    }
}
