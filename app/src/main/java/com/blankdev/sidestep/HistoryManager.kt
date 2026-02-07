package com.blankdev.sidestep

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages URL processing history with configurable retention policies.
 * Stores entries locally using SharedPreferences with JSON serialization.
 * All operations are performed on Dispatchers.IO to avoid blocking the main thread.
 */
object HistoryManager {
    private const val PREFS_NAME = "url_history"
    private const val KEY_HISTORY = "history_list"
    private const val MAX_HISTORY_SIZE = 100
    
    private val mutex = Mutex()
    
    // History Retention Constants
    const val KEY_HISTORY_RETENTION = "history_retention"
    const val KEY_HISTORY_DAYS = "history_days"
    const val KEY_HISTORY_ITEMS = "history_items"
    
    private const val LEGACY_PARTS_MIN_SIZE = 3
    private const val HOURS_IN_DAY = 24
    private const val MINUTES_IN_HOUR = 60
    private const val SECONDS_IN_MINUTE = 60
    private const val MILLIS_IN_SECOND = 1000L
    
    const val DEFAULT_HISTORY_DAYS = 14
    const val DEFAULT_HISTORY_ITEMS = 25
    const val DEFAULT_RETENTION_MODE = "auto"

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Add a processed URL to history
     */
    suspend fun addToHistory(context: Context, originalUrl: String, cleanedUrl: String, processedUrl: String) {
        val entry = HistoryEntry(
            originalUrl = originalUrl,
            cleanedUrl = cleanedUrl,
            processedUrl = processedUrl,
            timestamp = System.currentTimeMillis()
        )
        addToHistory(context, entry)
    }

    /**
     * Add a pre-constructed HistoryEntry to history
     */
    suspend fun addToHistory(context: Context, entry: HistoryEntry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val history = getHistoryInternal(context).toMutableList()
            val normalizedOriginal = UrlCleaner.ensureProtocol(entry.originalUrl)
            
            // Remove duplicate if exists by normalized original URL
            // This ensures google.com and https://google.com are treated as the same entry
            history.removeAll { UrlCleaner.ensureProtocol(it.originalUrl) == normalizedOriginal }
            
            // Add to front
            history.add(0, entry)
            
            saveHistoryInternal(context, history)
        }
    }
    
    /**
     * Update an existing entry (e.g. with preview data) without changing its position
     */
    suspend fun updateEntry(context: Context, updatedEntry: HistoryEntry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val history = getHistoryInternal(context).toMutableList()
            val index = history.indexOfFirst { it.timestamp == updatedEntry.timestamp && it.originalUrl == updatedEntry.originalUrl }
            
            if (index != -1) {
                history[index] = updatedEntry
                saveHistoryInternal(context, history)
            }
        }
    }
    
    /**
     * Get history list, sorted by latest first
     */
    suspend fun getHistory(context: Context): List<HistoryEntry> = mutex.withLock {
        withContext(Dispatchers.IO) {
            getHistoryInternal(context)
        }
    }

    /**
     * Synchronous version of getHistory for legacy support or strict UI requirements.
     * WARNING: This performs IO on the calling thread. Avoid calling on Main thread.
     */
    fun getHistorySync(context: Context): List<HistoryEntry> = synchronized(this) {
        getHistoryInternal(context)
    }
    
    private fun getHistoryInternal(context: Context): List<HistoryEntry> {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_HISTORY, "") ?: ""
        
        if (jsonString.isEmpty()) return emptyList()
        
        val list = try {
            // Try parsing as JSON Array first
            val jsonArray = org.json.JSONArray(jsonString)
            val mutableList = mutableListOf<HistoryEntry>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                mutableList.add(HistoryEntry(
                    originalUrl = obj.getString("originalUrl"),
                    cleanedUrl = if (obj.has("cleanedUrl")) obj.getString("cleanedUrl") else obj.getString("originalUrl"),
                    processedUrl = obj.getString("processedUrl"),
                    timestamp = obj.getLong("timestamp"),
                    title = if (obj.has("title")) obj.getString("title") else null,
                    author = if (obj.has("author")) obj.getString("author") else null,
                    description = if (obj.has("description")) obj.getString("description") else null,
                    isPreviewFetched = if (obj.has("isPreviewFetched")) obj.getBoolean("isPreviewFetched") else false,
                    postedTimestamp = if (obj.has("postedTimestamp")) obj.getLong("postedTimestamp") else null,
                    unshortenedUrl = if (obj.has("unshortenedUrl")) obj.getString("unshortenedUrl") else null
                ))
            }
            mutableList
        } catch (e: org.json.JSONException) {
            // Fallback: Try parsing legacy pipe-delimited format
            android.util.Log.i("HistoryManager", "JSON parse failed, attempting legacy format", e)
            try {
                val legacyList = jsonString.split("\n")
                    .filter { it.isNotEmpty() }
                    .mapNotNull { line ->
                        val parts = line.split("|")
                        if (parts.size >= LEGACY_PARTS_MIN_SIZE) {
                            HistoryEntry(
                                originalUrl = parts[1],
                                cleanedUrl = parts[1],
                                processedUrl = parts[2],
                                timestamp = parts[0].toLongOrNull() ?: 0L
                            )
                        } else null
                    }
                // Determine if we should migrate immediately
                if (legacyList.isNotEmpty()) {
                    saveHistoryInternal(context, legacyList) // Migrate to JSON
                    prefs.edit(commit = true) { remove("history") } // Purge legacy key forever
                }
                legacyList
            } catch (ignored: Exception) {
                emptyList()
            }
        }
        
        return list.sortedByDescending { it.timestamp }
    }

    private fun saveHistoryInternal(context: Context, history: List<HistoryEntry>) {
        // Limit size
        val entriesToSave = if (history.size > MAX_HISTORY_SIZE) {
            history.take(MAX_HISTORY_SIZE)
        } else {
            history
        }
        
        // Serialize to JSON
        val jsonArray = org.json.JSONArray()
        entriesToSave.forEach { entry ->
            val obj = org.json.JSONObject()
            obj.put("originalUrl", entry.originalUrl)
            obj.put("cleanedUrl", entry.cleanedUrl)
            obj.put("processedUrl", entry.processedUrl)
            obj.put("timestamp", entry.timestamp)
            entry.title?.let { obj.put("title", it) }
            entry.author?.let { obj.put("author", it) }
            entry.description?.let { obj.put("description", it) }
            obj.put("isPreviewFetched", entry.isPreviewFetched)
            entry.postedTimestamp?.let { obj.put("postedTimestamp", it) }
            entry.unshortenedUrl?.let { obj.put("unshortenedUrl", it) }
            jsonArray.put(obj)
        }
        
        val prefs = getPrefs(context)
        prefs.edit(commit = true) { putString(KEY_HISTORY, jsonArray.toString()) }
    }
    
    /**
     * Remove a specific entry from history
     */
    suspend fun removeFromHistory(context: Context, entry: HistoryEntry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val history = getHistoryInternal(context).toMutableList()
            history.removeAll { it.originalUrl == entry.originalUrl && it.timestamp == entry.timestamp }
            saveHistoryInternal(context, history)
        }
    }
    
    /**
     * Clear all history
     */
    suspend fun clearHistory(context: Context) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val prefs = getPrefs(context)
            prefs.edit(commit = true) { 
                remove(KEY_HISTORY)
                remove("history") // Also clear legacy key to prevent resurrection
            }
        }
    }
    
    /**
     * Apply history retention policy
     */
    suspend fun applyRetentionPolicy(context: Context) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val retentionMode = prefs.getString(KEY_HISTORY_RETENTION, DEFAULT_RETENTION_MODE) ?: DEFAULT_RETENTION_MODE
            
            when (retentionMode) {
                "never" -> {
                    val hPrefs = getPrefs(context)
                    hPrefs.edit(commit = true) { 
                        remove(KEY_HISTORY)
                        remove("history") // Also clear legacy key
                    }
                }
                "forever" -> { /* Do nothing */ }
                "auto" -> {
                    val days = prefs.getString(KEY_HISTORY_DAYS, DEFAULT_HISTORY_DAYS.toString())?.toIntOrNull() ?: DEFAULT_HISTORY_DAYS
                    val items = prefs.getString(KEY_HISTORY_ITEMS, DEFAULT_HISTORY_ITEMS.toString())?.toIntOrNull() ?: DEFAULT_HISTORY_ITEMS
                    
                    val history = getHistoryInternal(context).toMutableList()
                    var changed = false
                    
                    // Filter by days
                    val cutoffTime = System.currentTimeMillis() - (days * HOURS_IN_DAY * MINUTES_IN_HOUR * SECONDS_IN_MINUTE * MILLIS_IN_SECOND)
                    if (history.any { it.timestamp < cutoffTime }) {
                        history.removeAll { it.timestamp < cutoffTime }
                        changed = true
                    }
                    
                    // Filter by items
                    if (history.size > items) {
                        val trimmed = history.take(items)
                        history.clear()
                        history.addAll(trimmed)
                        changed = true
                    }
                    
                    if (changed) {
                        saveHistoryInternal(context, history)
                    }
                }
            }
        }
    }
    
    data class HistoryEntry(
        val originalUrl: String,
        val cleanedUrl: String,
        val processedUrl: String,
        val timestamp: Long,
        val title: String? = null,
        val author: String? = null,
        val description: String? = null,
        val isPreviewFetched: Boolean = false,
        val postedTimestamp: Long? = null,
        val unshortenedUrl: String? = null
    )
}
