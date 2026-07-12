package com.windscore.core.parser

import com.windscore.core.model.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicXmlParserTest {

    @Test
    fun parseRealScore() {
        val loader = Thread.currentThread().contextClassLoader
            ?: MusicXmlParser.javaClass.classLoader
        val bytes = loader!!.getResourceAsStream("konekuto-claris.mxl")!!
            .use { it.readBytes() }
        val score = MusicXmlParser.parse(bytes)

        println(
            "解析结果: notes=${score.notes.size}, divisions=${score.divisions}, " +
                "tempo=${score.tempo}BPM, totalTicks=${score.totalTicks}"
        )
        score.notes.take(25).forEach { println("  $it") }

        assertTrue(score.notes.isNotEmpty(), "应解析出音符")
        assertEquals(4, score.divisions, "divisions 应为 4")
        assertEquals(175, score.tempo, "tempo 应为 175")
        assertTrue(score.notes.any { it.track == TrackType.RIGHT_HAND }, "应含右手音符")
        assertTrue(score.notes.any { it.track == TrackType.LEFT_HAND }, "应含左手音符")

        // 音符必须按 startTick 升序
        for (i in 1 until score.notes.size) {
            val a = score.notes[i - 1]
            val b = score.notes[i]
            assertTrue(a.startTick <= b.startTick, "音符应按时间排序: ${a.id}(${a.startTick}) > ${b.id}(${b.startTick})")
        }
        // 所有音高落在钢琴范围
        score.notes.forEach {
            assertTrue(it.pitch in 21..108, "音高越界: ${it.pitch}")
            assertTrue(it.durationTick > 0, "持续时间为 0: ${it.id}")
        }
    }

    /**
     * 坑 3.1 同音连线 (Tie)：相邻同音高 start/stop 音符必须合并为一个 NoteEntity，
     * duration 相加。否则长辈会被迫在延音中途重新击键，违背乐理。
     */
    @Test
    fun tieMergesAdjacentSamePitchNotes() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="3.1">
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>4</divisions></attributes>
                  <note>
                    <pitch><step>C</step><octave>4</octave></pitch>
                    <duration>4</duration>
                    <tie type="start"/>
                    <type>quarter</type>
                    <staff>1</staff>
                  </note>
                  <note>
                    <pitch><step>C</step><octave>4</octave></pitch>
                    <duration>4</duration>
                    <tie type="stop"/>
                    <type>quarter</type>
                    <staff>1</staff>
                  </note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent().toByteArray()
        val score = MusicXmlParser.parseXml(xml)

        assertEquals(1, score.notes.size, "两个 tied 音符应合并为 1 个")
        val note = score.notes.single()
        assertEquals(60, note.pitch, "C4 = MIDI 60")
        assertEquals(8L, note.durationTick, "duration 应相加: 4 + 4 = 8")
        assertEquals(0L, note.startTick)
    }

    @Test
    fun parseHandlesUncompressedXml() {
        // Phase 2 Step 3：parse() 应同时支持未压缩 .xml（非 ZIP）
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="3.1">
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>4</divisions></attributes>
                  <note>
                    <pitch><step>G</step><octave>4</octave></pitch>
                    <duration>4</duration>
                    <type>quarter</type>
                    <staff>1</staff>
                  </note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent().toByteArray()
        val score = MusicXmlParser.parse(xml) // 走非 ZIP 分支
        assertEquals(1, score.notes.size)
        assertEquals(67, score.notes[0].pitch, "G4 = MIDI 67")
    }

    /**
     * Phase 3：解析指法标记 <notations><technical><fingering>。
     */
    @Test
    fun fingeringIsExtractedFromTechnicalNotation() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="3.1">
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>4</divisions></attributes>
                  <note>
                    <pitch><step>C</step><octave>4</octave></pitch>
                    <duration>4</duration>
                    <type>quarter</type>
                    <staff>1</staff>
                    <notations>
                      <technical>
                        <fingering>3</fingering>
                      </technical>
                    </notations>
                  </note>
                  <note>
                    <pitch><step>E</step><octave>4</octave></pitch>
                    <duration>4</duration>
                    <type>quarter</type>
                    <staff>1</staff>
                  </note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent().toByteArray()
        val score = MusicXmlParser.parseXml(xml)
        assertEquals(2, score.notes.size)
        assertEquals("3", score.notes[0].fingering, "C4 应标 3 指")
        assertEquals(null, score.notes[1].fingering, "E4 无指法")
    }

    @Test
    fun parseHandlesDecimalBpm() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="3.1">
              <part id="P1">
                <measure number="1">
                  <direction>
                    <sound tempo="120.5"/>
                  </direction>
                  <attributes><divisions>4</divisions></attributes>
                  <note>
                    <pitch><step>G</step><octave>4</octave></pitch>
                    <duration>4</duration>
                    <type>quarter</type>
                    <staff>1</staff>
                  </note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent().toByteArray()
        val score = MusicXmlParser.parse(xml)
        assertEquals(121, score.tempo, "BPM 120.5 应被四舍五入为 121")
    }
}
