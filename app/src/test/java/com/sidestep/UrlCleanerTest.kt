package com.blankdev.sidestep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.URLEncoder

/**
 * Unit tests for URL cleaning and redirection logic
 */
class UrlCleanerTest {

    @Test
    fun testCleanUrl_removesTrackingParameters() {
        val url = "https://twitter.com/user/status/123?s=20&t=abc123xyz"
        val cleaned = UrlCleaner.cleanUrl(url)
        
        // Should not contain tracking parameters
        assertTrue(!cleaned.contains("s="))
        assertTrue(!cleaned.contains("t="))
    }

    @Test
    fun testCleanUrl_removesAppleTracking() {
        val url = "https://example.com/page?itscg=30100"
        val cleaned = UrlCleaner.cleanUrl(url)
        assertFalse(cleaned.contains("itscg"))
    }

    @Test
    fun testCleanUrl_preservesPath() {
        val url = "https://twitter.com/NASA/status/1234567890123456789?s=20"
        val cleaned = UrlCleaner.cleanUrl(url)
        
        assertTrue(cleaned.contains("/NASA/status/1234567890123456789"))
    }

    @Test
    fun testReplaceDomain_userProfile() {
        val url = "https://twitter.com/elonmusk"
        val result = UrlCleaner.replaceDomain(url, "nitter.net")
        
        assertEquals("https://nitter.net/elonmusk", result)
    }

    @Test
    fun testReplaceDomain_xComProfile() {
        val url = "https://x.com/elonmusk"
        val result = UrlCleaner.replaceDomain(url, "nitter.net")
        
        assertEquals("https://nitter.net/elonmusk", result)
    }

    @Test
    fun testReplaceDomain_tweetUrl() {
        val url = "https://twitter.com/NASA/status/1234567890123456789"
        val result = UrlCleaner.replaceDomain(url, "nitter.net")
        
        assertEquals("https://nitter.net/NASA/status/1234567890123456789", result)
    }

    @Test
    fun testReplaceDomain_xComTweet() {
        val url = "https://x.com/NASA/status/1234567890123456789"
        val result = UrlCleaner.replaceDomain(url, "nitter.net")
        
        assertEquals("https://nitter.net/NASA/status/1234567890123456789", result)
    }

    @Test
    fun testReplaceDomain_threadUrl() {
        val url = "https://twitter.com/threadreaderapp/status/1234567890123456789"
        val result = UrlCleaner.replaceDomain(url, "nitter.net")
        
        assertEquals("https://nitter.net/threadreaderapp/status/1234567890123456789", result)
    }

    @Test
    fun testReplaceDomain_photoUrl() {
        val url = "https://x.com/username/status/987654321/photo/1"
        val result = UrlCleaner.replaceDomain(url, "nitter.net")
        
        assertEquals("https://nitter.net/username/status/987654321/photo/1", result)
    }

    @Test
    fun testFullWorkflow_cleanAndReplace() {
        val url = "https://twitter.com/user/status/123?s=20&t=abc123xyz&ref_src=twsrc%5Etfw"
        val cleaned = UrlCleaner.cleanUrl(url)
        val result = UrlCleaner.replaceDomain(cleaned, "nitter.net")
        
        // Should have nitter.net domain
        assertTrue(result.contains("nitter.net"))
        // Should preserve path
        assertTrue(result.contains("/user/status/123"))
        // Should not have tracking parameters
        assertTrue(!result.contains("s="))
        assertTrue(!result.contains("t="))
        assertTrue(!result.contains("ref_src"))
    }

    @Test
    fun testIsTwitterOrXUrl_twitter() {
        assertTrue(UrlCleaner.isTwitterOrXUrl("https://twitter.com/user"))
        assertTrue(UrlCleaner.isTwitterOrXUrl("https://www.twitter.com/user"))
    }

    @Test
    fun testIsTwitterOrXUrl_x() {
        assertTrue(UrlCleaner.isTwitterOrXUrl("https://x.com/user"))
        assertTrue(UrlCleaner.isTwitterOrXUrl("https://www.x.com/user"))
    }

    @Test
    fun testIsTwitterOrXUrl_http() {
        assertTrue(UrlCleaner.isTwitterOrXUrl("http://twitter.com/user"))
        assertTrue(UrlCleaner.isTwitterOrXUrl("http://x.com/user"))
    }

    @Test
    fun testGetServiceName() {
        assertEquals("Apple News", UrlCleaner.getServiceName("https://apple.news/asm"))
        assertEquals("Bitly", UrlCleaner.getServiceName("https://bit.ly/xyz"))
        assertEquals("TinyURL", UrlCleaner.getServiceName("https://tinyurl.com/123"))
        assertEquals("Rebrandly", UrlCleaner.getServiceName("https://rebrandly.com/foo"))
    }

    @Test
    fun testCleanUrl_nestedUrl() {
        val url = "https://www.google.com/url?rct=j&sa=t&url=https%3A%2F%2Fwww.rijksoverheid.nl%2Fministeries%2Fministerie-van-buitenlandse-zaken%2Fhet-werk-van-bz-in-de-praktijk%2Fweblogs%2F2025%2Finternationale-dag-tegen-straffeloosheid-voor-misdaden-tegen-journalisten&ct=ga&cd=CAEYACoTNjM1MDg4NzI5MTM4Nzg2Nzk1NjIbZTJhODA2NGY1MWMwNjNhMTpjYTplbjpVUzpM&usg=AOvVaw0q79BKP7EoxOe06EQCqWib"
        val cleaned = UrlCleaner.cleanUrl(url)
        
        assertEquals("https://www.rijksoverheid.nl/ministeries/ministerie-van-buitenlandse-zaken/het-werk-van-bz-in-de-praktijk/weblogs/2025/internationale-dag-tegen-straffeloosheid-voor-misdaden-tegen-journalisten", cleaned)
    }

    @Test
    fun testCleanUrl_recursiveNestedUrl() {
        val inner = "https://example.com/page?utm_source=test"
        val outer = "https://redirect.com/?url=${URLEncoder.encode(inner, "UTF-8")}"
        val cleaned = UrlCleaner.cleanUrl(outer)
        
        assertEquals("https://example.com/page", cleaned)
    }
    @Test
    fun testIsWikipediaUrl_subdomain() {
        assertTrue(UrlCleaner.isWikipediaUrl("https://en.wikipedia.org/wiki/Main_Page"))
    }

    @Test
    fun testGetServiceName_wikipediaSubdomain() {
        assertEquals("Wikipedia", UrlCleaner.getServiceName("https://en.wikipedia.org/wiki/Main_Page"))
    }

    @Test
    fun testConvertGoogleMapsToOsm_usesWww() {
        val url = "https://www.google.com/maps?ll=37.7749,-122.4194"
        val result = UrlCleaner.convertGoogleMapsToOsm(url)
        assertTrue(result.contains("https://www.openstreetmap.org"))
    }

    @Test
    fun testConvertGoogleMapsToOsm_searchQuery() {
        val url = "https://www.google.com/maps?q=Eiffel+Tower"
        val result = UrlCleaner.convertGoogleMapsToOsm(url)
        assertEquals("https://www.openstreetmap.org/search?query=Eiffel+Tower", result)
    }

    @Test
    fun testConvertGoogleMapsToOsm_searchPath() {
        val url = "https://www.google.com/maps/search/Golden+Gate+Bridge"
        val result = UrlCleaner.convertGoogleMapsToOsm(url)
        assertEquals("https://www.openstreetmap.org/search?query=Golden+Gate+Bridge", result)
    }

    @Test
    fun testConvertGoogleMapsToOsm_searchQueryParam() {
        val url = "https://www.google.com/maps/search/?api=1&query=London+Eye"
        val result = UrlCleaner.convertGoogleMapsToOsm(url)
        assertEquals("https://www.openstreetmap.org/search?query=London+Eye", result)
    }

    @Test
    fun testIsImgurUrl() {
        assertTrue(UrlCleaner.isImgurUrl("https://imgur.com/gallery/abc"))
        assertTrue(UrlCleaner.isImgurUrl("https://www.imgur.com/abc"))
        assertFalse(UrlCleaner.isImgurUrl("https://example.com"))
    }

    @Test
    fun testGetServiceName_imgur() {
        assertEquals("Imgur", UrlCleaner.getServiceName("https://imgur.com/abc"))
    }

    @Test
    fun testReplaceDomain_rimgo() {
        val url = "https://imgur.com/gallery/123"
        val result = UrlCleaner.replaceDomain(url, "imgur.artemislena.eu")
        assertEquals("https://imgur.artemislena.eu/gallery/123", result)
    }
    @Test
    fun testIsFacebookUrl() {
        assertTrue(UrlCleaner.isFacebookUrl("https://www.facebook.com/share/v/18Bwv2oVqL/"))
        assertTrue(UrlCleaner.isFacebookUrl("https://facebook.com/reel/123"))
    }

    @Test
    fun testNormalizeFacebookUrl_video() {
        val url = "https://www.facebook.com/thedailyshow/videos/2359858081104781/"
        val result = UrlCleaner.normalizeFacebookUrl(url)
        assertEquals("https://www.facebook.com/watch?v=2359858081104781", result)
    }

    @Test
    fun testNormalizeFacebookUrl_reel() {
        val url = "https://www.facebook.com/reel/2359858081104781/"
        val result = UrlCleaner.normalizeFacebookUrl(url)
        assertEquals("https://www.facebook.com/watch?v=2359858081104781", result)
    }

    @Test
    fun testCleanUrl_facebookNormalization() {
        val url = "https://www.facebook.com/reel/2359858081104781/?ref=share"
        val result = UrlCleaner.cleanUrl(url)
        // Should be normalized to watch and tracking param 'ref' removed
        assertEquals("https://www.facebook.com/watch?v=2359858081104781", result)
    }
    @Test
    fun testCleanUrl_facebookNormalization_removesVanity() {
        // vanity is now in TRACKING_PARAMS, so it should be removed
        val url = "https://www.facebook.com/reel/2359858081104781/?vanity=thedailyshow&ref=share"
        val result = UrlCleaner.cleanUrl(url)
        // Should be normalized to watch, ref removed, vanity removed
        assertTrue(result.contains("/watch"))
        assertTrue(result.contains("v=2359858081104781"))
        assertFalse(result.contains("vanity=thedailyshow"))
        assertFalse(result.contains("ref=share"))
    }
}
