package com.windscore.core

import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser

actual fun loadScore(): Score {
    val bytes = requireAndroidContext().assets.open("konekuto-claris.mxl").use { it.readBytes() }
    return MusicXmlParser.parse(bytes)
}
