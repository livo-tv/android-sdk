package tv.livo.sdk.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tv.livo.sdk.models.CaptionCue

class LiveCaptionsTest {
    @Test
    fun dwellClampsAndRushesWhenQueued() {
        assertEquals(CAPTION_MIN_DWELL_MS, captionDwellMs("hi"))
        assertEquals(CAPTION_RUSH_DWELL_MS, captionDwellMs("a".repeat(400), backlog = 3))
    }

    @Test
    fun livePlayoutWaitsForLagThenShows() {
        val cues = listOf(CaptionCue("Hello there friends", receivedAt = 10_000, speaker = "1"))
        val waiting =
            advanceLiveCaptionPlayout(EMPTY_LIVE_CAPTION_PLAYOUT, cues, 11_000, lagMs = 8_000.0)
        assertNull(waiting.screen)
        val shown = advanceLiveCaptionPlayout(waiting, cues, 20_500, lagMs = 8_000.0)
        assertNotNull(shown.screen)
        assertEquals(true, shown.screen!!.text.startsWith("– "))
    }

    @Test
    fun ignoresUpwardLagSpikesUnderThreshold() {
        val first = effectiveLiveLag(null, 8_000.0)
        val spiked = effectiveLiveLag(first, 10_000.0)
        assertEquals(8_000.0, spiked)
    }

    @Test
    fun cueAtTimeNeverTreatsNaNAsZero() {
        assertNull(captionScreenAtTime(listOf(TimedCaptionCue("hi", 0.0, 1.0)), Double.NaN))
    }
}
