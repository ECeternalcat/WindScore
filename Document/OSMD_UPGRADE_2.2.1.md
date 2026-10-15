# OSMD 2.0.0 → 2.2.1 evaluation and upgrade

Evaluated October 15, 2026. Work continues on branch `work`. The tree was clean at `afdd22b`; no baseline commit or separate branch was necessary. Nothing is pushed or merged.

## Decision and provenance

Adopt **2.2.1**, the latest non-draft, non-prerelease version returned by the official GitHub releases API when checked. It was published October 12, 2026. Both platform bundles are the unmodified [official release asset](https://github.com/opensheetmusicdisplay/opensheetmusicdisplay/releases/tag/2.2.1), verified against the asset's published SHA-256:

`537ed08f9c082ce0d785d21b80485b8d443cfb92ddc7a4ba4977804f09408cf4`

The previous bundled asset identifies itself at runtime as `2.0.0-release`; its SHA-256 is `6db4be76cfc1499cabe8bccb409ff0c261fe7aa22c002d93ed61f29817bf6f22`. The new bundle identifies itself as `2.2.1-release`.

`third_party/osmd/manifest.json`, `README.md` and `LICENSE` record provenance and reproduction. OSMD is **BSD-3-Clause**. Both platform directories now ship `opensheetmusicdisplay.min.js.LICENSE.txt` containing the OSMD license and bundled dependency notices; JSZip uses its MIT option. No application runtime downloads or npm dependencies are introduced. Source maps are not required for operation and are not shipped. The verified official npm bundle was consulted for notices; its JavaScript prefix is byte-identical to the GitHub asset, which additionally carries a source-map URL. The actual shipped artifact comes from GitHub.

## Upstream compatibility review

The pinned [changelog](https://github.com/opensheetmusicdisplay/opensheetmusicdisplay/blob/2.2.1/CHANGELOG.md), source and official source map were reviewed across 2.1.x and 2.2.x. Relevant upstream work includes geometric skyline/glyph caching performance improvements, cursor drawing improvements, more reliable repeated rendering, and fixes to multi-voice staggering, ties, slurs and expression positioning. Those are upstream claims; the measurements below establish the benefits actually observed for WindScore's score and pipeline.

The existing UMD global `opensheetmusicdisplay`, constructor, `load`, `updateGraphic`, `render`, zoom, SVG output, cursor iterator and cursor DOM methods remain usable. Actual rendering verifies the APIs WindScore calls. No new engraving rule is required. New defaults for percussion staff lines and lyric verse-number placement do not apply to this piano fixture. New incremental `renderNext()` APIs are deliberately not adopted: WindScore's current pipeline needs the complete sheet raster and cursor table before displaying the cached native snapshot.

Both 2.0.0 and 2.2.1 upstream TypeScript configurations target **ES2020**, and both shipped assets contain optional chaining/nullish coalescing. The newer bundle additionally uses `flatMap`, whose browser support predates that syntax. This does not establish a new syntax floor compared with the bundled 2.0.0, but Android API 24 alone does not guarantee a suitable WebView version. Actual compatibility is verified on Chromium 154 and JavaFX 23; old stock Android WebViews, Windows/macOS hosts and physical Android devices are not verified here. No Android minimum SDK or browser polyfill policy is changed.

## Measured benchmark

`tools/benchmark_osmd.cjs` serves each bundle locally with the **identical production HTML and identical `konekuto-claris.mxl` XML**. It wraps public methods for timing without changing library bytes or production rendering options. The score's MXL SHA-256 is `693c312c97a12e672124fc367aa3e52a4ae44cb1a2257f85f20133fba407042c`.

Environment: Linux cloud host, Node v24.19.0, Chromium 154.0.8037.57, viewport height 800. Each version/width gets one unmeasured warmup followed by five fresh-page measurements, with version order alternating. No Gradle build was run during the comparison. Remote requests are blocked and checked. This is a small, reproducible single-host benchmark, not a device performance guarantee or statistical confidence interval.

All values below are medians, milliseconds:

| Width | Old parse + two-pass render | New parse + two-pass render | Reduction | Old completed snapshot | New completed snapshot | Reduction |
|---:|---:|---:|---:|---:|---:|---:|
| 1200 | 3392 | 2209 | 34.9% | 4816.9 | 3616.0 | 24.9% |
| 800 | 2741 | 1621 | 40.9% | 4795.4 | 3338.7 | 30.4% |
| 360 | 3232 | 1846 | 42.9% | 8781.3 | 6932.6 | 21.1% |

“Parse + two-pass render” is WindScore's existing `__renderTime`, including its explicit graphic updates and both layout/render passes; it excludes cursor-table construction and PNG snapshots. “Completed snapshot” starts at the production XML-fetch stage and includes cursor enumeration, the existing 100-ms snapshot delay and completion of every PNG chunk. Median navigation-to-snapshot, also including script startup, improves 5073.7→4203.2 ms, 5063.3→3660.3 ms and 9077.0→7297.6 ms respectively (17.2%, 27.7%, 19.6%). Load-only medians improve 11.4–22.3%.

Bundle size increases **1,262,636→1,420,498 bytes** (+157,862; +12.5%). This trades a larger offline resource for faster rendering. Sampled JavaScript heap after snapshot completion changes 72.9→27.4 MB at 1200, 27.5→34.5 MB at 800 and 29.8→31.0 MB at 360. These samples are GC-sensitive, not peak memory or process RSS; **no general memory reduction is claimed**. PNG compression/rasterization remains a large part of narrow-screen startup time.

Raw measurements, browser version and asset checksums are committed in `Document/OSMD_BENCHMARK_2.0.0_TO_2.2.1.json`. Full renderer-local identity and layout diagnostics remain generated artifacts under `composeApp/build/validation/osmd-upgrade/comparison/`.

## Rendering, cursor geometry and identity

For both versions, the actual OSMD iterator yields all **2,049 pitched written segments**, and the existing independent semantic oracle still defines 1,708 merged performance notes, 341 tie connections and 874 attack groups. The actual cursor export has **987 steps** in both versions, including rest/tie positions and OSMD's terminal sentinel. Performance-group and cursor-step counts therefore should not be equated.

| Width | Old snapshot height | New snapshot height | PNG chunks, both | Old/new SVG path count |
|---:|---:|---:|---:|---:|
| 1200 | 7864 | 7898 | 2 | 9884 / 9885 |
| 800 | 13507 | 13583 | 4 | 10169 / 10170 |
| 360 | 41284 | 41360 | 11 | 11847 / 11848 |

The small layout differences are not treated as pixel equality failures. The probe checks all written onset/pitch groups, cursor bounds, real DOM cursor positions on forward/held/end/backward/loop seeks, and actual Compose sheet-cursor and falling-note pixels. A same-width extra re-render has **zero measured note-position drift** for both versions in this fixture. No repeat-render improvement is claimed for a case that already passes on 2.0.0.

The final-system comparison also shows the boundary's 175-BPM marking is now visible alongside the 58-BPM marking; the old renderer omitted it in the captured snapshot. OSMD's actual parsed tempo expressions agree with the independent oracle at ticks 2632 and 2648 in both versions. The newer API additionally exposes `AbsolutePlaybackTimestamp` and `PlaybackTempoInBpm`, which agree with that reference. The rendering probe asserts this distinction so two marks in one measure cannot be mistaken for simultaneous tempo changes. WindScore continues to use its own canonical `ScoreTiming` rather than creating a second playback timeline from these renderer APIs.

The reviewed APIs are `EngravingRules.GNote(note)`, `GraphicalNote.FromNote(note, rules)`, `Note.NoteToGraphicalNoteObjectId`, and `VexFlowGraphicalNote.getSVGId()` / `getSVGGElement()` / `getNoteheadSVGs()`. All 2,049 written notes map back to their graphical note's `sourceNote` in both versions. Their model-map IDs remain unchanged through the tested same-load re-render. However, the 2,049 source notes expose only **1,344 distinct SVG IDs**, and **all 2,049 SVG-ID observations change on re-render**. In the final chord, each pair of chord notes shares one SVG group, and the returned notehead elements have empty IDs. Upstream documents `getSVGId()` as a low-level API to use with caution.

These APIs support renderer-local lookup; they do not provide persistent MusicXML-to-performance identity. No SVG ID, graphical-map ID or array index is adopted as `NoteEntity.id`. Persisted score identity and the existing musical model remain unchanged. New source-to-performance mapping would need separate design for tie chains, overlapping unisons, voices and rests; it is not smuggled into this upgrade.

## Snapshot cache regression prevention

Before this change, desktop and Android used `${xmlHash}_${htmlHash}`. Changing only the JavaScript left the key unchanged, so an upgraded app could silently display an old renderer's PNG/geometry pair. The actual new layout heights above demonstrate that the renderer content matters.

The shared JVM helper `sheetSnapshotCacheKey` now includes XML hash, HTML hash, **SHA-256 of the actual JavaScript bytes**, and renderer family (`javafx-23` or `android-webview`). Both production hosts use it. Focused tests check changed bytes, deterministic reuse, XML/divisions/template changes, renderer isolation and bypassing legacy paths without overwriting their files. New snapshots still use the existing transactional publication and bounded retention mechanism. Legacy snapshot paths are not looked up; normal existing retention later removes old raster caches.

This change only namespaces disposable sheet snapshots. Parsed-score cache version, source XML hash, `NoteEntity` allocation, logical ticks, bookmarks and historical practice data are untouched. No migration or historical relabeling is necessary. It does not attempt to fingerprint every installed system font or WebView patch version; images and cursor geometry remain paired in one snapshot.

## Validation and reproduction

The cache fix was first tested against the old renderer: **202 desktop tests passed, zero failures/errors/skips**, including the original six unchanged music-semantics regressions and opt-in real rendering. This was committed independently as `ea00020` before replacing assets.

The complete upgraded run passes **203 desktop tests and 111 Android host tests, with zero failures, errors or skips**. Both opt-in actual-rendering tests execute. `compileAndroidMain`, debug APK assembly, unsigned release APK assembly, release vital lint and debug lint succeed. Debug lint reports zero errors and the same three warnings: old target API, newer compile SDK availability and missing application icon. The app debug unit-test task has no sources; connected testing succeeds with **zero tests** and no devices. These empty tasks are not counted as successful device tests.

`OsmdBundleTest` checks the pinned release digest, both platform asset copies and notices. Android host coverage checks its compiled cache-key helper and actual Android asset digest. `tools/validate_android_apks.py` confirms both built APKs contain the exact validated score, new OSMD bundle and notices, plus the existing MIDI library on all four ABIs. Debug APK size is 20,986,038 bytes; unsigned release APK size is 16,987,499 bytes. Neither is installed on a device.

The original six regression test bodies remain byte-identical to `a0ee1d9`; parser/cache/history compatibility coverage still passes. No upgrade-specific musical or cursor regression was found in this fixture. The confirmed cache invalidation defect is fixed separately. A final desktop-only full rerun also exercises the added actual-OSMD tempo-expression assertions; the Android code/assets are unchanged from the successful combined run.

Commands:

```sh
python3 tools/konekuto_reference.py --check
WINDSCORE_RENDER_VALIDATION=1 DISPLAY=:99 bash gradlew \
  :composeApp:desktopTest :composeApp:compileAndroidMain :composeApp:testAndroidHostTest \
  :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:testDebugUnitTest \
  :androidApp:lintDebug :androidApp:connectedDebugAndroidTest --no-daemon --console=plain
python3 tools/validate_android_apks.py
```

The browser checks require installed Node, Playwright and Chromium; `WINDSCORE_CHROMIUM` and `WINDSCORE_PLAYWRIGHT` override their locations. Linux JavaFX needs a running display. Cloud SDK, truststore, writable Java home and dummy Xorg configuration are described in `Document/KONEKUTO_END_TO_END_VALIDATION.md`; they remain outside the checkout. A first baseline JavaFX attempt found the previous virtual display no longer running and failed with “Unable to open DISPLAY”; restarting Xorg resolved it without code changes.

To reproduce the comparison after generating the normal rendering-test HTML/XML fixture:

```sh
mkdir -p composeApp/build/validation/osmd-compare-baseline
cp composeApp/build/validation/osmd/{index.html,score.xml} composeApp/build/validation/osmd-compare-baseline/
git show afdd22b:composeApp/src/desktopMain/resources/opensheetmusicdisplay.min.js \
  > composeApp/build/validation/osmd-compare-baseline/osmd.js
node tools/benchmark_osmd.cjs composeApp/build/validation/osmd-compare-baseline \
  composeApp/src/desktopMain/resources/opensheetmusicdisplay.min.js \
  composeApp/build/validation/osmd-compare-results 5
```

The JavaFX before/after full-score renders are correctness checks with single diagnostic timings, not a repeated JavaFX performance benchmark. APK assembly and Android host tests do not execute Android WebView. Connected test reports must be checked for actual test count; a successful empty task is not device evidence.

Generated evidence: `composeApp/build/validation/osmd-upgrade/baseline-captures/` contains the old Compose sheet/roll captures and cursor geometry; `composeApp/build/validation/osmd/` contains the upgraded equivalents. Native WebView output is under `composeApp/build/validation/javafx/`. Combined validation and final desktop logs are `/workspace/.windscore-setup/osmd-upgrade/final-validation.log` and `final-desktop-tempo.log`; current JUnit reports are under `composeApp/build/test-results/`.

## Remaining risks and manual checks

- On a physical Android device, cold-open and cached-open this score, rotate/resume/reopen it, then seek measure 165. Verify the installed WebView supports the bundle and compare startup memory/frame responsiveness on a low-memory device. The 360-px snapshot remains over 41,000 pixels high despite chunking.
- Verify measure 165's four pitches (30/42/58/66), its 58-BPM sustain (~4.138 s at 1×), and cursor/falling-note agreement during partial-chord waiting, speed changes, pause/resume, backward seeks and `[2632,2648)` loops. The 175-BPM mark is at the final boundary, not the chord onset.
- Verify real MIDI/audio latency, pedal/channel behavior and note release on reconnect or transport changes using the procedure in the prior end-to-end report. A rendering upgrade does not establish physical audio correctness.
- Check Windows/macOS native browser/font behavior and application lifecycle. Additional fixtures are needed for percussion, lyrics, multi-page output, CSS-transform fallback, grace notes and repeat navigation. The existing first-SVG snapshot architecture is retained; this score uses one SVG at all tested widths.
- Upstream engraving fixes beyond this fixture are promising but not independently established here. No MEI, Verovio, parser rewrite, incremental-render architecture or new persistent identity scheme is introduced.

## Commits

- `ea00020` — namespace sheet snapshots by actual renderer bytes and renderer family; focused cache tests, validated first on 2.0.0.
- `d010b02` — upgrade both offline assets to official 2.2.1, retain licenses/provenance, strengthen rendering/tempo/identity/offline checks and Android asset verification, add reproducible benchmark tooling.
- The following documentation-only commit records this report and all 30 measured benchmark samples. No push, merge or pull request was performed.
