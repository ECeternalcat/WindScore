package com.windscore.core.model

import kotlin.jvm.Volatile

/**
 * 降维后的唯一真理：按时间排序的一维音符单元。
 * 抛弃了五线谱排版、连音线、休止符等所有 MusicXML 排版细节。
 */
data class NoteEntity(
    val id: Int,
    val pitch: Int,
    val track: TrackType,
    val startTick: Long,
    val durationTick: Long,
    /** 指法提示（Phase 3），无指法时为 null。 */
    val fingering: String? = null,
) {
    @Volatile
    var isHit: Boolean = false
}
