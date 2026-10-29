package com.tvfs.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tvfs.app.player.PlaybackReconnectStateMachine
import com.tvfs.app.player.StreamRecoveryState

/**
 * Shared Media3 / ExoPlayer Singleton Engine (SDS 2.1, 2.2, 4.1 & 4.2).
 * Handles HLS (.m3u8), MPEG-TS (.ts), and MP4/MKV playback plus automated
 * 3-step reconnect failover without showing technical error dialogs.
 */
object ExoPlayerManager {

    private var player: ExoPlayer? = null
    private var currentUrl: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stateMachine = PlaybackReconnectStateMachine(maxAttempts = 3)

    private val _recoveryState = mutableStateOf<StreamRecoveryState>(StreamRecoveryState.ActivePlay)
    val recoveryState: State<StreamRecoveryState> = _recoveryState

    private val _isPlaying = mutableStateOf(true)
    val isPlaying: State<Boolean> = _isPlaying

    private val bufferingTimeoutRunnable = Runnable {
        handleStreamFailure()
    }

    private val attemptTimeoutRunnable = Runnable {
        handleStreamFailure()
    }

    private var retryActionRunnable: Runnable? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    cancelTimers()
                    _recoveryState.value = stateMachine.onStreamHealthy()
                }
                Player.STATE_BUFFERING -> {
                    if (_recoveryState.value is StreamRecoveryState.ActivePlay) {
                        mainHandler.removeCallbacks(bufferingTimeoutRunnable)
                        mainHandler.postDelayed(
                            bufferingTimeoutRunnable,
                            PlaybackReconnectStateMachine.BUFFERING_TRIGGER_THRESHOLD_MS
                        )
                    }
                }
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    // Handled by error listener or explicit stop
                }
            }
        }

        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
            _isPlaying.value = isPlayingNow
        }

        override fun onPlayerError(error: PlaybackException) {
            handleStreamFailure()
        }
    }

    fun getPlayer(context: Context): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context.applicationContext)
                .build()
                .also { exo ->
                    exo.addListener(playerListener)
                }
        }
        return player!!
    }

    fun playChannel(context: Context, url: String, maxRetries: Int = 3) {
        cancelTimers()
        currentUrl = url
        stateMachine = PlaybackReconnectStateMachine(maxAttempts = maxRetries)
        _recoveryState.value = stateMachine.onStreamHealthy()

        val exoPlayer = getPlayer(context)
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun togglePlayPause(context: Context) {
        val exoPlayer = getPlayer(context)
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekRelative(context: Context, deltaMs: Long) {
        val exoPlayer = getPlayer(context)
        val duration = exoPlayer.duration
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
        val boundedTarget = if (duration > 0) target.coerceAtMost(duration) else target
        exoPlayer.seekTo(boundedTarget)
    }

    /**
     * Triggered when user selects [ TRY AGAIN ] on the Terminal Failure modal (SDS 4.2).
     */
    fun retryCurrentStream(context: Context) {
        val url = currentUrl ?: return
        cancelTimers()
        val nextState = stateMachine.restartCycle()
        _recoveryState.value = nextState
        if (nextState is StreamRecoveryState.Reconnecting) {
            scheduleReconnectAttempt(nextState)
        } else {
            playChannel(context, url)
        }
    }

    private fun handleStreamFailure() {
        cancelTimers()
        val nextState = stateMachine.onStreamDropOrFailure()
        _recoveryState.value = nextState

        when (nextState) {
            is StreamRecoveryState.Reconnecting -> {
                scheduleReconnectAttempt(nextState)
            }
            is StreamRecoveryState.TerminalFailure -> {
                // SDS 4.2: Stop decoder pipeline on terminal failure
                player?.stop()
            }
            is StreamRecoveryState.ActivePlay -> Unit
        }
    }

    private fun scheduleReconnectAttempt(state: StreamRecoveryState.Reconnecting) {
        val url = currentUrl ?: return
        val action = Runnable {
            player?.let { exo ->
                exo.setMediaItem(MediaItem.fromUri(url))
                exo.prepare()
                exo.playWhenReady = true
                mainHandler.postDelayed(attemptTimeoutRunnable, state.attemptTimeoutMs)
            }
        }
        retryActionRunnable = action
        if (state.delayBeforeAttemptMs > 0L) {
            mainHandler.postDelayed(action, state.delayBeforeAttemptMs)
        } else {
            action.run()
        }
    }

    private fun cancelTimers() {
        mainHandler.removeCallbacks(bufferingTimeoutRunnable)
        mainHandler.removeCallbacks(attemptTimeoutRunnable)
        retryActionRunnable?.let { mainHandler.removeCallbacks(it) }
        retryActionRunnable = null
    }

    fun release() {
        cancelTimers()
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }
}
