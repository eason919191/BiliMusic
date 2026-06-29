package com.bili.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * 专门加载B站图片的组件，自动添加Referer头
 */
@Composable
fun BiliAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true
) {
    val url = model?.takeIf { it.isNotBlank() } ?: run {
        // No URL - show placeholder
        Box(modifier = modifier.background(Color.LightGray.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.Gray)
        }
        return
    }

    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(url)
        .crossfade(crossfade)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .addHeader("Referer", "https://www.bilibili.com/")
        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .build()

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
