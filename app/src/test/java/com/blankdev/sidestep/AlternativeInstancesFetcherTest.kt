package com.blankdev.sidestep

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class AlternativeInstancesFetcherTest {

    @Test
    fun testUptimeValueCalculation() {
        val instance1 = AlternativeInstancesFetcher.Instance("test.com", uptime = "99.9%", uptime7d = "98.5%")
        // uptimeValue should use uptime7d if available
        assertEquals(98.5f, instance1.uptimeValue)

        val instance2 = AlternativeInstancesFetcher.Instance("test2.com", uptime = "95.0%")
        assertEquals(95.0f, instance2.uptimeValue)

        val instance3 = AlternativeInstancesFetcher.Instance("test3.com")
        assertEquals(0f, instance3.uptimeValue)
    }

    @Test
    fun testInvidiousUpptimeParsing() {
        val json = """
        [
          {
            "name": "invidious.snopyta.org",
            "url": "https://invidious.snopyta.org/",
            "status": "up",
            "uptime": "99.92%",
            "uptimeWeek": "100.00%"
          }
        ]
        """.trimIndent()
        
        val root = JSONArray(json)
        val obj = root.getJSONObject(0)
        val url = obj.getString("url")
        val uptime = obj.getString("uptime")
        
        assertEquals("https://invidious.snopyta.org/", url)
        assertEquals("99.92%", uptime)
    }

    @Test
    fun testPipedUpptimeParsing() {
        val json = """
        [
          {
            "name": "kavin.rocks",
            "url": "https://pipedapi.kavin.rocks/",
            "status": "up",
            "uptime": "99.62%",
            "uptimeWeek": "100.00%"
          }
        ]
        """.trimIndent()
        
        val root = JSONArray(json)
        val obj = root.getJSONObject(0)
        val url = obj.getString("url")
        val uptime = obj.getString("uptime")
        val uptimeWeek = obj.getString("uptimeWeek")
        
        assertEquals("https://pipedapi.kavin.rocks/", url)
        assertEquals("99.62%", uptime)
        assertEquals("100.00%", uptimeWeek)
    }    

    @Test
    fun testNitterScrapingRegexRobust() {
        // Enclosed in a row with hidden Average column and All Time % column
        val row = """
          <tr>
            <td><a rel="nofollow external" href="https:&#x2F;&#x2F;nitter.poast.org">nitter.poast.org</a></td>
            <td>🇺🇸</td>
            <td>✅</td>
            <td>SVG</td>
            <td style="display: none">Average</td>
            <td>86.5%</td>
          </tr>
        """.trimIndent()
        
        val domainMatcher = Pattern.compile("href=\"https?(?::|&#[xX]3[aA];)(?:&#[xX]2[fF];|/){2}([a-z0-9.-]+\\.[a-z]{2,})\"", Pattern.CASE_INSENSITIVE).matcher(row)
        assertTrue(domainMatcher.find())
        assertEquals("nitter.poast.org", domainMatcher.group(1))
        
        val pctMatcher = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?%)").matcher(row)
        val percentages = mutableListOf<String>()
        while (pctMatcher.find()) {
            percentages.add(pctMatcher.group(1))
        }
        
        // Should find at least the last one
        assertEquals("86.5%", percentages.last())
    }

    @Test
    fun testMarkdownLinkRegexWithPossessiveQuantifiers() {
        // Test the fixed regex pattern from line 327
        val pattern = Pattern.compile("\\|\\[([^\\]]++)\\]\\(([^)]++)\\)\\|")
        
        // Valid markdown link
        val validInput = "|[example.com](https://example.com)|"
        val validMatcher = pattern.matcher(validInput)
        assertTrue(validMatcher.find())
        assertEquals("example.com", validMatcher.group(1))
        assertEquals("https://example.com", validMatcher.group(2))
        
        // Multiple links
        val multipleLinks = "|[domain1.com](https://domain1.com)| and |[domain2.org](https://domain2.org)|"
        val multipleMatcher = pattern.matcher(multipleLinks)
        assertTrue(multipleMatcher.find())
        assertEquals("domain1.com", multipleMatcher.group(1))
        assertTrue(multipleMatcher.find())
        assertEquals("domain2.org", multipleMatcher.group(1))
        
        // Edge case: malicious input with many brackets (should not cause ReDoS)
        val maliciousInput = "|[" + "[".repeat(1000) + "](https://example.com)|"
        val maliciousMatcher = pattern.matcher(maliciousInput)
        // Should complete quickly without catastrophic backtracking
        val startTime = System.currentTimeMillis()
        maliciousMatcher.find()
        val endTime = System.currentTimeMillis()
        assertTrue("Regex should complete in under 500ms", (endTime - startTime) < 500)
    }

    @Test
    fun testDomainPatternRegexWithPossessiveQuantifiers() {
        // Test the fixed regex pattern from line 422 - using atomic grouping to prevent ReDoS
        val pattern = Pattern.compile("((?>[a-z0-9-]+)(?:\\.(?>[a-z0-9-]+))*\\.[a-z]{2,})", Pattern.CASE_INSENSITIVE)
        
        // Valid domains embedded in HTML-like text (matching actual usage)
        val testCases = mapOf(
            "Visit example.com for more" to "example.com",
            "Check out sub.example.org today" to "sub.example.org",
            "Go to test-domain.co.uk now" to "test-domain.co.uk",
            "See 123.example.net here" to "123.example.net"
        )
        
        for ((html, expectedDomain) in testCases) {
            val matcher = pattern.matcher(html)
            assertTrue("Should match in: $html", matcher.find())
            assertEquals(expectedDomain, matcher.group(1))
        }
        
        // Edge case: malicious input with many valid characters (should not cause ReDoS)
        val maliciousInput = "a".repeat(10000) + "invalid"
        val maliciousMatcher = pattern.matcher(maliciousInput)
        // Should complete quickly without catastrophic backtracking
        val startTime = System.currentTimeMillis()
        maliciousMatcher.find()
        val endTime = System.currentTimeMillis()
        assertTrue("Regex should complete in under 500ms", (endTime - startTime) < 500)
    }
}
