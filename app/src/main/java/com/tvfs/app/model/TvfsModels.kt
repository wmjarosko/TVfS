package com.tvfs.app.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Live TV channel model with EPG schedule metadata for the Bottom Drawer & Quick Peek (SDS 5.2).
 */
data class Channel(
    val id: Int,
    val number: String,
    val name: String,
    val category: String,
    val streamUrl: String,
    val currentProgram: String,
    val currentProgramDescription: String,
    val currentTimeRange: String,
    val progressFraction: Float,
    val nextProgram: String,
    val nextStartTime: String
)

/**
 * EPG Program entry stored with start/end epoch timestamps for SDS 7.2 24-hour retention guardrails.
 */
data class EpgProgramEntry(
    val channelId: Int,
    val title: String,
    val description: String,
    val startEpochMs: Long,
    val endEpochMs: Long
)

/**
 * Curated Movie model for MovieBrowserActivity (SDS 2.1).
 */
data class MovieItem(
    val id: Int,
    val title: String,
    val genre: String,
    val releaseYear: Int,
    val durationMinutes: Int,
    val description: String,
    val streamUrl: String
)

/**
 * Episode item within a curated TV Series (SDS 2.1 & 3.1).
 */
data class EpisodeItem(
    val id: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val durationMinutes: Int,
    val description: String,
    val streamUrl: String
) {
    val codeLabel: String
        get() = "S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"
}

/**
 * Curated TV Series model with single-click "Play Next Episode" support (SDS 2.1).
 */
data class SeriesItem(
    val id: Int,
    val title: String,
    val genre: String,
    val description: String,
    val episodes: List<EpisodeItem>
)

/**
 * Last-viewed playback state for the 4th Hub tile ("Last Viewed - Instant Resume", SDS 2.1).
 */
data class LastViewedState(
    val title: String,
    val subtitle: String,
    val streamUrl: String,
    val contentType: String, // "LIVE", "MOVIE", "SERIES"
    val channelId: Int = 101,
    val seriesId: Int = -1,
    val episodeIndex: Int = 0
)

/**
 * Search result badge types per SDS 6.1: [LIVE CHANNEL], [MOVIE], [SERIES].
 */
enum class SearchBadge(val displayBadge: String) {
    LIVE_CHANNEL("[LIVE CHANNEL]"),
    MOVIE("[MOVIE]"),
    SERIES("[SERIES]")
}

data class SearchResultItem(
    val id: Int,
    val badge: SearchBadge,
    val title: String,
    val subtitle: String,
    val streamUrl: String,
    val channelId: Int = -1,
    val seriesId: Int = -1
)

/**
 * Remote JSON Bootstrap Schema models (SDS 7.1).
 */
data class ProviderConfig(
    val serverUrl: String,
    val username: String,
    val password: String
)

data class CurationConfig(
    val enableFullProviderCatalog: Boolean,
    val allowedLiveCategories: List<String>,
    val allowedChannelIds: List<Int>,
    val favoriteSeriesIds: List<Int>,
    val favoriteMovieIds: List<Int>
)

data class BehaviorConfig(
    val bootMode: String,
    val retryAttempts: Int,
    val autoPlayLastChannel: Boolean
)

data class BootstrapConfig(
    val version: Int,
    val lastUpdated: String,
    val provider: ProviderConfig,
    val curation: CurationConfig,
    val behavior: BehaviorConfig
)

/**
 * Parser and serializer for the SDS 7.1 Remote JSON Bootstrap Schema.
 */
object BootstrapConfigParser {

    val DEFAULT_BOOTSTRAP_JSON = """
        {
          "version": 1,
          "last_updated": "2026-10-28T10:00:00Z",
          "provider": {
            "server_url": "http://provider-domain.xyz:8080",
            "username": "client_user_id",
            "password": "client_password"
          },
          "curation": {
            "enable_full_provider_catalog": false,
            "allowed_live_categories": ["Local News", "Sports Networks", "Classic TV"],
            "allowed_channel_ids": [101, 102, 103, 205, 301],
            "favorite_series_ids": [12, 45, 88],
            "favorite_movie_ids": [8901, 9022]
          },
          "behavior": {
            "boot_mode": "hub",
            "retry_attempts": 3,
            "auto_play_last_channel": true
          }
        }
    """.trimIndent()

    fun parse(jsonString: String): BootstrapConfig {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val lastUpdated = root.optString("last_updated", "2026-10-28T10:00:00Z")

        val providerObj = root.optJSONObject("provider") ?: JSONObject()
        val provider = ProviderConfig(
            serverUrl = providerObj.optString("server_url", "http://provider-domain.xyz:8080"),
            username = providerObj.optString("username", "client_user_id"),
            password = providerObj.optString("password", "client_password")
        )

        val curationObj = root.optJSONObject("curation") ?: JSONObject()
        val curation = CurationConfig(
            enableFullProviderCatalog = curationObj.optBoolean("enable_full_provider_catalog", false),
            allowedLiveCategories = curationObj.optJSONArray("allowed_live_categories").toStringList(
                defaultList = listOf("Local News", "Sports Networks", "Classic TV")
            ),
            allowedChannelIds = curationObj.optJSONArray("allowed_channel_ids").toIntList(
                defaultList = listOf(101, 102, 103, 205, 301)
            ),
            favoriteSeriesIds = curationObj.optJSONArray("favorite_series_ids").toIntList(
                defaultList = listOf(12, 45, 88)
            ),
            favoriteMovieIds = curationObj.optJSONArray("favorite_movie_ids").toIntList(
                defaultList = listOf(8901, 9022)
            )
        )

        val behaviorObj = root.optJSONObject("behavior") ?: JSONObject()
        val behavior = BehaviorConfig(
            bootMode = behaviorObj.optString("boot_mode", "hub"),
            retryAttempts = behaviorObj.optInt("retry_attempts", 3).coerceIn(1, 5),
            autoPlayLastChannel = behaviorObj.optBoolean("auto_play_last_channel", true)
        )

        return BootstrapConfig(
            version = version,
            lastUpdated = lastUpdated,
            provider = provider,
            curation = curation,
            behavior = behavior
        )
    }

    fun defaultConfig(): BootstrapConfig = parse(DEFAULT_BOOTSTRAP_JSON)

    private fun JSONArray?.toStringList(defaultList: List<String>): List<String> {
        if (this == null) return defaultList
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            list.add(optString(i))
        }
        return if (list.isEmpty()) defaultList else list
    }

    private fun JSONArray?.toIntList(defaultList: List<Int>): List<Int> {
        if (this == null) return defaultList
        val list = mutableListOf<Int>()
        for (i in 0 until length()) {
            list.add(optInt(i))
        }
        return if (list.isEmpty()) defaultList else list
    }
}
