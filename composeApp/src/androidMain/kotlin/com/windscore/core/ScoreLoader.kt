package com.windscore.core

import com.windscore.AndroidApp
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser

actual fun loadScore(): Score {
    val bytes = AndroidApp.appContext.assets.open("konekuto-claris.mxl").use { it.readBytes() }
    return MusicXmlParser.parse(bytes)
}
