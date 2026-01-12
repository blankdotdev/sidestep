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
            "https://rb.gy/qux"
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
    fun testUnshorten_tinyCc() = runBlocking {
        val result = UrlUnshortener.unshorten("http://tiny.cc/qicx001")
        assertTrue("Expected Wikipedia but got: $result", result.contains("wikipedia.org"))
    }

    // Helper to call private method for testing (since it's an object)
    private fun UrlUnshortener_isShortenedUrl(url: String): Boolean {
        val method = UrlUnshortener::class.java.getDeclaredMethod("isShortenedUrl", String::class.java)
        method.isAccessible = true
        return method.invoke(UrlUnshortener, url) as Boolean
    }
}
