package com.windscore.core

import com.windscore.core.engine.JudgmentEngine
import com.windscore.core.engine.ScoreTiming
import com.windscore.core.model.TrackType
import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import com.windscore.ui.sheetSnapshotCacheKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the Android compilation's shared parser/domain code on a host JVM; no device claims. */
class KonekutoAndroidHostTest {
    @Test
    fun androidOsmdAssetAndCacheFingerprintMatchThePinnedRelease() {
        val root=if(File("composeApp").isDirectory) File(".") else File("..")
        val manifest=Json.parseToJsonElement(File(root,"third_party/osmd/manifest.json").readText()).jsonObject
        val bytes=File(root,"composeApp/src/androidMain/assets/opensheetmusicdisplay.min.js").readBytes()
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(manifest.getValue("sha256").jsonPrimitive.content,hash)
        assertTrue(sheetSnapshotCacheKey("xml","html",bytes,"android-webview").contains(hash))
        assertTrue(File(root,"composeApp/src/androidMain/assets/opensheetmusicdisplay.min.js.LICENSE.txt").isFile)
    }
    @Test
    fun androidBundledAssetMatchesIndependentEventsTempoAndPractice() {
        val root=if(File("composeApp").isDirectory) File("composeApp") else File(".")
        val score=MusicXmlParser.parse(File(root,"src/androidMain/assets/konekuto-claris.mxl").readBytes())
        val reference=Json.parseToJsonElement(File(root,"src/desktopTest/resources/konekuto-reference.json").readText()).jsonObject
        val expected=reference.getValue("events").jsonArray.map { it.jsonObject }
        val tuples=expected.map { listOf(it.getValue("pitch").jsonPrimitive.long,
            it.getValue("startTick").jsonPrimitive.long, it.getValue("durationTick").jsonPrimitive.long,
            it.getValue("staff").jsonPrimitive.long) }.groupingBy { it }.eachCount()
        assertEquals(tuples,score.notes.map { listOf(it.pitch.toLong(),it.startTick,it.durationTick,
            if(it.track==TrackType.LEFT_HAND) 2L else 1L) }.groupingBy { it }.eachCount())
        assertEquals(4,score.divisions)
        assertEquals(2648L,score.totalTicks)
        assertEquals(listOf(0L to 175.0,2632L to 58.0,2648L to 175.0),score.tempoChanges.map { it.tick to it.bpm })
        assertEquals(4*60000.0/58,ScoreTiming(score).millisecondsBetween(2632,2648),1e-6)
        val engine=JudgmentEngine(score)
        for((tick,notes) in expected.groupBy { it.getValue("startTick").jsonPrimitive.long }.toSortedMap()) {
            assertEquals(tick,engine.currentTick)
            val pitches=notes.map { it.getValue("pitch").jsonPrimitive.int }.toSet()
            assertEquals(pitches,engine.pendingPitches())
            pitches.forEach(engine::onNoteOn)
            pitches.forEach(engine::onNoteOff)
        }
        assertTrue(engine.isFinished)
        assertEquals(1708,engine.cleanHitCount())
        assertEquals(score.notes.map { it.id }.toSet(),engine.hitNoteIds())
    }
}
