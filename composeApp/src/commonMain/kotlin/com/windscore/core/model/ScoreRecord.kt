package com.windscore.core.model

import kotlinx.serialization.Serializable

/**
 * 一次评分的记录（持久化到 `<appStorageDir>/history/<scoreId>.json`）。
 * 纯准度计分，不含时机维度。
 */
@Serializable
data class ScoreRecord(
    /** 评分完成时的 epoch 毫秒。 */
    val timestamp: Long,
    /** 准度百分比 0..100。 */
    val percent: Int,
    /** 干净命中（正确）的练习轨道音符数。 */
    val cleanHits: Int,
    /** 练习轨道音符总数。 */
    val totalNotes: Int,
    /** 全程错按次数。 */
    val wrongPresses: Int,
    /** 本次评分使用的练习模式（单/双手）。 */
    val practiceMode: PracticeMode,
)
