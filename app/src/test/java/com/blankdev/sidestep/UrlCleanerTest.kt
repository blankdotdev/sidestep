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
    fun testIsTwitterOrXUrl_vxtwitter() {
        assertTrue(UrlCleaner.isTwitterOrXUrl("https://vxtwitter.com/user"))
        assertTrue(UrlCleaner.isTwitterOrXUrl("https://fxtwitter.com/user"))
    }

    @Test
    fun testGetServiceName() {
        assertEquals("Apple News", UrlCleaner.getServiceName("https://apple.news/asm"))
        assertEquals("Bitly", UrlCleaner.getServiceName("https://bit.ly/xyz"))
        assertEquals("TinyURL", UrlCleaner.getServiceName("https://tinyurl.com/123"))
        assertEquals("Rebrandly", UrlCleaner.getServiceName("https://rebrandly.com/foo"))
        assertEquals("Yahoo Finance", UrlCleaner.getServiceName("https://finance.yahoo.com/news/article.html"))
        assertEquals("The Grayzone", UrlCleaner.getServiceName("https://thegrayzone.com/2026/01/29/article/"))
        assertEquals("Google News", UrlCleaner.getServiceName("https://news.google.com/home"))
        assertEquals("The Verge", UrlCleaner.getServiceName("https://theverge.com/tech"))
        assertEquals("Google", UrlCleaner.getServiceName("https://google.co.uk"))
        assertEquals("Twitter", UrlCleaner.getServiceName("https://vxtwitter.com/user"))
        assertEquals("Twitter", UrlCleaner.getServiceName("https://fxtwitter.com/user"))
    }

    @Test
    fun testGetServiceName_improvedHeuristics() {
        assertEquals("Scientific American", UrlCleaner.getServiceName("https://scientificamerican.com/article/123"))
        assertEquals("Popular Mechanics", UrlCleaner.getServiceName("https://popularmechanics.com/tech/123"))
        assertEquals("Washington Post", UrlCleaner.getServiceName("https://washingtonpost.com/politics/123"))
        assertEquals("USA Today", UrlCleaner.getServiceName("https://usatoday.com/story/123"))
        assertEquals("NY Times", UrlCleaner.getServiceName("https://nytimes.com/2024/02/02/world/europe/123"))
        assertEquals("New Yorker", UrlCleaner.getServiceName("https://newyorker.com/magazine/123"))
        assertEquals("The Atlantic", UrlCleaner.getServiceName("https://theatlantic.com/ideas/123"))
        assertEquals("The Guardian", UrlCleaner.getServiceName("https://theguardian.com/world/123"))
        assertEquals("WSJ", UrlCleaner.getServiceName("https://wsj.com/articles/123"))
        assertEquals("BBC News", UrlCleaner.getServiceName("https://bbcnews.com/123"))
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
    fun testValidAppUrl() {
        assertTrue(UrlCleaner.isValidAppUrl("http://google.com"))
        assertTrue(UrlCleaner.isValidAppUrl("https://example.com/path"))
        assertTrue(UrlCleaner.isValidAppUrl("HTTP://UPPERCASE.COM"))
        
        assertFalse(UrlCleaner.isValidAppUrl("file:///etc/hosts"))
        assertFalse(UrlCleaner.isValidAppUrl("javascript:alert(1)"))
        assertFalse(UrlCleaner.isValidAppUrl("google.com")) // Must have protocol to be valid app URL
        assertFalse(UrlCleaner.isValidAppUrl(null))
        assertFalse(UrlCleaner.isValidAppUrl(""))
    }

    @Test
    fun testIsRedditUrl() {
        assertTrue(UrlCleaner.isRedditUrl("https://reddit.com/r/Android"))
        assertTrue(UrlCleaner.isRedditUrl("https://www.reddit.com/r/Android"))
        assertTrue(UrlCleaner.isRedditUrl("https://old.reddit.com/r/Android"))
        assertTrue(UrlCleaner.isRedditUrl("https://redd.it/123"))
        assertFalse(UrlCleaner.isRedditUrl("https://example.com"))
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
    fun testCleanUrl_removesAmazonSearchAndTrackingParams() {
        val url = "https://www.amazon.ca/Foldable-Smart-Intelligent-Muslim-Prayer/dp/B0CHW9ZPQY/ref=mp_s_a_1_8_mod_primary_new?crid=35NIWJEIHQ2UH&dib=eyJ2IjoiMSJ9&dib_tag=se&keywords=prayer+mat+for+beginners&qid=1771644082&sbo=RZvfv%2F%2FHxDF%2BO5021pAnSA%3D%3D&sprefix=prayer+mat%2Caps%2C214&sr=8-8"
        val result = UrlCleaner.cleanUrl(url)
        assertEquals("https://www.amazon.ca/Foldable-Smart-Intelligent-Muslim-Prayer/dp/B0CHW9ZPQY", result)
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

    @Test
    fun testCleanUrl_vxtwitter() {
        val url = "https://vxtwitter.com/user/status/123?s=20"
        val clean = UrlCleaner.cleanUrl(url)
        assertEquals("https://vxtwitter.com/user/status/123", clean)
    }
    @Test
    fun testCleanUrl_removesCmSpParam() {
        val url = "https://www.td.com/ca/en/investing/direct-investing/direct-investing-offer?cm_sp=:GOOGLE:Direct+Investing+-+Brand+-+Core+-+English+(26_S_WL_WDI_AO_ACQ_EN_BRA)+AP:26193:DIF:Brand+-+Core+-+Investing&gclsrc=aw.ds&gad_source=1&gad_campaignid=299938722&gclid=CjwKCAiAncvMBhBEEiwA9GU_flxtUnj71mAwmoS_nKDVkMjHpm4LEwuu5-ytKtOwHQEHwoPH4DLmxhoC0-cQAvD_BwE"
        val expected = "https://www.td.com/ca/en/investing/direct-investing/direct-investing-offer"
        val cleaned = UrlCleaner.cleanUrl(url)
        
        assertEquals(expected, cleaned)
    }

    @Test
    fun testCleanUrl_removesCTVNewsTaidParam() {
        val url = "https://www.ctvnews.ca/montreal/article/quebec-probing-reports-of-israeli-soldiers-speaking-in-montreal-schools/?cid=sm%3Atrueanthem%3Actvmontreal%3Atwitterpost%E2%80%8B&taid=69a0f3acb050d80001bce8b4&utm_campaign=trueAnthem%3A+Trending+Content&utm_medium=trueAnthem&utm_source=twitter"
        val expected = "https://www.ctvnews.ca/montreal/article/quebec-probing-reports-of-israeli-soldiers-speaking-in-montreal-schools/"
        val cleaned = UrlCleaner.cleanUrl(url)
        
        assertEquals(expected, cleaned)
        assertFalse(cleaned.contains("taid="))
        assertFalse(cleaned.contains("cid="))
    }

    @Test
    fun testIsFandomUrl() {
        assertTrue(UrlCleaner.isFandomUrl("https://minecraft.fandom.com/wiki/Creeper"))
        assertTrue(UrlCleaner.isFandomUrl("https://www.fandom.com/articles"))
        assertFalse(UrlCleaner.isFandomUrl("https://example.com"))
    }

    @Test
    fun testGetServiceName_fandom() {
        assertEquals("Fandom", UrlCleaner.getServiceName("https://minecraft.fandom.com/wiki/Creeper"))
        assertEquals("Fandom", UrlCleaner.getServiceName("https://www.fandom.com"))
    }

    @Test
    fun testReplaceDomain_fandomWithSubdomain() {
        val url = "https://minecraft.fandom.com/wiki/Creeper"
        val result = UrlCleaner.replaceDomain(url, "breezewiki.com")
        assertEquals("https://breezewiki.com/minecraft/wiki/Creeper", result)
    }

    @Test
    fun testReplaceDomain_fandomNoSubdomain() {
        val url = "https://www.fandom.com/articles/something"
        val result = UrlCleaner.replaceDomain(url, "breezewiki.com")
        assertEquals("https://breezewiki.com/articles/something", result)
    }

    @Test
    fun testReplaceDomain_fandomEmptyPath() {
        val url = "https://marvel.fandom.com"
        val result = UrlCleaner.replaceDomain(url, "breezewiki.com")
        assertEquals("https://breezewiki.com/marvel/", result)
    }

    @Test
    fun testIsPixivUrl() {
        assertTrue(UrlCleaner.isPixivUrl("https://www.pixiv.net/artworks/12345678"))
        assertTrue(UrlCleaner.isPixivUrl("https://pixiv.net/en/artworks/12345678"))
        assertTrue(UrlCleaner.isPixivUrl("https://touch.pixiv.net/member_illust.php?mode=medium&illust_id=12345678"))
        assertTrue(UrlCleaner.isPixivUrl("https://pixiv.me/artistname"))
        org.junit.Assert.assertFalse(UrlCleaner.isPixivUrl("https://example.com"))
    }

    @Test
    fun testGetServiceName_pixiv() {
        assertEquals("Pixiv", UrlCleaner.getServiceName("https://www.pixiv.net/artworks/12345678"))
        assertEquals("Pixiv", UrlCleaner.getServiceName("https://pixiv.me/artistname"))
    }

    @Test
    fun testReplaceDomain_pixiv() {
        val url = "https://www.pixiv.net/artworks/12345678"
        val result = UrlCleaner.replaceDomain(url, "pixivfe.exozy.me")
        assertEquals("https://pixivfe.exozy.me/artworks/12345678", result)
    }

    @Test
    fun testCleanUrl_removesInstagramIgsi() {
        val url = "https://www.instagram.com/p/ABC123/?igsi=abcdefghijk&igsh=xyz"
        val cleaned = UrlCleaner.cleanUrl(url)
        assertFalse("igsi should be stripped", cleaned.contains("igsi"))
        assertFalse("igsh should be stripped", cleaned.contains("igsh"))
        assertEquals("https://www.instagram.com/p/ABC123/", cleaned)
    }

    @Test
    fun testCleanUrl_removesInstagramIgsiInAppendMode() {
        // Simulates what append-mode custom redirect produces before cleaning:
        // the cleanedUrl fed into "$base$cleanedUrl" must not contain igsi
        val url = "https://www.instagram.com/reel/ABC123/?igsi=someid&utm_source=ig_web_copy_link"
        val cleaned = UrlCleaner.cleanUrl(url)
        assertFalse("igsi should be stripped before append", cleaned.contains("igsi"))
        assertFalse("utm_source should be stripped", cleaned.contains("utm_source"))
        assertEquals("https://www.instagram.com/reel/ABC123/", cleaned)
    }
}
