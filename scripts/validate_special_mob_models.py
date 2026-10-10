#!/usr/bin/env python3
"""Validate the five custom Halloween ModelEngine/Blockbench assets and mob definitions."""
from __future__ import annotations

import base64
import binascii
import hashlib
import json
import pathlib
import re
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "mythicmobs" / "models"
MOB_CONFIG = ROOT / "mythicmobs" / "mobs" / "halloween-special-mobs.yml"
MOB_IDS = (
    "halloween_cursed_zombie",
    "halloween_void_reaper",
    "halloween_gravekeeper",
    "halloween_frost_stalker",
    "halloween_blood_spider",
    "halloween_pumpkin_wraith",
    "halloween_hex_witch",
    "halloween_nightmare",
)


def fail(message: str) -> None:
    print("ERROR:", message, file=sys.stderr)
    raise SystemExit(1)


def inspect_png(path: pathlib.Path) -> tuple[int, int, bytes]:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        fail(f"{path.name} has an invalid PNG signature")
    offset = 8
    width = height = None
    idat = bytearray()
    saw_iend = False
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        end = offset + 8 + length
        if end + 4 > len(data):
            fail(f"{path.name} has a truncated PNG chunk")
        chunk_data = data[offset + 8:end]
        expected_crc = struct.unpack(">I", data[end:end + 4])[0]
        if (binascii.crc32(kind + chunk_data) & 0xffffffff) != expected_crc:
            fail(f"{path.name} has a corrupt {kind.decode('ascii', 'replace')} chunk")
        if kind == b"IHDR":
            width, height = struct.unpack(">II", chunk_data[:8])
            if len(chunk_data) != 13 or chunk_data[8:13] != bytes([8, 6, 0, 0, 0]):
                fail(f"{path.name} must be an 8-bit RGBA PNG")
        elif kind == b"IDAT":
            idat.extend(chunk_data)
        elif kind == b"IEND":
            saw_iend = True
            offset = end + 4
            break
        offset = end + 4
    if not saw_iend or width is None or height is None:
        fail(f"{path.name} is missing required PNG chunks")
    if (width, height) != (128, 128):
        fail(f"{path.name} must be 128x128, got {width}x{height}")
    try:
        pixels = zlib.decompress(bytes(idat))
    except zlib.error as exc:
        fail(f"{path.name} IDAT data cannot be decoded: {exc}")
    if len(pixels) != height * (1 + width * 4):
        fail(f"{path.name} decoded pixel data size is invalid")
    return width, height, data


def validate_model(mob_id: str) -> None:
    model_path = MODEL_DIR / f"{mob_id}.bbmodel"
    png_path = MODEL_DIR / f"{mob_id}.png"
    if not model_path.is_file() or not png_path.is_file():
        fail(f"Missing ModelEngine model or texture for {mob_id}")
    try:
        model = json.loads(model_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"{model_path.name} is invalid JSON: {exc}")
    if model.get("meta", {}).get("model_format") != "free" or model.get("meta", {}).get("box_uv", True):
        fail(f"{model_path.name} must use Blockbench Generic/Free UV format")
    if model.get("name") != mob_id or model.get("model_identifier") != mob_id:
        fail(f"{model_path.name} name/model_identifier must both be {mob_id}")
    if model.get("resolution") != {"width": 128, "height": 128}:
        fail(f"{model_path.name} must declare a 128x128 texture resolution")
    textures = model.get("textures", [])
    if not textures or not textures[0].get("source", "").startswith("data:image/png;base64,"):
        fail(f"{model_path.name} must embed its own texture atlas")
    try:
        embedded_png = base64.b64decode(textures[0]["source"].split(",", 1)[1], validate=True)
    except (ValueError, binascii.Error) as exc:
        fail(f"{model_path.name} has invalid embedded texture base64: {exc}")
    _, _, external_png = inspect_png(png_path)
    if embedded_png != external_png:
        fail(f"{model_path.name} embedded texture differs from {png_path.name}")

    elements = model.get("elements", [])
    if len(elements) < 15:
        fail(f"{model_path.name} should contain detailed geometry (at least 15 cubes)")
    uuids: list[str] = []
    element_ids = set()
    for element in elements:
        uid = element.get("uuid")
        if not uid or uid in element_ids:
            fail(f"{model_path.name} has missing or duplicate element UUIDs")
        element_ids.add(uid)
        uuids.append(uid)
        if element.get("type") != "cube":
            fail(f"{model_path.name} has unsupported element type {element.get('type')}")
        start, end = element.get("from", []), element.get("to", [])
        if len(start) != 3 or len(end) != 3 or any(a > b for a, b in zip(start, end)):
            fail(f"{model_path.name} has a reversed or malformed cube: {element.get('name')}")
        faces = element.get("faces", {})
        if set(faces) != {"north", "east", "south", "west", "up", "down"}:
            fail(f"{model_path.name} cube {element.get('name')} is missing faces")
        for face in faces.values():
            uv = face.get("uv")
            if uv is None or len(uv) != 4:
                fail(f"{model_path.name} has a malformed UV rectangle")
            if face.get("texture") == 0 and (
                min(uv[0], uv[2]) < 0 or min(uv[1], uv[3]) < 0
                or max(uv[0], uv[2]) > 128 or max(uv[1], uv[3]) > 128
            ):
                fail(f"{model_path.name} has UVs outside its atlas")

    group_ids = set()
    bone_names = set()
    references: list[str] = []

    def visit_group(node: dict) -> None:
        uid = node.get("uuid")
        if not uid or uid in group_ids or uid in element_ids:
            fail(f"{model_path.name} has missing or duplicate bone/group UUIDs")
        group_ids.add(uid)
        uuids.append(uid)
        if node.get("name"):
            bone_names.add(node["name"])
        children = node.get("children", [])
        if not isinstance(children, list):
            fail(f"{model_path.name} group children must be an array")
        for child in children:
            if isinstance(child, dict):
                visit_group(child)
            elif isinstance(child, str):
                references.append(child)
            else:
                fail(f"{model_path.name} has an invalid outliner child")

    for group in model.get("outliner", []):
        visit_group(group)
    if not model.get("outliner") or "root" not in bone_names:
        fail(f"{model_path.name} must contain a root bone")
    if set(references) != element_ids or len(references) != len(element_ids):
        fail(f"{model_path.name} outliner must reference each model cube exactly once")

    names = {anim.get("name") for anim in model.get("animations", [])}
    if not {"idle", "walk", "attack"}.issubset(names):
        fail(f"{model_path.name} must have idle, walk and attack animations")
    if mob_id not in ("halloween_blood_spider", "halloween_frost_stalker") and "fly" not in names:
        fail(f"{model_path.name} must include the floating/fly animation")
    for animation in model.get("animations", []):
        anim_uuid = animation.get("uuid")
        if not anim_uuid:
            fail(f"{model_path.name} has an animation without UUID")
        uuids.append(anim_uuid)
        for bone_uuid, track in (animation.get("animators") or {}).items():
            if bone_uuid not in group_ids:
                fail(f"{model_path.name} animation {animation.get('name')} references an absent bone")
            if not track.get("keyframes"):
                fail(f"{model_path.name} animation {animation.get('name')} has an empty track")
            for keyframe in track["keyframes"]:
                key_uuid = keyframe.get("uuid")
                if not key_uuid:
                    fail(f"{model_path.name} has a keyframe without UUID")
                uuids.append(key_uuid)
                if keyframe.get("channel") not in ("position", "rotation", "scale"):
                    fail(f"{model_path.name} contains an invalid animation channel")
                if not isinstance(keyframe.get("data_points"), list) or not keyframe["data_points"]:
                    fail(f"{model_path.name} has a keyframe without datapoints")

    texture_uuid = textures[0].get("uuid")
    if texture_uuid:
        uuids.append(texture_uuid)
    if len(uuids) != len(set(uuids)):
        fail(f"{model_path.name} has duplicate UUIDs across model objects, texture or animation")

    print(f"PASS {mob_id}: {len(elements)} cubes, {len(bone_names)} bones, {len(names)} animations, 128x128 embedded/external atlas")


def main() -> None:
    for mob_id in MOB_IDS:
        validate_model(mob_id)
    if not MOB_CONFIG.is_file():
        fail("Missing MythicMobs custom enemy definitions")
    yaml_text = MOB_CONFIG.read_text(encoding="utf-8")
    for mob_id in MOB_IDS:
        if not re.search(rf"(?m)^{re.escape(mob_id)}:\s*$", yaml_text):
            fail(f"MythicMobs config is missing {mob_id}")
        if f"model{{mid={mob_id};n=false}}" not in yaml_text:
            fail(f"MythicMobs config does not attach the ModelEngine model {mob_id}")
    if "prevent_other_drops" in yaml_text.lower():
        fail("Use the proper MythicMobs option spelling PreventOtherDrops")
    print(f"PASS all {len(MOB_IDS)} custom MythicMobs definitions reference matching ModelEngine models")


if __name__ == "__main__":
    main()
