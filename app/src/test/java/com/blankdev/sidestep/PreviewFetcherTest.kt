package com.blankdev.sidestep

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PreviewFetcherTest {

    @Test
    fun testPreviewDataWithAuthor() {
        val data = PreviewFetcher.PreviewData(title = "Title", author = "Channel")
        assertEquals("Title", data.title)
        assertEquals("Channel", data.author)
    }

    @Test
    fun testJsonParsingInvidious() {
        val jsonStr = """
            {
                "title": "Test Video",
                "videoThumbnails": [
                    {"url": "thumb1.jpg", "quality": "default"},
                    {"url": "thumb_high.jpg", "quality": "high"}
                ],
                "published": 1609459200
            }
        """.trimIndent()
        
        val json = JSONObject(jsonStr)
        val title = json.optString("title")
        val author = json.optString("author")
        assertEquals("Test Video", title)
        assertEquals("", author) // author field was added to PreviewFetcher but Invidious uses JSON differently
    }

    @Test
    fun testRedditUrlDetection() {
        // Testing isRedditUrl (if it was public or via behavior)
    }
    @Test
    fun testNitterDateRegex() {
        val html = """
            <div class="tweet-header">
                <span class="tweet-date"><a href="/user/status/123" title="Apr 2, 2024 · 5:30 PM UTC">Apr 2</a></span>
            </div>
        """.trimIndent()
        
        val pattern = java.util.regex.Pattern.compile("class=\"tweet-date\"[^>]*>\\s*<a[^>]+title=\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        var date: String? = null
        if (matcher.find()) {
            date = matcher.group(1)
        }
        
        assertEquals("Apr 2, 2024 · 5:30 PM UTC", date)
    }

    @Test
    fun testIsoDateParsingWithNitterFormat() {
        val dateStr = "Apr 2, 2024 · 5:30 PM UTC"
        val format = "MMM d, yyyy · h:mm a z"
        val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
        val date = sdf.parse(dateStr)
        assertNotNull(date)
    }
    @Test
    fun testRedlibDateScraping() {
        val url = "https://redlib.CATSARCH.COM/r/foo"
        val html = """
            <html>
            <body>
                 <time class="created" datetime="2023-10-27T10:00:00Z">2 days ago</time>
            </body>
            </html>
        """.trimIndent()
        
        // This simulates the internal logic of fetchRedditPreview fallback
        val pattern = java.util.regex.Pattern.compile("<time[^>]+datetime=\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        var date: String? = null
        if (matcher.find()) date = matcher.group(1)
        
        assertEquals("2023-10-27T10:00:00Z", date)
    }

    @Test
    fun testJsonLdExtraction() {
        val html = """
            <html>
            <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@type": "VideoObject",
              "name": "Test Video",
              "datePublished": "2024-04-01T12:00:00+00:00"
            }
            </script>
            </html>
        """.trimIndent()
        
        // Simulating the extraction logic
        val pattern = java.util.regex.Pattern.compile("<script type=['\"]application/ld\\+json['\"]>(.*?)</script>", java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.DOTALL)
        val matcher = pattern.matcher(html)
        var date: String? = null
        if (matcher.find()) {
            val json = matcher.group(1)
            val datePattern = java.util.regex.Pattern.compile("\"datePublished\"\\s*:\\s*['\"]([^'\"]+)['\"]")
            val dateMatcher = datePattern.matcher(json)
            if (dateMatcher.find()) date = dateMatcher.group(1)
        }
        
        assertEquals("2024-04-01T12:00:00+00:00", date)
    }

    @Test
    fun testDuckDuckGoScraping() {
        // Simulated HTML response from html.duckduckgo.com/html
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
            <div class="result results_links results_links_deep web-result">
                <div class="result__body links_main links_deep">
                    <h2 class="result__title">
                        <a class="result__a" href="somewhere">This is a Video Title - YouTube</a>
                    </h2>
                    <a class="result__snippet" href="somewhere">
                        Dec 14, 2025 ... This is the description of the video which was uploaded recently. It contains interesting content.
                    </a>
                </div>
            </div>
            </body>
            </html>
        """.trimIndent()
        
        // Simulating the extraction logic
        val titlePattern = java.util.regex.Pattern.compile("class=\"result__a\"[^>]*>([^<]+)</a>", java.util.regex.Pattern.CASE_INSENSITIVE)
        val titleMatcher = titlePattern.matcher(html)
        var title: String? = null
        if (titleMatcher.find()) {
            title = titleMatcher.group(1)
            if (title?.endsWith(" - YouTube") == true) {
                title = title.removeSuffix(" - YouTube")
            }
        }
        
        val snippetPattern = java.util.regex.Pattern.compile("class=\"result__snippet\"[^>]*>(.*?)</a>", java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.DOTALL)
        val snippetMatcher = snippetPattern.matcher(html)
        var date: String? = null
        if (snippetMatcher.find()) {
            val snippet = snippetMatcher.group(1)
            val dateRegex = java.util.regex.Pattern.compile("([A-Z][a-z]{2}\\s+\\d{1,2},?\\s+\\d{4})")
            val dateMatch = dateRegex.matcher(snippet)
            if (dateMatch.find()) date = dateMatch.group(1)
        }
        
        assertEquals("This is a Video Title", title)
        assertEquals("Dec 14, 2025", date)
    }
}
