package com.example.mediasearch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediasearch.MediaSearchApp
import com.example.mediasearch.data.AppSettings
import com.example.mediasearch.data.Movie
import com.example.mediasearch.data.ShowWithSeasons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as MediaSearchApp
    private val repo = container.mediaRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val debouncedQuery = _query.debounce(150).distinctUntilChanged()

    val movies: StateFlow<List<Movie>> = debouncedQuery
        .flatMapLatest { repo.searchMovies(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shows: StateFlow<List<ShowWithSeasons>> = debouncedQuery
        .flatMapLatest { repo.searchShows(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val movieCount: StateFlow<Int?> = repo.movieCount.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val showCount: StateFlow<Int?> = repo.showCount.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<AppSettings> =
        container.settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun onQueryChange(q: String) {
        _query.value = q
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val r = repo.refresh()
                _messages.send("Updated: ${r.movies} movies, ${r.shows} shows (${r.seasons} seasons)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(e.message ?: "Refresh failed")
            } finally {
                _refreshing.value = false
            }
        }
    }
}
