package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard

internal fun AnimeCard.withTitlePreference(language: String): AnimeCard = copy(
    title = when (language) {
        "romaji" -> titleRomaji ?: titleEnglish ?: titleNative ?: title
        "native" -> titleNative ?: titleRomaji ?: titleEnglish ?: title
        else -> titleEnglish ?: titleRomaji ?: titleNative ?: title
    },
)