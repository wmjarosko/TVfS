package com.tvfs.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.tvfs.app.data.TvfsRepository
import com.tvfs.app.model.SeriesItem
import com.tvfs.app.ui.theme.TvfsActionButton
import com.tvfs.app.ui.theme.TvfsFocusableSurface
import com.tvfs.app.ui.theme.TvfsTokens

/**
 * Icon 4: TV Shows Direct Entry Activity (SDS Section 2.1).
 * Launches directly into the curated series catalogue with a single-click
 * "Play Next Episode" mechanism.
 */
class SeriesBrowserActivity : ComponentActivity() {

    private var refreshTrigger by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = TvfsRepository.getBootstrapConfig(this)
        val seriesList = TvfsRepository.getCuratedSeries(config)

        setContent {
            val context = LocalContext.current
            val firstSeriesFocus = remember { FocusRequester() }
            val currentRefresh = refreshTrigger

            LaunchedEffect(Unit) {
                runCatching { firstSeriesFocus.requestFocus() }
            }

            SeriesBrowserScreen(
                seriesList = seriesList,
                refreshKey = currentRefresh,
                firstSeriesFocus = firstSeriesFocus,
                onPlayNextEpisode = { series ->
                    val nextIndex = TvfsRepository.getNextEpisodeIndexForSeries(
                        context = context,
                        seriesId = series.id,
                        totalEpisodes = series.episodes.size
                    )
                    val episode = series.episodes[nextIndex]
                    TvfsRepository.saveLastSeriesEpisodeViewed(context, series, nextIndex)
                    val intent = Intent(this, LivePlayerActivity::class.java).apply {
                        putExtra(LivePlayerActivity.EXTRA_MODE, "SERIES")
                        putExtra(LivePlayerActivity.EXTRA_TITLE, series.title)
                        putExtra(
                            LivePlayerActivity.EXTRA_SUBTITLE,
                            "${episode.codeLabel}: ${episode.title}"
                        )
                        putExtra(LivePlayerActivity.EXTRA_STREAM_URL, episode.streamUrl)
                        putExtra(LivePlayerActivity.EXTRA_SERIES_ID, series.id)
                        putExtra(LivePlayerActivity.EXTRA_EPISODE_INDEX, nextIndex)
                    }
                    startActivity(intent)
                },
                onOpenSearch = {
                    startActivity(Intent(this, SearchActivity::class.java))
                },
                onBackToHub = {
                    finish()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger += 1
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesBrowserScreen(
    seriesList: List<SeriesItem>,
    refreshKey: Int,
    firstSeriesFocus: FocusRequester,
    onPlayNextEpisode: (SeriesItem) -> Unit,
    onOpenSearch: () -> Unit,
    onBackToHub: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvfsTokens.ScreenBackground)
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 24.dp)
            ) {
                Text(
                    text = "Favorite TV Shows",
                    color = TvfsTokens.PureWhite,
                    fontSize = TvfsTokens.PrimaryHeadingSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "Press OK on any show to immediately Play Next Episode",
                    color = TvfsTokens.SecondaryGray,
                    fontSize = TvfsTokens.SecondaryMetadataSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvfsActionButton(
                    label = "SEARCH",
                    onClick = onOpenSearch
                )
                TvfsActionButton(
                    label = "TV HUB",
                    onClick = onBackToHub
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(seriesList) { index, series ->
                val nextIndex = remember(series.id, refreshKey) {
                    TvfsRepository.getNextEpisodeIndexForSeries(
                        context = context,
                        seriesId = series.id,
                        totalEpisodes = series.episodes.size
                    )
                }
                val nextEpisode = series.episodes[nextIndex]

                TvfsFocusableSurface(
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = if (index == 0) firstSeriesFocus else null,
                    onClick = { onPlayNextEpisode(series) }
                ) { isFocused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 20.dp)
                        ) {
                            Text(
                                text = "[SERIES] • ${series.genre} • ${series.episodes.size} Episodes",
                                color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.SecondaryGray,
                                fontSize = TvfsTokens.MinimumTextSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = series.title,
                                color = TvfsTokens.PureWhite,
                                fontSize = TvfsTokens.PrimaryHeadingSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Up Next (${nextEpisode.codeLabel}): ${nextEpisode.title}",
                                color = TvfsTokens.SecondaryGray,
                                fontSize = TvfsTokens.SecondaryMetadataSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "[ PLAY NEXT ]",
                            color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.PureWhite,
                            fontSize = TvfsTokens.ProgramTitleSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
