package com.stremio.mobile.sense

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject

class SenseAndroidDownloads(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = appContext.getSharedPreferences("sense_downloads", Context.MODE_PRIVATE)

    data class Item(val downloadId: Long, val name: String, val contentId: String?, val videoId: String?, val sourceUrl: String, val status: Int, val downloadedBytes: Long, val totalBytes: Long, val localUri: String?, val reason: Int)

    fun enqueue(sourceUrl: String, name: String, contentId: String? = null, videoId: String? = null): Long {
        require(sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")) { "Android downloads require a resolved HTTP(S) stream" }
        val fileName = safeFileName(name) + ".mp4"
        val request = DownloadManager.Request(Uri.parse(sourceUrl))
            .setTitle(name.ifBlank { "Stremio download" })
            .setDescription("Available offline in Stremio")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_MOVIES, "Stremio/$fileName")
        val id = manager.enqueue(request); remember(id, name, contentId, videoId, sourceUrl); return id
    }

    fun items(): List<Item> = records().mapNotNull { record ->
        val id = record.optLong("downloadId", -1L); if (id < 0) return@mapNotNull null
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return@mapNotNull null
            fun int(column: String) = cursor.getInt(cursor.getColumnIndexOrThrow(column))
            fun long(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
            fun string(column: String): String? = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getString)
            Item(id, record.optString("name", "Download"), record.optString("contentId").takeIf(String::isNotBlank), record.optString("videoId").takeIf(String::isNotBlank), record.optString("sourceUrl"), int(DownloadManager.COLUMN_STATUS), long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR), long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES), string(DownloadManager.COLUMN_LOCAL_URI), int(DownloadManager.COLUMN_REASON))
        }
    }.sortedByDescending { it.downloadId }

    fun remove(id: Long) { manager.remove(id); save(records().filter { it.optLong("downloadId") != id }) }
    fun open(item: Item) {
        val uri = manager.getUriForDownloadedFile(item.downloadId) ?: item.localUri?.let(Uri::parse) ?: return
        appContext.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "video/*"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) })
    }
    private fun remember(id: Long, name: String, contentId: String?, videoId: String?, sourceUrl: String) {
        val next = records().toMutableList(); next += JSONObject().apply { put("downloadId", id); put("name", name); put("contentId", contentId ?: ""); put("videoId", videoId ?: ""); put("sourceUrl", sourceUrl) }; save(next.takeLast(500))
    }
    private fun records(): List<JSONObject> = runCatching { val array = JSONArray(prefs.getString("items", "[]") ?: "[]"); List(array.length()) { array.getJSONObject(it) } }.getOrDefault(emptyList())
    private fun save(items: List<JSONObject>) { val array = JSONArray(); items.forEach(array::put); prefs.edit().putString("items", array.toString()).apply() }
    companion object { fun safeFileName(value: String): String = value.replace(Regex("[^a-zA-Z0-9._ -]+"), "_").trim().take(120).ifBlank { "stremio-download" } }
}
