package com.windscore.core.model

import kotlinx.serialization.Serializable

/**
 * 轨道归属（文档 4.2 单/双手分离）：用户弹哪些手，其余轨道由系统托管。
 * 纯轨道语义，与判定规则（见 engine.JudgmentPolicy）正交：
 * - 新增判定模式（跟弹/节奏）不应改动本枚举；
 * - 新增轨道组合（如仅伴奏轨）在此扩展。
 */
@Serializable
enum class TrackSelection {
    BOTH_HANDS,
    RIGHT_HAND,
    LEFT_HAND,
}

/** 该轨道是否由用户练习（true）/系统托管（false）。 */
fun TrackSelection.isActiveTrack(track: TrackType): Boolean = when (this) {
    TrackSelection.BOTH_HANDS -> true
    TrackSelection.RIGHT_HAND -> track == TrackType.RIGHT_HAND
    TrackSelection.LEFT_HAND -> track == TrackType.LEFT_HAND
}

/** 被系统托管的轨道；双手模式为 null。 */
val TrackSelection.hostedTrack: TrackType?
    get() = when (this) {
        TrackSelection.BOTH_HANDS -> null
        TrackSelection.RIGHT_HAND -> TrackType.LEFT_HAND
        TrackSelection.LEFT_HAND -> TrackType.RIGHT_HAND
    }

/** 用户侧练习模型到引擎轨道归属的映射。 */
fun PracticeMode.trackSelection(): TrackSelection = when (this) {
    PracticeMode.BOTH -> TrackSelection.BOTH_HANDS
    PracticeMode.RIGHT_ONLY -> TrackSelection.RIGHT_HAND
    PracticeMode.LEFT_ONLY -> TrackSelection.LEFT_HAND
}
