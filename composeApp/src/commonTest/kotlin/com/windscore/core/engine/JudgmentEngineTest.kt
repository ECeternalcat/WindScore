package com.windscore.core.engine

import com.windscore.core.model.NoteEntity
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JudgmentEngineTest {

    private fun score(vararg notes: NoteEntity): Score {
        val total = notes.maxOfOrNull { it.startTick + it.durationTick } ?: 0L
        return Score(notes.toList(), divisions = 4, tempo = 120, totalTicks = total)
    }

    private fun note(id: Int, pitch: Int, start: Long, dur: Long = 4L, track: TrackType = TrackType.RIGHT_HAND): NoteEntity =
        NoteEntity(id, pitch, track, start, dur)

    @Test
    fun singleNotesAdvanceWhenCorrectKeyPressed() {
        val s = score(note(0, 60, 0), note(1, 62, 4), note(2, 64, 8))
        val engine = JudgmentEngine(s)

        assertEquals(0L, engine.currentTick, "初始游标在第一组")
        // 按错音：不推进
        engine.onNoteOn(72)
        assertEquals(0L, engine.currentTick, "错音不推进")
        // 按对：推进到下一组
        engine.onNoteOn(60)
        assertEquals(4L, engine.currentTick)
        assertTrue(s.notes[0].isHit)
        assertFalse(s.notes[1].isHit)
        engine.onNoteOn(62)
        assertEquals(8L, engine.currentTick)
        engine.onNoteOn(64)
        assertTrue(engine.isFinished)
    }

    @Test
    fun chordRequiresAllPitchesTogether() {
        // 和弦 C-E-G 同一 tick
        val s = score(note(0, 60, 0), note(1, 64, 0), note(2, 67, 0), note(3, 72, 4))
        val engine = JudgmentEngine(s)

        // 坑 3.2：先按一个和弦音不应误判推进
        engine.onNoteOn(60)
        assertEquals(0L, engine.currentTick, "和弦未按齐不推进")
        engine.onNoteOn(64)
        assertEquals(0L, engine.currentTick, "还差一个不推进")
        engine.onNoteOn(67)
        assertEquals(4L, engine.currentTick, "三个都按齐才推进")
        assertTrue(s.notes[0].isHit && s.notes[1].isHit && s.notes[2].isHit)
    }

    @Test
    fun wrongExtraNotesAreIgnoredNoPenalty() {
        val s = score(note(0, 60, 0), note(1, 62, 4))
        val engine = JudgmentEngine(s)
        // 按一堆错音
        engine.onNoteOn(50); engine.onNoteOn(99); engine.onNoteOn(30)
        assertEquals(0L, engine.currentTick, "错音完全无视")
        // 即便夹着错音，按对正确音仍能推进
        engine.onNoteOn(60)
        assertEquals(4L, engine.currentTick)
    }

    @Test
    fun heldKeySatisfiesRepeatedOrLongNotes() {
        // 文档 2.3.3 / 3.2：只要“按在手里”就算过。一个音只需按一次，无论多长
        // 或与前一音相邻重复——长按不应被要求按第二下。
        val s = score(note(0, 60, 0), note(1, 60, 4), note(2, 60, 8))
        val engine = JudgmentEngine(s)

        // 按下一次 60，一直按住：三组同音应全部自动推进（无需第二下）
        engine.onNoteOn(60)
        assertTrue(engine.isFinished, "按住一个音应推进全部相邻同音，无需第二下")
        assertTrue(s.notes.all { it.isHit }, "三个音都应命中")
    }

    @Test
    fun differentPitchStillRequiresItsOwnPress() {
        // 不同音高的旋律：C 后接 E，按住 C 不应跳过 E
        val s = score(note(0, 60, 0), note(1, 64, 4), note(2, 67, 8))
        val engine = JudgmentEngine(s)
        engine.onNoteOn(60)
        assertEquals(4L, engine.currentTick, "C 命中推进到 E")
        assertEquals(4L, engine.currentTick, "按住 C 不能跳过 E（不同音高）")
        engine.onNoteOn(64)
        assertEquals(8L, engine.currentTick)
        engine.onNoteOn(67)
        assertTrue(engine.isFinished)
    }

    @Test
    fun pendingPitchesHighlightsCurrentGroup() {
        val s = score(note(0, 60, 0), note(1, 64, 0), note(2, 72, 4))
        val engine = JudgmentEngine(s)
        assertEquals(setOf(60, 64), engine.pendingPitches(), "高亮当前和弦")
        engine.onNoteOn(60); engine.onNoteOn(64)
        assertEquals(setOf(72), engine.pendingPitches(), "推进后高亮下一组")
    }

    // ---- Phase 2 Step 2：轨道隔离与自动托管 ----

    @Test
    fun rightOnlyHostsLeftHandAndOnlyRequiresRight() {
        // 组0: 右手 C4(60) + 左手 C3(48)；组1: 右手 E4(64)
        val s = score(
            note(0, 60, 0, track = TrackType.RIGHT_HAND),
            note(1, 48, 0, track = TrackType.LEFT_HAND),
            note(2, 64, 4, track = TrackType.RIGHT_HAND),
        )
        val engine = JudgmentEngine(s, PracticeMode.RIGHT_ONLY)

        val hosted = engine.arrive()
        assertEquals(listOf(48), hosted.map { it.pitch }, "组0 左手自动托管")
        assertTrue(s.notes[1].isHit, "托管音立即标记命中")
        assertFalse(s.notes[0].isHit, "右手尚未弹，未命中")
        assertEquals(setOf(60), engine.pendingPitches(), "用户只需按右手")

        // 只按左手（被托管的那只）不应推进
        engine.onNoteOn(48)
        assertEquals(0L, engine.currentTick, "按托管音不推进")
        // 按右手才推进
        engine.onNoteOn(60)
        assertEquals(4L, engine.currentTick)
        assertTrue(s.notes[0].isHit)
    }

    @Test
    fun leftOnlyHostsRightHand() {
        val s = score(
            note(0, 60, 0, track = TrackType.RIGHT_HAND),
            note(1, 48, 0, track = TrackType.LEFT_HAND),
        )
        val engine = JudgmentEngine(s, PracticeMode.LEFT_ONLY)
        val hosted = engine.arrive()
        assertEquals(listOf(60), hosted.map { it.pitch }, "左手模式托管右手")
        assertEquals(setOf(48), engine.pendingPitches(), "用户只需按左手 48")
        engine.onNoteOn(48)
        assertTrue(engine.isFinished)
    }

    @Test
    fun pureHostedGroupAdvancesImmediately() {
        // 组0: 只有左手（RIGHT_ONLY 下为纯托管）→ 到达即推进
        val s = score(
            note(0, 48, 0, track = TrackType.LEFT_HAND),
            note(1, 60, 4, track = TrackType.RIGHT_HAND),
        )
        val engine = JudgmentEngine(s, PracticeMode.RIGHT_ONLY)
        val hosted = engine.arrive()
        assertEquals(listOf(48), hosted.map { it.pitch }, "纯托管组到达即发声")
        assertEquals(4L, engine.currentTick, "无练习音的组立即推进到下一组")
        assertEquals(setOf(60), engine.pendingPitches())
    }

    @Test
    fun consecutiveHostedGroupsDoNotBurst() {
        // 组0,1,2 均为纯托管左手；组3 为右手练习组
        val s = score(
            note(0, 48, 0, track = TrackType.LEFT_HAND),
            note(1, 50, 4, track = TrackType.LEFT_HAND),
            note(2, 52, 8, track = TrackType.LEFT_HAND),
            note(3, 60, 12, track = TrackType.RIGHT_HAND),
        )
        val engine = JudgmentEngine(s, PracticeMode.RIGHT_ONLY)
        val firstHosted = engine.arrive()
        assertEquals(listOf(48), firstHosted.map { it.pitch }, "arrive 只处理第一个托管组")
        assertEquals(4L, engine.currentTick, "推进到组1 后停止，不 burst 到练习组")

        // 用户按键不应让托管段 burst 推进
        engine.onNoteOn(99)
        assertEquals(4L, engine.currentTick, "用户按键不推进纯托管段")

        // 上层按节奏显式推进
        val second = engine.advanceHosted()
        assertEquals(listOf(50), second.map { it.pitch })
        assertEquals(8L, engine.currentTick)

        val third = engine.advanceHosted()
        assertEquals(listOf(52), third.map { it.pitch })
        assertEquals(12L, engine.currentTick)
        assertEquals(setOf(60), engine.pendingPitches())
    }

    // ---- Phase 3：A/B 循环练习 ----

    @Test
    fun abLoopJumpsBackAtEnd() {
        val s = score(
            note(0, 60, 0),
            note(1, 62, 4),
            note(2, 64, 8),
            note(3, 65, 12),
            note(4, 67, 16),
        )
        val engine = JudgmentEngine(s)
        engine.arrive()
        engine.setLoop(4L, 12L) // 只在 tick 4~12 之间循环

        // 推进到 tick=12 边界时应回卷到 tick=4
        engine.onNoteOn(62) // tick 4
        assertEquals(8L, engine.currentTick)
        engine.onNoteOn(64) // tick 8
        assertEquals(4L, engine.currentTick, "越过循环终点后应回卷到循环起点")

        // 循环段内音符应重置为未命中，可再次练习
        assertFalse(s.notes[1].isHit, "回卷后循环段内音符应重置")
        assertFalse(s.notes[2].isHit, "回卷后循环段内音符应重置")
    }

    @Test
    fun abLoopDoesNotInflateCleanHitCount() {
        val s = score(
            note(0, 60, 0),
            note(1, 62, 4),
            note(2, 64, 8),
        )
        val engine = JudgmentEngine(s)
        engine.arrive()
        engine.setLoop(0L, 8L)

        engine.onNoteOn(60)
        assertEquals(1, engine.cleanHitCount())
        engine.onNoteOn(62)
        assertEquals(0L, engine.currentTick, "越过循环终点 8 后回卷到循环起点 0")
        // 回卷后 cleanHitCount 应重新计算，不累计超过总音符数
        assertEquals(0, engine.cleanHitCount(), "回卷后循环段内干净命中应重置")
    }

    @Test
    fun clearLoopRestoresFullPlaythrough() {
        val s = score(
            note(0, 60, 0),
            note(1, 62, 4),
            note(2, 64, 8),
        )
        val engine = JudgmentEngine(s)
        engine.arrive()
        engine.setLoop(0L, 4L)
        engine.onNoteOn(60)
        assertEquals(0L, engine.currentTick, "在循环内")
        engine.clearLoop()
        engine.onNoteOn(60)
        assertEquals(4L, engine.currentTick, "清除循环后可继续向前")
    }

    // ---- Phase 2 Step 3a：准度计分 ----

    @Test
    fun cleanRunScoresFull() {
        val s = score(note(0, 60, 0), note(1, 62, 4), note(2, 64, 8))
        val engine = JudgmentEngine(s)
        engine.arrive()
        engine.onNoteOn(60); engine.onNoteOn(62); engine.onNoteOn(64)
        assertTrue(engine.isFinished)
        assertEquals(3, engine.totalActiveNotes())
        assertEquals(3, engine.cleanHitCount())
        assertEquals(0, engine.wrongPressCount())
        assertEquals(100, engine.scorePercent())
    }

    @Test
    fun wrongPressLowersScore() {
        val s = score(note(0, 60, 0), note(1, 62, 4), note(2, 64, 8))
        val engine = JudgmentEngine(s)
        engine.arrive()
        engine.onNoteOn(72) // 组0 错按 → 污染组0
        engine.onNoteOn(60) // 组0 正确 → 推进（但不计入干净命中）
        engine.onNoteOn(62) // 组1 干净
        engine.onNoteOn(64) // 组2 干净
        assertTrue(engine.isFinished)
        assertEquals(3, engine.totalActiveNotes())
        assertEquals(2, engine.cleanHitCount(), "组0 被污染，仅组1/2 干净")
        assertEquals(1, engine.wrongPressCount())
        assertEquals(66, engine.scorePercent(), "2/3 = 66%")
        val heatmap = engine.groupWrongCounts()
        assertEquals(1, heatmap[0])
        assertEquals(0, heatmap[1])
        assertEquals(0, heatmap[2])
    }
}
