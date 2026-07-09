package com.windscore.core

import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser

actual fun loadScore(): Score {
    val stream = Thread.currentThread().contextClassLoader
        ?.getResourceAsStream("konekuto-claris.mxl")
        ?: MusicXmlParser.javaClass.classLoader?.getResourceAsStream("konekuto-claris.mxl")
    val bytes = stream?.use { it.readBytes() }
        ?: error("乐谱资源未找到: konekuto-claris.mxl")
    return MusicXmlParser.parse(bytes)
}
