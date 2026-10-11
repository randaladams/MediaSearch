package com.example.mediasearch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Finds the IMDb page for a title + year.
 *
 * Uses IMDb's public search-suggestion feed (what imdb.com's own search box uses) to get the exact
 * title ID. That feed is unofficial, so on any failure or when no result matches the year, we fall
 * back to IMDb's search results page — a tap never lands on the wrong film.
 */
object Imdb {
    private val movieKinds = setOf("movie", "tvMovie", "video", "short", "tvSpecial")
    private val showKinds = setOf("tvSeries", "tvMiniSeries")

    suspend fun movieUrl(title: String, year: Int?): String = url(title, year, movieKinds, "ft")
    suspend fun showUrl(title: String, year: Int?): String = url(title, year, showKinds, "tv")

    private suspend fun url(title: String, year: Int?, kinds: Set<String>, searchType: String): String =
        withContext(Dispatchers.IO) {
            runCatching { lookup(title, year, kinds) }.getOrNull()
                ?.let { "https://www.imdb.com/title/$it/" }
                ?: ("https://www.imdb.com/find/?q=" + enc(listOfNotNull(title, year?.toString()).joinToString(" ")) +
                    "&s=tt&ttype=$searchType")
        }

    private fun lookup(title: String, year: Int?, kinds: Set<String>): String? {
        val q = title.lowercase().trim()
        val first = q.firstOrNull { it.isLetterOrDigit() } ?: 'x'
        val conn = URL("https://v3.sg.media-imdb.com/suggestion/${enc(first.toString())}/${enc(q)}.json")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 4_000
        conn.readTimeout = 4_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MediaSearch")
        try {
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val arr = JSONObject(body).optJSONArray("d") ?: return null
            val candidates = (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optString("id").startsWith("tt") && it.optString("qid") in kinds }
            val match = if (year != null) {
                // Allow ±1 year: release years differ between countries and festival dates.
                candidates.firstOrNull { it.optInt("y") == year }
                    ?: candidates.firstOrNull { kotlin.math.abs(it.optInt("y") - year) == 1 }
            } else {
                candidates.firstOrNull()
            }
            return match?.optString("id")
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
