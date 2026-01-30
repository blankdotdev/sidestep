package com.blankdev.sidestep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlinx.coroutines.runBlocking

class UrlUnshortenerTest {

    @Test
    fun testIsShortenedUrl_allServices() {
        val shortenable = listOf(
            "https://bit.ly/3xyz",
            "https://bitly.com/abc",
            "https://tinyurl.com/123",
            "https://rebrandly.com/foo",
            "https://fb.me/bar",
            "https://amzn.to/baz",
            "https://apple.co/qux",
            "https://lnkd.in/qlm",
            "https://tiny.cc/abc",
            "https://short.io/xyz",
            "https://bl.ink/123",
            "https://snip.ly/foo",
            "https://t.ly/bar",
            "https://dub.sh/baz",
            "https://rb.gy/qux",
            "https://shop.app/p/123",
            "https://www.msn.com/en-us/health/other/our-oral-microbiome/ar-AA1UKmZh",
            "https://a.co/d/0jZk7S7"
        )
        
        for (url in shortenable) {
            assertTrue("Should be recognized as shortened: $url", UrlUnshortener_isShortenedUrl(url))
        }
    }

    @Test
    fun testIsShortenedUrl_nonShortened() {
        val normal = listOf(
            "https://www.google.com/search",
            "https://github.com/blankdev/sidestep",
            "https://nytimes.com/2024/01/01/news.html"
        )
        
        for (url in normal) {
            assertFalse("Should not be recognized as shortened: $url", UrlUnshortener_isShortenedUrl(url))
        }
    }

    @Test
    fun testHeuristicDetection_unknownShorteners() {
        // These are hypothetical shorteners not in the known list
        // They should be detected by heuristics (short domain + short path + shortener TLD)
        val likelyShortened = listOf(
            "https://xyz.link/abc",      // Short domain + .link TLD + short path
            "https://go.example.io/x1",  // go. subdomain + .io TLD + short path
            "https://short.ly/test123",  // short. subdomain + .ly TLD
            "https://mylink.to/abc123"   // .to TLD + short path + short domain
        )
        
        for (url in likelyShortened) {
            assertTrue("Should detect as likely shortened: $url", UrlUnshortener_isShortenedUrl(url))
        }
    }

    @Test
    fun testHeuristicDetection_edgeCases() {
        // These should NOT be detected as shorteners despite having some characteristics
        val notShortened = listOf(
            "https://github.com/user",           // Excluded domain
            "https://youtube.com/watch",         // Excluded domain
            "https://verylongdomainname.co/abc", // Long domain despite .co TLD
            "https://example.com/very-long-path-that-is-not-short"  // Long path
        )
        
        for (url in notShortened) {
            assertFalse("Should not detect as shortened: $url", UrlUnshortener_isShortenedUrl(url))
        }
    }

    @Test
    fun testHeuristicDetection_pathPatterns() {
        // Test detection based on common shortener path patterns
        val patterned = listOf(
            "https://example.link/share/abc123",  // /share/ pattern + .link TLD
            "https://mysite.io/d/xyz",            // /d/ pattern + .io TLD
            "https://link.me/p/12345"             // /p/ pattern + .me TLD
        )
        
        for (url in patterned) {
            assertTrue("Should detect pattern-based shortener: $url", UrlUnshortener_isShortenedUrl(url))
        }
    }

    @Test
    fun testUnshorten_tinyCc() = runBlocking {
        val result = UrlUnshortener.unshorten("http://tiny.cc/qicx001")
        assertTrue("Expected Wikipedia but got: $result", result.contains("wikipedia.org"))
    }

    @Test
    fun testParseHtmlRedirect_msnCanonical() {
        val msnHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <link rel="canonical" href="https://www.newscientist.com/article/2512970-our-oral-microbiome-could-hold-the-key-to-preventing-obesity/">
            </head>
            <body></body>
            </html>
        """.trimIndent()
        
        val result = UrlUnshortener.parseHtmlRedirect(msnHtml)
        assertEquals("https://www.newscientist.com/article/2512970-our-oral-microbiome-could-hold-the-key-to-preventing-obesity/", result)
    }

    @Test
    fun testParseHtmlRedirect_msnCanonicalSwapped() {
        val msnHtml = """
            <link href="https://www.example.com/article" rel="canonical">
        """.trimIndent()
        
        val result = UrlUnshortener.parseHtmlRedirect(msnHtml)
        assertEquals("https://www.example.com/article", result)
    }

    // Helper to call private method for testing (since it's an object)
    private fun UrlUnshortener_isShortenedUrl(url: String): Boolean {
        val method = UrlUnshortener::class.java.getDeclaredMethod("isShortenedUrl", String::class.java)
        method.isAccessible = true
        return method.invoke(UrlUnshortener, url) as Boolean
    }
}
