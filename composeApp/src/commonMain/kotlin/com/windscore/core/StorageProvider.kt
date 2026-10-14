package com.windscore.core

import com.windscore.core.model.Score
import com.windscore.core.model.ScoreRecord

expect fun getAppStorageDir(): String
expect fun calculateMd5(text: String): String
expect fun loadRecentScores(onLoaded: (List<Score>) -> Unit)
expect fun saveRecentScores(scores: List<Score>)
expect fun loadScoreXml(scoreId: String): String?
expect fun loadScoreSettings(scoreId: String): com.windscore.core.model.ScoreSettings
expect fun saveScoreSettings(scoreId: String, settings: com.windscore.core.model.ScoreSettings)
expect fun loadAppConfig(): com.windscore.core.model.AppConfig
expect fun saveAppConfig(config: com.windscore.core.model.AppConfig)

/** 读取持久化的应用语言代码（如 "en"/"zh"/"ja"）；未设置时返回 [default]。 */
expect fun loadAppLanguage(default: String = "zh"): String

/** 持久化应用语言代码。 */
expect fun saveAppLanguage(code: String)

/** 当前墙钟时间（epoch 毫秒）。 */
expect fun nowEpochMillis(): Long

/** 将 epoch 毫秒格式化为本地日期时间字符串（如 "2026/07/15 14:30"）。 */
expect fun formatDateTime(epochMillis: Long): String

/** 读取某乐谱的评分历史（最新在前）。 */
expect fun loadScoreHistory(scoreId: String): List<ScoreRecord>

/** 追加一条评分记录并落盘，返回更新后的历史列表（最新在前）。 */
expect fun appendScoreRecord(scoreId: String, record: ScoreRecord): List<ScoreRecord>

/** 保存一次练习 Attempt（原始 MIDI 事件与练习条件）。 */
expect fun savePracticeAttempt(attempt: com.windscore.core.practice.PracticeAttempt)

/** 读取某乐谱的全部 Attempt（最新在前）。 */
expect fun loadPracticeAttempts(scoreId: String): List<com.windscore.core.practice.PracticeAttempt>

/** 清理崩溃遗留的 Attempt 临时文件，返回删除数量。 */
expect fun cleanupAttemptTempFiles(): Int
