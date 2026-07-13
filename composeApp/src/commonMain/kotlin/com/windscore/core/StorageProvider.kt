package com.windscore.core

import com.windscore.core.model.Score

expect fun getAppStorageDir(): String
expect fun calculateMd5(text: String): String
expect fun loadRecentScores(onLoaded: (List<Score>) -> Unit)
expect fun saveRecentScores(scores: List<Score>)
