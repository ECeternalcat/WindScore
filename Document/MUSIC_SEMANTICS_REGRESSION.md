# Music Semantics Regression Suite

## Scope

This is a regression baseline, not a parser or playback refactor. Production code,
dependency declarations, and lockfiles are unchanged. The six tests live in
`composeApp/src/desktopTest/kotlin/com/windscore/core/semantics/MusicSemanticsRegressionTest.kt`
and use the actual `MusicXmlParser`, `ScorePlayer`, and `JudgmentEngine`. XML
fixtures are inline and self-contained. Playback uses a recording `AudioSynth`;
no audio device, MIDI keyboard, GUI, or notation renderer is required.

The tests assert desired behavior and intentionally remain ordinary failing
tests: no assertions are disabled, and no failures are marked as expected.

## Contracts under test

- Tick remains the musical coordinate. Divisions changes must preserve quarter-note
  positions and durations at the chosen canonical resolution.
- Simultaneous same-pitch ties in different voices remain independent.
- A same-voice tie may change staff and still produce one sustained event. Staff
  is visual placement; this test does not prescribe a practice hand or a tie-key
  implementation. Voice changes and ambiguous overlapping ties need additional rules.
- Tempo changes affect elapsed playback time, without moving musical onsets to
  milliseconds or changing their quarter-note positions.
- A/B loops select note onsets in the half-open interval `[A, B)`.
- A loop with no note onsets must not request or complete a note at B.

## Run

From the repository root:

```sh
bash gradlew :composeApp:desktopTest \
  --tests 'com.windscore.core.semantics.MusicSemanticsRegressionTest' \
  --no-daemon --console=plain
```

The standard desktop test task compiles the existing desktop application and test
sources, but executes only this class. It does not build an Android APK or start
the application. No dependency declarations were added for this suite.

In the cloud instance, the first attempt stopped at plugin resolution and did not
execute tests. A retry with the environment proxy CA trusted stopped while Android
configuration tried to write to the read-only default user directory. The final
run succeeded in configuring and compiling after setting a writable Android user
directory. All environment-only files remain outside the checkout:

```sh
GRADLE_USER_HOME=/workspace/.gradle \
ANDROID_USER_HOME=/workspace/.android \
JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/workspace/.windscore-setup/cacerts \
bash gradlew :composeApp:desktopTest \
  --tests '*MusicSemanticsRegressionTest' --no-daemon --console=plain
```

The local truststore was copied from the system Java truststore and augmented with
the supplied environment proxy CA; TLS verification remained enabled. The existing
Gradle user properties supply the cloud HTTP/HTTPS proxy. These paths are specific
to this instance, not new repository requirements.

## Observed baseline

Run: 2026-10-14 21:31 JST, against production HEAD
`de41bb42b49290bec5ae8e0e569676f60a6632fd` plus the new test file.
Toolchain: repository Gradle 9.6.1, Kotlin 2.3.10, JetBrains JDK 21.

**6 tests executed, 6 assertion failures, 0 errors, 0 skipped.** Gradle exited
with status 1 because of test failures. The suite took 5.305 seconds; the complete
configuration/compilation/test invocation took 2 minutes 10 seconds.

Pairs below are `(startTick, durationTick)`.

| Test | Expected behavior | Observed behavior |
| --- | --- | --- |
| `divisionsChangesPreserveQuarterNoteCoordinates` | Two quarters at divisions 8: onsets `[0,8]`, durations `[8,8]`, total 16 | Onsets `[0,4]`, durations `[4,8]`, total 12 |
| `simultaneousSamePitchTiesInDifferentVoicesRemainIndependent` | Two independent C4 events: `[(0,8),(0,8)]` | Three events: `[(0,4),(0,8),(4,4)]` |
| `tieChangingStaffInTheSameVoiceProducesOneSustainedEvent` | One C4 event: `[(0,8)]` | Two events: `[(0,4),(4,4)]`, assigned to right and left hands |
| `tempoChangeAffectsWallTimeWithoutChangingScoreTicks` | Onsets remain `[0,4,8]`; intervals approximately 500 ms then 2000 ms for 120 -> 30 BPM | Onsets remain `[0,4,8]`; intervals 1987.909 ms then 1998.029 ms |
| `playbackLoopNeverTriggersANoteAtExclusiveBoundaryB` | Through the second visit to A, note-ons `[60,60]` | `[60,62,60]`: pitch 62 at B sounded |
| `emptyPracticeLoopDoesNotAdvertiseOrJudgeANoteAtB` | No pending pitches in `[4,8)` | Current tick 8, pending pitch 62 at excluded B |

The authoritative local output is:

- `composeApp/build/test-results/desktopTest/TEST-com.windscore.core.semantics.MusicSemanticsRegressionTest.xml`
- `composeApp/build/reports/tests/desktopTest/index.html`

These are ignored generated artifacts, not committed files. Counts and observations
above were read from the current XML report, not inferred from source code.

## Limits and follow-up

The tempo test measures actual note-on callbacks with a monotonic clock. It uses
broad interval tolerances (300–900 ms and 1600–2600 ms) and a 15-second timeout;
it is not a deterministic virtual-clock test. Severe scheduling stalls can affect
this test. The observed approximately 2-second first interval establishes the
current fixed-tempo behavior without a timeout or environmental error.

The playback-loop test waits for two A note-ons, with an 8-second timeout, so it
proves a real wrap occurred rather than passing because playback was observed too
briefly. Both playback tests stop their player and cancel their scope in `finally`.

Assertions stop at the first failure in each test. In particular, the empty-loop
test confirmed the incorrect pending pitch; its subsequent input/completion
assertions were not reached. Likewise, later divisions assertions were not reached,
although the captured output reports durations and total ticks.

This baseline does not yet cover cross-part tie isolation, voice-changing ties,
ambiguous overlapping ties, repeats, notation-element identity, pause/resume,
seek/loop clock rebasing, tempo changes inside loops, or speed multipliers. Those
need separately agreed expectations. It does not prescribe `(part, voice, pitch,
staff)` as a tie key, require an onset-in-milliseconds migration, or decide whether
an empty loop should be rejected, remain silent, or expose a completed state.

No production fixes, commits, or pull requests were made. The full existing test
suite and Android builds were not run.
