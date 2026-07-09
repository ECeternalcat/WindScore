package com.windscore.core.model

/**
 * 降维解析后的乐谱。notes 按 startTick 升序排列。
 */
data class Score(
    val notes: List<NoteEntity>,
    val divisions: Int,
    val tempo: Int,
    val totalTicks: Long,
    /** 原始 MusicXML 文本（供 OSMD 看谱模式渲染；解析降维不需要）。 */
    val rawXml: String? = null,
) {
    companion object {
        val EMPTY = Score(notes = emptyList(), divisions = 1, tempo = 0, totalTicks = 0L)
    }
}
