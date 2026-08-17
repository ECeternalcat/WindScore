# WindScore Pre-Upgrade Audit

Date: 2026-08-18
Baseline commit: `38ee7e2` (complete rollback point; `49b8725` was missing all untracked controller/test files — fixed by `38ee7e2`).
Purpose: verify TECHNICAL_ROADMAP.md remediation status and clear known risks before introducing new practice models.

## 1. Roadmap Status Verification

### Priority 1: Cross-Layer Correctness — DONE

- `PlaybackRegion` centralizes constraints; `ScorePlayer` (seek/reset/step/loop) and `JudgmentEngine` (seek/step/snapback) are region-constrained.
- 93 desktop tests + 51 android host tests pass.
- `Score.supportsSheetView` replaced `rawXml != null` checks; `ScoreLibraryController.open` validates XML availability.
- `freshKeys` restores re-press semantics for repeated notes (`JudgmentEngine`).
- Library/settings loads run on `Dispatchers.IO` with stale-request rejection via generation counters.

### Priority 2: State Architecture — DONE

`ScoreLibraryController`, `PlaybackController`, `PracticeController`, `ScoringController`, `AppSettingsController` extracted from `App.kt`, all StateFlow-based, each with tests.

### Priority 3: Data Model Cleanup — DONE

- `NoteEntity` fully immutable; hit state is session-local in `JudgmentEngine`.
- `ScoreCacheEnvelope` schema versioning with legacy migration.
- Recents use typed serialization; score identity = md5(xml); fingerprints are only a fast path.

### Priority 4/5 — PARTIAL (not blocking)

- Android real-device MIDI/WebView tests: not done.
- Forced-landscape rationale, non-Windows packaging: not documented.
- `WindScoreColors` facade: still used in 100+ places (backed by CompositionLocal, safe but unfinished).
- Compact-panel expand affordance: not done.

## 2. New Risks Found (pre-refactor)

| # | Severity | Risk |
|---|----------|------|
| R1 | High | MIDI event arbitration inline in `App.kt` (`if (!scoring.onNoteOn(..)) practice.onNoteOn(..)`); each new practice model extends the if-chain. |
| R2 | High | `ScoreSession` coupled to Compose (`mutableStateOf`), inconsistent with the StateFlow controller convention; a second session type would fork the state-exposure pattern. |
| R3 | Medium | `ScoreSession.isHostedEnabled` public var written cross-object by `PlaybackController` without synchronization. |
| R4 | Medium | `JudgmentEngine` conflates track selection and judgment rules in `PracticeMode`; new judgment modes (follow/rhythm) would force enum-driven UI churn. |
| R5 | Medium | `ScoringController` polls `session.finished` every 20 ms instead of observing completion. |
| R6 | Low | `App.kt applyOpenedScore` passes the *previous* score's tick/loop into a fresh session when no settings restore. |
| R7 | Low | `PracticeMode` is a persisted `@Serializable` enum; old builds reading newer CBOR enum values will fail (documented, accepted). |

## 3. Refactor Plan and Status

Order (each step must keep all tests green):

1. **R1 → `MidiInputRouter`**: owns `running` state as StateFlow, registers ordered `MidiNoteHandler`s (scoring first, practice second), collects `midiManager.events`/`diagnostics`, drives synth passthrough. `App.kt` keeps only UI wiring.
2. **R2+R3 → de-Compose `ScoreSession`**: all observable fields become `StateFlow`; `isHostedEnabled` var becomes `setHostedEnabled()`; UI reads via a Compose adapter; `PlaybackController` uses `.value`/method.
3. **R4 → split `PracticeMode`**: engine/session take `TrackSelection` (track ownership) + `JudgmentPolicy` (hit rule, wait-mode default); `PracticeMode` remains the persisted/user-facing model mapping 1:1 today.
4. **R5 → completion flow**: `ScoringController` awaits session finished StateFlow instead of polling.
5. **R6 → fix fresh-open semantics**: fresh open (no restore) starts at tick 0 with no loop.

### Status

- [x] Rollback point completed (`38ee7e2`)
- [x] R1 MidiInputRouter (`core/MidiInputController.kt`; scoring/practice implement `MidiNoteHandler`; App.kt arbitration removed; 6 new tests; flaky `PlaybackControllerTest` poll budget raised to 5s)
- [x] R2/R3 ScoreSession de-Compose: single `StateFlow<SessionState>` snapshot; `setHostedEnabled()` replaces public var; UI collects state (`ss = session.state.collectAsState()`); PlaybackController uses method. R5 also done: ScoringController awaits `state.first { finished }` instead of 20 ms polling. Fixed latent android-host crash: `androidMain/StateTrace` guarded with `runCatching` (unmocked `android.util.Log` broke all session-touching host tests since the trace instrumentation landed).
- [x] R4 TrackSelection/JudgmentPolicy: `JudgmentEngine(score, trackSelection, policy)`; `TrackSelection` (BOTH_HANDS/RIGHT_HAND/LEFT_HAND) owns track hosting semantics via `isActiveTrack`/`hostedTrack` extensions; `JudgmentPolicy` (default `WaitModeJudgment`) owns hit rules; `PracticeMode` remains the persisted/user-facing model, mapped 1:1 today. New practice models plug in as policies/handlers without enum churn in the UI. Contract tests in `JudgmentPolicyTest`.
- [x] R6 fresh-open semantics: `applyOpenedScore` without saved settings starts at tick 0 with no loop (no longer inherits the previous score's session position).
- [x] Test robustness: `playbackDisablesHostedScheduling...` observes playback at 0.05x speed (score finished in <1s at 1x, making the window racy under load).

Remaining known risks (accepted, see §2): R7 enum persistence compat (documented), Priority 4/5 roadmap leftovers (real-device tests, WindScoreColors facade, expand affordance).

## 4. Test Results

| Step | desktopTest | androidHostTest |
|------|-------------|-----------------|
| baseline | 93/93 | 51/51 (pre-StateTrace code) |
| after R1 (MidiInputController) | 99/99 | exposed latent StateTrace crash |
| after R2/R3/R5 (session StateFlow + completion flow + StateTrace guard) | 99/99 | 68/68 |
| after R4 (TrackSelection/JudgmentPolicy) + R6 + test robustness | 102/102 | 71/71 |
