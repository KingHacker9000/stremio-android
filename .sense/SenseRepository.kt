package com.stremio.mobile.sense

import android.content.Context
import com.stremio.mobile.data.model.CatalogItem
import org.json.JSONArray
import org.json.JSONObject

class SenseRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("sense_history", Context.MODE_PRIVATE)
    private val index: SenseIndex by lazy { appContext.assets.open("sense/sense.index.bin").use { SenseIndex.fromBytes(it.readBytes()) } }

    fun similar(id: String, limit: Int = 20): List<SenseHit> = runCatching { index.similarDiverse(id, resultLimit = limit) }.getOrDefault(emptyList())
    fun recommendations(extraSeeds: List<String> = emptyList(), limit: Int = 20): List<SenseHit> = runCatching { index.recommendFromHistory((historyIds() + extraSeeds).distinct(), resultLimit = limit) }.getOrDefault(emptyList())
    fun recommendationItems(extraSeeds: List<String> = emptyList(), limit: Int = 20): List<CatalogItem> = recommendations(extraSeeds, limit).map(::toCatalogItem)
    fun similarItems(id: String, limit: Int = 20): List<CatalogItem> = similar(id, limit).map(::toCatalogItem)

    private fun toCatalogItem(hit: SenseHit): CatalogItem = CatalogItem(
        id = hit.id,
        type = hit.type,
        name = hit.name,
        poster = if (hit.id.startsWith("tt")) "https://images.metahub.space/poster/medium/${hit.id}/img" else null,
        background = null,
        releaseInfo = null,
        imdbRating = null,
    )

    fun record(id: String, kind: String = "completed") {
        if (id.isBlank()) return
        val history = history().filterNot { it.optString("id") == id }.toMutableList()
        history += JSONObject().apply { put("id", id); put("kind", kind); put("time", System.currentTimeMillis()) }
        val array = JSONArray(); history.takeLast(2000).forEach(array::put)
        prefs.edit().putString("history", array.toString()).apply()
    }
    private fun historyIds(): List<String> = history().mapNotNull { it.optString("id").takeIf(String::isNotBlank) }
    private fun history(): List<JSONObject> = runCatching { val array = JSONArray(prefs.getString("history", "[]") ?: "[]"); List(array.length()) { array.getJSONObject(it) } }.getOrDefault(emptyList())
}
