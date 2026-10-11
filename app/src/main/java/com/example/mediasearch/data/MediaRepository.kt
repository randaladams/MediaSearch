package com.example.mediasearch.data

import com.example.mediasearch.ssh.SshClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class RefreshResult(val movies: Int, val shows: Int, val seasons: Int)

class MediaRepository(
    private val dao: MediaDao,
    private val ssh: SshClient,
    private val settings: SettingsRepository,
) {
    fun searchMovies(q: String): Flow<List<Movie>> = dao.searchMovies(q.trim())
    fun searchShows(q: String): Flow<List<ShowWithSeasons>> = dao.searchShows(q.trim())
    val movieCount: Flow<Int> = dao.movieCount()
    val showCount: Flow<Int> = dao.showCount()

    /** Downloads the folder names over SSH and rebuilds the local database. */
    suspend fun refresh(): RefreshResult {
        val s = settings.settings.first()
        if (s.moviesPath.isBlank() && s.tvPath.isBlank()) {
            throw IllegalStateException("Set the Movies and TV folder paths in Settings first.")
        }

        val listing = ssh.fetchListing(s)
        if (listing.movieFolders.isEmpty() && listing.tvEntries.isEmpty()) {
            // Protects the offline copy if the media drive isn't mounted.
            throw IllegalStateException("No folders found on the server. Is the media drive mounted? Your saved library was kept.")
        }

        val movies = listing.movieFolders.map { folder ->
            val p = NameParser.parse(folder)
            Movie(title = p.title, year = p.year, folderName = folder)
        }

        // Group "Show (Year)" and "Show (Year)/Season N" entries by show.
        val showFolders = linkedMapOf<String, MutableList<String>>()
        for (entry in listing.tvEntries) {
            val slash = entry.indexOf('/')
            if (slash < 0) {
                showFolders.getOrPut(entry) { mutableListOf() }
            } else {
                showFolders.getOrPut(entry.substring(0, slash)) { mutableListOf() }.add(entry.substring(slash + 1))
            }
        }
        val shows = showFolders.map { (folder, seasonFolders) ->
            val p = NameParser.parse(folder)
            ShowWithSeasons(
                show = Show(title = p.title, year = p.year, folderName = folder),
                seasons = seasonFolders.map { Season(showId = 0, name = it, number = NameParser.seasonNumber(it)) },
            )
        }

        dao.replaceAll(movies, shows)
        settings.setLastRefresh(System.currentTimeMillis())
        return RefreshResult(movies.size, shows.size, shows.sumOf { it.seasons.size })
    }
}
