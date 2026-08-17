package com.windscore.core.engine

/** The score position range currently available to playback and practice operations. */
data class PlaybackRegion(
    val scoreStart: Long,
    val scoreEnd: Long,
    val loopStart: Long? = null,
    val loopEnd: Long? = null,
) {
    val start: Long
        get() = normalizedLoop()?.first ?: scoreStart

    val end: Long
        get() = normalizedLoop()?.last ?: scoreEnd

    fun constrain(position: Long): Long = position.coerceIn(start, end)

    fun containsNoteAt(tick: Long): Boolean = tick >= start && tick < end

    fun normalizedLoop(): LongRange? {
        val normalizedScoreStart = minOf(scoreStart, scoreEnd)
        val normalizedScoreEnd = maxOf(scoreStart, scoreEnd)
        val requestedStart = loopStart ?: return null
        val requestedEnd = loopEnd ?: return null
        val start = requestedStart.coerceIn(normalizedScoreStart, normalizedScoreEnd)
        val end = requestedEnd.coerceIn(start, normalizedScoreEnd)
        return (start..end).takeIf { end > start }
    }
}
