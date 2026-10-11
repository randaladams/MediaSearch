package com.example.mediasearch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mediasearch.data.AppSettings
import com.example.mediasearch.data.Movie
import com.example.mediasearch.data.Season
import com.example.mediasearch.data.ShowWithSeasons
import com.example.mediasearch.ui.theme.Brand
import java.text.DateFormat
import java.util.Date

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
            GradientHeader(
                title = "Media Library",
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(
                            Modifier.padding(12.dp).size(24.dp),
                            color = Brand.OnHeader,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = vm::refresh) {
                            Icon(Icons.Default.Refresh, "Sync from server", tint = Brand.OnHeader)
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = Brand.OnHeader)
                    }
                },
                below = {
                    TextField(
                        value = query,
                        onValueChange = vm::onQueryChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Search titles or years") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { vm.onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    )
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.Transparent,
                        contentColor = Brand.Popcorn,
                        divider = {},
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        LibraryTab("🎬  Movies", movieCount, selected = tab == 0) { tab = 0 }
                        LibraryTab("📺  TV Shows", showCount, selected = tab == 1) { tab = 1 }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SyncStatus(settings)
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
private fun LibraryTab(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        selectedContentColor = Brand.OnHeader,
        unselectedContentColor = Brand.OnHeader.copy(alpha = 0.65f),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (selected) Brand.Popcorn else Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                ) {
                    Text(
                        (count ?: 0).toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color(0xFF451A03) else Brand.OnHeader,
                    )
                }
            }
        },
    )
}

@Composable
private fun SyncStatus(settings: AppSettings) {
    val synced = settings.lastRefresh != 0L
    val via = settings.lastRefreshVia
    val dotColor = when {
        !synced -> MaterialTheme.colorScheme.outlineVariant
        via == "Away" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (!synced) "Not synced yet"
            else buildString {
                append("Synced ")
                append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastRefresh)))
                if (via != null) append(if (via == "Away") " · from away" else " · at home")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                leadingContent = { LetterAvatar(movie.title) },
                headlineContent = { Text(movie.title, fontWeight = FontWeight.Medium) },
                trailingContent = if (movie.year != null) {
                    {
                        Pill(
                            movie.year.toString(),
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                } else {
                    null
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
            HorizontalDivider(Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outlineVariant)
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

@OptIn(ExperimentalLayoutApi::class)
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

    Column(
        Modifier.background(
            if (expanded) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.background,
        ),
    ) {
        ListItem(
            modifier = Modifier.clickable(enabled = seasons.isNotEmpty()) { expanded = !expanded },
            leadingContent = { LetterAvatar(item.show.title, shape = CircleShape) },
            headlineContent = { Text(item.show.title, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Text(
                    listOfNotNull(item.show.year?.toString(), seasonLabel).joinToString(" · "),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            },
            trailingContent = if (seasons.isNotEmpty()) {
                {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Hide seasons" else "Show seasons",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else {
                null
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        AnimatedVisibility(visible = expanded) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 74.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                seasons.forEach {
                    Pill(it.name, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NoMatches(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔍", fontSize = 40.sp)
        Spacer(Modifier.size(8.dp))
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
        Text("🍿", fontSize = 64.sp)
        Text("Your library is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Enter your Raspberry Pi details in Settings, then sync to download your movie and TV folders.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onRefresh,
            enabled = !refreshing,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) { Text(if (refreshing) "Syncing…" else "Sync now") }
        OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
    }
}
