package com.example.mediasearch.data

/** Turns folder names like "The Matrix (1999)", "The Matrix 1999" or "The.Matrix.1999" into title + year. */
object NameParser {

    data class TitleYear(val title: String, val year: Int?)

    private val trailingYear = Regex("""^(.*?)[\s._\-]*[(\[]?((?:18|19|20)\d{2})[)\]]?\s*$""")
    private val seasonNumber = Regex("""(?i)\b(?:season|series|s)\s*0*(\d{1,3})\b""")
    private val whitespace = Regex("""\s+""")

    fun parse(folder: String): TitleYear {
        val name = folder.trim()
        val m = trailingYear.matchEntire(name)
        return if (m != null && m.groupValues[1].isNotBlank()) {
            TitleYear(clean(m.groupValues[1]), m.groupValues[2].toInt())
        } else {
            TitleYear(clean(name), null) // e.g. a movie literally called "1917" with no year
        }
    }

    /** "Season 1", "Season 01", "S02", "Series 3" → number; "Specials" → 0; anything else → null. */
    fun seasonNumber(folder: String): Int? {
        seasonNumber.find(folder)?.let { return it.groupValues[1].toInt() }
        if (folder.contains("special", ignoreCase = true)) return 0
        return null
    }

    private fun clean(s: String): String {
        // Only treat dots/underscores as separators when the name has no real spaces ("Mr. Robot" stays intact).
        val spaced = if (' ' in s) s else s.replace('.', ' ').replace('_', ' ')
        return spaced.replace(whitespace, " ").trim().trimEnd('-', ' ')
    }
}
