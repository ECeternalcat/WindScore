package com.windscore.core.model

import kotlinx.serialization.Serializable

/**
 * 练习模式（文档 4.2 单/双手分离）。
 * - [BOTH]：双手都要弹，无托管。
 * - [RIGHT_ONLY]：只练右手，左手由系统自动托管发声。
 * - [LEFT_ONLY]：只练左手，右手由系统自动托管发声。
 */
@Serializable
enum class PracticeMode { BOTH, RIGHT_ONLY, LEFT_ONLY }
