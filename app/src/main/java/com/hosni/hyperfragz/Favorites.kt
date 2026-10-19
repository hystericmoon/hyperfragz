package com.hosni.hyperfragz

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** Saved perfumes, newest first, kept on the phone (no account). */
class Favorites(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    val items = mutableStateListOf<Hit>().apply { addAll(load()) }

    fun contains(url: String) = items.any { samePath(it.url, url) }

    fun toggle(d: Detail) {
        val i = items.indexOfFirst { samePath(it.url, d.url) }
        if (i >= 0) items.removeAt(i) else items.add(0, d.toHit())
        save()
    }

    /** Details fill in as the page loads; keep the saved card up to date. */
    fun refresh(d: Detail) {
        val i = items.indexOfFirst { samePath(it.url, d.url) }
        if (i < 0 || d.name.isEmpty()) return
        val fresh = d.toHit()
        if (items[i] != fresh) {
            items[i] = fresh
            save()
        }
    }

    private fun Detail.toHit() = Hit(
        url = url,
        name = name,
        brand = brand,
        year = year,
        gender = when (audience?.lowercase()) {
            "women" -> "female"
            "men" -> "male"
            "women and men" -> "unisex"
            else -> null
        },
        thumb = image,
    )

    private fun save() {
        val a = JSONArray()
        items.forEach { h ->
            a.put(JSONObject().put("url", h.url).put("name", h.name).put("brand", h.brand)
                .put("year", h.year).put("gender", h.gender).put("thumb", h.thumb))
        }
        prefs.edit().putString("items", a.toString()).apply()
    }

    private fun load(): List<Hit> = runCatching {
        val a = JSONArray(prefs.getString("items", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Hit(o.optString("url"), o.optString("name"), o.optString("brand"), o.optStr("year"), o.optStr("gender"), o.optStr("thumb"))
        }
    }.getOrDefault(emptyList())

    private fun samePath(a: String, b: String) =
        runCatching { android.net.Uri.parse(a).path == android.net.Uri.parse(b).path }.getOrDefault(a == b)
}
