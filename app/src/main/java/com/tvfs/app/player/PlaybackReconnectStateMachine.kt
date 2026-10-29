package com.tvfs.app.player

/**
 * Pure, deterministic state machine implementing SDS Section 4.1 & 4.2:
 * - On Stream Failure / Buffering Exceeding 3500ms:
 *   Displays non-blocking banner: "Re-connecting to video stream... (Attempt X of 3)"
 * - Exponential / Stepped Backoff Retries:
 *   Attempt 1: Immediate reconnect attempt (0ms delay, 4000ms timeout)
 *   Attempt 2: 2000ms delay before reconnect attempt (4000ms timeout)
 *   Attempt 3: 2000ms delay before reconnect attempt (4000ms timeout)
 * - Terminal Failure State (After 3 Failed Attempts):
 *   Stops decoder pipeline and displays high-contrast alert dialog:
 *   Title: "Connection Failed"
 *   Message: "We couldn't connect to this channel right now. Would you like to try a different channel?"
 *   Buttons: [ OPEN CHANNEL LIST ] (Default Focused) and [ TRY AGAIN ]
 */
sealed class StreamRecoveryState {
    object ActivePlay : StreamRecoveryState()
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val delayBeforeAttemptMs: Long,
        val attemptTimeoutMs: Long = 4000L
    ) : StreamRecoveryState() {
        val bannerMessage: String
            get() = "Re-connecting to video stream... (Attempt $attempt of $maxAttempts)"
    }
    object TerminalFailure : StreamRecoveryState() {
        const val DIALOG_TITLE = "Connection Failed"
        const val DIALOG_MESSAGE =
            "We couldn't connect to this channel right now. Would you like to try a different channel?"
    }
}

class PlaybackReconnectStateMachine(
    private val maxAttempts: Int = 3
) {
    companion object {
        const val BUFFERING_TRIGGER_THRESHOLD_MS = 3500L
        const val ATTEMPT_TIMEOUT_MS = 4000L
        const val RETRY_BACKOFF_DELAY_MS = 2000L
    }

    var currentState: StreamRecoveryState = StreamRecoveryState.ActivePlay
        private set

    fun onStreamHealthy(): StreamRecoveryState {
        currentState = StreamRecoveryState.ActivePlay
        return currentState
    }

    fun onStreamDropOrFailure(): StreamRecoveryState {
        currentState = when (val state = currentState) {
            is StreamRecoveryState.ActivePlay -> {
                StreamRecoveryState.Reconnecting(
                    attempt = 1,
                    maxAttempts = maxAttempts,
                    delayBeforeAttemptMs = 0L,
                    attemptTimeoutMs = ATTEMPT_TIMEOUT_MS
                )
            }
            is StreamRecoveryState.Reconnecting -> {
                if (state.attempt < maxAttempts) {
                    StreamRecoveryState.Reconnecting(
                        attempt = state.attempt + 1,
                        maxAttempts = maxAttempts,
                        delayBeforeAttemptMs = RETRY_BACKOFF_DELAY_MS,
                        attemptTimeoutMs = ATTEMPT_TIMEOUT_MS
                    )
                } else {
                    StreamRecoveryState.TerminalFailure
                }
            }
            is StreamRecoveryState.TerminalFailure -> StreamRecoveryState.TerminalFailure
        }
        return currentState
    }

    fun restartCycle(): StreamRecoveryState {
        currentState = StreamRecoveryState.Reconnecting(
            attempt = 1,
            maxAttempts = maxAttempts,
            delayBeforeAttemptMs = 0L,
            attemptTimeoutMs = ATTEMPT_TIMEOUT_MS
        )
        return currentState
    }
}
