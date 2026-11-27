"""Verify standalone Android APKs from Gradle's preview outputs and stage release assets."""

import json
from pathlib import Path
import shutil
import subprocess
import sys
from zipfile import ZIP_DEFLATED, ZipFile

ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
PACKAGES = {"Rider": "com.ridenow.app", "Driver": "com.ridenow.driver"}


def stage_app(root, tools, release, app, package):
    build = root / f"{app.lower()}-build" / "app" / "build" / "outputs"
    mapping = build / "mapping" / "preview" / "mapping.txt"
    if not mapping.is_file() or mapping.stat().st_size == 0:
        raise SystemExit(f"Missing R8 mapping for {app}")

    output = build / "apk" / "preview"
    metadata = json.loads((output / "output-metadata.json").read_text())
    variants = set()
    for entry in metadata["elements"]:
        filters = entry["filters"]
        if filters and (len(filters) != 1 or filters[0]["filterType"] != "ABI"):
            raise SystemExit(f"Unexpected APK filters for {app}: {filters}")
        variant = filters[0]["value"] if filters else "universal"
        if variant not in ABIS | {"universal"} or variant in variants:
            raise SystemExit(f"Unexpected or duplicate APK variant for {app}: {variant}")

        apk = output / entry["outputFile"]
        expected_abis = ABIS if variant == "universal" else {variant}
        with ZipFile(apk) as archive:
            native = {
                info.filename: info
                for info in archive.infolist()
                if info.filename.startswith("lib/") and info.filename.endswith(".so")
            }
            actual_abis = {name.split("/")[1] for name in native}
            if actual_abis != expected_abis:
                raise SystemExit(f"{apk.name} contains {actual_abis}, expected {expected_abis}")
            for abi in expected_abis:
                info = native.get(f"lib/{abi}/libmaplibre.so")
                if info is None or info.compress_type != ZIP_DEFLATED:
                    raise SystemExit(f"{apk.name} is missing a compressed MapLibre library for {abi}")

        subprocess.run([tools / "apksigner", "verify", apk], check=True)
        info = subprocess.check_output([tools / "aapt", "dump", "badging", apk], text=True)
        expected_package = (
            f"package: name='{package}' "
            f"versionCode='{entry['versionCode']}' versionName='{entry['versionName']}' "
        )
        if not info.startswith(expected_package):
            raise SystemExit(f"Unexpected application ID or version in {apk.name}")

        manifest = subprocess.check_output(
            [tools / "aapt", "dump", "xmltree", apk, "AndroidManifest.xml"], text=True
        )
        for line in map(str.strip, manifest.splitlines()):
            if line.startswith(("E: uses-split ", "A: split=", "A: android:configForSplit")) or (
                line.startswith("A: android:isSplitRequired") and "0xffffffff" in line
            ):
                raise SystemExit(f"{apk.name} requires another APK to install")

        variants.add(variant)
        asset = release / f"RideNow-{app}-{variant}.apk"
        shutil.copyfile(apk, asset)
        print(f"{asset.name}: {asset.stat().st_size / 1_000_000:.2f} MB")

    if variants != ABIS | {"universal"}:
        raise SystemExit(f"{app} APK outputs are incomplete: {variants}")


if __name__ == "__main__":
    root, tools = map(Path, sys.argv[1:])
    release = root / "android-release"
    release.mkdir(parents=True, exist_ok=True)
    for app, package in PACKAGES.items():
        stage_app(root, tools, release, app, package)
