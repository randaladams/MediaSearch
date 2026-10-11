package com.example.mediasearch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mediasearch.data.Movie
import com.example.mediasearch.data.Season
import com.example.mediasearch.data.ShowWithSeasons
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenSettings: () -> Unit, vm: LibraryViewModel = viewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val movies by vm.movies.collectAsStateWithLifecycle()
    val shows by vm.shows.collectAsStateWithLifecycle()
    val movieCount by vm.movieCount.collectAsStateWithLifecycle()
    val showCount by vm.showCount.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media Library") },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh from server") }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search titles or years") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, "Clear search") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Movies (${movieCount ?: 0})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("TV Shows (${showCount ?: 0})") })
            }

            Text(
                text = if (settings.lastRefresh == 0L) "Never synced"
                else "Last synced " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(settings.lastRefresh)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            when {
                movieCount == 0 && showCount == 0 ->
                    EmptyLibrary(refreshing, onRefresh = vm::refresh, onOpenSettings = onOpenSettings)
                tab == 0 -> MovieList(movies, query)
                else -> ShowList(shows, query)
            }
        }
    }
}

@Composable
private fun MovieList(movies: List<Movie>, query: String) {
    if (movies.isEmpty()) {
        NoMatches("No movies match \"$query\"")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(movies, key = { it.id }) { movie ->
            ListItem(
                headlineContent = { Text(movie.title) },
                supportingContent = if (movie.year != null) {
                    { Text(movie.year.toString()) }
                } else {
                    null
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ShowList(shows: List<ShowWithSeasons>, query: String) {
    if (shows.isEmpty()) {
        NoMatches("No shows match \"$query\"")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(shows, key = { it.show.id }) { ShowRow(it) }
    }
}

@Composable
private fun ShowRow(item: ShowWithSeasons) {
    var expanded by rememberSaveable(item.show.id) { mutableStateOf(false) }
    val seasons = remember(item.seasons) {
        item.seasons.sortedWith(
            compareBy<Season, Int?>(nullsLast(naturalOrder())) { it.number }.thenBy { it.name },
        )
    }
    val seasonLabel = when (seasons.size) {
        0 -> "No season folders"
        1 -> "1 season"
        else -> "${seasons.size} seasons"
    }

    Column {
        ListItem(
            modifier = Modifier.clickable(enabled = seasons.isNotEmpty()) { expanded = !expanded },
            headlineContent = { Text(item.show.title) },
            supportingContent = {
                Text(listOfNotNull(item.show.year?.toString(), seasonLabel).joinToString(" · "))
            },
            trailingContent = if (seasons.isNotEmpty()) {
                {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Hide seasons" else "Show seasons",
                    )
                }
            } else {
                null
            },
        )
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, bottom = 12.dp)) {
                seasons.forEach {
                    Text(
                        it.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun NoMatches(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyLibrary(refreshing: Boolean, onRefresh: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Your library is empty", style = MaterialTheme.typography.titleMedium)
        Text(
            "Enter your Raspberry Pi details in Settings, then refresh to download your movie and TV folders.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRefresh, enabled = !refreshing) { Text(if (refreshing) "Refreshing…" else "Refresh now") }
        OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
    }
}
