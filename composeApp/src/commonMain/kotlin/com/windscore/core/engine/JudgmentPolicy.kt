package com.windscore.core.engine

/**
 * 判定规则：当前组的“要求音集合”如何被判为满足。
 *
 * 与轨道归属（model.TrackSelection）正交：
 * - [WaitModeJudgment]（默认）：等待模式，时间轴停滞，要求音必须被按住且是本组激活后新按下（重复音需重弹）。
 * - 未来的跟弹/节奏等练习模型在此注入新策略，而不改动轨道选择。
 */
interface JudgmentPolicy {
    fun isSatisfied(required: Set<Int>, activeKeys: Set<Int>, freshKeys: Set<Int>): Boolean
}

/** 等待模式（文档 2.3）：要求音 ⊆ activeKeys 且 ⊆ freshKeys。 */
object WaitModeJudgment : JudgmentPolicy {
    override fun isSatisfied(required: Set<Int>, activeKeys: Set<Int>, freshKeys: Set<Int>): Boolean =
        required.all { it in activeKeys && it in freshKeys }
}
