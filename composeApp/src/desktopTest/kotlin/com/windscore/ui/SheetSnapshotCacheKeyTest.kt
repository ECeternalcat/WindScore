package com.windscore.ui

import com.windscore.core.calculateMd5
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SheetSnapshotCacheKeyTest {
    private val xml="<score-partwise/>"
    private val html=OsmdHtml.buildHtml(4,"Loading","Failed")
    private val asset=byteArrayOf(1,2,3)
    private fun key(xml: String=this.xml, html: String=this.html, bytes: ByteArray=asset, renderer: String="javafx-23") =
        sheetSnapshotCacheKey(xml,html,bytes,renderer)

    @Test
    fun changingOnlyTheOfflineAssetCannotReuseThePreviousRaster() {
        assertNotEquals(key(),key(bytes=asset+4))
        assertEquals(key(),key(bytes=asset.copyOf()))
    }

    @Test
    fun xmlHtmlAndRendererChangesInvalidateGeometry() {
        assertNotEquals(key(),key(xml=xml+" "))
        assertNotEquals(key(),key(html=OsmdHtml.buildHtml(8,"Loading","Failed")))
        assertNotEquals(key(),key(renderer="android-webview"))
        assertNotEquals(key(),key(renderer="javafx-next"))
    }

    @Test
    fun legacySnapshotsAreBypassedWithoutChangingScoreIdentityOrDeletingFiles() {
        val root=Files.createTempDirectory("snapshot-upgrade").toFile()
        try {
            val legacy=root.resolve("${calculateMd5(xml)}_${calculateMd5(html)}").apply { mkdirs() }
            val meta=legacy.resolve("meta.json").apply { writeText("legacy snapshot sentinel") }
            assertFalse(root.resolve(key()).exists())
            assertEquals("legacy snapshot sentinel",meta.readText())
            assertTrue(key().startsWith(calculateMd5(xml)+"_"))
        } finally { root.deleteRecursively() }
    }
}
