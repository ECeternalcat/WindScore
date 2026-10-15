/* Same production HTML/OSMD/raster path, toggling only the importer sidecar. No UI mutations. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert/strict'),crypto=require('crypto');
let playwright;try{playwright=require('playwright');}catch(_){playwright=require(process.env.WINDSCORE_PLAYWRIGHT||'/opt/codex/runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');}
const output=path.resolve(process.argv[2]);
const html=fs.readFileSync(path.join(output,'index.html'),'utf8');
assert.ok(/window\.__provenance=\{/.test(html),'export the provenance-enabled production HTML first');
const disabled=html.replace(/window\.__provenance=[^\n]+;/,'window.__provenance=null;');
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost');
 if(url.pathname==='/'){
  res.setHeader('Content-Type','text/html');res.end(url.searchParams.get('geometry')==='off'?disabled:html);return;
 }
 const name=path.basename(url.pathname),file=path.join(output,name);
 if(!['osmd.js','score.xml'].includes(name)){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':'application/xml');res.end(fs.readFileSync(file));
});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await playwright.chromium.launch({executablePath:process.env.WINDSCORE_CHROMIUM||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--enable-precise-memory-info']});
 const samples=[];let rasterHash;
 try{
  for(let pair=0;pair<3;pair++)for(const enabled of (pair%2?[true,false]:[false,true])){
   const page=await browser.newPage({viewport:{width:1200,height:800}});
   const origin='http://127.0.0.1:'+server.address().port;
   await page.route('**/*',r=>new URL(r.request().url()).origin===origin?r.continue():r.abort());
   const start=performance.now();
   await page.goto(origin+'/?geometry='+(enabled?'on':'off'));
   await page.waitForFunction(()=>window.__snapshotChunkCount>0&&window.__snapshotChunksReady===window.__snapshotChunkCount,null,{timeout:120000});
   const readyMs=performance.now()-start;
   const cdp=await page.context().newCDPSession(page);await cdp.send('Performance.enable');await cdp.send('HeapProfiler.collectGarbage');
   const heap=(await cdp.send('Performance.getMetrics')).metrics.find(m=>m.name==='JSHeapUsedSize').value;
   const result=await page.evaluate(()=>({renderMs:window.__renderTime,geometryMs:window.__noteGeometry?.elapsedMs??0,
    mapped:window.__noteGeometry?.notes.filter(n=>n.status==='mapped').length??0,chunks:window.__snapshotChunks,
    geometryError:window.__noteGeometryError||null}));
   assert.equal(result.geometryError,null);assert.equal(result.mapped,enabled?2049:0);
   const hash=crypto.createHash('sha256').update(JSON.stringify(result.chunks)).digest('hex');
   if(rasterHash)assert.equal(hash,rasterHash,'geometry must preserve every production raster byte');else rasterHash=hash;
   delete result.chunks;samples.push({pair,enabled,readyMs,heapUsedBytes:heap,rasterHash:hash,...result});
   await page.close();
  }
  fs.writeFileSync(path.join(output,'note-geometry-ab-benchmark.json'),JSON.stringify({browser:await browser.version(),samples},null,2));
  console.log(JSON.stringify(samples));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>server.close());
