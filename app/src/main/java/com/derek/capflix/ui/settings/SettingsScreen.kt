package com.derek.capflix.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.derek.capflix.CapFlixApp
import com.derek.capflix.ui.theme.Bolt
import com.derek.capflix.ui.theme.Coral
import com.derek.capflix.ui.theme.Cream
import com.derek.capflix.ui.theme.Muted
import com.derek.capflix.ui.theme.Night
import com.derek.capflix.ui.theme.Panel
import kotlinx.coroutines.launch

val QUALITY_OPTIONS = listOf(
    0L to "Auto (original at home, 8 Mbps away)",
    Long.MAX_VALUE to "Original file, always",
    40_000_000L to "40 Mbps (4K)",
    20_000_000L to "20 Mbps (1080p, best)",
    10_000_000L to "10 Mbps (1080p)",
    8_000_000L to "8 Mbps",
    4_000_000L to "4 Mbps (720p)",
    2_000_000L to "2 Mbps (data saver)",
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit) {
    val s = CapFlixApp.session
    val scope = rememberCoroutineScope()
    var quality by remember { mutableStateOf(s.quality) }
    var audio by remember { mutableStateOf(s.audioLanguage) }
    var subs by remember { mutableStateOf(s.subtitleMode) }
    var autoplay by remember { mutableStateOf(s.autoplayNext) }
    var skip by remember { mutableStateOf(s.autoSkipIntro) }

    Column(Modifier.fillMaxSize().background(Night).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        Section("Account")
        Info("Signed in as", s.userName)
        Info("Server", s.server)

        Section("Playback")
        Choice("Streaming quality", quality, QUALITY_OPTIONS) { quality = it; s.quality = it }
        Choice("Audio language", audio, listOf("eng" to "English", "jpn" to "Japanese (anime)", "" to "Whatever the file prefers")) {
            audio = it; s.audioLanguage = it
        }
        Choice("Subtitles", subs, listOf("forced" to "Only for foreign-language parts", "eng" to "English, always", "off" to "Off")) {
            subs = it; s.subtitleMode = it
        }
        Toggle("Play next episode automatically", autoplay) { autoplay = it; s.autoplayNext = it }
        Toggle("Skip intros automatically", skip, "Needs the Intro Skipper plugin on Jellyfin") { skip = it; s.autoSkipIntro = it }

        Section("")
        Text(
            "Sign out", color = Coral, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().clickable {
                scope.launch { CapFlixApp.api.signOut(); onSignedOut() }
            }.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Cap Flix 1.0 · video by VLC (libVLC), library by Jellyfin", color = Muted,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(18.dp))
    if (title.isNotEmpty()) Text(title.uppercase(), color = Bolt, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    HorizontalDivider(color = Panel)
}

@Composable
private fun Info(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun <T> Choice(label: String, value: T, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { menuOpen = true }.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(options.firstOrNull { it.first == value }?.second ?: "", color = Muted, style = MaterialTheme.typography.bodySmall)
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            options.forEach { (v, text) ->
                DropdownMenuItem(text = { Text(text, color = if (v == value) Bolt else Cream) }, onClick = { menuOpen = false; onPick(v) })
            }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            hint?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Bolt, checkedThumbColor = Night))
    }
}
