package com.stremio.mobile.presentation.screens

import android.app.DownloadManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stremio.mobile.sense.SenseAndroidDownloads
import kotlinx.coroutines.delay

@Composable
fun SenseDownloadsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val downloads = remember { SenseAndroidDownloads(context) }
    var items by remember { mutableStateOf(downloads.items()) }
    LaunchedEffect(Unit) { while (true) { items = downloads.items(); delay(1200) } }
    Column(modifier = modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Downloads", color = Color.White)
        if (items.isEmpty()) Text("No offline media yet.", color = Color.LightGray)
        items.forEach { item ->
            val total = item.totalBytes.takeIf { it > 0 } ?: 0L
            val progress = if (total > 0) (item.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) else 0f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text(item.name, color = Color.White)
                Text(statusLabel(item.status, item.reason), color = Color.LightGray)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.status == DownloadManager.STATUS_SUCCESSFUL) Button(onClick = { downloads.open(item) }) { Text("Play Offline") }
                    Button(onClick = { downloads.remove(item.downloadId); items = downloads.items() }) { Text("Delete") }
                }
            }
        }
    }
}

private fun statusLabel(status: Int, reason: Int): String = when (status) {
    DownloadManager.STATUS_PENDING -> "Queued"
    DownloadManager.STATUS_RUNNING -> "Downloading"
    DownloadManager.STATUS_PAUSED -> "Paused"
    DownloadManager.STATUS_SUCCESSFUL -> "Downloaded"
    DownloadManager.STATUS_FAILED -> "Failed ($reason)"
    else -> "Unknown"
}
