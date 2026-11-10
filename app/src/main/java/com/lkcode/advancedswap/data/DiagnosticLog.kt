package com.lkcode.advancedswap.data

import android.content.Context
import com.lkcode.advancedswap.domain.DiagnosticEntry
import org.json.JSONArray
import org.json.JSONObject

class DiagnosticLog(context: Context) {
    private val preferences = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)

    @Synchronized
    fun add(entry: DiagnosticEntry) {
        val entries = read().takeLast(29) + entry
        val array = JSONArray()
        entries.forEach { array.put(JSONObject()
            .put("timestamp", it.timestamp)
            .put("operation", it.operation)
            .put("success", it.success)
            .put("message", it.message)) }
        preferences.edit().putString("entries", array.toString()).apply()
    }

    @Synchronized
    fun read(): List<DiagnosticEntry> = try {
        val array = JSONArray(preferences.getString("entries", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            DiagnosticEntry(item.getLong("timestamp"), item.getString("operation"), item.getBoolean("success"), item.getString("message"))
        }
    } catch (_: Exception) { emptyList() }
}

