/* Actual OSMD rendering, DOM iterator and raster checks. No reimplementation of layout. */
const fs = require('fs');
const path = require('path');
const http = require('http');
const assert = require('assert/strict');
let playwright;
try { playwright = require('playwright'); } catch (_) {
  playwright = require(process.env.WINDSCORE_PLAYWRIGHT || '/opt/codex/runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
}
const output = path.resolve(process.argv[2]);
const reference = JSON.parse(fs.readFileSync(path.join(output, 'reference.json')));
const server = http.createServer((req, res) => {
  const name = req.url.split('?')[0] === '/' ? 'index.html' : path.basename(req.url.split('?')[0]);
  const file = path.join(output, name);
  if (!fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
  res.setHeader('Content-Type', name.endsWith('.html') ? 'text/html' : name.endsWith('.js') ? 'application/javascript' : 'application/xml');
  res.end(fs.readFileSync(file));
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await playwright.chromium.launch({executablePath: process.env.WINDSCORE_CHROMIUM || '/usr/bin/chromium', headless: true, args: ['--no-sandbox']});
  try {
    const results = [];
    for (const width of [1200, 800, 360]) {
      const page = await browser.newPage({viewport: {width, height: 800}});
      page.on('pageerror', e => console.error('page error', e));
      await page.goto(`http://127.0.0.1:${server.address().port}/`);
      await page.waitForFunction(() => window.__renderTime >= 0 && window.__snapshotChunkCount > 0 && window.__snapshotChunksReady === window.__snapshotChunkCount, null, {timeout: 120000});
      const data = await page.evaluate(() => {
        const osmd = window.__osmd;
        const groups = [];
        osmd.cursor.reset();
        let safety = 10000;
        while (!osmd.cursor.iterator.EndReached && safety-- > 0) {
          const it = osmd.cursor.iterator;
          const entries = it.CurrentVoiceEntries || it.currentVoiceEntries || [];
          groups.push({tick: Math.round(fracReal(it.currentTimeStamp) * 16),
            pitches: entries.flatMap(e => (e.Notes || e.notes || []).filter(n => n.Pitch).map(n => n.Pitch.halfTone + 12)).sort((a,b) => a-b)});
          osmd.cursor.next();
        }
        osmd.cursor.reset(); window.__cursorIdx=0;
        const times = window.__cursorTimes;
        return {status: window.__status, renderMs: window.__renderTime, transform: document.getElementById('osmd').style.transform,
          svgCount: document.querySelectorAll('#osmd svg').length, pathCount: document.querySelectorAll('#osmd svg path').length,
          width: window.__snapshotChunkWidth, height: window.__snapshotTotalHeight,
          positions: window.__cursorPos, ticks: times.map(t => Math.round(t*16)), groups,
          chunks: window.__snapshotChunks, error: window.__snapshotError || null};
      });
      const chunks = data.chunks; delete data.chunks;
      console.log(JSON.stringify({...data, positions: data.positions.slice(-12), ticks: data.ticks.slice(-4), groups: data.groups.slice(-3)}));
      chunks.forEach((chunk, i) => fs.writeFileSync(path.join(output, `${width}-chunk-${i}.png`), Buffer.from(chunk.split(',')[1], 'base64')));
      await page.screenshot({path: path.join(output, `${width}-browser.png`)});
      fs.writeFileSync(path.join(output, `${width}-geometry.json`), JSON.stringify(data, null, 2));
      assert.equal(data.error, null);
      assert.equal(data.svgCount, 1, 'all score pages must be included in raster snapshot');
      assert.ok(data.pathCount > 1000);
      assert.deepEqual(data.groups.find(g => g.tick === 2632).pitches, [30,42,58,66]);
      const wanted = new Map();
      for (const note of reference.writtenNotes) {
        if (!wanted.has(note.startTick)) wanted.set(note.startTick, []);
        wanted.get(note.startTick).push(note.pitch);
      }
      for (const [tick, pitches] of wanted) {
        assert.deepEqual(data.groups.find(g => g.tick === tick)?.pitches, pitches.sort((a,b)=>a-b), `rendered written notes at ${tick}`);
      }
      // Drive the actual DOM cursor through forward, held-note, end, and backward/loop seeks.
      const seeks = await page.evaluate(() => {
        const found = [];
        let previous = -1;
        for (const tick of [0, 2616, 2632, 2640, 2648, 2632, 0]) {
          if (tick < previous) window.__resetCursor(); // production host's backward-seek protocol
          window.tickAndSeek(tick);
          const idx=window.__cursorIdx;
          const element=window.__osmd.cursor.cursorElement.getBoundingClientRect();
          const container=document.getElementById('osmd');
          const rect=container.getBoundingClientRect();
          found.push({tick, cursorTick: Math.round(window.__cursorTimes[idx]*16),
            x:element.left-rect.left, y:element.top-rect.top+container.scrollTop,
            expectedX:window.__cursorPos[idx*3], expectedY:window.__cursorPos[idx*3+1]});
          previous=tick;
        }
        return found;
      });
      for (const seek of seeks) {
        assert.equal(seek.cursorTick, seek.tick >=2632 ? 2632 : seek.tick);
        assert.ok(Math.abs(seek.x-seek.expectedX)<1 && Math.abs(seek.y-seek.expectedY)<1, JSON.stringify(seek));
      }
      data.seeks=seeks;
      fs.writeFileSync(path.join(output, `${width}-geometry.json`), JSON.stringify(data,null,2));
      assert.ok(data.ticks.includes(2632));
      for (let i=0; i<data.ticks.length; i++) {
        const [x,y,h] = data.positions.slice(i*3,i*3+3);
        assert.ok(x>=0 && x<data.width && y>=0 && y+h<=data.height+1, `cursor outside snapshot at ${data.ticks[i]}: ${x},${y},${h}`);
      }
      results.push({width, renderMs: data.renderMs, groups: data.groups.length, height: data.height});
      await page.close();
    }
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(results,null,2));
  } finally { await browser.close(); }
})().catch(e => {console.error(e); process.exitCode=1;}).finally(() => server.close());
