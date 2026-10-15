# Importer provenance and layout-scoped note geometry

**Follow-up:** Current-attack target cues now consume this metadata only in Score + keyboard. See [Three-view practice improvements](LEARNING_VIEWS_IMPLEMENTATION.md). The report below records the original metadata-only implementation and its measurements.

This implementation adds optional snapshot metadata. It does not enable highlighting, change the cursor or falling-note drawing, replace OSMD, or modify playback/practice behavior.

## Identity and compatibility

The authoritative implementation is `MusicXmlParser.parseXmlWithProvenance` in `composeApp/src/jvmShared/kotlin/com/windscore/core/parser/MusicXmlParser.kt`. The existing `parseXml` delegates to the same parser with provenance capture disabled. Each pitched raw note carries its written locator into the existing tie-resolution loop; immediately after the loop selects or allocates its event, the importer records that event's **existing** ID. No allocation or tie-matching rule was changed.

`ScoreProvenance` and `WrittenNote` in `commonMain/core/model/ScoreProvenance.kt` are separate from `Score`. A locator is `sourceHash/p<partOrdinal>/m<measureOrdinal>/n<noteOrdinal>`; note ordinals include rests and measure ordinals distinguish repeated measure numbers. The sidecar retains the original XML ID as descriptive data, original voice string, placement staff, canonical tick/duration, pitch, tie flags/numbers, and performed ID. Several written tie segments may point to one event. Staff changes never create identity boundaries.

Neither `Score` nor `NoteEntity` changed. Score version remains 4. Musical caches, bookmarks, recordings and historical practice schemas require no migration. The original six semantics regressions remain unchanged.

For this first integration, `NoteGeometryBridge.importerProvenanceJson` rebuilds provenance from raw XML on a raster-cache miss, on a background dispatcher. It requires exact agreement with the loaded score's source hash, version, divisions, complete event list, duration and tempo map. A historical or edited model that differs receives no geometry; it is never assigned newly inferred IDs. This deliberately avoids changes to storage/import application wiring. Passing the sidecar directly through future import/session plumbing could eliminate that additional DOM parse, but requires separate lifecycle/memory measurements.

## Source join and geometry

`commonMain/ui/OsmdNoteGeometryJs.kt` is the production JavaScript, embedded by `OsmdHtml` for both platforms and directly exercised by the rendering tests. It traverses OSMD source measures/staff entries/voice entries, deduplicating source objects. The structural signature is:

`(part ID, measure ordinal, local instrument staff, original canonical numeric voice, tick, duration, written MIDI pitch)`

Only a unique importer bucket and a unique OSMD bucket may bind. Missing or duplicate part IDs, identical unisons, noncanonical numeric voices (including leading zeros), missing/nonnumeric voices, unsafe JavaScript tick integers, missing graphical notes, and unresolved noteheads produce explicit statuses. No occurrence-order fallback is used.

The source note resolves through `EngravingRules.GNote(note)`, checked with `g.sourceNote === note`. A graphical anchor and public pixel-distance conversion resolve a unique head within the chord group's head rectangles. Root SVG and chord group measurements are cached for one collection. Coordinates are normalized out of CSS scale into page-local snapshot pixels. Renderer IDs, SVG IDs, source objects and OSMD tie graphs do not cross the native bridge and are never persisted as identity.

The pinned OSMD 2.2.1 cross-voice tie-graph defect remains: its tie graph can connect the wrong voices when equal-pitch tie stops are reversed. The importer associations remain correct because the collector never reads that graph.

## Layout lifetime and cache

`SheetSnapshot.noteGeometry` defaults to null. Existing consumers ignore it. Every render/load invalidates geometry and advances a layout generation. The collector records renderer version, generation, viewport dimensions, zoom, SVG/viewBox dimensions and transforms. Pending PNG callbacks from an earlier generation cannot publish into the next capture. Native export records the generation before extracting PNGs and accepts geometry only through a generation-checked accessor, with a second Kotlin generation check; a concurrent re-layout therefore yields no geometry instead of attaching new rectangles to an older bitmap.

The existing raster-cache key already includes XML, HTML/runtime code, bundled OSMD SHA-256 and native renderer family. An additional loaded-score identity digest prevents reuse across different historical event models. The geometry envelope is stored as `note-geometry.v1.json` inside the same temporary directory and publication operation as the raster. It binds to the full raster-cache key and dimensions. Native decoding checks schema, pinned renderer, source/version/divisions, unique written keys, complete performed-ID coverage, statuses and finite in-bounds rectangles. Missing, corrupt or incompatible geometry falls back to the existing raster/cursor snapshot.

The cache intentionally continues to reuse an existing bitmap layout across viewports, as before. Geometry belongs to that bitmap's pixel coordinates, not the current screen viewport. A future drawing consumer must apply exactly the bitmap transform.

**Pagination limitation:** OSMD's paginated source/graphical mapping is tested across five SVG pages, but the existing production capture pipeline takes the first SVG. Production collection therefore marks later pages `uncaptured-page`, with no usable rectangle. This change does not expand raster pagination.

## Reproducible validation

With the existing toolchain and display configured:

```sh
DISPLAY=:99 WINDSCORE_RENDER_VALIDATION=1 bash gradlew \
  :composeApp:desktopTest :composeApp:testAndroidHostTest \
  :androidApp:assembleDebug :androidApp:assembleRelease \
  :androidApp:testDebugUnitTest :androidApp:lintDebug \
  :androidApp:connectedDebugAndroidTest --no-daemon --console=plain

node tools/benchmark_note_geometry.cjs composeApp/build/validation/osmd
python tools/validate_android_apks.py
```

The opt-in tests require existing Node/Playwright/Chromium and a display for native JavaFX/Compose tests. No new application dependencies were added.

Coverage includes:
- Actual importer sidecar versus ordinary imports and unchanged serialized Score bytes; divisions changes, rests, repeated measure labels and arbitrary voice labels.
- The independent bundled-score oracle's every written pitch/tick/duration/voice/staff and every written-to-performed tie membership.
- Chords, same-pitch separate voices, cross-staff ties/chords, tie chains, reversed voice tie stops, numbered identical unisons, identical same-voice unisons, nonnumeric/missing voices, changing divisions, enharmonic ties and multiple parts.
- Fifteen fixtures across seven layouts each: initial, zoom, width reflow, CSS scale, pagination, original-layout return and reload. The production collector is compared against actual source/graphical/DOM geometry; persistent event IDs remain constant.
- Explicit unsupported/ambiguous/missing statuses, including unsafe times and duplicate part identity.
- Real production HTML in Chromium at 1200, 800 and 360 pixels and JavaFX WebView, including PNG chunks and existing Compose sheet/piano-roll image checks.
- Renderer/layout/source/history cache rejection, optional corrupt-cache fallback, Android escaped JavaScript JSON transport and stale asynchronous PNG callback rejection.
- A/B production raster hashes with provenance enabled/disabled, collection timing, GC-assisted JS retained heap, native decode allocations and importer timing.

Final verification on 2026-10-15:
- **211 desktop tests and 112 Android host tests:** zero failures, errors or skips, including all 105 actual-rendering fixture/layout cases.
- **Android debug/release APK builds and lint:** passed. Release APK is unsigned. Lint retains the three existing warnings: OldTargetApi, GradleDependency and MissingApplicationIcon.
- **APK packaging:** unchanged offline score/OSMD/license assets and all four MIDI native ABIs verified in both variants.
- **App unit-test task:** NO-SOURCE. The connected task completed without instrumented tests or attached devices; this is not an Android device-rendering result.
- **Bundled score:** all 2049 written notes retain their links to the existing 1708 event IDs, including every independent-oracle tie membership.

Representative measurements for the bundled score (five warm samples per viewport; three cold A/B pairs):

| Measurement | Observed result |
| --- | --- |
| Warm JS collection median, widths 1200 / 800 / 360 | 19.5 / 21.0 / 21.4 ms |
| Cold JS collection with metadata enabled | 30.2–41.0 ms |
| Native geometry decode | median 9.4 ms; 7.8–13.0 ms range |
| Native decode transient allocations | median 4.52 MB, not retained heap |
| Geometry JSON / importer sidecar JSON | 424,682 / 508,366 bytes at width 1200 |
| Retained geometry-only Chromium heap | about 0.231 MB after GC |
| Total enabled-versus-disabled Chromium heap difference | paired median 788,252 bytes, including provenance and geometry |
| Browser snapshot-ready time, metadata off / on | medians 3.559 / 3.631 s; ranges overlap substantially |
| PNG output with metadata off versus on | identical across all six A/B runs, including every chunk |

The browser-ready comparison excludes native HTML generation, bridge transfer and PNG decoding. Its noisy three-pair sample does not establish a stable end-to-end latency budget. Ordinary versus provenance-enabled parser medians were 45.6 versus 39.2 ms in five interleaved samples with overlapping ranges; **no parser speedup is claimed**. Collection runs once per capture/layout, never per playback frame.

Raw samples, counts and limitations are recorded in [NOTE_PROVENANCE_IMPLEMENTATION_RESULTS.json](NOTE_PROVENANCE_IMPLEMENTATION_RESULTS.json). Implementation commits are `94d1cc5` (importer provenance) and `5b2cd8f` (snapshot geometry and regression/rendering coverage); no push or merge was performed.

## Remaining validation and limits

These tests cover the listed fixtures, not every MusicXML construct. Grace/cue notes, microtonal pitches, transposing instruments, unusual source offsets, shared noteheads and unsupported voices may produce explicit missing/ambiguous results rather than geometry. No source-note identity is guessed to improve coverage. Existing importer behavior for repeats and other musical constructs is preserved; this work does not introduce repeat expansion.

Browser heap metrics are Chromium measurements, not native peak RSS or Android GPU/Bitmap memory. JVM allocation measurements are transient allocated bytes, not retained heap. The A/B test compares optional metadata on/off with the same OSMD version. Large scores and long-running layout churn still need device profiling.

No physical Android device, MIDI keyboard or audio loopback is available here. On representative Android WebView versions and desktop devices:
1. Open the bundled MXL offline, close/reopen it to hit the raster cache, rotate/resize, change view mode repeatedly, and verify unchanged cursor/falling-note behavior and image quality.
2. Using diagnostic inspection only, check 2049 written entries / 1708 performed IDs, first-page rectangle bounds and cache identity. Verify a renderer/template change causes a fresh snapshot; do not enable highlighting.
3. Profile cold load, warm reopen, repeated rotations/re-layouts and a substantially larger score for main-thread stalls, peak RSS/Bitmap/GPU memory and eventual reclamation.
4. Connect MIDI and audio output; practice chords, both-hand/single-hand modes, wait/resume, pause/seek and A–B loops, including measure 165's slowdown. Verify attack/release timing, clean-note feedback and absence of stuck notes by listening and, where possible, loopback recording.

Identical unisons and arbitrary voice labels remain unresolved for unique visual binding by design. Supporting them needs a separately verified source-identity API/reader hook or an explicitly justified normalization scheme; OSMD tie graphs and SVG IDs remain unsuitable.
