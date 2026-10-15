# Three-view practice improvements

## Behavior

- **Falling notes:** drawing, keyboard, judgment and practice controls are unchanged.
- **Score + keyboard:** thin target-colored outlines identify the current user-required attack noteheads. The existing blue cursor and scrolling remain unchanged. The cue uses the existing dark-pink keyboard target color; it never indicates success.
- **Score only:** no notehead cues or keyboard. Upcoming pitch-name answers are removed from compact status and desktop status cards. Progress, position, controls, completion and existing results remain.
- Existing view buttons now share localized accessible labels and standard Material tooltips: “Falling notes,” “Score + keyboard,” “Score only” (English, Chinese and Japanese). Tooltip activation does not select a different view. No new settings or progression system was introduced.

## Cue identity and feedback semantics

The collector exports importer-owned written tick/duration alongside the existing written key, performed ID, mapping status and rectangle. These two optional geometry fields are rebuildable metadata, not changes to Score or NoteEntity. Native validation checks segment containment within the performed event. Older snapshots without timing remain readable and receive no inferred cue; the changed HTML/runtime fingerprint causes fresh normal captures.

The sheetTargetCueIndex function builds an onset-indexed table once per score/layout/hand selection. It uses the original event track, so moving a tied note between staves does not change which hand is required. Only a unique mapped attack segment at the performed onset is eligible. Tied continuations, duplicate attack associations, unsupported voices, ambiguous unisons and uncaptured pages are skipped. OSMD tie graphs and SVG IDs are not identity sources.

The index is independent of held pitches and completed IDs:

- A partially assembled chord continues to show all current targets.
- Held keys retain the existing keyboard feedback, including wrong held keys; holding is not labeled correct.
- Completed IDs may include hosted notes and seeks, so they never drive success coloring.
- Cues are disabled during demonstration playback, outside Score + keyboard, and on fading Score-only surfaces during a view transition.
- Judgment rules, timing, loops, practice/scoring records, IDs and persisted view choices are unchanged.

The renderer draws small oval outlines around head bounds, then draws the existing blue cursor above them. It does not recolor OSMD chord groups or modify the cached score image.

## Verification

Reproducible command, using the existing environment/toolchain:

~~~sh
DISPLAY=:99 WINDSCORE_RENDER_VALIDATION=1 bash gradlew \
  :composeApp:desktopJar :composeApp:desktopTest :composeApp:testAndroidHostTest \
  :androidApp:assembleDebug :androidApp:assembleRelease \
  :androidApp:testDebugUnitTest :androidApp:lintDebug \
  :androidApp:connectedDebugAndroidTest --no-daemon --console=plain
python tools/validate_android_apks.py
~~~

Focused coverage:

- Attack-only selection across tied segments and original-hand selection; mapped/unresolved/old/mismatched metadata; view/playback gating.
- Native containment validation for optional timing fields, including missing or out-of-range timing.
- Actual bundled OSMD rendering and Compose pixels for both/right/left target outlines; visible blue cursor; pixel-identical cue-free output; no later sustain-position or playback cue. Generated images: composeApp/build/validation/osmd/compose-targets-*.png.
- Actual view controls switched during a partial chord with a wrong held note and through an A–B loop wrap, preserving session state.
- Desktop and compact pitch-answer visibility, preserved progress/results, accessible labels, and real long-press tooltip behavior.
- Existing full semantics, parser, persistence, playback, practice and 105 OSMD fixture/layout cases remain enabled.

Verified automated results on 2026-10-15:

- **216 desktop tests and 112 Android host tests**, zero failures, errors or skips.
- All **105 actual OSMD fixture/layout cases** passed, including chords, ties, cross-staff notes, nonnumeric voices, zoom, pagination and re-layout.
- Real Chromium/JavaFX rendering and Compose pixel assertions passed. The original six semantics regressions remain byte-identical to their initial baseline.
- Desktop JVM build and Android debug/release builds passed. The release APK is unsigned.
- Android lint passed with the same three existing warnings: OldTargetApi, GradleDependency and MissingApplicationIcon.
- Both APKs contain the unchanged offline OSMD/score/license assets and all four MIDI native ABIs. Packaged DEX also contains the new cue/view classes, labels and written timing fields.
- The app unit-test task is NO-SOURCE. The connected task completed with no instrumented tests and no attached devices; it is not evidence of physical Android rendering or MIDI/audio correctness.

Commits: 9eed231 adds attack cues and rendering tests; 1e43937 adds independent-reading guidance, localized view tooltips and state-preserving interaction tests. No push or merge was performed.

Full validation log: /workspace/.windscore-setup/learning-verified.log. JUnit reports: composeApp/build/test-results/desktopTest and composeApp/build/test-results/testAndroidHostTest. Packaging results: composeApp/build/validation/android-packages.json.

The core implementation is in SheetTargetCues.kt (selection), NativeSheetView.kt (drawing), PlayerScreen.kt (view gating and status), ViewModeButton.kt (shared labels/tooltips), OsmdNoteGeometryJs.kt (authoritative written timing export), and NoteGeometryBridge.kt (validation). All paths are under composeApp/src with their existing commonMain/jvmShared source sets.

## Device checks still required

No physical Android/WebView, MIDI keyboard or audio loopback validation is claimed. On representative devices:

1. Check outline legibility at small widths and device densities, both themes, both hand selections, cross-staff ties and partial chords.
2. Switch all three views mid-chord and during a loop; confirm no lost key state, accidental attacks or success indications, and no cue flashes in Score only.
3. Verify hover/long-press tooltips, screen-reader names and normal tap selection.
4. Reopen offline through the raster cache; verify cursor/outline alignment and unchanged MIDI/audio behavior, including tempo changes and tied sustains.

Production capture still supports only the first SVG; unsupported mappings retain cursor-only guidance. These cases were not expanded by this change.
