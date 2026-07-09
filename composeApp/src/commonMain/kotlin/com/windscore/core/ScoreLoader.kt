package com.windscore.core

import com.windscore.core.model.Score

/**
 * 加载并解析乐谱。各平台从各自的资源位置读取 .mxl 字节，
 * 解析逻辑（MusicXmlParser）在 jvmShared 中共享。
 */
expect fun loadScore(): Score
