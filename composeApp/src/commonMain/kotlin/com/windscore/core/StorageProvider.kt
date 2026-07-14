package com.windscore.core

import com.windscore.core.model.Score

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
