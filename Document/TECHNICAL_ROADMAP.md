# WindScore Technical Review and Roadmap

Date: 2026-07-22

## Purpose

This document records the current technical assessment of WindScore and the recommended next steps. It is a forward-looking engineering plan, not a replacement for the chronological implementation record in `Worklog.md`.

## Current Assessment

WindScore is a strong functional Beta rather than a prototype. Its most valuable quality is that the core product rules are based on real piano-practice requirements rather than generic MIDI playback:

- Chords are judged as one timed group.
- Repeated notes require meaningful re-press behavior.
- Single-hand practice can host the other hand.
- Hosted segments are scheduled at musical tempo instead of bursting.
- MusicXML is reduced to a focused note model before reaching the UI.
- Piano-roll and sheet views share the same score/session timeline.

Approximate assessment:

| Area | Assessment |
| --- | ---: |
| MusicXML parsing and judgment engine | 8/10 |
| Playback, MIDI and audio abstraction | 7/10 |
| Persistence and caching | 7/10 |
| Desktop/Android platform engineering | 6.5/10 |
| Compose state architecture | 5.5/10 |
| UI completeness and visual identity | 7/10 |
| Tests and maintainability | 6.5/10 |
| Overall product maturity | 7/10 Beta |

The main risk is no longer basic functionality. The main risk is state growth: the application is accumulating more features than the composition root and cross-layer contracts were designed to absorb comfortably.

## Strengths

### Domain Core

`JudgmentEngine` is the strongest architectural component. It is pure Kotlin, independent of Compose and platform APIs, and directly models the wait-mode practice experience. Its grouping, chord, fresh-key, hosted-track, scoring and loop behavior are testable without hardware.

### Score Reduction Boundary

`MusicXmlParser` intentionally converts MusicXML into `NoteEntity` data instead of leaking the full notation tree into the application. This keeps the rest of the product focused on timing, pitch, track and duration.

### Real-World Hardening

The project has already addressed issues that only appear in actual use:

- MIDI devices that enumerate but cannot open.
- MIDI running status and split callbacks.
- Audio initialization delay and note blasting.
- Tied notes and multi-part duration calculation.
- XML/MXL resource limits and unsafe external entities.
- Atomic persistence writes.
- Historical score restoration of raw MusicXML.
- Loop stop/reset escaping the active region.

### Product Identity

The UI has a recognizable visual language: monochrome application chrome, pastel functional blocks, piano-specific imagery, sheet/roll views, compact Android controls and a denser desktop workspace. It is not a generic Material starter interface.

## Main Technical Risks

### 1. Shared Runtime State in the Score Model

`NoteEntity` contains both persistent note data and mutable runtime state such as `isHit`. This means a `Score` is not fully immutable even though it is serialized as data.

Potential consequences:

- Practice and scoring sessions can accidentally share mutable note state.
- A cached score can be confused with a live session.
- Loop resets and scoring resets can become coupled.
- Multiple consumers can mutate the same note objects.

Recommended direction:

```text
NoteData / ScoreData       immutable serialized data
SessionRuntimeState        hit, active, wrong-count and cursor state
```

Do not start this refactor until the playback and session contracts are covered by stronger tests. It is important, but it has a broad blast radius.

### 2. Playback and Loop Rules Are Distributed

Loop behavior currently spans `ScorePlayer`, `ScoreSession`, `JudgmentEngine`, `App.kt` and UI callbacks. The recent stop-button bug is an example of a missing cross-layer invariant: the player remembered the loop but `reset()` still assumed the whole score began at tick zero.

Recommended invariant:

```text
Every position-changing operation is constrained by the active practice region.
```

This must apply consistently to:

- play
- pause
- stop
- reset
- seek
- step forward
- step backward
- loop snapback
- score/session synchronization

Recommended future model:

```kotlin
data class PlaybackRegion(
    val scoreStart: Long,
    val scoreEnd: Long,
    val loopStart: Long?,
    val loopEnd: Long?,
)
```

The UI should submit user intent. One shared core constraint function should determine the resulting position.

### 3. `App.kt` Is Still a Super-Composition Root

`App.kt` currently coordinates navigation, recent scores, MIDI, audio, `ScorePlayer`, practice sessions, scoring sessions, history, settings restoration, autosave, loop setup, splash, toast, theme reporting and platform back behavior.

This makes local changes deceptively risky. A button callback can affect storage, playback and session state at the same time.

Recommended extraction order:

1. `ScoreLibraryController`: open, close, recent list, rename, delete and raw XML restoration.
2. `PlaybackController`: player lifecycle, play/pause/stop/seek, speed, metronome and region constraints.
3. `PracticeController`: `ScoreSession`, practice mode, hosted voice and session reset.
4. `ScoringController`: scoring session, phase transitions and result persistence.
5. `AppSettingsController`: global settings, per-score settings, language and autosave.

Each controller can be a normal Kotlin class exposing `StateFlow` and event methods. Do not introduce a large state-management framework unless the simpler classes fail to express the contracts.

### 4. Mixed State Mechanisms

The application currently mixes Compose `MutableState`, `StateFlow`, `MutableStateFlow`, ordinary mutable properties, `LaunchedEffect`, `DisposableEffect`, `snapshotFlow` and callback-based storage.

Recommended convention:

- Controllers own business state with `StateFlow`.
- Composables collect state and render it.
- User interactions call controller methods/events.
- `DisposableEffect` only registers and releases external resources.
- Business coroutines do not originate from scattered UI branches.

This is a gradual direction, not a requirement for a single rewrite.

### 5. UI Capability Is Coupled to `rawXml`

The historical-score bug happened because the UI used `score.rawXml != null` to decide whether sheet controls should exist. That is a storage implementation detail, not a product capability.

Recommended model:

```kotlin
val supportsSheetView: Boolean
```

or:

```kotlin
val availableViews: Set<ViewMode>
```

The UI should ask whether the score supports a view, not whether a particular lazy-loaded field currently happens to be populated.

### 6. Persistence Correctness Is Better, but Still Implicit

The current persistence layer now has atomic writes, bounded OSMD caches, stable score identity and raw XML restoration. Remaining risks include:

- Hand-written `recents.json` parsing.
- Fingerprints based only on file size and modification time.
- Minimal cache schema migration.
- Mostly log-only error handling.
- Limited user visibility into cache corruption or reparse behavior.

Recommended direction:

- Prefer a typed serialization format for recent entries.
- Use content hash as the correctness identity; use fingerprints only as a fast path.
- Add explicit cache schema versions and migration behavior.
- Surface recoverable storage failures through localized UI state.

## Platform Assessment

### Desktop

Desktop integration is capable and pragmatic: Java MIDI, Java Sound, JavaFX WebView, JNA title-bar behavior and native file dialogs are all integrated. The Windows path is the most mature.

Remaining concerns:

- Android MIDI APIs emit deprecation warnings.
- Desktop packaging is still primarily Windows-oriented despite classifier logic for other OSes.
- JavaFX and JNA availability need continued graceful-degradation testing.

### Android

Android now has a clean application shell separated from the KMP shared module, and the APK includes all four `libmidi.so` ABIs.

Remaining concerns:

- Hardware MIDI behavior needs testing on real devices, not only compilation.
- WebView lifecycle and large-score memory usage need device-level validation.
- Forced landscape mode should be a deliberate product decision and documented in settings/help.
- Android file/MIDI failure states should be visible to users instead of only logged.

## UI and Product Critique

### What Works

- Strong visual identity.
- Clear distinction between roll, sheet and practice/scoring modes.
- Useful compact layout for piano practice.
- Theme and localization are now CompositionLocal/type-table based.
- Score history creates a real practice-feedback loop.

### What Needs Attention

#### Discoverability

The Android two-finger panel gesture is efficient but not obvious. When controls are hidden, keep a small visible expand affordance or handle so the feature remains discoverable.

#### Platform Parity

Desktop and Android intentionally differ, but a capability matrix should document which workflows exist on each platform:

- score import
- recent scores
- sheet rendering
- scoring history
- settings
- keyboard shortcuts
- MIDI diagnostics

#### Forced Orientation

Landscape is reasonable for piano practice but should be explained and revisited for tablets, foldables and multi-window Android modes.

#### Theme API Clarity

The A16 migration removed process-global theme state and made Canvas palette passing safe. The compatibility facade `WindScoreColors` remains for migration convenience. Long term, prefer:

```kotlin
val colors = windScoreColors()
colors.ink
colors.canvas
```

Then remove the facade once ordinary Composables have migrated.

## Testing Roadmap

Current status after the first Priority 1 remediation batch:

- 93 desktop tests pass.
- Core parser, MIDI parser, judgment, storage, playback, session and palette behavior have direct coverage.
- Desktop and Android builds pass.
- Playback and practice seek/step/reset operations are constrained by the active loop region.
- Repeated notes require release and re-press behavior again.
- Sheet-view capability is explicit and no longer inferred from `rawXml` in the UI.
- Historical score XML, settings and history restoration run on the IO dispatcher with stale-request rejection.

Next testing priorities:

1. Historical-score UI restoration: import, exit, reopen, verify all view controls.
2. Loop UI restoration: set loop, stop, verify cursor and progress remain inside the loop.
3. `ScorePlayer` property tests for all position-changing operations under an active loop.
4. `ScoreSession` tests for hosted scheduling, loop snapback and close behavior.
5. Android WebView cutover between two scores and explicit destruction behavior.
6. Compose UI tests for theme switching, language switching and control visibility.
7. Real-device Android MIDI tests with hot-plug and reconnect scenarios.
8. Long-score memory and render-time tests for OSMD snapshots.

The next tests should focus on state-to-UI contracts, because the most recent defects occurred at those boundaries rather than inside isolated algorithms.

## Recommended Execution Order

### Priority 1: Cross-Layer Correctness

- [x] Centralize playback-region constraints for reset, seek and step operations.
- [x] Add state regression tests for historical reopening and loop stop/reset.
- [x] Add explicit score view capability instead of checking `rawXml` in UI.
- [x] Restore fresh-key semantics for repeated notes.
- [x] Move historical score restoration off the UI thread and reject stale requests.
- [x] Add Compose UI coverage for historical reopening and loop controls.

### Priority 2: State Architecture

- [x] Extract `ScoreLibraryController` from `App.kt`.
- [x] Extract `PlaybackController` from `App.kt`.
- [x] Convert score-library business state to `StateFlow`.
- [x] Convert playback business state to `StateFlow`.
- [x] Extract `PracticeController` and move practice-session lifecycle out of `App.kt`.
- [x] Extract `ScoringController` and move scoring-session lifecycle out of `App.kt`.
- [x] Extract `AppSettingsController` and move global/per-score persistence out of `App.kt`.
- [x] Complete the initial controller/state-ownership extraction for library, playback, practice, scoring and settings.
- Keep UI layout and visual design unchanged during this stage.

### Priority 3: Data Model Cleanup

- [x] Separate immutable score data from runtime judgment state.
- [x] Add cache schema versions and migration tests.
- [x] Replace hand-written recent-score JSON with typed serialization.

### Priority 4: Platform Quality

- Add real Android MIDI/WebView device tests.
- [x] Improve user-facing diagnostics for recoverable import, storage, settings and MIDI failures.
- Revisit forced landscape and desktop non-Windows packaging.

### Priority 5: UI Refinement

- Remove the `WindScoreColors` compatibility facade.
- Add an always-discoverable compact-panel expand affordance.
- Add theme/language/layout UI tests and screenshots.

## Explicit Non-Goals

The following changes are not recommended in the immediate next batch:

- Rewriting the entire UI in a new framework.
- Introducing a large dependency-injection or state-management framework.
- Replacing the working MusicXML reduction model with a full notation-domain model.
- Broad visual redesign before state invariants and restoration tests are in place.
- Refactoring every file solely to remove a small amount of duplication.

## Final View

WindScore's domain core is already close to product quality. The next quality jump will not come from adding another feature; it will come from making state ownership and cross-layer invariants explicit.

The target architecture is not necessarily bigger. It should be clearer:

```text
Immutable score data
        |
Playback region and timeline
        |
Practice / scoring controllers
        |
StateFlow UI state
        |
Compose views and platform shells
```

The project should preserve its current domain strengths while investing the next development cycle in correctness at boundaries, restoration behavior, controller ownership and UI integration tests.
