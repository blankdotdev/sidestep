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

    data class PreviewData(
        val title: String? = null,
        val author: String? = null,
        val description: String? = null,
        val timestamp: Long? = null
    )

    private val cache = object : LinkedHashMap<String, PreviewData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreviewData>?): Boolean {
            return size > 50 // Keep last 50 previews
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
        try {
            // Use the original URL to search, not the instance URL if possible, to get better results?
            // Actually, searching the specific URL is best.
            val query = java.net.URLEncoder.encode(targetUrl, "UTF-8")
            val searchUrl = "https://html.duckduckgo.com/html?q=$query"
            
            val request = okhttp3.Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Referer", "https://html.duckduckgo.com/")
                .build()
            
            val response = NetworkClient.client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                
                // Parse first result
                // Structure: <div class="result ..."> <h2 class="result__title"> <a ...>TITLE</a> ... <a class="result__snippet" ...> ... DATE ... </a>
                
                // Extract Title
                val titlePattern = Pattern.compile("class=\"result__a\"[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE)
                val titleMatcher = titlePattern.matcher(html)
                var title: String? = null
                var author: String? = null
                if (titleMatcher.find()) {
                    title = decodeHtml(titleMatcher.group(1))
                    // Clean YouTube titles from DDG
                    if (title?.endsWith(" - YouTube") == true) {
                        title = title.removeSuffix(" - YouTube")
                    }
                }
                
                // Extract Snippet for Date
                val snippetPattern = Pattern.compile("class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                val snippetMatcher = snippetPattern.matcher(html)
                var timestamp: Long? = null
                var description: String? = null
                
                if (snippetMatcher.find()) {
                    val snippet = decodeHtml(snippetMatcher.group(1)) ?: ""
                    description = snippet
                    
                    // Improved author/channel extraction for YouTube/X/TikTok
                    // Often snippet starts with "Channel Name · Dec 14, 2025 ..." or similar
                    if (targetUrl.contains("youtube.com") || targetUrl.contains("youtu.be")) {
                        // Look for author in snippet: "Channel Name · date"
                        val authorPattern = Pattern.compile("^([^·•\\|]+)[·•\\|]", Pattern.CASE_INSENSITIVE)
                        val authorMatcher = authorPattern.matcher(snippet)
                        if (authorMatcher.find()) {
                            author = authorMatcher.group(1)?.trim()
                        }
                    } else if (targetUrl.contains("twitter.com") || targetUrl.contains("x.com")) {
                         // X snippet often looks like "Author (@handle) on X: ..."
                         val authorPattern = Pattern.compile("^([^\\(]+)\\s+\\(@[^\\)]+\\)", Pattern.CASE_INSENSITIVE)
                         val authorMatcher = authorPattern.matcher(snippet)
                         if (authorMatcher.find()) {
                             author = authorMatcher.group(1)?.trim()
                         }
                    }

                    // Look for date at start of snippet (common in search results like "Dec 14, 2025 ...")
                    // Regex for "MMM d, yyyy" or "d MMM yyyy" or "MMM d, yyyy"
                    val dateRegex = Pattern.compile("([A-Z][a-z]{2}\\s+\\d{1,2},?\\s+\\d{4})")
                    val dateMatch = dateRegex.matcher(snippet)
                    if (dateMatch.find()) {
                        timestamp = parseIsoDate(dateMatch.group(1))
                    } else {
                        // Try "N days ago" or "N hours ago"
                        val relativeRegex = Pattern.compile("(\\d+)\\s+(day|hour|minute)s?\\s+ago", Pattern.CASE_INSENSITIVE)
                        val relMatch = relativeRegex.matcher(snippet)
                        if (relMatch.find()) {
                            val amount = relMatch.group(1)?.toLongOrNull() ?: 0L
                            val unit = relMatch.group(2)?.lowercase()
                            val now = System.currentTimeMillis()
                            timestamp = when(unit) {
                                "day" -> now - (amount * 24 * 60 * 60 * 1000L)
                                "hour" -> now - (amount * 60 * 60 * 1000L)
                                "minute" -> now - (amount * 60 * 1000L)
                                else -> null
                            }
                        }
                    }
                }
                
                if (title != null) {
                    return PreviewData(title = title, author = author, timestamp = timestamp, description = description)
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return try {
            val host = java.net.URI(url).host?.lowercase() ?: ""
            host.contains("youtube.com") || host.contains("youtu.be")
        } catch (e: Exception) {
            false
        }
    }

    private fun extractNitterDate(html: String, url: String): String? {
         if (!url.contains("nitter")) return null // Basic check (imperfect if custom domain but standard defaults use 'nitter')
         // Look for <span class="tweet-date"><a ... title="Apr 2, 2024 · 5:30 PM UTC">
         val pattern = Pattern.compile("class=\"tweet-date\"[^>]*>\\s*<a[^>]+title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
         val matcher = pattern.matcher(html)
         if (matcher.find()) return matcher.group(1)
         return null
    }

    private fun extractRedlibDate(html: String, url: String): String? {
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
        return input.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .trim()
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
        
        for (format in isoFormats) {
            try {
                val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                if (format.endsWith("'Z'")) sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(dateStr)?.time
            } catch (e: Exception) {}
        }
        
        // Fallback: Try parsing as pure numeric Unix timestamp (seconds or milliseconds)
        try {
            val longVal = dateStr.trim().toLong()
            return if (longVal > 1_000_000_000_000L) {
                longVal // milliseconds
            } else if (longVal > 100_000_000L) {
                longVal * 1000 // seconds
            } else null
        } catch (e: Exception) {}
        
        return null
    }
}
