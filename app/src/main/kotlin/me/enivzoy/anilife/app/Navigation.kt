package me.enivzoy.anilife.app

import me.enivzoy.anilife.app.data.AnimeVideoSource
 
sealed interface Screen {
    data object Home : Screen
    data object Browse : Screen
    data object Schedule : Screen
    data object Library : Screen
    data object Downloads : Screen
    data object Providers : Screen
    data object Notifications : Screen
    data object Profile : Screen
    data object Settings : Screen
    data class Detail(val id: Long) : Screen
    data class Watch(val detail: me.enivzoy.anilife.core.model.AnimeDetail, val initialEpisode: Double? = null) : Screen
    data class Player(
        val title: String,
        val url: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val alternatives: List<AnimeVideoSource> = emptyList(),
        val audioTrack: String = "Sub",
        val detail: me.enivzoy.anilife.core.model.AnimeDetail? = null,
        val episode: Double? = null,
        val episodeTitle: String? = null,
        val episodes: List<Double> = emptyList(),
        val episodeTitles: Map<Double, String> = emptyMap(),
        val episodeThumbnails: Map<Double, String> = emptyMap(),
    ) : Screen
}

