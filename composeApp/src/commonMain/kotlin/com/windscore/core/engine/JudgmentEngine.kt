package com.windscore.core.engine

import com.windscore.core.model.NoteEntity
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType

/**
 * 判定状态机 ——“等待模式”的核心（文档 2.3）。Phase 2 扩展：单/双手隔离与自动托管（文档 4.2）。
 *
 * 事件驱动的步进系统：
 * - [currentTick] 永远等于“当前组”的起始 tick。
 * - 一组 = 同一 startTick 的若干音（可能是和弦，可能跨左右手）。
 *
 * 练习模式 [practiceMode]：
 * - [PracticeMode.BOTH]：双手都要按，无托管。
 * - [PracticeMode.RIGHT_ONLY]：用户只按右手；左手轨道（LEFT_HAND）由系统**自动托管**——
 *   当游标到达其所在组时立即标记 isHit 并交由上层 SoftSynth 发声。
 * - [PracticeMode.LEFT_ONLY]：镜像，托管右手。
 *
 * 命中条件：当前组“练习轨道”的音高 ⊆ activeKeys 且 ⊆ freshKeys（本组激活后新按下）。
 * 按错/漏按：无视，无负面反馈（坑 3.2）。和弦微小时间差由每次 NoteOn 重判天然容忍。
 * 重复音/震音：freshKeys 要求重新击键；延音长音（Tie 已合并）只属其起始组。
 *
 * [step] / [onNoteOn] 返回“本次因游标落到新组而应自动托管的 NoteEntity 列表”，供上层发声。
 *
 * 纯 Kotlin，不依赖 Compose / 协程，便于单元测试。
 */
class JudgmentEngine(
    score: Score,
    val practiceMode: PracticeMode = PracticeMode.BOTH,
) {

    /** 按起始 tick 分组（同 tick 多音 = 和弦 = 一组）。保留对原 [NoteEntity] 的引用。 */
    private val groups: List<List<NoteEntity>> = score.notes
        .groupBy { it.startTick }
        .toSortedMap()
        .values
        .toList()

    private val totalTicks: Long = score.totalTicks

    private var groupIndex = 0
    private val activeKeys = mutableSetOf<Int>()
    private val freshKeys = mutableSetOf<Int>()
    /** 上次已“到达”（已托管）的组号，避免重复发声。 */
    private var lastArrivedGroupIndex = -1

    // —— 准度计分（文档 4.3，抛弃时机判定，按“是否干净命中”计分）——
    private val groupWrongCounts = IntArray(groups.size)
    private var currentGroupDirty = false
    /** 用户干净命中（所在组弹奏期间无任何错按）的“练习轨道”音符数。 */
    private var cleanHitCount = 0
    /** 练习轨道音符总数（分母）。 */
    private val totalActiveNotes: Int = groups.sumOf { group -> group.count { isActiveTrack(it.track) } }
    /** 全程错按次数。 */
    private var wrongPressCount = 0

    /** 当前游标 tick = 当前组的 startTick；全部完成则为 totalTicks。 */
    val currentTick: Long
        get() = if (groupIndex < groups.size) groups[groupIndex].first().startTick else totalTicks

    val isFinished: Boolean get() = groupIndex >= groups.size

    val progress: Int get() = groupIndex
    val groupCount: Int get() = groups.size

    /** 准度得分百分比（0..100）。 */
    fun scorePercent(): Int =
        if (totalActiveNotes == 0) 100 else (cleanHitCount * 100 / totalActiveNotes).coerceIn(0, 100)

    /** 每组的错按次数（用于结算页“卡壳热力图”）。 */
    fun groupWrongCounts(): IntArray = groupWrongCounts.copyOf()

    /** 练习轨道音符总数（分母）。 */
    fun totalActiveNotes(): Int = totalActiveNotes

    /** 干净命中数（分子）。 */
    fun cleanHitCount(): Int = cleanHitCount

    /** 全程错按次数。 */
    fun wrongPressCount(): Int = wrongPressCount

    /** 当前物理键盘处于 NoteOn 的音高集合。 */
    fun activeKeys(): Set<Int> = activeKeys.toSet()

    /** 当前组“需要用户按”的音高（仅练习轨道）。用于 UI 高亮提示。 */
    fun pendingPitches(): Set<Int> =
        groups.getOrNull(groupIndex)
            ?.filter { isActiveTrack(it.track) }
            ?.mapTo(mutableSetOf()) { it.pitch }
            ?: emptySet()

    /** 该轨道是否由用户练习（true）/系统托管（false）。 */
    fun isActiveTrack(track: TrackType): Boolean = when (practiceMode) {
        PracticeMode.BOTH -> true
        PracticeMode.RIGHT_ONLY -> track == TrackType.RIGHT_HAND
        PracticeMode.LEFT_ONLY -> track == TrackType.LEFT_HAND
    }

    /** 初始/重置后调用：游标落到第一组，返回应自动托管的音。 */
    fun arrive(): List<NoteEntity> = step()

    /** 用户按下某音高。返回本次新到达组应自动托管的音。 */
    fun onNoteOn(pitch: Int): List<NoteEntity> {
        activeKeys.add(pitch)
        freshKeys.add(pitch)
        recordPress(pitch)
        return step()
    }

    fun onNoteOff(pitch: Int) {
        activeKeys.remove(pitch)
        // 松键不推进判定（时间轴停滞直到按对）
    }

    fun reset(): List<NoteEntity> {
        groupIndex = 0
        lastArrivedGroupIndex = -1
        activeKeys.clear()
        freshKeys.clear()
        groups.flatten().forEach { it.isHit = false }
        for (i in groupWrongCounts.indices) groupWrongCounts[i] = 0
        currentGroupDirty = false
        cleanHitCount = 0
        wrongPressCount = 0
        return step()
    }

    /** 准度统计：若该音不在当前组的练习轨道需求中，记为“错按”，污染当前组。 */
    private fun recordPress(pitch: Int) {
        val group = groups.getOrNull(groupIndex) ?: return
        val required = group.asSequence()
            .filter { isActiveTrack(it.track) }
            .mapTo(mutableSetOf()) { it.pitch }
        if (pitch !in required) {
            wrongPressCount++
            groupWrongCounts[groupIndex] = groupWrongCounts[groupIndex] + 1
            currentGroupDirty = true
        }
    }

    /**
     * 推进判定。游标每落到一个新组，标记并收集其托管音；当练习轨道音高满足则推进到下一组。
     * 返回本次“到达”应托管的音。
     */
    private fun step(): List<NoteEntity> {
        val hostedPlayed = mutableListOf<NoteEntity>()
        while (groupIndex < groups.size) {
            val group = groups[groupIndex]
            // 游标落到新组 → 托管该组的非练习轨道音
            if (groupIndex > lastArrivedGroupIndex) {
                val hosted = group.filter { !isActiveTrack(it.track) }
                hosted.forEach { it.isHit = true }
                hostedPlayed.addAll(hosted)
                lastArrivedGroupIndex = groupIndex
            }
            val required = group
                .filter { isActiveTrack(it.track) }
                .mapTo(mutableSetOf()) { it.pitch }
            // 命中条件：练习轨道音高全部新按下且仍按住；多余/错音无视（坑 3.2）
            val satisfied = required.all { it in activeKeys && it in freshKeys }
            if (satisfied) {
                // 准度：该组弹奏期间若未错按，则计入“干净命中”
                if (!currentGroupDirty) {
                    cleanHitCount += group.count { isActiveTrack(it.track) }
                }
                group.forEach { it.isHit = true }
                groupIndex++
                freshKeys.clear()
                currentGroupDirty = false
            } else {
                break
            }
        }
        return hostedPlayed
    }
}
