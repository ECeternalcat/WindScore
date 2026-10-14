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
