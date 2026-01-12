package com.blankdev.sidestep

import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Fetches and manages alternative frontend instances with health/uptime data.
 * Provides fallback domains when API fetching fails.
 */
object AlternativeInstancesFetcher {

    data class Instance(
        val domain: String, 
        val uptime: String? = null,
        val uptime7d: String? = null,
        val health: String? = null // e.g., "Healthy", "Dead", "Slow"
    ) {
        val uptimeValue: Float
            get() = (uptime7d ?: uptime)?.replace("%", "")?.toFloatOrNull() ?: 0f
    }

    private const val NITTER_WIKI_URL = "https://github.com/zedeus/nitter/wiki/Instances"
    private const val NITTER_STATUS_URL = "https://status.d420.de/"
    private const val REDLIB_API_URL = "https://stats.uptimerobot.com/api/getMonitorList/mpmqAs1G2Q"
    private const val INVIDIOUS_API_URL = "https://raw.githubusercontent.com/gitetsu/invidious-instances-upptime/master/history/summary.json"
    private const val PIPED_INSTANCES_URL = "https://raw.githubusercontent.com/TeamPiped/piped-uptime/master/history/summary.json"
    private const val PRIVIBLUR_INSTANCES_URL = "https://raw.githubusercontent.com/syeopite/priviblur/master/instances.md"

    private val NITTER_FALLBACK_DOMAINS = listOf(
        "nitter.net", "xcancel.com", "nitter.poast.org", "nitter.privacyredirect.com", 
        "nitter.space", "nitter.tiekoetter.com", "nuku.trabun.org"
    )

    private val REDLIB_FALLBACK_DOMAINS = listOf(
        "l.opnxng.com", "librdit.com", "lr.artemislena.eu", "rd.809090.xyz", 
        "redlib.catsarch.com", "redlib.freedit.eu", "redlib.private.coffee"
    )

    private val INVIDIOUS_FALLBACK_DOMAINS = listOf(
        "yewtu.be", "inv.nadeko.net", "invidious.snopyta.org", "invidious.kavin.rocks",
        "invidious.namazso.eu", "invidious.lunar.icu", "invidious.projectsegfau.lt"
    )

    private val PIPED_FALLBACK_DOMAINS = listOf(
        "piped.video", "piped.uzair.dev", "piped.mha.fi", "piped.kavin.rocks",
        "piped.link", "piped.projectsegfau.lt", "piped.privacy.com.de"
    )

    private val IMDB_FALLBACK_DOMAINS = listOf(
        "libremdb.pussthecat.org", "libremdb.iket.me"
    )

    private val MEDIUM_FALLBACK_DOMAINS = listOf(
        "scribe.rip", "scribe.nixnet.services", "scribe.citizen4.eu", "scribe.privacydev.net", "m.opnxng.com"
    )

    private val PRIVIBLUR_FALLBACK_DOMAINS = listOf(
        "tb.opnxng.com", "pb.bloat.cat", "priviblur.pussthecat.org"
    )

    fun getNitterDefaults(): List<Instance> = NITTER_FALLBACK_DOMAINS.map { Instance(it) }
    fun getRedlibDefaults(): List<Instance> = REDLIB_FALLBACK_DOMAINS.map { Instance(it) }
    fun getInvidiousDefaults(): List<Instance> = INVIDIOUS_FALLBACK_DOMAINS.map { Instance(it) }
    fun getPipedDefaults(): List<Instance> = PIPED_FALLBACK_DOMAINS.map { Instance(it) }
    fun getImdbDefaults(): List<Instance> = IMDB_FALLBACK_DOMAINS.map { Instance(it) }
    fun getMediumDefaults(): List<Instance> = MEDIUM_FALLBACK_DOMAINS.map { Instance(it) }
    fun getPriviblurDefaults(): List<Instance> = PRIVIBLUR_FALLBACK_DOMAINS.map { Instance(it) }

    private val WIKILESS_FALLBACK_DOMAINS = listOf(
        "wikiless.tiekoetter.com", "wiki.adminforge.de", "wikiless.rawbit.ninja"
    )

    private val BIBLIOREADS_FALLBACK_DOMAINS = listOf(
        "biblioreads.eu.org", "biblioreads.vercel.app", "biblioreads.mooo.com"
    )
    
    private val DUMB_FALLBACK_DOMAINS = listOf(
        "dm.vern.cc", "sing.whatever.social", "dumb.lunar.icu", "dumb.privacydev.net"
    )
    
    private val GOTHUB_FALLBACK_DOMAINS = listOf(
        "gothub.lunar.icu", "g.opnxng.com", "gh.owo.si", "gothub.projectsegfau.lt"
    )
    
    private val ANONYMOUSOVERFLOW_FALLBACK_DOMAINS = listOf(
        "ao.vern.cc", "code.whatever.social", "overflow.smnz.de", "overflow.lunar.icu"
    )

    private val RURAL_DICTIONARY_FALLBACK_DOMAINS = listOf(
        "rd.vern.cc", "rd.bloat.cat"
    )

    private val RIMGO_FALLBACK_DOMAINS = listOf(
        "imgur.artemislena.eu", "rimgo.manasiwara.com", "rimgo.totaljustice.org", "rimgo.vern.cc"
    )

    private val INTELLECTUAL_FALLBACK_DOMAINS = listOf(
        "intellectual.insprill.net", "intellectual.privacydev.net"
    )

    fun getWikilessDefaults(): List<Instance> = WIKILESS_FALLBACK_DOMAINS.map { Instance(it) }
    fun getBiblioReadsDefaults(): List<Instance> = BIBLIOREADS_FALLBACK_DOMAINS.map { Instance(it) }
    fun getDumbDefaults(): List<Instance> = DUMB_FALLBACK_DOMAINS.map { Instance(it) }
    fun getGotHubDefaults(): List<Instance> = GOTHUB_FALLBACK_DOMAINS.map { Instance(it) }
    fun getAnonymousOverflowDefaults(): List<Instance> = ANONYMOUSOVERFLOW_FALLBACK_DOMAINS.map { Instance(it) }
    fun getRuralDictionaryDefaults(): List<Instance> = RURAL_DICTIONARY_FALLBACK_DOMAINS.map { Instance(it) }
    fun getRimgoDefaults(): List<Instance> = RIMGO_FALLBACK_DOMAINS.map { Instance(it) }
    fun getIntellectualDefaults(): List<Instance> = INTELLECTUAL_FALLBACK_DOMAINS.map { Instance(it) }

    suspend fun fetchNitterInstances(): List<Instance> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Instance>()
        try {
            results.addAll(fetchNitterFromStatusPage(NITTER_STATUS_URL))
            if (results.isEmpty()) {
                results.addAll(fetchNitterFromWiki())
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        if (results.isEmpty()) {
            results.addAll(getNitterDefaults())
        }
        return@withContext results.sortedByDescending { it.uptimeValue }
    }

    suspend fun fetchRedlibInstances(): List<Instance> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Instance>()
        try {
            val connection = URL(REDLIB_API_URL).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }
            
            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val monitors = root.optJSONObject("psp")?.optJSONArray("monitors")
                
                if (monitors != null) {
                    for (i in 0 until monitors.length()) {
                        val m = monitors.getJSONObject(i)
                        val domain = m.optString("name")
                        val status = m.optInt("status") // 2 is typically UP
                        val uptime = m.optJSONObject("ratio")?.optString("ratio")
                        val uptime30d = m.optJSONObject("30dRatio")?.optString("ratio")
                        
                        val health = if (status == 2) "Healthy" else if (status == 0) "Down" else "Issues"
                        
                        if (isAlternativeInstance(domain)) {
                            results.add(Instance(
                                domain = domain,
                                uptime = if (uptime != null) "$uptime%" else null,
                                uptime7d = if (uptime30d != null) "$uptime30d%" else null,
                                health = health
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        if (results.isEmpty()) {
            results.addAll(getRedlibDefaults())
        }
        return@withContext results.sortedByDescending { it.uptimeValue }
    }

    suspend fun fetchInvidiousInstances(): List<Instance> = withContext(Dispatchers.IO) {
        val instances = mutableListOf<Instance>()
        try {
            val connection = URL(INVIDIOUS_API_URL).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            
            // Upptime summary.json format: [{"url": "...", "uptime": "99.9%", "status": "up", ...}, ...]
            val root = JSONArray(json)
            for (i in 0 until root.length()) {
                val obj = root.getJSONObject(i)
                val url = obj.optString("url")
                val domain = try { URI(url).host } catch (e: Exception) { null } ?: continue
                
                val uptime = obj.optString("uptime") // "99.92%"
                val status = obj.optString("status") // "up" or "down"
                
                val health = if (status.equals("up", ignoreCase = true)) "Healthy" else "Down"
                
                // Parse percentage string to float for uptimeValue calculation
                // "uptime" field usually has the % sign
                
                if (isAlternativeInstance(domain)) {
                    instances.add(Instance(
                        domain = domain,
                        uptime = uptime,
                        uptime7d = obj.optString("uptimeWeek"), // Upptime provides uptimeWeek
                        health = health
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (instances.isEmpty()) {
            instances.addAll(getInvidiousDefaults())
        }
        return@withContext instances.sortedByDescending { it.uptimeValue }
    }

    suspend fun fetchPipedInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("piped")
        return if (fetched.isEmpty()) getPipedDefaults() else fetched
    }

    private const val LIBREDIRECT_INSTANCES_URL = "https://raw.githubusercontent.com/libredirect/instances/main/data.json"

    suspend fun fetchWikilessInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("wikiless")
        return if (fetched.isEmpty()) getWikilessDefaults() else fetched
    }

    suspend fun fetchLibremdbInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("libremdb")
        return if (fetched.isEmpty()) getImdbDefaults() else fetched
    }

    private suspend fun fetchLibRedirectInstances(key: String): List<Instance> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Instance>()
        try {
            val connection = URL(LIBREDIRECT_INSTANCES_URL).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }
            
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val serviceObj = root.optJSONObject(key)
            val clearnetList = serviceObj?.optJSONArray("clearnet")
            
            if (clearnetList != null) {
                for (i in 0 until clearnetList.length()) {
                    val url = clearnetList.getString(i)
                    val domain = try { URI(url).host } catch (e: Exception) { null } ?: continue
                    // LibRedirect json doesn't have uptime data, so we just list them
                    results.add(Instance(domain)) 
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results
    }

    suspend fun fetchScribeInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("scribe")
        return if (fetched.isEmpty()) getMediumDefaults() else fetched
    }

    suspend fun fetchBiblioReadsInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("biblioReads")
        return if (fetched.isEmpty()) getBiblioReadsDefaults() else fetched
    }

    suspend fun fetchDumbInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("dumb")
        return if (fetched.isEmpty()) getDumbDefaults() else fetched
    }

    suspend fun fetchGotHubInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("gothub")
        return if (fetched.isEmpty()) getGotHubDefaults() else fetched
    }

    suspend fun fetchAnonymousOverflowInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("anonymousOverflow")
        return if (fetched.isEmpty()) getAnonymousOverflowDefaults() else fetched
    }

    suspend fun fetchRuralDictionaryInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("ruralDictionary")
        return if (fetched.isEmpty()) getRuralDictionaryDefaults() else fetched
    }

    suspend fun fetchRimgoInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("rimgo")
        return if (fetched.isEmpty()) getRimgoDefaults() else fetched
    }

    suspend fun fetchIntellectualInstances(): List<Instance> {
        val fetched = fetchLibRedirectInstances("intellectual")
        return if (fetched.isEmpty()) getIntellectualDefaults() else fetched
    }

    suspend fun fetchPriviblurInstances(): List<Instance> = withContext(Dispatchers.IO) {
        val instances = mutableListOf<Instance>()
        try {
            val connection = URL(PRIVIBLUR_INSTANCES_URL).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            // Matches |[domain](url)|
            val pattern = Pattern.compile("\\|\\[(.*?)\\]\\((.*?)\\)\\|")
            val matcher = pattern.matcher(html)
            
            while (matcher.find()) {
                val url = matcher.group(2) ?: continue
                val domain = try { URI(url).host } catch (e: Exception) { null } ?: continue
                if (isAlternativeInstance(domain)) {
                    instances.add(Instance(domain))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (instances.isEmpty()) {
            instances.addAll(getPriviblurDefaults())
        }
        return@withContext instances
    }

    private fun fetchNitterFromStatusPage(statusUrl: String): List<Instance> {
        val instances = mutableListOf<Instance>()
        try {
            val connection = URL(statusUrl).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Sidestep/1.0")
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            
            // Regex for Nitter health table
            // <td><a ...>domain</a></td> ... <td><span ...>emoji</span></td> ... <td>uptime%</td>
            // Each row starts with <tr> and contains columns. 
            // We'll split by <tr> to process row by row more safely.
            val rows = html.split("<tr>")
            for (row in rows) {
                if (!row.contains("nitter")) continue
                
                // Handle HTML entities like &#x2F;
                val domainMatcher = Pattern.compile("href=\"https?(?::|&#[xX]3[aA];)(?:&#[xX]2[fF];|/){2}([a-z0-9.-]+\\.[a-z]{2,})\"", Pattern.CASE_INSENSITIVE).matcher(row)
                val domain = if (domainMatcher.find()) {
                    domainMatcher.group(1) ?: continue
                } else {
                    // Fallback to simple domain match if href fails
                    val simpleDomainMatcher = Pattern.compile(">([a-z0-9.-]+\\.nitter\\.[a-z]{2,})<", Pattern.CASE_INSENSITIVE).matcher(row)
                    if (simpleDomainMatcher.find()) simpleDomainMatcher.group(1) ?: continue else continue
                }
                
                if (!isAlternativeInstance(domain)) continue
                
                val healthy = row.contains("aria-label=\"healthy\"") || row.contains("✅")
                val unhealthy = row.contains("aria-label=\"unhealthy\"") || row.contains("❌")
                val health = if (healthy) "Healthy" else if (unhealthy) "Down" else "Issues"
                
                // The uptime % is usually in the 6th column (td). Let's extract all percentages.
                // We'll filter for percentages that look like uptime (usually > 50%) if possible,
                // or just take the last one in the row which is often the "All Time %".
                val pctMatcher = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?%)").matcher(row)
                val percentages = mutableListOf<String>()
                while (pctMatcher.find()) {
                    percentages.add(pctMatcher.group(1))
                }
                
                val uptime = if (percentages.isNotEmpty()) percentages.last() else null
                
                instances.add(Instance(domain, uptime = uptime, uptime7d = uptime, health = health))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return instances
    }

    private fun fetchFromStatusPage(statusUrl: String): List<Instance> {
        val instances = mutableListOf<Instance>()
        try {
            val connection = URL(statusUrl).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            
            val domainPattern = Pattern.compile("([a-z0-9.-]+\\.[a-z]{2,})", Pattern.CASE_INSENSITIVE)
            val matcher = domainPattern.matcher(html)
            
            val seenDomains = mutableSetOf<String>()
            
            while (matcher.find()) {
                val domain = matcher.group(1)?.lowercase() ?: continue
                if (isAlternativeInstance(domain) && !seenDomains.contains(domain)) {
                    // Look ahead for percentages
                    val lookAhead = html.substring(matcher.end(), (matcher.end() + 200).coerceAtMost(html.length))
                    
                    // Match multiple percentages if they exist (common in status pages: 24h, 7d, 30d)
                    val pctMatcher = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?%)").matcher(lookAhead)
                    val percentages = mutableListOf<String>()
                    while (pctMatcher.find()) {
                        percentages.add(pctMatcher.group(1))
                    }

                    val uptime = if (percentages.isNotEmpty()) percentages[0] else null
                    val uptime7d = if (percentages.size > 1) percentages[1] else null
                    
                    // Check for health keywords
                    val health = when {
                        lookAhead.contains("Healthy", ignoreCase = true) || lookAhead.contains("Up", ignoreCase = true) -> "Healthy"
                        lookAhead.contains("Down", ignoreCase = true) || lookAhead.contains("Dead", ignoreCase = true) -> "Down"
                        lookAhead.contains("Slow", ignoreCase = true) || lookAhead.contains("Issues", ignoreCase = true) -> "Issues"
                        else -> null
                    }
                    
                    instances.add(Instance(domain, uptime = uptime, uptime7d = uptime7d, health = health))
                    seenDomains.add(domain)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return instances
    }

    private fun fetchNitterFromWiki(): List<Instance> {
        val instances = mutableListOf<Instance>()
        try {
            val connection = URL(NITTER_WIKI_URL).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val pattern = Pattern.compile("\\[([a-z0-9.-]+\\.[a-z]{2,})\\]\\(https?://", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            
            while (matcher.find()) {
                val domain = matcher.group(1) ?: continue
                if (isAlternativeInstance(domain)) {
                    instances.add(Instance(domain))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return instances
    }

    private fun isAlternativeInstance(domain: String): Boolean {
        val low = domain.lowercase()
        val exclusions = setOf("github", "ssllabs", "d420.de", "google", "cloudflare", "uptimerobot", "statuspage", "favicon", "gravatar")
        if (exclusions.any { low.contains(it) }) return false
        
        return low.contains("nitter") || low.contains("redlib") || low.contains("reddit") || 
               low.contains("lr.") || low.contains("rl.") || low == "xcancel.com" || 
               low == "safereddit.com" || low == "librdit.com" || low == "twiiit.com" ||
               low.contains("invidious") || low.contains("inv.") || low == "yewtu.be" ||
               low.contains("piped") || low == "pipedapi.kavin.rocks" ||
                low.contains("libremdb") || low == "imdb.rivo.cc" ||
                low.contains("scribe.rip") || low == "scribe.nixnet.services" ||
                low.contains("wikiless") || low.contains("biblioreads") ||
                low.contains("dumb") || low.contains("gothub") || low.contains("anonymousoverflow") || low.contains("overflow.") ||
                low.contains("priviblur") || low.contains("pb.") || low == "tb.opnxng.com" || low.contains("tumblr") ||
                low.contains("ruraldictionary") || low == "rd.vern.cc" || low == "rd.bloat.cat" || low.contains("urbandictionary") ||
                low.contains("rimgo") || low == "imgur.artemislena.eu" || low.contains("imgur") ||
                low.contains("intellectual")
    }
}
