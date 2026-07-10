package com.windscore

import com.windscore.core.engine.JudgmentEngine
import com.windscore.core.loadScore
import com.windscore.core.model.PracticeMode
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 端到端集成测试：用真实乐谱 konekuto-claris.mxl 跑完整流程。
 * 模拟“完美演奏”——依次按下每组所需音（含和弦），验证 解析→引擎→托管→计分 全链路。
 */
class IntegrationTest {

    @Test
    fun fullPlaythroughBothHands() {
        val score = loadScore()
        val engine = JudgmentEngine(score, PracticeMode.BOTH)
        engine.arrive()

        autoPlay(engine)
        assertTrue(engine.isFinished, "双手模式应能完整弹完整谱")
        println("[集成-BOTH] ${engine.groupCount} 组完成, 得分 ${engine.scorePercent()}%, " +
            "干净 ${engine.cleanHitCount()}/${engine.totalActiveNotes()}, 错按 ${engine.wrongPressCount()}")
        assertEqualsClean(engine)
    }

    @Test
    fun fullPlaythroughRightOnlyHostsLeft() {
        val score = loadScore()
        val engine = JudgmentEngine(score, PracticeMode.RIGHT_ONLY)
        engine.arrive() // 触发组0 左手托管

        autoPlay(engine)
        assertTrue(engine.isFinished, "右手模式（左手托管）应能完整弹完")
        println("[集成-RIGHT_ONLY] ${engine.groupCount} 组完成, 得分 ${engine.scorePercent()}%, " +
            "干净 ${engine.cleanHitCount()}/${engine.totalActiveNotes()}, 错按 ${engine.wrongPressCount()}")
        assertEqualsClean(engine)
    }

    @Test
    fun fumbledPlaythroughLowersScore() {
        val score = loadScore()
        val engine = JudgmentEngine(score, PracticeMode.BOTH)
        engine.arrive()

        // 每组先按一个错音（72，多半不在需求里），再按对的 —— 模拟长辈卡壳
        var groups = 0
        while (!engine.isFinished && groups < 100000) {
            val required = engine.pendingPitches()
            if (required.isEmpty()) {
                // 纯托管组显式推进
                engine.advanceHosted()
                groups++
                continue
            }
            engine.onNoteOn(72) // 故意错按
            required.forEach { engine.onNoteOn(it) }
            required.forEach { engine.onNoteOff(it) }
            groups++
        }
        assertTrue(engine.isFinished)
        println("[集成-错按] 得分 ${engine.scorePercent()}%, 错按 ${engine.wrongPressCount()}")
        assertTrue(engine.wrongPressCount() > 0, "应有错按记录")
        assertTrue(engine.scorePercent() < 100, "错按应拉低分数")
    }

    /** 自动完美演奏：按下当前组所需、推进、松键（为下一组 fresh 准备）。 */
    private fun autoPlay(engine: JudgmentEngine) {
        var steps = 0
        while (!engine.isFinished && steps < 100000) {
            val required = engine.pendingPitches()
            if (required.isEmpty()) {
                // 纯托管组需显式推进（避免 burst，由上层按节奏调度）
                engine.advanceHosted()
                steps++
                continue
            }
            required.forEach { engine.onNoteOn(it) }
            required.forEach { engine.onNoteOff(it) }
            steps++
        }
    }

    private fun assertEqualsClean(engine: JudgmentEngine) {
        // 完美演奏应 100% 干净（无错按）
        assertTrue(engine.scorePercent() == 100, "完美演奏应 100%，实际 ${engine.scorePercent()}%")
    }
}
