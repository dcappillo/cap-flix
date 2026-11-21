package com.derek.capflix.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.derek.capflix.CapFlixApp
import com.derek.capflix.data.Item
import com.derek.capflix.ui.components.ErrorState
import com.derek.capflix.ui.components.Loading
import com.derek.capflix.ui.components.RowSection
import com.derek.capflix.ui.components.Wordmark
import com.derek.capflix.ui.components.friendlyError
import com.derek.capflix.ui.theme.Bolt
import com.derek.capflix.ui.theme.Cream
import com.derek.capflix.ui.theme.Muted
import com.derek.capflix.ui.theme.Night
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class Filter(val label: String, val types: String) {
    All("Home", "Movie,Series"), Movies("Movies", "Movie"), Shows("TV Shows", "Series")
}

data class HomeRow(val title: String, val items: List<Item>, val landscape: Boolean = false, val more: String? = null)

data class HomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val hero: Item? = null,
    val rows: List<HomeRow> = emptyList(),
)

class HomeViewModel : ViewModel() {
    private val api = CapFlixApp.api
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state
    private val _filter = MutableStateFlow(Filter.All)
    val filter: StateFlow<Filter> = _filter
    private val cache = mutableMapOf<Filter, HomeState>()

    init { load() }

    fun setFilter(f: Filter) {
        if (f == _filter.value) return
        _filter.value = f
        cache[f]?.let { _state.value = it } ?: load()
    }

    private var shownOnce = false
    fun onShown() { if (shownOnce) refresh() else shownOnce = true }

    fun refresh() { cache.clear(); load(quiet = true) }

    fun load(quiet: Boolean = false) {
        val f = _filter.value
        if (!quiet) _state.value = HomeState(loading = true)
        viewModelScope.launch {
            try {
                val s = build(f)
                cache[f] = s
                if (_filter.value == f) _state.value = s
            } catch (e: Exception) {
                if (_state.value.rows.isEmpty()) _state.value = HomeState(loading = false, error = friendlyError(e))
            }
        }
    }

    private suspend fun build(f: Filter): HomeState = coroutineScope {
        val views = api.views()
        val movieView = views.firstOrNull { it.collectionType == "movies" }
        val tvView = views.firstOrNull { it.collectionType == "tvshows" }

        val resumeD = async { runCatching { api.resume(24) }.getOrDefault(emptyList()) }
        val nextUpD = async { if (f != Filter.Movies) runCatching { api.nextUp(24) }.getOrDefault(emptyList()) else emptyList() }
        val latestMoviesD = async { if (f != Filter.Shows && movieView != null) runCatching { api.latest(movieView.id, "Movie") }.getOrDefault(emptyList()) else emptyList() }
        val latestShowsD = async { if (f != Filter.Movies && tvView != null) runCatching { api.latest(tvView.id, "Series") }.getOrDefault(emptyList()) else emptyList() }
        val favsD = async { runCatching { api.items(types = f.types, filters = "IsFavorite", sortBy = "DateCreated", sortOrder = "Descending", limit = 30).items }.getOrDefault(emptyList()) }
        val topD = async { runCatching { api.items(types = f.types, sortBy = "CommunityRating,SortName", sortOrder = "Descending", isPlayed = false, minRating = 7.0, limit = 30).items.shuffled().take(20) }.getOrDefault(emptyList()) }
        val genresD = async { runCatching { api.genres(f.types) }.getOrDefault(emptyList()) }
        val boxSetsD = async { if (f != Filter.Shows) runCatching { api.items(types = "BoxSet", sortBy = "Random", limit = 20).items }.getOrDefault(emptyList()) else emptyList() }

        val resume = resumeD.await().filter {
            when (f) { Filter.All -> true; Filter.Movies -> it.isMovie; Filter.Shows -> it.isEpisode }
        }
        val nextUp = nextUpD.await().filter { n -> resume.none { it.seriesId == n.seriesId } }

        // "Because you watched": the most recent thing finished or in progress.
        val anchor = resume.firstOrNull()?.let { if (it.isEpisode) it.seriesId?.let { sid -> runCatching { api.item(sid) }.getOrNull() } else it }
        val becauseD = async { anchor?.let { a -> runCatching { api.similar(a.id, 16) }.getOrDefault(emptyList()) } ?: emptyList() }

        // A few genre rows, different each visit.
        val preferred = listOf("Action", "Comedy", "Science Fiction", "Animation", "Drama", "Horror", "Thriller", "Adventure",
            "Crime", "Fantasy", "Family", "Mystery", "Documentary", "Romance", "War", "Western")
        val gs = genresD.await().map { it.name }
        val pick = (preferred.filter { it in gs }.shuffled().take(5) + gs.shuffled()).distinct().take(6)
        val genreRows = pick.map { g ->
            async { HomeRow(g, runCatching { api.items(types = f.types, genre = g, sortBy = "Random", limit = 24).items }.getOrDefault(emptyList()), more = "genre:$g") }
        }.awaitAll().filter { it.items.size >= 4 }

        val latestMovies = latestMoviesD.await()
        val latestShows = latestShowsD.await()
        val newAll = (latestMovies.take(12) + latestShows.take(12)).sortedByDescending { it.premiereDate ?: "" }

        val rows = buildList {
            if (resume.isNotEmpty()) add(HomeRow("Continue Watching", resume, landscape = true))
            if (nextUp.isNotEmpty()) add(HomeRow("Next Up", nextUp, landscape = true))
            when (f) {
                Filter.All -> if (newAll.isNotEmpty()) add(HomeRow("Recently Added", newAll, more = "recent"))
                Filter.Movies -> add(HomeRow("Recently Added Movies", latestMovies, more = "recent"))
                Filter.Shows -> add(HomeRow("New Episodes", latestShows, more = "recent"))
            }
            val favs = favsD.await()
            if (favs.isNotEmpty()) add(HomeRow("My List", favs, more = "mylist"))
            val because = becauseD.await()
            if (anchor != null && because.size >= 4) add(HomeRow("Because You Watched ${anchor.name}", because))
            val top = topD.await()
            if (top.size >= 4) add(HomeRow("Top Rated, Not Yet Seen", top))
            genreRows.take(3).forEach { add(it) }
            val boxes = boxSetsD.await()
            if (boxes.size >= 3) add(HomeRow("Collections", boxes, more = "collections"))
            genreRows.drop(3).forEach { add(it) }
        }

        // Billboard: something recent and unwatched with good art.
        val heroPool = (latestMovies + latestShows + topD.await()).filter {
            it.backdropTags.isNotEmpty() && it.userData?.played != true
        }
        HomeState(loading = false, hero = heroPool.shuffled().firstOrNull() ?: resume.firstOrNull(), rows = rows)
    }

    fun toggleFavorite(item: Item) {
        viewModelScope.launch {
            val fav = item.userData?.isFavorite != true
            runCatching { api.setFavorite(item.id, fav) }
            val s = _state.value
            val h = s.hero
            if (h != null && h.id == item.id) {
                _state.value = s.copy(hero = h.copy(userData = (h.userData ?: com.derek.capflix.data.UserData()).copy(isFavorite = fav)))
            }
            cache.clear()
        }
    }
}

@Composable
fun HomeScreen(
    openItem: (Item) -> Unit,
    play: (Item) -> Unit,
    openMore: (String, Filter) -> Unit,
    openSettings: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val filter by vm.filter.collectAsState()
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
    val barColor by animateColorAsState(if (scrolled) Night.copy(alpha = .94f) else Color.Transparent, label = "bar")

    // Coming back from the player: refresh progress bars and Continue Watching.
    LifecycleResumeEffect(Unit) {
        vm.onShown()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        when {
            state.loading && state.rows.isEmpty() -> Loading()
            state.error != null -> ErrorState(state.error!!) { vm.load() }
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item { state.hero?.let { Billboard(it, openItem, play, vm::toggleFavorite) } ?: Spacer(Modifier.height(110.dp)) }
                items(state.rows, key = { it.title }) { row ->
                    RowSection(
                        row.title, row.items, landscape = row.landscape,
                        onMore = row.more?.let { m -> { openMore(m, filter) } },
                    ) { if (row.landscape) play(it) else openItem(it) }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }

        // Top bar: wordmark + Movies / TV Shows chips, fading in on scroll.
        Column(Modifier.fillMaxWidth().background(barColor)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(size = 22, modifier = Modifier.weight(1f))
                IconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = Cream) }
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f ->
                    val on = f == filter
                    Text(
                        f.label, style = MaterialTheme.typography.labelMedium,
                        color = if (on) Night else Cream,
                        modifier = Modifier
                            .background(if (on) Bolt else Color.Transparent, RoundedCornerShape(50))
                            .border(1.dp, if (on) Bolt else Color.White.copy(.45f), RoundedCornerShape(50))
                            .clickable { vm.setFilter(f) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Billboard(item: Item, openItem: (Item) -> Unit, play: (Item) -> Unit, toggleFav: (Item) -> Unit) {
    val api = CapFlixApp.api
    Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
        AsyncImage(
            model = api.backdrop(item, 1280) ?: api.poster(item, 900), contentDescription = item.name,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Night.copy(.75f), .25f to Color.Transparent, .55f to Color.Transparent, 1f to Night,
        )))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val logo = api.logo(item)
            if (logo != null) {
                AsyncImage(logo, item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth(.75f).heightIn(max = 110.dp))
            } else {
                Text(item.name, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(10.dp))
            Text(item.genres.take(3).joinToString("  •  "), color = Cream, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                val fav = item.userData?.isFavorite == true
                Column(Modifier.clickable { toggleFav(item) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (fav) Icons.Filled.Check else Icons.Filled.Add, null, tint = Color.White)
                    Text("My List", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Button(
                    onClick = { play(item) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp), modifier = Modifier.width(130.dp).height(44.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.size(4.dp))
                    Text(if (item.progress > 0f) "Resume" else "Play", fontWeight = FontWeight.Bold)
                }
                Column(Modifier.clickable { openItem(item) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Info, null, tint = Color.White)
                    Text("Info", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
