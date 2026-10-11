#!/usr/bin/env python3
"""Generate four detailed, individually textured Blockbench/ModelEngine Halloween mobs."""
from __future__ import annotations

import base64
import colorsys
import copy
import io
import json
import pathlib
import uuid

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "mythicmobs" / "models"

# New ID, template ID, hue shift, saturation multiplier, brightness multiplier, highlight hue.
MODELS = (
    ("halloween_void_reaper", "halloween_gravekeeper", 0.72, 1.30, 0.82, 0.78),
    ("halloween_frost_stalker", "halloween_gravekeeper", 0.52, 0.95, 1.12, 0.50),
    ("halloween_nightmare", "halloween_hex_witch", 0.94, 1.40, 0.86, 0.96),
    ("halloween_headless_nick", "halloween_gravekeeper", 0.99, 1.25, 0.68, 0.00),
)


def recolor_texture(source: bytes, hue_shift: float, saturation: float,
                    brightness: float, accent_hue: float) -> bytes:
    with Image.open(io.BytesIO(source)) as raw:
        image = raw.convert("RGBA")
    pixels = []
    for red, green, blue, alpha in image.getdata():
        if alpha < 8:
            pixels.append((red, green, blue, alpha))
            continue
        hue, sat, value = colorsys.rgb_to_hsv(red / 255.0, green / 255.0, blue / 255.0)
        source_value = value
        if sat < 0.12 and value > 0.65:
            hue = accent_hue
            sat = max(sat, 0.42)
        else:
            hue = (hue + hue_shift) % 1.0
        sat = max(0.15, min(1.0, sat * saturation))
        # Preserve painted shadows, deepen mid-tones, and keep bright accents legible.
        contrasted = max(0.0, min(1.0, (value - 0.48) * 1.18 + 0.48))
        value = max(0.035, min(1.0, contrasted * brightness))
        if source_value > 0.82:
            sat = max(sat, 0.48)
        nr, ng, nb = colorsys.hsv_to_rgb(hue, sat, value)
        pixels.append((round(nr * 255), round(ng * 255), round(nb * 255), alpha))
    image.putdata(pixels)
    output = io.BytesIO()
    image.save(output, format="PNG", optimize=True)
    return output.getvalue()


def collect_uuids(node: object, found: set[str]) -> None:
    if isinstance(node, dict):
        value = node.get("uuid")
        if isinstance(value, str):
            found.add(value)
        for child in node.values():
            collect_uuids(child, found)
    elif isinstance(node, list):
        for child in node:
            collect_uuids(child, found)


def remap_uuids(node: object, mapping: dict[str, str]) -> object:
    if isinstance(node, dict):
        return {
            mapping.get(key, key) if isinstance(key, str) else key: remap_uuids(value, mapping)
            for key, value in node.items()
        }
    if isinstance(node, list):
        return [remap_uuids(item, mapping) for item in node]
    if isinstance(node, str):
        return mapping.get(node, node)
    return node


def find_group(nodes: list, name: str) -> dict | None:
    for node in nodes:
        if not isinstance(node, dict):
            continue
        if node.get("name") == name:
            return node
        found = find_group(node.get("children", []), name)
        if found is not None:
            return found
    return None


def remove_named_group(model: dict, name: str) -> None:
    """Remove a complete bone subtree and its elements/animation tracks."""
    target = find_group(model.get("outliner", []), name)
    if target is None:
        raise RuntimeError(f"{model.get('name')} has no '{name}' group to remove")
    target_uuid = target.get("uuid")
    removed_group_uuids: set[str] = set()
    removed_element_uuids: set[str] = set()

    def collect(node: object) -> None:
        if isinstance(node, dict):
            node_uuid = node.get("uuid")
            if isinstance(node_uuid, str): removed_group_uuids.add(node_uuid)
            for child in node.get("children", []): collect(child)
        elif isinstance(node, str):
            removed_element_uuids.add(node)

    collect(target)
    def strip_children(nodes: list) -> list:
        retained = []
        for node in nodes:
            if isinstance(node, dict):
                if node.get("uuid") == target_uuid: continue
                node["children"] = strip_children(node.get("children", []))
                retained.append(node)
            elif isinstance(node, str):
                if node not in removed_element_uuids: retained.append(node)
            else:
                retained.append(node)
        return retained

    model["outliner"] = strip_children(model.get("outliner", []))
    model["elements"] = [element for element in model.get("elements", [])
                         if element.get("uuid") not in removed_element_uuids]
    for animation in model.get("animations", []):
        animators = animation.get("animators") or {}
        for group_uuid in removed_group_uuids: animators.pop(group_uuid, None)

def add_accessory_cube(model: dict, parent_name: str, name: str,
                       start: tuple[float, float, float], end: tuple[float, float, float],
                       uv: tuple[int, int, int, int]) -> None:
    parent = find_group(model.get("outliner", []), parent_name)
    if parent is None:
        raise RuntimeError(f"{model.get('name')} has no '{parent_name}' bone for accessory {name}")

    # Use a valid Generic/Free cube template, then assign a small dedicated atlas region.
    cube = copy.deepcopy(model["elements"][0])
    cube_id = str(uuid.uuid4())
    cube["uuid"] = cube_id
    cube["name"] = name
    cube["from"] = list(start)
    cube["to"] = list(end)
    cube["origin"] = [(start[i] + end[i]) / 2 for i in range(3)]
    cube["rotation"] = [0, 0, 0]
    u, v, width, height = uv
    if u < 0 or v < 0 or u + width > 128 or v + height > 128:
        raise RuntimeError(f"Accessory UV for {name} falls outside the 128x128 texture atlas")
    cube["faces"] = {
        face: {"uv": [u, v, u + width, v + height], "texture": 0}
        for face in ("north", "east", "south", "west", "up", "down")
    }
    model["elements"].append(cube)
    parent.setdefault("children", []).append(cube_id)


def add_distinctive_geometry(model: dict, mob_id: str) -> None:
    # Give each new model a silhouette detail as well as its individually recolored atlas.
    if mob_id == "halloween_void_reaper":
        for name, start, end, parent, uv in (
            ("void_horn_left", (-5.5, 40, -2), (-3.0, 47, 1), "head", (96, 0, 8, 8)),
            ("void_horn_right", (3.0, 40, -2), (5.5, 47, 1), "head", (104, 0, 8, 8)),
            ("reaper_scythe_blade", (9.0, 27.0, -2.0), (15.0, 31.0, 1.0), "weapon", (112, 0, 8, 8)),
            ("reaper_scythe_tip", (13.0, 30.0, -1.0), (17.0, 35.0, 2.0), "weapon", (120, 0, 8, 8)),
            ("void_pauldron_left", (-7.5, 27.0, -2.5), (-3.5, 32.0, 2.0), "body", (96, 8, 8, 8)),
            ("void_pauldron_right", (3.5, 27.0, -2.5), (7.5, 32.0, 2.0), "body", (104, 8, 8, 8)),
            ("void_cloak_tatter_left", (-7.0, 8.0, 3.0), (-3.5, 18.0, 6.0), "body", (112, 8, 8, 8)),
            ("void_cloak_tatter_right", (3.5, 8.0, 3.0), (7.0, 18.0, 6.0), "body", (120, 8, 8, 8)),
        ):
            add_accessory_cube(model, parent, name, start, end, uv)
    elif mob_id == "halloween_frost_stalker":
        for name, start, end, uv in (
            ("frost_spike_left", (-8.0, 25.0, -2.0), (-5.0, 32.0, 2.0), (0, 0, 6, 8)),
            ("frost_spike_right", (5.0, 25.0, -2.0), (8.0, 32.0, 2.0), (8, 0, 6, 8)),
            ("frost_back_spike", (-2.5, 23.0, 3.5), (2.5, 31.0, 7.0), (16, 0, 6, 8)),
            ("frost_pauldron_left", (-7.5, 21.0, -2.0), (-4.0, 26.0, 2.0), (96, 16, 8, 8)),
            ("frost_pauldron_right", (4.0, 21.0, -2.0), (7.5, 26.0, 2.0), (104, 16, 8, 8)),
            ("frost_forearm_crystal_left", (-7.0, 12.0, -2.0), (-4.0, 17.0, 2.0), (112, 16, 8, 8)),
            ("frost_forearm_crystal_right", (4.0, 12.0, -2.0), (7.0, 17.0, 2.0), (120, 16, 8, 8)),
        ):
            add_accessory_cube(model, "body", name, start, end, uv)
    elif mob_id == "halloween_nightmare":
        for name, start, end, parent, uv in (
            ("nightmare_wisp_left", (-12.0, 21.0, 2.0), (-6.0, 28.0, 6.0), "body", (96, 0, 8, 8)),
            ("nightmare_wisp_right", (6.0, 21.0, 2.0), (12.0, 28.0, 6.0), "body", (104, 0, 8, 8)),
            ("nightmare_crown_left", (-5.0, 39.0, 0.0), (-2.5, 45.0, 2.0), "head", (112, 0, 8, 8)),
            ("nightmare_crown_right", (2.5, 39.0, 0.0), (5.0, 45.0, 2.0), "head", (120, 0, 8, 8)),
            ("nightmare_rib_left", (-5.0, 17.0, -3.0), (-2.0, 21.0, -1.0), "body", (96, 24, 8, 8)),
            ("nightmare_rib_right", (2.0, 17.0, -3.0), (5.0, 21.0, -1.0), "body", (104, 24, 8, 8)),
            ("nightmare_mantle_left", (-8.0, 25.0, 1.0), (-5.0, 32.0, 4.0), "body", (112, 24, 8, 8)),
            ("nightmare_mantle_right", (5.0, 25.0, 1.0), (8.0, 32.0, 4.0), "body", (120, 24, 8, 8)),
        ):
            add_accessory_cube(model, parent, name, start, end, uv)
    elif mob_id == "halloween_headless_nick":
        # Make the absent head real geometry removal, not an invisible/default face.
        remove_named_group(model, "head")
        add_accessory_cube(model, "body", "headless_ember_stump",
                           (-2.5, 31.0, -2.5), (2.5, 36.0, 2.5), (96, 0, 8, 8))
        add_accessory_cube(model, "body", "torn_riding_collar",
                           (-4.5, 26.0, -2.5), (4.5, 30.0, 2.5), (104, 0, 8, 8))
        add_accessory_cube(model, "body", "ragged_cape",
                           (-6.0, 17.0, 2.0), (6.0, 30.0, 6.0), (112, 0, 8, 8))
        add_accessory_cube(model, "body", "nick_mantle_left",
                           (-7.0, 22.0, 1.5), (-4.5, 28.0, 4.5), (120, 0, 8, 8))
        add_accessory_cube(model, "body", "nick_mantle_right",
                           (4.5, 22.0, 1.5), (7.0, 28.0, 4.5), (96, 8, 8, 8))
        add_accessory_cube(model, "body", "nick_belt_buckle",
                           (-2.0, 23.0, -3.0), (2.0, 26.0, -2.0), (104, 8, 8, 8))


def create_model(new_id: str, template_id: str, hue_shift: float, saturation: float,
                 brightness: float, accent_hue: float) -> None:
    source_model = MODEL_DIR / f"{template_id}.bbmodel"
    model = json.loads(source_model.read_text(encoding="utf-8"))
    encoded = model["textures"][0]["source"]
    if not encoded.startswith("data:image/png;base64,"):
        raise RuntimeError(f"{source_model.name} has no embedded PNG texture")
    new_png = recolor_texture(
        base64.b64decode(encoded.split(",", 1)[1], validate=True),
        hue_shift, saturation, brightness, accent_hue,
    )

    ids: set[str] = set()
    collect_uuids(model, ids)
    model = remap_uuids(model, {old: str(uuid.uuid4()) for old in ids})
    model["name"] = new_id
    model["model_identifier"] = new_id
    texture = model["textures"][0]
    texture["name"] = f"{new_id}.png"
    texture["path"] = f"{new_id}.png"
    texture["relative_path"] = f"{new_id}.png"
    texture["source"] = "data:image/png;base64," + base64.b64encode(new_png).decode("ascii")
    add_distinctive_geometry(model, new_id)

    (MODEL_DIR / f"{new_id}.bbmodel").write_text(
        json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (MODEL_DIR / f"{new_id}.png").write_bytes(new_png)
    print(f"Generated {new_id}.bbmodel and {new_id}.png from {template_id}")


def main() -> None:
    for definition in MODELS:
        create_model(*definition)


if __name__ == "__main__":
    main()
