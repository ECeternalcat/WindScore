#!/usr/bin/env python3
"""Verify built Android packages contain the exact validated score, OSMD and MIDI library."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "composeApp/src/androidMain/assets"
OUTPUT = ROOT / "composeApp/build/validation/android-packages.json"


def main():
    results = []
    for variant in ("debug", "release"):
        packages = list((ROOT / f"androidApp/build/outputs/apk/{variant}").glob("*.apk"))
        assert len(packages) == 1, f"Build exactly one {variant} APK before validation"
        package = packages[0]
        assets = {}
        with zipfile.ZipFile(package) as archive:
            for name in ("konekuto-claris.mxl", "opensheetmusicdisplay.min.js", "opensheetmusicdisplay.min.js.LICENSE.txt"):
                actual = archive.read(f"assets/{name}")
                assert actual == (ASSETS / name).read_bytes(), f"Packaged asset differs: {variant}/{name}"
                assert actual == (ROOT / "composeApp/src/desktopMain/resources" / name).read_bytes()
                assets[name] = hashlib.sha256(actual).hexdigest()
            abis = sorted({name.split("/")[1] for name in archive.namelist()
                           if name.startswith("lib/") and name.endswith(".so")})
            assert abis == ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"], abis
            for abi in abis:
                assert f"lib/{abi}/libmidi.so" in archive.namelist(), f"MIDI synth missing on {abi}"
        results.append(dict(variant=variant, path=str(package.relative_to(ROOT)),
            sizeBytes=package.stat().st_size, sha256=hashlib.sha256(package.read_bytes()).hexdigest(),
            assetSha256=assets, nativeAbis=abis))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(results, indent=2) + "\n")
    print(OUTPUT.read_text())


if __name__ == "__main__":
    main()
