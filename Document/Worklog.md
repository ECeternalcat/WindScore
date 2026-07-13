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

## Phase 3 — Playback Speed Control, CBOR Serialization Caching, & UI Refactoring (Completed)

> Addresses the remaining items (M4, L3, L6) from the July 14, 2026 audit.

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
