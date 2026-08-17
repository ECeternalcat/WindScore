<!-- 
[AI CLIENT DIRECTION]
- DEFAULT RESPONSE LANGUAGE: English only (for general analysis, code reviews, and log updates).
- EXCEPTION: You can output Chinese ONLY when explicitly commanded to perform localized translation tasks.
-->

# WindScore Worklog

## Phase 1 — Infrastructure / Parser / Piano Roll / State Machine (Completed)

> Corresponds to `Document_1.md` chapters 1–3 + the first 4 milestones.

### Step 1 — KMP + Compose Multiplatform project skeleton & dual-platform MIDI detection
- `composeApp` single module (commonMain / androidMain / desktopMain / jvmShared), version catalog, Kotlin DSL.
- Hardware abstraction layer via `expect/actual`: `MidiManager` interface exposes a `Flow<MidiEvent>`.
  - Desktop: `javax.sound.midi.MidiSystem`.
  - Android: `android.media.midi.MidiManager`, hand-written status-byte parsing.
  - **Pitfall 3.3**: CC (pedal) signals are hard-filtered; only NoteOn/NoteOff are recognized.
- Version stack (local cache, mutually compatible): Kotlin 2.3.10 + Compose MP 1.11.1 + **AGP 8.13.2 + Gradle 8.14.4**
  (the wrapper was originally Gradle 9.6.1, but AGP 8.x is incompatible with Gradle 9.6; AGP 9 breaks KMP integration, hence the downgrade).

### Step 2 — MusicXML dimensionality-reduction parser
- `MusicXmlParser` (jvmShared, JDK DOM + java.util.zip, no external dependencies).
- staff 1 → right hand / 2 → left hand; chords do not advance time; backup/forward advance the timeline correctly; rests only consume time.
- **Pitfall 3.1**: Tie (same-pitch slur) merging (all 341 ties in a real score merged correctly).
- Supports both ZIP (.mxl) and uncompressed .xml (detected by magic bytes); retains raw XML for sheet-music mode.

### Step 3 — Compose Canvas piano roll
- `PianoRoll` is pure `Canvas` (avoids recomposition storms); viewport clipping; `currentTickProvider` read during the draw phase.
- Left hand cool color / right hand warm color; the current group to be played is highlighted with a white border; bottom keyboard strip. **Pitfall 3.4**: keyWidth is divided evenly based on screen width.

### Step 4 — Judgment state machine (the soul of "wait mode")
- `JudgmentEngine` (commonMain, pure Kotlin): groups notes by startTick, advances instantly to the next group on correct hits; wrong notes are ignored.
- `freshKeys` handles tremolo/repeated notes; chords re-judge on every NoteOn, naturally tolerating timing differences (pitfall 3.2).
- `ScoreSession` (Compose wrapper) exposes currentTick/progress/pendingPitches, etc.

---

## Phase 2 — Productization & Experience Refinement (Completed)

> Corresponds to `Document_1.md` chapters 4–5.

### Step 1 — Software synthesizer (SoftSynth)
- `AudioSynth` interface; Desktop uses `javax Synthesizer`; Android uses **billthefarmer/mididriver v1.29** (local AAR, consumed via flatDir, 4-ABI native lib).
- Verification: Desktop produces real sound, noteOn API latency ~225μs.

### Step 2 — Single/both-hand separation & auto-hosting
- `PracticeMode { BOTH, RIGHT_ONLY, LEFT_ONLY }`; the hosted hand is auto-marked isHit +发声 via SoftSynth (noteOff delayed by tempo).
- `PianoRoll` gains `hostedTrack`; hosted track rendered at 0.3 opacity.

### Step 3a — Accuracy scoring + stalling heatmap
- Clean hits / total practice-track notes × 100; the process never shows crosses/reds; the result screen `ResultScreen` uses a gentle heatmap (cool green → warm amber).

### Step 3b — Local IO
- `expect @Composable ScoreFilePickerButton`: Desktop JFileChooser, Android SAF.

### Step 4 — OSMD sheet-music mode (experimental)
- `expect @Composable ScoreSheetView`: Android WebView + OSMD; currentTick advances drive the cursor.

---

## Enhancements & Fix Rounds (Latest)

### OSMD sheet-music mode fixes (Desktop now actually usable)
- **CDN path bug fixed**: the original `build/minimal/opensheetmusicdisplay.min.js` did not exist; the correct path is `/build/opensheetmusicdisplay.min.js` (pinned to 1.8.9).
- HTML/JS building moved to commonMain `OsmdHtml`, shared by both platforms; raw XML placed in a `type="text/xml"` script to avoid escaping; exposes `window.__advanceCursor(n)`.
- **Desktop now truly renders via JavaFX WebView** (no longer a placeholder): Compose `SwingPanel` → `JFXPanel` → JavaFX `WebView`.
  - Dependencies `org.openjfx:javafx-{base,graphics,controls,swing,media,web}:21.0.5:win`.
  - At runtime, `WebEngine` initialization was found to require `javafx-media` (missing it causes `NoClassDefFound: jfxmedia...PlayerStateListener`); added.
  - Gracefully degrades to a hint when JavaFX is unavailable; never crashes.
- Cursor sync: each currentTick advance calls `__advanceCursor(1)` (approximated per group, sufficient for auto-paging).

### Key-press visual feedback (prevent blind playing)
- `ScoreSession.heldPitches` exposed as Compose State (key changes are observable).
- `PianoRoll` gains `heldPitchesProvider`: highlights the keys of currently pressed pitches + a glowing block above the judgment line, so the elder can see what they pressed.

### MIDI multi-device fix (Code 10 bug)
- Symptom: Yamaha shows two entries in the Windows Device Manager, one of which is Code 10 (device cannot start). The old `firstOrNull` implementation picked the first device that could transmit; if the Code 10 device was first, it would open it and receive no signal, causing the app to appear unresponsive — the user had to disable that device. Web MIDI sites listen to all devices, so they were unaffected.
- Fix: `DesktopMidiManager.start()` now **opens all transmitting devices and merges them into one event flow**; devices whose `getMidiDevice` throws or whose `open()` fails (Code 10) are silently skipped via try/catch with diagnostic logging, while the actually-working devices are connected. Android was likewise changed to open all devices.
- Verification: with the Code 10 device enabled, the app connects to the P-Series normally and is playable.

---

## Verification status
- `:composeApp:desktopTest` —— **17 tests all PASSED** (synth real sound / parser incl. ties / engine all branches incl. isolation+scoring / end-to-end integration with a real score).
- `:composeApp:assembleDebug` → `composeApp-debug.apk` (~19MB, incl. mididriver native libs + WebView).
- `:composeApp:desktopJar` passed; `:composeApp:run` smoke test passed (JavaFX OSMD rendering, MIDI multi-device, key feedback).

## Enhancements & Fix Rounds (Latest)

### OSMD sync residue cleanup
- Removed `Score.cursorStepTicks` and the corresponding "raw note start tick" list in `MusicXmlParser`; OSMD now self-calibrates using its own timestamps, so this field is unused — removed to avoid leaving a misleading "per-group approximation" residue.

### Real-time advance of pure-hosted segments (eliminating bursts)
- `JudgmentEngine.step()` now returns proactively after processing a pure-hosted group instead of bursting through consecutive advances.
- Added `JudgmentEngine.advanceHosted()`: explicitly advances one pure-hosted group, scheduled by the upper layer to the tempo; user key presses no longer trigger pure-hosted group advances.
- `ScoreSession` gained a hosted-segment scheduling coroutine: it records the start tick of the previous hosted group and, after a `(currentTick - lastHostedTick) * msPerTick` delay, calls `advanceHosted()`, so consecutive left-hand/right-hand hosted segments sound at the real tempo and the UI advances group by group.
- Added unit test `consecutiveHostedGroupsDoNotBurst` to cover this behavior; updated integration tests `autoPlay` / `fumbledPlaythroughLowersScore` to call `advanceHosted()` explicitly.

### JavaFX cross-platform classifier
- `composeApp/build.gradle.kts` gained `javafxClassifier`: auto-selects `win` / `mac` / `mac-aarch64` / `linux` based on the build host OS, no longer hardcoding `:win`.

### OSMD offline bundling
- Bundled `opensheetmusicdisplay@1.8.9.min.js` (~1.17 MB) into:
  - `composeApp/src/desktopMain/resources/opensheetmusicdisplay.min.js`
  - `composeApp/src/androidMain/assets/opensheetmusicdisplay.min.js`
- `OsmdHtml.build()` now accepts Base64-encoded JS, injected via `data:application/javascript;base64,...`, no longer depending on the jsDelivr CDN.
- Desktop / Android `ScoreSheetView` read the JS from classpath / assets respectively, Base64-encode it, and pass it in.

## Verification status
- `:composeApp:desktopTest` —— **18 tests all PASSED** (added `consecutiveHostedGroupsDoNotBurst`).
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` both compile.

---

## Phase 3 — Advanced Features & Family Interaction (Completed)

> Corresponds to `Document_1.md` chapter 6.

### 6.1 Smart section loop practice (A/B Repeat)
- `JudgmentEngine` gained `setLoop(startTick, endTick)` / `clearLoop()`; setting a loop auto-resets to the loop start.
- When passing `loopEndTick`, `jumpToLoopStart()` runs: locates the loop-start group, clears activeKeys (forcing re-press), resets `isHit` within the looped segment, and recomputes `cleanHitCount` to prevent inflated scores.
- `ScoreSession` exposes `isLoopActive`, `loopRange`, `setLoop()`, `clearLoop()`.
- `App.kt` provides "loop start / loop end / clear loop" buttons: press start to record the pending tick, then end to activate the loop.
- Added unit tests `abLoopJumpsBackAtEnd`, `abLoopDoesNotInflateCleanHitCount`, `clearLoopRestoresFullPlaythrough`.

### 6.2 Fingering display
- `NoteEntity` already had `fingering: String?`.
- `MusicXmlParser.readFingering()` accepts both `<notations><fingering>` and `<notations><technical><fingering>`; tie merging preserves the fingering of the starting note.
- `PianoRoll` uses `rememberTextMeasurer()` to draw white bold fingering digits at the center of each note block, font size 10sp~36sp adaptive to keyWidth.
- Added unit test `fingeringIsExtractedFromTechnicalNotation`.

### 6.3 Decentralized remote score pushing
- Remains shelved (the doc explicitly lists it as a long-term outlook).

### 6.4 Dynamic beat pulse (Visual Pulse)
- `ScoreSession` maintains a `pulseIntensity` Compose State: set to 1.0 on each progress advance, then decays by 0.97 every 50ms, floor 0.15.
- `PianoRoll` receives state via `pulseIntensityProvider` / `bpmProvider`.
- `drawBackground()` draws a horizontal light-blue band whose alpha is modulated by `sin(phase * π) * pulseIntensity`, flashing to the BPM; when stalled the intensity dims.

## Verification status
- `:composeApp:desktopTest` —— **21 tests all PASSED** (added 3 A/B loop + 1 fingering test).
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` both compile.
- `:composeApp:assembleDebug` succeeded.

---

## Beta UI overhaul (based on Document/DESIGN.md)

For beta testing, the UI was comprehensively rebuilt following the Figma marketing-page design system:

### Design-system theme
- Added `com.windscore.ui.theme.WindScoreTheme` / `WindScoreColors` / `WindScoreSpacing` / `WindScoreRounded`.
- Colors: black-and-white editorial base (primary/canvas/ink) + pastel blocks (block-lime/lilac/cream/pink/mint/coral/navy).
- Typography: Material Typography customized per DESIGN.md's display/body/button/mono sizes, weights, and letter-spacing; system sans-serif + monospace fallback.
- Corner radius: pill (50dp) for all buttons, lg (24dp) for block sections.

### Global components
- Added `PillButton` (PRIMARY / SECONDARY surfaces), unifying all CTAs as capsule-shaped.
- `ScoreFilePickerButton` gained a `style: PillButtonStyle` parameter; Desktop / Android actuals both now use `PillButton`.

### App.kt refactor
- Top bar: 56dp white background with black text; brand name + subtitle on the left; a pill button group on the right (parse score / open file / reset / MIDI toggle).
- Control bar: both/right/left hand modes as a pill toggle; roll/sheet switch as a pill toggle; A/B loop controls arranged compactly on one row.
- Main stage: a large `blockLime` block section (rounded lg + 48dp padding) hosting `PianoRoll` or `ScoreSheetView`.
- Empty state: centered large Hero text + subtitle + dual pill CTAs.

### PianoRoll color migration
- Background changed from dark to a white/soft gradient.
- Left-hand notes → `blockMint`, right-hand notes → `blockCoral`; hits are high-saturation, misses are muted pastel.
- Keyboard area changed to white keys + black keys + hairline separators; press feedback is now a soft `accentMagenta` glow.
- Beat-pulse band changed to `blockLilac`.

### ResultScreen color migration
- Changed from a dark translucent card to a large rounded `blockCream` block card.
- The large score uses display-style weight with negative letter-spacing.
- Buttons changed to `PillButton` PRIMARY.

## Verification status
- `:composeApp:desktopTest` —— **21 tests all PASSED**.
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` both compile.

## Known leftovers
- None currently.

---

## Desktop/Mobile dual-layout refactor (Native App Layout)

Redesigned the overall navigation and page structure as "Windows/Android software", no longer copying the Figma marketing page's long-page layout:

### ScoreSession crash fix
- Fixed `NullPointerException: Cannot invoke "State.getValue()"` when clicking "parse score / open file".
- Root cause: the `by` delegation of `var xxx by mutableStateOf(...)` in `ScoreSession` could, during `init { sync() }`, occasionally hit a delegate field that wasn't ready.
- Fix: changed `currentTick` / `progress` / `finished` / `heldPitches` / `pulseIntensity` to explicit `MutableState` + custom getter/setter; `sync()` is only called in `init` after the properties are initialized.

### Score metadata
- `Score` gained a `title: String` field; `MusicXmlParser` parses `<work-title>` / `<movement-title>` / `<credit-words>`, falling back to "未命名乐谱" (untitled) when absent.
- Used by the desktop welcome page and the Android score-library list.

### Desktop welcome page (Photoshop-style)
- `App(isCompact = false)` enters the welcome page:
  - A minimal top brand bar (WindScore / 钢琴伴学 = piano study companion).
  - A large left card: `blockLime` block, a centered white square + `plus_thin` icon, an "add score" title below it, then descriptive text + an "open file" pill button.
  - A right "recently opened scores" panel: a `surfaceSoft` card; list items use the `music_notes_thin` icon + title/note-count/BPM.
- Clicking a sample/file enters the play page; returning goes back to the welcome page.

### Android score-library list + forced landscape
- `MainActivity` sets `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE` before `setContent`.
- `App(isCompact = true)` enters the score-library list:
  - Top "sample score" / "open file" pill buttons.
  - A `surfaceSoft` list area below showing recently opened scores; tapping enters the play page (shares `PlayerScreen` with desktop).

### Unified play-page structure
- Top toolbar: back (`arrow_left_thin`), score title, reset (`arrows_counter_clockwise_thin`), MIDI start/stop pill.
- Control bar: both/right/left hand pill toggle; sheet/roll round icon buttons (`piano_keys_thin` / `book_open_text_thin`); A/B loop start/end/clear round icon buttons (`repeat_thin` / `x_thin`).
- Main stage: a large `blockLime` block hosting `PianoRoll` or `ScoreSheetView`; in sheet mode there are play/pause/stop round icon buttons (`play_thin` / `pause_thin` / `stop_thin`).
- Any marquee/scrolling effects have been removed.

### Icon resources
- Added the Compose Multiplatform Resources dependency `compose.components.resources`.
- Copied Phosphor icons from `icons/SVGs Flat/thin/` to `composeApp/src/commonMain/composeResources/drawable/` (filenames snake_cased):
  - `plus_thin`, `clock_thin`, `music_notes_thin`, `play_thin`, `pause_thin`, `stop_thin`, `arrow_left_thin`, `arrows_counter_clockwise_thin`, `repeat_thin`, `piano_keys_thin`, `book_open_text_thin`, `x_thin`, `list_thin`.
- Referenced uniformly in commonMain via `windscore.composeapp.generated.resources.Res` and `painterResource(...)`.

## Verification status
- `:composeApp:desktopTest` —— **21 tests all PASSED**.
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` both compile.
- `:composeApp:assembleDebug` produced an APK.
- `:composeApp:run` desktop run starts without crashing.

## Known leftovers
- None currently.

---

## OSMD sheet-music mode corrections

### Cursor-not-moving fix
- Rewrote `OsmdHtml.buildCursorTimes()`: instead of mapping each step of the underlying iterator to a cursor `next()`, it directly calls `osmd.cursor.next()` and records the whole-note timestamp of each **actual cursor position**.
- `__syncCursor(posWhole)` now advances only by these real positions, avoiding the previous stall/jump caused by "iterator step count ≠ cursor step count".
- `ScoreSheetView` (Desktop / Android) calls `__resetCursor()` on tick wrap-around / loop / reset so the cursor can return to the start.
- The sheet-mode cursor now follows both:
  - playback progress from the `ScorePlayer` triggered by the play button;
  - the manual advance of `ScoreSession.currentTick` in practice mode.

### Displaying one/two full pages
- `ScoreSheetView` gained a `pages: Int` parameter (1 or 2).
- OSMD's `autoResize` is disabled, using `pageFormat: 'A4_P'`; after load, `zoom` is computed from the container width:
  - `pages=1`: fits the A4 page width fully into the container (one page fills the width).
  - `pages=2`: fits twice the page width into the container (both pages visible at once).
- A HUD in the top-right shows the current zoom factor and render time.

### Play/stop buttons moved out of the score area
- `App.kt`'s `PlayerControlBar` gained play/pause/stop round icon buttons (`play_thin` / `pause_thin` / `stop_thin`).
- `MainStage` removed the play-control row previously embedded in the score area; the score area now shows only the score.

## Verification status
- `:composeApp:desktopTest` —— **21 tests all PASSED**.
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` passed.
- `:composeApp:run` desktop run; OSMD diagnostics output `len=987` cursor positions; rendering and cursor initialization normal.

## Known leftovers
- None currently.

---

## Second Code Audit Fixes (2026-07-14)

Addressed the second code audit (`Document/Audit_20260714.md`): fixed **9 of 13** newly-found issues plus the **1 carried-over issue (#10)** from the first audit. The audit report file has been converted into a handoff doc listing only the 3 remaining items.

### Critical (S1, S2)
- **S1 — `DisposableEffect(session)` closure capture** (`App.kt`): the `onDispose` block read the delegated `session` value, which by dispose time already pointed to the *new* session, so old sessions were never `close()`d (coroutine leak on every practice-mode switch). Fixed by capturing `val currentSession = session` in the setup block.
- **S2 — `loadRecentScores` blocking the main thread** (`App.kt`): the IO-heavy file scan + per-score XML parse ran on the `Main` dispatcher inside `LaunchedEffect`. Wrapped in `withContext(Dispatchers.IO)`.

### Medium (M1, M2, M3, M5)
- **M1 — multi-Part `maxTick` overwrite** (`MusicXmlParser.kt`, also first-audit #10): `maxTick` was reset to 0 per part, so multi-Part scores (quartets, full scores) only kept the last part's duration. Introduced `globalMaxTick`, accumulated via `maxOf(globalMaxTick, maxTick)` after each part; `Score.totalTicks` now uses it. Added regression test `multiPartTotalTicksReflectsLongestPart`.
- **M2 — `saveRecentScores` blocking the main thread** (`App.kt`): the three synchronous file-write call sites (`openScore` / `onRename` / `onDelete`) moved to `scope.launch(Dispatchers.IO)`.
- **M3 — metronome click pitch conflicts with score notes** (`ScorePlayer.kt` + `AudioSynth`): clicks used real pitches 96/108 (C7/C8), which sit inside the piano range and could be cut off by — or cut off — score notes of the same pitch. Added channel-aware `noteOn(channel, pitch, velocity)` / `noteOff(channel, pitch)` to the `AudioSynth` interface (old no-channel overloads kept as defaults delegating to channel 0). Implemented in `DesktopSynth` and `AndroidSynth`. Metronome now plays on GM percussion channel 9 using Woodblock timbre (76 = high on downbeats / 77 = low otherwise).
- **M5 — incomplete JSON escaping** (was `StorageProvider.kt`): only `"` was escaped; a title containing `\n`, `\t`, or `\\` would corrupt `recents.json` and lose all recent scores on next launch. Fixed with full escaping + a hand-written JSON string parser (the old `[^\"]+` regex truncated on escaped quotes).

### Minor (L1, L2, L4, L5)
- **L1 — non-standard `@Volatile`** (`NoteEntity.kt`): swapped `kotlin.jvm.Volatile` (JVM-only) for the standard KMP annotation `kotlin.concurrent.Volatile` (available in Kotlin 2.3.10).
- **L2 — duplicate imports** (`WelcomeScreen.kt`): removed the duplicated `fillMaxWidth` and `clickable` imports.
- **L4 — keep-awake Robot disturbing user input** (`Main.kt`): the 30s timer simulated **Shift**, which modifies text input in other apps. Switched to `VK_F15` (the standard no-op keep-awake key, not bound by any app) and changed the initial delay from `0` to `30000` so it no longer fires immediately on startup. (True `SetThreadExecutionState` would need JNA/JNI; not viable on the JVM 11 toolchain with no JNA dependency.)
- **L5 — duplicated Desktop/Android storage logic** (handled alongside M5): extracted the shared `save`/`load` JSON + XML-landing logic into `jvmShared/.../core/RecentsStorage.kt`. Both `actual fun saveRecentScores` / `loadRecentScores` now delegate to it; only `getAppStorageDir()` / `calculateMd5()` remain platform-specific.

### Files touched
- `composeApp/src/commonMain/kotlin/com/windscore/App.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/audio/AudioSynth.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/core/engine/ScorePlayer.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/core/model/NoteEntity.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/ui/WelcomeScreen.kt`
- `composeApp/src/jvmShared/kotlin/com/windscore/core/parser/MusicXmlParser.kt`
- `composeApp/src/jvmShared/kotlin/com/windscore/core/RecentsStorage.kt` (new)
- `composeApp/src/desktopMain/kotlin/com/windscore/audio/DesktopSynth.kt`
- `composeApp/src/desktopMain/kotlin/com/windscore/core/StorageProvider.kt`
- `composeApp/src/desktopMain/kotlin/com/windscore/Main.kt`
- `composeApp/src/androidMain/kotlin/com/windscore/audio/AndroidSynth.kt`
- `composeApp/src/androidMain/kotlin/com/windscore/core/StorageProvider.kt`
- `composeApp/src/desktopTest/kotlin/com/windscore/core/parser/MusicXmlParserTest.kt` (new test)
- `Document/Audit_20260714.md` (converted to handoff doc)

## Verification status
- `:composeApp:desktopTest` —— **26 tests all PASSED** (added `multiPartTotalTicksReflectsLongestPart`; prior run was 25).
- `:composeApp:compileKotlinDesktop` (`--rerun-tasks`) and `:composeApp:compileDebugKotlinAndroid` both pass.

## Audit Follow-up — Playback Speed, CBOR Cache & UI Refactoring (Completed)

> Addresses the remaining items (M4, L3, L6) from the July 14, 2026 audit. (Originally mis-labelled "Phase 3", which collides with the foundational Phase 3 above.)

### Step 1 — Real-time Playback Speed Control (M4)
- Unlocked playback speed adjustments during active playback. Users can drag the speed slider or click presets (`x0.5`, `原速`, `x1.5`) in real-time, and the playing loop/synthesizer adapts instantly.

### Step 2 — CBOR Serialization Cache & File Fingerprints (L6)
- Applied Kotlin serialization plugin (`alias(libs.plugins.kotlin.serialization)`) to root and subproject configuration.
- Annotated `Score`, `NoteEntity`, and `TrackType` with `@Serializable`.
- Added `Fingerprint(size, lastModified)` checks. Parsed scores are serialized and deserialized using `kotlinx-serialization-cbor` to `<xmlHash>.cache` binary files.
- If the file fingerprint matches the backup XML on disk and cache version matches `CURRENT_VERSION`, the app loads the score directly without XML parsing, achieving near-instantaneous startup.
- Implemented lazy loading in `App.kt` `openScore` so the XML content (`rawXml`) is resolved from local storage only when the user opens the score for practice, keeping the recent scores list memory footprint minimal.

### Step 3 — PlayerScreen.kt Component Refactoring (L3)
- Extracted common UI components (`CustomSwitch`, `PillToggle`, `CircularIconButton`, `pitchName`) into `com.windscore.ui.components.CustomComponents.kt`.
- Extracted the control panel layout (~300 lines of UI) into `com.windscore.ui.ControlSidebar.kt`.
- Simplified `PlayerScreen.kt` imports and layout code, reducing its size to under 800 lines.

### Files touched
- `gradle/libs.versions.toml`
- `build.gradle.kts`
- `composeApp/build.gradle.kts`
- `composeApp/src/commonMain/kotlin/com/windscore/core/StorageProvider.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/core/model/Score.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/core/model/NoteEntity.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/core/model/TrackType.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/ui/components/CustomComponents.kt` (new)
- `composeApp/src/commonMain/kotlin/com/windscore/ui/ControlSidebar.kt` (new)
- `composeApp/src/commonMain/kotlin/com/windscore/ui/PlayerScreen.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/App.kt`
- `composeApp/src/desktopMain/kotlin/com/windscore/core/StorageProvider.kt`
- `composeApp/src/desktopMain/kotlin/com/windscore/ui/ScoreSheetView.kt`
- `composeApp/src/androidMain/kotlin/com/windscore/core/StorageProvider.kt`
- `composeApp/src/jvmShared/kotlin/com/windscore/core/parser/MusicXmlParser.kt`
- `composeApp/src/jvmShared/kotlin/com/windscore/core/RecentsStorage.kt`

### Verification status
- `:composeApp:desktopTest` —— **23 tests all PASSED**.
- `:composeApp:compileKotlinDesktop` and `:composeApp:compileDebugKotlinAndroid` both compile successfully without errors or warnings.

## Phase 4 — Per-Score Personal Settings Persistence (Completed)

> Implements persistent settings (practice mode, view mode, playback speed, metronome, countdown, loop range, and playback progress) mapped to each score's unique hash.

### Step 1 — Defined ScoreSettings Data Model
- Created `ScoreSettings` model class in `com.windscore.core.model` mapping user settings and playback progress.
- Marked `ViewMode` and `PracticeMode` enums as `@Serializable` to allow clean serialization.

### Step 2 — Storage layer expect/actual declarations
- Added `loadScoreSettings` and `saveScoreSettings` expect/actual functions to `StorageProvider.kt` for common, desktop, and android modules.
- Created `SettingsStorage.kt` in `jvmShared` to handle reading/writing settings as JSON files under `<appStorageDir>/settings/<scoreId>.json`.

### Step 3 — State hoisting & Auto-save synchronization
- Hoisted `metronomeVol`, `countdownEnabled`, and `playbackSpeed` to `App.kt` for unified state management and binding with the MIDI player.
- Restored prior settings (including loop range and current tick seek) upon opening scores in `openScore`.
- Avoided reloading settings when just reconstructing sessions (e.g. on practice mode changes) by passing a `restoreSettings: Boolean = false` flag.
- Configured a Compose `snapshotFlow` with a `debounce(500ms)` block to write configurations to disk automatically only when `isPlaying == false` to prevent high-frequency write operations during active playback.
- Forced a synchronous settings save when returning to the welcome screen (`closeScore`).

### Files touched
- `composeApp/src/commonMain/kotlin/com/windscore/App.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/core/StorageProvider.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/core/model/ScoreSettings.kt` (new)
- `composeApp/src/commonMain/kotlin/com/windscore/core/model/PracticeMode.kt`
- `composeApp/src/jvmShared/kotlin/com/windscore/core/SettingsStorage.kt` (new)
- `composeApp/src/desktopMain/kotlin/com/windscore/core/StorageProvider.kt`
- `composeApp/src/androidMain/kotlin/com/windscore/core/StorageProvider.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/ui/PlayerScreen.kt`
- `composeApp/src/commonMain/kotlin/com/windscore/ui/ControlSidebar.kt`

### Verification status
- `:composeApp:desktopTest` —— **23 tests all PASSED**.
- `:composeApp:compileKotlinDesktop` and `:composeApp:compileDebugKotlinAndroid` compile successfully with no errors.

## Phase 5: Interactive Loop Selection, Tabs Layout, and Scoring Mode Restrictions
- **Interactive Loop Selection**:
  - Implemented a state machine in [App.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt) (`LoopSetupState`) to manage loop point selection step-by-step.
  - Intercepted score seeking clicks during active loop setup to handle loop points rather than playhead jumps.
  - Added step-by-step guidance toasts with yellow cursor drawing support across both [NativeSheetView.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/NativeSheetView.kt) and [PianoRoll.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/PianoRoll.kt).
  - Configured custom delays (500ms pause on last note, 500ms wait after snapback) inside [ScorePlayer.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/engine/ScorePlayer.kt) for looping.
- **Sidebar Tab Layout**:
  - Replaced the bottom "重新加载" (Reload) button in the right sidebar with two animated page tab buttons ("练习" and "评分").
  - Wrapped right sidebar panels inside a Compose `Crossfade` to switch smoothly between the practice controls and scoring panels.
  - Added custom icons (`music_notes_thin` for Practice, `pencil_simple_thin` for Scoring) to the page tab buttons and the scoring placeholder screen.
  - Preserved all original control panels, layouts, view options, and metronome/countdown toggles exactly under the "练习" tab.

### State Flag: `SidebarTab.SCORING`
- **Definition**: The state variable `sidebarTab` of enum type `SidebarTab` is defined in [PlayerScreen.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/PlayerScreen.kt) to keep track of the active sidebar view (`PRACTICE` or `SCORING`).
- **Applied Restrictions**:
  - **Playback Disallowance**: When `sidebarTab == SidebarTab.SCORING`, pressing the spacebar does not start/pause playback.
  - **User Alert**: Any attempt to play via spacebar in scoring mode triggers a toast message: `"抱歉，评分模式不允许播放乐谱"`.
  - **Developer Note**: Use the `sidebarTab == SidebarTab.SCORING` check inside [PlayerScreen.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/PlayerScreen.kt) or propagate it to apply any further restrictions/disallowed features (e.g., keyboard shortcuts, seeking, adjustments) in scoring mode.

### Bug Fixes
- **Scoring Tab Button Text Clipping**:
  - **Bug**: In English mode, the bottom portion of the letter 'g' in the "Scoring" tab button text was clipped/cut off.
  - **Cause**: M3 `Button` defaults to a larger vertical content padding which, when constrained to a fixed `40.dp` height, left insufficient room for descending letter glyphs.
  - **Solution**: Explicitly set a smaller vertical content padding (`contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)`) for both the "Practice" and "Scoring" bottom tab buttons inside [PlayerScreen.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/PlayerScreen.kt), providing sufficient vertical space to prevent text clipping.
- **Audio Synthesizer Lag & Note Blasting**:
  - **Bug**: Sometimes clicking play right after loading froze/lagged for 1+ seconds with the cursor moving silently, followed by a blast of all notes at once when the audio output resumed.
  - **Cause**: Starting the synthesizer (audio thread/device init) occurred synchronously when play was triggered, blocking either the UI/player thread, and generating large elapsed time deltas `dt` which forced the playback loop to jump ahead and play all missed notes instantly.
  - **Solution**: (1) Pre-start (warm up) the synthesizer asynchronously in [App.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt) as soon as `player` is initialized (on score load) using `scope.launch(Dispatchers.Default)`. (2) Capped the time step delta `dt` in [ScorePlayer.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/engine/ScorePlayer.kt) to a maximum of `0.1` seconds, preventing progress skipping or note blasting during any system lag.
- **Loop Settings Serialization Issue**:
  - **Bug**: When a loop was deleted, exiting the score and opening it again caused a yellow cursor representing the loop to appear at the beginning of the sheet (tick `0L..totalTicks`), and clicks on the score prompted the user to turn off loop.
  - **Cause**: The serialization settings fallback logic in `LaunchedEffect` and `closeScore()` used `pendingLoopStart.takeIf { !activeSession.isLoopActive } ?: activeSession.loopRange?.first` which evaluated to `0L` when loop was inactive (since `loopRange` returns `0L..totalTicks` by default when inactive).
  - **Solution**: Updated loop settings persistence inside [App.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt) to strictly save `loopStartTick = if (activeSession.isLoopActive) activeSession.loopRange.first else pendingLoopStart` and `loopEndTick = if (activeSession.isLoopActive) activeSession.loopRange.last else null`, preventing any default fallback to `0L` and `totalTicks` when the loop is inactive.

- **Elegant Launch Splash Screen Mask**:
  - Implemented a theme-aware startup Splash screen in [App.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt) that instantly matches the active light or dark theme background (`WindScoreColors.canvas`).
  - Added a centered classic piano keys icon (`piano_keys_thin`) to mask all underlying JRE/AWT startup window state shifts, layout initialization, and MIDI manager loading processes.
  - Automatically transitions to the main `WelcomeScreen` using a smooth 400ms fade-out animation after exactly 1 second.
## Phase 6: Global Application Settings & UI Dark Theme
- **AppConfig Data Structure**:
  - Defined serializable `AppConfig` class in [AppConfig.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/model/AppConfig.kt) supporting UI Night Mode (Follow System, Light, Dark), Default View Mode (Piano Roll, Sheet Keyboard, Sheet Full), and Default Pre-beat Countdown seconds.
  - Implemented thread-safe `ConfigStorage` JSON serialization helper in [ConfigStorage.kt](file:///c:/A1/Code/WindScore/composeApp/src/jvmShared/kotlin/com/windscore/core/ConfigStorage.kt) to load/save `app_config.json` inside the platform application storage directory.
- **Compile-Compatible Dynamic Colors Architecture**:
  - **Issue**: `WindScoreColors` properties were defined as static constants, bypassing the Compose theme. Refactoring them to `@Composable` properties caused compilation errors because they were read inside non-composable lambdas (like `Canvas { ... }` drawing blocks in `PianoRoll.kt` and `StaticKeyboard.kt`).
  - **Solution**: Defined a global `var isDarkThemeGlobal by mutableStateOf(false)` state tracker. Kept `WindScoreColors` properties as regular Kotlin getters (non-composable) that read `isDarkThemeGlobal`, triggering recomposition in Composable contexts and redraw invalidation in drawing contexts. Updates `isDarkThemeGlobal` inside `WindScoreTheme` using a `SideEffect`.
- **Theme Mode & System Dark Mode Tracking**:
  - Leveraged Compose's `isSystemInDarkTheme()` to detect the OS system dark state on Android and Desktop.
  - Wired `appConfig` state dynamically to `WindScoreTheme(darkTheme = isDarkTheme)` inside [App.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt), enabling instant dark theme switching.
- **Global Settings Fallback**:
  - Updated [SettingsStorage.kt](file:///c:/A1/Code/WindScore/composeApp/src/jvmShared/kotlin/com/windscore/core/SettingsStorage.kt) to check global config defaults (default view mode, pre-beat enable status) when initializing settings for a brand-new score.
  - Updated countdown pre-beat playback loops in [PlayerScreen.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/PlayerScreen.kt) to use `appConfig.defaultCountdownSeconds` dynamically.
- **Consistent Platform Settings Dialog & Context Theme Wrapping**:
  - **Issue**: Clicking the settings button on the Welcome screen still showed the inline full-screen view on desktop instead of a standalone dialog window. In addition, opening the settings dialog from the player screen flashed the default light theme momentarily because JVM `DialogWindow` creates a separate window context root that bypasses the parent's `WindScoreTheme`.
  - **Solution**: Wrapped the Welcome screen's settings button handler to call the platform-aware `SettingsDialog` instead of inline rendering. Wrapped the standalone settings dialog content in both [WelcomeScreen.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/WelcomeScreen.kt) and [App.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt) inside `WindScoreTheme(darkTheme = isDarkTheme)`, ensuring correct theme propagation and instant dark mode rendering in separate Swing windows.
- **UI Settings Panel**:
  - Expanded [SettingsScreen](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/WelcomeScreen.kt) with card selections for UI Theme, Default View Mode, and Default Pre-beat seconds (0s - 5s).
  - Used scrollable Column layout to fit and display nicely on both desktop dialog windows and mobile screens.

## Phase 7: Concise UI Animations System
- **Bounce Click Micro-Animation**:
  - Implemented `Modifier.bounceClick` extension in [ModifierExtensions.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/components/ModifierExtensions.kt) using Compose spring physics.
  - Applied the bounce click scale-bounce animation to all circular icon buttons, custom switches, and pill button/toggles inside [CustomComponents.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/components/CustomComponents.kt) and [PillButtons.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/components/PillButtons.kt).
- **Smooth MainStage View Switcher**:
  - Replaced the abrupt alpha visibility logic in [PlayerScreen.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/PlayerScreen.kt) with a smooth `Crossfade` animation between `PianoRoll` and `NativeSheetView`.
  - Shared `sheetSnapshot` across view mode selections to prevent restarting the off-screen WebView renderer when switching modes.
  - Added a smooth keyboard alpha fade transition in [PlayerScreen.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/PlayerScreen.kt) using `animateFloatAsState`.
- **Platform-Aware Settings Window**:
  - Declared `expect fun SettingsDialog` in [SettingsDialog.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/SettingsDialog.kt).
  - In [desktopMain](file:///c:/A1/Code/WindScore/composeApp/src/desktopMain/kotlin/com/windscore/ui/SettingsDialog.kt), implemented settings as a standalone JVM OS window (`DialogWindow`) with a fixed resolution (`480.dp` x `560.dp`), styling, and window decoration.
  - In [androidMain](file:///c:/A1/Code/WindScore/composeApp/src/androidMain/kotlin/com/windscore/ui/SettingsDialog.kt), kept the settings as a full-screen sliding sheet using Compose mobile slide animations.
- **Welcome to Player Screen Transitions**:
  - Wrapped the screen switching logic in [App.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt) inside a `Crossfade` animation.
  - Toggling between `WelcomeScreen` and `PlayerScreen` now fades smoothly instead of instantly jumping.
- **Slide Transitions**:
  - Animated Toast notification message boxes in [PlayerScreen.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/PlayerScreen.kt) to slide up and down dynamically using `AnimatedVisibility`.
  - Added slide-in-from-bottom and slide-out-to-bottom transitions for the settings drawer screen in [App.kt](file:///C:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt) using `AnimatedVisibility`.

---

## Phase 8 — Scoring System (Completed)

> Implements the "test yourself" capstone: a dedicated scoring flow in the sidebar Scoring tab (previously a placeholder). Reuses `JudgmentEngine`'s pure-accuracy scoring (clean hits / total active notes × 100, time-independent).

### Flow — `ScoringPhase { SETUP, ACTIVE, RESULT }` state machine
- **SETUP** (`ScoringSetupPanel`): pick scoring range (both/right/left hand → maps to `PracticeMode`) and scoring view; no speed control (accuracy-only). "开始评分" button at the bottom.
- **ACTIVE** (`ScoringActivePanel`): on start, a fresh `ScoreSession` is created from the beginning, MIDI auto-starts if not running, and MIDI events route to the scoring session (`scoringSession ?: session`). Sidebar shows "请开始演奏" + neutral progress; the bottom 练习/评分 tab buttons are **grayed out**; seek/step interaction on the main stage is disabled. Live play drives the cursor; single-hand modes auto-host the other hand (accompaniment, not scored).
- **RESULT** (`ScoringResultOverlay`): when all notes are played the session finishes and auto-transitions (after a 1 s delay) to a centered overlay on the main stage showing the final `%` + clean/wrong counts + "回到练习", which tears down the scoring session and returns to the practice tab.

### Key files
- `ui/ScoringPanel.kt` (new) — `ScoringSetupPanel` / `ScoringActivePanel` / `ScoringDonePanel` / `ScoringResultOverlay`.
- `ui/PlayerScreen.kt` — `ScoringPhase` enum, params, sidebar `Crossfade`, tab graying, result overlay, interaction lock during scoring.
- `App.kt` — `scoringSession`/`scoringPhase` state, `startScoring`/`exitScoring`, MIDI routing to the scoring session, auto-finish observer, guarding the practice-sync effect during scoring.

## Phase 9 — Localization (EN / ZH / JA) (Completed)

> Decouples all hardcoded Chinese UI strings and adds an in-app language switcher with three locales.

### i18n system — `ui/i18n/UiStrings.kt`
- `AppLanguage` enum (EN/ZH/JA with `code` + native display name) and a global Compose-observable `AppStrings.language` state; `@Composable fun strings()` reads it and triggers recomposition on switch.
- `UiStrings` class with ~110 type-safe properties (lambdas for interpolated strings, e.g. `finishedAccuracy(pct)`); three complete tables `ZhStrings` / `EnStrings` / `JaStrings` — the constructor enforces that every locale supplies every key.
### Persistence
- `expect fun loadAppLanguage/saveAppLanguage` → `jvmShared/.../LangStorage.kt` (`lang.txt` in app storage). Loaded on startup (default `zh`), saved on change via `Dispatchers.IO`.
### Settings UI
- Language card added to `SettingsScreen` (three pill toggles labelled in each language's native name); threaded through `WelcomeScreen` and `App.kt`.
### Decoupling
- Replaced every user-facing Chinese string in `WelcomeScreen`, `PlayerScreen`, `ControlSidebar`, `ScoringPanel`, `ResultScreen`, `App.kt` (toasts), and the desktop/Android file-picker buttons + dialog titles.
- Title fallback `"未命名乐谱"` removed from the parser/`Score` default → UI shows a localized "Untitled" via `ifBlank`.
- **Known limitation**: `OsmdHtml.kt` still embeds Chinese diagnostic HUD strings in the generated JS (transient render status); localizing requires parameterizing the JS template — deferred.

## Phase 10 — Scoring History (Completed)

> Makes scoring results persistent and reviewable, closing the practice → score → see-progress loop. Each finished scoring run is recorded per score and browsable in a dedicated window.

### Data & storage
- `ScoreRecord` model (`@Serializable`: timestamp, percent, cleanHits, totalNotes, wrongPresses, practiceMode).
- `jvmShared/.../ScoreHistoryStorage.kt` — `kotlinx-serialization-json`, keyed by `history/<scoreId>.json`, newest-first, capped at 50.
- `DateTimeUtil` + `expect/actual nowEpochMillis()/formatDateTime()` for wall-clock timestamps and `yyyy/MM/dd HH:mm` formatting.
### UI
- In `ScoringSetupPanel`, a "评分历史" entry below the start button shows best `%` + attempt count (or the empty hint); clicking opens a **separate window**.
- `SettingsDialog` generalized with a `title` parameter (reused for the history window — `DialogWindow` on desktop, full-screen sheet on Android).
- `ScoreHistoryContent`: list where each record shows date/time + mode tag (top) and a large score (body); clicking an item expands to reveal 正确 (clean/total) / 错误 (wrong) / 总数 details.
### Wiring
- History loaded in `openScore`; a `ScoreRecord` is appended right after a scoring run finishes (after the 1 s result delay) and the in-memory list refreshed.
### Key files
- new: `core/model/ScoreRecord.kt`, `jvmShared/.../DateTimeUtil.kt`, `jvmShared/.../ScoreHistoryStorage.kt`, `ui/ScoreHistoryContent.kt`
- modified: `core/StorageProvider.kt` (common/desktop/android), `ui/SettingsDialog.kt` (common/desktop/android — `title`), `ui/i18n/UiStrings.kt`, `ui/ScoringPanel.kt`, `ui/PlayerScreen.kt`, `App.kt`

## Phase 11 — Dark-Theme Score Colors & Dedup (Completed)

> Migrates the hardcoded accuracy score colors to the theme system so result/history screens adapt to dark mode, and removes the triple duplication.

- Added `WindScoreColors.scoreClean` / `scoreStuck` getters (green/amber; brighter variants for dark mode) next to the existing `if (isDarkThemeGlobal)` block-color pattern.
- Removed the duplicated `CLEAN/STUCK` (ResultScreen), `SCORE_CLEAN/SCORE_STUCK` (ScoringPanel), and `SCORE_GOOD/SCORE_WEAK` (ScoreHistoryContent) literals — all three sites + the heatmap `lerp` now read from `WindScoreColors`. Dropped the now-unused `Color` imports.
- Files: `ui/theme/WindScoreTheme.kt`, `ui/ResultScreen.kt`, `ui/ScoringPanel.kt`, `ui/ScoreHistoryContent.kt`.

## Phase 12 — Score-History Trend UI & Storage Tests (Completed)

> Optimization + test coverage for the persistence layer and the history dialog.

### Trend visualization (`ScoreHistoryContent.kt`)
- **Sparkline**: a Canvas line chart at the top of the history dialog plotting accuracy over time (oldest→newest, left→right); colored green when the overall trend is improving, amber when declining (reuses `WindScoreColors.scoreClean/scoreStuck`, so dark-theme adaptive). Shown only when ≥ 2 records exist.
- **Per-item delta**: each record shows a "vs previous attempt" chip next to its score (`+5` green / `-3` amber / `±0` grey); the oldest record has no previous attempt so shows none.

### Storage testability refactor
- Added an injectable `baseDir: File = File(getAppStorageDir())` parameter to `ScoreHistoryStorage`, `ConfigStorage`, and `LangStorage` load/save methods, so tests run against a throwaway temp dir instead of the user's real `~/.windscore`. Production call sites unchanged (use the default).

### New unit tests (`desktopTest`) — 10 tests
- `ScoreHistoryStorageTest`: empty-when-missing, append persists newest-first, sorts by timestamp desc, caps at 50, per-score isolation.
- `ConfigStorageTest`: default-when-missing, save/load round-trip.
- `LangStorageTest`: default-when-missing, save/load round-trip, blank-content fallback.

## Phase 13 — Middle-C Keyboard Landmark (Completed)

> Helps users map the on-screen keyboard to a real piano.

- Marked **Middle C (MIDI 60 / C4)** on both keyboard renderings — the piano-roll's built-in keyboard strip (`PianoRoll.drawKeyboard`) and the static keyboard in 识谱 / SHEET_KEYBOARD mode (`StaticKeyboard.drawKeyboard`).
- Middle C is drawn as a prominent magenta dot near the front of the key; every other C (each octave) gets a subtle grey dot for octave orientation. Dots scale with key width (clamped to a minimum) so they stay visible on 88-key widths. Dark-theme adaptive (uses `accentMagenta` + themed `ink`).
- Files: `ui/PianoRoll.kt`, `ui/StaticKeyboard.kt`.

## Phase 14 — Android Launch Crash Fixes (Completed)

> Two independent crashes that prevented the Android app from launching. Neither was caused by the recent feature work; both were latent and surfaced once testing reached Android.

### A. `AndroidMidiManager` null-cast
- **Crash**: `getSystemService(MIDI_SERVICE) as MidiManager` threw NPE on devices/emulators that don't provide a MIDI service.
- **Fix** (`androidMain/.../midi/AndroidMidiManager.kt`): safe-cast `as? MidiManager`; `start()` no-ops (logs and returns) when the system manager is null. App degrades gracefully — no MIDI input, but playback audio and the rest of the UI still work.

### B. SVG icons unsupported on Android
- **Crash**: `java.lang.IllegalStateException: Android platform doesn't support SVG format.` Compose Multiplatform's resource pipeline can't decode `.svg` on Android; all 21 drawable icons were SVG, so the very first `painterResource` (the splash's `piano_keys_thin`) crashed.
- **Fix**: rasterized all 21 icons to **transparent-background PNG** (black silhouette; Compose `Icon` recolors via its `tint`/`LocalContentColor` using the bitmap alpha) and removed the SVGs. PNG works on both desktop and Android; the `Res.drawable.*` accessors regenerated by filename stem — **no Kotlin code changes**. Conversion done with `svglib`+`reportlab`+`Pillow` (currentColor → black, white-bg render → alpha mask for anti-aliased transparency).
- Files: `composeApp/src/commonMain/composeResources/drawable/*.png` (21 files; `.svg` removed).

## Verification status (current)
- `:composeApp:desktopTest` —— **36 tests all PASSED**.
- `:composeApp:clean compileKotlinDesktop` + `:composeApp:compileDebugKotlinAndroid` both pass (resources regenerated for PNG).


## Known leftovers
- `OsmdHtml.kt` embedded JS HUD strings still Chinese (see Phase 9 limitation).

---

## Phase 15 — Audit Remediation and Theme Context (2026-07-22)

### Code audit
- Reviewed Android input/rendering, parser safety, persistence, identity, testing, build structure, and UI theme risks; the resulting current priorities are maintained in `TECHNICAL_ROADMAP.md`.
- Fixed A01-A15 and A17. A16 is now also fixed: theme colors are immutable `WindScorePalette` values provided through a `CompositionLocal`; Desktop title-bar dark mode is driven by an explicit resolved boolean instead of process-global state.
- Settings/About/license labels and dialog titles now come from the typed EN/ZH/JA `UiStrings` tables. OSMD loading/error messages use localized inputs while technical HUD statuses remain language-neutral.

### Reliability fixes
- Android MIDI now preserves running status and split messages per output port, handles hot-plug/remove events, rejects stale asynchronous opens, and closes detached devices.
- Android WebView score data updates through current references and is explicitly stopped, cleared, and destroyed on disposal.
- Android file import moves reading and MusicXML parsing to `Dispatchers.IO`.
- MusicXML/MXL parsing now bounds raw input, ZIP entry count/size/total expansion, rejects unsafe DOCTYPE/internal entities, and disables external resource access where supported.
- Recent scores use stable `Score.id` identity; Compose state updates stay on the main scope; persistence uses atomic writes and bounded OSMD snapshot caches.
- Added direct tests for MIDI parsing, parser safety/time-axis branches, atomic writes, application state, ScorePlayer, and ScoreSession lifecycle behavior.

### Android/KMP build migration
- Extracted the Android entry point into the new `androidApp` module.
- Migrated `composeApp` to the AGP 9 Android-KMP library plugin and removed the obsolete KMP/Application mixed-module configuration.
- Desktop keep-awake now uses Windows `SetThreadExecutionState` through JNA instead of injecting a synthetic keyboard event.

### Verification
- `:composeApp:desktopTest --rerun-tasks`: 50 tests passed, including the A16 palette regression test and the historical-score sheet restoration regression test.
- `:composeApp:compileKotlinDesktop`: passed.
- `:androidApp:compileDebugKotlin`: passed.
- `:androidApp:assembleDebug`: passed; APK contains `libmidi.so` for arm64-v8a, armeabi-v7a, x86, and x86_64.

### Bug fix — sheet buttons disappear after reopening a historical score
- Symptom: an imported score showed Roll, Read Sheet, and Full Sheet controls on first open, but after leaving and reopening it from Recent Scores only Roll remained.
- Root cause: `App.openScore()` inserted a lightweight recent entry with `rawXml = null` and then passed that lightweight list to `saveRecentScores()`. `RecentsStorage.save()` correctly skips entries without raw XML, so the original MusicXML was never persisted. After reopening, the CBOR score still had notes but no `rawXml`; `PlayerScreen` therefore evaluated `score.rawXml != null` as false and hid the two sheet buttons.
- Fix: keep the in-memory recent list lightweight, but create the persistence snapshot with the currently opened `finalScore` (including `rawXml`) for the matching `Score.id`. This preserves lazy list memory usage while ensuring sheet mode can be restored from history.
- Added `RecentsStorageTest.savingFullScoreMakesItAvailableForSheetModeAfterReload` and injected a test `baseDir` into `RecentsStorage` to lock the XML landing-file contract.

### Bug fix — stopping playback escaped the active loop
- Symptom: with A/B loop enabled, pressing the playback stop button moved the cursor/progress to tick 0. Playback then appeared to escape the loop until it reached the loop boundary and snapped back.
- Root cause: `ScorePlayer.stop()` called `reset()`, and `reset()` always assigned `_tick = 0L` without considering `loopRange`.
- Fix: `ScorePlayer.reset()` now targets `loopRange.first` when a loop is active, rebuilds pre-loop-start active-note state silently, and keeps the normal tick-0 behavior when no loop is configured. This applies to both the stop button and any playback reset path.
- Added regression test `ScorePlaybackTest.stoppingWithLoopActiveReturnsToLoopStart`.

## Documentation Index

- `Document/Worklog.md`: chronological implementation and verification history.
- `Document/TECHNICAL_ROADMAP.md`: current architecture assessment and forward-looking engineering roadmap.
- `Document/DESIGN.md`: visual design system and UI direction.

---

## Priority 1 Correctness Remediation (2026-07-22)

### Playback region invariant
- Added the pure Kotlin `PlaybackRegion` model as the shared score/loop boundary definition.
- `ScorePlayer` and `JudgmentEngine` now constrain reset, seek, step-forward and step-backward operations to the active loop.
- Changing a player's loop range immediately normalizes its current position.
- Added playback and practice-session regression tests for seeks and steps outside an active loop.

### Repeated-note behavior
- Restored per-group `freshKeys` tracking in `JudgmentEngine`.
- A held key can no longer pass consecutive repeated notes; the key must be released and pressed again.
- Chords retain natural staggered input because fresh keys accumulate within the current group.
- Seek, reset and loop snapback clear fresh-key state.

### Sheet capability and historical restoration
- Added serialized `Score.supportsSheetView`; `PlayerScreen` no longer uses `rawXml` presence to decide which views exist.
- Recent-score CBOR entries retain sheet capability while keeping `rawXml` out of the in-memory list.
- Opening a score now restores XML, per-score settings and scoring history on `Dispatchers.IO`.
- A generation token rejects stale results from superseded open requests; back navigation cancels the active request.
- A blocking loading overlay prevents conflicting library actions, and restoration failures remain on the library screen with localized feedback.

### Verification
- `:composeApp:desktopTest --rerun-tasks`: 54 tests passed.
- `:composeApp:compileKotlinDesktop`: passed.
- `:androidApp:compileDebugKotlin`: passed.
- `:androidApp:assembleDebug`: passed.
- APK contains `libmidi.so` for arm64-v8a, armeabi-v7a, x86 and x86_64.

---

## ScoreLibraryController Extraction (2026-07-22)

### State ownership
- Added `ScoreLibraryController` with `StateFlow` state for recent scores, opening status, opened-score results, score history and loading errors.
- Moved recent-score initialization, asynchronous XML/settings/history restoration, request generation cancellation, rename, delete and history append operations out of `App.kt`.
- Added `ScoreLibraryDataSource` injection so controller behavior can be tested without touching platform storage.
- `App.kt` remains responsible only for converting an opened score into playback/practice state.

### Persistence correction
- Recent-list mutations can contain lightweight entries without `rawXml`; `RecentsStorage` now preserves their existing XML/cache identity instead of silently dropping them from `recents.json`.

### Verification
- `ScoreLibraryControllerTest`: open restoration, stale request rejection, rename and delete behavior.
- `:composeApp:desktopTest --rerun-tasks`: 58 tests passed.
- Desktop compilation passed.
- `:androidApp:compileDebugKotlin` and `:androidApp:assembleDebug` passed.

---

## PlaybackController Extraction (2026-07-22)

### Playback ownership
- Added `PlaybackController` and immutable `PlaybackState` exposed through `StateFlow`.
- The controller now owns `ScorePlayer` lifecycle, tick/playing/pitch collection, speed, metronome volume, loop range, seek, step, reset, play, stop and practice-session synchronization.
- `ScorePlayer` is no longer referenced by `App.kt`, `PlayerScreen` or `ControlSidebar`.
- Removed direct speed mutation from the sidebar; UI interactions now submit controller events.
- Playback disables hosted scheduling while active and restores it after pause/stop; scoring can explicitly suspend practice synchronization.
- Loop creation, adjustment and clearing now align playback and practice timelines through one controller path.

### State cleanup
- Playback speed and metronome volume are no longer duplicated as Compose state in `App.kt`; `PlaybackState` is their single owner.
- Closing playback restores default practice synchronization so a scoring session cannot affect the next opened score.

### Verification
- Added `PlaybackControllerTest` for restored parameters, loop/seek/step alignment and hosted scheduling transitions.
- `:composeApp:desktopTest --rerun-tasks`: 61 tests passed.
- Desktop and Android compilation passed; Android APK assembly passed.

---

## PracticeController Extraction (2026-07-22)

### Practice ownership
- Added `PracticeController` and `PracticeState` for the current score, practice mode and owned `ScoreSession`.
- Moved practice-session creation, mode switching, MIDI input routing, position/loop restoration and session disposal out of `App.kt`.
- Mode changes create one replacement session while preserving the current tick and active loop; the replaced session is closed immediately.
- `PlaybackController.attachSession()` switches playback synchronization to the replacement practice session without rebuilding the player.

### State cleanup
- `App.kt` no longer owns duplicate `session`, `practiceMode` or active-loop state.
- The active loop is read from `PlaybackState`; only the incomplete A-point selection remains UI-local.
- Direct `ScoreSession` construction in `App.kt` is now limited to scoring and is the next extraction target.

### Verification
- Added `PracticeControllerTest` for mode replacement/restoration, MIDI event routing and lifecycle cleanup.
- `:composeApp:desktopTest --rerun-tasks`: 64 tests passed.
- Desktop and Android compilation passed; Android APK assembly passed.

---

## ScoringController Extraction (2026-07-22)

### Scoring ownership
- Added `ScoringController`, `ScoringState` and core `ScoringPhase` for the scoring Session and SETUP/ACTIVE/RESULT lifecycle.
- Moved scoring-session creation, MIDI routing, replacement, completion monitoring, one-second result delay, `ScoreRecord` creation and cleanup out of `App.kt`.
- Completion is monitored by the controller instead of a UI `LaunchedEffect`, so hosted-only completion does not depend on a subsequent recomposition or MIDI event.
- Score records are emitted once through a callback to `ScoreLibraryController`, preserving history persistence ownership.
- `PlayerScreen` now imports the core scoring phase instead of defining a UI-owned business enum.

### Verification
- Added `ScoringControllerTest` for successful completion/record creation, cancellation before result and Session replacement.
- `:composeApp:desktopTest --rerun-tasks`: 67 tests passed.
- Desktop and Android compilation passed; Android APK assembly passed.

---

## AppSettingsController Extraction (2026-07-22)

### Settings ownership
- Added `AppSettingsController`, `AppSettingsState` and an injectable `AppSettingsDataSource`.
- Moved global config and language initialization/update persistence out of `App.kt`.
- Moved per-score settings restoration, debounce scheduling, playback write suppression and immediate close-time saving out of `App.kt`.
- `ScoreLibraryController` no longer loads per-score settings; score/history restoration and settings restoration now have separate ownership and are coordinated before the player page is committed.
- The loading overlay remains visible through both asynchronous stages, and back navigation cancels both requests.

### Concurrency correctness
- Debounced and immediate settings writes use a generation plus `Mutex`, ensuring the latest user intent wins and an old debounce cannot overwrite the final close-time snapshot.
- Recent-list initialization and persistence use corresponding generation/serialization guards, preventing late initialization or older rename/delete writes from replacing newer state.
- IO dispatchers are injectable for deterministic controller tests while production continues to use `Dispatchers.IO`.

### Verification
- Added `AppSettingsControllerTest` for initialization/global updates, stale score-settings rejection, playback write suppression, debounce cancellation and immediate final save.
- `:composeApp:desktopTest --rerun-tasks`: 70 tests passed.
- Desktop and Android compilation passed; Android APK assembly passed.

---

## Immutable Score Data and Runtime Hit State (2026-07-22)

### Data model
- Removed mutable `NoteEntity.isHit`; `NoteEntity` is now immutable serialized score data.
- `JudgmentEngine` owns a session-local `hitNoteIds` set for completed and hosted notes.
- `ScoreSession` exposes the current hit-ID snapshot for Compose rendering without sharing runtime state through `Score`.
- `PianoRoll` receives a hit-ID provider and no longer reads mutable fields from score notes.

### Isolation
- Seek, reset and loop snapback update only the owning engine's hit set.
- Added a regression test proving two `JudgmentEngine` instances using the same `Score` keep independent hit state.
- Existing practice, scoring, loop, hosted-track and history behavior remains unchanged.

### Verification
- `NoteEntity.isHit` has no remaining source references.
- `:composeApp:desktopTest --rerun-tasks`: 71 tests passed.
- Desktop and Android compilation passed; Android APK assembly passed.

---

## Versioned Score Cache and Typed Recent Index (2026-07-22)

### Cache schema
- Added a versioned `ScoreCacheEnvelope` around parsed-score CBOR cache files.
- Existing bare `Score` CBOR cache files are decoded as legacy data and rewritten in the current envelope format.
- Unknown future schema versions are rejected, then recovered by parsing the persisted MusicXML and writing a current cache.
- Added regression coverage for both legacy-cache migration and unknown-schema recovery.

### Recent-score index
- Replaced manual `recents.json` string escaping and parsing with `kotlinx.serialization` typed entries.
- Kept the persisted `title` and `xmlHash` field names, so existing recent-index files remain readable.
- Added a title round-trip test covering quotes, backslashes, newlines and tabs.

### Verification
- `:composeApp:desktopTest --rerun-tasks`: 74 tests passed.
- `:composeApp:compileKotlinDesktop`: passed.
- `:androidApp:compileDebugKotlin`: passed.
- `:androidApp:assembleDebug`: passed.

---

## Historical Reopen and Loop Control UI Coverage (2026-07-22)

### UI test infrastructure
- Added the Compose Desktop UI test dependency and a `ControlSidebarUiTest` suite.
- Added stable semantics tags for restored view controls and loop start/end/clear controls.
- Tests verify that a reopened score renders its restored sheet view selection and complete loop state, then updates controls after clearing the loop.
- Tests also verify that an incomplete persisted loop start enables selecting the loop end after reopening.

### Restoration correction
- Restored loop setup phase now becomes `WAITING_FOR_END` when persisted settings contain a loop start without an end.
- This fixes the previous path where a reopened pending A point was displayed but the next stage click sought instead of completing the loop.

### Verification
- `:composeApp:desktopTest --rerun-tasks`: 77 tests passed.
- `:composeApp:compileKotlinDesktop`: passed.
- `:androidApp:compileDebugKotlin`: passed.
- `:androidApp:assembleDebug`: passed.

---

## Recoverable Failure Diagnostics (2026-07-22)

### Diagnostics channel
- Added a buffered `DiagnosticController` for transient recoverable failures across import, library storage, settings and MIDI boundaries.
- Added localized Chinese, English and Japanese diagnostics rendered as a root-level in-app toast, so welcome-screen and player-screen failures share the same visible behavior.

### Storage and settings
- `ScoreLibraryController` reports failed recent-list loading, score opening, history writes and recent-score persistence without reporting normal coroutine cancellation.
- `AppSettingsController` reports load/save failures and supplies default score settings after a failed restoration, preventing the score-opening overlay from waiting indefinitely.
- Added regression tests for diagnostic delivery, failed score opening and failed settings restoration.

### Import and MIDI
- File picker APIs now distinguish user cancellation from picker/read/parse failure; failures report through the shared diagnostics channel.
- Desktop and Android MIDI managers expose unsupported service, no input device, device open failure and disconnect events.
- MIDI start/runtime exceptions now reset the requested running state and show a localized diagnostic instead of leaving the UI in a false connected state.

### Verification
- `:composeApp:desktopTest --rerun-tasks`: 80 tests passed.
- `:composeApp:compileKotlinDesktop`: passed.
- `:androidApp:compileDebugKotlin`: passed.
- `:androidApp:assembleDebug`: passed.

---

## Android USB-MIDI Fallback and Practice Keyboard (2026-07-31)

### USB-MIDI fallback
- Added a raw Android USB Host fallback for devices whose ROM does not provide `android.media.midi.MidiManager`.
- The standard Android MIDI service remains the primary path; fallback claims USB Audio/MIDIStreaming 1.0 bulk input endpoints only when the service is unavailable.
- Added permission handling, hot-plug lifecycle, USB-MIDI event-packet decoding, virtual-cable state isolation and non-dropping MIDI event delivery.
- Real-device fallback input was confirmed by the user on a device without the standard MIDI service.

### Mode-two diagnosis
- Structured `WindScore-State` logs proved the practice session did not reset and USB NoteOn/NoteOff events reached transport, queue, App and session layers in order.
- Apparent disappearing cues matched hosted-only timeline groups where the active-hand cue was empty.
- A later apparent single-note cue was actually a three-note target `[73, 77, 80]`; the two black-key targets were visually too weak in the old equal-width chromatic keyboard.

### Practice keyboard
- Replaced `StaticKeyboard` with `PracticeKeyboard` using real piano geometry: equal-width white keys and overlaid black keys.
- The component uses an independent neutral keyboard palette rather than inheriting the page background.
- Active targets use a strong magenta key treatment plus a top marker on both white and black keys; hosted targets are gray; held notes are green.
- Score pitch ranges expand to stable C-B octave boundaries, avoiding arbitrary narrow chromatic layouts.
- Added geometry, range, chord visibility and visual-state priority tests.

### Verification
- `:composeApp:desktopTest --rerun-tasks`: 93 tests passed.
- Android compilation and packaging were intentionally left to the user because Gradle Android builds stall in the shared terminal environment.
