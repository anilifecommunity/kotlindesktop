package me.enivzoy.anilife.core.model

import java.time.LocalDate

 
private val FORMATS = mapOf(
    "TV" to "TV",
    "TV_SHORT" to "TV Short",
    "MOVIE" to "Movie",
    "SPECIAL" to "Special",
    "OVA" to "OVA",
    "ONA" to "ONA",
    "MUSIC" to "Music",
)

private val STATUSES = mapOf(
    "RELEASING" to "Airing",
    "FINISHED" to "Finished",
    "NOT_YET_RELEASED" to "Upcoming",
    "CANCELLED" to "Cancelled",
    "HIATUS" to "Hiatus",
)

fun formatLabel(format: String?): String? = format?.let { FORMATS[it] ?: it }

fun statusLabel(status: String?): String? = status?.let { STATUSES[it] ?: it }

 
fun seasonLabel(season: String?, year: Int?): String? {
    if (year == null) return null
    if (season.isNullOrBlank()) return year.toString()
    return season.lowercase().replaceFirstChar { it.uppercase() } + " $year"
}

 
fun seasonOf(date: LocalDate = LocalDate.now()): String = when (date.monthValue) {
    1, 2, 3 -> "WINTER"
    4, 5, 6 -> "SPRING"
    7, 8, 9 -> "SUMMER"
    else -> "FALL"
}

 



fun seasonYearOf(date: LocalDate = LocalDate.now()): Int =
    if (date.monthValue == 12) date.year + 1 else date.year

 
fun countdown(airingAt: Long, now: Long = System.currentTimeMillis() / 1000): String {
    val delta = (airingAt - now).coerceAtLeast(0)
    if (delta < 60) return "now"
    val days = delta / 86_400
    val hours = (delta % 86_400) / 3_600
    val minutes = (delta % 3_600) / 60
    return when {
        days > 0 -> "in ${days}d ${hours}h"
        hours > 0 -> "in ${hours}h ${minutes}m"
        else -> "in ${minutes}m"
    }
}

 
fun plainDescription(text: String?, limit: Int? = null): String {
    if (text.isNullOrBlank()) return ""
    val plain = text
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    if (limit != null && plain.length > limit) return plain.take(limit).trimEnd() + "…"
    return plain
}

 
val GENRES = listOf(
    "Action",
    "Adventure",
    "Comedy",
    "Drama",
    "Ecchi",
    "Fantasy",
    "Horror",
    "Mahou Shoujo",
    "Mecha",
    "Music",
    "Mystery",
    "Psychological",
    "Romance",
    "Sci-Fi",
    "Slice of Life",
    "Sports",
    "Supernatural",
    "Thriller",
)

