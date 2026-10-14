package com.windscore.ui

import com.sun.net.httpserver.HttpServer
import com.windscore.core.loadScore
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.web.WebView
import javafx.stage.Stage
import java.io.File
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Native desktop browser integration. On Linux needs an X display (a dummy Xorg is sufficient). */
class KonekutoJavaFxTest {
    private fun <T> fx(block: () -> T): T {
        val task=FutureTask(block); Platform.runLater(task); return task.get(30,TimeUnit.SECONDS)
    }

    @Test
    fun javaFxWebViewRendersActualScoreAndRasterChunks() {
        assumeTrue("Opt-in native browser integration: set WINDSCORE_RENDER_VALIDATION=1",
            System.getenv("WINDSCORE_RENDER_VALIDATION")=="1")
        assumeTrue("JavaFX integration requires DISPLAY on Linux", !System.getProperty("os.name").startsWith("Linux") || System.getenv("DISPLAY")!=null)
        try { Platform.startup {} } catch (_: IllegalStateException) {}
        Platform.setImplicitExit(false)
        val html=OsmdHtml.buildHtml(4,"Loading","Loading failed").toByteArray()
        val xml=loadScore().rawXml!!.toByteArray()
        val js=javaClass.getResourceAsStream("/opensheetmusicdisplay.min.js")!!.readBytes()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/") { ex ->
            val data=when(ex.requestURI.path) { "/score.xml" -> xml; "/osmd.js" -> js; else -> html }
            ex.responseHeaders["Content-Type"]=when(ex.requestURI.path) { "/score.xml" -> "application/xml"; "/osmd.js" -> "application/javascript"; else -> "text/html" }
            ex.sendResponseHeaders(200,data.size.toLong()); ex.responseBody.use { it.write(data) }
        }
        server.start()
        val (stage,view)=fx {
            val view=WebView()
            val stage=Stage().apply { scene=Scene(StackPane(view),1200.0,800.0); show() }
            view.engine.load("http://127.0.0.1:${server.address.port}/")
            stage to view
        }
        try {
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120)
            var ready=false
            while(!ready && System.nanoTime()<deadline) {
                Thread.sleep(100)
                ready=fx { view.engine.executeScript("window.__renderTime>=0 && window.__snapshotChunkCount>0 && window.__snapshotChunksReady===window.__snapshotChunkCount") }==true
            }
            assertTrue(ready, fx { view.engine.executeScript("document.body.textContent").toString() })
            val json=fx { view.engine.executeScript("JSON.stringify({width:window.__snapshotChunkWidth,height:window.__snapshotTotalHeight,times:window.__cursorTimes,positions:window.__cursorPos,chunks:window.__snapshotChunks,renderMs:window.__renderTime,error:window.__snapshotError||null})").toString() }
            val data=Json.parseToJsonElement(json).jsonObject
            assertEquals(JsonNull,data["error"])
            val root=if(File("tools").isDirectory) File(".") else File("..")
            val output=File(root,"composeApp/build/validation/javafx").apply { mkdirs() }
            File(output,"result.json").writeText(json)
            data.getValue("chunks").jsonArray.forEachIndexed { i,e ->
                val png=Base64.getDecoder().decode(e.jsonPrimitive.content.substringAfter("base64,"))
                val file=File(output,"chunk-$i.png").apply { writeBytes(png) }
                val bitmap=ImageIO.read(file)
                assertEquals(data.getValue("width").jsonPrimitive.int,bitmap.width)
                assertTrue(bitmap.height in 1..4000)
                assertTrue((0 until bitmap.height step 5).any { y -> (0 until bitmap.width step 5).any { x -> bitmap.getRGB(x,y) and 0xffffff != 0xffffff } }, "nonblank raster chunk $i")
            }
            val chord=fx {
                view.engine.executeScript("window.tickAndSeek(2632); JSON.stringify(window.__osmd.cursor.iterator.CurrentVoiceEntries.reduce(function(a,e){return a.concat(e.Notes.filter(function(n){return n.Pitch;}).map(function(n){return n.Pitch.halfTone+12;}));},[]).sort(function(a,b){return a-b;}))").toString()
            }
            assertEquals("[30,42,58,66]",chord)
            assertEquals(164.5,fx { (view.engine.executeScript("window.__cursorTimes[window.__cursorIdx]") as Number).toDouble() })
            fx { view.engine.executeScript("window.__resetCursor(); window.tickAndSeek(0);") }
            assertEquals(0.0,fx { (view.engine.executeScript("window.__cursorTimes[window.__cursorIdx]") as Number).toDouble() })
        } finally { fx { stage.close(); view.engine.loadContent("") }; server.stop(0) }
    }
}
