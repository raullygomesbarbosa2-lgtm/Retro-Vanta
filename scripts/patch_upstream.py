#!/usr/bin/env python3
"""Patch the pinned Lemuroid source into the Retro Vanta build."""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, found {count}: {old!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


app_build = ROOT / "lemuroid-app/build.gradle.kts"
replace_once(app_build, 'versionCode = 252', 'versionCode = 1')
replace_once(app_build, 'versionName = "1.17.0"', 'versionName = "1.0.0"')
replace_once(app_build, 'applicationId = "com.swordfish.lemuroid"', 'applicationId = "com.raullygomesbarbosa2.retrovanta"')
replace_once(app_build, 'resValue("string", "lemuroid_name", "Lemuroid")', 'resValue("string", "lemuroid_name", "Retro Vanta")')
replace_once(app_build, 'resValue("string", "lemuroid_name", "LemuroiDebug")', 'resValue("string", "lemuroid_name", "Retro Vanta")')

# Replace the displayed product name in the app's translated text.
for strings in (ROOT / "lemuroid-app/src/main/res").glob("values*/strings.xml"):
    original = strings.read_text(encoding="utf-8")
    updated = original.replace("Lemuroid", "Retro Vanta")
    if updated != original:
        strings.write_text(updated, encoding="utf-8")

manifest = ROOT / "lemuroid-app/src/main/AndroidManifest.xml"
manifest_text = manifest.read_text(encoding="utf-8")
manifest.write_text(manifest_text.replace('android:scheme="lemuroid"', 'android:scheme="retrovanta"'), encoding="utf-8")

systems = ROOT / "retrograde-app-shared/src/main/java/com/swordfish/lemuroid/lib/library/GameSystem.kt"
marker = "            )\n\n        private val byIdCache"
replacement = """            ).filter { system ->
                system.id in setOf(
                    SystemID.ATARI2600,
                    SystemID.ATARI7800,
                    SystemID.LYNX,
                    SystemID.NES,
                    SystemID.SNES,
                    SystemID.SMS,
                    SystemID.GENESIS,
                    SystemID.SEGACD,
                    SystemID.GB,
                    SystemID.GBC,
                    SystemID.GBA,
                    SystemID.GG,
                    SystemID.FBNEO,
                    SystemID.MAME2003PLUS,
                    SystemID.PC_ENGINE,
                    SystemID.NGP,
                    SystemID.NGC,
                    SystemID.WS,
                    SystemID.WSC,
                )
            }

        private val byIdCache"""
replace_once(systems, marker, replacement)

# Remove libraries for excluded systems from the bundled APK.
core_libs = ROOT / "lemuroid-cores/bundled-cores/src/main/jniLibs"
excluded_markers = ("pcsx_rearmed", "ppsspp", "mupen64plus", "desmume", "melonds", "citra", "dosbox_pure")
removed = 0
if core_libs.exists():
    for library in core_libs.rglob("*.so"):
        if any(marker in library.name.lower() for marker in excluded_markers):
            library.unlink()
            removed += 1
print(f"Retro Vanta source configured; removed {removed} unsupported core binaries.")
