package com.windscore.ui

/** Shared runtime collector. Renderer objects and SVG IDs never cross the bridge. */
internal object OsmdNoteGeometryJs {
    val source: String = """
(function(){
'use strict';
window.WindScoreNoteGeometry={
 install:function(osmd){
   ['render','load'].forEach(function(method){
     var original=osmd[method];
     osmd[method]=function(){
       window.__layoutGeneration=(window.__layoutGeneration||0)+1;
       window.__noteGeometry=null;
       window.__snapshotGeneration=null;
       window.__snapshotChunksReady=0;
       window.__snapshotChunkCount=0;
       return original.apply(this,arguments);
     };
   });
 },
 forSnapshot:function(expected){
   var g=window.__noteGeometry;
   return g&&g.generation===expected&&window.__snapshotGeneration===expected&&
     window.__layoutGeneration===expected?g:null;
 },
 collect:function(osmd,provenance,container,capturedPages){
   var started=performance.now(),generation=window.__layoutGeneration||0;
   var svgs=Array.from(container.querySelectorAll('svg'));
   var roots=new Map(),groups=new Map(),sources=new Set(),buckets=new Map(),writtenBuckets=new Map();
   var pages=svgs.map(function(svg,index){
     var rect=svg.getBoundingClientRect(),width=parseFloat(svg.getAttribute('width')),height=parseFloat(svg.getAttribute('height'));
     roots.set(svg,{index:index,rect:rect,scaleX:rect.width/width,scaleY:rect.height/height});
     return {width:Math.ceil(width),height:Math.ceil(height)};
   });
   var signature=function(n){return JSON.stringify([n.part,n.measure,n.staff,n.voice,n.tick,n.duration,n.pitch]);};
   var measures=new Map(osmd.Sheet.SourceMeasures.map(function(m,i){return [m,i];}));
   osmd.Sheet.SourceMeasures.forEach(function(m){
     m.VerticalSourceStaffEntryContainers.forEach(function(c){
       c.StaffEntries.forEach(function(s){if(s)s.VoiceEntries.forEach(function(v){
         v.Notes.forEach(function(n){if(n.Pitch)sources.add(n);});
       });});
     });
   });
   sources.forEach(function(n){
     var staff=n.ParentStaff,instrument=staff.ParentInstrument;
     var row={part:instrument.IdString,measure:measures.get(n.SourceMeasure),
       staff:instrument.Staves.indexOf(staff)+1,voice:String(n.ParentVoiceEntry.ParentVoice.VoiceId),
       tick:Math.round(n.getAbsoluteTimestamp().RealValue*4*provenance.divisions),
       duration:Math.round(n.Length.RealValue*4*provenance.divisions),pitch:n.Pitch.halfTone+12};
     var key=signature(row);if(!buckets.has(key))buckets.set(key,[]);buckets.get(key).push(n);
   });
   provenance.notes.forEach(function(n){
     var key=signature(n);writtenBuckets.set(key,(writtenBuckets.get(key)||0)+1);
   });
   var partIds=new Map();
   provenance.notes.forEach(function(n){
     var ordinal=n.key.split('/')[1];if(!partIds.has(n.part))partIds.set(n.part,new Set());partIds.get(n.part).add(ordinal);
   });
   var entries=provenance.notes.map(function(w){
     var result={writtenKey:w.key,performedId:w.performedId,status:'missing-source',page:null,rect:null};
     if(osmd.Version!=='2.2.1-release'||provenance.schema!==1){result.status='unsupported-renderer';return result;}
     if(!Number.isSafeInteger(w.tick)||!Number.isSafeInteger(w.duration)){result.status='unsupported-time';return result;}
     if(!/^[1-9][0-9]*$/.test(w.voice)||!Number.isSafeInteger(Number(w.voice))){result.status='unsupported-voice';return result;}
     var key=signature(w),candidates=buckets.get(key)||[];
     if(!w.part||partIds.get(w.part).size!==1){result.status='ambiguous-part';return result;}
     if(writtenBuckets.get(key)>1||candidates.length>1){result.status='ambiguous-source';return result;}
     if(candidates.length!==1)return result;
     var note=candidates[0],g=osmd.EngravingRules.GNote(note);
     if(!g||g.sourceNote!==note){result.status='missing-graphical';return result;}
     var group=g.getSVGGElement(),root=group&&roots.get(group.ownerSVGElement);
     if(!root){result.status='missing-page';return result;}
     result.page=root.index;
     if(root.index>=capturedPages){result.status='uncaptured-page';return result;}
     if(!groups.has(group)){
       groups.set(group,(g.getNoteheadSVGs()||[]).map(function(head){
         var r=head.getBoundingClientRect();
         return {x:(r.x-root.rect.x)/root.scaleX,y:(r.y-root.rect.y)/root.scaleY,
           width:r.width/root.scaleX,height:r.height/root.scaleY};
       }));
     }
     var pos=g.PositionAndShape.AbsolutePosition,unit=osmd.Drawer.calculatePixelDistance(1)*osmd.Zoom;
     var matches=groups.get(group).filter(function(r){
       return Math.hypot((r.x+r.width/2-pos.x*unit)*root.scaleX,(r.y+r.height/2-pos.y*unit)*root.scaleY)<1;
     });
     if(matches.length!==1){result.status='ambiguous-head';return result;}
     var rect=matches[0],page=pages[root.index];
     if(![rect.x,rect.y,rect.width,rect.height].every(Number.isFinite)||rect.width<=0||rect.height<=0||
       rect.x< -1||rect.y< -1||rect.x+rect.width>page.width+1||rect.y+rect.height>page.height+1){
       result.status='invalid-geometry';return result;
     }
     result.status='mapped';result.rect=rect;return result;
   });
   return {schema:1,sourceHash:provenance.sourceHash,scoreVersion:provenance.scoreVersion,divisions:provenance.divisions,
     renderer:osmd.Version,generation:generation,
     layout:JSON.stringify([osmd.Version,osmd.Zoom,container.clientWidth,container.clientHeight,
       svgs.map(function(s){return [s.getAttribute('width'),s.getAttribute('height'),s.getAttribute('viewBox'),getComputedStyle(s).transform];}),
       getComputedStyle(container).transform,generation]),
     pages:pages,notes:entries,elapsedMs:performance.now()-started};
 }
};
})();
    """.trimIndent()
}
