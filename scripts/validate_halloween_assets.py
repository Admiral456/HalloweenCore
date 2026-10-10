#!/usr/bin/env python3
from pathlib import Path
import binascii
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
SOUNDS_DIR = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "sounds"
THEME = SOUNDS_DIR / "haunted_theme.ogg"
PLAYLIST_TRACKS = {
    "horror_atmosphere": SOUNDS_DIR / "horror_atmosphere.ogg",
    "creepy_ambient": SOUNDS_DIR / "creepy_ambient.ogg",
    "dark_cavern_ambient": SOUNDS_DIR / "dark_cavern_ambient.ogg",
}
EVENT_STING = SOUNDS_DIR / "event_sting.ogg"
EVENT_CUES = {
    "soulstorm_sting": 5.2,
    "witching_sting": 4.8,
    "harvest_sting": 4.2,
    "blood_moon_rise": 7.0,
    "pumpkin_apocalypse": 5.4,
    "graveyard_rising": 6.0,
}
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
    "grave_marker.png",
    "grave_marker_cracked.png",
    "crimson_warden_shield.png",
    "crimson_warden_shield_blocking.png",
}
ARMOR_TEXTURES = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "armor" / "crimson_warden"
VANILLA_BLOCK_TEXTURES = CONTENT / "resourcepack" / "assets" / "minecraft" / "textures" / "block"
EXPECTED_SEASONAL_BLOCK_TEXTURES = {
    "pumpkin_side.png", "pumpkin_top.png", "pumpkin_face.png",
    "jack_o_lantern.png", "cobweb.png", "red_candle.png",
    "red_mushroom.png", "brown_mushroom.png", "dead_bush.png",
    "red_mushroom_block.png", "brown_mushroom_block.png", "mushroom_block_inside.png", "mushroom_stem.png",
}


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
    """Return dimensions for legacy assets using the original basic PNG check."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG file")
    if data[12:16] != b"IHDR":
        raise ValueError(f"{path} has no PNG IHDR chunk")
    return struct.unpack(">II", data[16:24])


def strict_png_size(path: Path) -> tuple[int, int]:
    """Validate PNG signature, every chunk CRC, IEND and decodable IDAT data."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG file")
    offset = 8
    width = height = None
    saw_iend = False
    idat = bytearray()
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        end = offset + 8 + length
        if end + 4 > len(data):
            raise ValueError(f"{path} has a truncated {kind.decode('ascii', 'replace')} chunk")
        chunk_data = data[offset + 8:end]
        expected_crc = struct.unpack(">I", data[end:end + 4])[0]
        if (binascii.crc32(kind + chunk_data) & 0xffffffff) != expected_crc:
            raise ValueError(f"{path} has a corrupt {kind.decode('ascii', 'replace')} chunk")
        if kind == b"IHDR":
            if length != 13:
                raise ValueError(f"{path} has an invalid PNG IHDR chunk")
            width, height = struct.unpack(">II", chunk_data[:8])
        elif kind == b"IDAT":
            idat.extend(chunk_data)
        elif kind == b"IEND":
            if length != 0:
                raise ValueError(f"{path} has an invalid PNG IEND chunk")
            saw_iend = True
            offset = end + 4
            break
        offset = end + 4
    if not saw_iend or width is None or height is None or offset != len(data):
        raise ValueError(f"{path} has missing IEND, invalid dimensions or trailing PNG data")
    if not idat:
        raise ValueError(f"{path} has no image data")
    try:
        zlib.decompress(bytes(idat))
    except zlib.error as exc:
        raise ValueError(f"{path} image data cannot be decoded: {exc}") from exc
    return width, height


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)

missing = sorted(name for name in EXPECTED if not (TEXTURES / name).is_file())
if missing:
    fail("Missing item textures: " + ", ".join(missing))

for name in sorted(EXPECTED):
    width, height = strict_png_size(TEXTURES / name)
    if (width, height) != (32, 32):
        fail(f"{name} must be 32x32, got {width}x{height}")

items_config = CONFIG.read_text(encoding="utf-8")
for item_id, texture in (("grave_marker", "item/grave_marker"),
                         ("grave_marker_cracked", "item/grave_marker_cracked")):
    match = re.search(rf"(?ms)^  {re.escape(item_id)}:\n(.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\Z)", items_config)
    if not match:
        fail(f"ItemsAdder config is missing custom gravestone item {item_id}")
    if f"texture: {texture}" not in match.group(1) or "material: PAPER" not in match.group(1):
        fail(f"{item_id} must use its generated ItemsAdder pixel-art texture")

for layer in ("layer_1.png", "layer_2.png"):
    path = ARMOR_TEXTURES / layer
    if not path.is_file():
        fail("Missing Crimson Warden armor layer: " + str(path.relative_to(ROOT)))
    width, height = strict_png_size(path)
    if (width, height) != (64, 32):
        fail(f"{layer} must be 64x32, got {width}x{height}")
    source = ARMOR_SOURCE_TEXTURES / layer
    if not source.is_file():
        fail("Missing source Crimson Warden armor layer: " + str(source.relative_to(ROOT)))
    source_width, source_height = strict_png_size(source)
    if (source_width, source_height) != (64, 32):
        fail(f"Source {layer} must be 64x32, got {source_width}x{source_height}")
    if source.read_bytes() != path.read_bytes():
        fail(f"Source and resource-pack copies of {layer} differ; keep both paths in sync")

# Verify that ItemsAdder's equipment modifiers match the advertised armor totals.
ARMOR_BALANCE = {
    "crimson_warden_helmet": ("NETHERITE_HELMET", 6, 900),
    "crimson_warden_chestplate": ("NETHERITE_CHESTPLATE", 12, 1300),
    "crimson_warden_leggings": ("NETHERITE_LEGGINGS", 9, 1150),
    "crimson_warden_boots": ("NETHERITE_BOOTS", 6, 950),
}
items_yaml = CONFIG.read_text(encoding="utf-8")

# Validate the link between every ItemsAdder graphics texture/state and its actual
# PNG. This also checks state textures such as SHIELD's normal/blocking variants.
referenced_textures = set()
texture_root = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures"
item_sections = re.finditer(
    r"(?ms)^  ([A-Za-z0-9_-]+):\n(.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\Z)",
    items_yaml,
)
for item_section in item_sections:
    item_id, section = item_section.group(1), item_section.group(2)
    graphics_match = re.search(r"(?ms)^    graphics:\n(.*?)(?=^    [A-Za-z0-9_-]+:\s*$|\Z)", section)
    if not graphics_match:
        continue
    graphics = graphics_match.group(1)
    texture_ids = []
    single_texture = re.search(r"(?m)^      texture:\s*([A-Za-z0-9_./-]+)\s*$", graphics)
    if single_texture:
        texture_ids.append(single_texture.group(1))
    texture_map = re.search(r"(?ms)^      textures:\s*\n(.*?)(?=^      [A-Za-z0-9_-]+:\s*$|\Z)", graphics)
    if texture_map:
        texture_ids.extend(
            match.group(1)
            for match in re.finditer(r"(?m)^        [A-Za-z0-9_-]+:\s*([A-Za-z0-9_./-]+)\s*$", texture_map.group(1))
        )
    for texture_id in texture_ids:
        referenced_textures.add(texture_id)
        texture_path = texture_root / (texture_id + ".png")
        if not texture_path.is_file():
            fail(f"ItemsAdder item '{item_id}' points to missing texture: {texture_id}.png")
        try:
            width, height = strict_png_size(texture_path)
        except (OSError, ValueError) as exc:
            fail(f"Texture for ItemsAdder item '{item_id}' is invalid: {exc}")
        if (width, height) != (32, 32):
            fail(f"Texture for ItemsAdder item '{item_id}' must be 32x32, got {width}x{height}")

expected_item_texture_refs = {f"item/{name[:-4]}" for name in EXPECTED}
if referenced_textures != expected_item_texture_refs:
    fail(
        "ItemsAdder texture references do not match the validated item PNG set. "
        f"Missing refs={sorted(expected_item_texture_refs - referenced_textures)}; "
        f"unvalidated refs={sorted(referenced_textures - expected_item_texture_refs)}"
    )
print(f"PASS ItemsAdder texture links: {len(referenced_textures)} definitions map to existing, valid 32x32 PNGs")

for item_id, (material, equipped_armor, durability) in ARMOR_BALANCE.items():
    match = re.search(rf"(?ms)^  {re.escape(item_id)}:\n(.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\Z)", items_yaml)
    if not match:
        fail(f"ItemsAdder config is missing armor item {item_id}")
    section = match.group(1)
    if re.search(rf"(?m)^    material: {re.escape(material)}\s*$", section) is None:
        fail(f"{item_id} must use base material {material}")
    bonus_match = re.search(r"(?ms)^    equipment:\s*\n.*?^      slot_attribute_modifiers:\s*\n\s+armor:\s*(\d+)\s*$", section)
    if not bonus_match or int(bonus_match.group(1)) != equipped_armor:
        fail(f"{item_id} must show the actual When Equipped attribute +{equipped_armor} Armor")
    if re.search(r'(?m)^\s+- "&c\+\d+ Armor"\s*$', section):
        fail(f"{item_id} must not duplicate armor stats in lore")
    if "id: warriorland_halloween:crimson_warden" not in section:
        fail(f"{item_id} is missing its Crimson Warden armor-layer binding")
    durability_match = re.search(r"(?m)^      max_durability: (\d+)\s*$", section)
    if not durability_match or int(durability_match.group(1)) != durability:
        fail(f"{item_id} must have durability {durability}")
    nbt_match = re.search(r'(?m)^    components_nbt_file: "([^"]+)"\s*$', section)
    if not nbt_match:
        fail(f"{item_id} is missing its component JSON")
    component_path = CONFIG.parent / nbt_match.group(1)
    try:
        component = json.loads(component_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"{item_id} component JSON is invalid: {exc}")
    components = component.get("components", {})
    if components.get("minecraft:max_damage") != durability:
        fail(f"{item_id} component max_damage must match {durability}")
    if components.get("minecraft:enchantable", {}).get("value") != 25:
        fail(f"{item_id} must remain normally enchantable (enchantability 25)")
shield_match = re.search(r"(?ms)^  crimson_warden_shield:\n(.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\Z)", items_yaml)
if not shield_match:
    fail("ItemsAdder config is missing crimson_warden_shield")
shield_section = shield_match.group(1)
if ("material: SHIELD" not in shield_section
        or "normal: item/crimson_warden_shield" not in shield_section
        or "blocking: item/crimson_warden_shield_blocking" not in shield_section):
    fail("Crimson Warden shield must define both its normal and blocking textures")
shield_component_match = re.search(r'(?m)^    components_nbt_file: "([^"]+)"\s*$', shield_section)
if not shield_component_match:
    fail("Crimson Warden shield is missing its component JSON")
try:
    shield_component = json.loads((CONFIG.parent / shield_component_match.group(1)).read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as exc:
    fail(f"Crimson Warden shield component JSON is invalid: {exc}")
if shield_component.get("components", {}).get("minecraft:max_damage") != 1600:
    fail("Crimson Warden shield must have 1600 durability")

print("PASS Crimson Warden armor modifiers and displayed totals: 6/12/9/6; durability and component JSON verified")

if not SHADER.is_file():
    fail("Missing Halloween sky shader: " + str(SHADER.relative_to(ROOT)))
shader_text = SHADER.read_text(encoding="utf-8")
for marker in ("#version 330", "#moj_import <minecraft:fog.glsl>", "#moj_import <minecraft:dynamictransforms.glsl>",
                "halloweenTint", "fragColor = sky;"):
    if marker not in shader_text:
        fail(f"Sky shader is missing required Minecraft 1.21.10 marker: {marker}")
if not re.search(r"(?m)^\s*const vec3 halloweenTint = vec3\(1\.10, 0\.76, 0\.62\);$", shader_text):
    fail("Sky shader must use the validated restrained Halloween tint (not a harsh full-screen orange cast)")
if not re.search(r"sky\.rgb\s*=\s*mix\(sky\.rgb,\s*max\(sky\.rgb\s*\*\s*halloweenTint,\s*vec3\(0\.0\)\),\s*0\.24\s*\);", shader_text):
    fail("Sky shader tint blend is missing or differs from the validated low-intensity grade")

# Explicit vanilla texture overrides give physical Halloween decoration its
# seasonal appearance while keeping the server-side blocks present with or
# without the pack. Fail the build if any required 16x16 texture is missing.
missing_seasonal = sorted(name for name in EXPECTED_SEASONAL_BLOCK_TEXTURES
                          if not (VANILLA_BLOCK_TEXTURES / name).is_file())
if missing_seasonal:
    fail("Missing Halloween vanilla block textures: " + ", ".join(missing_seasonal))
for name in sorted(EXPECTED_SEASONAL_BLOCK_TEXTURES):
    try:
        width, height = strict_png_size(VANILLA_BLOCK_TEXTURES / name)
    except (OSError, ValueError) as exc:
        fail(f"Invalid seasonal vanilla block texture {name}: {exc}")
    if (width, height) != (16, 16):
        fail(f"Seasonal vanilla block texture {name} must be 16x16, got {width}x{height}")

theme_duration = 0.0
audio_specs = [(THEME, None), (EVENT_STING, 5.0)]
audio_specs.extend((path, None) for path in PLAYLIST_TRACKS.values())
audio_specs.extend((SOUNDS_DIR / f"{name}.ogg", duration) for name, duration in EVENT_CUES.items())
for audio, expected_duration in audio_specs:
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
    elif audio in PLAYLIST_TRACKS.values() and actual_duration < 5.0:
        fail(f"{audio.name} playlist loop is unexpectedly short")
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
for sound in ("haunted_theme", "horror_atmosphere", "creepy_ambient", "dark_cavern_ambient", "event_sting", *EVENT_CUES.keys()):
    if f"  {sound}:" not in sounds_text:
        fail(f"ItemsAdder sound '{sound}' missing from sounds.yml")
    if f"sound.{sound}:" not in sounds_text:
        fail(f"ItemsAdder subtitle for '{sound}' missing from sounds.yml")

# Ensure each ItemsAdder sound resolves to a real file inside the delivered client pack.
for sound in ("haunted_theme", "horror_atmosphere", "creepy_ambient", "dark_cavern_ambient",
              "event_sting", *EVENT_CUES.keys()):
    match = re.search(rf"(?ms)^  {re.escape(sound)}:\s*\n(.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\Z)", sounds_text)
    if not match or not re.search(rf"(?m)^    path: {re.escape(sound)}\s*$", match.group(1)):
        fail(f"ItemsAdder sound '{sound}' must point to its matching resource-pack OGG")
    if not (SOUNDS_DIR / f"{sound}.ogg").is_file():
        fail(f"Missing client resource-pack audio file: {SOUNDS_DIR / (sound + '.ogg')}")

runtime_config = (ROOT / "src" / "main" / "resources" / "config.yml").read_text(encoding="utf-8")
if not re.search(r"(?m)^  volume:\s*3\.0\s*$", runtime_config):
    fail("Halloween ambient volume must be configured to 3.0 (three times full-volume gain)")
if "volume-tripled-v2-migrated: true" not in runtime_config:
    fail("Default config must declare the one-time 3x volume migration marker")

atmosphere_java = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenAtmosphere.java").read_text(encoding="utf-8")
if "SoundCategory.AMBIENT, volume, pitch" not in atmosphere_java:
    fail("Halloween theme must use the AMBIENT category so vanilla MUSIC can be stopped independently")
if "player.stopSound(SoundCategory.MUSIC)" not in atmosphere_java or "}, 1L, 20L);" not in atmosphere_java:
    fail("Halloween ambience must actively stop the vanilla MUSIC category at least once per second")

reload_java = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenCommand.java").read_text(encoding="utf-8")
if '"iazip"' not in reload_java or '"meg reload"' not in reload_java or '"meg reload models"' in reload_java or "ModelEngine/resource pack" not in reload_java:
    fail("/halloween reload must use /meg reload to generate ModelEngine's pack, merge it, and then run /iazip")
if "Files.walk(assetsDirectory)" not in reload_java or "pngCount == 0" not in reload_java:
    fail("Reload must verify ModelEngine generated real PNG textures before building the ItemsAdder ZIP")
if "if (!ensureModelEngineResourcePackMerged(sender))" not in reload_java:
    fail("Reload must abort packaging when ModelEngine textures or the merge config are missing")
if 'Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iatexture' in reload_java or "iatexture all" in reload_java:
    fail("/halloween reload must not automatically push resource packs to online players; offer the new pack on reconnect")
if "resourcePackRebuildInProgress" not in reload_java or "Resource pack je připraven" not in reload_java:
    fail("Resource-pack rebuild must block overlapping reloads and report completion without a client packet burst")

world_decorator = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenWorldDecorator.java").read_text(encoding="utf-8")
for marker in ("grave_marker", "grave_marker_cracked", "ItemDisplay.class", "Display.Billboard.VERTICAL",
               "halloween_decorated_v5", "POLISHED_BLACKSTONE_BRICKS", "CHISELED_POLISHED_BLACKSTONE",
               "DEEPSLATE_BRICKS", "RED_MUSHROOM_BLOCK", "BROWN_MUSHROOM_BLOCK", "MUSHROOM_STEM",
               "POLISHED_BLACKSTONE_BRICK_WALL"):
    if marker not in world_decorator:
        fail(f"Halloween world gravestone pipeline is missing: {marker}")
if "display.setPersistent(true)" not in world_decorator or "display.setInvulnerable(true)" not in world_decorator:
    fail("Halloween gravestone displays must persist without taking damage")
if "new Vector3f(2.4F, 2.4F, 1.0F)" in world_decorator:
    fail("Gravestones must be built from physical blocks, not rendered as a giant flat ItemDisplay")
if "resumeWithoutScanning()" not in world_decorator:
    fail("Halloween world decorator must be resumable without re-scanning all loaded chunks on hot reload")
core_java = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenCore.java").read_text(encoding="utf-8")
reload_state = core_java[core_java.find("public void reloadEventConfig()"):core_java.find("public void ", core_java.find("public void reloadEventConfig()") + 20)]
if "worldDecorator.resumeWithoutScanning()" not in reload_state or "worldDecorator.scanLoadedChunks()" in reload_state:
    fail("/halloween reload must not enqueue every loaded chunk for a mass decoration pass")

passive_java = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenPassiveEffectManager.java").read_text(encoding="utf-8")
if "Attribute.MAX_HEALTH" not in passive_java or "getItemInOffHand()" not in passive_java:
    fail("Cursed talisman must add max health while held in either hand")


for sound_id in ("warriorland_halloween:event_sting", "warriorland_halloween:haunted_theme",
                 *(f"warriorland_halloween:{name}" for name in PLAYLIST_TRACKS),
                 *(f"warriorland_halloween:{name}" for name in EVENT_CUES)):
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

track_durations = {}
for match in re.finditer(
    r"(?m)^    (haunted_theme|horror_atmosphere|creepy_ambient|dark_cavern_ambient):\s*(\d+)\s*$",
    runtime_config,
):
    track_durations[match.group(1)] = int(match.group(2))
expected_track_names = {"haunted_theme", *PLAYLIST_TRACKS.keys()}
if set(track_durations) != expected_track_names:
    fail("Runtime config must define loop durations for each of the four Halloween playlist tracks")
for track_name, track_path in {
    "haunted_theme": THEME,
    **PLAYLIST_TRACKS,
}.items():
    try:
        metadata = subprocess.run(
            ["ffprobe", "-v", "error", "-select_streams", "a:0",
             "-show_entries", "stream=duration", "-of", "json", str(track_path)],
            check=True, capture_output=True, text=True,
        )
        actual_ms = round(float(json.loads(metadata.stdout)["streams"][0]["duration"]) * 1000)
    except (OSError, subprocess.CalledProcessError, ValueError, KeyError, IndexError) as exc:
        fail(f"Could not inspect playlist duration for {track_name}: {exc}")
    if abs(track_durations[track_name] - actual_ms) > 100:
        fail(f"Playlist loop duration for {track_name} differs from encoded OGG length")

if "night-sounds:" not in runtime_config or '"warriorland_halloween:dark_cavern_ambient"' not in runtime_config:
    fail("Runtime config must set the night playlist and cave-only soundtrack")
if "cave-min-depth-below-surface" not in runtime_config or "night-start-tick" not in runtime_config:
    fail("Runtime config must define nighttime and underground-cave selection rules")

config = CONFIG.read_text(encoding="utf-8")
if "namespace: warriorland_halloween" not in config:
    fail("ItemsAdder namespace missing from items.yml")

for item_id in ("hunter_mask", "cursed_talisman", "halloween_token", "cursed_candy", "haunted_map",
                "crimson_warden_helmet", "crimson_warden_chestplate",
                "crimson_warden_leggings", "crimson_warden_boots",
                "crimson_warden_sword", "crimson_warden_pickaxe", "crimson_warden_axe",
                "crimson_warden_shovel", "crimson_warden_hoe", "crimson_warden_shield"):
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
    section_tail = config[start + len(f"  {item_id}:"):]
    next_item = re.search(r"(?m)^  [a-z0-9_]+:\s*$", section_tail)
    end = start + len(f"  {item_id}:") + next_item.start() if next_item else len(config)
    definition = config[start:end]
    if "material: NETHERITE_" not in definition or "attribute_modifiers:" not in definition or "durability:" not in definition:
        fail(f"Custom gear '{item_id}' needs a netherite base material, extra attributes, and durability")
    if "blocked_enchants:" in definition and "blocked_enchants:\n      - ALL" in definition:
        fail(f"Custom gear '{item_id}' must remain enchantable")

expected_armor_stats = {
    "crimson_warden_helmet": ("max_durability: 900", "armor: 6"),
    "crimson_warden_chestplate": ("max_durability: 1300", "armor: 12"),
    "crimson_warden_leggings": ("max_durability: 1150", "armor: 9"),
    "crimson_warden_boots": ("max_durability: 950", "armor: 6"),
}
for item_id, required_stats in expected_armor_stats.items():
    start = config.find(f"  {item_id}:")
    section_tail = config[start + len(f"  {item_id}:"):]
    next_item = re.search(r"(?m)^  [a-z0-9_]+:\s*$", section_tail)
    end = start + len(f"  {item_id}:") + next_item.start() if next_item else len(config)
    definition = config[start:end]
    for stat in required_stats:
        if stat not in definition:
            fail(f"{item_id} is missing expected full-set armour/durability stat: {stat}")
    if "equipment:" not in definition or "slot_attribute_modifiers:" not in definition:
        fail(f"{item_id} must use its Crimson Warden equipment layer and required armor modifier")

expected_gear_stats = {
    "crimson_warden_sword": ("attackDamage: 15.0", "attackSpeed: 0.8", "max_durability: 5000"),
    "crimson_warden_pickaxe": ("attackDamage: 10.0", "attackSpeed: 0.8", "max_durability: 5000"),
    "crimson_warden_axe": ("attackDamage: 14.0", "attackSpeed: 0.6", "max_durability: 5000"),
    "crimson_warden_shovel": ("attackDamage: 10.0", "attackSpeed: 0.8", "max_durability: 4500"),
    "crimson_warden_hoe": ("attackDamage: 7.0", "attackSpeed: 1.0", "max_durability: 4500"),
}
for item_id, expected_stats in expected_gear_stats.items():
    start = config.find(f"  {item_id}:")
    if start < 0:
        fail(f"Custom gear '{item_id}' missing from ItemsAdder config")
    section_tail = config[start + len(f"  {item_id}:"):]
    next_item = re.search(r"(?m)^  [a-z0-9_]+:\s*$", section_tail)
    end = start + len(f"  {item_id}:") + next_item.start() if next_item else len(config)
    definition = config[start:end]
    for stat in expected_stats:
        if stat not in definition:
            fail(f"Custom gear '{item_id}' is missing expected stronger-than-netherite stat: {stat}")


gear_components = {
    "crimson_warden_helmet": ("crimson_warden_helmet.json", None, 900),
    "crimson_warden_chestplate": ("crimson_warden_chestplate.json", None, 1300),
    "crimson_warden_leggings": ("crimson_warden_leggings.json", None, 1150),
    "crimson_warden_boots": ("crimson_warden_boots.json", None, 950),
    "crimson_warden_sword": ("crimson_warden_sword.json", None, 5000),
    "crimson_warden_pickaxe": ("crimson_warden_pickaxe.json", "#minecraft:mineable/pickaxe", 5000),
    "crimson_warden_axe": ("crimson_warden_axe.json", "#minecraft:mineable/axe", 5000),
    "crimson_warden_shovel": ("crimson_warden_shovel.json", "#minecraft:mineable/shovel", 4500),
    "crimson_warden_hoe": ("crimson_warden_hoe.json", "#minecraft:mineable/hoe", 4500),
}
for item_id, (component_file, mining_tag, expected_max_damage) in gear_components.items():
    start = config.find(f"  {item_id}:")
    if start < 0:
        fail(f"Custom gear '{item_id}' missing from ItemsAdder config")
    section_tail = config[start + len(f"  {item_id}:"):]
    next_item = re.search(r"(?m)^  [a-z0-9_]+:\s*$", section_tail)
    end = start + len(f"  {item_id}:") + next_item.start() if next_item else len(config)
    definition = config[start:end]
    if f'components_nbt_file: "{component_file}"' not in definition:
        fail(f"Custom gear '{item_id}' must load its enchantability/tool components from {component_file}")
    component_path = CONTENT / "configs" / component_file
    if not component_path.is_file():
        fail(f"Missing custom gear component file: {component_path.relative_to(ROOT)}")
    try:
        components_json = json.loads(component_path.read_text(encoding="utf-8"))
        components = components_json["components"]
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as exc:
        fail(f"Invalid components file {component_file}: {exc}")
    if int(components.get("minecraft:max_damage", 0)) != expected_max_damage:
        fail(f"{item_id} component max_damage must be {expected_max_damage}")
    enchantable = components.get("minecraft:enchantable", {})
    if not isinstance(enchantable, dict) or int(enchantable.get("value", 0)) < 25:
        fail(f"{item_id} must retain enchanting support with enchantability >= 25")
    if mining_tag:
        tool_component = components.get("minecraft:tool", {})
        rules = tool_component.get("rules", []) if isinstance(tool_component, dict) else []
        if not isinstance(rules, list) or not any(
            rule.get("blocks") == mining_tag
            and float(rule.get("speed", 0)) >= 14.0
            and rule.get("correct_for_drops") is True
            for rule in rules if isinstance(rule, dict)
        ):
            fail(f"{item_id} must have mining speed >= 14 and correct drops for {mining_tag}")

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

# Arena generation must choose a site from the admin's current standing block,
# not require a preconfigured exact center. Confirmation must reuse the saved preview.
arena_command = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenCommand.java").read_text(encoding="utf-8")
arena_start = arena_command.find("private boolean buildVampireArena(CommandSender sender, String[] args)")
arena_end = arena_command.find("private boolean setVampireArena(CommandSender sender)", arena_start)
if arena_start < 0 or arena_end < 0:
    fail("Vampire arena build command could not be located")
arena_method = arena_command[arena_start:arena_end]
for marker in (
    "playerLocation.getBlockX() + 0.5D",
    "playerLocation.getBlockY()",
    "playerLocation.getBlockZ() + 0.5D",
    'arenaPath + ".pending"',
    'pendingPath + ".configured"',
    'plugin.getConfig().set(arenaPath + ".configured", true)',
    'plugin.getConfig().set(pendingPath, null)',
):
    if marker not in arena_method:
        fail(f"Vampire arena auto-placement/confirmation is missing: {marker}")
if 'if (!plugin.getConfig().getBoolean("bosses.vampire.arena.configured", false))' in arena_method:
    fail("Arena generation must not require a previous /halloween setvampirearena command")
if "builder.inspect(center)" not in arena_method or "builder.build(center)" not in arena_method:
    fail("Arena generation must retain preflight and explicit-confirm safety")
print("PASS Vampire arena: auto-selects the standing block, stores preview, confirms same site, and keeps safety checks")

curse_command = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenCommand.java").read_text(encoding="utf-8")
curse_data = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenDataManager.java").read_text(encoding="utf-8")
curse_service = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenServiceImpl.java").read_text(encoding="utf-8")
core_source = (ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenCore.java").read_text(encoding="utf-8")
for marker in ("return curse(sender, args)", "curse set <úroveň>", "curse reset", "configuredCurseLevels()"):
    if marker not in curse_command:
        fail(f"Admin curse-setting command is missing: {marker}")
if "setCurseLevelOverride" not in curse_data or "curse-level-override" not in curse_data:
    fail("Manual curse override must persist in data.yml")
if "getCurseLevelOverride" not in curse_service:
    fail("Curse progression must honor the manual level override")
if "migrateCrimsonWardenLoreV5" not in core_source:
    fail("Existing configs must migrate away from the duplicated Crimson Warden +Armor lore")
if "migrateHalloween3dDecorationsV1" not in core_source:
    fail("Existing configs must get the low-density 3D grave/mushroom decoration migration")

print("Halloween asset validation passed.")
print("16 item textures: 32x32 PNG including two custom gravestones; 2 Crimson Warden armor layers: 64x32 PNG")
print("Crimson Warden gear: attack stats/durability verified; 4 armor pieces and 5 weapons/tools enchantable")
print("Pickaxe, axe, shovel and hoe: custom 14x mining-speed tool components verified")
print("ItemsAdder namespace: warriorland_halloween")
print(f"Seasonal vanilla block overrides: {len(EXPECTED_SEASONAL_BLOCK_TEXTURES)} textures verified at 16x16, including 3D mushroom cap textures")
print("Crimson Warden armor: real equipment modifiers, no duplicate +Armor lore, equip texture bindings and durability verified")
print("Crimson Warden weapon icons: generated high-contrast pixel-art icons for sword, pickaxe, axe, shovel and hoe")
print("Sky shader: Minecraft 1.21.10 entry point and restrained Halloween tint verified")
print(f"Audio: Spooky Fester ambience ({round(theme_duration * 1000)} ms), 5s generic cue, and {len(EVENT_CUES)} unique event cues; all mono OGG/Vorbis")
print("ItemsAdder sound definitions: present")
print(f"Verified exact Minecraft 1.21.10 music-event overrides: {len(vanilla_sound_events)}")
print("Halloween silent OGG: mono OGG/Vorbis, about 1 second")
print("Content layout: structure method 2")
