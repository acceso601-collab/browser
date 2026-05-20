package com.browser.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class HistoryItem(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class FavoriteItem(
    val url: String,
    val title: String
)

object StorageManager {

    private const val PREFS = "novabrowser"
    private const val KEY_HISTORY = "history"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_THEME = "theme"
    private const val MAX_HISTORY = 200

    const val THEME_DARK = 0
    const val THEME_LIGHT = 1
    const val THEME_FORCED_DARK = 2

    // ── HISTORY ───────────────────────────────────────────────────────────

    fun addHistory(ctx: Context, url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val list = getHistory(ctx).toMutableList()
        // Quitar duplicado si ya existe
        list.removeAll { it.url == url }
        list.add(0, HistoryItem(url, title.ifEmpty { url }))
        if (list.size > MAX_HISTORY) list.subList(MAX_HISTORY, list.size).clear()
        saveList(ctx, KEY_HISTORY, list.map {
            JSONObject().apply {
                put("url", it.url); put("title", it.title); put("ts", it.timestamp)
            }
        })
    }

    fun getHistory(ctx: Context): List<HistoryItem> {
        return loadList(ctx, KEY_HISTORY).map {
            HistoryItem(it.getString("url"), it.getString("title"), it.optLong("ts"))
        }
    }

    fun clearHistory(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_HISTORY).apply()
    }

    fun formatDate(ts: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - ts
        return when {
            diff < 60_000 -> "Hace un momento"
            diff < 3_600_000 -> "Hace ${diff / 60_000} min"
            diff < 86_400_000 -> "Hace ${diff / 3_600_000} h"
            else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))
        }
    }

    // ── FAVORITES ─────────────────────────────────────────────────────────

    fun addFavorite(ctx: Context, url: String, title: String) {
        val list = getFavorites(ctx).toMutableList()
        if (list.any { it.url == url }) return
        list.add(0, FavoriteItem(url, title.ifEmpty { url }))
        saveList(ctx, KEY_FAVORITES, list.map {
            JSONObject().apply { put("url", it.url); put("title", it.title) }
        })
    }

    fun removeFavorite(ctx: Context, url: String) {
        val list = getFavorites(ctx).toMutableList()
        list.removeAll { it.url == url }
        saveList(ctx, KEY_FAVORITES, list.map {
            JSONObject().apply { put("url", it.url); put("title", it.title) }
        })
    }

    fun getFavorites(ctx: Context): List<FavoriteItem> {
        return loadList(ctx, KEY_FAVORITES).map {
            FavoriteItem(it.getString("url"), it.getString("title"))
        }
    }

    fun isFavorite(ctx: Context, url: String): Boolean {
        return getFavorites(ctx).any { it.url == url }
    }

    // ── THEME ─────────────────────────────────────────────────────────────

    fun saveTheme(ctx: Context, theme: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME, theme).apply()
    }

    fun getTheme(ctx: Context): Int {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, THEME_DARK)
    }

    // ── UTILS ─────────────────────────────────────────────────────────────

    private fun saveList(ctx: Context, key: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, arr.toString()).apply()
    }

    private fun loadList(ctx: Context, key: String): List<JSONObject> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { emptyList() }
    }

    // ── TABS ──────────────────────────────────────────────────────────────

    fun saveTabs(ctx: Context, tabs: List<Pair<String, String>>, activeIndex: Int) {
        val arr = JSONArray()
        tabs.forEach { (url, title) ->
            arr.put(JSONObject().apply { put("url", url); put("title", title) })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("saved_tabs", arr.toString())
            .putInt("active_tab", activeIndex)
            .apply()
    }

    fun loadSavedTabs(ctx: Context): Pair<List<Pair<String, String>>, Int> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString("saved_tabs", "[]") ?: "[]"
        val activeIndex = prefs.getInt("active_tab", 0)
        return try {
            val arr = JSONArray(json)
            val list = (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                Pair(obj.getString("url"), obj.optString("title", ""))
            }.filter { it.first.isNotEmpty() }
            Pair(list, activeIndex)
        } catch (e: Exception) { Pair(emptyList(), 0) }
    }
}
