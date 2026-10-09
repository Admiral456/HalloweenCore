#!/usr/bin/env python3
from pathlib import Path
import json
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "itemsadder" / "contents" / "warriorland_halloween"
TEXTURES = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "item"
SHADER = CONTENT / "resourcepack" / "assets" / "minecraft" / "shaders" / "core" / "sky.fsh"
CONFIG = CONTENT / "configs" / "items.yml"
SOUNDS_CONFIG = CONTENT / "configs" / "sounds.yml"
THEME = CONTENT / "sounds" / "haunted_theme.ogg"
EVENT_STING = CONTENT / "sounds" / "event_sting.ogg"

EXPECTED = {
    "hunter_mask.png",
    "cursed_talisman.png",
    "halloween_token.png",
    "cursed_candy.png",
    "haunted_map.png",
}

def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG file")
    if data[12:16] != b"IHDR":
        raise ValueError(f"{path} has no PNG IHDR chunk")
    return struct.unpack(">II", data[16:24])

def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)

missing = sorted(name for name in EXPECTED if not (TEXTURES / name).is_file())
if missing:
    fail("Missing item textures: " + ", ".join(missing))

for name in sorted(EXPECTED):
    width, height = png_size(TEXTURES / name)
    if (width, height) != (32, 32):
        fail(f"{name} must be 32x32, got {width}x{height}")

if not SHADER.is_file():
    fail("Missing Halloween sky shader: " + str(SHADER.relative_to(ROOT)))
shader_text = SHADER.read_text(encoding="utf-8")
for marker in ("#version 330", "#moj_import <minecraft:fog.glsl>", "halloweenTint", "fragColor = sky;"):
    if marker not in shader_text:
        fail(f"Sky shader is missing required Minecraft 1.21.10 marker: {marker}")

for audio, expected_duration in ((THEME, 64.0), (EVENT_STING, 5.0)):
    if not audio.is_file():
        fail("Missing Halloween audio asset: " + str(audio.relative_to(ROOT)))
    if audio.read_bytes()[:4] != b"OggS":
        fail(str(audio.relative_to(ROOT)) + " is not a valid OGG container")
    try:
        metadata = subprocess.run(
            [
                "ffprobe", "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=codec_name,sample_rate,channels,duration",
                "-of", "json", str(audio),
            ],
            check=True, capture_output=True, text=True,
        )
        stream = json.loads(metadata.stdout)["streams"][0]
    except (OSError, subprocess.CalledProcessError, ValueError, KeyError, IndexError) as exc:
        fail(f"Could not inspect audio stream {audio.name}: {exc}")
    if stream.get("codec_name") != "vorbis":
        fail(f"{audio.name} must use the Vorbis codec")
    if int(stream.get("sample_rate", 0)) != 22050:
        fail(f"{audio.name} must use 22050 Hz sample rate")
    if int(stream.get("channels", 0)) != 1:
        fail(f"{audio.name} must be mono")
    if abs(float(stream.get("duration", 0)) - expected_duration) > 0.06:
        fail(f"{audio.name} must be {expected_duration:.0f} seconds long")

if not SOUNDS_CONFIG.is_file():
    fail("Missing ItemsAdder sounds configuration: " + str(SOUNDS_CONFIG.relative_to(ROOT)))
sounds_text = SOUNDS_CONFIG.read_text(encoding="utf-8")
for sound in ("haunted_theme", "event_sting"):
    if f"  {sound}:" not in sounds_text:
        fail(f"ItemsAdder sound '{sound}' missing from sounds.yml")

runtime_config = (ROOT / "src" / "main" / "resources" / "config.yml").read_text(encoding="utf-8")
for sound_id in ("warriorland_halloween:event_sting", "warriorland_halloween:haunted_theme"):
    if sound_id not in runtime_config:
        fail(f"Plugin configuration is missing sound ID: {sound_id}")
if "loop-milliseconds: 64000" not in runtime_config:
    fail("Ambient playback interval must match the 64-second generated theme loop")

config = CONFIG.read_text(encoding="utf-8")
if "namespace: warriorland_halloween" not in config:
    fail("ItemsAdder namespace missing from items.yml")

for item_id in ("hunter_mask", "cursed_talisman", "halloween_token", "cursed_candy", "haunted_map"):
    if f"  {item_id}:" not in config:
        fail(f"ItemsAdder item '{item_id}' missing from items.yml")

root_textures = CONTENT / "textures"
if root_textures.exists():
    fail("Mixed ItemsAdder content layout detected: top-level textures/ exists; use resourcepack/assets layout only")

print("Halloween asset validation passed.")
print("5 textures: 32x32 PNG")
print("ItemsAdder namespace: warriorland_halloween")
print("Sky shader: Minecraft 1.21.10 entry point present")
print("Audio: 64s ambience + 5s event cue, mono OGG/Vorbis containers")
print("ItemsAdder sound definitions: present")
print("Content layout: structure method 2")
