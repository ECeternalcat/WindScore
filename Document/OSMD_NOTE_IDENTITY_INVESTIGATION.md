# Per-note identity investigation — OSMD 2.2.1

Research performed October 15, 2026 on branch `work`, beginning from clean commit `8d498c7`. The prototype is confined to desktop tests and `tools/prototypes/`. No production behavior, musical event representation, persisted IDs, cache schema or rendering backend is changed. Nothing is pushed.

## Conclusion

A reliable **partial mapping is feasible using existing OSMD APIs plus an importer-owned provenance sidecar**. The prototype maps every one of the bundled score's **2,049 written pitched segments** to OSMD source/graphical notes, individual notehead geometry and the existing **1,708 performed-event IDs**, through zoom, width changes, CSS scaling, five-page pagination and reloading.

It is not a universal identifier supplied by OSMD. Written and performed identities are different; chord SVG groups are shared; source objects survive layout but not reload; SVG IDs change on render. Identical same-voice unisons and arbitrary MusicXML voice strings require additional handling. OSMD's tie graph must not be substituted for WindScore's authoritative event provenance.

## Evidence and current ID contract

| Implementation / evidence | Repository path and symbols |
|---|---|
| Existing import and allocation | `composeApp/src/jvmShared/kotlin/com/windscore/core/parser/MusicXmlParser.kt`: `RawNote`, `TieKey`, `MutableNote`, `nextId`, tie merge loop |
| Persisted musical model | `composeApp/src/commonMain/kotlin/com/windscore/core/model/NoteEntity.kt`; `Score.kt`, `CURRENT_VERSION = 4` |
| Existing judgment grouping | `composeApp/src/commonMain/kotlin/com/windscore/core/engine/JudgmentEngine.kt`: required pitch sets and `hitNoteIds` |
| Test-only provenance/export | `composeApp/src/desktopTest/kotlin/com/windscore/ui/identity/OsmdIdentityPrototypeTest.kt`: `sidecar`, `Written`, `Fixture` |
| Actual browser mapping and geometry | `tools/prototypes/osmd_identity.cjs`: structural source traversal, `collect`, uniqueness/refusal checks and layout matrix |
| Pinned actual bundle | `third_party/osmd/manifest.json`; identical desktop resource / Android asset, SHA-256 `537ed08f9c082ce0d785d21b80485b8d443cfb92ddc7a4ba4977804f09408cf4` |

The parser allocates `NoteEntity.id` in source traversal order **when a new performed event is created**. A matching tie continuation extends an existing event's duration and does not allocate another ID. Final sorting by onset/pitch retains these IDs. Tie identity includes part, original voice string, sounding pitch and optional tied number, with exact endpoint continuity; staff is excluded. Practice-hand assignment remains that of the attack.

`NoteEntity` retains pitch, onset, duration, hand and fingering, but not all written-note voice/staff/ordinal information. Matching the already-flattened list by `(pitch,tick)` or even `(pitch,tick,duration,hand)` cannot reliably recover provenance for simultaneous duplicates. No new IDs should be assigned after sorting.

The prototype replays the documented allocation contract **only inside tests**, then verifies every reconstructed ID, onset, pitch, duration and hand against the actual production parser result. This duplication is a research mechanism, not the proposed production importer. The full-score test checks 2,049 unique written locators, 1,708 unchanged performed IDs and 341 additional tied segments. The original XML is not rewritten or annotated; explicit `xml-note-*` attributes exist only in the synthetic fixtures.

## Actual bundled APIs

Upstream source was inspected through the verified release source map and the pinned tag, particularly `Note.ts`, `SourceStaffEntry.ts`, `VoiceEntry.ts`, `Staff.ts`, `GraphicalNote.ts`, `VexFlowGraphicalNote.ts`, `VexFlowMusicSheetDrawer.ts`, `VoiceGenerator.ts`, `Tie.ts` and `ReaderPluginManager.ts`. The executable probe uses the actual bundled JavaScript, not TypeScript mocks.

| API | Verified use / boundary |
|---|---|
| `Sheet.SourceMeasures` → `VerticalSourceStaffEntryContainers` → `StaffEntries` → `VoiceEntries` → `Notes` | Enumerates source notes independent of cursor steps. A `Set<Note>` avoids duplicate object visits through staff links. Pitched-note filtering excludes rests. |
| `note.ParentStaff.ParentInstrument.IdString`, instrument `Staves`, `ParentVoiceEntry.ParentVoice.VoiceId`, `SourceMeasure`, `getAbsoluteTimestamp()`, `Length`, `Pitch` | Supplies structural fields for the supported written-note join. Measure ordinal and local staff ordinal are used, not printed measure numbers or global staff indices. |
| `EngravingRules.GNote(note)` | Resolves each tested source note to a graphical note; `g.sourceNote === note` is verified. `GraphicalNote.FromNote(note,rules)` was reviewed as the same map lookup. |
| `g.ParentMusicPage.PageNumber`, `getSVGGElement()`, owning root SVG | Identifies the correct page and page-local coordinate space; all five pages are covered. |
| `g.PositionAndShape.AbsolutePosition` | Provides distinct note anchors inside a chord. Its `Size` can be zero, so it is not an individual head rectangle. |
| `Drawer.calculatePixelDistance(1)`, `Zoom` | Converts the anchor to renderer pixels without assuming a hardcoded ten-pixel unit. Root-SVG CSS scale and origin must also be applied. |
| `g.getNoteheadSVGs()` | Returns a **chord's head collection**, not necessarily one head for this source note. The prototype chooses a unique candidate whose center matches the graphical anchor within one CSS pixel; otherwise it must decline an individual-head result. |
| `g.setColor(color,options)` | An actual DOM probe on one note of a three-note chord colors **all three heads**, even with stem/modifier coloring disabled. It is not a single-pitch highlight API for that chord. |
| `getSVGId()`, `NoteToGraphicalNoteObjectId` | Renderer-local handles only. The bundled score has 2,049 source notes but only 1,344 SVG IDs; every SVG-ID observation changes after re-layout. Neither replaces persisted event IDs. |

No public imported MusicXML `<note id>` accessor was found on the source-note model. The explicit fixture IDs are not exposed in source-note string fields. `ReaderPluginManager` offers a voice-measure callback over voice entries, not a public raw-XML-node-to-created-note callback. `OnXMLRead` can preprocess XML but does not itself preserve arbitrary attributes on `Note`. A metadata-carrying reader hook would be preferable to monkey-patching constructors if additional disambiguation becomes necessary.

## Prototype mapping

```text
unchanged XML + existing Score.id/version/divisions
    ├─ written locator: sourceHash / partOrdinal / measureOrdinal / noteOrdinal
    └─ importer provenance: written locator → existing performed NoteEntity.id

supported structural join
    (part ID, measure ordinal, local staff, numeric voice, canonical tick,
     written duration, pitch) → unique OSMD source Note
        → EngravingRules.GNote → page + anchor + candidate SVG head rectangles
        → layout-scoped individual rectangle → existing performed ID
```

The written ordinal counts all XML `<note>` elements, including rests; rests do not receive performed IDs. Staff participates in locating a **written segment**, but does not separate **tie-event identity**. Consequently a cross-staff continuation maps to another visual location while retaining the attack's performed ID and hand.

The prototype refuses a structural bucket with multiple XML or OSMD candidates. It does not silently use occurrence order, nearest pitch, SVG ID or `NoteToGraphicalNoteObjectId` to assign a persisted ID. The production design should additionally retain original pitch spelling, grace/cue roles and any needed disambiguators. The current prototype uses integer pitch and deliberately handles only its verified subset.

### Written versus performed relationships

- A normal chord has multiple performed IDs, potentially one shared SVG group, and several distinct head rectangles.
- A tie chain has multiple written locators and head rectangles, mapping to **one** performed ID and one sustained falling note.
- Duplicate pitches in different numeric voices retain separate performed IDs and separate source objects. The tested engraving staggers their heads, so geometry is distinct in those fixtures; that is not a universal promise that simultaneous voices can never share a physical glyph.
- A visual click on a tie continuation must not create a new attack. Whether practice seeking should target the written position or the performed attack is an interaction decision; the mapping layer should expose both, without silently changing the existing seek/judgment behavior.
- The judgment engine currently uses required pitch sets. Separate simultaneous same-pitch IDs do not imply that hardware input can distinguish two presses of the same key. The prototype preserves that existing behavior.

## Fixture and layout matrix

All **12 fixtures** are tested in seven states (**84 fixture/layout combinations**): width 1000 / zoom 1, zoom 0.75, width 620 re-layout, CSS scale 0.8, A4 portrait pages / zoom 0.75, return to the original layout, and reload of the same XML. The same written-locator/performed-ID pairs must survive every state. Source-object references remain present through layout changes, then are replaced on reload. Returning to the original layout must reproduce group positions within one pixel.

| Fixture | Written / performed | Result |
|---|---:|---|
| Three-pitch chord | 3 / 3 | All mapped; three individually resolved heads; one shared SVG ID; color API affects all three. |
| Same pitch, two voices | 2 / 2 | All mapped; voice is necessary. |
| Cross-staff tie | 2 / 1 | Both mapped; one performed ID despite staff change. |
| Cross-staff chord | 2 / 2 | Both mapped to their actual staff/head geometry. |
| Same-pitch ties in two voices | 4 / 2 | Written/source mapping succeeds; same-order endpoints also produce the expected renderer tie groups. |
| Reversed voice tie endpoints | 4 / 2 | All mapped to correct performed IDs; **both OSMD tie chains cross into the wrong voice**. |
| Stop/start tie chain | 3 / 1 | Three written notes remain one performed event; source tie entries are inspected. |
| Numbered same-voice unison ties, reversed endpoints | 4 / 2 | All four source matches explicitly ambiguous; performed IDs are still preserved in provenance. |
| Untied same-voice simultaneous unisons | 2 / 2 | Both source matches explicitly ambiguous. |
| `upper` / `lower` voice strings | 2 / 2 | OSMD voice IDs are `NaN`; both matches explicitly unsupported. |
| Same pitch/voice in two parts | 2 / 2 | Part identity separates both notes. |
| `konekuto-claris.mxl` | 2049 / 1708 | All written notes and individual heads map in every state, including five SVG pages. |

The refused cases are successful safety checks, not missing notes silently discarded. Supporting them fully requires an additional reliable XML-to-source correspondence. Source note order or internal tie counters alone are insufficient guarantees.

### Tie-graph boundary

The bundled `VoiceGenerator.findCurrentNoteInTieDict` checks pitch/spelling (and fallback sounding pitch), without a voice comparison. Its open-tie dictionary is shared by staff. `TieNumber` is assigned by `getNextAvailableNumberForTie`; it is not read as the original MusicXML tied number. These are reviewed implementation facts, not assumptions that a number called “TieNumber” has the required identity meaning.

The actual reversed-endpoint fixture confirms the defect: voice 1's start at tick 0 is linked by OSMD to voice 2's stop at tick 16, and vice versa. All four source notes therefore expose mixed-voice tie chains. The sidecar correctly keeps performed IDs **0, 1, 1, 0** in written order. The same-order control fixture produces correct same-voice chains, showing why a benign sample can miss the problem.

The prototype does not derive event identity from OSMD `NoteTie`, and does not patch the renderer or musical semantics. Use source `NoteTie` / graphical tie APIs for drawing diagnostics only until the relationships required by the importer have been validated. Numbered/overlapping unison matching remains an explicit unsupported case for this structural join. The research assertion records this specific 2.2.1 limitation; a later library upgrade should revisit the expected discrepancy rather than preserve the bug. The local reproducer is committed; no upstream issue or external message was posted.

## Geometry and performance implications

The probe obtains real DOM head/group rectangles and checks that they lie inside their **own SVG**, not the first page's coordinate system. It combines the public graphical anchor with `calculatePixelDistance`, render zoom, SVG origin and CSS scale to select a head. All 2,049 heads of this score resolve uniquely in all seven states. This is verified for the tested standard piano glyphs, not arbitrary tablature, custom noteheads, transposition, hidden notes or rotated CSS transforms.

Prototype rectangles are page-local CSS pixels, including the tested CSS scale. Before integration with the existing unscaled PNG snapshots, export must normalize through the SVG/viewBox transform or inverse CSS scale into snapshot pixels, then apply the native view's pan/fit scale during drawing. Raw viewport rectangles must not be copied directly into a cached native overlay. The prototype tests lookup/geometry and draws research-only SVG overlays; it does not yet implement that native snapshot bridge.

The existing production snapshot bridge captures the first SVG and caches cursor steps. It does not yet carry the per-note/page metadata demonstrated here. Paginated identity lookup therefore works in the prototype but is **not integrated into WindScore's native sheet view**. Future integration must rasterize all required pages and retain each page's offsets when flattening them into chunks.

Collection work should occur once per load/layout generation. An early exploratory collector repeatedly queried all SVG roots and head DOM nodes and took roughly 0.45–0.51 s for the bundled score. Pre-indexing SVG roots and caching shared group geometry reduces the final observed collection to **96.7–168.9 ms** across the seven layout states (median about 118 ms). These are diagnostic single-run layout samples, not a controlled device benchmark; detailed final measurements are in `OSMD_NOTE_IDENTITY_RESULTS.json`. They exclude MusicXML parsing, OSMD layout, JSON/file transport, PNG encoding and native decoding. Debug exports repeat substantial data; a production projection should bridge only the minimal record fields.

Do not perform DOM traversal, tie reconstruction or full JSON bridging on every playback tick. Build source-note lookups once, then project/cache geometry after render. Playback/practice should use the existing event IDs and update overlay state against a small indexed rectangle table. Page/zoom/width/CSS changes invalidate geometry, not musical IDs. Use layout-generation tokens to discard stale asynchronous results. Re-render invalidates SVG handles; reload also invalidates source-object/WeakMap handles.

## Recommended minimal implementation design — not implemented

1. **Capture provenance in the existing parser pass.** Record written locators while reading notes; attach each locator to the existing `MutableNote` at allocation or tie merge. Return an optional separate provenance result. Do not change `NoteEntity.id`, ID allocation, `Score.CURRENT_VERSION`, CBOR event representation, historical attempts or original XML hash. Avoid maintaining the test prototype's second allocation algorithm in production.
2. **Bind source objects with verified structural joins.** Keep importer-owned original voice strings, pitch spelling and tie numbers in the sidecar. Use public OSMD source properties and `GNote`; retain explicit ambiguous/unsupported outcomes. Do not replace importer semantics with the OSMD tie graph. For indistinguishable candidates, prefer an upstream raw-note reader hook or an explicitly validated mapping extension over a silent ordering guess.
3. **Export minimal layout records.** Suggested fields are written locator, existing event ID, written tick/duration, page index, individual head rectangle and layout-generation token. Return a set of event IDs for genuinely shared visual geometry. Use a native Compose overlay over the existing raster snapshots for per-note feedback; avoid relying on whole-chord `setColor` for one pitch.
4. **Keep mapping and caches version-aware.** A sidecar must match source hash, parser version and divisions of the loaded score. Historical versions must never be relabeled as current IDs. Cache geometry separately under a schema version, renderer asset fingerprint, page layout, zoom/viewport and scale context. A missing or ambiguous map should preserve the existing cursor-only behavior. No cache format is changed in this research.
5. **Gate integration on further cases and runtime checks.** Validate representative transposition, grace/cue notes, enharmonic unisons, nonnumeric/omitted voices, hidden/generated content, repeat occurrences and custom head shapes. A repeat occurrence requires a playback-occurrence coordinate in addition to a written locator; this prototype does not unfold repeats. Validate the identity collector in Android WebView and JavaFX, not just a Chromium page, before shipping.

## Validation, limitations and reproduction

The opt-in browser test uses the bundled **2.2.1-release** in real Chromium, with remote requests blocked. Two ordinary JVM tests verify sidecar allocation and full-score ID preservation. All application production sources remain unchanged.

The first complete run executed 206 desktop tests and had one existing real-time hosted-practice timeout. Inspection found its synth recorder appending parallel note-off callbacks to an unsynchronized list, while reads expected `SessionLane` serialization. Commit `8190707` serializes those test recorder callbacks through the same lane. No assertions, time bounds, engine behavior or original six regression tests were changed. The race was real; attributing the single timeout exclusively to it is not independently proven.

After this test-harness fix, the complete suite passes **206 desktop tests and 111 Android host tests**, with no failures/errors/skips. The existing JavaFX rendering smoke test executes, but the new identity collector itself is verified in Chromium; neither that JavaFX smoke test nor Android host tests establish Android WebView identity runtime behavior. No new APK build or physical MIDI/audio test is required to validate these test-only files, and none is claimed.

Commands (with the existing cloud SDK/truststore/writable-home settings described in the prior validation report):

```sh
WINDSCORE_RENDER_VALIDATION=1 DISPLAY=:99 bash gradlew \
  :composeApp:desktopTest :composeApp:testAndroidHostTest --no-daemon --console=plain

# After the JVM test exports its verified sidecars:
node tools/prototypes/osmd_identity.cjs composeApp/build/validation/osmd-identity
```

Node, Playwright and Chromium are existing external test prerequisites. `WINDSCORE_CHROMIUM` and `WINDSCORE_PLAYWRIGHT` override their locations. Opt-in rendering requires a Linux display for the existing JavaFX test; the new identity page is headless Chromium.

Generated `fixtures.json`, per-fixture/per-layout JSON, `summary.json` and research-only overlay screenshots are under `composeApp/build/validation/osmd-identity/`. The generated data includes source version/hash/divisions; the committed compact result file records the tested runtime and summary. The initial probe also established that `load(string)` can treat a short XML string without an XML declaration as a URL. The prototype now uses the supported `load(Document)` API; it does not modify the production loader.

Commits: `8190707` fixes the test recorder synchronization; `0b4b707` adds the test-only prototype; `0a6b3aa` adds the reversed-voice tie reproducer. A separate documentation commit records this report and result data. This is a research recommendation, not approval or implementation of new production behavior.
