package com.windscore.ui

import com.windscore.ViewMode
import com.windscore.core.model.*
import kotlin.test.*

class SheetTargetCuesTest {
    private val score=Score(id="source",version=4,divisions=4,tempo=120,totalTicks=24,notes=listOf(
        NoteEntity(8,60,TrackType.RIGHT_HAND,0,16),
        NoteEntity(9,64,TrackType.RIGHT_HAND,0,4),
        NoteEntity(10,48,TrackType.LEFT_HAND,0,4),
        NoteEntity(11,67,TrackType.RIGHT_HAND,16,4)))
    private fun segment(id: Int, tick: Long, ordinal: Int=id, status: String="mapped", duration: Long=4)=
        NoteGeometry("source/p0/m0/n$ordinal",id,status,0,
            if(status=="mapped")NoteHeadRect(ordinal.toDouble(),20.0,8.0,5.0) else null,tick,duration)
    private fun geometry(notes: List<NoteGeometry>)=NoteGeometrySnapshot(1,"source",4,4,"2.2.1-release",1,
        "layout",listOf(GeometryPage(100,200)),notes,0.0)

    @Test fun onlyRequiredAttackHeadsAreCuedAcrossStaffChangingTies() {
        // Event 8's later segment can move to the other staff; its original track remains authoritative.
        val g=geometry(listOf(segment(8,0),segment(8,4,88,duration=12),segment(9,0),segment(10,0),segment(11,16)))
        val right=sheetTargetCueIndex(score,g,TrackSelection.RIGHT_HAND)
        assertEquals(listOf(8.0,9.0),right[0]?.map { it.x })
        assertNull(right[4]) // No re-attack cue at the tied continuation.
        assertEquals(listOf(11.0),right[16]?.map { it.x })
        assertEquals(listOf(10.0),sheetTargetCueIndex(score,g,TrackSelection.LEFT_HAND)[0]?.map { it.x })
        assertEquals(3,sheetTargetCueIndex(score,g,TrackSelection.BOTH_HANDS)[0]?.size)
        for(mode in ViewMode.entries) {
            assertEquals(if(mode==ViewMode.SHEET_KEYBOARD)2 else 0,
                sheetTargetCuesAt(right,mode,false,0).size)
            assertTrue(sheetTargetCuesAt(right,mode,true,0).isEmpty())
        }
    }

    @Test fun unresolvedOldOrMismatchedMetadataNeverGuesses() {
        val g=geometry(listOf(segment(8,0,status="ambiguous-source"),segment(9,0,status="unsupported-voice")))
        assertTrue(sheetTargetCueIndex(score,g,TrackSelection.BOTH_HANDS).isEmpty())
        val old=geometry(listOf(segment(8,0).copy(writtenTick=null,writtenDuration=null)))
        assertTrue(sheetTargetCueIndex(score,old,TrackSelection.BOTH_HANDS).isEmpty())
        assertTrue(sheetTargetCueIndex(score,old.copy(sourceHash="other"),TrackSelection.BOTH_HANDS).isEmpty())
        val duplicate=geometry(listOf(segment(8,0),segment(8,0,88)))
        assertTrue(sheetTargetCueIndex(score,duplicate,TrackSelection.BOTH_HANDS).isEmpty())
        val index=sheetTargetCueIndex(score,geometry(listOf(segment(9,0))),TrackSelection.BOTH_HANDS)
        assertTrue(sheetTargetCuesAt(index,ViewMode.SHEET_KEYBOARD,false,4).isEmpty())
    }
}
