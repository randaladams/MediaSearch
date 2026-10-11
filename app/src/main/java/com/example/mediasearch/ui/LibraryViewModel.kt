package com.example.mediasearch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediasearch.MediaSearchApp
import com.example.mediasearch.data.AppSettings
import com.example.mediasearch.data.Imdb
import com.example.mediasearch.data.Movie
import com.example.mediasearch.data.Show
import com.example.mediasearch.download.DownloadStore
import com.example.mediasearch.download.DownloadWorker
import com.example.mediasearch.ssh.SshClient
import android.net.ConnectivityManager
import android.os.Environment
import android.os.StatFs
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

    // ---- Tap: open IMDb ----

    private val _urls = Channel<String>(Channel.BUFFERED)
    val urls: Flow<String> = _urls.receiveAsFlow()

    fun openImdb(movie: Movie) {
        viewModelScope.launch { _urls.send(Imdb.movieUrl(movie.title, movie.year)) }
    }

    fun openImdb(show: Show) {
        viewModelScope.launch { _urls.send(Imdb.showUrl(show.title, show.year)) }
    }

    // ---- Long press: download ----

    data class DownloadDialog(
        val movie: Movie,
        val loading: Boolean = true,
        val files: List<SshClient.RemoteFile> = emptyList(),
        val selected: Set<String> = emptySet(),
        val error: String? = null,
        val freeBytes: Long = 0L,
        val onMobileData: Boolean = false,
    ) {
        val selectedBytes: Long get() = files.filter { it.name in selected }.sumOf { it.size }
        val fitsOnPhone: Boolean get() = selectedBytes < freeBytes
    }

    private val _download = MutableStateFlow<DownloadDialog?>(null)
    val download: StateFlow<DownloadDialog?> = _download.asStateFlow()

    fun showDownload(movie: Movie) {
        _download.value = DownloadDialog(movie)
        viewModelScope.launch {
            val next = try {
                val files = container.sshClient.listMovieFiles(container.settingsRepository.settings.first(), movie.folderName)
                    // Videos first, then subtitles, then everything else.
                    .sortedWith(
                        compareBy<SshClient.RemoteFile>(
                            { !DownloadStore.isVideo(it.name) },
                            { !DownloadStore.isSubtitle(it.name) },
                            { it.name.lowercase() },
                        ),
                    )
                DownloadDialog(
                    movie = movie,
                    loading = false,
                    files = files,
                    selected = files.filter { DownloadStore.isVideo(it.name) || DownloadStore.isSubtitle(it.name) }.map { it.name }.toSet(),
                    error = if (files.isEmpty()) "No files in this folder." else null,
                    freeBytes = StatFs(Environment.getExternalStorageDirectory().path).availableBytes,
                    onMobileData = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
                        ?.isActiveNetworkMetered == true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadDialog(movie, loading = false, error = e.message ?: "Couldn't reach the server.")
            }
            // Ignore the result if the dialog was closed or switched to another movie meanwhile.
            _download.update { if (it?.movie?.id == movie.id) next else it }
        }
    }

    fun toggleFile(name: String) {
        _download.update { d -> d?.copy(selected = if (name in d.selected) d.selected - name else d.selected + name) }
    }

    fun dismissDownload() {
        _download.value = null
    }

    fun startDownload() {
        val d = _download.value ?: return
        val files = d.files.filter { it.name in d.selected }
        if (files.isEmpty()) return
        DownloadWorker.enqueue(getApplication(), d.movie, files)
        _download.value = null
        viewModelScope.launch { _messages.send("Downloading ${d.movie.title}. Progress is in your notifications.") }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val r = repo.refresh()
                _messages.send("Synced via ${r.via}: ${r.movies} movies, ${r.shows} shows (${r.seasons} seasons)")
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
