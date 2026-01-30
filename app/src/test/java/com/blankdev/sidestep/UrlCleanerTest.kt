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
    fun testCleanUrl_removesShopAppParams() {
        val url = "https://shop.app/p/4809742778417?variantId=32563652558897&utm_source=shop_app&link_alias=J9gp0loQ93Zxr"
        val cleaned = UrlCleaner.cleanUrl(url)
        
        assertEquals("https://shop.app/p/4809742778417", cleaned)
        assertFalse(cleaned.contains("variantId"))
        assertFalse(cleaned.contains("link_alias"))
        assertFalse(cleaned.contains("utm_source"))
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
    fun testNormalizeFacebookUrl_video_withTitle() {
        val url = "https://www.facebook.com/thedailyshow/videos/trump-wants-that-sweet-venezuelan-oil/2359858081104781/"
        val result = UrlCleaner.normalizeFacebookUrl(url)
        assertEquals("https://www.facebook.com/watch?v=2359858081104781", result)
    }

    @Test
    fun testNormalizeFacebookUrl_reel_noTrailingSlash() {
        val url = "https://www.facebook.com/reel/2359858081104781"
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

    @Test
    fun testCleanUrl_removesGaaParams_TheTimes() {
        val url = "https://www.thetimes.com/uk/education/article/uae-limiting-students-coming-to-uk-over-muslim-brotherhood-concerns-zvpdd6fqn?gaa_at=eafs&gaa_n=AWEtsqc0spmJcNYZeBEucErTMHQ6iCtJvanCklgSsjJVRIPJkxogu-avozTFkR7BLeA=&gaa_ts=69614113&gaa_sig=x99uGKXW3xO6B_uGNZmdUxz5pYJC1gGhnk9JUc1JonAZRoUl214i01hAbTNmpH4l83LoQwIrCt4TWeQlfzIvjg=="
        val result = UrlCleaner.cleanUrl(url)
        
        assertFalse(result.contains("gaa_at"))
        assertFalse(result.contains("gaa_n"))
        assertFalse(result.contains("gaa_ts"))
        assertFalse(result.contains("gaa_sig"))
        
        // Ensure path is preserved
        assertTrue(result.contains("/uk/education/article/uae-limiting-students-coming-to-uk-over-muslim-brotherhood-concerns-zvpdd6fqn"))
    }

    @Test
    fun testCleanUrl_removesYahooFinanceParams() {
        val url = "https://finance.yahoo.com/news/no-reasons-own-software-stocks-140000103.html?guce_referrer=aHR0cHM6Ly93d3cuZ29vZ2xlLmNvbS8&guce_referrer_sig=AQAAADxZ3Kl8GXgKMmTJgB8mCd599Vstr2JrlTBO6dDBdbXaH2CunZdP6nPkrcdFAnJzLK6_zyF6LyO3zitRzzSAZ_HmVG164_wP524AhTwLniT9YVRmPzKtB9CQQlInnsbgHwzOXk6xZ62t4AGfbXGHYs-7v5mblpW0UPVyduyRiPvN&_guc_consent_skip=1769715675"
        val result = UrlCleaner.cleanUrl(url)
        
        assertFalse(result.contains("guce_referrer"))
        assertFalse(result.contains("guce_referrer_sig"))
        assertFalse(result.contains("_guc_consent_skip"))
        
        assertEquals("https://finance.yahoo.com/news/no-reasons-own-software-stocks-140000103.html", result)
    }

    @Test
    fun testCleanUrl_removesRobustTrackingParams() {
        val url = "https://example.com/page?utm_campaign=legacy&pk_campaign=piwik&sc_lid=emarsys&vero_id=vero&_hsenc=hubspot&mkt_tok=marketo"
        val clean = UrlCleaner.cleanUrl(url)
        assertEquals("https://example.com/page", clean)
    }

    @Test
    fun testCleanUrl_removesAmazonSocialShareParams() {
        val url = "https://www.amazon.ca/dp/B07DD7PDGH?ref=cm_sw_r_cso_cp_apan_dp_ES1C45KPVMYJQ0QS883D&ref_=cm_sw_r_cso_cp_apan_dp_ES1C45KPVMYJQ0QS883D&social_share=cm_sw_r_cso_cp_apan_dp_ES1C45KPVMYJQ0QS883D"
        val result = UrlCleaner.cleanUrl(url)
        
        assertFalse(result.contains("social_share"))
        assertFalse(result.contains("cm_sw_r"))
        assertFalse(result.contains("ref="))
        assertFalse(result.contains("ref_="))
        
        assertEquals("https://www.amazon.ca/dp/B07DD7PDGH", result)
    }

    @Test
    fun testCleanUrl_removesAmazonPathParams() {
        val url = "https://www.amazon.ca/COSORI-TurboBlaze-Technology-Airfryer-Dishwasher/dp/B0D1KQKZM2/ref=sr_1_1_sspa"
        val result = UrlCleaner.cleanUrl(url)
        assertEquals("https://www.amazon.ca/COSORI-TurboBlaze-Technology-Airfryer-Dishwasher/dp/B0D1KQKZM2", result)
    }

    @Test
    fun testCleanUrl_removesGenericPathParams() {
        val url = "https://example.com/article/source=rss/title"
        val result = UrlCleaner.cleanUrl(url)
        assertEquals("https://example.com/article/title", result)
    }

    @Test
    fun testCleanUrl_removesImdbPathParams() {
        val url = "https://www.imdb.com/title/tt1234567/ref_=tt_sims_tt_i_1"
        val result = UrlCleaner.cleanUrl(url)
        assertEquals("https://www.imdb.com/title/tt1234567", result)
    }

    @Test
    fun testCleanUrl_removesTikTokTrackingParams() {
        val url = "https://www.tiktok.com/@whitewoodmac/video/7600787341246090518?_r=1&_t=ZS-93Ujdr9rNH6"
        val result = UrlCleaner.cleanUrl(url)
        
        assertFalse(result.contains("_r="))
        assertFalse(result.contains("_t="))
        assertEquals("https://www.tiktok.com/@whitewoodmac/video/7600787341246090518", result)
    }
}
