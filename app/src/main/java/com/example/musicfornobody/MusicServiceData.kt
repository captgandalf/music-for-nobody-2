package com.example.musicfornobody

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

enum class MusicPlatform(val apiKey: String, val displayName: String) {
    ASK_EVERY_TIME("ask", "Ask Every Time"),
    TIDAL("tidal", "Tidal"),
    YOUTUBE_MUSIC("youtubeMusic", "YouTube Music"),
    SPOTIFY("spotify", "Spotify"),
    APPLE_MUSIC("appleMusic", "Apple Music"),
    YOUTUBE("youtube", "YouTube"),
    AMAZON_MUSIC("amazonMusic", "Amazon Music"),
    DEEZER("deezer", "Deezer"),
    SOUNDCLOUD("soundcloud", "SoundCloud");

    companion object {
        val selectableTargets = entries.filter { it != ASK_EVERY_TIME }
        fun fromKey(key: String?): MusicPlatform =
            entries.find { it.apiKey == key } ?: ASK_EVERY_TIME
    }
}

enum class AutoAction(val key: String, val label: String) {
    COPY_LINK("copy", "Copy translated link to clipboard"),
    OPEN_APP("open", "Open translated link immediately"),
    BOTH("both", "Copy link & open immediately");

    companion object {
        fun fromKey(key: String?): AutoAction =
            entries.find { it.key == key } ?: COPY_LINK
    }
}

data class SonglinkResponse(
    val entityUniqueId: String?,
    val pageUrl: String?,
    val linksByPlatform: Map<String, PlatformLink>?,
    val entitiesByUniqueId: Map<String, TrackEntity>?
)

data class PlatformLink(
    val url: String,
    val nativeAppUriMobile: String? = null
)

data class TrackEntity(
    val title: String?,
    val artistName: String?,
    val thumbnailUrl: String?
)

interface SonglinkApi {
    @GET("v1-alpha.1/links")
    suspend fun resolveMusicLink(
        @Query("url") url: String,
        @Query("userCountry") userCountry: String = "US"
    ): SonglinkResponse

    companion object {
        fun create(): SonglinkApi {
            return Retrofit.Builder()
                .baseUrl("https://api.song.link/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SonglinkApi::class.java)
        }
    }
}
