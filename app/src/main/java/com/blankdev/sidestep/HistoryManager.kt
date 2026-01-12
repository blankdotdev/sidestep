package com.blankdev.sidestep

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages URL processing history with configurable retention policies.
 * Stores entries locally using SharedPreferences with JSON serialization.
 * All operations are performed on Dispatchers.IO to avoid blocking the main thread.
 */
object HistoryManager {
    private const val PREFS_NAME = "url_history"
    private const val KEY_HISTORY = "history_list"
    private const val MAX_HISTORY_SIZE = 100
    
    // History Retention Constants
    const val KEY_HISTORY_RETENTION = "history_retention"
    const val KEY_HISTORY_DAYS = "history_days"
    const val KEY_HISTORY_ITEMS = "history_items"
    
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
    suspend fun addToHistory(context: Context, entry: HistoryEntry) = withContext(Dispatchers.IO) {
        val history = getHistoryInternal(context).toMutableList()
        
        // Remove duplicate if exists by original URL
        history.removeAll { it.originalUrl == entry.originalUrl }
        
        // Add to front
        history.add(0, entry)
        
        saveHistoryInternal(context, history)
    }
    
    /**
     * Update an existing entry (e.g. with preview data) without changing its position
     */
    suspend fun updateEntry(context: Context, updatedEntry: HistoryEntry) = withContext(Dispatchers.IO) {
        val history = getHistoryInternal(context).toMutableList()
        val index = history.indexOfFirst { it.timestamp == updatedEntry.timestamp && it.originalUrl == updatedEntry.originalUrl }
        
        if (index != -1) {
            history[index] = updatedEntry
            saveHistoryInternal(context, history)
        }
    }
    
    /**
     * Get history list, sorted by latest first
     */
    suspend fun getHistory(context: Context): List<HistoryEntry> = withContext(Dispatchers.IO) {
        getHistoryInternal(context)
    }

    /**
     * Synchronous version of getHistory for legacy support or strict UI requirements.
     * WARNING: This performs IO on the calling thread. Avoid calling on Main thread.
     */
    fun getHistorySync(context: Context): List<HistoryEntry> {
        return getHistoryInternal(context)
    }
    
    private fun getHistoryInternal(context: Context): List<HistoryEntry> {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_HISTORY, "") ?: ""
        
        if (jsonString.isEmpty()) return emptyList()
        
        try {
            // Try parsing as JSON Array first
            val jsonArray = org.json.JSONArray(jsonString)
            val list = mutableListOf<HistoryEntry>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(HistoryEntry(
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
            return list
        } catch (e: Exception) {
            // Fallback: Try parsing legacy pipe-delimited format
            return try {
                val list = jsonString.split("\n")
                    .filter { it.isNotEmpty() }
                    .mapNotNull { line ->
                        val parts = line.split("|")
                        if (parts.size >= 3) {
                            HistoryEntry(
                                originalUrl = parts[1],
                                cleanedUrl = parts[1],
                                processedUrl = parts[2],
                                timestamp = parts[0].toLongOrNull() ?: 0L
                            )
                        } else null
                    }
                // Determine if we should migrate immediately
                if (list.isNotEmpty()) {
                    saveHistoryInternal(context, list) // Migrate to JSON
                }
                list
            } catch (e2: Exception) {
                emptyList()
            }
        }
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
        prefs.edit { putString(KEY_HISTORY, jsonArray.toString()) }
    }
    
    /**
     * Remove a specific entry from history
     */
    suspend fun removeFromHistory(context: Context, entry: HistoryEntry) = withContext(Dispatchers.IO) {
        val history = getHistoryInternal(context).toMutableList()
        history.removeAll { it.originalUrl == entry.originalUrl && it.timestamp == entry.timestamp }
        saveHistoryInternal(context, history)
    }
    
    /**
     * Clear all history
     */
    suspend fun clearHistory(context: Context) = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        prefs.edit { remove(KEY_HISTORY) }
    }
    
    /**
     * Apply history retention policy
     */
    suspend fun applyRetentionPolicy(context: Context) = withContext(Dispatchers.IO) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val retentionMode = prefs.getString(KEY_HISTORY_RETENTION, DEFAULT_RETENTION_MODE) ?: DEFAULT_RETENTION_MODE
        
        when (retentionMode) {
            "never" -> clearHistory(context)
            "forever" -> { /* Do nothing */ }
            "auto" -> {
                val days = prefs.getString(KEY_HISTORY_DAYS, DEFAULT_HISTORY_DAYS.toString())?.toIntOrNull() ?: DEFAULT_HISTORY_DAYS
                val items = prefs.getString(KEY_HISTORY_ITEMS, DEFAULT_HISTORY_ITEMS.toString())?.toIntOrNull() ?: DEFAULT_HISTORY_ITEMS
                
                val history = getHistoryInternal(context).toMutableList()
                var changed = false
                
                // Filter by days
                val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
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
