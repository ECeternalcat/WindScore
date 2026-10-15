package com.windscore.core.parser

import com.windscore.core.model.ImportedScore
import com.windscore.core.model.ScoreProvenance
import com.windscore.core.model.WrittenNote
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.Score
import com.windscore.core.model.TempoChange
import com.windscore.core.model.TrackType
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/**
 * 尽力设置 XML 安全特性；某些平台（如 Android 基于 Expat 的实现）不支持 Xerces 专有 feature，
 * 此时静默忽略，保证解析仍可进行。
 */
private fun javax.xml.parsers.DocumentBuilderFactory.safeSetFeature(name: String, value: Boolean) {
    runCatching { setFeature(name, value) }
}

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isValidating = false
        safeSetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        safeSetFeature("http://xml.org/sax/features/external-general-entities", false)
        safeSetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    }

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

    internal const val MAX_INPUT_BYTES = 32 * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 256
    private const val MAX_ZIP_ENTRY_BYTES = 32 * 1024 * 1024
    private const val MAX_ZIP_TOTAL_BYTES = 64 * 1024 * 1024

    private val STEP_OFFSET = mapOf(
        'C' to 0, 'D' to 2, 'E' to 4, 'F' to 5,
        'G' to 7, 'A' to 9, 'B' to 11,
    )

    fun parse(mxlBytes: ByteArray): Score {
        require(mxlBytes.size <= MAX_INPUT_BYTES) { "乐谱文件过大（最大 ${MAX_INPUT_BYTES / 1024 / 1024} MB）" }
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

    fun parseXml(xmlBytes: ByteArray): Score = parseXmlInternal(xmlBytes, false).score

    fun parseXmlWithProvenance(xmlBytes: ByteArray): ImportedScore = parseXmlInternal(xmlBytes, true)

    private fun parseXmlInternal(xmlBytes: ByteArray, captureProvenance: Boolean): ImportedScore {
        require(xmlBytes.size <= MAX_ZIP_ENTRY_BYTES) { "MusicXML 内容过大" }
        require(hasOnlySafeMusicXmlDoctype(xmlBytes)) { "MusicXML 包含不安全的 DOCTYPE" }
        val factory = secureDocumentBuilderFactory()
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        doc.documentElement.normalize()

        val (divisions, legacyTimingCompatible) = canonicalDivisions(doc)
        data class RawTempo(val part: Int, val tick: Long, val bpm: Double)
        val tempos = mutableListOf<RawTempo>()
        val title = readTitle(doc)

        data class TieKey(val part: Int, val voice: String, val pitch: Int, val number: String?)
        data class RawNote(
            val written: WrittenNote?,
            val pitch: Int,
            val stopKey: TieKey,
            val startKey: TieKey,
            val staff: Int,
            val startTick: Long,
            val durationTick: Long,
            val tieStart: Boolean,
            val tieStop: Boolean,
            val fingering: String?,
        )

        val rawNotes = mutableListOf<RawNote>()
        var tickCursor = 0L
        var maxTick = 0L
        var globalMaxTick = 0L
        var lastStartTick = 0L

        val parts = doc.getElementsByTagName("part")
        for (pi in 0 until parts.length) {
            val part = parts.item(pi) as Element
            var sourceDivisions = 4
            // 每个 part 重置时间轴游标，但 globalMaxTick 跨 part 累计，确保多 Part 总谱总时长正确
            tickCursor = 0L
            maxTick = 0L
            lastStartTick = 0L

            val measures = part.getElementsByTagName("measure")
            for (mi in 0 until measures.length) {
                val measure = measures.item(mi) as Element
                var noteOrdinal = 0
                var node: Node? = measure.firstChild
                while (node != null) {
                    if (node is Element) {
                        when (node.nodeName) {
                            "note" -> {
                                val ordinal = noteOrdinal++
                                val isChord = node.getElementsByTagName("chord").length > 0
                                val duration = normalizedDuration(node, sourceDivisions, divisions)
                                val pitch = readPitch(node)
                                val staff = readStaff(node)
                                val (tieStart, tieStop) = readTies(node)
                                val fingering = readFingering(node)

                                val startTick = if (isChord) lastStartTick else tickCursor
                                if (!isChord) {
                                    lastStartTick = tickCursor
                                }

                                if (pitch != null) {
                                    rawNotes.add(
                                        RawNote(
                                            if (captureProvenance) WrittenNote(
                                                key = "p$pi/m$mi/n$ordinal", xmlId = node.getAttribute("id"),
                                                part = part.getAttribute("id"), measure = mi, ordinal = ordinal,
                                                voice = textOf(node, "voice")?.trim().orEmpty(), staff = staff,
                                                tick = startTick, duration = duration, pitch = pitch, performedId = -1,
                                                tieStart = tieStart, tieStop = tieStop,
                                                startNumber = readTieNumber(node, "start"), stopNumber = readTieNumber(node, "stop"),
                                            ) else null,
                                            pitch,
                                            TieKey(pi, textOf(node, "voice")?.trim().orEmpty(), pitch, readTieNumber(node, "stop")),
                                            TieKey(pi, textOf(node, "voice")?.trim().orEmpty(), pitch, readTieNumber(node, "start")),
                                            staff, startTick, duration, tieStart, tieStop, fingering,
                                        )
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
                                val d = normalizedDuration(node, sourceDivisions, divisions)
                                tickCursor -= d
                            }
                            "forward" -> {
                                val d = normalizedDuration(node, sourceDivisions, divisions)
                                tickCursor += d
                                if (tickCursor > maxTick) maxTick = tickCursor
                            }
                            "attributes" -> {
                                val divNode = node.getElementsByTagName("divisions")
                                if (divNode.length > 0) {
                                    sourceDivisions = (divNode.item(0) as Element).textContent.trim().toInt()
                                }
                            }
                            "direction", "sound" -> {
                                val sounds = if (node.nodeName == "sound") listOf(node) else {
                                    val children = node.getElementsByTagName("sound")
                                    (0 until children.length).map { children.item(it) as Element }
                                }
                                for (sound in sounds) {
                                    val text = sound.getAttribute("tempo")
                                    if (text.isEmpty()) continue
                                    val bpm = text.toDoubleOrNull()
                                    require(bpm != null && bpm.isFinite() && bpm > 0) {
                                        "MusicXML tempo must be positive; tempo 0 requires an explicit user choice"
                                    }
                                    val offset = playbackOffset(node, sound)
                                    val delta = offset?.textContent?.trim()?.let {
                                        BigDecimal(it).multiply(BigDecimal.valueOf((divisions / sourceDivisions).toLong())).longValueExact()
                                    } ?: 0L
                                    val position = Math.addExact(tickCursor, delta)
                                    require(position >= 0) { "MusicXML tempo offset precedes the score" }
                                    tempos.add(RawTempo(pi, position, bpm))
                                }
                            }
                        }
                    }
                    node = node.nextSibling
                }
            }
            globalMaxTick = maxOf(globalMaxTick, maxTick)
        }

        // —— Tie 合并 ——
        class MutableNote(
            val id: Int,
            val pitch: Int,
            val track: TrackType,
            val startTick: Long,
            var durationTick: Long,
            val fingering: String?,
        )

        val merged = mutableListOf<MutableNote>()
        // Staff is placement, not identity. Keep multiple open ties rather than overwriting unisons.
        val openTies = mutableMapOf<TieKey, MutableList<MutableNote>>()
        var nextId = 0
        val written = mutableListOf<WrittenNote>()

        for (raw in rawNotes) {
            var continuation: MutableNote? = null
            if (raw.tieStop) {
                val open = openTies[raw.stopKey]
                val candidates = open.orEmpty().filter {
                    Math.addExact(it.startTick, it.durationTick) == raw.startTick
                }
                require(candidates.size <= 1) {
                    "Ambiguous MusicXML tie at tick ${raw.startTick}, part ${raw.stopKey.part + 1}, " +
                        "voice '${raw.stopKey.voice}', pitch ${raw.pitch}; distinguish overlapping ties with tied numbers"
                }
                continuation = candidates.singleOrNull()
                if (continuation != null) {
                    continuation.durationTick = Math.addExact(continuation.durationTick, raw.durationTick)
                    open?.remove(continuation)
                    if (open.isNullOrEmpty()) openTies.remove(raw.stopKey)
                } else {
                    // Excerpts, voice changes or broken chains must not swallow an independent attack.
                    println("[MusicXML] Unmatched tie stop at tick ${raw.startTick}, voice '${raw.stopKey.voice}'")
                }
            }
            val entity = continuation ?: MutableNote(
                id = nextId++, pitch = raw.pitch,
                track = if (raw.staff == 2) TrackType.LEFT_HAND else TrackType.RIGHT_HAND,
                startTick = raw.startTick, durationTick = raw.durationTick, fingering = raw.fingering,
            ).also { merged.add(it) }
            raw.written?.let { written.add(it.copy(performedId = entity.id)) }
            if (raw.tieStart) openTies.getOrPut(raw.startKey) { mutableListOf() }.add(entity)
        }

        val finalNotes = merged.map { m ->
            NoteEntity(
                id = m.id,
                pitch = m.pitch,
                track = m.track,
                startTick = m.startTick,
                durationTick = m.durationTick,
                fingering = m.fingering,
            )
        }.sortedWith(compareBy({ it.startTick }, { it.pitch }))

        val tempoChanges = tempos.groupBy { it.tick }.toSortedMap().map { (tick, entries) ->
            // Repeated part declarations agree; contradictory simultaneous global tempos cannot be guessed.
            val effective = entries.groupBy { it.part }.values.map { it.last().bpm }.distinct()
            require(effective.size == 1) { "Conflicting MusicXML tempos in different parts at tick $tick" }
            TempoChange(tick, effective.single())
        }
        val initialTempo = if (tempoChanges.isEmpty()) 0 else
            (tempoChanges.firstOrNull { it.tick == 0L }?.bpm ?: 120.0).roundToInt()

        val xmlText = xmlBytes.toString(Charsets.UTF_8)
        val xmlHash = com.windscore.core.calculateMd5(xmlText)
        val score = Score(
            id = xmlHash,
            notes = finalNotes,
            version = Score.CURRENT_VERSION,
            legacyTimingCompatible = legacyTimingCompatible,
            divisions = divisions,
            tempo = initialTempo,
            tempoChanges = tempoChanges,
            totalTicks = maxOf(globalMaxTick, maxTick),
            rawXml = xmlText,
            supportsSheetView = true,
            title = title,
        )
        return ImportedScore(score, ScoreProvenance(xmlHash, score.version, divisions,
            written.map { it.copy(key = "$xmlHash/${it.key}") }))
    }

    /** Exact common tick resolution; retain the original resolution for uniform inputs. */
    private fun canonicalDivisions(doc: org.w3c.dom.Document): Pair<Int, Boolean> {
        var resolution = 1L
        val resolutions = mutableSetOf<Int>()
        fun include(value: Int) {
            require(value > 0) { "MusicXML divisions must be positive" }
            var a = resolution
            var b = value.toLong()
            while (b != 0L) { val rest = a % b; a = b; b = rest }
            resolution = Math.multiplyExact(resolution / a, value.toLong())
            require(resolution <= Int.MAX_VALUE) { "MusicXML divisions exceed supported tick resolution" }
            resolutions += value
        }
        val parts = doc.getElementsByTagName("part")
        for (pi in 0 until parts.length) {
            var current = 4
            val measures = (parts.item(pi) as Element).getElementsByTagName("measure")
            for (mi in 0 until measures.length) {
                var node = measures.item(mi).firstChild
                while (node != null) {
                    if (node is Element) when (node.nodeName) {
                        "attributes" -> {
                            textOf(node, "divisions")?.let { current = it.trim().toInt(); include(current) }
                        }
                        "note", "backup", "forward" -> include(current)
                        "direction", "sound" -> {
                            val sounds = if (node.nodeName == "sound") listOf(node) else {
                                val children = node.getElementsByTagName("sound")
                                (0 until children.length).map { children.item(it) as Element }
                            }
                            for (sound in sounds.filter { it.getAttribute("tempo").isNotEmpty() }) {
                                val offset = playbackOffset(node, sound) ?: continue
                                val value = BigDecimal(offset.textContent.trim()).stripTrailingZeros()
                                if (value.scale() <= 0) continue
                                require(value.scale() <= 30) { "MusicXML tempo offset exceeds supported tick resolution" }
                                val power = BigInteger.TEN.pow(value.scale())
                                val denominator = power / value.unscaledValue().abs().gcd(power)
                                val required = denominator * BigInteger.valueOf(current.toLong())
                                require(required <= BigInteger.valueOf(Int.MAX_VALUE.toLong())) {
                                    "MusicXML tempo offset exceeds supported tick resolution"
                                }
                                include(current)
                                include(required.toInt())
                            }
                        }
                    }
                    node = node.nextSibling
                }
            }
        }
        return (if (resolutions.isEmpty()) 4 else resolution.toInt()) to (resolutions.size <= 1)
    }

    private fun normalizedDuration(el: Element, sourceDivisions: Int, divisions: Int): Long {
        val duration = readDuration(el)
        require(duration >= 0) { "MusicXML duration must not be negative" }
        return Math.multiplyExact(duration, (divisions / sourceDivisions).toLong())
    }

    private fun playbackOffset(direction: Element, sound: Element): Element? {
        val own = sound.getElementsByTagName("offset").item(0) as? Element
        if (own != null) return own
        return (direction.getElementsByTagName("offset").item(0) as? Element)
            ?.takeIf { it.getAttribute("sound") == "yes" }
    }

    private fun readTieNumber(note: Element, type: String): String? {
        val tied = note.getElementsByTagName("tied")
        for (i in 0 until tied.length) {
            val mark = tied.item(i) as Element
            if (mark.getAttribute("type") == type) {
                return mark.getAttribute("number").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun readTitle(doc: org.w3c.dom.Document): String {
        val root = doc.documentElement
        // 优先 <work><work-title>
        textOf(root, "work-title")?.let { return it.trim() }
        // 其次 <identification><movement-title>
        textOf(root, "movement-title")?.let { return it.trim() }
        // 兼容部分导出器放在 <credit><credit-words>
        val credits = root.getElementsByTagName("credit-words")
        if (credits.length > 0) {
            return (credits.item(0) as Element).textContent.trim()
        }
        return ""
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

    /**
     * 读取指法标记。MusicXML 标准位置为 <notations><technical><fingering>，
     * 部分导出器也可能直接放在 <notations><fingering>，这里同时兼容。
     */
    private fun readFingering(note: Element): String? {
        val notations = note.getElementsByTagName("notations")
        for (ni in 0 until notations.length) {
            val n = notations.item(ni) as? Element ?: continue
            // 优先 technical/fingering
            textOf(n, "fingering")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val technical = n.getElementsByTagName("technical")
            for (ti in 0 until technical.length) {
                val t = technical.item(ti) as? Element ?: continue
                textOf(t, "fingering")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

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
            require(hasOnlySafeMusicXmlDoctype(container, allowContainerWithoutScoreRoot = true)) {
                "MXL container.xml 包含不安全的 DOCTYPE"
            }
            val factory = secureDocumentBuilderFactory()
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
        var entryCount = 0
        var totalBytes = 0
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entryCount++
                    require(entryCount <= MAX_ZIP_ENTRIES) { "MXL 条目过多" }
                    val bytes = zis.readBytesBounded(MAX_ZIP_ENTRY_BYTES)
                    totalBytes += bytes.size
                    require(totalBytes <= MAX_ZIP_TOTAL_BYTES) { "MXL 解压后内容过大" }
                    result[entry.name] = bytes
                }
                entry = zis.nextEntry
            }
        }
        return result
    }

    private fun hasOnlySafeMusicXmlDoctype(
        bytes: ByteArray,
        allowContainerWithoutScoreRoot: Boolean = false,
    ): Boolean {
        val header = bytes.copyOfRange(0, minOf(bytes.size, XML_HEADER_SCAN_BYTES)).toString(Charsets.UTF_8)
        val start = header.indexOf("<!DOCTYPE", ignoreCase = true)
        if (start < 0) return true
        val end = header.indexOf('>', startIndex = start)
        if (end < 0) return false
        val declaration = header.substring(start, end + 1)
        if ('[' in declaration || declaration.contains("<!ENTITY", ignoreCase = true)) return false
        if (allowContainerWithoutScoreRoot) return false
        return SAFE_MUSIC_XML_DOCTYPE.matches(declaration)
    }

    private fun ZipInputStream.readBytesBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(8192, limit))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "MXL 单个条目解压后过大" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private const val XML_HEADER_SCAN_BYTES = 16 * 1024
    private val SAFE_MUSIC_XML_DOCTYPE = Regex(
        pattern = """<!DOCTYPE\s+(score-partwise|score-timewise)\s+(PUBLIC\s+[^>]+|SYSTEM\s+[^>]+)>""",
        option = RegexOption.IGNORE_CASE,
    )
}
