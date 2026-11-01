package com.tvfs.app.data

import android.content.Context
import android.util.Log
import com.tvfs.app.data.db.ChannelEntity
import com.tvfs.app.data.db.TvfsDatabase
import com.tvfs.app.data.parser.SeniorTvConfigParser
import com.tvfs.app.data.parser.XmltvPullParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

data class IngestionResult(
    val success: Boolean,
    val channelCount: Int = 0,
    val programCount: Int = 0,
    val sourceDescription: String = "",
    val errorMessage: String? = null
)

/**
 * Orchestrates ingestion of XC-PLaPA exports (seniortv_config.json & pruned_epg.xml)
 * into the local Room database (TvfsDatabase).
 *
 * Supports:
 * - Direct Content / Strings
 * - Local Files (e.g. adb pushed /sdcard/tvfs/ or exports/ directory)
 * - Remote HTTP / HTTPS endpoints with optional Bearer/Token auth
 * - Private / Public GitHub repositories (such as https://github.com/wmjarosko/trimmed_EPG)
 * - Bundled APK assets fallback
 */
object DataIngestionManager {

    private const val TAG = "DataIngestionManager"
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000

    /**
     * Ingests from raw strings / streams into Room.
     */
    suspend fun ingestFromContent(
        configJson: String,
        epgInputStream: InputStream?,
        database: TvfsDatabase,
        filterExpiredBeforeMs: Long? = null
    ): IngestionResult = withContext(Dispatchers.IO) {
        try {
            val parsedConfig = SeniorTvConfigParser.parse(configJson)
            database.channelDao().replaceAllChannels(parsedConfig.channels)

            var epgCount = 0
            if (epgInputStream != null) {
                val programs = XmltvPullParser.parse(epgInputStream, filterExpiredBeforeMs)
                database.epgDao().replaceAllPrograms(programs)
                epgCount = programs.size
            }

            IngestionResult(
                success = true,
                channelCount = parsedConfig.channels.size,
                programCount = epgCount,
                sourceDescription = "In-memory content"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ingest from content: ${e.message}", e)
            IngestionResult(
                success = false,
                errorMessage = e.message ?: "Unknown content ingestion error"
            )
        }
    }

    /**
     * Ingests from local File objects.
     */
    suspend fun ingestFromFiles(
        configFile: File,
        epgFile: File?,
        database: TvfsDatabase,
        filterExpiredBeforeMs: Long? = null
    ): IngestionResult = withContext(Dispatchers.IO) {
        if (!configFile.exists()) {
            return@withContext IngestionResult(
                success = false,
                errorMessage = "Config file does not exist: ${configFile.absolutePath}"
            )
        }

        try {
            val jsonText = configFile.readText(Charsets.UTF_8)
            val epgStream = if (epgFile != null && epgFile.exists()) {
                FileInputStream(epgFile)
            } else {
                null
            }

            epgStream.use { stream ->
                val result = ingestFromContent(jsonText, stream, database, filterExpiredBeforeMs)
                result.copy(sourceDescription = "Local files (${configFile.name})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ingest from files: ${e.message}", e)
            IngestionResult(
                success = false,
                errorMessage = e.message ?: "Failed reading local export files"
            )
        }
    }

    /**
     * Searches a local directory for seniortv_config.json and pruned_epg.xml.
     */
    suspend fun ingestFromDirectory(
        directory: File,
        database: TvfsDatabase,
        filterExpiredBeforeMs: Long? = null
    ): IngestionResult {
        val configFile = File(directory, "seniortv_config.json")
        val epgFile = File(directory, "pruned_epg.xml")
        return ingestFromFiles(configFile, epgFile, database, filterExpiredBeforeMs)
    }

    /**
     * Ingests from remote HTTP / HTTPS URLs with optional Authorization header (Bearer or token).
     */
    suspend fun ingestFromRemote(
        configUrl: String,
        epgUrl: String?,
        authHeader: String? = null,
        database: TvfsDatabase,
        filterExpiredBeforeMs: Long? = null
    ): IngestionResult = withContext(Dispatchers.IO) {
        try {
            val configJson = downloadString(configUrl, authHeader)

            var epgProgramsCount = 0
            if (!epgUrl.isNullOrBlank()) {
                downloadStream(epgUrl, authHeader) { stream ->
                    val programs = XmltvPullParser.parse(stream, filterExpiredBeforeMs)
                    database.epgDao().replaceAllPrograms(programs)
                    epgProgramsCount = programs.size
                }
            }

            val parsedConfig = SeniorTvConfigParser.parse(configJson)
            database.channelDao().replaceAllChannels(parsedConfig.channels)

            IngestionResult(
                success = true,
                channelCount = parsedConfig.channels.size,
                programCount = epgProgramsCount,
                sourceDescription = "Remote URL: $configUrl"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Remote ingestion failed: ${e.message}", e)
            IngestionResult(
                success = false,
                errorMessage = e.message ?: "Failed downloading remote exports"
            )
        }
    }

    /**
     * Ingests directly from a private or public GitHub repository.
     * Uses the GitHub API / raw content delivery with PAT token authentication.
     */
    suspend fun ingestFromGitHubRepo(
        owner: String,
        repo: String,
        branch: String = "main",
        pat: String? = null,
        database: TvfsDatabase,
        filterExpiredBeforeMs: Long? = null
    ): IngestionResult {
        val authHeader = if (!pat.isNullOrBlank()) {
            if (pat.startsWith("Bearer ") || pat.startsWith("token ")) pat else "Bearer $pat"
        } else null

        // Try raw.githubusercontent.com first
        val rawConfigUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/seniortv_config.json"
        val rawEpgUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/pruned_epg.xml"

        val rawResult = ingestFromRemote(rawConfigUrl, rawEpgUrl, authHeader, database, filterExpiredBeforeMs)
        if (rawResult.success) {
            return rawResult.copy(sourceDescription = "GitHub: $owner/$repo ($branch)")
        }

        // If raw fails (e.g. private repo needing GitHub API raw media-type accept header), fallback to API:
        val apiConfigUrl = "https://api.github.com/repos/$owner/$repo/contents/seniortv_config.json?ref=$branch"
        val apiEpgUrl = "https://api.github.com/repos/$owner/$repo/contents/pruned_epg.xml?ref=$branch"

        return try {
            val apiResult = ingestFromGitHubApi(apiConfigUrl, apiEpgUrl, authHeader, database, filterExpiredBeforeMs)
            apiResult.copy(sourceDescription = "GitHub API: $owner/$repo ($branch)")
        } catch (_: Exception) {
            rawResult // return the original failure details if both fail
        }
    }

    private suspend fun ingestFromGitHubApi(
        apiConfigUrl: String,
        apiEpgUrl: String?,
        authHeader: String?,
        database: TvfsDatabase,
        filterExpiredBeforeMs: Long?
    ): IngestionResult = withContext(Dispatchers.IO) {
        val configJson = downloadString(apiConfigUrl, authHeader, acceptHeader = "application/vnd.github.raw+json")

        var epgProgramsCount = 0
        if (!apiEpgUrl.isNullOrBlank()) {
            downloadStream(apiEpgUrl, authHeader, acceptHeader = "application/vnd.github.raw+json") { stream ->
                val programs = XmltvPullParser.parse(stream, filterExpiredBeforeMs)
                database.epgDao().replaceAllPrograms(programs)
                epgProgramsCount = programs.size
            }
        }

        val parsedConfig = SeniorTvConfigParser.parse(configJson)
        database.channelDao().replaceAllChannels(parsedConfig.channels)

        IngestionResult(
            success = true,
            channelCount = parsedConfig.channels.size,
            programCount = epgProgramsCount,
            sourceDescription = "GitHub API: $apiConfigUrl"
        )
    }

    /**
     * Seeds database from APK assets (seniortv_config.json and optionally pruned_epg.xml).
     */
    suspend fun ingestFromAssets(
        context: Context,
        database: TvfsDatabase,
        filterExpiredBeforeMs: Long? = null
    ): IngestionResult = withContext(Dispatchers.IO) {
        try {
            val assetManager = context.assets
            val configJson = assetManager.open("seniortv_config.json").bufferedReader(Charsets.UTF_8).use { it.readText() }

            var epgStream: InputStream? = null
            try {
                epgStream = assetManager.open("pruned_epg.xml")
            } catch (_: FileNotFoundException) {
                // Optional
            }

            epgStream.use { stream ->
                val result = ingestFromContent(configJson, stream, database, filterExpiredBeforeMs)
                result.copy(sourceDescription = "APK Bundled Assets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Assets ingestion failed: ${e.message}", e)
            IngestionResult(
                success = false,
                errorMessage = e.message ?: "Failed loading bundled assets"
            )
        }
    }

    private fun downloadString(urlStr: String, authHeader: String?, acceptHeader: String? = null): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            if (!authHeader.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", authHeader)
            }
            if (!acceptHeader.isNullOrBlank()) {
                conn.setRequestProperty("Accept", acceptHeader)
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                throw IOException("HTTP error $responseCode fetching $urlStr: ${conn.responseMessage}")
            }

            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadStream(
        urlStr: String,
        authHeader: String?,
        acceptHeader: String? = null,
        consumer: (InputStream) -> Unit
    ) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            if (!authHeader.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", authHeader)
            }
            if (!acceptHeader.isNullOrBlank()) {
                conn.setRequestProperty("Accept", acceptHeader)
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                throw IOException("HTTP error $responseCode fetching $urlStr: ${conn.responseMessage}")
            }

            conn.inputStream.use { consumer(it) }
        } finally {
            conn.disconnect()
        }
    }
}
