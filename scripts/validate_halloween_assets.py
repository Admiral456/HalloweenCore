#!/usr/bin/env python3
from pathlib import Path
import json
import re
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
VANILLA_SOUNDS_CONFIG = CONTENT / "resourcepack" / "assets" / "minecraft" / "sounds.json"
SILENT_MUSIC = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "sounds" / "halloween_silence.ogg"
ARMOR_SOURCE_TEXTURES = CONTENT / "textures" / "armor" / "crimson_warden"

EXPECTED = {
    "hunter_mask.png",
    "cursed_talisman.png",
    "halloween_token.png",
    "cursed_candy.png",
    "haunted_map.png",
    "crimson_warden_helmet.png",
    "crimson_warden_chestplate.png",
    "crimson_warden_leggings.png",
    "crimson_warden_boots.png",
}
ARMOR_TEXTURES = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "armor" / "crimson_warden"


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

for layer in ("layer_1.png", "layer_2.png"):
    path = ARMOR_TEXTURES / layer
    if not path.is_file():
        fail("Missing Crimson Warden armor layer: " + str(path.relative_to(ROOT)))
    width, height = png_size(path)
    if (width, height) != (64, 32):
        fail(f"{layer} must be 64x32, got {width}x{height}")

if not SHADER.is_file():
    fail("Missing Halloween sky shader: " + str(SHADER.relative_to(ROOT)))
shader_text = SHADER.read_text(encoding="utf-8")
for marker in ("#version 330", "#moj_import <minecraft:fog.glsl>", "halloweenTint", "fragColor = sky;"):
    if marker not in shader_text:
        fail(f"Sky shader is missing required Minecraft 1.21.10 marker: {marker}")

theme_duration = 0.0
for audio, expected_duration in ((THEME, None), (EVENT_STING, 5.0)):
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
    actual_duration = float(stream.get("duration", 0))
    if audio == THEME:
        theme_duration = actual_duration
        if actual_duration < 10.0:
            fail("Spooky Fester ambient theme is unexpectedly short")
    elif expected_duration is not None and abs(actual_duration - expected_duration) > 0.06:
        fail(f"{audio.name} must be {expected_duration:.0f} seconds long")

if not VANILLA_SOUNDS_CONFIG.is_file():
    fail("Missing vanilla music suppression file: " + str(VANILLA_SOUNDS_CONFIG.relative_to(ROOT)))
try:
    vanilla_sound_events = json.loads(VANILLA_SOUNDS_CONFIG.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as exc:
    fail(f"Invalid resource-pack sounds.json: {exc}")
if not isinstance(vanilla_sound_events, dict) or len(vanilla_sound_events) < 40:
    fail("Vanilla sounds.json must override the supported vanilla music event set")
for event_id in ("music.game", "music.menu", "music.creative", "music.under_water", "music.nether.crimson_forest"):
    definition = vanilla_sound_events.get(event_id)
    if not isinstance(definition, dict) or definition.get("replace") is not True:
        fail(f"Vanilla music event {event_id} is not explicitly replaced")
    sounds = definition.get("sounds", [])
    if not sounds or sounds[0].get("name") != "warriorland_halloween:halloween_silence":
        fail(f"Vanilla music event {event_id} must play halloween_silence.ogg")

if not SILENT_MUSIC.is_file():
    fail("Missing silent OGG used to suppress vanilla music: " + str(SILENT_MUSIC.relative_to(ROOT)))
if SILENT_MUSIC.read_bytes()[:4] != b"OggS":
    fail("halloween_silence.ogg is not an OGG container")
try:
    silence_info = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "a:0",
         "-show_entries", "stream=codec_name,sample_rate,channels,duration",
         "-of", "json", str(SILENT_MUSIC)],
        check=True, capture_output=True, text=True,
    )
    silence_stream = json.loads(silence_info.stdout)["streams"][0]
except (OSError, subprocess.CalledProcessError, ValueError, KeyError, IndexError) as exc:
    fail(f"Could not inspect silent music asset: {exc}")
if silence_stream.get("codec_name") != "vorbis" or int(silence_stream.get("sample_rate", 0)) != 22050 or int(silence_stream.get("channels", 0)) != 1:
    fail("halloween_silence.ogg must be mono 22050Hz OGG/Vorbis")
if not 0.8 <= float(silence_stream.get("duration", 0)) <= 1.2:
    fail("halloween_silence.ogg must be about one second long")

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
loop_match = re.search(r"(?m)^  loop-milliseconds:\s*(\d+)\s*$", runtime_config)
if not loop_match:
    fail("Runtime config must define atmosphere.loop-milliseconds")
configured_loop_ms = int(loop_match.group(1))
if abs(configured_loop_ms - round(theme_duration * 1000)) > 100:
    fail(
        f"Ambient playback interval ({configured_loop_ms} ms) does not match "
        f"Spooky Fester duration ({round(theme_duration * 1000)} ms)"
    )

config = CONFIG.read_text(encoding="utf-8")
if "namespace: warriorland_halloween" not in config:
    fail("ItemsAdder namespace missing from items.yml")

for item_id in ("hunter_mask", "cursed_talisman", "halloween_token", "cursed_candy", "haunted_map",
                "crimson_warden_helmet", "crimson_warden_chestplate",
                "crimson_warden_leggings", "crimson_warden_boots"):
    if f"  {item_id}:" not in config:
        fail(f"ItemsAdder item '{item_id}' missing from items.yml")

# ItemsAdder equipment layers are source assets at contents/<namespace>/textures/armor,
# while item icons and vanilla overrides are emitted from the resourcepack/assets tree.
for layer in ("layer_1.png", "layer_2.png"):
    path = ARMOR_SOURCE_TEXTURES / layer
    if not path.is_file():
        fail("Missing ItemsAdder source armor layer: " + str(path.relative_to(ROOT)))
    width, height = png_size(path)
    if (width, height) != (64, 32):
        fail(f"ItemsAdder source {layer} must be 64x32, got {width}x{height}")

root_textures = CONTENT / "textures"
if root_textures.exists():
    unexpected = [p for p in root_textures.rglob("*") if p.is_file()
                  and not (p.parent == ARMOR_SOURCE_TEXTURES and p.name in ("layer_1.png", "layer_2.png"))]
    if unexpected:
        fail("Unexpected ItemsAdder top-level texture files: " + ", ".join(str(p.relative_to(ROOT)) for p in unexpected))

print("Halloween asset validation passed.")
print("9 item textures: 32x32 PNG; 2 armor layers: 64x32 PNG")
print("ItemsAdder namespace: warriorland_halloween")
print("Sky shader: Minecraft 1.21.10 entry point present")
print(f"Audio: Spooky Fester ambience ({round(theme_duration * 1000)} ms) + 5s event cue, mono OGG/Vorbis containers")
print("ItemsAdder sound definitions: present")
print(f"Vanilla music events suppressed: {len(vanilla_sound_events)}")
print("Halloween silent OGG: mono OGG/Vorbis, about 1 second")
print("Content layout: structure method 2")
