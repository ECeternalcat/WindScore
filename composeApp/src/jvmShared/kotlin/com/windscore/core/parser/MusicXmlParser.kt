package com.windscore.core.parser

import com.windscore.core.model.NoteEntity
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * MusicXML (.mxl) 降维解析器。
 *
 * 把庞大的 MusicXML 树“降维”成一维的 [NoteEntity] 列表，丢弃五线谱排版信息：
 * - staff 1 → 右手, staff 2 → 左手
 * - 同音连线 (Tie) 合并：相邻同 pitch 的 start/stop 音符合并 durationTick
 * - 和弦 (chord) 音符不推进时间轴，共享前一个音符的起始
 * - 休止符不产生 NoteEntity，但消耗时间
 * - backup/forward 正确推进/回退时间轴游标
 */
object MusicXmlParser {

    private val STEP_OFFSET = mapOf(
        'C' to 0, 'D' to 2, 'E' to 4, 'F' to 5,
        'G' to 7, 'A' to 9, 'B' to 11,
    )

    fun parse(mxlBytes: ByteArray): Score {
        // 同时支持压缩 .mxl（ZIP）与未压缩 .xml
        return if (isZip(mxlBytes)) {
            val files = unzip(mxlBytes)
            val rootName = resolveRootFile(files)
            val xml = files[rootName] ?: error("MusicXML 根文件未找到: $rootName")
            parseXml(xml)
        } else {
            parseXml(mxlBytes)
        }
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 3 &&
            (bytes[0].toInt() and 0xFF) == 0x50 && // 'P'
            (bytes[1].toInt() and 0xFF) == 0x4B && // 'K'
            (bytes[2].toInt() and 0xFF) == 0x03 &&
            (bytes[3].toInt() and 0xFF) == 0x04

    fun parseXml(xmlBytes: ByteArray): Score {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            // 抑制 MusicXML DTD 远程下载
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        doc.documentElement.normalize()

        var divisions = 4
        var tempo = 0

        data class RawNote(
            val pitch: Int,
            val staff: Int,
            val startTick: Long,
            val durationTick: Long,
            val tieStart: Boolean,
            val tieStop: Boolean,
        )

        val rawNotes = mutableListOf<RawNote>()
        var tickCursor = 0L
        var maxTick = 0L
        var lastStartTick = 0L

        val parts = doc.getElementsByTagName("part")
        for (pi in 0 until parts.length) {
            val part = parts.item(pi) as Element
            // 每个 part 重置时间轴（本谱只有一个 piano part）
            tickCursor = 0L
            maxTick = 0L
            lastStartTick = 0L

            val measures = part.getElementsByTagName("measure")
            for (mi in 0 until measures.length) {
                val measure = measures.item(mi) as Element
                var node: Node? = measure.firstChild
                while (node != null) {
                    if (node is Element) {
                        when (node.nodeName) {
                            "note" -> {
                                val isChord = node.getElementsByTagName("chord").length > 0
                                val duration = readDuration(node)
                                val pitch = readPitch(node)
                                val staff = readStaff(node)
                                val (tieStart, tieStop) = readTies(node)

                                val startTick = if (isChord) lastStartTick else tickCursor
                                if (!isChord) {
                                    lastStartTick = tickCursor
                                }

                                if (pitch != null) {
                                    rawNotes.add(
                                        RawNote(pitch, staff, startTick, duration, tieStart, tieStop)
                                    )
                                }
                                // 和弦音符与休止符都不改变“主”时间推进规则：
                                // 非 chord 才推进；rest 也是非 chord，会正常推进
                                if (!isChord) {
                                    tickCursor += duration
                                    if (tickCursor > maxTick) maxTick = tickCursor
                                }
                            }
                            "backup" -> {
                                val d = readDuration(node)
                                tickCursor -= d
                            }
                            "forward" -> {
                                val d = readDuration(node)
                                tickCursor += d
                                if (tickCursor > maxTick) maxTick = tickCursor
                            }
                            "attributes" -> {
                                val divNode = node.getElementsByTagName("divisions")
                                if (divNode.length > 0) {
                                    divisions = (divNode.item(0) as Element).textContent.trim().toInt()
                                }
                            }
                            "direction" -> {
                                val sound = node.getElementsByTagName("sound")
                                if (sound.length > 0) {
                                    val t = (sound.item(0) as Element).getAttribute("tempo")
                                    if (t.isNotEmpty()) tempo = t.toInt()
                                }
                            }
                        }
                    }
                    node = node.nextSibling
                }
            }
        }

        // —— Tie 合并 ——
        val merged = mutableListOf<NoteEntity>()
        // openTies: (pitch, staff) -> 当前正在延音的 NoteEntity
        val openTies = mutableMapOf<Pair<Int, Int>, NoteEntity>()
        var nextId = 0

        for (raw in rawNotes) {
            val key = raw.pitch to raw.staff
            var consumed = false
            if (raw.tieStop) {
                val open = openTies[key]
                if (open != null) {
                    open.durationTick += raw.durationTick
                    consumed = true
                    if (!raw.tieStart) {
                        openTies.remove(key)
                    }
                    // 若同时 tieStart，则同一条 open 继续延音到下一个 stop
                }
            }
            if (!consumed) {
                val track = if (raw.staff == 2) TrackType.LEFT_HAND else TrackType.RIGHT_HAND
                val entity = NoteEntity(
                    id = nextId++,
                    pitch = raw.pitch,
                    track = track,
                    startTick = raw.startTick,
                    durationTick = raw.durationTick,
                )
                merged.add(entity)
                if (raw.tieStart) {
                    openTies[key] = entity
                }
            }
        }

        // 按 startTick 升序、再按 pitch 排序，保证渲染与判定顺序稳定
        merged.sortWith(compareBy({ it.startTick }, { it.pitch }))

        return Score(
            notes = merged,
            divisions = divisions,
            tempo = tempo,
            totalTicks = maxTick,
            rawXml = xmlBytes.toString(Charsets.UTF_8),
        )
    }

    private fun readPitch(note: Element): Int? {
        if (note.getElementsByTagName("rest").length > 0) return null
        val pitchEl = note.getElementsByTagName("pitch").item(0) as? Element ?: return null
        val stepTxt = textOf(pitchEl, "step") ?: return null
        val step = stepTxt.trim().first()
        val alter = textOf(pitchEl, "alter")?.trim()?.toIntOrNull() ?: 0
        val octave = textOf(pitchEl, "octave")?.trim()?.toIntOrNull() ?: return null
        val base = (octave + 1) * 12 + (STEP_OFFSET[step] ?: return null)
        return base + alter
    }

    private fun readDuration(el: Element): Long =
        textOf(el, "duration")?.trim()?.toLongOrNull() ?: 0L

    private fun readStaff(note: Element): Int =
        textOf(note, "staff")?.trim()?.toIntOrNull() ?: 1

    private fun readTies(note: Element): Pair<Boolean, Boolean> {
        val ties = note.getElementsByTagName("tie")
        var start = false
        var stop = false
        for (i in 0 until ties.length) {
            val t = ties.item(i) as Element
            when (t.getAttribute("type")) {
                "start" -> start = true
                "stop" -> stop = true
            }
        }
        return start to stop
    }

    private fun textOf(parent: Element, tag: String): String? {
        val list = parent.getElementsByTagName(tag)
        return if (list.length > 0) (list.item(0) as Element).textContent else null
    }

    private fun resolveRootFile(files: Map<String, ByteArray>): String {
        val container = files["META-INF/container.xml"]
        if (container != null) {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isValidating = false
            }
            runCatching {
                val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(container))
                val roots = doc.getElementsByTagName("rootfile")
                if (roots.length > 0) {
                    val path = (roots.item(0) as Element).getAttribute("full-path")
                    if (path.isNotEmpty()) return path
                }
            }
        }
        return files.keys.firstOrNull { it.endsWith(".xml", ignoreCase = true) } ?: "score.xml"
    }

    private fun unzip(data: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    result[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return result
    }
}
