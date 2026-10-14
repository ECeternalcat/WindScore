package com.windscore.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Fingerprint(
    val size: Long,
    val lastModified: Long
) {
    companion object {
        val EMPTY = Fingerprint(0L, 0L)
    }
}

/**
 * 降维解析后的乐谱。notes 按 startTick 升序排列。
 */
@Serializable
data class Score(
    val notes: List<NoteEntity>,
    val divisions: Int,
    val tempo: Int,
    val totalTicks: Long,
    val id: String = "",
    val fingerprint: Fingerprint = Fingerprint.EMPTY,
    val version: Int = 1,
    /** 原始 MusicXML 文本（供 OSMD 看谱模式渲染；解析降维不需要）。 */
    @Transient
    val rawXml: String? = null,
    /** Whether the score can restore MusicXML-backed sheet views. */
    val supportsSheetView: Boolean = false,
    /** 乐谱标题，用于乐谱库列表显示。为空时由 UI 层显示本地化的“未命名乐谱”。 */
    val title: String = "",
    /** Whether legacy unversioned bookmarks use the same musical tick coordinates. */
    val legacyTimingCompatible: Boolean = true,
) {
    companion object {
        const val CURRENT_VERSION = 2
        val EMPTY = Score(
            id = "",
            notes = emptyList(),
            divisions = 1,
            tempo = 0,
            totalTicks = 0L,
            fingerprint = Fingerprint.EMPTY,
            version = CURRENT_VERSION
        )
    }
}
