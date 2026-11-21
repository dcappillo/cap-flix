package com.derek.capflix.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.derek.capflix.CapFlixApp
import com.derek.capflix.data.Item
import com.derek.capflix.ui.theme.Bolt
import com.derek.capflix.ui.theme.Bungee
import com.derek.capflix.ui.theme.Cream
import com.derek.capflix.ui.theme.Muted
import com.derek.capflix.ui.theme.Panel
import com.derek.capflix.ui.theme.PanelHigh

@Composable
fun Wordmark(modifier: Modifier = Modifier, size: Int = 22) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Cream)) { append("CAP") }
            withStyle(SpanStyle(color = Bolt)) { append("FLIX") }
        },
        fontFamily = Bungee, fontSize = size.sp, modifier = modifier,
    )
}

/** Image with a calm placeholder while it loads, and the title if there's no art. */
@Composable
fun Art(url: String?, fallback: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    Box(modifier.background(Panel)) {
        if (url == null) {
            Text(
                fallback, style = MaterialTheme.typography.titleSmall, color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(8.dp), maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
        } else {
            SubcomposeAsyncImage(
                model = url, contentDescription = fallback, contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = { Shimmer(Modifier.fillMaxSize()) },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(fallback, style = MaterialTheme.typography.titleSmall, color = Muted,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                    }
                },
            )
        }
    }
}

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.alpha(a).background(PanelHigh))
}

@Composable
fun PosterCard(item: Item, width: Dp = 112.dp, onClick: () -> Unit) {
    val api = CapFlixApp.api
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))) {
            Art(api.poster(item), item.name, Modifier.fillMaxSize())
            if (item.userData?.played == true) {
                Icon(Icons.Filled.CheckCircle, null, tint = Bolt,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).background(Color.Black.copy(.55f), CircleShape))
            } else if ((item.userData?.unplayedCount ?: 0) > 0 && item.isSeries) {
                Text("${item.userData!!.unplayedCount}", style = MaterialTheme.typography.labelSmall, color = Color.Black,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).background(Bolt, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
            }
            if (item.progress > 0f) ProgressBar(item.progress, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** 16:9 card with a play badge and progress: Continue Watching, episodes. */
@Composable
fun LandscapeCard(item: Item, width: Dp = 230.dp, showSeries: Boolean = true, onClick: () -> Unit) {
    val api = CapFlixApp.api
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))) {
            Art(api.landscape(item), item.name, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.55f)))))
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(42.dp).background(Color.Black.copy(.45f), CircleShape)
                    .border(1.5.dp, Color.White.copy(.85f), CircleShape).padding(6.dp))
            if (item.progress > 0f) ProgressBar(item.progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        val title = if (item.isEpisode && showSeries) item.seriesName ?: item.name else item.name
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.isEpisode) {
            Text(
                listOf(item.episodeCode, if (showSeries) item.name else null).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            remainingLabel(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
    }
}

fun remainingLabel(item: Item): String? {
    val len = item.runTimeTicks ?: return null
    val pos = item.userData?.positionTicks ?: 0
    if (pos <= 0) return item.runtimeMinutes?.let { formatMinutes(it) }
    val left = ((len - pos) / 600_000_000L).toInt().coerceAtLeast(1)
    return "${formatMinutes(left)} left"
}

fun formatMinutes(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

@Composable
fun ProgressBar(p: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(.25f))) {
        Box(Modifier.fillMaxWidth(p).height(3.dp).background(Bolt))
    }
}

@Composable
fun RowSection(
    title: String,
    items: List<Item>,
    landscape: Boolean = false,
    onMore: (() -> Unit)? = null,
    onClick: (Item) -> Unit,
) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onMore != null) TextButton(onClick = onMore) { Text("See all", color = Muted) }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                if (landscape) LandscapeCard(item) { onClick(item) } else PosterCard(item) { onClick(item) }
            }
        }
    }
}

@Composable
fun Badge(text: String, highlight: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.labelSmall,
        color = if (highlight) Color.Black else Cream,
        modifier = Modifier
            .background(if (highlight) Bolt else Color.Transparent, RoundedCornerShape(3.dp))
            .border(1.dp, if (highlight) Bolt else Muted, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Bolt) }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Can't reach your library", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRetry) { Text("Try again", color = Bolt, fontWeight = FontWeight.Bold) }
    }
}

fun friendlyError(t: Throwable): String = when {
    t is com.derek.capflix.data.ApiException && t.code == 401 -> "Your sign-in expired. Sign in again from Settings."
    t is java.net.UnknownHostException || t is java.net.ConnectException || t is java.net.SocketTimeoutException ->
        "Is Tailscale on, and is the PC awake with Jellyfin running?"
    else -> t.message ?: "Something went wrong."
}
