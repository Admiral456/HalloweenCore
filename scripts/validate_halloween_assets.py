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
    "crimson_warden_sword.png",
    "crimson_warden_pickaxe.png",
    "crimson_warden_axe.png",
    "crimson_warden_shovel.png",
    "crimson_warden_hoe.png",
}
ARMOR_TEXTURES = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "armor" / "crimson_warden"


EXPECTED_MUSIC_EVENTS = {
    "music.creative", "music.credits", "music.dragon", "music.end", "music.game", "music.menu",
    "music.nether.basalt_deltas", "music.nether.crimson_forest", "music.nether.nether_wastes",
    "music.nether.soul_sand_valley", "music.nether.warped_forest",
    "music.overworld.badlands", "music.overworld.bamboo_jungle", "music.overworld.cherry_grove",
    "music.overworld.deep_dark", "music.overworld.desert", "music.overworld.dripstone_caves",
    "music.overworld.flower_forest", "music.overworld.forest", "music.overworld.frozen_peaks",
    "music.overworld.grove", "music.overworld.jagged_peaks", "music.overworld.jungle",
    "music.overworld.lush_caves", "music.overworld.meadow", "music.overworld.old_growth_taiga",
    "music.overworld.snowy_slopes", "music.overworld.sparse_jungle", "music.overworld.stony_peaks",
    "music.overworld.swamp", "music.under_water",
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
if not isinstance(vanilla_sound_events, dict) or set(vanilla_sound_events) != EXPECTED_MUSIC_EVENTS:
    missing_events = sorted(EXPECTED_MUSIC_EVENTS - set(vanilla_sound_events))
    unexpected_events = sorted(set(vanilla_sound_events) - EXPECTED_MUSIC_EVENTS)
    fail(f"Vanilla music overrides differ from Minecraft 1.21.10. Missing={missing_events}; unexpected={unexpected_events}")
for event_id in sorted(EXPECTED_MUSIC_EVENTS):
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
if not re.search(r"(?m)^  volume:\\s*3\\.0\\s*$", runtime_config):
    fail("Halloween ambient volume must be configured to 3.0 (three times full-volume gain)")
if "volume-tripled-v2-migrated: true" not in runtime_config:
    fail("Default config must declare the one-time 3x volume migration marker")

atmosphere_java = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenAtmosphere.java").read_text(encoding="utf-8")
if "SoundCategory.AMBIENT, volume, pitch" not in atmosphere_java:
    fail("Halloween theme must use the AMBIENT category so vanilla MUSIC can be stopped independently")
if "player.stopSound(SoundCategory.MUSIC)" not in atmosphere_java or "}, 1L, 20L);" not in atmosphere_java:
    fail("Halloween ambience must actively stop the vanilla MUSIC category at least once per second")

passive_java = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenPassiveEffectManager.java").read_text(encoding="utf-8")
if "Attribute.MAX_HEALTH" not in passive_java or "getItemInOffHand()" not in passive_java:
    fail("Cursed talisman must add max health while held in either hand")


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
                "crimson_warden_leggings", "crimson_warden_boots",
                "crimson_warden_sword", "crimson_warden_pickaxe", "crimson_warden_axe",
                "crimson_warden_shovel", "crimson_warden_hoe"):
    if f"  {item_id}:" not in config:
        fail(f"ItemsAdder item '{item_id}' missing from items.yml")

shop_config = (ROOT / "src" / "main" / "resources" / "config.yml").read_text(encoding="utf-8")
if "      cost: 1000000" not in shop_config or "      bonus-health: 20.0" not in shop_config:
    fail("Cursed talisman must cost 1,000,000 fragments and grant +20 max health")
for item_id in ("crimson_warden_sword", "crimson_warden_pickaxe", "crimson_warden_axe",
                "crimson_warden_shovel", "crimson_warden_hoe"):
    start = config.find(f"  {item_id}:")
    if start < 0:
        fail(f"Custom gear '{item_id}' missing from ItemsAdder config")
    end = config.find("\n  ", start + 4)
    definition = config[start:end if end >= 0 else len(config)]
    if "material: NETHERITE_" not in definition or "attribute_modifiers:" not in definition or "durability:" not in definition:
        fail(f"Custom gear '{item_id}' needs a netherite base material, extra attributes, and durability")
    if "blocked_enchants:" in definition and "blocked_enchants:\n      - ALL" in definition:
        fail(f"Custom gear '{item_id}' must remain enchantable")

expected_gear_stats = {
    "crimson_warden_sword": ("attackDamage: 11.0", "attackSpeed: -2.0"),
    "crimson_warden_pickaxe": ("attackDamage: 8.0", "attackSpeed: -1.8"),
    "crimson_warden_axe": ("attackDamage: 12.0", "attackSpeed: -2.4"),
    "crimson_warden_shovel": ("attackDamage: 8.0", "attackSpeed: -1.8"),
    "crimson_warden_hoe": ("attackDamage: 4.0", "attackSpeed: 0.0"),
}
for item_id, expected_stats in expected_gear_stats.items():
    for stat in expected_stats:
        if stat not in definition:
            fail(f"Custom gear '{item_id}' is missing expected stronger-than-netherite stat: {stat}")


shop_ids = ("crimson-warden-sword", "crimson-warden-pickaxe", "crimson-warden-axe",
            "crimson-warden-shovel", "crimson-warden-hoe")
for shop_id in shop_ids:
    if f"    {shop_id}:" not in shop_config:
        fail(f"Shop reward '{shop_id}' missing from config.yml")

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
print("14 item textures: 32x32 PNG; 2 armor layers: 64x32 PNG")
print("ItemsAdder namespace: warriorland_halloween")
print("Sky shader: Minecraft 1.21.10 entry point present")
print(f"Audio: Spooky Fester ambience ({round(theme_duration * 1000)} ms) + 5s event cue, mono OGG/Vorbis containers")
print("ItemsAdder sound definitions: present")
print(f"Verified exact Minecraft 1.21.10 music-event overrides: {len(vanilla_sound_events)}")
print("Halloween silent OGG: mono OGG/Vorbis, about 1 second")
print("Content layout: structure method 2")
