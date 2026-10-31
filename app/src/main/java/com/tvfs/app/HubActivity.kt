package com.tvfs.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.tvfs.app.model.LastViewedState
import com.tvfs.app.ui.theme.TvfsFocusableSurface
import com.tvfs.app.ui.theme.TvfsTokens

/**
 * Icon 1: TV Hub (Main) - SDS Section 2.1 & 6.1.
 * Calibrated for 960x540dp (1080p @ 320dpi) Android TV viewports with zero vertical text clipping.
 */
class HubActivity : ComponentActivity() {

    private var lastViewedState by mutableStateOf<LastViewedState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastViewedState = TvfsRepository.getLastViewedState(this)

        setContent {
            val context = LocalContext.current
            val firstTileFocusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                TvfsRepository.syncRemoteBootstrap(context)
                lastViewedState = TvfsRepository.getLastViewedState(context)
                runCatching { firstTileFocusRequester.requestFocus() }
            }

            HubScreen(
                lastViewed = lastViewedState ?: TvfsRepository.getLastViewedState(context),
                firstTileFocusRequester = firstTileFocusRequester,
                onOpenLiveTv = {
                    startActivity(Intent(this, LivePlayerActivity::class.java))
                },
                onOpenSeries = {
                    startActivity(Intent(this, SeriesBrowserActivity::class.java))
                },
                onOpenMovies = {
                    startActivity(Intent(this, MovieBrowserActivity::class.java))
                },
                onResumeLastViewed = { resumeState ->
                    val intent = Intent(this, LivePlayerActivity::class.java).apply {
                        putExtra(LivePlayerActivity.EXTRA_MODE, resumeState.contentType)
                        putExtra(LivePlayerActivity.EXTRA_TITLE, resumeState.title)
                        putExtra(LivePlayerActivity.EXTRA_SUBTITLE, resumeState.subtitle)
                        putExtra(LivePlayerActivity.EXTRA_STREAM_URL, resumeState.streamUrl)
                        putExtra(LivePlayerActivity.EXTRA_CHANNEL_ID, resumeState.channelId)
                        putExtra(LivePlayerActivity.EXTRA_SERIES_ID, resumeState.seriesId)
                        putExtra(LivePlayerActivity.EXTRA_EPISODE_INDEX, resumeState.episodeIndex)
                    }
                    startActivity(intent)
                },
                onOpenSearch = {
                    startActivity(Intent(this, SearchActivity::class.java))
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        lastViewedState = TvfsRepository.getLastViewedState(this)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubScreen(
    lastViewed: LastViewedState,
    firstTileFocusRequester: FocusRequester,
    onOpenLiveTv: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenMovies: () -> Unit,
    onResumeLastViewed: (LastViewedState) -> Unit,
    onOpenSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvfsTokens.ScreenBackground)
            .padding(horizontal = 40.dp, vertical = 22.dp)
    ) {
        // Top Header + Dedicated Voice/QWERTY Search Card (SDS 6.1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 20.dp)) {
                Text(
                    text = "TV for Seniors",
                    color = TvfsTokens.PureWhite,
                    fontSize = TvfsTokens.PrimaryHeadingSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "Use Arrow Keys and press OK to watch",
                    color = TvfsTokens.SecondaryGray,
                    fontSize = TvfsTokens.SecondaryMetadataSize,
                    maxLines = 1
                )
            }

            TvfsFocusableSurface(
                onClick = onOpenSearch
            ) { isFocused ->
                Box(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VOICE / TEXT SEARCH",
                        color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.PureWhite,
                        fontSize = TvfsTokens.SecondaryMetadataSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2x2 Grid of 4 Large High-Contrast Tiles (SDS 2.1)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                HubLargeTile(
                    badge = "TILE 1 • LIVE CHANNELS",
                    title = "Live TV",
                    subtitle = "News & Favorites",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    focusRequester = firstTileFocusRequester,
                    onClick = onOpenLiveTv
                )

                HubLargeTile(
                    badge = "TILE 2 • EPISODES",
                    title = "Favorite TV Shows",
                    subtitle = "Play Next Episode",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onOpenSeries
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                HubLargeTile(
                    badge = "TILE 3 • CINEMA",
                    title = "Movies",
                    subtitle = "Classic & Family Films",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onOpenMovies
                )

                HubLargeTile(
                    badge = "TILE 4 • INSTANT RESUME",
                    title = "Last Viewed",
                    subtitle = lastViewed.title,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onResumeLastViewed(lastViewed) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubLargeTile(
    badge: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    TvfsFocusableSurface(
        modifier = modifier,
        focusRequester = focusRequester,
        onClick = onClick
    ) { isFocused ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = badge,
                color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.SecondaryGray,
                fontSize = TvfsTokens.MinimumTextSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Column {
                Text(
                    text = title,
                    color = TvfsTokens.PureWhite,
                    fontSize = TvfsTokens.PrimaryHeadingSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TvfsTokens.SecondaryGray,
                    fontSize = TvfsTokens.ProgramTitleSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
