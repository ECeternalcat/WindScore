package com.windscore.core.engine

import com.windscore.core.model.NoteEntity
import com.windscore.core.model.Score
import com.windscore.core.model.TrackSelection
import com.windscore.core.model.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 判定策略与轨道归属解耦的契约测试：
 * 注入非默认策略可以改变命中规则，而不触碰 TrackSelection 语义。
 */
class JudgmentPolicyTest {

    @Test
    fun waitModeRequiresFreshRepressForRepeatedNotes() {
        val engine = JudgmentEngine(repeatedPitchScore())

        engine.onNoteOn(60)

        assertEquals(4L, engine.currentTick, "wait mode stops at the repeated note until re-press")
    }

    @Test
    fun customPolicyCanRelaxTheFreshKeyRule() {
        val lenient = object : JudgmentPolicy {
            override fun isSatisfied(required: Set<Int>, activeKeys: Set<Int>, freshKeys: Set<Int>) =
                required.all { it in activeKeys }
        }
        val engine = JudgmentEngine(repeatedPitchScore(), TrackSelection.BOTH_HANDS, lenient)

        engine.onNoteOn(60)

        assertEquals(8L, engine.currentTick, "lenient policy advances through repeated notes while held")
        assertEquals(true, engine.isFinished)
    }

    @Test
    fun defaultPolicyIsWaitMode() {
        val engine = JudgmentEngine(repeatedPitchScore())

        assertEquals(WaitModeJudgment, engine.policy)
        assertEquals(TrackSelection.BOTH_HANDS, engine.trackSelection)
    }

    private fun repeatedPitchScore() = Score(
        notes = listOf(
            NoteEntity(0, 60, TrackType.RIGHT_HAND, 0, 2),
            NoteEntity(1, 60, TrackType.RIGHT_HAND, 4, 2),
        ),
        divisions = 4,
        tempo = 120,
        totalTicks = 8,
    )
}
