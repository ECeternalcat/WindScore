package com.windscore.core.engine

import com.windscore.core.model.NoteEntity
import com.windscore.core.model.Score
import com.windscore.core.model.TrackSelection
import com.windscore.core.model.TrackType
import com.windscore.core.model.isActiveTrack

/**
 * 判定状态机 ——“等待模式”的核心（文档 2.3）。Phase 2 扩展：单/双手隔离与自动托管（文档 4.2）。
 *
 * 事件驱动的步进系统：
 * - [currentTick] 永远等于“当前组”的起始 tick。
 * - 一组 = 同一 startTick 的若干音（可能是和弦，可能跨左右手）。
 *
 * 轨道归属 [trackSelection]：
 * - [TrackSelection.BOTH_HANDS]：双手都要按，无托管。
 * - [TrackSelection.RIGHT_HAND]：用户只按右手；左手轨道由系统**自动托管**——
 *   当游标到达其所在组时立即标记 isHit 并交由上层 SoftSynth 发声。
 * - [TrackSelection.LEFT_HAND]：镜像，托管右手。
 *
 * 判定规则 [policy]：当前默认为等待模式 [WaitModeJudgment]；新的练习模型
 * （跟弹/节奏等）通过注入不同策略实现，而不与轨道归属耦合。
 *
 * 命中条件（等待模式）：当前组“练习轨道”的音高 ⊆ activeKeys 且 ⊆ freshKeys（本组激活后新按下）。
 * 按错/漏按：无视，无负面反馈（坑 3.2）。和弦微小时间差由每次 NoteOn 重判天然容忍。
 * 重复音/震音：freshKeys 要求重新击键；延音长音（Tie 已合并）只属其起始组。
 *
 * [step] / [onNoteOn] 返回“本次因游标落到新组而应自动托管的 NoteEntity 列表”，供上层发声。
 *
 * 为避免连续纯托管段“burst”推进，[step] 处理完一个纯托管组后会主动返回，
 * 由上层 [ScoreSession] 按真实节奏定时调用 [advanceHosted] 继续推进。
 *
 * 纯 Kotlin，不依赖 Compose / 协程，便于单元测试。
 */
class JudgmentEngine(
    score: Score,
    val trackSelection: TrackSelection = TrackSelection.BOTH_HANDS,
    val policy: JudgmentPolicy = WaitModeJudgment,
) {

    /** 按起始 tick 分组（同 tick 多音 = 和弦 = 一组）。保留对原 [NoteEntity] 的引用。 */
    private val groups: List<List<NoteEntity>> = score.notes
        .groupBy { it.startTick }
        .toSortedMap()
        .values
        .toList()

    private val totalTicks: Long = score.totalTicks

    private val completedGroups = mutableListOf<CompletedGroup>()

    /** Only performance advancement emits evidence; seek/reset/step never do. */
    data class CompletedGroup(
        val tick: Long,
        val group: Int,
        val boundaryTick: Long,
        val completedPass: Boolean,
        val fullCompletion: Boolean,
    )

    fun drainCompletedGroups(): List<CompletedGroup> = completedGroups.toList().also { completedGroups.clear() }

    private var groupIndex = 0
    private val activeKeys = mutableSetOf<Int>()
    /** Session-local note hit state; score data remains immutable and shareable. */
    private val hitNoteIds = mutableSetOf<Int>()
    /** 当前组激活后新按下的按键；连续同音必须经过松开并重新按下。 */
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

    // —— A/B 循环练习（Phase 3 文档 6.1）——
    private var loopStartTick: Long = 0L
    private var loopEndTick: Long = totalTicks
    private var loopEnabled: Boolean = false

    /** 当前循环起点 tick；未设置循环时返回 0。 */
    val currentLoopStart: Long get() = if (loopEnabled) loopStartTick else 0L

    /** 当前循环终点 tick；未设置循环时返回 [totalTicks]。 */
    val currentLoopEnd: Long get() = if (loopEnabled) loopEndTick else totalTicks

    /** 是否已启用 A/B 循环。 */
    val isLoopActive: Boolean get() = loopEnabled

    /**
     * 设置 A/B 循环范围，并将游标重置到循环起点。
     * 起点/终点会被规范到 [0, totalTicks] 内，且终点必须大于起点。
     */
    fun setLoop(startTick: Long, endTick: Long) {
        val start = startTick.coerceIn(0L, totalTicks)
        val end = endTick.coerceIn(start, totalTicks)
        if (end > start) {
            loopStartTick = start
            loopEndTick = end
            loopEnabled = true
        } else {
            loopEnabled = false
        }
        reset()
    }

    /** 清除 A/B 循环。 */
    fun clearLoop() {
        loopEnabled = false
    }

    /**
     * 微观步进（文档 3.3 盲操微调）：游标前进到下一个组的起始 tick。
     * 等价于相对 seek —— 当前组标记为已过，落在下一组。
     */
    fun stepForward() {
        if (groupIndex >= groups.size) return
        val region = playbackRegion()
        val targetTick = groups.asSequence()
            .drop(groupIndex + 1)
            .map { it.first().startTick }
            .firstOrNull(region::containsNoteAt)
            ?: region.end
        seekTo(targetTick)
    }

    /**
     * 微观步进（文档 3.3 盲操微调）：游标后退到上一个组的起始 tick。
     * 落到的组重置为未命中，便于长辈重新练习。
     */
    fun stepBackward() {
        val region = playbackRegion()
        val targetTick = groups.asSequence()
            .take(groupIndex)
            .map { it.first().startTick }
            .filter(region::containsNoteAt)
            .lastOrNull()
            ?: region.start
        seekTo(targetTick)
    }

    /**
     * 定点跳转（文档 3.3 Click-to-Practice）。
     * 将游标跳到 >= [targetTick] 的第一个组，之前的组标记为已命中。
     */
    fun seekTo(targetTick: Long) {
        activeKeys.clear()
        freshKeys.clear()
        for (i in groupWrongCounts.indices) groupWrongCounts[i] = 0
        currentGroupDirty = false
        wrongPressCount = 0

        val constrainedTick = playbackRegion().constrain(targetTick)
        val targetIdx = findGroupIndexAtOrAfter(constrainedTick).let { if (it < 0) groups.size else it }
        hitNoteIds.clear()
        groups.take(targetIdx).forEach { group -> group.forEach { hitNoteIds.add(it.id) } }
        cleanHitCount = (0 until targetIdx).sumOf { i ->
            groups[i].count { isActiveTrack(it.track) }
        }
        groupIndex = targetIdx.coerceIn(0, groups.size)
        lastArrivedGroupIndex = groupIndex - 1
    }

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

    /** Session-local IDs of notes already completed or hosted. */
    fun hitNoteIds(): Set<Int> = hitNoteIds.toSet()

    /** 当前组“需要用户按”的音高（仅练习轨道）。用于 UI 高亮提示。 */
    fun pendingPitches(): Set<Int> =
        groups.getOrNull(groupIndex)
            ?.filter { isActiveTrack(it.track) }
            ?.mapTo(mutableSetOf()) { it.pitch }
            ?: emptySet()

    /** Active-hand pitches in the current timeline group. Never advertises a future group. */
    fun cuePitches(): Set<Int> = pendingPitches()

    /** Hosted-hand pitches in the current timeline group; empty in both-hands mode. */
    fun hostedCuePitches(): Set<Int> {
        if (trackSelection == TrackSelection.BOTH_HANDS) return emptySet()
        return groups.getOrNull(groupIndex)
            ?.asSequence()
            ?.filterNot { isActiveTrack(it.track) }
            ?.mapTo(mutableSetOf()) { it.pitch }
            ?: emptySet()
    }

    /** 该轨道是否由用户练习（true）/系统托管（false）。 */
    fun isActiveTrack(track: TrackType): Boolean = trackSelection.isActiveTrack(track)

    /** 用户按下某音高。返回本次新到达组应自动托管的音。 */
    fun onNoteOn(pitch: Int, allowHostedAdvance: Boolean = true): List<NoteEntity> {
        if (activeKeys.add(pitch)) {
            freshKeys.add(pitch)
        }
        recordPress(pitch)
        // 当前为纯托管组时，用户按键不推进，由上层按真实节奏调度 advanceHosted
        val group = groups.getOrNull(groupIndex) ?: return emptyList()
        if (group.none { isActiveTrack(it.track) }) return emptyList()
        return step(allowHostedAdvance)
    }

    fun onNoteOff(pitch: Int) {
        activeKeys.remove(pitch)
        // 松键不推进判定（时间轴停滞直到按对）
    }

    fun reset(): List<NoteEntity> {
        activeKeys.clear()
        freshKeys.clear()
        hitNoteIds.clear()
        for (i in groupWrongCounts.indices) groupWrongCounts[i] = 0
        currentGroupDirty = false
        cleanHitCount = 0
        wrongPressCount = 0
        groupIndex = if (loopEnabled) findGroupIndexAtOrAfter(loopStartTick) else 0
        lastArrivedGroupIndex = groupIndex - 1
        return emptyList()
    }

    /**
     * 显式推进一个纯托管组。若当前组不是纯托管（含练习音），返回空列表且不推进。
     * 由 [ScoreSession] 按真实节奏定时调用，避免连续托管段 burst。
     */
    fun advanceHosted(): List<NoteEntity> {
        val group = groups.getOrNull(groupIndex) ?: return emptyList()
        if (group.any { isActiveTrack(it.track) }) return emptyList()
        return step()
    }

    /** 到达当前组并返回应自动托管的音（测试用例入口；等同于驱动一次 [step]）。 */
    fun arrive(): List<NoteEntity> = step()

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
     *
     * 注意：处理完一个纯托管组后会返回，不把连续托管段一次 burst 推进完；
     * 上层通过 [advanceHosted] 按真实节奏继续调度。
     *
     * A/B 循环：当推进后的下一组起始 tick >= 循环终点时，自动回卷到循环起点，
     * 并将循环范围内的音符重置为未命中，以便长辈反复练习。
     */
    private fun step(allowHostedAdvance: Boolean = true): List<NoteEntity> {
        val hostedPlayed = mutableListOf<NoteEntity>()
        while (groupIndex < groups.size) {
            val group = groups[groupIndex]
            if (!allowHostedAdvance && group.none { isActiveTrack(it.track) }) break
            // 游标落到新组 → 托管该组的非练习轨道音
            if (groupIndex > lastArrivedGroupIndex) {
                val hosted = group.filter { !isActiveTrack(it.track) }
                hosted.forEach { hitNoteIds.add(it.id) }
                hostedPlayed.addAll(hosted)
                lastArrivedGroupIndex = groupIndex
            }
            val required = group
                .filter { isActiveTrack(it.track) }
                .mapTo(mutableSetOf()) { it.pitch }
            // 判定规则由策略决定（默认等待模式：按住 + 新按下；和弦可分次按齐，重复音必须重新击键）。
            // 多余或错误音不阻止推进，只影响最终准确率。
            val satisfied = policy.isSatisfied(required, activeKeys, freshKeys)
            if (satisfied) {
                // 准度：该组弹奏期间若未错按，则计入“干净命中”
                if (!currentGroupDirty) {
                    cleanHitCount += group.count { isActiveTrack(it.track) }
                }
                group.forEach { hitNoteIds.add(it.id) }
                val completedIndex = groupIndex
                groupIndex++
                val passEnd = loopEnabled && (groupIndex >= groups.size || currentTick >= loopEndTick)
                completedGroups += CompletedGroup(
                    tick = group.first().startTick,
                    group = completedIndex,
                    boundaryTick = if (passEnd) loopEndTick else currentTick,
                    completedPass = passEnd,
                    fullCompletion = !loopEnabled && groupIndex >= groups.size,
                )
                freshKeys.clear()
                currentGroupDirty = false
                // A/B 循环边界检测：下一组起点已越过循环终点，回卷
                if (passEnd) {
                    jumpToLoopStart()
                    break // explicit pass boundary, including single-group and score-final loops
                }
                // 纯托管组不连续 burst；返回给上层按节奏调度
                if (required.isEmpty()) break
            } else {
                break
            }
        }
        return hostedPlayed
    }

    /** 回卷到循环起点，并重置循环范围内音符的命中状态与干净命中计数。 */
    private fun jumpToLoopStart() {
        val targetIndex = findGroupIndexAtOrAfter(loopStartTick)
        if (targetIndex < 0) return // 无效循环，保持当前位置
        groupIndex = targetIndex
        lastArrivedGroupIndex = groupIndex - 1
        currentGroupDirty = false
        // 清按键池：循环回卷后要求长辈重新按下，避免因为一直按着导致无限自循环
        activeKeys.clear()
        freshKeys.clear()
        // 重置循环段内音符为未命中，让长辈重新练习
        groups.forEach { group ->
            group.forEach { note ->
                if (note.startTick in loopStartTick until loopEndTick) hitNoteIds.remove(note.id)
            }
        }
        recomputeCleanHitCount()
    }

    /** 找到第一个起始 tick >= [tick] 的组索引；没有则返回 -1。 */
    private fun findGroupIndexAtOrAfter(tick: Long): Int =
        groups.indexOfFirst { it.first().startTick >= tick }

    private fun playbackRegion(): PlaybackRegion = PlaybackRegion(
        scoreStart = 0L,
        scoreEnd = totalTicks,
        loopStart = loopStartTick.takeIf { loopEnabled },
        loopEnd = loopEndTick.takeIf { loopEnabled },
    )

    /** 根据当前各组命中与错按状态，重新计算干净命中数（循环回卷后防止分数虚高）。 */
    private fun recomputeCleanHitCount() {
        cleanHitCount = 0
        for ((i, group) in groups.withIndex()) {
            val active = group.filter { isActiveTrack(it.track) }
            if (active.isEmpty()) continue
            if (groupWrongCounts[i] > 0) continue
            if (active.all { it.id in hitNoteIds }) {
                cleanHitCount += active.size
            }
        }
    }
}
