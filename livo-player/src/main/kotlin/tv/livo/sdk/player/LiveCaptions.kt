package tv.livo.sdk.player

import tv.livo.sdk.models.CaptionCue

internal const val CAPTION_CHARS_PER_SECOND = 17.0
internal const val CAPTION_MIN_DWELL_MS = 1_200L
internal const val CAPTION_MAX_DWELL_MS = 6_000L
internal const val CAPTION_RUSH_DWELL_MS = 2_500L
internal const val CAPTION_BACKLOG_DROP = 8
internal const val LIVE_CUE_EXTRA_LAG_MS = 2_000L
internal const val LAG_SPIKE_IGNORE_MS = 2_500L
internal const val CAPTION_MAX_CHARS_PER_LINE = 42
internal const val CAPTION_MAX_LINES = 2
internal const val CAPTION_GAP_FILL_MS = 1_000.0

public data class CaptionScreen(val text: String, val speaker: String? = null)

public data class LiveCaptionPlayout(
    val screen: CaptionScreen? = null,
    val hideAt: Long = 0,
    val cursor: Long = 0,
    val queue: List<CaptionScreen> = emptyList(),
    val lastLagMs: Double? = null,
)

public val EMPTY_LIVE_CAPTION_PLAYOUT: LiveCaptionPlayout = LiveCaptionPlayout()

public fun captionDwellMs(text: String, backlog: Int = 0): Long {
    val chars = text.replace(Regex("\\s+"), " ").trim().length
    val raw = (chars / CAPTION_CHARS_PER_SECOND) * 1000
    val max = if (backlog > 0) CAPTION_RUSH_DWELL_MS else CAPTION_MAX_DWELL_MS
    return raw.toLong().coerceIn(CAPTION_MIN_DWELL_MS, max)
}

public fun splitCaptionScreens(text: String, maxCharsPerLine: Int = CAPTION_MAX_CHARS_PER_LINE): List<String> {
    val trimmed = text.trim().replace(Regex("\\s+"), " ")
    if (trimmed.isEmpty()) return emptyList()
    val words = trimmed.split(" ")
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
        val next = if (current.isEmpty()) word else "$current $word"
        if (next.length > maxCharsPerLine && current.isNotEmpty()) {
            lines += current.toString()
            current = StringBuilder(word)
        } else {
            current = StringBuilder(next)
        }
    }
    if (current.isNotEmpty()) lines += current.toString()
    val screens = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        screens += lines.subList(index, minOf(index + CAPTION_MAX_LINES, lines.size)).joinToString("\n")
        index += CAPTION_MAX_LINES
    }
    return screens
}

public fun captionScreenRow(text: String, speaker: String?): CaptionScreen {
    val prefix = if (speaker.isNullOrBlank()) "" else "– "
    return CaptionScreen(prefix + text, speaker)
}

public fun effectiveLiveLag(previous: Double?, next: Double?): Double? {
    if (next == null || !next.isFinite() || next < 0) return previous
    if (previous != null && next - previous < LAG_SPIKE_IGNORE_MS && next > previous) {
        return previous
    }
    return next
}

public fun advanceLiveCaptionPlayout(state: LiveCaptionPlayout, cues: List<CaptionCue>, now: Long, lagMs: Double?, extraLagMs: Long = LIVE_CUE_EXTRA_LAG_MS): LiveCaptionPlayout {
    var screen = state.screen
    var hideAt = state.hideAt
    var cursor = state.cursor
    val queue = state.queue.toMutableList()
    val lag = effectiveLiveLag(state.lastLagMs, lagMs)
    if (lag != null && lag.isFinite() && lag >= 0) {
        val threshold = lag + extraLagMs
        val incoming =
            cues
                .filter {
                    it.text.isNotBlank() &&
                        it.receivedAt != null &&
                        it.receivedAt!! > cursor &&
                        now - it.receivedAt!! >= threshold
                }.sortedBy { it.receivedAt }
        for (cue in incoming) {
            for (text in splitCaptionScreens(cue.text)) {
                queue += captionScreenRow(text, cue.speaker)
            }
            cursor = cue.receivedAt ?: cursor
        }
        while (queue.size > CAPTION_BACKLOG_DROP) queue.removeAt(0)
    }
    if (now >= hideAt) {
        val next = if (queue.isNotEmpty()) queue.removeAt(0) else null
        if (next != null) {
            screen = next
            hideAt = now + captionDwellMs(next.text, queue.size)
        } else {
            screen = null
        }
    }
    return LiveCaptionPlayout(screen, hideAt, cursor, queue.toList(), lag)
}

public data class TimedCaptionCue(val text: String, val start: Double, val end: Double)

public fun captionScreenAtTime(cues: List<TimedCaptionCue>, time: Double): CaptionScreen? {
    if (!time.isFinite()) return null
    val ordered = cues.filter { it.text.isNotBlank() && it.start.isFinite() && it.end.isFinite() }.sortedBy { it.start }
    for (index in ordered.indices) {
        val cue = ordered[index]
        val next = ordered.getOrNull(index + 1)
        val end =
            if (next != null) {
                val gapMs = (next.start - cue.end) * 1000
                if (gapMs > 0 && gapMs < CAPTION_GAP_FILL_MS) next.start else cue.end
            } else {
                cue.end
            }
        if (time >= cue.start && time < end) {
            return CaptionScreen(splitCaptionScreens(cue.text).firstOrNull() ?: cue.text)
        }
    }
    return null
}
