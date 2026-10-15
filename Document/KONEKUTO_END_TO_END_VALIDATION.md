# Bundled Konekuto validation

Validation performed in October 2026, against `composeApp/src/desktopMain/resources/konekuto-claris.mxl` and the identical Android asset. This report separates executable evidence from device and hardware checks. No notation backend or event schema was replaced.

## Independent reference

`tools/konekuto_reference.py` uses only Python standard-library ZIP, XML and exact `Fraction` arithmetic. It does not call WindScore or OSMD. Measure origins use maximum content extent; ties form an explicit adjacency graph between written segments, rather than copying the application's open-tie algorithm. The committed `composeApp/src/desktopTest/resources/konekuto-reference.json` contains both merged performance events and original written segments.

Source SHA-256: `693c312c97a12e672124fc367aa3e52a4ae44cb1a2257f85f20133fba407042c`.
Extracted XML SHA-256: `26544509db2719b534b7cd3de1f6e038259325f18af188371a4d7269f0d76b9f`.

The score contains 166 measures labeled 0–165, divisions 4, 2,049 pitched written segments, 341 tie connections, 1,708 merged performance notes, and 874 performance onset groups. Measure 0 is an eight-tick pickup. The score ends at tick 2,648.

| Location | Logical tick | Quarter-note BPM | Elapsed score time at 1× |
|---|---:|---:|---:|
| Measure 0 | 0 | 175 | 0 ms |
| Measure 165 start | 2632 | 58 | 225,600 ms |
| Measure 165 end | 2648 | 175 | 229,737.931034 ms |

The two tempo marks in measure 165 occur at different positions. Its XML declares 58, writes a right-hand whole-note chord, backs up 16 ticks, writes a left-hand whole-note chord, then declares 175 at the local cursor's **end**. They are not conflicting simultaneous tempos. The final chord comprises MIDI pitches 30, 42, 58 and 66; each has duration 16 ticks (four quarters), lasting 4,137.931034 ms at 58 BPM. The return to 175 does not shorten that chord.

The oracle intentionally covers this fixture's features, with explicit guards rejecting unexpected grace/cue/repeat constructs. Passing this piece is not evidence of general repeat unfolding or all MusicXML semantics. The existing synthetic regressions remain necessary, particularly for divisions changes, cross-staff ties and empty loops absent from this piece.

## Parser, playback and practice evidence

`KonekutoSemanticIntegrationTest` verifies:

- Both platform assets are byte-identical and match the pinned source hash.
- Every parser note's pitch, onset, duration and hand/staff assignment matches the independent event multiset; all 1,708 session-local note IDs are unique.
- `ScoreTiming` matches the complete tempo map, measure-165 interval and total integrated score duration.
- `JudgmentEngine` visits every reference onset in both-hands mode, recognizes every note, and produces 100% clean completion under correct input. Right-only and left-only modes produce exactly the other hand's expected hosted events.
- A complete `ScorePlayer` pass, with an injected monotonic clock, matches all expected synth callback events and their ordering, including same-pitch reference counting. Callback lateness is checked within one 100-ms simulated sampling interval; this is not an acoustic latency measurement.
- A real-clock seek and playback of measure 165 produces exactly four attacks and four releases, with duration checked against 58 BPM. The complete 229.7-second piece is not played through in real time.
- Real-clock `ScoreSession` checks seek to measure 165, partial-chord waiting, hosted-hand note events and sustain duration at 1× and 2×. Wall time alone does not complete the waiting group.

Relevant production symbols: `MusicXmlParser.parse`, `ScoreTiming.millisecondsAt` / `millisecondsBetween`, `ScorePlayer.rebuildAt`, `JudgmentEngine.onNoteOn` / `step`, and `ScoreSession.playHosted`.

| Evidence or implementation | Repository path |
|---|---|
| Parser | `composeApp/src/jvmShared/kotlin/com/windscore/core/parser/MusicXmlParser.kt` |
| Timing, transport and practice | `composeApp/src/commonMain/kotlin/com/windscore/core/engine/{ScoreTiming,ScorePlayer,ScoreSession,JudgmentEngine}.kt` |
| Whole-piece semantic checks | `composeApp/src/desktopTest/kotlin/com/windscore/core/semantics/KonekutoSemanticIntegrationTest.kt` |
| Browser, cursor and raster generation | `composeApp/src/commonMain/kotlin/com/windscore/ui/OsmdHtml.kt` |
| Visible sheet and falling notes | `composeApp/src/commonMain/kotlin/com/windscore/ui/{NativeSheetView,PianoRoll}.kt` |
| Native desktop rendering check | `composeApp/src/desktopTest/kotlin/com/windscore/ui/KonekutoJavaFxTest.kt` |
| Cross-view drawing check | `composeApp/src/desktopTest/kotlin/com/windscore/ui/KonekutoRenderingTest.kt` |
| Android host semantics | `composeApp/src/androidHostTest/kotlin/com/windscore/core/KonekutoAndroidHostTest.kt` |

### Confirmed defect and fix

Before the fix, seeking to tick 2632 retriggered MIDI pitches **49 and 65**, which ended exactly there, before attacking the final chord. `ScorePlayer.rebuildAt` replayed only events strictly before the target; it therefore reconstructed already-expired notes as sustains until the next playback iteration.

The fix silently consumes release events at the exact seek target while leaving attacks at that target pending. A focused `PlaybackTimingTest` also checks overlapping same-pitch sustains: an expired note is not retriggered, while a longer overlapping note remains sounding. The independent final-chord assertion was retained.

This changes transport reconstruction only. `NoteEntity`, score tick resolution, note allocation/identity, `Score.CURRENT_VERSION`, parsed-cache schemas, source hash IDs, bookmarks and historical practice records are unchanged. Existing cache and history compatibility coverage remains in the complete suite. No cache migration is required for this fix.

## Actual rendering and cross-view evidence

`KonekutoRenderingTest` exports the **production** `OsmdHtml.buildHtml` and bundled OSMD JavaScript, then invokes `tools/validate_osmd.cjs` in real Chromium. The probe renders at widths 1200, 800 and 360 px, waits for asynchronous SVG-to-PNG chunk rasterization, checks all written onset/pitch groups against the independent written-segment reference, and verifies every exported cursor rectangle stays inside the snapshot.

The probe drives the actual OSMD DOM cursor at ticks 0, 2616, 2632, 2640 and 2648, then seeks backward to 2632 and 0 using the production reset/advance protocol. Actual DOM coordinates, corrected for scroll, must match exported cursor geometry. During the sustained final chord and at score end, the sheet cursor remains on that chord.

The same test decodes the actual exported PNG chunks into `SheetSnapshot`, renders production `NativeSheetView` and `PianoRoll` with Compose, and captures pixels. It verifies the visible blue sheet cursor at the exported position during held timing, backward seek and loop return, and verifies all four final-chord note rectangles at the falling-note hit line using the real hand colors. These are drawing tests, not just conversions between milliseconds and ticks.

`KonekutoJavaFxTest` separately renders the production HTML in JavaFX 23 WebView, the actual desktop browser engine, using a native window on a dummy Xorg display on Linux. It verifies nonblank PNG chunks and moves the real cursor to the final chord and back to the pickup. Chromium at 1200 px produced a 7,864-px sheet; JavaFX produced a 7,877-px sheet. This difference is acceptable because each host exports its own cursor geometry and raster dimensions. One host's cached geometry must not be assumed interchangeable with another's. Windows/macOS runtime behavior and Windows installer packaging were not tested.

Generated evidence is under `composeApp/build/validation/osmd/` (browser screenshots, chunks, JSON geometry, Compose sheet/roll captures) and `composeApp/build/validation/javafx/` (native raster chunks and JSON). These are ignored build artifacts, not committed binaries. Rendering times are diagnostic measurements of this cloud host, not mobile performance guarantees.

Limitations: the Compose tests drive shared ticks explicitly; they do not measure live frame-to-audio latency or exercise the complete interactive application's window lifecycle. Matching onset/pitch groups does not establish stable individual OSMD glyph IDs linked to persisted performance IDs. No additional source-note identity mapping was introduced. CSS-transform fallback and multiple SVG pages are not encountered at the tested widths for this score. Android WebView is a separate runtime and requires a device check.

## Reproduction

From the repository root:

```sh
python3 tools/konekuto_reference.py --check
bash gradlew :composeApp:desktopTest --no-daemon --console=plain
```

The two actual-browser integration tests are opt-in, so ordinary development tests do not acquire external browser requirements. For the full rendering validation, provide Node, Playwright, Chromium and a Linux X display (or a native display on other platforms):

```sh
WINDSCORE_RENDER_VALIDATION=1 DISPLAY=:99 \
bash gradlew :composeApp:desktopTest --no-daemon --console=plain --rerun-tasks
```

`WINDSCORE_CHROMIUM` selects a Chromium executable; `WINDSCORE_PLAYWRIGHT` selects an installed Playwright module if normal Node module resolution does not find it. The cloud runner already provides Node/Playwright and `/usr/bin/chromium`. No npm dependency or application dependency was added. The checked-in probe's fallback module path matches that runner.

Cloud-specific runtime preparation remains outside the checkout: `GRADLE_USER_HOME=/workspace/.gradle`, `ANDROID_USER_HOME=/workspace/.android`, and `JAVA_TOOL_OPTIONS='-Djavax.net.ssl.trustStore=/workspace/.windscore-setup/cacerts -Duser.home=/workspace/.windscore-setup/home'`. The supplied proxy CA is trusted; TLS verification remains enabled. Native rendering used `Xorg :99 -config /etc/X11/xorg.conf -logfile /workspace/.windscore-setup/Xorg.log -nolisten tcp -noreset` with the image's existing dummy-display configuration.

## Android validation

Android SDK preparation uses Google's HTTPS repository manifest and verifies downloaded command-line archives against its published checksums. Packages are installed outside the checkout at `/workspace/.windscore-setup/android-sdk`; no `local.properties` is committed. API 36, build tools 36.0.0 and platform tools are available.

The final combined run passes **199 desktop tests and 110 Android host tests**, with zero failures, errors or skips; both opt-in rendering tests actually execute. The initial semantic fix was separately validated by the complete 196-test desktop suite, and rendering was validated by the complete 198-test suite before adding the final session check. The original six regression tests remain byte-identical to commit `a0ee1d9`.

`compileAndroidMain`, `assembleDebug` and `assembleRelease` succeed. Release assembly's vital lint checks pass. The app's `testDebugUnitTest` task reports **NO-SOURCE**, not a passing device test. Android host coverage includes shared engine/controller/MIDI-decoder tests and the new actual-asset semantics test; it runs on the host JVM against the Android compilation. `tools/validate_android_apks.py` verifies both APKs contain byte-identical score/OSMD assets and `libmidi.so` for arm64-v8a, armeabi-v7a, x86 and x86_64. Debug APK: 20,546,585 bytes; unsigned release APK: 16,931,254 bytes. Neither APK was installed on a device.

Compilation reports existing deprecated Android MIDI APIs and experimental expect/actual-class warnings. Native-library stripping is unavailable for some bundled libraries; the build packages those libraries unchanged. These are build warnings, not validation of the native synth's runtime behavior.

`lintDebug` passes with **zero errors and three warnings**: `OldTargetApi`, newer compile SDK availability (37 versus the project's 36), and `MissingApplicationIcon`. No target upgrade or unrelated icon work was performed. `connectedDebugAndroidTest` completes successfully but its report contains **zero tests**, with no ADB devices attached. This is an empty instrumentation result, not successful on-device integration coverage. Reports are under `androidApp/build/reports/lint-results-debug.*` and `androidApp/build/reports/androidTests/connected/debug/`.

Reproduction command:

```sh
ANDROID_HOME=/workspace/.windscore-setup/android-sdk \
bash gradlew :composeApp:compileAndroidMain :composeApp:testAndroidHostTest \
  :androidApp:assembleDebug :androidApp:assembleRelease \
  :androidApp:testDebugUnitTest --no-daemon --console=plain
python3 tools/validate_android_apks.py
bash gradlew :androidApp:lintDebug :androidApp:connectedDebugAndroidTest --no-daemon --console=plain
```

There is no `testReleaseUnitTest` task in this configuration. Initial task selection attempted it and failed before execution; the corrected command uses the discovered tasks. App unit-test or connected-test tasks with no sources are not proof of device integration. `adb devices -l` returns no devices, and `/dev/kvm` and physical audio/MIDI devices are absent. No connected instrumentation tests, Android WebView runtime checks or Android audio driver execution can be claimed from host tests or APK assembly.

## Physical device and MIDI/audio procedure

1. Install the debug APK on a supported Android device; also launch desktop on a supported desktop OS. Open the bundled score in sheet and falling-note modes, rotate/resize, scroll to the last system, and reopen it to check cached snapshots. Confirm measure 165 shows Gb1/Gb2/Bb3/Gb4 with no cursor drift. Record device, OS, WebView version and audio route.
2. Connect a real MIDI keyboard. In both-hands wait mode, press a partial correct chord, wait, add the remaining notes, release and repeat. Check waiting freezes both views, fresh strikes are required for repeated notes, held keys and judgment agree, and wrong notes affect feedback. Repeat right-only/left-only with hosted accompaniment. Test unplug/reconnect and changing the selected input.
3. Seek directly to measure 165 and play at 1×. Only pitches 30/42/58/66 should attack; preceding pitches 49/65 must not retrigger. Measure the chord's logical sustain near 4.138 seconds, then test 0.5× (8.276 seconds) and 2× (2.069 seconds). Distinguish callback/synth scheduling from audible attack/release envelopes and hardware latency.
4. Pause during that chord, wait several seconds, resume, seek backward, and loop `[2616,2632)` and `[2632,2648)`. The first excludes the final chord; the second includes it once per pass. Check no stuck notes, stale delayed releases or flashes of the B-boundary attack. Repeat after changing speed and switching practice/playback views. Use an empty region from the synthetic loop fixture to check it generates no cues or completion evidence.
5. Record audio plus screen/MIDI timestamps where possible to assess acoustic latency and visual alignment over the whole piece. Check speaker/headphone routes and USB/Bluetooth MIDI separately if supported. Pedal/controller/channel semantics are not established by the application's pitch-only note-event tests; record observed behavior rather than assuming full MIDI support.

Remaining risks include resource use on low-memory Android devices (360-px rendering produces a tall multi-chunk snapshot), platform-specific font/WebView layout, real scheduling stalls, audio voice ownership during transport changes, and generic musical constructs absent from this fixture. These require targeted device or additional fixture coverage, not a notation migration.

## Commits

- `095ec76`: exact-release seek fix, focused overlap test, independent oracle and whole-piece semantic checks.
- `29b9a2d`: written-note oracle coverage, real-clock hosted-practice check, actual Chromium/JavaFX rendering and Compose cross-view pixel tests.
- `c2d1f8d`: Android host actual-score semantics and APK asset/native-library verification.
- A documentation-only commit records this report and the final validation outcomes. No commits were pushed and no pull request was opened.

Cloud logs are `/workspace/.windscore-setup/konekuto-semantics-full.log`, `rendering-full.log`, `android-validation.log`, and `android-lint.log`. The latest complete JUnit XML reports are under `composeApp/build/test-results/desktopTest/` and `composeApp/build/test-results/testAndroidHostTest/`. Generated package checksums are in `composeApp/build/validation/android-packages.json`.
