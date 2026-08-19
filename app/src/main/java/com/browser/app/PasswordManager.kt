package com.browser.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class SavedCredential(
    val domain: String,
    val username: String,
    val password: String,
    val timestamp: Long = System.currentTimeMillis()
)

object PasswordManager {

    private const val PREFS = "novabrowser_passwords"
    private const val KEY_CREDENTIALS = "credentials"

    fun domainFromUrl(url: String): String {
        return try {
            Uri.parse(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }

    fun save(ctx: Context, url: String, username: String, password: String) {
        if (username.isBlank() || password.isBlank()) return
        val domain = domainFromUrl(url)
        val list = getAll(ctx).toMutableList()
        list.removeAll { it.domain == domain && it.username == username }
        list.add(0, SavedCredential(domain, username, password))
        saveList(ctx, list)
    }

    fun remove(ctx: Context, domain: String, username: String) {
        val list = getAll(ctx).toMutableList()
        list.removeAll { it.domain == domain && it.username == username }
        saveList(ctx, list)
    }

    fun getForDomain(ctx: Context, url: String): List<SavedCredential> {
        val domain = domainFromUrl(url)
        return getAll(ctx).filter { it.domain == domain }
    }

    fun hasCredentialsFor(ctx: Context, url: String): Boolean {
        return getForDomain(ctx, url).isNotEmpty()
    }

    fun getAll(ctx: Context): List<SavedCredential> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CREDENTIALS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SavedCredential(
                    o.getString("domain"),
                    o.getString("username"),
                    o.getString("password"),
                    o.optLong("ts")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveList(ctx: Context, list: List<SavedCredential>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("domain", it.domain)
                put("username", it.username)
                put("password", it.password)
                put("ts", it.timestamp)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CREDENTIALS, arr.toString()).apply()
    }
}
