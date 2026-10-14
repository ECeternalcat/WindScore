# Incremental music semantics fixes

The six original regression tests and assertions remain unchanged. Each phase
runs the complete desktop suite (common tests, desktop tests, and Compose UI tests).
Intermediate failures are reported explicitly rather than ignored or disabled.

## Compatibility and semantic boundaries

- Score IDs remain the hash of the original MusicXML text. Original XML,
  historical attempts, their score versions/divisions, and scoring history are
  not rewritten. New parser semantics increment `Score.CURRENT_VERSION` so old
  parsed caches are rebuilt from the original XML.
- Unversioned bookmarks for variable-divisions inputs cannot be converted using
  one factor. They are not restored as corrected positions. Other preferences
  remain, and the first version-aware save archives the original settings as
  `<scoreId>.legacy.json`. Uniform-resolution legacy bookmarks remain usable.
- New bookmarks record score version and divisions. Parser-version changes do
  not relabel historical note IDs as current ones.
- Downgrading the application is not a supported cache/settings migration.
  Original XML and archived legacy settings remain available; old attempts stay
  in their original schema. Consumers must not compare historical and new note
  identities without checking score version.

## Phase 1: exact divisions normalization

Effective per-part divisions are combined using their least common multiple.
Notes, rests, backup and forward durations are converted exactly before time
accumulation. Uniform-resolution scores retain their ticks and allocation order.
Invalid/nonpositive or excessive resolutions are rejected. Score parser version: 2.
Saving a cache preserves its actual parser version instead of stamping stale data
as current.

Full desktop suite: **160 executed, 155 passed, 5 failed, 0 errors/skipped**.
The five failures are the two original tie regressions, two loop regressions,
and tempo-change regression. Divisions normalization, cache restoration,
bookmark compatibility, and both Compose UI tests pass.

Cloud command (from `/workspace/WindScore`):

```sh
GRADLE_USER_HOME=/workspace/.gradle ANDROID_USER_HOME=/workspace/.android \
JAVA_TOOL_OPTIONS='-Djavax.net.ssl.trustStore=/workspace/.windscore-setup/cacerts -Duser.home=/workspace/.windscore-setup/home' \
bash gradlew :composeApp:desktopTest --no-daemon --console=plain
```

The writable Java home is required for Skiko native extraction in this sandbox.
It changes runtime directories only; TLS verification remains enabled. Phase logs
are outside the checkout under `/workspace/.windscore-setup`.


## Phase 2: tie identity and continuity

Tie matching uses part, voice, sounding pitch, optional explicit tied number,
and exact endpoint continuity. Staff is excluded. Multiple open unisons are
retained rather than overwritten, and numbered stop/start chains may change
numbers. Existing practice-hand assignment is retained from the attack.

Ambiguity policy: voice changes, gaps and unmatched
stops remain independent attacks with an import warning. No cross-voice connection
is guessed. Multiple equally eligible unnumbered ties produce a clear import
error; explicit tied numbers resolve those unisons. These are conservative
boundaries, not a complete claim about all possible notational tie relationships.
Score parser version: 3; original source identity and historical data remain.

Full desktop suite: **166 executed, 163 passed, 3 failed, 0 errors/skipped**.
The remaining failures are the two loop regressions and tempo-change regression.
Both original tie regressions and six focused tie edge-case tests pass.

A follow-up check of the official MusicXML schema confirms explicit ties between
enharmonically equivalent pitches are allowed. Tie identity therefore uses
sounding pitch rather than spelling; the focused test covers C-sharp to D-flat.
Source: https://github.com/w3c/musicxml/blob/master/schema/musicxml.xsd


## Phase 3: loop endpoints and empty regions

Playback dispatch is capped at B before wrapping; onsets at B or beyond are
excluded even if a scheduler update overshoots. Sounding notes are released at B.
Existing 500 ms end/start presentation pauses are retained. Off events without
an active note no longer emit unrelated releases.

Judgment seek/reset selects only groups inside `[A,B)`. Empty regions retain the
selected range and position at B with no pending group, no hosted scheduling,
and no performance-completion evidence. Clearing/resetting returns to full-score
practice. Seek/reset clears stale transition evidence. No event representation
or cache version change is needed for this phase.

Full desktop suite (including the enharmonic tie refinement): **170 executed,
169 passed, 1 failed, 0 errors/skipped**. Only the original tempo-change regression
remains. Original loop regressions and focused overshoot, sustain cutoff,
beyond-last-note, B-seek and zero-length-region checks pass.


## Phase 4: tempo maps and transport rebasing

`TempoChange` stores quarter-notes-per-minute at a canonical tick. `ScoreTiming`
integrates that piecewise map and provides its inverse. MusicXML sound tempo,
decimal BPM, standalone sound elements and playback offsets are retained.
Fractional playback offsets extend the exact canonical resolution when needed;
visual-only direction offsets do not move sound. Sound-level offsets take
precedence. Initial tempo remains available as rounded compatibility metadata;
legacy fixed-tempo scores retain their existing 120 BPM fallback when unspecified.

Ambiguity policy: contradictory simultaneous tempos in different parts are
reported as import errors. The last declaration at the same position within one
part wins. Tempo 0 requests explicit user input under MusicXML; this application
has no such import prompt, so it reports that requirement instead of silently
choosing a speed. Nonpositive/nonfinite tempos are rejected. Repeat unfolding and
visual metronome-only directions are outside this fix.

Playback applies speed to elapsed time through the map without rewriting note
onsets. Pause, seek, reset and loop presentation pauses rebase elapsed time.
Commands and scheduler mutations use the existing `SessionLane`; generation
ownership stops cancelled jobs from changing the replacement transport state.
Paused/seeked sustains are restored when playback resumes, while notes beginning
outside the selected loop remain excluded. Existing anti-burst lag handling and
loop presentation pauses are retained.

Hosted wait-mode intervals and note durations use the same map. Waiting for a
user does not independently advance score position. Hosted sustains are no longer
clipped to 60–4000 ms: musical duration is rounded up to at least 1 ms and clipped
at an active loop's B boundary. A focused test observes a five-second sustain
across a tempo change. Variable-tempo attempts do not claim a single fixed BPM;
fixed decimal-tempo metadata is calculated before rounding. Historical attempts
are not rewritten.

Score parser version: 4. Missing serialized version still decodes as 1, ensuring
legacy caches omitting default fields cannot masquerade as current. Cache upgrades
retain the original previous model as `<scoreId>.cache.v<parserVersion>` for
historical tick/group interpretation, without overwriting an existing archive.
One failed source reparse does not hide unrelated valid library entries; the
original source and persisted index are retained by loading.

Final complete desktop suite: **189 executed, 189 passed, 0 failures/errors/skipped**.
All six original regression tests and their assertions are byte-for-byte unchanged
from the baseline commit. Coverage includes timing integration/inverse, decimal
and fractional offsets, multi-part tempo agreement/conflicts, versioned cache
round-trips and archives, metadata, speed, pause/resume, playing seek/reset, loop
clock rebasing, hosted wait-mode timing and long sustains. The fake-clock transport
tests control monotonic readings while retaining the real coroutine scheduler.
Original tempo and hosted-duration regressions also observe real elapsed time.

An intermediate hosted test observed the audio callback before session state
publication completed. Its state assertion now synchronizes through SessionLane;
no musical assertion was relaxed. This resolves observer ordering rather than
hiding a timeout or changing the expected tick.

Android APK/device builds and physical MIDI/audio-device checks were not run.
Shared domain/JVM code, all common and desktop tests, and both Compose UI tests
were validated through the desktop task. No MEI, Verovio, dependency changes,
commits to remote repositories, or pull requests are involved.
