package com.blankdev.sidestep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlRedirectParserTest {

    @Test
    fun testParseHtmlRedirect_ogUrl() {
        val html = """<html><head><meta property="og:url" content="https://example.com/story"></head></html>"""
        assertEquals("https://example.com/story", UrlUnshortener.parseHtmlRedirect(html))
    }

    @Test
    fun testParseHtmlRedirect_ogUrlSwapped() {
        val html = """<html><head><meta content="https://example.com/story" property="og:url"></head></html>"""
        assertEquals("https://example.com/story", UrlUnshortener.parseHtmlRedirect(html))
    }

    @Test
    fun testParseHtmlRedirect_canonical() {
        val html = """<html><head><link rel="canonical" href="https://example.com/canonical"></head></html>"""
        assertEquals("https://example.com/canonical", UrlUnshortener.parseHtmlRedirect(html))
    }

    @Test
    fun testParseHtmlRedirect_jsReplace() {
        val html = """<script>window.location.replace("https://example.com/js-replace")</script>"""
        assertEquals("https://example.com/js-replace", UrlUnshortener.parseHtmlRedirect(html))
    }

    @Test
    fun testParseHtmlRedirect_redirectToUrl() {
        val html = """<script>redirectToUrl("https://example.com/redirect-to-url")</script>"""
        assertEquals("https://example.com/redirect-to-url", UrlUnshortener.parseHtmlRedirect(html))
    }

    @Test
    fun testParseHtmlRedirect_redirectToUrlAfterTimeout() {
        val html = """<script>redirectToUrlAfterTimeout("https://example.com/timeout", 0)</script>"""
        assertEquals("https://example.com/timeout", UrlUnshortener.parseHtmlRedirect(html))
    }

    @Test
    fun testParseHtmlRedirect_fallbackLink() {
        val html = """<p><a href="https://example.com/fallback">Click here</a> if it doesn't open</p>"""
        assertEquals("https://example.com/fallback", UrlUnshortener.parseHtmlRedirect(html))
    }

    @Test
    fun testParseHtmlRedirect_fallbackLinkTap() {
        val html = """<p><a href="https://example.com/fallback-tap">Tap here</a> if it doesn't open</p>"""
        assertEquals("https://example.com/fallback-tap", UrlUnshortener.parseHtmlRedirect(html))
    }

    @Test
    fun testParseHtmlRedirect_fallbackLinkSpan() {
        val html = """<p><a href="https://example.com/fallback-span"><span class="click-here">Click here</span></a> if it doesn't open</p>"""
        assertEquals("https://example.com/fallback-span", UrlUnshortener.parseHtmlRedirect(html))
    }

    @Test
    fun testParseHtmlRedirect_noMatch() {
        val html = """<html><body>No link here</body></html>"""
        assertNull(UrlUnshortener.parseHtmlRedirect(html))
    }
}
