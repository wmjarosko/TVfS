package com.tvfs.app

import com.tvfs.app.data.TvfsRepository
import com.tvfs.app.model.BootstrapConfigParser
import com.tvfs.app.model.EpgProgramEntry
import com.tvfs.app.model.SearchBadge
import com.tvfs.app.player.PlaybackReconnectStateMachine
import com.tvfs.app.player.StreamRecoveryState
import org.junit.Assert.*
import org.junit.Test

class TvfsSdsComplianceTest {

    @Test
    fun `SDS 7_1 bootstrap JSON schema parses provider, curation, and behavior fields`() {
        val config = BootstrapConfigParser.defaultConfig()
        assertEquals(1, config.version)
        assertEquals("http://provider-domain.xyz:8080", config.provider.serverUrl)
        assertFalse(config.curation.enableFullProviderCatalog)
        assertEquals(listOf(101, 102, 103, 205, 301), config.curation.allowedChannelIds)
        assertEquals(listOf(12, 45, 88), config.curation.favoriteSeriesIds)
        assertEquals(listOf(8901, 9022), config.curation.favoriteMovieIds)
        assertEquals("hub", config.behavior.bootMode)
        assertEquals(3, config.behavior.retryAttempts)
        assertTrue(config.behavior.autoPlayLastChannel)
    }

    @Test
    fun `SDS 4_1 and 4_2 reconnect state machine transitions through 3 attempts to TerminalFailure`() {
        val machine = PlaybackReconnectStateMachine(maxAttempts = 3)
        assertTrue(machine.currentState is StreamRecoveryState.ActivePlay)

        // Attempt 1: Immediate reconnect (0ms delay, 4000ms timeout)
        val state1 = machine.onStreamDropOrFailure() as StreamRecoveryState.Reconnecting
        assertEquals(1, state1.attempt)
        assertEquals(0L, state1.delayBeforeAttemptMs)
        assertEquals(4000L, state1.attemptTimeoutMs)
        assertEquals("Re-connecting to video stream... (Attempt 1 of 3)", state1.bannerMessage)

        // Attempt 2: 2000ms backoff delay
        val state2 = machine.onStreamDropOrFailure() as StreamRecoveryState.Reconnecting
        assertEquals(2, state2.attempt)
        assertEquals(2000L, state2.delayBeforeAttemptMs)
        assertEquals("Re-connecting to video stream... (Attempt 2 of 3)", state2.bannerMessage)

        // Attempt 3: 2000ms backoff delay
        val state3 = machine.onStreamDropOrFailure() as StreamRecoveryState.Reconnecting
        assertEquals(3, state3.attempt)
        assertEquals(2000L, state3.delayBeforeAttemptMs)
        assertEquals("Re-connecting to video stream... (Attempt 3 of 3)", state3.bannerMessage)

        // After 3 failed attempts -> TerminalFailure
        val terminal = machine.onStreamDropOrFailure()
        assertTrue(terminal is StreamRecoveryState.TerminalFailure)

        // Selecting [ TRY AGAIN ] restarts the 3-attempt cycle at Attempt 1
        val restarted = machine.restartCycle() as StreamRecoveryState.Reconnecting
        assertEquals(1, restarted.attempt)
    }

    @Test
    fun `SDS 7_2 storage guardrails filter uncurated channels and purge expired or beyond-24h EPG entries`() {
        val config = BootstrapConfigParser.defaultConfig()
        val curatedChannels = TvfsRepository.getCuratedChannels(config)
        assertTrue(curatedChannels.none { it.id == 999 })
        assertEquals(5, curatedChannels.size)

        val now = 1_700_000_000_000L
        val entries = listOf(
            // Expired entry (ended in the past) -> should be purged
            EpgProgramEntry(101, "Old News", "Past", now - 7200_000L, now - 1000L),
            // Current allowed entry -> should be retained
            EpgProgramEntry(101, "CBS Morning News", "Current", now - 1800_000L, now + 1800_000L),
            // Beyond 24h window -> should be excluded
            EpgProgramEntry(
                101,
                "Far Future Program",
                "Too far ahead",
                now + TvfsRepository.EPG_RETENTION_WINDOW_MS + 60_000L,
                now + TvfsRepository.EPG_RETENTION_WINDOW_MS + 3600_000L
            ),
            // Uncurated channel 999 -> should be excluded
            EpgProgramEntry(999, "Uncurated Show", "Ignored", now, now + 3600_000L)
        )

        val retained = TvfsRepository.filterAndPurgeEpgSchedule(entries, now, config)
        assertEquals(1, retained.size)
        assertEquals("CBS Morning News", retained.first().title)
    }

    @Test
    fun `SDS 6_1 unified search returns explicit badges for Live Channels, Movies, and Series`() {
        val allResults = TvfsRepository.searchCatalog("")
        assertTrue(allResults.any { it.badge == SearchBadge.LIVE_CHANNEL })
        assertTrue(allResults.any { it.badge == SearchBadge.MOVIE })
        assertTrue(allResults.any { it.badge == SearchBadge.SERIES })

        val newsResults = TvfsRepository.searchCatalog("CBS")
        assertEquals(1, newsResults.size)
        assertEquals(SearchBadge.LIVE_CHANNEL, newsResults.first().badge)
    }
}
