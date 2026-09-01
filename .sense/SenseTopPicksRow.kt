package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.sense.SenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SenseTopPicksRow(seedIds: List<String>, onOpenDetails: (CatalogItem) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { SenseRepository(context) }
    var recommendations by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    val key = seedIds.joinToString("|")
    LaunchedEffect(key) { recommendations = withContext(Dispatchers.Default) { repository.recommendationItems(seedIds, 20) } }
    if (recommendations.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Top Picks for You", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            items(recommendations, key = { it.id }) { item ->
                Column(modifier = Modifier.width(118.dp).clickable { onOpenDetails(item) }) {
                    AsyncImage(model = item.poster, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.width(118.dp).height(174.dp).clip(RoundedCornerShape(12.dp)))
                    Text(item.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
