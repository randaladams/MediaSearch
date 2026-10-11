package com.example.mediasearch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MediaDao {

    // Sorting ignores a leading "The " so "The Matrix" sits under M.
    @Query(
        """
        SELECT * FROM movies
        WHERE title LIKE '%' || :q || '%' OR folderName LIKE '%' || :q || '%'
        ORDER BY CASE WHEN title LIKE 'The %' THEN substr(title, 5) ELSE title END COLLATE NOCASE, year
        """,
    )
    abstract fun searchMovies(q: String): Flow<List<Movie>>

    @Transaction
    @Query(
        """
        SELECT * FROM shows
        WHERE title LIKE '%' || :q || '%' OR folderName LIKE '%' || :q || '%'
        ORDER BY CASE WHEN title LIKE 'The %' THEN substr(title, 5) ELSE title END COLLATE NOCASE, year
        """,
    )
    abstract fun searchShows(q: String): Flow<List<ShowWithSeasons>>

    @Query("SELECT COUNT(*) FROM movies")
    abstract fun movieCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM shows")
    abstract fun showCount(): Flow<Int>

    @Insert
    protected abstract suspend fun insertMovies(movies: List<Movie>)

    @Insert
    protected abstract suspend fun insertShow(show: Show): Long

    @Insert
    protected abstract suspend fun insertSeasons(seasons: List<Season>)

    @Query("DELETE FROM seasons")
    protected abstract suspend fun clearSeasons()

    @Query("DELETE FROM shows")
    protected abstract suspend fun clearShows()

    @Query("DELETE FROM movies")
    protected abstract suspend fun clearMovies()

    /** Swaps in a fresh copy of the library in one transaction (the old one stays if anything fails). */
    @Transaction
    open suspend fun replaceAll(movies: List<Movie>, shows: List<ShowWithSeasons>) {
        clearSeasons()
        clearShows()
        clearMovies()
        insertMovies(movies)
        for (s in shows) {
            val id = insertShow(s.show)
            insertSeasons(s.seasons.map { it.copy(showId = id) })
        }
    }
}
