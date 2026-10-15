package com.windscore.ui

import com.windscore.core.calculateMd5
import java.security.MessageDigest

/** A raster snapshot belongs to its exact offline renderer, not just the XML and HTML. */
internal fun sheetSnapshotCacheKey(
    rawXml: String,
    html: String,
    osmdJsBytes: ByteArray,
    renderer: String,
): String {
    val assetHash = MessageDigest.getInstance("SHA-256").digest(osmdJsBytes)
        .joinToString("") { "%02x".format(it) }
    return "${calculateMd5(rawXml)}_${calculateMd5(html)}_${assetHash}_$renderer"
}
