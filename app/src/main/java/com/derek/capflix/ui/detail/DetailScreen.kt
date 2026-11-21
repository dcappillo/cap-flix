package com.derek.capflix.ui.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.derek.capflix.CapFlixApp
import com.derek.capflix.data.Item
import com.derek.capflix.data.MediaSource
import com.derek.capflix.data.UserData
import com.derek.capflix.ui.components.Art
import com.derek.capflix.ui.components.Badge
import com.derek.capflix.ui.components.ErrorState
import com.derek.capflix.ui.components.Loading
import com.derek.capflix.ui.components.PosterCard
import com.derek.capflix.ui.components.ProgressBar
import com.derek.capflix.ui.components.formatMinutes
import com.derek.capflix.ui.components.friendlyError
import com.derek.capflix.ui.components.remainingLabel
import com.derek.capflix.ui.theme.Bolt
import com.derek.capflix.ui.theme.Cream
import com.derek.capflix.ui.theme.Muted
import com.derek.capflix.ui.theme.Night
import com.derek.capflix.ui.theme.PanelHigh
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class DetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val item: Item? = null,
    val seasons: List<Item> = emptyList(),
    val season: Item? = null,
    val episodes: List<Item> = emptyList(),
    val upNext: Item? = null,       // series: the episode Play should start
    val similar: List<Item> = emptyList(),
    val version: MediaSource? = null,
)

class DetailViewModel : ViewModel() {
    private val api = CapFlixApp.api
    val state = MutableStateFlow(DetailState())
    private var id: String = ""

    fun open(itemId: String) {
        if (id == itemId) return
        id = itemId
        load()
    }

    fun load(quiet: Boolean = false) {
        if (!quiet) state.value = DetailState()
        viewModelScope.launch {
            try {
                coroutineScope {
                    val item = api.item(id)
                    val similarD = async { runCatching { api.similar(id, 18) }.getOrDefault(emptyList()) }
                    if (item.isSeries) {
                        val seasons = api.seasons(id).filter { (it.indexNumber ?: 0) > 0 || it.name.contains("Special", true) }
                        val up = runCatching { api.nextUp(1, seriesId = id).firstOrNull() }.getOrNull()
                            ?: runCatching { api.resume(50).firstOrNull { it.seriesId == id } }.getOrNull()
                        val keep = state.value.season?.let { s -> seasons.firstOrNull { it.id == s.id } }
                        val season = keep ?: seasons.firstOrNull { it.id == up?.seasonId } ?: seasons.firstOrNull { (it.indexNumber ?: 0) > 0 } ?: seasons.firstOrNull()
                        val eps = season?.let { api.episodes(id, it.id) } ?: emptyList()
                        val first = up ?: eps.firstOrNull()
                        state.value = DetailState(false, null, item, seasons, season, eps, first, similarD.await())
                    } else {
                        state.value = DetailState(false, null, item, similar = similarD.await(),
                            version = state.value.version ?: item.mediaSources.firstOrNull())
                    }
                }
            } catch (e: Exception) {
                state.value = DetailState(loading = false, error = friendlyError(e))
            }
        }
    }

    fun onResumed() { if (state.value.item != null) load(quiet = true) }

    fun pickSeason(s: Item) {
        val item = state.value.item ?: return
        state.value = state.value.copy(season = s, episodes = emptyList())
        viewModelScope.launch {
            val eps = runCatching { api.episodes(item.id, s.id) }.getOrDefault(emptyList())
            state.value = state.value.copy(episodes = eps)
        }
    }

    fun pickVersion(v: MediaSource) { state.value = state.value.copy(version = v) }

    fun toggleFavorite() {
        val item = state.value.item ?: return
        val fav = item.userData?.isFavorite != true
        state.value = state.value.copy(item = item.copy(userData = (item.userData ?: UserData()).copy(isFavorite = fav)))
        viewModelScope.launch { runCatching { api.setFavorite(item.id, fav) } }
    }

    fun togglePlayed(target: Item) {
        val played = target.userData?.played != true
        viewModelScope.launch {
            runCatching { api.setPlayed(target.id, played) }
            load(quiet = true)
        }
    }
}

@Composable
fun DetailScreen(
    itemId: String,
    onBack: () -> Unit,
    openItem: (Item) -> Unit,
    play: (Item, String?) -> Unit,
    vm: DetailViewModel = viewModel(key = itemId),
) {
    vm.open(itemId)
    val s by vm.state.collectAsState()
    LifecycleResumeEffect(itemId) {
        vm.onResumed()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        when {
            s.loading -> Loading()
            s.error != null -> ErrorState(s.error!!) { vm.load() }
            s.item != null -> DetailBody(s, vm, openItem, play)
        }
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(6.dp).background(Color.Black.copy(.45f), CircleShape)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
    }
}

@Composable
private fun DetailBody(s: DetailState, vm: DetailViewModel, openItem: (Item) -> Unit, play: (Item, String?) -> Unit) {
    val api = CapFlixApp.api
    val main = s.item!!
    var expanded by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                AsyncImage(api.backdrop(main), main.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to Night)))
                api.logo(main)?.let {
                    AsyncImage(it, main.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).fillMaxWidth(.6f).heightIn(max = 90.dp))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (api.logo(main) == null) {
                    Text(main.name, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                }
                MetaLine(main, s)
                Spacer(Modifier.height(14.dp))

                // Play / Resume
                val target = if (main.isSeries) s.upNext else main
                val label = when {
                    target == null -> "Play"
                    main.isSeries && target.progress > 0f -> "Resume ${target.episodeCode}"
                    main.isSeries -> "Play ${target.episodeCode}"
                    target.progress > 0f -> "Resume"
                    else -> "Play"
                }
                Button(
                    onClick = { target?.let { play(it, if (main.isSeries) null else s.version?.id) } },
                    enabled = target != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp))
                    Text(label, fontWeight = FontWeight.Bold)
                }
                if (target != null && target.progress > 0f) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(2.dp))) { ProgressBar(target.progress) }
                        Spacer(Modifier.width(10.dp))
                        Text(remainingLabel(target) ?: "", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }

                // Versions (Director's Cut, Extended...)
                if (main.isMovie && main.mediaSources.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Text("Version", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        main.mediaSources.forEach { ms ->
                            val on = ms.id == s.version?.id
                            Text(
                                versionName(ms, main), style = MaterialTheme.typography.labelMedium,
                                color = if (on) Night else Cream,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Bolt else PanelHigh)
                                    .clickable { vm.pickVersion(ms) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                main.taglines.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = Cream)
                    Spacer(Modifier.height(6.dp))
                }
                main.overview?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(.88f),
                        maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
                Spacer(Modifier.height(10.dp))
                val cast = main.people.filter { it.type == "Actor" }
                if (cast.isNotEmpty()) Text("Starring: " + cast.take(4).joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = Muted)
                val directors = main.people.filter { it.type == "Director" }
                if (directors.isNotEmpty()) Text("Director: " + directors.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = Muted)

                // Actions
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    val fav = main.userData?.isFavorite == true
                    Action(if (fav) Icons.Filled.Check else Icons.Filled.Add, "My List") { vm.toggleFavorite() }
                    val played = main.userData?.played == true
                    Action(if (played) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, if (played) "Watched" else "Mark watched") { vm.togglePlayed(main) }
                }
            }
        }

        // Cast row
        val cast = main.people.filter { it.type == "Actor" }.take(15)
        if (cast.isNotEmpty()) item {
            Text("Cast", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cast) { p ->
                    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Art(api.personImage(p), p.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                            Modifier.size(70.dp).clip(CircleShape))
                        Spacer(Modifier.height(4.dp))
                        Text(p.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        p.role?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }

        // Episodes
        if (main.isSeries) {
            item { SeasonPicker(s, vm) }
            items(s.episodes, key = { it.id }) { ep -> EpisodeRow(ep, onPlay = { play(ep, null) }, onToggle = { vm.togglePlayed(ep) }) }
            if (s.episodes.isEmpty()) item { Loading(Modifier.fillMaxWidth().height(120.dp)) }
        }

        // More like this
        if (s.similar.isNotEmpty()) {
            item { Text("More Like This", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 10.dp)) }
            items(s.similar.chunked(3)) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { Box(Modifier.weight(1f)) { PosterCard(it, width = 200.dp) { openItem(it) } } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

private fun versionName(ms: MediaSource, item: Item): String {
    val n = ms.name ?: return "Version"
    // Jellyfin names versions after the text following " - " in the file name.
    val tail = n.substringAfterLast(" - ", "")
    return tail.ifBlank { if (n.contains(item.name, true)) "Original" else n }.take(28)
}

@Composable
private fun MetaLine(item: Item, s: DetailState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item.rating?.let {
            Icon(Icons.Filled.Star, null, tint = Bolt, modifier = Modifier.size(15.dp))
            Text(String.format("%.1f", it), style = MaterialTheme.typography.labelMedium, color = Cream)
        }
        item.year?.let { y ->
            val end = item.endDate?.take(4)
            Text(if (item.isSeries && end != null && end != y.toString()) "$y–$end" else "$y", style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        item.officialRating?.let { Badge(it) }
        if (item.isSeries) {
            val n = s.seasons.count { (it.indexNumber ?: 0) > 0 }
            if (n > 0) Text(if (n == 1) "1 Season" else "$n Seasons", style = MaterialTheme.typography.labelMedium, color = Muted)
        } else {
            item.runtimeMinutes?.let { Text(formatMinutes(it), style = MaterialTheme.typography.labelMedium, color = Muted) }
        }
        val src = s.version
        val badges = if (src != null) item.copy(mediaSources = listOf(src)).qualityBadges else item.qualityBadges
        badges.forEach { Badge(it, highlight = it == "4K") }
    }
    if (item.genres.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(item.genres.take(4).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun SeasonPicker(s: DetailState, vm: DetailViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 16.dp, top = 24.dp, bottom = 6.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).background(PanelHigh).clickable { menuOpen = true }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.season?.name ?: "Episodes", style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Filled.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            s.seasons.forEach { season ->
                DropdownMenuItem(
                    text = {
                        val unplayed = season.userData?.unplayedCount ?: 0
                        Text(season.name + if (unplayed > 0) "   ·  $unplayed new" else "")
                    },
                    onClick = { menuOpen = false; vm.pickSeason(season) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Item, onPlay: () -> Unit, onToggle: () -> Unit) {
    val api = CapFlixApp.api
    Column(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(5.dp))) {
                Art(api.landscape(ep, 400), ep.name, Modifier.fillMaxSize())
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(30.dp).background(Color.Black.copy(.45f), CircleShape).padding(3.dp))
                if (ep.progress > 0f) ProgressBar(ep.progress, Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${ep.indexNumber ?: ""}. ${ep.name}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ep.runtimeMinutes?.let { formatMinutes(it) } ?: "", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            IconButton(onClick = onToggle) {
                if (ep.userData?.played == true) Icon(Icons.Filled.CheckCircle, "Watched", tint = Bolt)
                else Icon(Icons.Outlined.CheckCircle, "Mark watched", tint = Muted)
            }
        }
        ep.overview?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
