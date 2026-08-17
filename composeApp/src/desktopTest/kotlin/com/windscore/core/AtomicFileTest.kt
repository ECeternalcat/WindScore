package com.windscore.core

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AtomicFileTest {
    @Test
    fun atomicWriteReplacesContentAndRemovesTemporaryFile() {
        val dir = createTempDirectory("windscore-atomic-").toFile()
        try {
            val target = File(dir, "state.json")
            target.writeText("old")

            target.atomicWriteText("new")

            assertEquals("new", target.readText())
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }
}
