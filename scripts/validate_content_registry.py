#!/usr/bin/env python3
"""Cross-check the canonical Halloween registry against ItemsAdder and files on disk."""
from __future__ import annotations

import hashlib
import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "src/main/resources/content-registry.yml"
ITEMS_CONFIG = ROOT / "itemsadder/contents/warriorland_halloween/configs/items.yml"
ITEM_TEXTURES = ROOT / "itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/textures/item"
BLOCK_TEXTURES = ROOT / "itemsadder/contents/warriorland_halloween/resourcepack/assets/minecraft/textures/block"
MODEL_DIR = ROOT / "mythicmobs/models"
ARMOR_DIR = ROOT / "itemsadder/contents/warriorland_halloween/textures/armor/crimson_warden"
SOUND_DIR = ROOT / "itemsadder/contents/warriorland_halloween/sounds"
PACK_SOUND_DIR = ROOT / "itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/sounds"
SOURCE_MANIFEST = ROOT / "third-party-assets/stevs-halloween/asset-source-manifest.json"


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def png_dimensions(path: Path) -> tuple[int, int]:
    """Validate a PNG header before trusting its declared dimensions."""
    data = path.read_bytes()
    if len(data) < 26 or data[:8] != bytes.fromhex("89504e470d0a1a0a") or data[12:16] != b"IHDR":
        fail(f"Invalid PNG signature or IHDR: {path.relative_to(ROOT)}")
    if data[24] != 8 or data[25] != 6:
        fail(f"{path.relative_to(ROOT)} must be an 8-bit RGBA PNG")
    return struct.unpack(">II", data[16:24])


def entries(text: str, section_name: str) -> dict[str, dict[str, str]]:
    """Parse the deliberately simple ID/field part of a YAML section using stdlib only."""
    lines = text.splitlines()
    start = next((i for i, line in enumerate(lines) if line.strip() == section_name + ":"), None)
    if start is None:
        return {}
    result: dict[str, dict[str, str]] = {}
    current = None
    for line in lines[start + 1:]:
        if line and not line[0].isspace() and not line.lstrip().startswith("#"):
            break
        match = re.match(r"^  ([a-z0-9][a-z0-9_-]*):\s*$", line)
        if match:
            current = match.group(1)
            if current in result:
                fail(f"Duplicate ID {current!r} in registry section {section_name}")
            result[current] = {}
            continue
        field = re.match(r"^    ([a-z0-9][a-z0-9_-]*):\s*(.*?)\s*$", line)
        if current is not None and field:
            value = field.group(2).strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            result[current][field.group(1)] = value
    return result


def assert_same_paths(label: str, expected: set[Path], actual: set[Path]) -> None:
    missing = sorted(p.as_posix() for p in expected - actual)
    unregistered = sorted(p.as_posix() for p in actual - expected)
    if missing or unregistered:
        fail(f"{label} registry mismatch. Missing={missing}; unregistered/unused={unregistered}")


def parse_versions(text: str) -> tuple[str, list[str]]:
    section = re.search(r"(?ms)^target-versions:\s*\n(.*?)(?=^[a-z][a-z0-9_-]*:\s*$|\Z)", text)
    if not section:
        fail("Registry is missing target-versions")
    body = section.group(1)
    default = re.search(r"(?m)^\s+default:\s*[\"']?([0-9]+(?:\.[0-9]+){1,2})", body)
    verified_block = re.search(r"(?ms)^\s+verified:\s*\n((?:^\s+-\s*[\"']?[0-9]+(?:\.[0-9]+){1,2}[\"']?\s*$\n?)+)", body)
    if not default or not verified_block:
        fail("target-versions requires a default and a verified list")
    verified = re.findall(r"(?m)^\s+-\s*[\"']?([0-9]+(?:\.[0-9]+){1,2})[\"']?\s*$", verified_block.group(1))
    if not verified or default.group(1) not in verified or len(verified) != len(set(verified)):
        fail("Default target version must occur exactly once in the verified target list")
    return default.group(1), verified


def validate(root: Path = ROOT) -> None:
    registry_path = root / "src/main/resources/content-registry.yml"
    items_path = root / "itemsadder/contents/warriorland_halloween/configs/items.yml"
    item_texture_root = root / "itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/textures/item"
    block_texture_root = root / "itemsadder/contents/warriorland_halloween/resourcepack/assets/minecraft/textures/block"
    model_root = root / "mythicmobs/models"
    armor_root = root / "itemsadder/contents/warriorland_halloween/textures/armor/crimson_warden"
    sound_root = root / "itemsadder/contents/warriorland_halloween/sounds"
    pack_sound_root = root / "itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/sounds"
    source_manifest_path = root / "third-party-assets/stevs-halloween/asset-source-manifest.json"

    try:
        registry_text = registry_path.read_text(encoding="utf-8")
        items_config = items_path.read_text(encoding="utf-8")
    except OSError as exc:
        fail(f"Cannot read registry or ItemsAdder config: {exc}")

    default_version, verified_versions = parse_versions(registry_text)
    if default_version != "1.21.10" or verified_versions != ["1.21.10"]:
        fail(f"Only the tested Minecraft 1.21.10 profile may be marked verified; got {verified_versions}")

    item_defs = entries(registry_text, "items")
    if not item_defs:
        fail("The items section in content-registry.yml is empty")
    item_ids = list(item_defs)
    ia_ids = [definition.get("itemsadder-id", "") for definition in item_defs.values()]
    runtime_ids = [definition.get("runtime-identity", "") for definition in item_defs.values()]
    if any(not value for value in ia_ids + runtime_ids):
        fail("Every item requires itemsadder-id and runtime-identity")
    if len(ia_ids) != len(set(ia_ids)):
        fail("Duplicate ItemsAdder IDs in content-registry.yml")
    if len(runtime_ids) != len(set(runtime_ids)):
        fail("Duplicate runtime identities in content-registry.yml")

    namespace = re.search(r"(?m)^\s*namespace:\s*([a-z0-9_]+)\s*$", items_config)
    if not namespace:
        fail("ItemsAdder namespace is missing")
    config_section = items_config.split("\nitems:\n", 1)
    if len(config_section) != 2:
        fail("ItemsAdder config has no items section")
    config_ids = set(re.findall(r"(?m)^  ([a-z0-9_]+):\s*$", config_section[1]))
    registry_config_ids = set()
    for internal_id, definition in item_defs.items():
        expected_id = f"{namespace.group(1)}:{internal_id}"
        actual_id = definition["itemsadder-id"]
        if not actual_id.startswith(namespace.group(1) + ":"):
            fail(f"{internal_id}: ItemsAdder ID is outside namespace {namespace.group(1)}")
        suffix = actual_id.split(":", 1)[1]
        registry_config_ids.add(suffix)
        if suffix not in config_ids:
            fail(f"{internal_id}: ItemsAdder ID {actual_id} has no matching items.yml definition")
        texture_key = definition.get("texture", "")
        if not texture_key or not re.fullmatch(r"[a-z0-9_/-]+", texture_key):
            fail(f"{internal_id}: missing or invalid texture key")
        texture_path = (item_texture_root / (texture_key + ".png")).resolve()
        if item_texture_root.resolve() not in texture_path.parents or not texture_path.is_file():
            fail(f"{internal_id}: missing/out-of-tree icon {texture_key}.png")
    if registry_config_ids != config_ids:
        fail(f"Registry/ItemsAdder item IDs differ. Not in registry={sorted(config_ids-registry_config_ids)}; not in ItemsAdder={sorted(registry_config_ids-config_ids)}")

    registered_icons = {
        (item_texture_root / (definition["texture"] + ".png")).resolve()
        for definition in item_defs.values()
    }
    actual_icons = {path.resolve() for path in item_texture_root.rglob("*.png") if path.is_file()}
    assert_same_paths("Item icon", registered_icons, actual_icons)

    model_defs = entries(registry_text, "model-assets")
    registered_model_files: set[Path] = set()
    for model_id, definition in model_defs.items():
        for field in ("blueprint-path", "texture-path"):
            value = definition.get(field, "")
            if not value:
                fail(f"Model {model_id} lacks {field}")
            path = (root / value).resolve()
            if root.resolve() not in path.parents or not path.is_file():
                fail(f"Model {model_id} references missing/out-of-tree {value}")
            registered_model_files.add(path)
    actual_model_files = {path.resolve() for path in model_root.iterdir()
                          if path.is_file() and path.suffix.lower() in {".bbmodel", ".png"}}
    assert_same_paths("ModelEngine/Blockbench", registered_model_files, actual_model_files)

    furniture_defs = entries(registry_text, "furniture-model-assets")
    furniture_model_root = root / "itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/models/furniture"
    registered_furniture_models: set[Path] = set()
    for model_id, definition in furniture_defs.items():
        value = definition.get("asset-path", "")
        path = (root / value).resolve() if value else None
        if path is None or not path.is_file() or path.suffix.lower() != ".json":
            fail(f"Furniture model {model_id} is missing a JSON asset: {value}")
        if furniture_model_root.resolve() not in path.parents:
            fail(f"Furniture model {model_id} escapes the registered furniture model directory: {value}")
        try:
            furniture_model = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            fail(f"Furniture model {value} is invalid JSON: {exc}")
        if not isinstance(furniture_model.get("textures"), dict) or not furniture_model["textures"]:
            fail(f"Furniture model {value} must declare textures")
        for texture_name, texture_path in furniture_model["textures"].items():
            if not isinstance(texture_path, str) or not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", texture_path):
                fail(f"Furniture model {value} has an invalid namespaced texture path for {texture_name}: {texture_path}")
            namespace_path = texture_path.split(":", 1)[1]
            if namespace_path.startswith("/") or ".." in namespace_path.split("/"):
                fail(f"Furniture model {value} has an unsafe texture path for {texture_name}: {texture_path}")
        elements = furniture_model.get("elements", [])
        if not isinstance(elements, list) or not elements:
            fail(f"Furniture model {value} must contain at least one 3D element")
        for element in elements:
            start, end = element.get("from", []), element.get("to", [])
            faces = element.get("faces", {})
            if len(start) != 3 or len(end) != 3 or any(a > b for a, b in zip(start, end)):
                fail(f"Furniture model {value} has invalid cube bounds")
            if set(faces) != {"north", "east", "south", "west", "up", "down"}:
                fail(f"Furniture model {value} has a cube missing one or more faces")
            for face in faces.values():
                if face.get("texture", "").lstrip("#") not in furniture_model["textures"]:
                    fail(f"Furniture model {value} references an undeclared face texture")
        registered_furniture_models.add(path)
    actual_furniture_models = {p.resolve() for p in furniture_model_root.glob("*.json") if p.is_file()}
    assert_same_paths("ItemsAdder furniture model", registered_furniture_models, actual_furniture_models)

    block_defs = entries(registry_text, "world-decoration-assets")
    registered_blocks = set()
    for block_id, definition in block_defs.items():
        value = definition.get("asset-path", "")
        if not value:
            fail(f"Decoration {block_id} lacks asset-path")
        path = (root / value).resolve()
        if not path.is_file():
            fail(f"Decoration {block_id}: missing file {value}")
        registered_blocks.add(path)
    actual_blocks = {path.resolve() for path in block_texture_root.glob("*.png") if path.is_file()}
    assert_same_paths("World decoration texture", registered_blocks, actual_blocks)

    armor_defs = entries(registry_text, "armor-assets")
    registered_armor = set()
    for armor_id, definition in armor_defs.items():
        value = definition.get("asset-path", "")
        path = (root / value).resolve() if value else None
        if path is None or not path.is_file():
            fail(f"Armor asset {armor_id} is missing its asset-path/file")
        if png_dimensions(path) != (64, 32):
            fail(f"Armor texture {path.relative_to(root)} must be exactly 64x32 pixels")
        registered_armor.add(path)
    actual_armor = {path.resolve() for path in armor_root.glob("*.png") if path.is_file()}
    assert_same_paths("Equipment texture", registered_armor, actual_armor)
    armor_hashes = {hashlib.sha256(path.read_bytes()).hexdigest() for path in registered_armor}
    if len(armor_hashes) != len(registered_armor):
        fail("Armor layer textures must not be duplicate copies of one another")

    sound_defs = entries(registry_text, "sound-assets")
    registered_sound_paths = set()
    for sound_id, definition in sound_defs.items():
        value = definition.get("asset-path", "")
        path = (root / value).resolve() if value else None
        if path is None or not path.is_file():
            fail(f"Sound {sound_id} references a missing asset: {value}")
        if path.suffix.lower() != ".ogg":
            fail(f"Sound {sound_id} is not registered as an OGG asset")
        registered_sound_paths.add(path)
    roots = {sound_root.resolve(), pack_sound_root.resolve()}
    registered_by_root = {base: {p for p in registered_sound_paths if base in p.parents} for base in roots}
    actual_by_root = {
        sound_root.resolve(): {p.resolve() for p in sound_root.glob("*.ogg") if p.is_file()},
        pack_sound_root.resolve(): {p.resolve() for p in pack_sound_root.glob("*.ogg") if p.is_file()},
    }
    for base in roots:
        assert_same_paths(f"Sound asset under {base}", registered_by_root[base], actual_by_root[base])

    source_manifest = root / "third-party-assets/stevs-halloween/asset-source-manifest.json"
    try:
        manifest = json.loads(source_manifest.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"Cannot read third-party asset source manifest: {exc}")
    packaged_license_dir = root / "itemsadder/contents/warriorland_halloween/THIRD_PARTY_LICENSES"
    packaged_license = packaged_license_dir / "stevs-halloween-MIT.txt"
    packaged_notice = packaged_license_dir / "README.txt"
    packaged_manifest = packaged_license_dir / "stevs-halloween-source-manifest.json"
    for required in (packaged_license, packaged_notice, packaged_manifest):
        if not required.is_file():
            fail(f"Third-party licence/provenance file is missing from the distributable pack: {required.relative_to(root)}")
    if "MIT License" not in packaged_license.read_text(encoding="utf-8"):
        fail("The distributable pack must include the complete MIT license notice")
    if "Stev51" not in packaged_notice.read_text(encoding="utf-8"):
        fail("The distributable pack must attribute Stev's Halloween artwork to Stev51")
    try:
        packaged_manifest_data = json.loads(packaged_manifest.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"Bundled third-party source manifest is invalid: {exc}")
    if packaged_manifest_data != manifest:
        fail("Bundled third-party source manifest differs from the repository's source manifest")

    imported_count = 0
    for entry in manifest.get("assets", []):
        path = root / entry["target_path"]
        if not path.is_file():
            fail(f"Imported Stev texture is missing: {entry['target_path']}")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != entry["sha256"]:
            fail(f"Imported Stev texture hash differs from source: {entry['target_path']}")
        if entry.get("license") != "MIT":
            fail(f"Unexpected license in imported asset manifest: {entry['target_path']}")
        imported_count += 1
    distinct_sources = {entry.get("source_path", "") for entry in manifest.get("assets", [])}
    if imported_count != 15:
        fail(f"Expected 15 explicit MIT-licensed Stev target files (14 distinct source assets), found {imported_count}")
    if len(distinct_sources) != 14 or "" in distinct_sources:
        fail(f"Expected 14 distinct source assets in the Stev provenance manifest, found {len(distinct_sources)}")

    protected = entries(registry_text, "protected-assets")
    for asset_id in ("void-reaper", "frost-stalker", "nightmare"):
        definition = protected.get(asset_id)
        if not definition or definition.get("status") != "externally-authored-protected" or definition.get("generation") != "prohibited":
            fail(f"External art {asset_id} must remain protected from generation/recoloring")

    print(f"PASS canonical content registry: {len(item_defs)} unique IDs; ItemsAdder IDs in sync.")
    print(f"PASS item icon inventory: {len(registered_icons)} registered; no unregistered/unused PNGs.")
    print(f"PASS model inventory: {len(model_defs)} ModelEngine model/texture pairs; no unregistered files.")
    print(f"PASS ItemsAdder furniture: {len(registered_furniture_models)} registered 3D furniture model(s), structurally valid.")
    print(f"PASS world decorations: {len(registered_blocks)} block textures; no unregistered files.")
    print(f"PASS armor layers: {len(registered_armor)} registered source texture files.")
    print(f"PASS sounds: {len(registered_sound_paths)} OGG assets and no unregistered OGG files.")
    print(f"PASS provenance: {imported_count} MIT target files from 14 distinct source assets match their source SHA-256.")
    print(f"PASS Minecraft profile: {default_version}; donor-only versions remain unverified.")


if __name__ == "__main__":
    validate()
