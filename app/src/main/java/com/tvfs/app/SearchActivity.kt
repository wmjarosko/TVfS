package com.tvfs.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.tvfs.app.data.TvfsRepository
import com.tvfs.app.model.SearchBadge
import com.tvfs.app.model.SearchResultItem
import com.tvfs.app.ui.theme.TvfsActionButton
import com.tvfs.app.ui.theme.TvfsFocusableSurface
import com.tvfs.app.ui.theme.TvfsTokens
import java.util.Locale

/**
 * SDS Section 6.1 Unified Search Flow:
 * - Primary Input: Launches the Android TV System Voice Dialog (RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
 * - Fallback Input: If the remote has no microphone access or voice recognition fails, smoothly falls
 *   back to an accessible on-screen QWERTY keyboard row.
 * - Search Execution: Queries across Live Channel Names/Categories, Movie Titles/Genres, and TV Show Titles.
 * - Results Display: Grouped into flat horizontal rows marked with explicit badges:
 *   [LIVE CHANNEL], [MOVIE], [SERIES].
 */
class SearchActivity : ComponentActivity() {

    private var searchQuery by mutableStateOf("")
    private var showOnScreenQwerty by mutableStateOf(false)
    private var voiceStatusBanner by mutableStateOf("Press VOICE SEARCH or use the on-screen keyboard")

    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull().orEmpty()
            if (spokenText.isNotBlank()) {
                searchQuery = spokenText
                voiceStatusBanner = "Showing results for: \"$spokenText\""
            } else {
                showOnScreenQwerty = true
                voiceStatusBanner = "Didn't catch that — use the on-screen keyboard below"
            }
        } else {
            // Smooth fallback to on-screen QWERTY keyboard (SDS 6.1)
            showOnScreenQwerty = true
            voiceStatusBanner = "Voice canceled — use the on-screen keyboard below"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Automatically trigger Voice Search dialog first per SDS 6.1
        launchSystemVoiceSearch()

        setContent {
            val config = remember { TvfsRepository.getBootstrapConfig(this) }
            val results = remember(searchQuery) {
                TvfsRepository.searchCatalog(searchQuery, config, this)
            }
            val voiceFocusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                runCatching { voiceFocusRequester.requestFocus() }
            }

            SearchScreen(
                query = searchQuery,
                statusBanner = voiceStatusBanner,
                showQwertyFallback = showOnScreenQwerty,
                results = results,
                voiceFocusRequester = voiceFocusRequester,
                onTriggerVoice = { launchSystemVoiceSearch() },
                onToggleQwerty = { showOnScreenQwerty = !showOnScreenQwerty },
                onAppendChar = { ch -> searchQuery += ch },
                onBackspace = {
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = searchQuery.dropLast(1)
                    }
                },
                onClearQuery = { searchQuery = "" },
                onSelectResult = { item -> openSearchResult(item) },
                onBack = { finish() }
            )
        }
    }

    private fun launchSystemVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a Channel, Movie, or TV Show name")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // SDS 6.1 Fallback when microphone / voice recognizer is unavailable
            showOnScreenQwerty = true
            voiceStatusBanner = "Voice remote unavailable — using On-Screen QWERTY Keyboard"
        }
    }

    private fun openSearchResult(item: SearchResultItem) {
        val mode = when (item.badge) {
            SearchBadge.LIVE_CHANNEL -> "LIVE"
            SearchBadge.MOVIE -> "MOVIE"
            SearchBadge.SERIES -> "SERIES"
        }
        val intent = Intent(this, LivePlayerActivity::class.java).apply {
            putExtra(LivePlayerActivity.EXTRA_MODE, mode)
            putExtra(LivePlayerActivity.EXTRA_TITLE, item.title)
            putExtra(LivePlayerActivity.EXTRA_SUBTITLE, item.subtitle)
            putExtra(LivePlayerActivity.EXTRA_STREAM_URL, item.streamUrl)
            putExtra(LivePlayerActivity.EXTRA_CHANNEL_ID, item.channelId)
            putExtra(LivePlayerActivity.EXTRA_SERIES_ID, item.seriesId)
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchScreen(
    query: String,
    statusBanner: String,
    showQwertyFallback: Boolean,
    results: List<SearchResultItem>,
    voiceFocusRequester: FocusRequester,
    onTriggerVoice: () -> Unit,
    onToggleQwerty: () -> Unit,
    onAppendChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearQuery: () -> Unit,
    onSelectResult: (SearchResultItem) -> Unit,
    onBack: () -> Unit
) {
    val liveResults = results.filter { it.badge == SearchBadge.LIVE_CHANNEL }
    val movieResults = results.filter { it.badge == SearchBadge.MOVIE }
    val seriesResults = results.filter { it.badge == SearchBadge.SERIES }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvfsTokens.ScreenBackground)
            .padding(horizontal = 44.dp, vertical = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header & Voice / QWERTY Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (query.isBlank()) "Search All Channels, Movies & Shows" else "Search: \"$query\"",
                    color = TvfsTokens.PureWhite,
                    fontSize = TvfsTokens.PrimaryHeadingSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusBanner,
                    color = TvfsTokens.SecondaryGray,
                    fontSize = TvfsTokens.SecondaryMetadataSize
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvfsActionButton(
                    label = "VOICE SEARCH",
                    focusRequester = voiceFocusRequester,
                    onClick = onTriggerVoice
                )
                TvfsActionButton(
                    label = if (showQwertyFallback) "HIDE KEYBOARD" else "KEYBOARD",
                    onClick = onToggleQwerty
                )
                TvfsActionButton(
                    label = "BACK",
                    onClick = onBack
                )
            }
        }

        // On-Screen QWERTY Keyboard Fallback (SDS 6.1)
        if (showQwertyFallback) {
            OnScreenQwertyBar(
                onAppendChar = onAppendChar,
                onBackspace = onBackspace,
                onClearQuery = onClearQuery
            )
        }

        // Flat Horizontal Rows with Explicit Badges: [LIVE CHANNEL], [MOVIE], [SERIES] (SDS 6.1)
        if (liveResults.isNotEmpty()) {
            SearchCategoryRow(
                rowTitle = "${SearchBadge.LIVE_CHANNEL.displayBadge} Channels (${liveResults.size})",
                items = liveResults,
                onSelectResult = onSelectResult
            )
        }

        if (movieResults.isNotEmpty()) {
            SearchCategoryRow(
                rowTitle = "${SearchBadge.MOVIE.displayBadge} Films (${movieResults.size})",
                items = movieResults,
                onSelectResult = onSelectResult
            )
        }

        if (seriesResults.isNotEmpty()) {
            SearchCategoryRow(
                rowTitle = "${SearchBadge.SERIES.displayBadge} TV Shows (${seriesResults.size})",
                items = seriesResults,
                onSelectResult = onSelectResult
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OnScreenQwertyBar(
    onAppendChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearQuery: () -> Unit
) {
    val qwertyRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M", "SPACE", "DELETE", "CLEAR")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvfsTokens.CardBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        qwertyRows.forEach { rowKeys ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowKeys.forEach { keyLabel ->
                    TvfsFocusableSurface(
                        onClick = {
                            when (keyLabel) {
                                "SPACE" -> onAppendChar(" ")
                                "DELETE" -> onBackspace()
                                "CLEAR" -> onClearQuery()
                                else -> onAppendChar(keyLabel)
                            }
                        }
                    ) { isFocused ->
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = keyLabel,
                                color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.PureWhite,
                                fontSize = TvfsTokens.SecondaryMetadataSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchCategoryRow(
    rowTitle: String,
    items: List<SearchResultItem>,
    onSelectResult: (SearchResultItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = rowTitle,
            color = TvfsTokens.SolarYellow,
            fontSize = TvfsTokens.ProgramTitleSize,
            fontWeight = FontWeight.Bold
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(items) { item ->
                TvfsFocusableSurface(
                    modifier = Modifier
                        .width(380.dp)
                        .height(155.dp),
                    onClick = { onSelectResult(item) }
                ) { isFocused ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.badge.displayBadge,
                            color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.SecondaryGray,
                            fontSize = TvfsTokens.MinimumTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.title,
                            color = TvfsTokens.PureWhite,
                            fontSize = TvfsTokens.ProgramTitleSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = item.subtitle,
                            color = TvfsTokens.SecondaryGray,
                            fontSize = TvfsTokens.MinimumTextSize,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
