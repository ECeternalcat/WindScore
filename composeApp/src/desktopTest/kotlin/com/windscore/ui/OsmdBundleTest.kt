package com.windscore.ui

import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OsmdBundleTest {
    @Test
    fun offlineAssetsAndNoticesMatchThePinnedOfficialRelease() {
        val root=if(File("third_party").isDirectory) File(".") else File("..")
        val manifest=Json.parseToJsonElement(File(root,"third_party/osmd/manifest.json").readText()).jsonObject
        val bytes=javaClass.getResourceAsStream("/opensheetmusicdisplay.min.js")!!.readBytes()
        assertEquals(manifest.getValue("sha256").jsonPrimitive.content,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        assertEquals(manifest.getValue("sizeBytes").jsonPrimitive.int,bytes.size)
        assertContentEquals(bytes,File(root,"composeApp/src/androidMain/assets/opensheetmusicdisplay.min.js").readBytes())
        val notices=javaClass.getResourceAsStream("/opensheetmusicdisplay.min.js.LICENSE.txt")!!.readBytes()
        assertContentEquals(notices,File(root,"composeApp/src/androidMain/assets/opensheetmusicdisplay.min.js.LICENSE.txt").readBytes())
        assertTrue(notices.toString(Charsets.UTF_8).contains(File(root,"third_party/osmd/LICENSE").readText()))
        assertTrue(sheetSnapshotCacheKey("xml","html",bytes,"javafx-23").contains(manifest.getValue("sha256").jsonPrimitive.content))
    }
}
