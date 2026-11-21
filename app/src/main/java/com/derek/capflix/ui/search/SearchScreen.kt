package com.derek.capflix.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derek.capflix.CapFlixApp
import com.derek.capflix.data.GenreItem
import com.derek.capflix.data.Item
import com.derek.capflix.ui.components.Loading
import com.derek.capflix.ui.components.PosterCard
import com.derek.capflix.ui.theme.Bolt
import com.derek.capflix.ui.theme.Muted
import com.derek.capflix.ui.theme.Night
import com.derek.capflix.ui.theme.PanelHigh
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val api = CapFlixApp.api
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<Item>>(emptyList())
    val busy = MutableStateFlow(false)
    val genres = MutableStateFlow<List<GenreItem>>(emptyList())
    private var job: Job? = null

    init { viewModelScope.launch { genres.value = runCatching { api.genres() }.getOrDefault(emptyList()) } }

    fun type(q: String) {
        query.value = q
        job?.cancel()
        if (q.isBlank()) { results.value = emptyList(); busy.value = false; return }
        job = viewModelScope.launch {
            busy.value = true
            delay(250) // wait for a pause in typing
            results.value = runCatching { api.items(types = "Movie,Series", search = q.trim(), sortBy = "SortName", limit = 60).items }
                .getOrDefault(emptyList())
            busy.value = false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(openItem: (Item) -> Unit, openGenre: (String) -> Unit, vm: SearchViewModel = viewModel()) {
    val q by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val busy by vm.busy.collectAsState()
    val genres by vm.genres.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(Night).statusBarsPadding()) {
        TextField(
            value = q, onValueChange = vm::type, singleLine = true,
            placeholder = { Text("Search movies and shows") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { vm.type("") }) { Icon(Icons.Filled.Close, "Clear") } },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PanelHigh, unfocusedContainerColor = PanelHigh,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Bolt,
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus),
        )
        when {
            q.isBlank() -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Browse by genre", style = MaterialTheme.typography.titleLarge)
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { g ->
                        Text(g.name, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(PanelHigh).clickable { openGenre(g.name) }
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            busy && results.isEmpty() -> Loading()
            results.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp)) { Text("Nothing matches \"$q\".", color = Muted) }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(results, key = { it.id }) { item -> PosterCard(item, width = 400.dp) { openItem(item) } }
            }
        }
    }
}
