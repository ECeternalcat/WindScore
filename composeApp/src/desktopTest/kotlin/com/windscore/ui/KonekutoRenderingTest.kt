package com.windscore.ui

import com.windscore.core.loadScore
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.windscore.ui.theme.WindScoreTheme
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Assume.assumeTrue
import javax.imageio.ImageIO
import java.io.File
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

/** Exercises the production HTML/OSMD asset in an actual browser, including raster snapshots. */
class KonekutoRenderingTest {
    @get:Rule val compose = createComposeRule()
    @Test
    fun productionOsmdRendersTheBundledScoreAndExportsAlignedCursorGeometry() {
        assumeTrue("Opt-in browser integration: set WINDSCORE_RENDER_VALIDATION=1 and provide Node/Playwright/Chromium",
            System.getenv("WINDSCORE_RENDER_VALIDATION")=="1")
        val root = if (File("tools").isDirectory) File(".") else File("..")
        val output = File(root, "composeApp/build/validation/osmd").apply { mkdirs() }
        File(output, "index.html").writeText(OsmdHtml.buildHtml(4, "Loading", "Loading failed", importerProvenanceJson(loadScore().rawXml!!,loadScore())))
        File(output, "score.xml").writeText(loadScore().rawXml!!)
        File(output, "osmd.js").writeBytes(javaClass.getResourceAsStream("/opensheetmusicdisplay.min.js")!!.readBytes())
        File(output, "reference.json").writeBytes(javaClass.getResourceAsStream("/konekuto-reference.json")!!.readBytes())
        val process = ProcessBuilder("node", File(root, "tools/validate_osmd.cjs").absolutePath, output.absolutePath)
            .redirectErrorStream(true).redirectOutput(File(output, "browser.log")).start()
        try {
            assertTrue(process.waitFor(180, TimeUnit.SECONDS), "browser validation timed out; see ${File(output,"browser.log")}")
            assertEquals(0, process.exitValue(), File(output, "browser.log").readText())
        } finally { if (process.isAlive) process.destroyForcibly() }

        // Native bridge costs measured independently of browser collection and PNG decoding.
        val noteText=File(output,"1200-notes.json").readText()
        val benchmarkScore=loadScore()
        val rasterDimensions=Json.parseToJsonElement(File(output,"1200-geometry.json").readText()).jsonObject
        val rasterWidth=rasterDimensions.getValue("width").jsonPrimitive.int
        val rasterHeight=rasterDimensions.getValue("height").jsonPrimitive.int
        val nativeSamples=buildJsonArray {
            repeat(5) {
                val bean=java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
                val thread=Thread.currentThread().threadId()
                val before=bean?.getThreadAllocatedBytes(thread) ?: 0L
                val started=System.nanoTime()
                val decoded=decodeNoteGeometry(noteText,benchmarkScore,rasterWidth,rasterHeight)
                kotlin.test.assertNotNull(decoded)
                add(buildJsonObject {
                    put("decodeMs",(System.nanoTime()-started)/1e6)
                    put("allocatedBytes",(bean?.getThreadAllocatedBytes(thread) ?: 0L)-before)
                })
            }
        }
        val parserSamples=buildJsonArray {
            repeat(5) {
                for(enriched in listOf(false,true)) {
                    val started=System.nanoTime()
                    if(enriched) com.windscore.core.parser.MusicXmlParser.parseXmlWithProvenance(benchmarkScore.rawXml!!.toByteArray())
                    else com.windscore.core.parser.MusicXmlParser.parseXml(benchmarkScore.rawXml!!.toByteArray())
                    add(buildJsonObject {put("provenance",enriched);put("elapsedMs",(System.nanoTime()-started)/1e6)})
                }
            }
        }
        File(output,"native-note-benchmark.json").writeText(buildJsonObject {
            put("decodeSamples",nativeSamples);put("parserSamples",parserSamples)
            put("geometryJsonBytes",noteText.toByteArray().size)
            put("provenanceJsonBytes",importerProvenanceJson(benchmarkScore.rawXml!!,benchmarkScore)!!.toByteArray().size)
        }.toString())

        val geometry = Json.parseToJsonElement(File(output, "1200-geometry.json").readText()).jsonObject
        val ticks = geometry.getValue("ticks").jsonArray.map { it.jsonPrimitive.long }
        val positions = geometry.getValue("positions").jsonArray.map { it.jsonPrimitive.float }
        val steps = ticks.mapIndexed { i, t -> CursorStep(t, positions[i*3], positions[i*3+1], positions[i*3+2]) }
        val chunks = output.listFiles()!!.filter { it.name.startsWith("1200-chunk-") }
            .sortedBy { it.name.substringAfter("chunk-").substringBefore('.').toInt() }
            .map { ImageIO.read(it).toComposeImageBitmap() }
        val snapshot = SheetSnapshot(chunks, geometry.getValue("width").jsonPrimitive.int,
            chunks.first().height, geometry.getValue("height").jsonPrimitive.int, steps,
            decodeNoteGeometry(noteText,benchmarkScore,rasterWidth,rasterHeight))
        val tick = mutableStateOf(2632L)
        val view = mutableStateOf(com.windscore.ViewMode.SHEET_FULL)
        val hand = mutableStateOf(com.windscore.core.model.TrackSelection.BOTH_HANDS)
        val demonstrating = mutableStateOf(false)
        val score = loadScore()
        compose.setContent {
            WindScoreTheme { Row {
                NativeSheetView(snapshot, { tick.value }, {}, {}, {}, modifier=Modifier.size(600.dp,500.dp).testTag("sheet"),
                    targetCues=sheetTargetCuesAt(sheetTargetCueIndex(score,snapshot.noteGeometry,hand.value),
                        view.value,demonstrating.value,tick.value))
                PianoRoll(score, { tick.value }, modifier=Modifier.size(600.dp,500.dp).testTag("roll"), pulseIntensityProvider={0f})
            } }
        }
        // Verify actual raster pixels through held timing, backward seek and loop return.
        for (position in listOf(2632L,2640L,2616L,2632L)) {
            compose.runOnIdle { tick.value=position }
            compose.waitForIdle()
            val sheet = compose.onNodeWithTag("sheet").captureToImage().toPixelMap()
            val step = findCursorStep(position, steps)!!
            val cursorX = (step.x*sheet.width/snapshot.chunkWidth+1).toInt()
            val bluePixels = (0 until sheet.height).count { y ->
                val c=sheet[cursorX,y]; c.blue>c.red+0.1f && c.blue>c.green+0.04f
            }
            assertTrue(bluePixels>30, "visible native cursor at $position / x=$cursorX")
            val roll = compose.onNodeWithTag("roll").captureToImage().toPixelMap()
            writePixels(sheet, File(output,"compose-sheet-$position.png"))
            writePixels(roll, File(output,"compose-roll-$position.png"))
            val low=(score.notes.minOf { it.pitch }-2).coerceIn(21,108)
            val high=(score.notes.maxOf { it.pitch }+2).coerceIn(21,108)
            if (position==2632L) {
                for (pitch in listOf(30,42,58,66)) {
                    val x=((pitch-low+0.5f)*roll.width/(high-low+1)).toInt()
                    val c=roll[x,(roll.height*0.82f-10).toInt()]
                    val expected=if(pitch<50) intArrayOf(157,201,164) else intArrayOf(212,154,126)
                    assertTrue(abs(c.red*255-expected[0])<3 && abs(c.green*255-expected[1])<3 && abs(c.blue*255-expected[2])<3,
                        "rendered final chord pitch $pitch: $c")
                }
            }
        }
        // Real OSMD heads, raster and Compose overlays: cues add target guidance only in the bridge view.
        fun capture() = compose.onNodeWithTag("sheet").captureToImage().toPixelMap()
        fun pinkPixels(pixels: PixelMap) = (0 until pixels.height).sumOf { y ->
            (0 until pixels.width).count { x -> val c=pixels[x,y];c.red>c.green+0.15f && c.red>c.blue+0.08f }
        }
        val baseline=capture()
        assertEquals(0,pinkPixels(baseline))
        for(selection in com.windscore.core.model.TrackSelection.entries) {
            compose.runOnIdle { view.value=com.windscore.ViewMode.SHEET_KEYBOARD;hand.value=selection }
            compose.waitForIdle()
            val index=sheetTargetCueIndex(score,snapshot.noteGeometry,selection)
            assertEquals(if(selection==com.windscore.core.model.TrackSelection.BOTH_HANDS)4 else 2,index[2632L]?.size)
            val cued=capture()
            assertTrue(pinkPixels(cued)>12,"visible mapped target outlines for $selection")
            writePixels(cued,File(output,"compose-targets-$selection.png"))
        }
        for(mode in listOf(com.windscore.ViewMode.SHEET_FULL,com.windscore.ViewMode.ROLL)) {
            compose.runOnIdle { view.value=mode };compose.waitForIdle()
            val clean=capture()
            assertEquals(0,pinkPixels(clean))
            assertTrue((0 until clean.height).all { y -> (0 until clean.width).all { x ->clean[x,y].toArgb()==baseline[x,y].toArgb() } })
        }
        compose.runOnIdle { view.value=com.windscore.ViewMode.SHEET_KEYBOARD;demonstrating.value=true }
        compose.waitForIdle()
        assertEquals(0,pinkPixels(capture()),"playback is not a user target judgment")
        compose.runOnIdle { demonstrating.value=false;tick.value=2640L }
        compose.waitForIdle()
        assertEquals(0,pinkPixels(capture()),"the sustained final chord does not advertise another attack")

    }

    private fun writePixels(pixels: PixelMap, file: File) {
        val image=BufferedImage(pixels.width,pixels.height,BufferedImage.TYPE_INT_ARGB)
        for(y in 0 until pixels.height) for(x in 0 until pixels.width) image.setRGB(x,y,pixels[x,y].toArgb())
        ImageIO.write(image,"png",file)
    }
}
