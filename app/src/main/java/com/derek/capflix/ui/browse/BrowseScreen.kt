package com.derek.capflix.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derek.capflix.CapFlixApp
import com.derek.capflix.data.Item
import com.derek.capflix.ui.components.ErrorState
import com.derek.capflix.ui.components.Loading
import com.derek.capflix.ui.components.PosterCard
import com.derek.capflix.ui.components.friendlyError
import com.derek.capflix.ui.theme.Bolt
import com.derek.capflix.ui.theme.Cream
import com.derek.capflix.ui.theme.Muted
import com.derek.capflix.ui.theme.Night
import com.derek.capflix.ui.theme.PanelHigh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * One grid for every "See all": a genre, Recently Added, My List, a
 * collection (Alien, Star Wars...), or the whole Movies / TV library.
 *
 * kind: "recent" | "mylist" | "collections" | "genre:<name>" | "parent:<id>:<title>" | "all"
 */
enum class Sort(val label: String, val by: String, val order: String) {
    Added("Recently added", "DateCreated,SortName", "Descending"),
    Name("A–Z", "SortName", "Ascending"),
    Year("Newest", "PremiereDate,ProductionYear,SortName", "Descending"),
    Rating("Top rated", "CommunityRating,SortName", "Descending"),
    Random("Surprise me", "Random", "Ascending"),
}

data class BrowseState(val loading: Boolean = true, val error: String? = null, val items: List<Item> = emptyList(),
                       val total: Int = 0, val sort: Sort = Sort.Added, val types: String = "Movie,Series")

class BrowseViewModel : ViewModel() {
    private val api = CapFlixApp.api
    val state = MutableStateFlow(BrowseState())
    private var kind = ""
    private var busy = false

    fun open(kind: String, types: String) {
        if (this.kind == kind && state.value.types == types && state.value.items.isNotEmpty()) return
        this.kind = kind
        val sort = when {
            kind == "mylist" || kind == "recent" -> Sort.Added
            kind.startsWith("parent:") -> Sort.Year
            else -> Sort.Name
        }
        state.value = BrowseState(sort = sort, types = types)
        loadMore()
    }

    fun setSort(s: Sort) { state.value = BrowseState(sort = s, types = state.value.types); loadMore() }

    fun setTypes(t: String) { state.value = BrowseState(sort = state.value.sort, types = t); loadMore() }

    fun loadMore() {
        val s = state.value
        if (busy || (s.items.isNotEmpty() && s.items.size >= s.total)) return
        busy = true
        viewModelScope.launch {
            try {
                val genre = kind.removePrefix("genre:").takeIf { kind.startsWith("genre:") }
                val parent = kind.split(":").getOrNull(1)?.takeIf { kind.startsWith("parent:") }
                val res = when {
                    kind == "collections" -> api.items(types = "BoxSet", sortBy = s.sort.by, sortOrder = s.sort.order, start = s.items.size)
                    parent != null -> api.items(types = "Movie,Series,Episode", parentId = parent, sortBy = s.sort.by, sortOrder = s.sort.order, start = s.items.size)
                    kind == "mylist" -> api.items(types = s.types, filters = "IsFavorite", sortBy = s.sort.by, sortOrder = s.sort.order, start = s.items.size)
                    else -> api.items(types = s.types, genre = genre, sortBy = s.sort.by, sortOrder = s.sort.order, start = s.items.size)
                }
                val cur = state.value
                state.value = cur.copy(loading = false, error = null, items = (cur.items + res.items).distinctBy { it.id }, total = res.total)
            } catch (e: Exception) {
                state.value = state.value.copy(loading = false, error = friendlyError(e))
            } finally {
                busy = false
            }
        }
    }
}

fun browseTitle(kind: String): String = when {
    kind == "recent" -> "Recently Added"
    kind == "mylist" -> "My List"
    kind == "collections" -> "Collections"
    kind == "all" -> "Everything"
    kind.startsWith("genre:") -> kind.removePrefix("genre:")
    kind.startsWith("parent:") -> kind.split(":", limit = 3).getOrNull(2) ?: "Collection"
    else -> kind
}

@Composable
fun BrowseScreen(
    kind: String,
    types: String,
    onBack: (() -> Unit)?,
    openItem: (Item) -> Unit,
    vm: BrowseViewModel = viewModel(key = "browse:$kind"),
) {
    LaunchedEffect(kind, types) { vm.open(kind, types) }
    val s by vm.state.collectAsState()
    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf {
        val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= grid.layoutInfo.totalItemsCount - 12
    } }
    LaunchedEffect(nearEnd, s.items.size) { if (nearEnd && s.items.isNotEmpty()) vm.loadMore() }
    var sortOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Night).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            else Box(Modifier.padding(start = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(browseTitle(kind), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.total > 0) Text("${s.total} titles", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Box {
                IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    Sort.entries.forEach { so ->
                        DropdownMenuItem(text = { Text(so.label, color = if (so == s.sort) Bolt else Cream) },
                            onClick = { sortOpen = false; vm.setSort(so) })
                    }
                }
            }
        }
        if (kind != "collections" && !kind.startsWith("parent:")) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Movie,Series" to "All", "Movie" to "Movies", "Series" to "TV Shows").forEach { (t, label) ->
                    val on = t == s.types
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Night else Cream,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Bolt else PanelHigh)
                            .clickable { if (!on) vm.setTypes(t) }.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }
        when {
            s.loading && s.items.isEmpty() -> Loading()
            s.error != null && s.items.isEmpty() -> ErrorState(s.error!!) { vm.loadMore() }
            s.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (kind == "mylist") "Tap + My List on anything to save it here." else "Nothing here yet.", color = Muted)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp), state = grid,
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize(),
            ) {
                items(s.items, key = { it.id }) { item ->
                    Column {
                        PosterCard(item, width = 400.dp) { openItem(item) }
                        if (item.type == "BoxSet" || item.type == "Episode") {
                            Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                if (s.items.size < s.total) item(span = { GridItemSpan(maxLineSpan) }) { Loading(Modifier.fillMaxWidth().padding(16.dp)) }
            }
        }
    }
}
