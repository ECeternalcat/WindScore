/* Research-only structural join; never imported by application production code. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert/strict');
let playwright;
try {playwright=require('playwright');} catch(_) {playwright=require(process.env.WINDSCORE_PLAYWRIGHT||'/opt/codex/runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');}
const output=path.resolve(process.argv[2]),fixtures=JSON.parse(fs.readFileSync(path.join(output,'fixtures.json')));
const server=http.createServer((req,res)=> {
  if(req.url==='/geometry.js'){res.setHeader('Content-Type','application/javascript');res.end(fs.readFileSync(path.join(output,'geometry.js')));}
  else if(req.url==='/osmd.js') {res.setHeader('Content-Type','application/javascript');res.end(fs.readFileSync(path.join(output,'osmd.js')));}
  else {res.setHeader('Content-Type','text/html');res.end('<!doctype html><style>body{margin:0}#score{width:1000px}</style><script src="/osmd.js"></script><script src="/geometry.js"></script><div id="score"></div>');}
});
(async()=> {
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const origin=`http://127.0.0.1:${server.address().port}`;
  const browser=await playwright.chromium.launch({executablePath:process.env.WINDSCORE_CHROMIUM||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--enable-precise-memory-info']});
  const results=[];
  try {
    for(const fixture of fixtures) {
      console.log('begin '+fixture.name);
      const page=await browser.newPage({viewport:{width:1000,height:800}});
      await page.route('**/*',r=>new URL(r.request().url()).origin===origin?r.continue():r.abort());
      await page.goto(origin);
      console.log('loaded '+fixture.name);
      await page.evaluate(async f=> {
        window.osmd=new opensheetmusicdisplay.OpenSheetMusicDisplay('score',{autoResize:false,drawTitle:false,drawPartNames:false});
        window.WindScoreNoteGeometry.install(window.osmd);
        const doc=new DOMParser().parseFromString(f.xml.replace(/<!DOCTYPE[^>]*>/i,''),'application/xml');
        await window.osmd.load(doc); // short XML strings without a declaration are otherwise treated as URLs
        window.osmd.render();
        window.fixture=f;
        window.collect=function() {
          const osmd=window.osmd,f=window.fixture;
          const started=performance.now();
          // Structural source traversal, not cursor enumeration or renderer allocation IDs.
          const source=new Set();
          const measureIndex=new Map(osmd.Sheet.SourceMeasures.map((m,i)=>[m,i]));
          const svgs=[...document.querySelectorAll('#score svg')];
          const svgIndices=new Map(svgs.map((s,i)=>[s,i]));
          const svgRects=new Map(svgs.map(s=>[s,s.getBoundingClientRect()]));
          const groupGeometry=new Map();
          for(const m of osmd.Sheet.SourceMeasures) for(const c of m.VerticalSourceStaffEntryContainers)
            for(const staff of c.StaffEntries) if(staff) for(const voice of staff.VoiceEntries)
              for(const note of voice.Notes) if(note.Pitch)source.add(note);
          const fraction=v=>v?.RealValue??v?.realValue;
          const signature=n=>JSON.stringify([n.part,n.measure,n.staff,n.voice,n.tick,n.duration,n.pitch]);
          const groups=new Map();let geometryMs=0;
          if(!window.firstSourceObjects)window.firstSourceObjects=new Set(source);
          const retainedSources=[...source].filter(n=>window.firstSourceObjects.has(n)).length;
          const entries=[];
          for(const note of source) {
            const staff=note.ParentStaff,instrument=staff.ParentInstrument;
            const row={part:instrument.IdString,measure:measureIndex.get(note.SourceMeasure),
              staff:instrument.Staves.indexOf(staff)+1,voice:String(note.ParentVoiceEntry.ParentVoice.VoiceId),
              tick:Math.round(fraction(note.getAbsoluteTimestamp())*4*f.divisions),
              duration:Math.round(fraction(note.Length)*4*f.divisions),pitch:note.Pitch.halfTone+12};
            const geometryStart=performance.now();
            const g=osmd.EngravingRules.GNote(note);
            const svgGroup=g?.getSVGGElement?.();
            const page=g?.ParentMusicPage;
            const svg=svgGroup?.ownerSVGElement;
            const svgIndex=svgIndices.get(svg)??-1;
            const box=g?.PositionAndShape;
            const svgRect=svgRects.get(svg);
            if(!groupGeometry.has(svgGroup))groupGeometry.set(svgGroup,{
              rect:svgGroup?.getBoundingClientRect(),
              headRects:(g?.getNoteheadSVGs?.()||[]).map(h=> {const r=h.getBoundingClientRect();return {x:r.x-svgRect.x,y:r.y-svgRect.y,width:r.width,height:r.height};})
            });
            const {rect,headRects}=groupGeometry.get(svgGroup);
            const unitPixels=osmd.Drawer.calculatePixelDistance(1)*osmd.Zoom;
            const scaleX=svgRect.width/parseFloat(svg.getAttribute('width'));
            const scaleY=svgRect.height/parseFloat(svg.getAttribute('height'));
            const anchor={x:box.AbsolutePosition.x*unitPixels*scaleX,y:box.AbsolutePosition.y*unitPixels*scaleY};
            const headCandidates=headRects.filter(r=>Math.hypot(r.x+r.width/2-anchor.x,r.y+r.height/2-anchor.y)<1);
            const entry={...row,rendererId:note.NoteToGraphicalNoteObjectId,svgId:g?.getSVGId?.(),
              mapped:g?.sourceNote===note,page:page?.PageNumber,svgIndex,
              box:box?{x:box.AbsolutePosition.x,y:box.AbsolutePosition.y,width:box.Size.width,height:box.Size.height}:null,
              rect:rect?{x:rect.x-svgRect.x,y:rect.y-svgRect.y,width:rect.width,height:rect.height}:null,
              headRects,anchor,headCandidates,headRect:headCandidates.length===1?headCandidates[0]:null,
              tie:note.NoteTie?.Notes.map(n=>({voice:String(n.ParentVoiceEntry.ParentVoice.VoiceId),
                tick:Math.round(fraction(n.getAbsoluteTimestamp())*4*f.divisions),staff:n.ParentStaff.ParentInstrument.Staves.indexOf(n.ParentStaff)+1})),
              internalTieNumber:note.NoteTie?.TieNumber,
              xmlIdsRetained:Object.values(note).filter(v=>typeof v==='string' && v.startsWith('xml-note-'))};
            geometryMs+=performance.now()-geometryStart;
            entries.push(entry);
            const key=signature(row);if(!groups.has(key))groups.set(key,[]);groups.get(key).push(entry);
          }
          const xmlGroups=new Map();for(const n of f.notes) {const k=signature(n);if(!xmlGroups.has(k))xmlGroups.set(k,[]);xmlGroups.get(k).push(n);}
          const mapped=[],ambiguous=[],unsupported=[],missing=[];
          for(const written of f.notes) {
            // OSMD numeric voice IDs are not a lossless representation of arbitrary MusicXML voice strings.
            if(!/^[1-9][0-9]*$/.test(written.voice)) {unsupported.push(written.key);continue;}
            const candidates=groups.get(signature(written))||[];
            if(candidates.length>1 || xmlGroups.get(signature(written)).length>1) {ambiguous.push(written.key);continue;}
            if(candidates.length!==1) {missing.push({written,candidates:candidates.length});continue;}
            mapped.push({writtenKey:written.key,performedId:written.performedId,...candidates[0]});
          }
          return {elapsedMs:performance.now()-started,geometryMs,retainedSources,sourceCount:source.size,mapped,ambiguous,unsupported,missing,
            svgCount:svgs.length,version:osmd.Version,
            allSource:entries,svgSizes:svgs.map(s=>({width:svgRects.get(s).width,height:svgRects.get(s).height}))};
        };
      },fixture);
      console.log('parsed '+fixture.name);
      const modes=[{name:'initial',zoom:1,pageFormat:'Endless',width:1000},
        {name:'zoom',zoom:0.75,pageFormat:'Endless',width:1000},
        {name:'relayout',zoom:1,pageFormat:'Endless',width:620},
        {name:'css-scale',zoom:1,pageFormat:'Endless',width:1000,cssScale:0.8},
        {name:'pages',zoom:0.75,pageFormat:'A4_P',width:1000},
        {name:'return',zoom:1,pageFormat:'Endless',width:1000},
        {name:'reload',zoom:1,pageFormat:'Endless',width:1000}];
      const layouts=[];
      for(const mode of modes) {
        const result=await page.evaluate(async m=> {
          if(m.name==='reload')await window.osmd.load(new DOMParser().parseFromString(window.fixture.xml.replace(/<!DOCTYPE[^>]*>/i,''),'application/xml'));
          document.getElementById('score').style.width=m.width+'px';
          document.getElementById('score').style.transform=`scale(${m.cssScale||1})`;
          document.getElementById('score').style.transformOrigin='top left';
          window.osmd.setOptions({pageFormat:m.pageFormat});window.osmd.Zoom=m.zoom;
          window.osmd.updateGraphic();window.osmd.render();
          if(window.__noteGeometry!==null)throw Error('layout must invalidate previous mapping');
          const diagnostic=window.collect();
          const provenance={...window.fixture,schema:1};
          const heapBefore=performance.memory?.usedJSHeapSize||null;
          const geometry=window.WindScoreNoteGeometry.collect(window.osmd,provenance,document.getElementById('score'),Infinity);
          window.__noteGeometry=geometry;
          const heapAfter=performance.memory?.usedJSHeapSize||null;
          const firstPage=window.WindScoreNoteGeometry.collect(window.osmd,provenance,document.getElementById('score'),1);
          return {...diagnostic,production:geometry,firstPage,
            overhead:{elapsedMs:geometry.elapsedMs,bytes:new TextEncoder().encode(JSON.stringify(geometry)).length,
              heapBefore,heapAfter,heapDelta:heapBefore?heapAfter-heapBefore:null}};

        },mode);
        fs.writeFileSync(path.join(output,`${fixture.name}-${mode.name}.json`),JSON.stringify(result,null,2));
        console.log(JSON.stringify({fixture:fixture.name,mode:mode.name,sources:result.sourceCount,mapped:result.mapped.length,
          ambiguous:result.ambiguous.length,unsupported:result.unsupported.length,missing:result.missing.slice(0,2),svgCount:result.svgCount,elapsedMs:result.elapsedMs}));
        assert.equal(result.version,'2.2.1-release');
        const production=result.production;
        assert.equal(production.notes.length,fixture.notes.length);
        if(mode.name==='initial'){
          const statuses=await page.evaluate(()=>{
            const f={...window.fixture,schema:1};
            const collect=notes=>window.WindScoreNoteGeometry.collect(window.osmd,{...f,notes},document.getElementById('score'),Infinity).notes.map(n=>n.status);
            const first=f.notes[0];
            return {
              voices:collect([{...first,voice:'01'},{...first,voice:''},{...first,voice:'9007199254740993'}]),
              unsafeTime:collect([{...first,tick:Number.MAX_SAFE_INTEGER+1}]),
              missing:collect([{...first,voice:'1',pitch:200}]),
              duplicateParts:collect([{...first,voice:'1'},{...first,voice:'1',key:first.key.replace('/p0/','/p99/')}])
            };
          });
          assert.deepEqual(statuses.voices,['unsupported-voice','unsupported-voice','unsupported-voice']);
          assert.deepEqual(statuses.unsafeTime,['unsupported-time']);
          assert.deepEqual(statuses.missing,['missing-source']);
          assert.deepEqual(statuses.duplicateParts,['ambiguous-part','ambiguous-part']);
        }
        assert.equal(production.notes.filter(n=>n.status==='mapped').length,result.mapped.length);
        assert.equal(production.notes.filter(n=>n.status==='ambiguous-source').length,fixture.expectedAmbiguous);
        assert.equal(production.notes.filter(n=>n.status==='unsupported-voice').length,fixture.expectedUnsupported);
        for(const note of production.notes.filter(n=>n.status==='mapped')){
          const old=result.mapped.find(n=>n.writtenKey===note.writtenKey);
          assert.equal(note.performedId,old.performedId);
          assert.equal(note.page,old.svgIndex);
          for(const k of ['x','y','width','height'])
            assert.ok(Math.abs(note.rect[k]-old.headRect[k]/(mode.cssScale||1))<0.01,'snapshot pixel normalization');
        }
        assert.equal(result.firstPage.notes.filter(n=>n.status==='uncaptured-page').length,
          production.notes.filter(n=>n.status==='mapped'&&n.page>0).length);
        assert.ok(result.firstPage.notes.filter(n=>n.status==='uncaptured-page').every(n=>n.rect===null));

        assert.equal(result.ambiguous.length,fixture.expectedAmbiguous);
        assert.equal(result.unsupported.length,fixture.expectedUnsupported);
        assert.deepEqual(result.missing,[],'structural mapping must not guess or drop supported notes');
        if(!fixture.expectedUnsupported)assert.equal(result.sourceCount,fixture.notes.length);
        if(fixture.name==='voice-ties-reversed')assert.ok(result.allSource.some(n=>n.tie && new Set(n.tie.map(t=>t.voice)).size>1),
          'document the bundled 2.2.1 cross-voice tie mismatch; never use that tie graph for event IDs');
        assert.equal(result.retainedSources,mode.name==='reload'?0:result.sourceCount,'source objects survive layout but not reload');
        assert.equal(result.mapped.length+result.ambiguous.length+result.unsupported.length,fixture.notes.length);
        assert.ok(result.mapped.every(n=>n.mapped && n.rect && n.rect.width>0 && n.rect.height>0 && n.svgIndex>=0 && n.page===n.svgIndex+1));
        if(fixture.name==='chord') {
          assert.ok(result.mapped.every(n=>n.headRect),'public graphical anchor resolves three separate chord heads');
          assert.equal(new Set(result.mapped.map(n=>n.headRect.y)).size,3);
        }
        let colorProbe=null;
        if(fixture.name==='chord' && mode.name==='initial') {
          colorProbe=await page.evaluate(()=> {
            const note=window.osmd.Sheet.SourceMeasures[0].VerticalSourceStaffEntryContainers[0].StaffEntries[0].VoiceEntries[0].Notes[0];
            const g=window.osmd.EngravingRules.GNote(note);
            const options={applyToNoteheads:true,applyToStem:false,applyToFlag:false,applyToModifiers:false};
            g.setColor('#ff0000',options);
            const changed=g.getNoteheadSVGs().filter(h=>[...h.querySelectorAll('path')].some(p=>p.getAttribute('fill')==='#ff0000')).length;
            g.setColor('#000000',options);return changed;
          });
          assert.equal(colorProbe,3,'setColor on one graphical note colors the entire chord head collection');
        }
        for(const n of result.mapped) {
          const size=result.svgSizes[n.svgIndex];
          assert.ok(n.rect.x>=-1 && n.rect.y>=-1 && n.rect.x+n.rect.width<=size.width+1 && n.rect.y+n.rect.height<=size.height+1,`geometry in its own SVG: ${n.writtenKey}`);
        }
        const identity=result.mapped.map(n=>[n.writtenKey,n.performedId]).sort();
        if(layouts.length)assert.deepEqual(identity,layouts[0].identity,'zoom/pagination must not replace persistent event IDs');
        layouts.push({mode:mode.name,identity,colorProbe,...result});
      }
      if(fixture.name==='konekuto') {
        const pages=layouts.find(l=>l.mode==='pages');assert.ok(pages.svgCount>1);
        assert.ok(new Set(pages.mapped.map(n=>n.svgIndex)).size>1,'all pages need their own coordinate space');
        assert.ok(layouts.every(l=>l.mapped.every(n=>n.headRect)),'all 2049 fixture heads resolve in every layout');
      }
      const first=layouts[0],last=layouts.find(l=>l.mode==='return');
      for(let i=0;i<first.mapped.length;i++) {
        assert.ok(Math.abs(first.mapped[i].rect.x-last.mapped[i].rect.x)<1 && Math.abs(first.mapped[i].rect.y-last.mapped[i].rect.y)<1,'returning to original layout rebuilds equivalent geometry');
      }
      const svgChurn=first.mapped.filter((n,i)=>n.svgId!==last.mapped[i].svgId).length;
      results.push({fixture:fixture.name,notes:fixture.notes.length,events:new Set(fixture.notes.map(n=>n.performedId)).size,
        mapped:first.mapped.length,ambiguous:first.ambiguous.length,unsupported:first.unsupported.length,
        distinctSvgIds:new Set(first.mapped.map(n=>n.svgId)).size,svgChurn,
        crossVoiceTieObservations:first.allSource.filter(n=>n.tie && new Set(n.tie.map(t=>t.voice)).size>1).length,
        layouts:layouts.map(l=>({mode:l.mode,svgCount:l.svgCount,elapsedMs:l.elapsedMs,geometryMs:l.geometryMs,
          productionOverhead:l.overhead,
          uniqueHeadGeometry:l.mapped.filter(n=>n.headRect).length,colorProbe:l.colorProbe,
          distinctHeadRects:new Set(l.mapped.filter(n=>n.headRect).map(n=>JSON.stringify([n.svgIndex,...['x','y','width','height'].map(k=>Math.round(n.headRect[k]*100)/100)]))).size})),
        xmlIdsRetained:first.allSource.flatMap(n=>n.xmlIdsRetained).length});
      // Optional visual evidence, in this research page only; no application code uses these overlays.
      const displayed=layouts.at(-1).mapped.filter(n=>n.headRect && (fixture.name!=='konekuto'||n.tick===2632));
      if(displayed.length) {
        await page.evaluate(notes=> {
          const svgs=[...document.querySelectorAll('#score svg')];
          for(const n of notes) {
            const r=document.createElementNS('http://www.w3.org/2000/svg','rect');
            for(const k of ['x','y','width','height'])r.setAttribute(k,n.headRect[k]);
            r.setAttribute('fill','none');r.setAttribute('stroke','#dc2680');r.setAttribute('stroke-width','1');
            r.setAttribute('data-written-key',n.writtenKey);r.setAttribute('data-performed-id',n.performedId);
            svgs[n.svgIndex].appendChild(r);
          }
          const last=notes.at(-1);const rect=svgs[last.svgIndex].getBoundingClientRect();
          window.scrollTo(0,window.scrollY+rect.top+last.headRect.y-300);
        },displayed);
        await page.screenshot({path:path.join(output,`${fixture.name}-overlay.png`)});
      }
      await page.close();
    }
    fs.writeFileSync(path.join(output,'summary.json'),JSON.stringify({browser:await browser.version(),results},null,2));
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>server.close());
