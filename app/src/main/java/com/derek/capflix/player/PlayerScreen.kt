package com.derek.capflix.player

import android.app.Activity
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.derek.capflix.CapFlixApp
import com.derek.capflix.ui.settings.QUALITY_OPTIONS
import com.derek.capflix.ui.theme.Bolt
import com.derek.capflix.ui.theme.Cream
import com.derek.capflix.ui.theme.Muted
import com.derek.capflix.ui.theme.Panel
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

private enum class Sheet { None, Tracks, Settings }

@Composable
fun PlayerScreen(c: PlayerController, inPip: Boolean, onClose: () -> Unit) {
    if (c.finished) LaunchedEffect(Unit) { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { VLCVideoLayout(it).also { v -> c.attach(v) } }, modifier = Modifier.fillMaxSize())
        if (!inPip) PlayerOverlay(c, onClose)   // the PiP window shows only the picture
    }
}

@Composable
private fun PlayerOverlay(c: PlayerController, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    var controls by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var lastTouch by remember { mutableIntStateOf(0) }
    var seekFlash by remember { mutableStateOf<String?>(null) }
    var gestureHud by remember { mutableStateOf<Pair<ImageVector, Float>?>(null) }

    // Hide controls 4s after the last touch while playing.
    LaunchedEffect(controls, lastTouch, c.playing, sheet) {
        if (controls && c.playing && sheet == Sheet.None) { delay(4000); controls = false }
    }
    LaunchedEffect(seekFlash) { if (seekFlash != null) { delay(600); seekFlash = null } }
    LaunchedEffect(gestureHud) { if (gestureHud != null) { delay(900); gestureHud = null } }

    Box(Modifier.fillMaxSize()) {
        // Gestures: tap = controls, double-tap sides = ±10s, drag left = brightness, right = volume.
        val audio = remember { ctx.getSystemService(AudioManager::class.java) }
        var dragAcc by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controls = !controls; lastTouch++ },
                        onDoubleTap = { o ->
                            if (o.x < size.width / 2f) { c.seekBy(-10_000); seekFlash = "« 10s" } else { c.seekBy(10_000); seekFlash = "10s »" }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { o -> leftSide = o.x < size.width / 2f; dragAcc = 0f },
                        onVerticalDrag = { _, dy ->
                            dragAcc -= dy / size.height
                            if (leftSide && activity != null) {
                                val lp = activity.window.attributes
                                val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                lp.screenBrightness = (cur - dy / size.height).coerceIn(0.02f, 1f)
                                activity.window.attributes = lp
                                gestureHud = Icons.Filled.BrightnessMedium to lp.screenBrightness
                            } else if (audio != null) {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val step = (dragAcc * max * 1.5f).toInt()
                                if (step != 0) {
                                    val v = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + step).coerceIn(0, max)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                    dragAcc = 0f
                                    gestureHud = Icons.AutoMirrored.Filled.VolumeUp to v / max.toFloat()
                                }
                            }
                        },
                    )
                }
        )

        if (c.buffering && c.error == null) {
            CircularProgressIndicator(color = Bolt, modifier = Modifier.align(Alignment.Center).size(48.dp))
        }

        seekFlash?.let {
            Text(it, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.Center)
                .background(Color.Black.copy(.5f), RoundedCornerShape(30.dp)).padding(horizontal = 20.dp, vertical = 10.dp))
        }
        gestureHud?.let { (icon, v) ->
            Row(Modifier.align(Alignment.TopCenter).padding(top = 40.dp).background(Color.Black.copy(.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(120.dp).height(4.dp).background(Color.White.copy(.3f))) {
                    Box(Modifier.fillMaxWidth(v.coerceIn(0f, 1f)).height(4.dp).background(Bolt))
                }
            }
        }

        // ---- the control overlay ----
        AnimatedVisibility(controls && c.error == null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(.7f), .25f to Color.Transparent, .7f to Color.Transparent, 1f to Color.Black.copy(.8f),
            ))) {
                // top
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.subtitle.isNotBlank()) Text(c.subtitle, style = MaterialTheme.typography.bodySmall, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(c.modeNote, style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(end = 6.dp))
                    IconButton(onClick = { (activity as? PlayerActivity)?.enterPip() }) { Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
                    IconButton(onClick = { c.cycleScale(); lastTouch++ }) { Icon(Icons.Filled.AspectRatio, "Zoom: ${c.scale.label}", tint = Color.White) }
                    IconButton(onClick = { sheet = Sheet.Tracks }) { Icon(Icons.Filled.Subtitles, "Audio & subtitles", tint = Color.White) }
                    IconButton(onClick = { sheet = Sheet.Settings }) { Icon(Icons.Filled.Settings, "Playback settings", tint = Color.White) }
                }

                // center transport
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    IconButton(onClick = { c.seekBy(-10_000); lastTouch++ }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { c.togglePlay(); lastTouch++ }, modifier = Modifier.size(76.dp)) {
                        Icon(if (c.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                    IconButton(onClick = { c.seekBy(10_000); lastTouch++ }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }

                // bottom: seek bar
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    val len = c.lengthMs.coerceAtLeast(1)
                    val frac = dragging ?: (c.timeMs.toFloat() / len).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = frac,
                            onValueChange = { dragging = it; lastTouch++ },
                            onValueChangeFinished = { dragging?.let { c.seekTo((it * len).toLong()) }; dragging = null },
                            colors = SliderDefaults.colors(thumbColor = Bolt, activeTrackColor = Bolt, inactiveTrackColor = Color.White.copy(.3f)),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (dragging != null) clock((frac * len).toLong()) else "-" + clock(len - c.timeMs),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(clock(c.timeMs), style = MaterialTheme.typography.labelSmall, color = Muted)
                        Spacer(Modifier.weight(1f))
                        if (c.rate != 1f) Text("${c.rate}x  ", style = MaterialTheme.typography.labelSmall, color = Bolt)
                        if (c.next != null) {
                            TextButton(onClick = { c.playNext() }) {
                                Icon(Icons.Filled.SkipNext, null, tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Next Episode", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Skip Intro
        if (c.introActive && !c.showNextCard) {
            PillButton("Skip Intro", Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 96.dp)) { c.skipIntro() }
        }

        // Next episode card over the credits
        if (c.showNextCard && c.next != null) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 96.dp),
                horizontalAlignment = Alignment.End,
            ) {
                val n = c.next!!
                Text("Up next: ${n.episodeCode} ${n.name}", style = MaterialTheme.typography.bodySmall, color = Cream,
                    modifier = Modifier.background(Color.Black.copy(.6f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Watch Credits", dark = true) { c.dismissNext() }
                    PillButton(if (c.countdown > 0) "Next Episode in ${c.countdown}" else "Next Episode") { c.playNext() }
                }
            }
        }

        c.error?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Playback problem", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(msg, color = Muted)
                Spacer(Modifier.height(16.dp))
                PillButton("Close") { onClose() }
            }
        }

        when (sheet) {
            Sheet.Tracks -> TracksSheet(c) { sheet = Sheet.None }
            Sheet.Settings -> SettingsSheet(c) { sheet = Sheet.None }
            Sheet.None -> {}
        }
    }
}

@Composable
private fun PillButton(text: String, modifier: Modifier = Modifier, dark: Boolean = false, onClick: () -> Unit) {
    Text(
        text, fontWeight = FontWeight.Bold, color = if (dark) Color.White else Color.Black,
        modifier = modifier.clip(RoundedCornerShape(4.dp))
            .background(if (dark) Color.Black.copy(.6f) else Color.White)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** Netflix-style "Audio & Subtitles" panel: two columns side by side. */
@Composable
private fun TracksSheet(c: PlayerController, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(.85f)).clickable(onClick = onDismiss)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Audio", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn {
                    itemsIndexed(c.audioOptions) { i, o -> Choice(o.label, i == c.audioSel) { c.selectAudio(i); onDismiss() } }
                }
            }
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Subtitles", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn {
                    item { Choice("Off", c.subSel < 0) { c.selectSubtitle(-1); onDismiss() } }
                    itemsIndexed(c.subOptions) { i, o -> Choice(o.label, i == c.subSel) { c.selectSubtitle(i); onDismiss() } }
                }
            }
        }
    }
}

@Composable
private fun SettingsSheet(c: PlayerController, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(.85f)).clickable(onClick = onDismiss)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            Column(Modifier.weight(1.3f).fillMaxHeight()) {
                Text("Quality", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn {
                    itemsIndexed(QUALITY_OPTIONS) { _, (q, label) ->
                        Choice(label, q == c.quality) { c.setQuality(q); onDismiss() }
                    }
                }
            }
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Speed", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn {
                    itemsIndexed(listOf(0.75f, 1f, 1.25f, 1.5f, 2f)) { _, r ->
                        Choice(if (r == 1f) "Normal" else "${r}x", r == c.rate) { c.setSpeed(r); onDismiss() }
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text("Zoom", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(10.dp))
                    }
                    itemsIndexed(Scale.entries) { _, s ->
                        Choice(s.label, s == c.scale) { while (c.scale != s) c.cycleScale(); onDismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .background(if (selected) Panel else Color.Transparent).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp)) { if (selected) Icon(Icons.Filled.Check, null, tint = Bolt) }
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (selected) Color.White else Cream, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun clock(ms: Long): String {
    val t = (ms.coerceAtLeast(0) / 1000)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Suppress("unused")
private val keepCapFlixAppLinked = CapFlixApp::class
