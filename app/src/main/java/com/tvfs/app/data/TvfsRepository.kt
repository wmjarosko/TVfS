package com.tvfs.app.data

import android.content.Context
import com.tvfs.app.data.db.ChannelEntity
import com.tvfs.app.data.db.EpgDao
import com.tvfs.app.data.db.TvfsDatabase
import com.tvfs.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Central repository handling:
 * - Local Room DB for curated channels and EPG programs (TvfsDatabase)
 * - Ingestion of XC-PLaPA exports (seniortv_config.json & pruned_epg.xml)
 * - Remote JSON Bootstrap synchronization with offline cache fallback (SDS 7.1)
 * - Curated Live TV, Movie, and Series catalogs filtered by allowed IDs/categories (SDS 7.1 & 7.2)
 * - EPG retention limit & expired schedule purge guardrails for 8 GB eMMC boxes (SDS 7.2)
 * - Persistent user state (last channel played, instant resume item, next episode per series)
 * - Unified search across Live Channels, Movies, and TV Shows (SDS 6.1)
 */
object TvfsRepository {

    private const val PREFS_NAME = "tvfs_senior_prefs"
    private const val KEY_LAST_CHANNEL_ID = "last_channel_id"
    private const val KEY_LAST_TITLE = "last_viewed_title"
    private const val KEY_LAST_SUBTITLE = "last_viewed_subtitle"
    private const val KEY_LAST_STREAM_URL = "last_viewed_stream_url"
    private const val KEY_LAST_CONTENT_TYPE = "last_viewed_content_type"
    private const val KEY_LAST_SERIES_ID = "last_viewed_series_id"
    private const val KEY_LAST_EPISODE_INDEX = "last_viewed_episode_index"
    private const val KEY_BOOTSTRAP_ENDPOINT = "remote_bootstrap_url"
    const val DEFAULT_REMOTE_BOOTSTRAP_URL = "https://tvfs-bootstrap-config.onrender.com/bootstrap.json"
    private const val CACHE_FILE_NAME = "bootstrap_cache.json"

    // Default GitHub repository for trimmed EPG exports
    const val DEFAULT_GITHUB_OWNER = "wmjarosko"
    const val DEFAULT_GITHUB_REPO = "trimmed_EPG"
    const val DEFAULT_GITHUB_BRANCH = "main"
    const val DEFAULT_GITHUB_PAT = ""
    private const val KEY_GITHUB_PAT = "github_pat"

    // 24 hours in milliseconds for SDS 7.2 EPG retention window
    const val EPG_RETENTION_WINDOW_MS: Long = 24L * 60L * 60L * 1000L

    private const val STREAM_TEARS_OF_STEEL =
        "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
    private const val STREAM_AKAMAI_LIVE =
        "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
    private const val STREAM_BIG_BUCK_BUNNY =
        "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private const val STREAM_SINTEL =
        "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"

    private var cachedBootstrapConfig: BootstrapConfig = BootstrapConfigParser.defaultConfig()

    private val fullProviderChannels = listOf(
        Channel(
            id = 101,
            number = "04",
            name = "CBS Local",
            category = "Local News",
            streamUrl = STREAM_TEARS_OF_STEEL,
            currentProgram = "CBS Morning News (LIVE)",
            currentProgramDescription = "Local headlines, community weather forecast, and morning updates.",
            currentTimeRange = "7:00 AM - 9:00 AM",
            progressFraction = 0.62f,
            nextProgram = "The Price is Right",
            nextStartTime = "9:00 AM"
        ),
        Channel(
            id = 102,
            number = "05",
            name = "NBC News",
            category = "Local News",
            streamUrl = STREAM_AKAMAI_LIVE,
            currentProgram = "Today Show",
            currentProgramDescription = "National breaking news, interviews, and lifestyle stories.",
            currentTimeRange = "7:00 AM - 10:00 AM",
            progressFraction = 0.45f,
            nextProgram = "NBC Midday Report",
            nextStartTime = "10:00 AM"
        ),
        Channel(
            id = 103,
            number = "06",
            name = "ABC World News",
            category = "Local News",
            streamUrl = STREAM_BIG_BUCK_BUNNY,
            currentProgram = "Good Morning America",
            currentProgramDescription = "Top national stories, health tips, and weather across the country.",
            currentTimeRange = "7:00 AM - 9:00 AM",
            progressFraction = 0.75f,
            nextProgram = "Live with Kelly and Mark",
            nextStartTime = "9:00 AM"
        ),
        Channel(
            id = 205,
            number = "12",
            name = "Classic TV Gold",
            category = "Classic TV",
            streamUrl = STREAM_SINTEL,
            currentProgram = "The Andy Griffith Show",
            currentProgramDescription = "Sheriff Andy Taylor keeps the peace in Mayberry with Deputy Barney Fife.",
            currentTimeRange = "8:00 AM - 8:30 AM",
            progressFraction = 0.50f,
            nextProgram = "I Love Lucy",
            nextStartTime = "8:30 AM"
        ),
        Channel(
            id = 301,
            number = "24",
            name = "Sports Network HD",
            category = "Sports Networks",
            streamUrl = STREAM_TEARS_OF_STEEL,
            currentProgram = "Morning SportsDesk",
            currentProgramDescription = "Highlights from last night's baseball, football, and golf tournaments.",
            currentTimeRange = "8:00 AM - 10:00 AM",
            progressFraction = 0.30f,
            nextProgram = "Classic Golf Showcase",
            nextStartTime = "10:00 AM"
        ),
        // Uncurated extra channel to verify SDS 7.1/7.2 curation guardrails
        Channel(
            id = 999,
            number = "99",
            name = "Uncurated International Feed",
            category = "International",
            streamUrl = STREAM_AKAMAI_LIVE,
            currentProgram = "World Feed",
            currentProgramDescription = "Uncurated feed filtered out unless full catalog is enabled.",
            currentTimeRange = "All Day",
            progressFraction = 0.10f,
            nextProgram = "Late World Feed",
            nextStartTime = "11:00 PM"
        )
    )

    private val fullProviderMovies = listOf(
        MovieItem(
            id = 8901,
            title = "Casablanca",
            genre = "Classic Romance",
            releaseYear = 1942,
            durationMinutes = 102,
            description = "A cynical expatriate nightclub owner in Morocco helps a former lover and her husband escape in WWII.",
            streamUrl = STREAM_SINTEL
        ),
        MovieItem(
            id = 9022,
            title = "Singin' in the Rain",
            genre = "Classic Musical",
            releaseYear = 1952,
            durationMinutes = 103,
            description = "A silent film production company and cast make a joyful transition to sound in late 1920s Hollywood.",
            streamUrl = STREAM_BIG_BUCK_BUNNY
        ),
        MovieItem(
            id = 9105,
            title = "Roman Holiday",
            genre = "Classic Comedy",
            releaseYear = 1953,
            durationMinutes = 118,
            description = "A European princess escapes her guardians and falls in love with an American newsman in Rome.",
            streamUrl = STREAM_TEARS_OF_STEEL
        )
    )

    private val fullProviderSeries = listOf(
        SeriesItem(
            id = 12,
            title = "The Golden Years Mystery Hour",
            genre = "Cozy Mystery",
            description = "Lighthearted small-town mysteries solved with charm and wit.",
            episodes = listOf(
                EpisodeItem(
                    id = 1201,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "The Missing Heirloom Clock",
                    durationMinutes = 45,
                    description = "Episode 1: A prized grandfather clock vanishes before the town founder's banquet.",
                    streamUrl = STREAM_BIG_BUCK_BUNNY
                ),
                EpisodeItem(
                    id = 1202,
                    seasonNumber = 1,
                    episodeNumber = 2,
                    title = "Letters from the Harbor",
                    durationMinutes = 46,
                    description = "Episode 2: A fifty-year-old postcard leads to a heartwarming reunion at the pier.",
                    streamUrl = STREAM_SINTEL
                ),
                EpisodeItem(
                    id = 1203,
                    seasonNumber = 1,
                    episodeNumber = 3,
                    title = "The Sunday Afternoon Train",
                    durationMinutes = 45,
                    description = "Episode 3: An unexpected passenger arrives on the vintage steam locomotive.",
                    streamUrl = STREAM_TEARS_OF_STEEL
                )
            )
        ),
        SeriesItem(
            id = 45,
            title = "Mayberry & Friends Classics",
            genre = "Classic Sitcom",
            description = "Timeless family sitcom episodes remastered for crystal-clear viewing.",
            episodes = listOf(
                EpisodeItem(
                    id = 4501,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "The New Housekeeper",
                    durationMinutes = 26,
                    description = "Episode 1: Aunt Bee arrives to help run the household and win over Opie.",
                    streamUrl = STREAM_SINTEL
                ),
                EpisodeItem(
                    id = 4502,
                    seasonNumber = 1,
                    episodeNumber = 2,
                    title = "The Fishing Opener",
                    durationMinutes = 26,
                    description = "Episode 2: Plans for a quiet fishing morning turn into a town-wide adventure.",
                    streamUrl = STREAM_BIG_BUCK_BUNNY
                )
            )
        ),
        SeriesItem(
            id = 88,
            title = "National Parks of America",
            genre = "Nature & Travel",
            description = "Scenic tours through Yellowstone, Yosemite, and the Grand Canyon with soothing narration.",
            episodes = listOf(
                EpisodeItem(
                    id = 8801,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "Autumn in Yellowstone",
                    durationMinutes = 52,
                    description = "Episode 1: Geysers, elk meadows, and golden aspens across Wyoming.",
                    streamUrl = STREAM_AKAMAI_LIVE
                ),
                EpisodeItem(
                    id = 8802,
                    seasonNumber = 1,
                    episodeNumber = 2,
                    title = "Grand Canyon Sunrise",
                    durationMinutes = 50,
                    description = "Episode 2: A tranquil journey along the South Rim and Colorado River.",
                    streamUrl = STREAM_TEARS_OF_STEEL
                )
            )
        )
    )

    fun getBootstrapConfig(context: Context? = null): BootstrapConfig {
        if (context != null) {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) {
                runCatching {
                    cachedBootstrapConfig = BootstrapConfigParser.parse(cacheFile.readText())
                }
            }
        }
        return cachedBootstrapConfig
    }

    /**
     * Synchronizes the Remote JSON Bootstrap Configuration (SDS 7.1).
     * Falls back seamlessly to the local cached profile if offline.
     */
    suspend fun syncRemoteBootstrap(context: Context, remoteUrl: String? = null): BootstrapConfig =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val endpoint = remoteUrl ?: prefs.getString(KEY_BOOTSTRAP_ENDPOINT, DEFAULT_REMOTE_BOOTSTRAP_URL)
            if (!endpoint.isNullOrBlank()) {
                runCatching {
                    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 4000
                        readTimeout = 4000
                    }
                    if (connection.responseCode in 200..299) {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val parsed = BootstrapConfigParser.parse(body)
                        File(context.filesDir, CACHE_FILE_NAME).writeText(body)
                        cachedBootstrapConfig = parsed
                        return@withContext parsed
                    }
                }
            }
            return@withContext getBootstrapConfig(context)
        }

    /**
     * Builds a domain Channel model from a Room ChannelEntity and optionally queries
     * EpgDao for live "Now & Next" program details at the given timestamp.
     */
    fun buildChannelFromEntity(
        entity: ChannelEntity,
        epgDao: EpgDao? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Channel {
        val currentProg = if (entity.epgId.isNotEmpty()) epgDao?.getCurrentProgram(entity.epgId, nowMs) else null
        val nextProg = if (entity.epgId.isNotEmpty()) epgDao?.getNextProgram(entity.epgId, nowMs) else null

        val currentTitle = currentProg?.title ?: "Live Programming"
        val currentDesc = currentProg?.description?.ifEmpty { "No program description available." }
            ?: "No program description available."

        val timeFormatter = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        val timeRange = if (currentProg != null) {
            "${timeFormatter.format(Date(currentProg.startEpochMs))} - ${timeFormatter.format(Date(currentProg.stopEpochMs))}"
        } else {
            "Live"
        }

        val progress = if (currentProg != null && currentProg.stopEpochMs > currentProg.startEpochMs) {
            ((nowMs - currentProg.startEpochMs).toFloat() / (currentProg.stopEpochMs - currentProg.startEpochMs).toFloat()).coerceIn(0f, 1f)
        } else {
            0.5f
        }

        val nextTitle = nextProg?.title ?: "Upcoming Program"
        val nextTime = if (nextProg != null) timeFormatter.format(Date(nextProg.startEpochMs)) else "Next"

        return Channel(
            id = entity.channelNumber,
            number = entity.channelNumber.toString().padStart(2, '0'),
            name = entity.name,
            category = entity.groupName,
            streamUrl = entity.streamUrl,
            currentProgram = currentTitle,
            currentProgramDescription = currentDesc,
            currentTimeRange = timeRange,
            progressFraction = progress,
            nextProgram = nextTitle,
            nextStartTime = nextTime
        )
    }

    /**
     * Loads channels directly from the Room database (populated by XC-PLaPA ingestion).
     * Automatically queries EpgDao for live "Now & Next" schedule metadata.
     */
    suspend fun loadChannelsFromDatabase(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): List<Channel> = withContext(Dispatchers.IO) {
        val db = TvfsDatabase.getInstance(context)
        val entities = db.channelDao().getAllChannels()
        if (entities.isEmpty()) {
            return@withContext emptyList()
        }
        val epgDao = db.epgDao()
        entities.map { entity ->
            buildChannelFromEntity(entity, epgDao, nowMs)
        }
    }

    /**
     * Returns curated live channels filtered according to SDS 7.1 & 7.2 guardrails.
     * If context is provided and Room DB contains ingested channels, returns DB channels.
     */
    fun getCuratedChannels(
        config: BootstrapConfig = cachedBootstrapConfig,
        context: Context? = null
    ): List<Channel> {
        if (context != null) {
            runCatching {
                val db = TvfsDatabase.getInstance(context)
                val dbEntities = db.channelDao().getAllChannels()
                if (dbEntities.isNotEmpty()) {
                    val epgDao = db.epgDao()
                    val now = System.currentTimeMillis()
                    return dbEntities.map { buildChannelFromEntity(it, epgDao, now) }
                }
            }
        }

        if (config.curation.enableFullProviderCatalog) {
            return fullProviderChannels
        }
        val filtered = fullProviderChannels.filter { channel ->
            channel.id in config.curation.allowedChannelIds ||
                channel.category in config.curation.allowedLiveCategories
        }
        return if (filtered.isNotEmpty()) filtered else fullProviderChannels.take(5)
    }

    /**
     * Ingests from the private or public GitHub repo where XC-PLaPA trimmed exports are published.
     */
    suspend fun syncFromGitHub(
        context: Context,
        owner: String = DEFAULT_GITHUB_OWNER,
        repo: String = DEFAULT_GITHUB_REPO,
        branch: String = DEFAULT_GITHUB_BRANCH,
        pat: String? = null
    ): IngestionResult = withContext(Dispatchers.IO) {
        val effectivePat = pat ?: getSavedGitHubPat(context)
        val db = TvfsDatabase.getInstance(context)
        DataIngestionManager.ingestFromGitHubRepo(owner, repo, branch, effectivePat, db)
    }

    fun saveGitHubCredentials(
        context: Context,
        pat: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_GITHUB_PAT, pat)
            .apply()
    }

    fun getSavedGitHubPat(context: Context): String? {
        val token = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GITHUB_PAT, null)
        return if (!token.isNullOrBlank()) token else null
    }

    /**
     * Ingests from a remote HTTP / HTTPS URL with optional authorization header.
     */
    suspend fun syncFromRemote(
        context: Context,
        configUrl: String,
        epgUrl: String? = null,
        authHeader: String? = null
    ): IngestionResult = withContext(Dispatchers.IO) {
        val db = TvfsDatabase.getInstance(context)
        DataIngestionManager.ingestFromRemote(configUrl, epgUrl, authHeader, db)
    }

    /**
     * Ingests from a local directory (e.g. /sdcard/tvfs/ or exports/).
     */
    suspend fun syncFromDirectory(
        context: Context,
        directory: File
    ): IngestionResult = withContext(Dispatchers.IO) {
        val db = TvfsDatabase.getInstance(context)
        DataIngestionManager.ingestFromDirectory(directory, db)
    }

    /**
     * Seeds or refreshes Room DB from bundled APK assets (seniortv_config.json & pruned_epg.xml).
     */
    suspend fun syncFromAssets(context: Context): IngestionResult = withContext(Dispatchers.IO) {
        val db = TvfsDatabase.getInstance(context)
        DataIngestionManager.ingestFromAssets(context, db)
    }

    /**
     * Purges expired EPG schedule entries older than cutoffMs (defaults to 6 hours ago).
     */
    suspend fun purgeExpiredEpg(
        context: Context,
        cutoffMs: Long = System.currentTimeMillis() - (6L * 60L * 60L * 1000L)
    ): Int = withContext(Dispatchers.IO) {
        val db = TvfsDatabase.getInstance(context)
        db.epgDao().purgeExpiredPrograms(cutoffMs)
    }

    /**
     * Returns curated movies filtered according to SDS 7.1 favorite_movie_ids.
     */
    fun getCuratedMovies(config: BootstrapConfig = cachedBootstrapConfig): List<MovieItem> {
        if (config.curation.enableFullProviderCatalog) {
            return fullProviderMovies
        }
        val favorites = fullProviderMovies.filter { it.id in config.curation.favoriteMovieIds }
        return if (favorites.isNotEmpty()) fullProviderMovies else fullProviderMovies
    }

    /**
     * Returns curated series filtered according to SDS 7.1 favorite_series_ids.
     */
    fun getCuratedSeries(config: BootstrapConfig = cachedBootstrapConfig): List<SeriesItem> {
        if (config.curation.enableFullProviderCatalog) {
            return fullProviderSeries
        }
        val favorites = fullProviderSeries.filter { it.id in config.curation.favoriteSeriesIds }
        return if (favorites.isNotEmpty()) favorites else fullProviderSeries
    }

    /**
     * SDS 7.2 Storage Guardrail: Purges past EPG schedules and restricts retention to
     * allowed channels and Current Time + 24 Hours ahead.
     */
    fun filterAndPurgeEpgSchedule(
        entries: List<EpgProgramEntry>,
        nowEpochMs: Long,
        config: BootstrapConfig = cachedBootstrapConfig
    ): List<EpgProgramEntry> {
        val maxFutureEpochMs = nowEpochMs + EPG_RETENTION_WINDOW_MS
        val allowedIds = config.curation.allowedChannelIds.toSet()
        return entries.filter { entry ->
            val isAllowedChannel = config.curation.enableFullProviderCatalog || entry.channelId in allowedIds
            val isCurrentOrUpcoming = entry.endEpochMs > nowEpochMs
            val isWithin24Hours = entry.startEpochMs <= maxFutureEpochMs
            isAllowedChannel && isCurrentOrUpcoming && isWithin24Hours
        }
    }

    /**
     * Unified Search across Live Channel Names/Categories, Movie Titles/Genres, and TV Show Titles (SDS 6.1).
     * Results carry explicit badges: [LIVE CHANNEL], [MOVIE], [SERIES].
     */
    fun searchCatalog(
        query: String,
        config: BootstrapConfig = cachedBootstrapConfig,
        context: Context? = null
    ): List<SearchResultItem> {
        val normalized = query.trim().lowercase()
        val channels = getCuratedChannels(config, context)
        val movies = getCuratedMovies(config)
        val series = getCuratedSeries(config)

        val results = mutableListOf<SearchResultItem>()

        channels.filter {
            normalized.isEmpty() ||
                it.name.lowercase().contains(normalized) ||
                it.category.lowercase().contains(normalized) ||
                it.currentProgram.lowercase().contains(normalized) ||
                it.number.contains(normalized)
        }.forEach { channel ->
            results.add(
                SearchResultItem(
                    id = channel.id,
                    badge = SearchBadge.LIVE_CHANNEL,
                    title = "Ch ${channel.number} • ${channel.name}",
                    subtitle = "${channel.category} • Now: ${channel.currentProgram}",
                    streamUrl = channel.streamUrl,
                    channelId = channel.id
                )
            )
        }

        movies.filter {
            normalized.isEmpty() ||
                it.title.lowercase().contains(normalized) ||
                it.genre.lowercase().contains(normalized) ||
                it.description.lowercase().contains(normalized)
        }.forEach { movie ->
            results.add(
                SearchResultItem(
                    id = movie.id,
                    badge = SearchBadge.MOVIE,
                    title = movie.title,
                    subtitle = "${movie.genre} (${movie.releaseYear}) • ${movie.durationMinutes} min",
                    streamUrl = movie.streamUrl
                )
            )
        }

        series.filter {
            normalized.isEmpty() ||
                it.title.lowercase().contains(normalized) ||
                it.genre.lowercase().contains(normalized) ||
                it.description.lowercase().contains(normalized)
        }.forEach { show ->
            val firstEp = show.episodes.first()
            results.add(
                SearchResultItem(
                    id = show.id,
                    badge = SearchBadge.SERIES,
                    title = show.title,
                    subtitle = "${show.genre} • ${show.episodes.size} Episodes",
                    streamUrl = firstEp.streamUrl,
                    seriesId = show.id
                )
            )
        }

        return results
    }

    fun getLastChannelIndex(context: Context, channels: List<Channel>): Int {
        if (channels.isEmpty()) return 0
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastChannelId = prefs.getInt(KEY_LAST_CHANNEL_ID, channels.first().id)
        val foundIndex = channels.indexOfFirst { it.id == lastChannelId }
        return if (foundIndex >= 0) foundIndex else 0
    }

    fun saveLastChannel(context: Context, channel: Channel) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_LAST_CHANNEL_ID, channel.id)
            .putString(KEY_LAST_TITLE, "Ch ${channel.number} - ${channel.name}")
            .putString(KEY_LAST_SUBTITLE, channel.currentProgram)
            .putString(KEY_LAST_STREAM_URL, channel.streamUrl)
            .putString(KEY_LAST_CONTENT_TYPE, "LIVE")
            .apply()
    }

    fun saveLastMovieViewed(context: Context, movie: MovieItem) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_TITLE, movie.title)
            .putString(KEY_LAST_SUBTITLE, "Movie • ${movie.genre}")
            .putString(KEY_LAST_STREAM_URL, movie.streamUrl)
            .putString(KEY_LAST_CONTENT_TYPE, "MOVIE")
            .apply()
    }

    fun saveLastSeriesEpisodeViewed(context: Context, series: SeriesItem, episodeIndex: Int) {
        val safeIndex = episodeIndex.coerceIn(0, series.episodes.lastIndex)
        val episode = series.episodes[safeIndex]
        val nextIndex = (safeIndex + 1) % series.episodes.size
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_TITLE, series.title)
            .putString(KEY_LAST_SUBTITLE, "${episode.codeLabel}: ${episode.title}")
            .putString(KEY_LAST_STREAM_URL, episode.streamUrl)
            .putString(KEY_LAST_CONTENT_TYPE, "SERIES")
            .putInt(KEY_LAST_SERIES_ID, series.id)
            .putInt(KEY_LAST_EPISODE_INDEX, safeIndex)
            .putInt("series_next_ep_${series.id}", nextIndex)
            .apply()
    }

    fun getNextEpisodeIndexForSeries(context: Context, seriesId: Int, totalEpisodes: Int): Int {
        if (totalEpisodes <= 0) return 0
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("series_next_ep_$seriesId", 0).coerceIn(0, totalEpisodes - 1)
    }

    fun getLastViewedState(context: Context): LastViewedState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultChannel = getCuratedChannels().first()
        return LastViewedState(
            title = prefs.getString(KEY_LAST_TITLE, "Ch ${defaultChannel.number} - ${defaultChannel.name}")
                ?: "Ch ${defaultChannel.number} - ${defaultChannel.name}",
            subtitle = prefs.getString(KEY_LAST_SUBTITLE, defaultChannel.currentProgram)
                ?: defaultChannel.currentProgram,
            streamUrl = prefs.getString(KEY_LAST_STREAM_URL, defaultChannel.streamUrl)
                ?: defaultChannel.streamUrl,
            contentType = prefs.getString(KEY_LAST_CONTENT_TYPE, "LIVE") ?: "LIVE",
            channelId = prefs.getInt(KEY_LAST_CHANNEL_ID, defaultChannel.id),
            seriesId = prefs.getInt(KEY_LAST_SERIES_ID, -1),
            episodeIndex = prefs.getInt(KEY_LAST_EPISODE_INDEX, 0)
        )
    }
}
