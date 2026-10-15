# Bundled OpenSheetMusicDisplay

Both platform copies of `opensheetmusicdisplay.min.js` are the **unmodified official GitHub release asset**, pinned by `manifest.json`. No CDN or npm runtime is used. The source map is optional developer tooling and is not shipped.

To reproduce the download:

```sh
curl -fL https://github.com/opensheetmusicdisplay/opensheetmusicdisplay/releases/download/2.2.1/opensheetmusicdisplay.min.js -o /tmp/osmd-2.2.1.js
echo '537ed08f9c082ce0d785d21b80485b8d443cfb92ddc7a4ba4977804f09408cf4  /tmp/osmd-2.2.1.js' | sha256sum -c -
```

The checksum is also published in the official GitHub release API's asset `digest`. Copy the verified file to `composeApp/src/desktopMain/resources/` and `composeApp/src/androidMain/assets/`, keeping its original filename. `OsmdBundleTest`, Android host coverage, and `tools/validate_android_apks.py` check the copies and packaged assets.

`LICENSE` is upstream's BSD-3-Clause license from the pinned Git tag. The adjacent `opensheetmusicdisplay.min.js.LICENSE.txt` files in **both platform asset directories** retain that license and the bundled dependency notices. Those notices were collected from upstream license files in published npm packages and the isarray README's MIT section; JSZip is distributed under its MIT option. The dependency notice labels identify their source packages rather than constituting a complete runtime dependency inventory. The license files are shipped offline with the JavaScript.

The verified official npm 2.2.1 bundle has an identical JavaScript prefix; the GitHub asset additionally contains its source-map URL. The GitHub asset is used as requested. Its source map, SHA-256 `523dfb6b784103d8000443017fb4cf5d1e9ae1fb166f648ed1d36be80ed184cd`, was consulted for dependency-source/API review, not installed or shipped.

Snapshot cache keys hash the actual asset bytes as well as XML, HTML and renderer family. An asset update therefore cannot silently reuse the previous renderer's PNGs. This does not change parsed score versions, musical ticks, note IDs, bookmarks or practice history.
