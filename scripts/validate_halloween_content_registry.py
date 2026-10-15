#!/usr/bin/env python3
"""Validate canonical item definitions, ItemsAdder wiring, PNGs, and third-party hashes."""
from __future__ import annotations

import hashlib
import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "src/main/resources/content-registry.yml"
ITEMS_YML = ROOT / "itemsadder/contents/warriorland_halloween/configs/items.yml"
ITEM_TEXTURES = ROOT / "itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/textures/item"
SOURCE_MANIFEST = ROOT / "third-party-assets/stevs-halloween/asset-source-manifest.json"


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_flat_entries(section_text: str, label: str) -> dict[str, str]:
    """Read flat two-space YAML map keys and keep their indented block as text.

    The registry and ItemsAdder files intentionally use a small, stable subset of YAML;
    parsing the blocks this way avoids adding an unpinned third-party YAML dependency to CI.
    """
    matches = list(re.finditer(r"(?m)^  ([a-z0-9_]+):\s*$", section_text))
    parsed: dict[str, str] = {}
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(section_text)
        parsed[match.group(1)] = section_text[match.end():end]
    if not parsed:
        fail(f"{label} section has no entries")
    return parsed


def section_after(text: str, heading: str, label: str) -> str:
    match = re.search(rf"(?m)^{re.escape(heading)}:\s*$", text)
    if not match:
        fail(f"Missing {label} section")
    # Stop before the next root-level key; later registry sections are not items.
    following = re.search(r"(?m)^[A-Za-z0-9_-]+:\s*(?:#.*)?$", text[match.end():])
    end = match.end() + following.start() if following else len(text)
    return text[match.end():end]


def field(block: str, key: str, item_id: str) -> str:
    match = re.search(rf"(?m)^    {re.escape(key)}:\s*(.*?)\s*$", block)
    if not match:
        fail(f"Registry item '{item_id}' is missing '{key}'")
    return match.group(1).strip().strip('"').strip("'")


def png_dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        fail(f"Invalid PNG signature or IHDR: {path.relative_to(ROOT)}")
    return struct.unpack(">II", data[16:24])


def load_registry() -> dict[str, dict[str, str]]:
    text = REGISTRY.read_text(encoding="utf-8")
    if not re.search(r"(?m)^schema-version:\s*1\s*$", text):
        fail("content-registry.yml must declare schema-version: 1")
    entries = parse_flat_entries(section_after(text, "items", "registry items"), "registry items")
    result = {}
    seen_ia_ids: set[str] = set()
    seen_textures: set[str] = set()
    seen_runtime_ids: set[str] = set()
    for internal_id, block in entries.items():
        definition = {
            "category": field(block, "category", internal_id),
            "itemsadder-id": field(block, "itemsadder-id", internal_id),
            "runtime-identity": field(block, "runtime-identity", internal_id),
            "fallback-material": field(block, "fallback-material", internal_id),
            "texture": field(block, "texture", internal_id),
            "display-name": field(block, "display-name", internal_id),
        }
        # Some placeable blocks need a different block-face texture than their item icon.
        block_texture = re.search(r"(?m)^    itemsadder-texture:\s*(.*?)\s*$", block)
        if block_texture:
            definition["itemsadder-texture"] = block_texture.group(1).strip().strip('"').strip("'")
        ia_id = definition["itemsadder-id"]
        texture = definition["texture"]
        if not re.fullmatch(r"[a-z0-9_]+:[a-z0-9_]+", ia_id):
            fail(f"Invalid namespaced ItemsAdder ID '{ia_id}' in {internal_id}")
        if ia_id in seen_ia_ids:
            fail(f"Duplicate ItemsAdder ID: {ia_id}")
        seen_ia_ids.add(ia_id)
        if texture in seen_textures:
            fail(f"Two registered items use the same texture key: {texture}")
        seen_textures.add(texture)
        runtime_identity = definition["runtime-identity"]
        if runtime_identity in seen_runtime_ids:
            fail(f"Duplicate runtime identity: {runtime_identity}")
        seen_runtime_ids.add(runtime_identity)
        if not re.fullmatch(r"[a-z0-9_/-]+", texture) or texture.startswith("/") or ".." in texture:
            fail(f"Unsafe or invalid logical texture key '{texture}'")
        if not re.fullmatch(r"[A-Z0-9_]+", definition["fallback-material"]):
            fail(f"Invalid fallback material for {internal_id}")
        result[internal_id] = definition
    return result


def validate(root: Path = ROOT) -> int:
    registry = load_registry()
    items_yml = ITEMS_YML.read_text(encoding="utf-8")
    configured = parse_flat_entries(section_after(items_yml, "items", "ItemsAdder items"), "ItemsAdder items")
    expected_ia_names = {item["itemsadder-id"].split(":", 1)[1] for item in registry.values()}
    if set(configured) != expected_ia_names:
        missing = sorted(expected_ia_names - set(configured))
        extra = sorted(set(configured) - expected_ia_names)
        fail(f"ItemsAdder/registry mismatch; missing={missing}, unregistered={extra}")

    expected_textures: set[str] = set()
    for internal_id, definition in registry.items():
        ia_key = definition["itemsadder-id"].split(":", 1)[1]
        block = configured[ia_key]
        expected_texture_ref = definition.get("itemsadder-texture", "item/" + definition["texture"])
        texture_match = re.search(r"(?m)^\s+texture:\s*([^\s#]+)", block)
        if not texture_match:
            fail(f"ItemsAdder item {ia_key} has no graphics.texture")
        texture_ref = texture_match.group(1)
        if texture_ref != expected_texture_ref:
            fail(f"ItemsAdder item {ia_key} points at {texture_ref}, registry says {expected_texture_ref}")
        texture_key = definition["texture"]
        expected_textures.add(texture_key + ".png")
        texture_file = ITEM_TEXTURES / (texture_key + ".png")
        if not texture_file.is_file():
            fail(f"Missing registered item texture {texture_file.relative_to(ROOT)}")
        width, height = png_dimensions(texture_file)
        expected_size = (16, 16) if texture_key.startswith("stev_candy/") else (32, 32)
        if (width, height) != expected_size:
            fail(f"{texture_file.relative_to(ROOT)} expected {expected_size}, got {(width, height)}")

    actual_textures = {path.relative_to(ITEM_TEXTURES).as_posix() for path in ITEM_TEXTURES.rglob("*.png")}
    if actual_textures != expected_textures:
        fail("Unreferenced/missing item texture PNGs; "
             f"unreferenced={sorted(actual_textures - expected_textures)}, "
             f"missing={sorted(expected_textures - actual_textures)}")

    source_manifest = json.loads(SOURCE_MANIFEST.read_text(encoding="utf-8"))
    if source_manifest.get("source", {}).get("license") != "MIT":
        fail("Imported Stev's Halloween source manifest must retain its MIT license record")
    imported_entries = source_manifest.get("assets", [])
    imported_targets = set()
    for entry in imported_entries:
        target = (ROOT / entry["target_path"]).resolve()
        if ROOT.resolve() not in target.parents:
            fail(f"Third-party asset target escapes repository root: {entry['target_path']}")
        if entry.get("license") != "MIT" or source_manifest["source"].get("author") != "Stev51":
            fail(f"Imported texture is missing its MIT/author provenance: {entry.get('source_path')}")
        if not target.is_file():
            fail(f"Missing licensed texture: {entry['target_path']}")
        actual_sha = hashlib.sha256(target.read_bytes()).hexdigest()
        if actual_sha != entry.get("sha256"):
            fail(f"SHA-256 mismatch for imported asset {entry['target_path']}: {actual_sha}")
        if png_dimensions(target) != (16, 16):
            fail(f"Imported Stev texture must remain 16x16: {entry['target_path']}")
        imported_targets.add(target.relative_to(ROOT).as_posix())
    item_target_prefix = "itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/textures/item/stev_candy/"
    block_target_prefix = "itemsadder/contents/warriorland_halloween/resourcepack/assets/minecraft/textures/block/"
    actual_imported_items = {p.relative_to(ROOT).as_posix() for p in (ITEM_TEXTURES / "stev_candy").rglob("*.png")}
    imported_item_targets = {p for p in imported_targets if p.startswith(item_target_prefix)}
    imported_block_targets = {p for p in imported_targets if p.startswith(block_target_prefix)}
    if len(imported_targets) != len(imported_entries):
        fail("The third-party source manifest contains duplicate target paths")
    if actual_imported_items != imported_item_targets:
        fail("The imported item-texture directory and source manifest do not match")
    if any(not (p.startswith(item_target_prefix) or p.startswith(block_target_prefix)) for p in imported_targets):
        fail("A third-party texture target uses an unapproved output directory")
    actual_block_targets = {p.relative_to(ROOT).as_posix() for p in BLOCK_TEXTURES.glob("*.png")}
    if not imported_block_targets.issubset(actual_block_targets):
        fail("A licensed block texture target is missing from the Minecraft block texture directory")
    print(f"PASS {len(registry)} canonical item IDs, unique ItemsAdder IDs and texture keys")
    print(f"PASS {len(actual_textures)} item icons are referenced; no unused item-icon PNGs")
    print(f"PASS {len(imported_entries)} MIT-attributed imported textures match their recorded SHA-256")
    return len(registry)


def main() -> None:
    validate()


if __name__ == "__main__":
    main()
