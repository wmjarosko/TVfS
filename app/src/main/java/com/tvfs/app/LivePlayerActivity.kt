package com.tvfs.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.tvfs.app.data.TvfsRepository
import com.tvfs.app.model.Channel
import com.tvfs.app.player.StreamRecoveryState
import com.tvfs.app.ui.theme.TvfsActionButton
import com.tvfs.app.ui.theme.TvfsFocusableSurface
import com.tvfs.app.ui.theme.TvfsTokens
import kotlinx.coroutines.delay

/**
 * Icon 2: Live TV & Universal Full-Screen Player Activity (SDS 2.1, 3.1, 3.2, 4.1, 4.2, 5.1 & 5.2).
 *
 * Implements:
 * - G10 Remote Mapping Matrix (SDS 3.1):
 *   * D-Pad Up / Channel Up: Next Channel (Live) or Previous Episode (Series)
 *   * D-Pad Down / Channel Down: Previous Channel (Live) or Next Episode (Series)
 *   * D-Pad Left / Right: Mini EPG Quick Peek (Live) or -15s / +15s Seek (VOD/Series)
 *   * Center / OK: Toggle Top/Bottom Status Banner (Live) or Pause/Play Toggle (VOD/Series)
 *   * Back (Single Tap): Open/Close Bottom 40% EPG Drawer without interrupting playback
 *   * Back (Double Tap within 1500ms): Display high-contrast Exit Confirmation Modal with 10s auto-dismiss
 * - Playback Resilience & Error Recovery (SDS 4.1 & 4.2):
 *   * Non-blocking banner for Attempts 1..3
 *   * Terminal Failure Modal ([ OPEN CHANNEL LIST ] default-focused vs [ TRY AGAIN ])
 */
class LivePlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode" // "LIVE", "MOVIE", "SERIES"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_SERIES_ID = "extra_series_id"
        const val EXTRA_EPISODE_INDEX = "extra_episode_index"

        private const val DOUBLE_TAP_WINDOW_MS = 1500L
        private const val EXIT_MODAL_AUTO_DISMISS_SECONDS = 10
    }

    private lateinit var channels: List<Channel>
    private var playbackMode by mutableStateOf("LIVE")
    private var currentChannelIndex by mutableStateOf(0)
    private var vodTitle by mutableStateOf("")
    private var vodSubtitle by mutableStateOf("")
    private var vodStreamUrl by mutableStateOf("")
    private var currentSeriesId by mutableStateOf(-1)
    private var currentEpisodeIndex by mutableStateOf(0)

    // Overlay states (reactive Compose states so UI recomposes immediately on remote presses)
    private var isBottomDrawerOpen by mutableStateOf(false)
    private var isMiniEpgPeekVisible by mutableStateOf(false)
    private var isTopBannerVisible by mutableStateOf(true)
    private var isExitModalVisible by mutableStateOf(false)
    private var exitModalCountdownSeconds by mutableStateOf(EXIT_MODAL_AUTO_DISMISS_SECONDS)

    private var lastBackTapTimestampMs: Long = 0L
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideMiniEpgRunnable = Runnable { isMiniEpgPeekVisible = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = TvfsRepository.getBootstrapConfig(this)
        channels = TvfsRepository.getCuratedChannels(config)

        playbackMode = intent.getStringExtra(EXTRA_MODE) ?: "LIVE"
        val requestedChannelId = intent.getIntExtra(EXTRA_CHANNEL_ID, -1)

        if (playbackMode == "LIVE") {
            currentChannelIndex = if (requestedChannelId > 0) {
                val idx = channels.indexOfFirst { it.id == requestedChannelId }
                if (idx >= 0) idx else TvfsRepository.getLastChannelIndex(this, channels)
            } else {
                TvfsRepository.getLastChannelIndex(this, channels)
            }
            startCurrentLiveChannel()
        } else {
            vodTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Featured Program"
            vodSubtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: ""
            vodStreamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
                ?: channels.first().streamUrl
            currentSeriesId = intent.getIntExtra(EXTRA_SERIES_ID, -1)
            currentEpisodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0)
            ExoPlayerManager.playChannel(
                context = this,
                url = vodStreamUrl,
                maxRetries = config.behavior.retryAttempts
            )
        }

        setContent {
            val context = LocalContext.current
            val recoveryState by ExoPlayerManager.recoveryState
            val isPlaying by ExoPlayerManager.isPlaying
            val keepWatchingFocusRequester = remember { FocusRequester() }
            val openChannelListFocusRequester = remember { FocusRequester() }

            // Auto-hide top channel info banner after 5 seconds during normal play
            LaunchedEffect(currentChannelIndex, isTopBannerVisible) {
                if (isTopBannerVisible && !isBottomDrawerOpen && !isExitModalVisible) {
                    delay(5000L)
                    isTopBannerVisible = false
                }
            }

            // SDS 3.2: 10-second auto-dismiss timer for the Exit Confirmation Modal
            LaunchedEffect(isExitModalVisible) {
                if (isExitModalVisible) {
                    exitModalCountdownSeconds = EXIT_MODAL_AUTO_DISMISS_SECONDS
                    runCatching { keepWatchingFocusRequester.requestFocus() }
                    while (exitModalCountdownSeconds > 0 && isExitModalVisible) {
                        delay(1000L)
                        exitModalCountdownSeconds -= 1
                    }
                    if (isExitModalVisible) {
                        isExitModalVisible = false
                    }
                }
            }

            // Default focus on [ OPEN CHANNEL LIST ] when Terminal Failure modal appears (SDS 4.2)
            LaunchedEffect(recoveryState) {
                if (recoveryState is StreamRecoveryState.TerminalFailure) {
                    runCatching { openChannelListFocusRequester.requestFocus() }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Full-screen Media3 PlayerView (Video continues playing in top 60% when drawer is open - SDS 5.2)
                AndroidView(
                    factory = {
                        PlayerView(context).apply {
                            useController = false
                            player = ExoPlayerManager.getPlayer(context)
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        view.player = ExoPlayerManager.getPlayer(context)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                val activeChannel = channels.getOrElse(currentChannelIndex) { channels.first() }

                // Top Status / Channel Banner
                if (isTopBannerVisible && !isBottomDrawerOpen) {
                    TopChannelBanner(
                        title = if (playbackMode == "LIVE") {
                            "CH ${activeChannel.number} • ${activeChannel.name}"
                        } else {
                            vodTitle
                        },
                        subtitle = if (playbackMode == "LIVE") {
                            "Now: ${activeChannel.currentProgram} (${activeChannel.currentTimeRange})"
                        } else {
                            if (isPlaying) vodSubtitle else "$vodSubtitle • [PAUSED]"
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(32.dp)
                    )
                }

                // SDS 3.1: Mini EPG Quick Peek (Now & Next) on D-Pad Left / Right in Live TV
                if (isMiniEpgPeekVisible && !isBottomDrawerOpen && playbackMode == "LIVE") {
                    MiniEpgQuickPeekCard(
                        channel = activeChannel,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(32.dp)
                    )
                }

                // SDS 4.1 & 4.2: Non-blocking Reconnect Banner (Attempts 1..3)
                if (recoveryState is StreamRecoveryState.Reconnecting) {
                    val reconnecting = recoveryState as StreamRecoveryState.Reconnecting
                    ReconnectBannerOverlay(
                        message = reconnecting.bannerMessage,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 36.dp)
                    )
                }

                // SDS 5.2: Bottom Drawer EPG Overlay (Lower 40% of display)
                if (isBottomDrawerOpen) {
                    BottomEpgDrawerOverlay(
                        channels = channels,
                        activeChannelIndex = currentChannelIndex,
                        onSelectChannel = { selectedIndex ->
                            currentChannelIndex = selectedIndex
                            startCurrentLiveChannel()
                            isBottomDrawerOpen = false
                            isTopBannerVisible = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.42f)
                    )
                }

                // SDS 4.2: Terminal Failure Recovery Modal Dialog
                if (recoveryState is StreamRecoveryState.TerminalFailure) {
                    TerminalFailureRecoveryModal(
                        openChannelListFocusRequester = openChannelListFocusRequester,
                        onOpenChannelList = {
                            isBottomDrawerOpen = true
                        },
                        onTryAgain = {
                            ExoPlayerManager.retryCurrentStream(context)
                        }
                    )
                }

                // SDS 3.2: Accidental Exit Protection Modal (Double-Tap Back)
                if (isExitModalVisible) {
                    ExitConfirmationModal(
                        countdownSeconds = exitModalCountdownSeconds,
                        keepWatchingFocusRequester = keepWatchingFocusRequester,
                        onKeepWatching = {
                            isExitModalVisible = false
                        },
                        onExitApp = {
                            isExitModalVisible = false
                            ExoPlayerManager.release()
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun startCurrentLiveChannel() {
        val channel = channels.getOrElse(currentChannelIndex) { channels.first() }
        val config = TvfsRepository.getBootstrapConfig(this)
        TvfsRepository.saveLastChannel(this, channel)
        isTopBannerVisible = true
        ExoPlayerManager.playChannel(
            context = this,
            url = channel.streamUrl,
            maxRetries = config.behavior.retryAttempts
        )
    }

    private fun stepSeriesEpisode(delta: Int) {
        if (currentSeriesId <= 0) return
        val series = TvfsRepository.getCuratedSeries().find { it.id == currentSeriesId } ?: return
        if (series.episodes.isEmpty()) return
        val nextIdx = (currentEpisodeIndex + delta).mod(series.episodes.size)
        currentEpisodeIndex = nextIdx
        val ep = series.episodes[nextIdx]
        vodTitle = series.title
        vodSubtitle = "${ep.codeLabel}: ${ep.title}"
        vodStreamUrl = ep.streamUrl
        TvfsRepository.saveLastSeriesEpisodeViewed(this, series, nextIdx)
        isTopBannerVisible = true
        ExoPlayerManager.playChannel(this, ep.streamUrl)
    }

    /**
     * SDS 3.1 G10 Remote Mapping Matrix & SDS 3.2 Accidental Exit Protection.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Always handle Back according to SDS 3.2 (Single Tap = EPG Drawer, Double Tap within 1500ms = Exit Modal)
            if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                handleBackPressErgonomics()
                return true
            }

            // When modal or bottom drawer is open, let Compose focus navigation handle D-Pad / OK keys
            if (isExitModalVisible ||
                isBottomDrawerOpen ||
                ExoPlayerManager.recoveryState.value is StreamRecoveryState.TerminalFailure
            ) {
                return super.dispatchKeyEvent(event)
            }

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (playbackMode == "LIVE") {
                        currentChannelIndex = (currentChannelIndex + 1) % channels.size
                        startCurrentLiveChannel()
                    } else {
                        // SDS 3.1: In VOD / TV Shows, Up switches to Previous Episode in Queue
                        stepSeriesEpisode(-1)
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (playbackMode == "LIVE") {
                        currentChannelIndex = if (currentChannelIndex - 1 < 0) {
                            channels.lastIndex
                        } else {
                            currentChannelIndex - 1
                        }
                        startCurrentLiveChannel()
                    } else {
                        // SDS 3.1: In VOD / TV Shows, Down switches to Next Episode in Queue
                        stepSeriesEpisode(+1)
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (playbackMode == "LIVE") {
                        // SDS 3.1: Mini EPG Quick Peek (Now & Next)
                        triggerMiniEpgPeek()
                    } else {
                        // SDS 3.1: Rewind 15 seconds in VOD / TV Shows
                        ExoPlayerManager.seekRelative(this, -15_000L)
                        isTopBannerVisible = true
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (playbackMode == "LIVE") {
                        // SDS 3.1: Mini EPG Quick Peek (Now & Next)
                        triggerMiniEpgPeek()
                    } else {
                        // SDS 3.1: Fast Forward 15 seconds in VOD / TV Shows
                        ExoPlayerManager.seekRelative(this, +15_000L)
                        isTopBannerVisible = true
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (playbackMode == "LIVE") {
                        // SDS 3.1: Center / OK toggles overlay controls in Live TV
                        isTopBannerVisible = !isTopBannerVisible
                    } else {
                        // SDS 3.1: Center / OK toggles Pause / Play in VOD / TV Shows
                        ExoPlayerManager.togglePlayPause(this)
                        isTopBannerVisible = true
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun triggerMiniEpgPeek() {
        isMiniEpgPeekVisible = true
        uiHandler.removeCallbacks(hideMiniEpgRunnable)
        uiHandler.postDelayed(hideMiniEpgRunnable, 5000L)
    }

    /**
     * SDS 3.2 Accidental Exit Protection:
     * - Single Tap: Toggles the semi-transparent Bottom Drawer guide without interrupting playback.
     * - Double Tap (within 1500ms): Displays the full-screen high-contrast Exit Confirmation Modal.
     */
    private fun handleBackPressErgonomics() {
        if (isExitModalVisible) {
            isExitModalVisible = false
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastBackTapTimestampMs <= DOUBLE_TAP_WINDOW_MS) {
            // Double tap detected within 1500ms -> Open Exit Confirmation Modal
            lastBackTapTimestampMs = 0L
            isBottomDrawerOpen = false
            isExitModalVisible = true
        } else {
            // Single tap -> Toggle Bottom EPG Drawer without interrupting playback
            lastBackTapTimestampMs = now
            isBottomDrawerOpen = !isBottomDrawerOpen
        }
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(hideMiniEpgRunnable)
        super.onDestroy()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopChannelBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(TvfsTokens.OverlayBackground, RoundedCornerShape(16.dp))
            .border(BorderStroke(2.dp, TvfsTokens.SolarYellow), RoundedCornerShape(16.dp))
            .padding(horizontal = 28.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            color = TvfsTokens.PureWhite,
            fontSize = TvfsTokens.PrimaryHeadingSize,
            fontWeight = FontWeight.Bold
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = TvfsTokens.SecondaryGray,
                fontSize = TvfsTokens.SecondaryMetadataSize
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MiniEpgQuickPeekCard(
    channel: Channel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(TvfsTokens.OverlayBackground, RoundedCornerShape(16.dp))
            .border(BorderStroke(3.dp, TvfsTokens.SolarYellow), RoundedCornerShape(16.dp))
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Text(
            text = "CHANNEL ${channel.number} • ${channel.name}",
            color = TvfsTokens.SolarYellow,
            fontSize = TvfsTokens.ProgramTitleSize,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Now: ${channel.currentProgram} (${channel.currentTimeRange})",
            color = TvfsTokens.PureWhite,
            fontSize = TvfsTokens.ProgramTitleSize,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Next: ${channel.nextProgram} at ${channel.nextStartTime}",
            color = TvfsTokens.SecondaryGray,
            fontSize = TvfsTokens.SecondaryMetadataSize
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ReconnectBannerOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(TvfsTokens.OverlayBackground, RoundedCornerShape(14.dp))
            .border(BorderStroke(3.dp, TvfsTokens.SolarYellow), RoundedCornerShape(14.dp))
            .padding(horizontal = 32.dp, vertical = 18.dp)
    ) {
        Text(
            text = message,
            color = TvfsTokens.SolarYellow,
            fontSize = TvfsTokens.ProgramTitleSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * SDS Section 5.2 Bottom Drawer EPG Overlay Layout:
 * - Screen Coverage: Lower 40% of the display
 * - Video playback remains visible in top 60%
 * - Horizontal carousel of large channel cards (Channel Number, Channel Name, Current Program + progress bar)
 * - Two-line summary below displaying current schedule & upcoming program ("Next: [Title] at [Time]")
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BottomEpgDrawerOverlay(
    channels: List<Channel>,
    activeChannelIndex: Int,
    onSelectChannel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var focusedChannelIndex by remember(activeChannelIndex) { mutableStateOf(activeChannelIndex) }
    val inspectedChannel = channels.getOrElse(focusedChannelIndex) { channels.first() }

    Column(
        modifier = modifier
            .background(TvfsTokens.OverlayBackground)
            .padding(horizontal = 36.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(channels) { index, channel ->
                TvfsFocusableSurface(
                    modifier = Modifier
                        .width(340.dp)
                        .height(165.dp),
                    onFocused = { focusedChannelIndex = index },
                    onClick = { onSelectChannel(index) }
                ) { isFocused ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[CHANNEL ${channel.number}]",
                            color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.SecondaryGray,
                            fontSize = TvfsTokens.SecondaryMetadataSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = channel.name,
                            color = TvfsTokens.PureWhite,
                            fontSize = TvfsTokens.ProgramTitleSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = channel.currentProgram,
                            color = TvfsTokens.SecondaryGray,
                            fontSize = TvfsTokens.MinimumTextSize,
                            maxLines = 1
                        )
                        // High-contrast Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(Color(0xFF334152), RoundedCornerShape(5.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(channel.progressFraction.coerceIn(0.05f, 1.0f))
                                    .fillMaxHeight()
                                    .background(TvfsTokens.SolarYellow, RoundedCornerShape(5.dp))
                            )
                        }
                    }
                }
            }
        }

        // Two-line summary below the card carousel (SDS 5.2)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Text(
                text = "Now: ${inspectedChannel.currentProgram} (${inspectedChannel.currentTimeRange})",
                color = TvfsTokens.PureWhite,
                fontSize = TvfsTokens.SecondaryMetadataSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "Next: ${inspectedChannel.nextProgram} at ${inspectedChannel.nextStartTime}",
                color = TvfsTokens.SecondaryGray,
                fontSize = TvfsTokens.SecondaryMetadataSize,
                maxLines = 1
            )
        }
    }
}

/**
 * SDS Section 4.2 Terminal Failure State Modal:
 * Title: "Connection Failed"
 * Message: "We couldn't connect to this channel right now. Would you like to try a different channel?"
 * Buttons: [ OPEN CHANNEL LIST ] (Default Focused) and [ TRY AGAIN ]
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TerminalFailureRecoveryModal(
    openChannelListFocusRequester: FocusRequester,
    onOpenChannelList: () -> Unit,
    onTryAgain: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvfsTokens.OverlayBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .background(TvfsTokens.CardBackground, RoundedCornerShape(24.dp))
                .border(BorderStroke(4.dp, TvfsTokens.SolarYellow), RoundedCornerShape(24.dp))
                .padding(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = StreamRecoveryState.TerminalFailure.DIALOG_TITLE,
                color = TvfsTokens.SolarYellow,
                fontSize = TvfsTokens.PrimaryHeadingSize,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = StreamRecoveryState.TerminalFailure.DIALOG_MESSAGE,
                color = TvfsTokens.PureWhite,
                fontSize = TvfsTokens.ProgramTitleSize,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(36.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                TvfsActionButton(
                    label = "[ OPEN CHANNEL LIST ]",
                    focusRequester = openChannelListFocusRequester,
                    onClick = onOpenChannelList
                )
                TvfsActionButton(
                    label = "[ TRY AGAIN ]",
                    onClick = onTryAgain
                )
            }
        }
    }
}

/**
 * SDS Section 3.2 Accidental Exit Protection Modal:
 * Prompt: "Are you sure you want to turn off the TV app?"
 * Options: [ KEEP WATCHING ] (Default Focus) vs [ EXIT APP ]
 * Auto-dismisses after 10 seconds if no action is taken.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ExitConfirmationModal(
    countdownSeconds: Int,
    keepWatchingFocusRequester: FocusRequester,
    onKeepWatching: () -> Unit,
    onExitApp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvfsTokens.OverlayBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .background(TvfsTokens.CardBackground, RoundedCornerShape(24.dp))
                .border(BorderStroke(4.dp, TvfsTokens.SolarYellow), RoundedCornerShape(24.dp))
                .padding(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Are you sure you want to turn off the TV app?",
                color = TvfsTokens.PureWhite,
                fontSize = TvfsTokens.PrimaryHeadingSize,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Resuming TV automatically in $countdownSeconds seconds...",
                color = TvfsTokens.SecondaryGray,
                fontSize = TvfsTokens.SecondaryMetadataSize
            )
            Spacer(modifier = Modifier.height(36.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                TvfsActionButton(
                    label = "[ KEEP WATCHING ]",
                    focusRequester = keepWatchingFocusRequester,
                    onClick = onKeepWatching
                )
                TvfsActionButton(
                    label = "[ EXIT APP ]",
                    onClick = onExitApp
                )
            }
        }
    }
}
