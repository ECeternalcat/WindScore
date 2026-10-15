/* Compare two official offline bundles using the identical production HTML and score.
 * Usage: node tools/benchmark_osmd.cjs <baseline-dir> <candidate-js> <output-dir> [runs=5]
 * External requirements are the same as validate_osmd.cjs. One warmup per width/version.
 */
const fs=require('fs'), path=require('path'), http=require('http'), assert=require('assert/strict'), crypto=require('crypto');
let playwright;
try { playwright=require('playwright'); } catch (_) { playwright=require(process.env.WINDSCORE_PLAYWRIGHT || '/opt/codex/runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'); }
const baseline=path.resolve(process.argv[2]), candidate=path.resolve(process.argv[3]), output=path.resolve(process.argv[4]);
const count=Number(process.argv[5] || 5);
assert.ok(Number.isInteger(count) && count>0);
fs.mkdirSync(output,{recursive:true});
const assets={old:fs.readFileSync(path.join(baseline,'osmd.js')),new:fs.readFileSync(candidate)};
const server=http.createServer((req,res)=> {
  const [variant,name]=req.url.split('?')[0].split('/').filter(Boolean);
  if(!assets[variant]) { res.writeHead(404);res.end();return; }
  const file=name||'index.html';
  if(!['index.html','osmd.js','score.xml'].includes(file)) {res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.html')?'text/html':'application/xml');
  res.end(file==='osmd.js'?assets[variant]:fs.readFileSync(path.join(baseline,file)));
});
(async()=> {
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const origin=`http://127.0.0.1:${server.address().port}`;
  const browser=await playwright.chromium.launch({executablePath:process.env.WINDSCORE_CHROMIUM || '/usr/bin/chromium',headless:true,args:['--no-sandbox','--enable-precise-memory-info']});
  const results=[];
  try {
    for(const width of [1200,800,360]) for(let run=-1;run<count;run++) {
      for(const variant of run%2===0?['new','old']:['old','new']) {
        const page=await browser.newPage({viewport:{width,height:800}});
        const external=[];
        await page.route('**/*',route=> {
          if(new URL(route.request().url()).origin!==origin) {external.push(route.request().url());return route.abort();}
          return route.continue();
        });
        await page.addInitScript(()=> {
          window.__benchmark={loadMs:0,renderMs:[],updateMs:[]};
          const metrics=window.__benchmark;
          let osmd,status,ready;
          Object.defineProperty(window,'__osmd',{configurable:true,get:()=>osmd,set:value=> {
            osmd=value;
            if(!osmd)return;
            for(const [method,key] of [['render','renderMs'],['updateGraphic','updateMs']]) {
              const original=osmd[method].bind(osmd);
              osmd[method]=function(...args) { const start=performance.now(); const result=original(...args);metrics[key].push(performance.now()-start);return result; };
            }
            const load=osmd.load.bind(osmd);
            osmd.load=function(...args) {const start=performance.now();return load(...args).then(result=>{metrics.loadMs=performance.now()-start;return result;});};
          }});
          Object.defineProperty(window,'__status',{configurable:true,get:()=>status,set:value=> {
            status=value;
            if(value==='fetch XML')metrics.pipelineStart=performance.now();
            if(value.startsWith('ready ')) metrics.readyMs=performance.now()-metrics.pipelineStart;
          }});
          Object.defineProperty(window,'__snapshotChunksReady',{configurable:true,get:()=>ready,set:value=> {
            ready=value;
            if(value>0 && value===window.__snapshotChunkCount) {
              metrics.snapshotMs=performance.now()-metrics.pipelineStart;
              metrics.navigationToSnapshotMs=performance.now();
            }
          }});
        });
        await page.goto(`${origin}/${variant}/index.html`);
        await page.waitForFunction(()=>window.__benchmark.snapshotMs>0,null,{timeout:120000});
        const data=await page.evaluate(()=>({
          ...window.__benchmark,version:window.__osmd.Version,appRenderMs:window.__renderTime,
          width:window.__snapshotChunkWidth,height:window.__snapshotTotalHeight,
          chunks:window.__snapshotChunkCount,cursorSteps:window.__cursorTimes.length,
          paths:document.querySelectorAll('#osmd svg path').length,
          pngBytes:window.__snapshotChunks.reduce((sum,p)=>sum+Math.floor(p.split(',')[1].length*3/4),0),
          heapBytes:performance.memory?.usedJSHeapSize||null,error:window.__snapshotError||null
        }));
        assert.equal(data.error,null);assert.deepEqual(external,[],'offline runtime must not request remote resources');
        if(run>=0) {results.push({variant,width,run,...data});console.log(JSON.stringify(results.at(-1)));}
        if(run===0) {
          // Capture layout and renderer-local IDs before and after an identical re-render.
          const detail=await page.evaluate(()=> {
            const osmd=window.__osmd;
            function inspect() {
              osmd.cursor.reset(); const notes=[]; let safety=10000;
              while(!osmd.cursor.iterator.EndReached && safety-->0) {
                const it=osmd.cursor.iterator;
                for(const voice of it.CurrentVoiceEntries) for(const note of voice.Notes) {
                  if(!note.Pitch)continue;
                  const g=osmd.EngravingRules.GNote(note);
                  notes.push({tick:Math.round(fracReal(it.currentTimeStamp)*16),pitch:note.Pitch.halfTone+12,
                    modelId:note.NoteToGraphicalNoteObjectId,svgId:g?.getSVGId?.(),
                    noteheads:g?.getNoteheadSVGs?.().map(e=>e.id), mapped:g?.sourceNote===note,
                    rect:g?.getSVGGElement?.()?.getBoundingClientRect().toJSON()});
                }
                osmd.cursor.next();
              }
              osmd.cursor.reset();
              const svg=document.querySelector('#osmd svg');
              return {notes,height:svg.getBoundingClientRect().height,pathCount:svg.querySelectorAll('path').length};
            }
            const before=inspect();osmd.updateGraphic();osmd.render();const after=inspect();
            return {before,after};
          });
          fs.writeFileSync(path.join(output,`${variant}-${width}-identity-layout.json`),JSON.stringify(detail,null,2));
          await page.screenshot({path:path.join(output,`${variant}-${width}-rerender.png`)});
        }
        await page.close();
      }
    }
    const metadata={node:process.version,browser:await browser.version(),runs:count,warmups:1,
      baselineDirectory:baseline,candidateAsset:candidate,
      assets:Object.fromEntries(Object.entries(assets).map(([k,v])=>[k,{bytes:v.length,sha256:crypto.createHash('sha256').update(v).digest('hex')}])),results};
    fs.writeFileSync(path.join(output,'benchmark.json'),JSON.stringify(metadata,null,2));
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>server.close());
