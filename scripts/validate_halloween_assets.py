#!/usr/bin/env python3
from pathlib import Path
import json
import re
import struct
import subprocess
import sys
import zlib

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
    "crimson_warden_shovel.png",
    "crimson_warden_pickaxe.png",
    "crimson_warden_axe.png",
    "crimson_warden_hoe.png",
}
ARMOR_TEXTURES = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "armor" / "crimson_warden"


def png_size(path: Path) -> tuple[int, int]:
    """Read dimensions and validate the complete PNG chunk/CRC/IDAT stream."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG file")
    offset = 8
    width = height = 0
    saw_header = saw_data = saw_end = False
    idat = bytearray()
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        payload_start = offset + 8
        payload_end = payload_start + length
        crc_end = payload_end + 4
        if crc_end > len(data):
            raise ValueError(f"{path} has a truncated PNG chunk")
        payload = data[payload_start:payload_end]
        saved_crc = struct.unpack(">I", data[payload_end:crc_end])[0]
        actual_crc = zlib.crc32(chunk_type + payload) & 0xFFFFFFFF
        if saved_crc != actual_crc:
            raise ValueError(f"{path} has a corrupt PNG chunk {chunk_type.decode('ascii', 'replace')}")
        if chunk_type == b"IHDR":
            if saw_header or length != 13:
                raise ValueError(f"{path} has an invalid IHDR")
            width, height = struct.unpack(">II", payload[:8])
            if width <= 0 or height <= 0:
                raise ValueError(f"{path} has invalid dimensions")
            saw_header = True
        elif chunk_type == b"IDAT":
            if not saw_header or saw_end:
                raise ValueError(f"{path} has a misplaced IDAT chunk")
            idat.extend(payload)
            saw_data = True
        elif chunk_type == b"IEND":
            if length != 0 or not saw_data:
                raise ValueError(f"{path} has an invalid IEND or no image data")
            saw_end = True
            offset = crc_end
            break
        offset = crc_end
    if not (saw_header and saw_data and saw_end) or offset != len(data):
        raise ValueError(f"{path} is incomplete or contains trailing data")
    try:
        zlib.decompress(bytes(idat))
    except zlib.error as exc:
        raise ValueError(f"{path} has a broken compressed image stream: {exc}") from exc
    return width, height

def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)

missing = sorted(name for name in EXPECTED if not (TEXTURES / name).is_file())
if missing:
    fail("Missing item textures: " + ", ".join(missing))

for name in sorted(EXPECTED):
    try:
        width, height = png_size(TEXTURES / name)
    except (OSError, ValueError, struct.error) as exc:
        fail(f"Invalid item texture {name}: {exc}")
    if (width, height) != (32, 32):
        fail(f"{name} must be 32x32, got {width}x{height}")

for layer in ("layer_1.png", "layer_2.png"):
    path = ARMOR_TEXTURES / layer
    if not path.is_file():
        fail("Missing Crimson Warden armor layer: " + str(path.relative_to(ROOT)))
    try:
        width, height = png_size(path)
    except (OSError, ValueError, struct.error) as exc:
        fail(f"Invalid armor texture {layer}: {exc}")
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
                "crimson_warden_leggings", "crimson_warden_boots",
                "crimson_warden_sword", "crimson_warden_shovel", "crimson_warden_pickaxe",
                "crimson_warden_axe", "crimson_warden_hoe"):
    if f"  {item_id}:" not in config:
        fail(f"ItemsAdder item '{item_id}' missing from items.yml")

# Audit custom gear definitions: every item must use its matching texture and keep vanilla enchantability.
GEAR_SPECS = {
    "crimson_warden_sword": ("NETHERITE_SWORD", 2500, "10", "-2.0"),
    "crimson_warden_shovel": ("NETHERITE_SHOVEL", 2500, "8", "-2.3"),
    "crimson_warden_pickaxe": ("NETHERITE_PICKAXE", 3000, "8", "-2.2"),
    "crimson_warden_axe": ("NETHERITE_AXE", 2800, "12", "-2.6"),
    "crimson_warden_hoe": ("NETHERITE_HOE", 2500, "4", "0.4"),
}
for item_id, (material, min_durability, damage, speed) in GEAR_SPECS.items():
    match = re.search(
        rf"(?ms)^  {re.escape(item_id)}:\n(.*?)(?=^  [a-z0-9_]+:\n|\Z)",
        config,
    )
    if not match:
        fail(f"Tool item '{item_id}' missing from items.yml")
    block = match.group(1)
    for required in (f"material: {material}", f"texture: item/{item_id}", "attribute_modifiers:", "mainhand:"):
        if required not in block:
            fail(f"Tool item '{item_id}' is missing {required}")
    if re.search(r"(?m)^\s*blocked_enchants:", block):
        fail(f"Tool item '{item_id}' must not block normal enchantments")
    durability = re.search(r"(?m)^\s*max_durability:\s*(\d+)", block)
    if not durability or int(durability.group(1)) < min_durability:
        fail(f"Tool item '{item_id}' durability must be at least {min_durability}")
    if not re.search(rf"(?m)^\s*attackDamage:\s*{re.escape(damage)}(?:\.0)?\s*$", block):
        fail(f"Tool item '{item_id}' has an unexpected attack-damage modifier")
    if not re.search(rf"(?m)^\s*attackSpeed:\s*{re.escape(speed)}\s*$", block):
        fail(f"Tool item '{item_id}' has an unexpected attack-speed modifier")

ARMOR_SPECS = {
    "crimson_warden_helmet": ("NETHERITE_HELMET", 800),
    "crimson_warden_chestplate": ("NETHERITE_CHESTPLATE", 1200),
    "crimson_warden_leggings": ("NETHERITE_LEGGINGS", 1000),
    "crimson_warden_boots": ("NETHERITE_BOOTS", 900),
}
for item_id, (material, min_durability) in ARMOR_SPECS.items():
    match = re.search(
        rf"(?ms)^  {re.escape(item_id)}:\n(.*?)(?=^  [a-z0-9_]+:\n|\Z)",
        config,
    )
    if not match:
        fail(f"Armor item '{item_id}' missing from items.yml")
    block = match.group(1)
    for required in (f"material: {material}", f"texture: item/{item_id}", "slot_attribute_modifiers:", "armor: 1"):
        if required not in block:
            fail(f"Armor item '{item_id}' is missing {required}")
    if re.search(r"(?m)^\s*blocked_enchants:", block):
        fail(f"Armor item '{item_id}' must not block normal enchantments")
    durability = re.search(r"(?m)^\s*max_durability:\s*(\d+)", block)
    if not durability or int(durability.group(1)) < min_durability:
        fail(f"Armor item '{item_id}' durability must be at least {min_durability}")

SHOP_CONFIG = (ROOT / "src" / "main" / "resources" / "config.yml").read_text(encoding="utf-8")
SHOP_IDS = {
    "hunter-mask": "hunter_mask",
    "cursed-talisman": "cursed_talisman",
    "halloween-token": "halloween_token",
    "crimson-warden-helmet": "crimson_warden_helmet",
    "crimson-warden-chestplate": "crimson_warden_chestplate",
    "crimson-warden-leggings": "crimson_warden_leggings",
    "crimson-warden-boots": "crimson_warden_boots",
    "crimson-warden-sword": "crimson_warden_sword",
    "crimson-warden-shovel": "crimson_warden_shovel",
    "crimson-warden-pickaxe": "crimson_warden_pickaxe",
    "crimson-warden-axe": "crimson_warden_axe",
    "crimson-warden-hoe": "crimson_warden_hoe",
}
shop_start = SHOP_CONFIG.find("  shop:")
shop_end = SHOP_CONFIG.find("\nhaunted-village:", shop_start)
if shop_start < 0 or shop_end < 0:
    fail("Could not locate the Halloween rewards.shop section")
shop_block = SHOP_CONFIG[shop_start:shop_end]
actual_shop_ids = re.findall(r"(?m)^    ([a-z0-9-]+):\s*$", shop_block)
if len(actual_shop_ids) < len(SHOP_IDS):
    fail(f"Expected at least {len(SHOP_IDS)} shop items, found {len(actual_shop_ids)}")
for shop_id, item_id in SHOP_IDS.items():
    match = re.search(
        rf"(?ms)^    {re.escape(shop_id)}:\n(.*?)(?=^    [a-z0-9-]+:\n|\Z)",
        shop_block,
    )
    if not match or f'itemsadder-id: "warriorland_halloween:{item_id}"' not in match.group(1):
        fail(f"Shop entry '{shop_id}' does not point to ItemsAdder item '{item_id}'")

REWARD_MANAGER = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenRewardManager.java").read_text(encoding="utf-8")
if 'Bukkit.createInventory(holder, 54,' not in REWARD_MANAGER:
    fail("Halloween shop must use a 54-slot inventory so all gear is visible")
slots_match = re.search(r"(?m)^\s*int\[\] slots = \{([^}]+)\};", REWARD_MANAGER)
if not slots_match or len(re.findall(r"\d+", slots_match.group(1))) < len(SHOP_IDS):
    fail(f"Halloween shop needs at least {len(SHOP_IDS)} reward slots for every configured reward")

# Static integration checks for arena, event scheduling, test spawning and reload.
JAVA = ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core"
ARENA_SOURCE = (JAVA / "VampireArenaBuilder.java").read_text(encoding="utf-8")
EVENT_SOURCE = (JAVA / "HalloweenEventManager.java").read_text(encoding="utf-8")
COMMAND_SOURCE = (JAVA / "HalloweenCommand.java").read_text(encoding="utf-8")
CORE_SOURCE = (JAVA / "HalloweenCore.java").read_text(encoding="utf-8")
BOSS_SOURCE = (JAVA / "HalloweenVampireEncounterManager.java").read_text(encoding="utf-8")
DECORATION_SOURCE = (JAVA / "HalloweenWorldDecorator.java").read_text(encoding="utf-8")

for marker in ("RADIUS = 48", "public Inspection inspect(", "public int build(", "Point[] towers", "Point[] gates", "Point[] obelisks"):
    if marker not in ARENA_SOURCE:
        fail(f"Epic arena builder is missing required feature: {marker}")
if not re.search(r"RADIUS\s*=\s*48", ARENA_SOURCE):
    fail("Epic arena must have a 97-block diameter (radius 48)")

for marker in ("scheduleFirstEvent()", "startEventNow(String requestedId)", "beginEvent(String eventId)", "runActiveEventEffects", "end-message"):
    if marker not in EVENT_SOURCE:
        fail(f"Event manager is missing required behavior: {marker}")
for event_id in ("soulstorm", "witching-hour", "cursed-harvest", "blood-moon-invasion"):
    if f'"{event_id}"' not in EVENT_SOURCE:
        fail(f"Event manager does not register event '{event_id}'")

for marker in (
    'args[0].equalsIgnoreCase("buildvampirearena")',
    'args[0].equalsIgnoreCase("event")',
    'args[0].equalsIgnoreCase("boss")',
    'args[0].equalsIgnoreCase("shader")',
    "startTestEncounter()",
    "plugin.reloadEventConfig()",
    'Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iazip")',
):
    if marker not in COMMAND_SOURCE:
        fail(f"Admin command wiring is missing: {marker}")

for marker in (
    "eventManager.scheduleFirstEvent()",
    "atmosphere.refreshPlayback()",
    "worldDecorator.scanLoadedChunks()",
    "migrateAtmosphereVolume()",
):
    if marker not in CORE_SOURCE:
        fail(f"Runtime reload/activation is missing: {marker}")

for marker in (
    "public boolean startTestEncounter()",
    "spawnTestFallback(location)",
    "testEncounter",
    "Test neodemkne finále",
):
    if marker not in BOSS_SOURCE:
        fail(f"Boss test mode is missing safety behavior: {marker}")

for marker in ("Material.JACK_O_LANTERN", "Material.PUMPKIN", "Material.COBWEB", "chunks-per-tick", "treeAnchor"):
    if marker not in DECORATION_SOURCE:
        fail(f"World Halloween decoration is missing: {marker}")

# ItemsAdder equipment layers are source assets at contents/<namespace>/textures/armor,
# while item icons and vanilla overrides are emitted from the resourcepack/assets tree.
for layer in ("layer_1.png", "layer_2.png"):
    path = ARMOR_SOURCE_TEXTURES / layer
    if not path.is_file():
        fail("Missing ItemsAdder source armor layer: " + str(path.relative_to(ROOT)))
    width, height = png_size(path)
    if (width, height) != (64, 32):
        fail(f"ItemsAdder source {layer} must be 64x32, got {width}x{height}")

for layer in ("layer_1.png", "layer_2.png"):
    source_path = ARMOR_SOURCE_TEXTURES / layer
    resource_path = ARMOR_TEXTURES / layer
    try:
        source_size = png_size(source_path)
        resource_size = png_size(resource_path)
    except (OSError, ValueError, struct.error) as exc:
        fail(f"Invalid packaged armor layer {layer}: {exc}")
    if source_size != (64, 32) or resource_size != (64, 32):
        fail(f"Armor layers {layer} must be 64x32 in both source and resource-pack locations")
    if source_path.read_bytes() != resource_path.read_bytes():
        fail(f"Armor layer copies for {layer} differ between ItemsAdder source and resource pack")

root_textures = CONTENT / "textures"
if root_textures.exists():
    unexpected = [p for p in root_textures.rglob("*") if p.is_file()
                  and not (p.parent == ARMOR_SOURCE_TEXTURES and p.name in ("layer_1.png", "layer_2.png"))]
    if unexpected:
        fail("Unexpected ItemsAdder top-level texture files: " + ", ".join(str(p.relative_to(ROOT)) for p in unexpected))

print("Halloween asset validation passed.")
print(f"{len(EXPECTED)} item textures: valid 32x32 PNGs; 2 armor layers: valid 64x32 PNGs")
print("Crimson Warden set: 4 armor pieces + 5 enchantable netherite-based tools verified")
print("Halloween shop: 54-slot GUI and all 5 custom tools are configured")
print("ItemsAdder namespace: warriorland_halloween")
print("Sky shader: Minecraft 1.21.10 entry point present")
print(f"Audio: Spooky Fester ambience ({round(theme_duration * 1000)} ms) + 5s event cue, mono OGG/Vorbis containers")
print("ItemsAdder sound definitions: present")
print(f"Vanilla music events suppressed: {len(vanilla_sound_events)}")
print("Halloween silent OGG: mono OGG/Vorbis, about 1 second")
print("Content layout: structure method 2")
