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
}
