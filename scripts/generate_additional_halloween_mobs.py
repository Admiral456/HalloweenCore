#!/usr/bin/env python3
"""Generate extra editable Blockbench/ModelEngine Halloween mobs from existing model bases."""
from __future__ import annotations

import base64
import colorsys
import copy
import io
import json
import pathlib
import uuid

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "mythicmobs" / "models"

# New ID, template ID, hue shift, saturation multiplier, brightness multiplier, highlight hue.
MODELS = (
    ("halloween_void_reaper", "halloween_gravekeeper", 0.72, 1.30, 0.72, 0.78),
    ("halloween_frost_stalker", "halloween_gravekeeper", 0.52, 0.85, 1.18, 0.50),
    ("halloween_nightmare", "halloween_hex_witch", 0.94, 1.40, 0.78, 0.96),
    ("halloween_goblin_king", "halloween_cursed_zombie", 0.27, 1.15, 0.78, 0.08),
    ("halloween_headless_nick", "halloween_cursed_zombie", 0.94, 0.55, 0.62, 0.02),
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
        if sat < 0.12 and value > 0.65:
            hue = accent_hue
            sat = max(sat, 0.35)
        else:
            hue = (hue + hue_shift) % 1.0
        sat = max(0.15, min(1.0, sat * saturation))
        value = max(0.04, min(1.0, value * brightness))
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


def add_accessory_cube(model: dict, parent_name: str, name: str,
                       start: tuple[float, float, float], end: tuple[float, float, float],
                       uv: tuple[int, int, int, int] = (96, 0, 8, 8),
                       rotation: tuple[float, float, float] = (0, 0, 0)) -> None:
    parent = find_group(model.get("outliner", []), parent_name)
    if parent is None:
        raise RuntimeError(f"{model.get('name')} has no '{parent_name}' bone for accessory {name}")
    cube = copy.deepcopy(model["elements"][0])
    cube_id = str(uuid.uuid4())
    cube["uuid"] = cube_id
    cube["name"] = name
    cube["from"] = list(start)
    cube["to"] = list(end)
    cube["origin"] = [(start[i] + end[i]) / 2 for i in range(3)]
    cube["rotation"] = list(rotation)
    u, v, width, height = uv
    if u < 0 or v < 0 or u + width > 128 or v + height > 128:
        raise RuntimeError(f"Accessory UV for {name} falls outside the 128x128 texture atlas")
    cube["faces"] = {
        face: {"uv": [u, v, u + width, v + height], "texture": 0}
        for face in ("north", "east", "south", "west", "up", "down")
    }
    model["elements"].append(cube)
    parent.setdefault("children", []).append(cube_id)


GOBLIN_TEXTURE_TILES = {
    "armor": (56, 16, 8, 8), "armor_light": (64, 16, 8, 8),
    "gold": (72, 16, 8, 8), "gold_light": (80, 16, 8, 8),
    "crimson": (88, 16, 8, 8), "crimson_light": (96, 16, 8, 8),
    "wing": (104, 16, 8, 8), "wing_rib": (112, 16, 8, 8),
    "bone": (120, 16, 8, 8), "skin": (32, 24, 8, 8),
    "emerald": (40, 24, 8, 8), "ruby": (48, 24, 8, 8),
    "black_steel": (56, 24, 8, 8), "rune": (64, 24, 8, 8),
    "leather": (72, 24, 8, 8), "deep_crimson": (80, 24, 8, 8),
    "pale_gold": (88, 24, 8, 8), "wing_shadow": (96, 24, 8, 8),
    "silver": (104, 24, 8, 8), "fang": (112, 24, 8, 8),
    "green_shadow": (120, 24, 8, 8),
}


def paint_goblin_texture(source: bytes) -> bytes:
    """Paint original royal armor, crimson cape, horns and emerald magic into dedicated UV tiles."""
    with Image.open(io.BytesIO(source)) as raw:
        image = raw.convert("RGBA")
    draw = ImageDraw.Draw(image)

    # A readable green-grey goblin face on the head's front UV.
    draw.rectangle((0, 0, 8, 8), fill=(91, 117, 75, 255))
    draw.rectangle((0, 0, 8, 1), fill=(52, 72, 47, 255))
    draw.rectangle((1, 2, 2, 3), fill=(218, 166, 49, 255))
    draw.rectangle((5, 2, 6, 3), fill=(218, 166, 49, 255))
    draw.point((1, 2), fill=(245, 216, 100, 255))
    draw.point((5, 2), fill=(245, 216, 100, 255))
    draw.point((2, 3), fill=(24, 29, 20, 255))
    draw.point((6, 3), fill=(24, 29, 20, 255))
    draw.rectangle((3, 4, 4, 5), fill=(57, 77, 48, 255))
    draw.point((2, 6), fill=(238, 226, 190, 255))
    draw.point((5, 6), fill=(238, 226, 190, 255))
    draw.rectangle((0, 7, 8, 8), fill=(55, 70, 47, 255))

    colors = {
        "armor": ((35, 49, 63, 255), (78, 94, 108, 255), (22, 31, 41, 255)),
        "armor_light": ((69, 85, 101, 255), (134, 145, 154, 255), (35, 46, 58, 255)),
        "gold": ((139, 100, 38, 255), (229, 189, 89, 255), (77, 51, 24, 255)),
        "gold_light": ((190, 143, 49, 255), (255, 226, 130, 255), (106, 70, 27, 255)),
        "crimson": ((93, 12, 27, 255), (157, 30, 48, 255), (43, 8, 18, 255)),
        "crimson_light": ((135, 21, 39, 255), (204, 50, 64, 255), (58, 7, 18, 255)),
        "wing": ((37, 43, 31, 255), (76, 91, 55, 255), (15, 19, 15, 255)),
        "wing_rib": ((63, 75, 47, 255), (111, 126, 73, 255), (25, 31, 22, 255)),
        "bone": ((190, 176, 135, 255), (235, 219, 176, 255), (95, 81, 59, 255)),
        "skin": ((91, 117, 75, 255), (131, 150, 95, 255), (47, 66, 41, 255)),
        "emerald": ((23, 132, 46, 255), (120, 255, 75, 255), (7, 48, 26, 255)),
        "ruby": ((105, 10, 28, 255), (250, 54, 66, 255), (42, 8, 20, 255)),
        "black_steel": ((24, 30, 39, 255), (65, 77, 91, 255), (9, 12, 19, 255)),
        "rune": ((24, 98, 43, 255), (126, 255, 72, 255), (9, 40, 21, 255)),
        "leather": ((65, 29, 29, 255), (120, 50, 43, 255), (30, 14, 17, 255)),
        "deep_crimson": ((63, 7, 21, 255), (115, 17, 35, 255), (25, 5, 12, 255)),
        "pale_gold": ((213, 174, 91, 255), (255, 235, 163, 255), (116, 78, 32, 255)),
        "wing_shadow": ((19, 25, 20, 255), (50, 66, 38, 255), (7, 10, 9, 255)),
        "silver": ((94, 109, 119, 255), (173, 188, 195, 255), (36, 45, 55, 255)),
        "fang": ((202, 199, 168, 255), (245, 242, 214, 255), (94, 83, 65, 255)),
        "green_shadow": ((51, 77, 43, 255), (93, 125, 59, 255), (23, 35, 21, 255)),
    }
    for name, (x, y, width, height) in GOBLIN_TEXTURE_TILES.items():
        base, highlight, shadow = colors[name]
        draw.rectangle((x, y, x + width - 1, y + height - 1), fill=base)
        draw.line((x, y, x + width - 1, y), fill=highlight)
        draw.line((x, y, x, y + height - 1), fill=highlight)
        draw.line((x, y + height - 1, x + width - 1, y + height - 1), fill=shadow)
        draw.line((x + width - 1, y, x + width - 1, y + height - 1), fill=shadow)
        if name in {"armor", "armor_light", "black_steel", "silver"}:
            draw.line((x + 2, y + 2, x + 5, y + 2), fill=highlight)
            draw.point((x + 2, y + 4), fill=shadow)
            draw.point((x + 5, y + 5), fill=shadow)
        elif name in {"gold", "gold_light", "pale_gold"}:
            draw.point((x + 3, y + 3), fill=highlight)
            draw.line((x + 2, y + 5, x + 5, y + 5), fill=shadow)
        elif name in {"crimson", "crimson_light", "deep_crimson", "leather"}:
            for offset in (2, 5):
                draw.line((x + offset, y + 1, x + offset, y + 6),
                          fill=highlight if offset == 2 else shadow)
        elif name in {"wing", "wing_shadow", "wing_rib"}:
            draw.line((x + 2, y + 2, x + 6, y + 5),
                      fill=highlight if name == "wing_rib" else shadow)
        elif name in {"emerald", "rune"}:
            draw.polygon([(x + 3, y + 1), (x + 6, y + 3),
                          (x + 4, y + 6), (x + 1, y + 4)], fill=highlight)
            draw.point((x + 3, y + 3), fill=(221, 255, 153, 255))
        elif name in {"bone", "fang", "skin", "green_shadow"}:
            draw.point((x + 2, y + 3), fill=highlight)
            draw.point((x + 5, y + 5), fill=shadow)

    for box in ((32, 96, 45, 112), (48, 96, 61, 112), (64, 96, 79, 108), (96, 96, 111, 109)):
        draw.rectangle(box, fill=(76, 8, 25, 255))
    draw.rectangle((34, 98, 36, 110), fill=(135, 20, 42, 255))
    draw.rectangle((40, 98, 41, 110), fill=(49, 5, 17, 255))
    draw.rectangle((51, 98, 53, 110), fill=(153, 25, 43, 255))
    draw.rectangle((57, 98, 58, 110), fill=(43, 6, 17, 255))
    draw.rectangle((65, 104, 78, 106), fill=(119, 19, 37, 255))
    draw.line((64, 107, 79, 107), fill=(206, 160, 62, 255))
    draw.rectangle((97, 104, 110, 106), fill=(124, 20, 37, 255))
    draw.line((96, 108, 111, 108), fill=(206, 160, 62, 255))

    draw.rectangle((32, 0, 42, 14), fill=(39, 52, 68, 255))
    draw.line((32, 0, 42, 0), fill=(182, 137, 58, 255))
    draw.rectangle((48, 0, 58, 14), fill=(29, 41, 54, 255))
    draw.rectangle((64, 0, 90, 14), fill=(38, 49, 64, 255))
    draw.rectangle((74, 1, 79, 13), fill=(65, 81, 97, 255))
    draw.line((64, 1, 90, 1), fill=(188, 145, 60, 255))
    draw.line((64, 12, 90, 12), fill=(17, 25, 34, 255))
    draw.rectangle((96, 0, 128, 10), fill=(36, 40, 49, 255))
    draw.line((96, 0, 128, 0), fill=(173, 127, 48, 255))
    draw.rectangle((0, 32, 22, 44), fill=(42, 55, 70, 255))
    draw.rectangle((32, 32, 54, 44), fill=(36, 48, 63, 255))
    draw.line((0, 32, 22, 32), fill=(185, 142, 63, 255))
    draw.line((32, 32, 54, 32), fill=(185, 142, 63, 255))

    output = io.BytesIO()
    image.save(output, format="PNG", optimize=True)
    return output.getvalue()


def add_model_bone(model: dict, name: str, origin: tuple[float, float, float],
                   parent_name: str = "root") -> dict:
    parent = find_group(model.get("outliner", []), parent_name)
    template = find_group(model.get("outliner", []), "weapon")
    if parent is None or template is None:
        raise RuntimeError(f"{model.get('name')} cannot add bone {name}: missing parent or template bone")
    bone = copy.deepcopy(template)
    bone["uuid"] = str(uuid.uuid4())
    bone["name"] = name
    bone["origin"] = list(origin)
    bone["rotation"] = [0, 0, 0]
    bone["children"] = []
    bone["selected"] = False
    bone["primary_selected"] = False
    bone["isOpen"] = False
    parent.setdefault("children", []).append(bone)
    return bone


def remove_named_elements(model: dict, names: set[str]) -> None:
    removed = {element.get("uuid") for element in model.get("elements", [])
               if element.get("name") in names}
    model["elements"] = [element for element in model.get("elements", [])
                         if element.get("uuid") not in removed]

    def strip(nodes: list) -> None:
        for node in nodes:
            if not isinstance(node, dict):
                continue
            node["children"] = [child for child in node.get("children", [])
                                if not (isinstance(child, str) and child in removed)]
            strip([child for child in node.get("children", []) if isinstance(child, dict)])

    strip(model.get("outliner", []))

def remove_head_geometry(model: dict) -> None:
    """Remove all cube references under the head bone while retaining animation bones."""
    head = find_group(model.get("outliner", []), "head")
    if head is None:
        raise RuntimeError(f"{model.get('name')} has no head bone to remove")

    removed_ids: set[str] = set()

    def strip_cubes(group: dict) -> None:
        kept_children = []
        for child in group.get("children", []):
            if isinstance(child, str):
                removed_ids.add(child)
            elif isinstance(child, dict):
                strip_cubes(child)
                kept_children.append(child)
        group["children"] = kept_children

    strip_cubes(head)
    before = len(model.get("elements", []))
    model["elements"] = [element for element in model.get("elements", [])
                         if element.get("uuid") not in removed_ids]
    if before - len(model["elements"]) < 1:
        raise RuntimeError(f"{model.get('name')} head bone contained no removable geometry")


def add_distinctive_geometry(model: dict, mob_id: str) -> None:
    # Give each new model a silhouette detail as well as its individually recolored atlas.
    if mob_id == "halloween_goblin_king":
        remove_named_elements(model, {"broken_spike_left", "broken_spike_right", "torn_gutters"})
        cubes = (
            ("head", "goblin_horn_left_base", (-8.0, 38.5, -1.3), (-5.0, 43.5, 1.7), "bone", (0, 0, 18)),
            ("head", "goblin_horn_left_curve", (-10.5, 42.0, -1.0), (-7.2, 47.5, 1.5), "bone", (0, 0, 31)),
            ("head", "goblin_horn_left_tip", (-10.1, 46.0, -0.8), (-7.8, 51.0, 1.4), "bone", (0, 0, -22)),
            ("head", "goblin_horn_right_base", (5.0, 38.5, -1.3), (8.0, 43.5, 1.7), "bone", (0, 0, -18)),
            ("head", "goblin_horn_right_curve", (7.2, 42.0, -1.0), (10.5, 47.5, 1.5), "bone", (0, 0, -31)),
            ("head", "goblin_horn_right_tip", (7.8, 46.0, -0.8), (10.1, 51.0, 1.4), "bone", (0, 0, 22)),
            ("head", "goblin_horn_band_left", (-8.4, 42.0, -1.7), (-5.0, 43.0, 2.0), "gold_light", (0, 0, 18)),
            ("head", "goblin_horn_band_right", (5.0, 42.0, -1.7), (8.4, 43.0, 2.0), "gold_light", (0, 0, -18)),
            ("head", "goblin_crown_band", (-5.5, 41.0, -4.4), (5.5, 43.7, 4.3), "gold", (0, 0, 0)),
            ("head", "goblin_crown_spike_left", (-4.6, 43.0, -3.5), (-2.8, 49.0, -1.6), "gold_light", (0, 0, 8)),
            ("head", "goblin_crown_spike_center", (-1.2, 43.0, -4.0), (1.2, 52.0, -1.5), "gold_light", (0, 0, 0)),
            ("head", "goblin_crown_spike_right", (2.8, 43.0, -3.5), (4.6, 49.0, -1.6), "gold_light", (0, 0, -8)),
            ("head", "goblin_crown_gem", (-1.2, 43.4, -4.8), (1.2, 45.8, -4.0), "ruby", (0, 0, 0)),
            ("body", "goblin_royal_cuirass", (-5.7, 22.0, -4.6), (5.7, 31.2, -3.3), "armor", (0, 0, 0)),
            ("body", "goblin_central_emblem", (-2.1, 24.0, -5.1), (2.1, 28.1, -4.4), "crimson", (0, 0, 0)),
            ("body", "goblin_emblem_ruby", (-0.9, 25.0, -5.45), (0.9, 27.0, -4.9), "ruby", (0, 0, 0)),
            ("left_arm", "goblin_heavy_pauldron", (-10.0, 29.0, -4.0), (-4.3, 35.0, 3.7), "black_steel", (0, 0, 12)),
            ("left_arm", "goblin_pauldron_gold_trim", (-9.7, 33.5, -4.1), (-4.6, 34.6, 3.8), "gold", (0, 0, 12)),
            ("right_arm", "goblin_right_pauldron", (4.3, 29.0, -4.0), (10.0, 35.0, 3.7), "armor_light", (0, 0, -12)),
            ("right_arm", "goblin_right_pauldron_trim", (4.6, 33.5, -4.1), (9.7, 34.6, 3.8), "gold", (0, 0, -12)),
            ("cloak", "goblin_royal_cape_panel", (-7.2, 8.5, 5.4), (7.2, 31.7, 6.7), "crimson", (0, 0, 0)),
            ("cloak", "goblin_cape_gold_hem", (-7.0, 8.5, 6.75), (7.0, 9.6, 7.05), "gold", (0, 0, 0)),
            ("cloak", "goblin_cape_clasp", (-1.7, 30.5, 6.3), (1.7, 33.8, 7.0), "pale_gold", (0, 0, 0)),
            ("cloak", "goblin_cape_sigil", (-0.9, 31.2, 7.0), (0.9, 33.1, 7.35), "ruby", (0, 0, 0)),
            ("weapon", "goblin_staff_shaft", (7.1, 2.5, -1.6), (8.5, 30.0, -0.2), "gold", (0, 0, 0)),
            ("weapon", "goblin_staff_grip", (6.9, 10.0, -1.9), (8.7, 16.0, 0.0), "leather", (0, 0, 0)),
            ("weapon", "goblin_staff_prong_left", (5.6, 29.0, -1.5), (8.0, 33.5, 0.1), "gold_light", (0, 0, 22)),
            ("weapon", "goblin_staff_prong_right", (8.0, 29.0, -1.5), (10.4, 33.5, 0.1), "gold_light", (0, 0, -22)),
            ("weapon", "goblin_staff_hook", (7.1, 31.5, -1.6), (10.7, 34.0, 0.2), "gold", (0, 0, 18)),
        )
        for parent, name, start, end, tile, rotation in cubes:
            add_accessory_cube(model, parent, name, start, end, GOBLIN_TEXTURE_TILES[tile], rotation)

        add_model_bone(model, "left_wing", (-7.0, 31.0, 3.5), "root")
        add_model_bone(model, "right_wing", (7.0, 31.0, 3.5), "root")
        wing_parts = (
            ("left_wing", "goblin_wing_left_upper_membrane", (-18.0, 30.0, 3.2), (-8.0, 38.0, 5.2), "wing", (0, 0, 14)),
            ("left_wing", "goblin_wing_left_inner_membrane", (-17.0, 22.0, 3.2), (-8.0, 32.0, 5.2), "wing_shadow", (0, 0, 14)),
            ("left_wing", "goblin_wing_left_outer_membrane", (-23.0, 20.0, 3.2), (-15.0, 30.0, 5.1), "wing", (0, 0, -9)),
            ("left_wing", "goblin_wing_left_lower_membrane", (-20.0, 15.0, 3.2), (-11.0, 23.0, 5.1), "wing_shadow", (0, 0, 14)),
            ("left_wing", "goblin_wing_left_rib_inner", (-11.2, 25.0, 3.0), (-10.0, 37.0, 4.2), "wing_rib", (0, 0, 22)),
            ("left_wing", "goblin_wing_left_rib_middle", (-10.7, 25.0, 3.0), (-9.5, 35.0, 4.2), "wing_rib", (0, 0, 43)),
            ("left_wing", "goblin_wing_left_rib_outer", (-10.2, 25.0, 3.0), (-9.0, 32.0, 4.2), "wing_rib", (0, 0, 62)),
            ("right_wing", "goblin_wing_right_upper_membrane", (8.0, 30.0, 3.2), (18.0, 38.0, 5.2), "wing", (0, 0, -14)),
            ("right_wing", "goblin_wing_right_inner_membrane", (8.0, 22.0, 3.2), (17.0, 32.0, 5.2), "wing_shadow", (0, 0, -14)),
            ("right_wing", "goblin_wing_right_outer_membrane", (15.0, 20.0, 3.2), (23.0, 30.0, 5.1), "wing", (0, 0, 9)),
            ("right_wing", "goblin_wing_right_lower_membrane", (11.0, 15.0, 3.2), (20.0, 23.0, 5.1), "wing_shadow", (0, 0, -14)),
            ("right_wing", "goblin_wing_right_rib_inner", (10.0, 25.0, 3.0), (11.2, 37.0, 4.2), "wing_rib", (0, 0, -22)),
            ("right_wing", "goblin_wing_right_rib_middle", (9.5, 25.0, 3.0), (10.7, 35.0, 4.2), "wing_rib", (0, 0, -43)),
            ("right_wing", "goblin_wing_right_rib_outer", (9.0, 25.0, 3.0), (10.2, 32.0, 4.2), "wing_rib", (0, 0, -62)),
        )
        for parent, name, start, end, tile, rotation in wing_parts:
            add_accessory_cube(model, parent, name, start, end, GOBLIN_TEXTURE_TILES[tile], rotation)

        add_model_bone(model, "staff_orb", (9.2, 32.4, -0.9), "weapon")
        for name, start, end, tile in (
            ("goblin_staff_orb_core", (8.0, 30.7, -2.0), (11.8, 34.5, 1.8), "emerald"),
            ("goblin_staff_orb_highlight", (8.8, 32.1, -2.25), (10.2, 33.5, -1.9), "rune"),
            ("goblin_staff_orb_lower_shard", (8.7, 30.0, -1.6), (10.4, 30.8, 0.3), "emerald"),
        ):
            add_accessory_cube(model, "staff_orb", name, start, end, GOBLIN_TEXTURE_TILES[tile])
        return

    if mob_id == "halloween_void_reaper":
        for name, start, end in (
            ("void_horn_left", (-5.5, 40, -2), (-3.0, 47, 1)),
            ("void_horn_right", (3.0, 40, -2), (5.5, 47, 1)),
            ("reaper_scythe_blade", (9.0, 27.0, -2.0), (15.0, 31.0, 1.0)),
            ("reaper_scythe_tip", (13.0, 30.0, -1.0), (17.0, 35.0, 2.0)),
        ):
            parent = "head" if "horn" in name else "weapon"
            add_accessory_cube(model, parent, name, start, end, (96, 0, 8, 8))
    elif mob_id == "halloween_frost_stalker":
        for name, start, end, uv in (
            ("frost_spike_left", (-8.0, 25.0, -2.0), (-5.0, 32.0, 2.0), (0, 0, 6, 8)),
            ("frost_spike_right", (5.0, 25.0, -2.0), (8.0, 32.0, 2.0), (0, 0, 6, 8)),
            ("frost_back_spike", (-2.5, 23.0, 3.5), (2.5, 31.0, 7.0), (0, 0, 6, 8)),
        ):
            add_accessory_cube(model, "body", name, start, end, uv)
    elif mob_id == "halloween_nightmare":
        for name, start, end, parent in (
            ("nightmare_wisp_left", (-12.0, 21.0, 2.0), (-6.0, 28.0, 6.0), "body"),
            ("nightmare_wisp_right", (6.0, 21.0, 2.0), (12.0, 28.0, 6.0), "body"),
            ("nightmare_crown_left", (-5.0, 39.0, 0.0), (-2.5, 45.0, 2.0), "head"),
            ("nightmare_crown_right", (2.5, 39.0, 0.0), (5.0, 45.0, 2.0), "head"),
        ):
            add_accessory_cube(model, parent, name, start, end, (96, 0, 8, 8))
    elif mob_id == "halloween_headless_nick":
        remove_head_geometry(model)
        add_accessory_cube(model, "body", "nick_bloody_neck_stump",
                           (-2.4, 37.5, -1.8), (2.4, 41.0, 2.2), (96, 0, 8, 8))
        add_accessory_cube(model, "body", "nick_shoulder_collar",
                           (-5.0, 35.0, -2.8), (5.0, 38.5, 2.8), (96, 8, 8, 8))
        add_accessory_cube(model, "weapon", "nick_executioner_blade",
                           (9.0, 26.0, -2.0), (16.0, 31.0, 1.5), (96, 16, 8, 8))
        add_accessory_cube(model, "weapon", "nick_blade_tip",
                           (13.0, 30.0, -1.5), (18.0, 35.0, 1.5), (104, 16, 8, 8))


def animation_template_keyframe(model: dict, channel: str) -> dict:
    if channel == "scale":
        template = animation_template_keyframe(model, "position")
        template["channel"] = "scale"
        return template
    for animation in model.get("animations", []):
        for track in (animation.get("animators") or {}).values():
            for keyframe in track.get("keyframes", []):
                if keyframe.get("channel") == channel:
                    return copy.deepcopy(keyframe)
    raise RuntimeError(f"{model.get('name')} has no template keyframe for channel {channel}")


def add_animation_clip(model: dict, name: str, length: float,
                       tracks: dict[str, tuple[str, list[tuple[float, tuple[float, float, float]]]]],
                       loop: str = "once") -> None:
    if any(animation.get("name") == name for animation in model.get("animations", [])):
        return
    template = next((animation for animation in model.get("animations", [])
                     if animation.get("name") == "attack"), None)
    if template is None:
        raise RuntimeError(f"{model.get('name')} cannot generate {name}: missing attack template")
    clip = copy.deepcopy(template)
    clip.update({
        "uuid": str(uuid.uuid4()),
        "name": name,
        "loop": loop,
        "length": float(length),
        "selected": False,
        "saved": True,
        "animators": {},
    })
    for bone_key, (channel, frames) in tracks.items():
        # Suffix keys express multiple channels on the same bone in a normal dict.
        bone_name = bone_key[:-9] if bone_key.endswith("_position") else bone_key
        bone = find_group(model.get("outliner", []), bone_name)
        if bone is None:
            raise RuntimeError(f"{model.get('name')} has no '{bone_name}' bone for {name}")
        template_frame = animation_template_keyframe(model, channel)
        keyframes = []
        for time, vector in frames:
            keyframe = copy.deepcopy(template_frame)
            keyframe["uuid"] = str(uuid.uuid4())
            keyframe["channel"] = channel
            keyframe["time"] = float(time)
            keyframe["interpolation"] = "catmullrom"
            keyframe["data_points"] = [{"x": float(vector[0]), "y": float(vector[1]), "z": float(vector[2])}]
            keyframes.append(keyframe)
        animator = clip["animators"].setdefault(bone["uuid"], {
            "name": bone_name,
            "type": "bone",
            "rotation_global": False,
            "quaternion_interpolation": False,
            "keyframes": [],
        })
        animator["keyframes"].extend(keyframes)
    model.setdefault("animations", []).append(clip)


def enrich_animation_set(model: dict) -> None:
    """Add authored reaction/death and encounter-specific clips to all ten special models."""
    mob_id = model.get("model_identifier", model.get("name", ""))
    if mob_id == "halloween_goblin_king":
        add_animation_clip(model, "hit_reaction", 0.28, {
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.08, (-14, 0, 7)), (0.28, (0, 0, 0))]),
            "head": ("rotation", [(0.0, (0, 0, 0)), (0.08, (0, 0, -9)), (0.28, (0, 0, 0))]),
        })
        add_animation_clip(model, "death", 1.35, {
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.35, (14, 0, -5)), (1.35, (68, 0, 0))]),
            "body_position": ("position", [(0.0, (0, 0, 0)), (0.55, (0, -1.5, 0)), (1.35, (0, -7, 0))]),
            "left_wing": ("rotation", [(0.0, (0, 0, 18)), (0.8, (0, 0, 5)), (1.35, (0, 0, 0))]),
            "right_wing": ("rotation", [(0.0, (0, 0, -18)), (0.8, (0, 0, -5)), (1.35, (0, 0, 0))]),
        })
    else:
        add_animation_clip(model, "hit_reaction", 0.24, {
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.08, (-12, 0, 6)), (0.24, (0, 0, 0))]),
        })
        add_animation_clip(model, "death", 0.82, {
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.3, (28, 0, 0)), (0.82, (72, 0, 0))]),
            "body_position": ("position", [(0.0, (0, 0, 0)), (0.3, (0, -2, 0)), (0.82, (0, -7, 0))]),
        })

    if mob_id == "halloween_goblin_king":
        add_animation_clip(model, "idle_breath", 2.4, {
            "body": ("rotation", [(0.0, (0, 0, 0)), (1.2, (-1.8, 0, 0)), (2.4, (0, 0, 0))]),
            "body_position": ("position", [(0.0, (0, 0, 0)), (1.2, (0, 0.45, 0)), (2.4, (0, 0, 0))]),
        }, loop="loop")
        add_animation_clip(model, "head_turn", 1.1, {
            "head": ("rotation", [(0.0, (0, 0, 0)), (0.35, (0, 16, 0)), (0.75, (0, -11, 0)), (1.1, (0, 0, 0))]),
        })
        add_animation_clip(model, "wing_fold", 1.0, {
            "left_wing": ("rotation", [(0.0, (0, 0, 18)), (0.5, (0, 0, 25)), (1.0, (0, 0, 18))]),
            "right_wing": ("rotation", [(0.0, (0, 0, -18)), (0.5, (0, 0, -25)), (1.0, (0, 0, -18))]),
        })
        add_animation_clip(model, "wing_spread", 0.9, {
            "left_wing": ("rotation", [(0.0, (0, 0, 24)), (0.3, (0, 0, 2)), (0.9, (0, 0, -8))]),
            "right_wing": ("rotation", [(0.0, (0, 0, -24)), (0.3, (0, 0, -2)), (0.9, (0, 0, 8))]),
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.4, (-5, 0, 0)), (0.9, (0, 0, 0))]),
        })
        add_animation_clip(model, "wing_flap", 1.0, {
            "left_wing": ("rotation", [(0.0, (0, 0, -6)), (0.25, (0, 0, -24)), (0.5, (0, 0, 2)), (0.75, (0, 0, -24)), (1.0, (0, 0, -6))]),
            "right_wing": ("rotation", [(0.0, (0, 0, 6)), (0.25, (0, 0, 24)), (0.5, (0, 0, -2)), (0.75, (0, 0, 24)), (1.0, (0, 0, 6))]),
        }, loop="loop")
        add_animation_clip(model, "staff_raise", 0.8, {
            "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.3, (-45, 0, 12)), (0.8, (-72, 0, 8))]),
            "weapon": ("rotation", [(0.0, (0, 0, 0)), (0.3, (-10, 0, 4)), (0.8, (-18, 0, 0))]),
        })
        add_animation_clip(model, "spell_cast", 0.95, {
            "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.28, (-68, 0, 6)), (0.55, (18, 0, -8)), (0.95, (0, 0, 0))]),
            "weapon": ("rotation", [(0.0, (0, 0, 0)), (0.25, (-16, 0, 0)), (0.55, (14, 0, 0)), (0.95, (0, 0, 0))]),
            "staff_orb": ("scale", [(0.0, (1.0, 1.0, 1.0)), (0.35, (1.25, 1.25, 1.25)), (0.65, (1.45, 1.45, 1.45)), (0.95, (1.0, 1.0, 1.0))]),
        })
        add_animation_clip(model, "staff_strike", 0.95, {
            "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.22, (-82, 0, 6)), (0.48, (38, 0, -12)), (0.95, (0, 0, 0))]),
            "weapon": ("rotation", [(0.0, (0, 0, 0)), (0.22, (-8, 0, 0)), (0.48, (20, 0, 0)), (0.95, (0, 0, 0))]),
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.48, (9, 0, 0)), (0.95, (0, 0, 0))]),
        })
        add_animation_clip(model, "wing_slam", 1.0, {
            "left_wing": ("rotation", [(0.0, (0, 0, -8)), (0.4, (0, 0, -43)), (0.62, (0, 0, 26)), (1.0, (0, 0, 0))]),
            "right_wing": ("rotation", [(0.0, (0, 0, 8)), (0.4, (0, 0, 43)), (0.62, (0, 0, -26)), (1.0, (0, 0, 0))]),
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.5, (18, 0, 0)), (1.0, (0, 0, 0))]),
        })
        add_animation_clip(model, "stagger", 0.68, {
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.12, (-17, 0, 8)), (0.35, (12, 0, -5)), (0.68, (0, 0, 0))]),
            "head": ("rotation", [(0.0, (0, 0, 0)), (0.12, (12, 0, -12)), (0.5, (-5, 0, 5)), (0.68, (0, 0, 0))]),
        })
        add_animation_clip(model, "staff_orb_pulse", 1.4, {
            "staff_orb": ("scale", [(0.0, (1.0, 1.0, 1.0)), (0.35, (1.22, 1.22, 1.22)), (0.7, (1.0, 1.0, 1.0)), (1.05, (1.3, 1.3, 1.3)), (1.4, (1.0, 1.0, 1.0))]),
        }, loop="loop")
        add_animation_clip(model, "heavy_attack", 0.95, {
            "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.25, (-78, 0, 0)),
                                       (0.48, (52, 0, 0)), (0.95, (0, 0, 0))]),
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.25, (0, 0, -8)),
                                 (0.5, (0, 0, 10)), (0.95, (0, 0, 0))]),
        })
        add_animation_clip(model, "phase_transition", 1.4, {
            "body_position": ("position", [(0.0, (0, 0, 0)), (0.3, (0, 1.5, 0)),
                                          (0.8, (0, -2, 0)), (1.4, (0, 0, 0))]),
            "left_wing": ("rotation", [(0.0, (0, 0, 18)), (0.55, (0, 0, -18)), (1.4, (0, 0, -8))]),
            "right_wing": ("rotation", [(0.0, (0, 0, -18)), (0.55, (0, 0, 18)), (1.4, (0, 0, 8))]),
            "staff_orb": ("scale", [(0.0, (1.0, 1.0, 1.0)), (0.6, (1.4, 1.4, 1.4)), (1.4, (1.0, 1.0, 1.0))]),
            "left_arm": ("rotation", [(0.0, (0, 0, 0)), (0.45, (-80, 0, -12)),
                                     (0.9, (-105, 0, -8)), (1.4, (0, 0, 0))]),
            "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.45, (-80, 0, 12)),
                                      (0.9, (-105, 0, 8)), (1.4, (0, 0, 0))]),
        })
        add_animation_clip(model, "victory", 1.15, {
            "left_arm": ("rotation", [(0.0, (0, 0, 0)), (0.35, (-100, 0, -18)), (1.15, (-85, 0, -12))]),
            "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.35, (-100, 0, 18)), (1.15, (-85, 0, 12))]),
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.5, (-8, 0, 0)), (1.15, (0, 0, 0))]),
        })
    elif mob_id == "halloween_headless_nick":
        add_animation_clip(model, "charge", 0.7, {
            "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.18, (35, 0, 12)),
                                       (0.4, (-55, 0, -15)), (0.7, (0, 0, 0))]),
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.2, (-18, 0, 0)),
                                 (0.45, (12, 0, 0)), (0.7, (0, 0, 0))]),
        })
        add_animation_clip(model, "executioner_strike", 0.78, {
            "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.24, (-95, 0, 12)),
                                       (0.45, (52, 0, -8)), (0.78, (0, 0, 0))]),
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.28, (-8, 0, 0)),
                                 (0.48, (22, 0, 0)), (0.78, (0, 0, 0))]),
        })
        add_animation_clip(model, "retreat", 0.8, {
            "left_leg": ("rotation", [(0.0, (0, 0, 0)), (0.2, (26, 0, 0)),
                                     (0.4, (0, 0, 0)), (0.6, (-26, 0, 0)), (0.8, (0, 0, 0))]),
            "right_leg": ("rotation", [(0.0, (0, 0, 0)), (0.2, (-26, 0, 0)),
                                      (0.4, (0, 0, 0)), (0.6, (26, 0, 0)), (0.8, (0, 0, 0))]),
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.35, (0, 0, 8)), (0.8, (0, 0, 0))]),
        })
    else:
        extra_clip = {
            "halloween_cursed_zombie": ("frenzy", {
                "left_arm": ("rotation", [(0.0, (0, 0, 0)), (0.15, (-55, 0, -10)), (0.35, (35, 0, 15)), (0.65, (0, 0, 0))]),
            }),
            "halloween_void_reaper": ("void_cast", {
                "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.25, (-70, 0, 0)), (0.55, (25, 0, 0)), (0.9, (0, 0, 0))]),
            }),
            "halloween_gravekeeper": ("ground_slam", {
                "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.25, (-85, 0, 10)), (0.5, (35, 0, 0)), (0.9, (0, 0, 0))]),
                "body": ("rotation", [(0.0, (0, 0, 0)), (0.35, (18, 0, 0)), (0.55, (-15, 0, 0)), (0.9, (0, 0, 0))]),
            }),
            "halloween_frost_stalker": ("pounce", {
                "body": ("rotation", [(0.0, (0, 0, 0)), (0.2, (-22, 0, 0)), (0.45, (18, 0, 0)), (0.8, (0, 0, 0))]),
                "body_position": ("position", [(0.0, (0, 0, 0)), (0.2, (0, 2, 0)), (0.45, (0, -1, 0)), (0.8, (0, 0, 0))]),
            }),
            "halloween_blood_spider": ("blood_leap", {
                "body": ("rotation", [(0.0, (0, 0, 0)), (0.15, (-25, 0, 0)), (0.35, (15, 0, 0)), (0.7, (0, 0, 0))]),
            }),
            "halloween_pumpkin_wraith": ("ember_burst", {
                "body": ("rotation", [(0.0, (0, 0, 0)), (0.2, (0, -18, 0)), (0.4, (0, 18, 0)), (0.8, (0, 0, 0))]),
            }),
            "halloween_hex_witch": ("hex_cast", {
                "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.25, (-70, 0, 10)), (0.55, (30, 0, -8)), (0.9, (0, 0, 0))]),
                "body": ("rotation", [(0.0, (0, 0, 0)), (0.45, (0, 8, 0)), (0.9, (0, 0, 0))]),
            }),
            "halloween_nightmare": ("spectral_charge", {
                "body": ("rotation", [(0.0, (0, 0, 0)), (0.2, (-28, 0, 0)), (0.45, (18, 0, 0)), (0.8, (0, 0, 0))]),
                "body_position": ("position", [(0.0, (0, 0, 0)), (0.2, (0, 2, 0)), (0.45, (0, -1, 0)), (0.8, (0, 0, 0))]),
            }),
        }.get(mob_id, (None, None))
        if extra_clip[0]:
            add_animation_clip(model, extra_clip[0], 0.9, extra_clip[1])


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
    if new_id == "halloween_goblin_king":
        new_png = paint_goblin_texture(new_png)

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



NICK_STEED_UVS = {
    "hide": (0, 0, 16, 16),
    "hide_light": (16, 0, 16, 16),
    "rib": (32, 0, 16, 16),
    "plate": (48, 0, 16, 16),
    "crimson": (64, 0, 16, 16),
    "gold": (80, 0, 16, 16),
    "spectral": (96, 0, 16, 16),
    "shadow": (112, 0, 16, 16),
    "bone": (0, 16, 16, 16),
    "bone_dark": (16, 16, 16, 16),
    "leather": (32, 16, 16, 16),
    "hoof": (48, 16, 16, 16),
    "mane": (64, 16, 16, 16),
    "rune": (80, 16, 16, 16),
    "strap": (96, 16, 16, 16),
    "rust": (112, 16, 16, 16),
}


def paint_nick_steed_texture() -> bytes:
    """Create a crisp, original pixel-art atlas for Nick's separate undead warhorse."""
    image = Image.new("RGBA", (128, 128), (21, 20, 26, 255))
    draw = ImageDraw.Draw(image)
    colors = {
        "hide": ((35, 35, 43, 255), (76, 75, 82, 255), (15, 15, 21, 255)),
        "hide_light": ((57, 58, 67, 255), (104, 103, 110, 255), (28, 28, 36, 255)),
        "rib": ((87, 83, 82, 255), (145, 134, 122, 255), (39, 36, 40, 255)),
        "plate": ((34, 42, 48, 255), (75, 87, 91, 255), (12, 17, 22, 255)),
        "crimson": ((75, 10, 23, 255), (151, 27, 42, 255), (29, 5, 14, 255)),
        "gold": ((110, 78, 35, 255), (204, 160, 73, 255), (48, 33, 21, 255)),
        "spectral": ((19, 75, 69, 255), (66, 224, 174, 255), (7, 27, 32, 255)),
        "shadow": ((13, 13, 20, 255), (37, 31, 45, 255), (4, 4, 10, 255)),
        "bone": ((161, 153, 130, 255), (225, 210, 173, 255), (70, 63, 57, 255)),
        "bone_dark": ((95, 88, 81, 255), (158, 145, 126, 255), (37, 34, 35, 255)),
        "leather": ((54, 38, 34, 255), (111, 74, 55, 255), (22, 17, 20, 255)),
        "hoof": ((22, 22, 28, 255), (61, 61, 69, 255), (7, 7, 12, 255)),
        "mane": ((20, 16, 25, 255), (65, 33, 49, 255), (7, 6, 11, 255)),
        "rune": ((10, 61, 50, 255), (101, 255, 167, 255), (5, 25, 26, 255)),
        "strap": ((61, 42, 30, 255), (159, 114, 52, 255), (24, 19, 19, 255)),
        "rust": ((71, 31, 31, 255), (137, 53, 41, 255), (28, 10, 17, 255)),
    }
    for name, (x, y, width, height) in NICK_STEED_UVS.items():
        base, highlight, shadow = colors[name]
        draw.rectangle((x, y, x + width - 1, y + height - 1), fill=base)
        draw.line((x, y, x + width - 1, y), fill=highlight)
        draw.line((x, y, x, y + height - 1), fill=highlight)
        draw.line((x, y + height - 1, x + width - 1, y + height - 1), fill=shadow)
        draw.line((x + width - 1, y, x + width - 1, y + height - 1), fill=shadow)
        if name in {"hide", "hide_light", "shadow", "mane"}:
            for offset in (3, 8, 12):
                draw.line((x + offset, y + 2, x + max(0, offset - 2), y + 13),
                          fill=highlight if offset != 8 else shadow)
            draw.point((x + 5, y + 5), fill=shadow)
            draw.point((x + 10, y + 9), fill=highlight)
        elif name in {"rib", "bone", "bone_dark"}:
            draw.rectangle((x + 3, y + 3, x + 5, y + 12), fill=highlight)
            draw.rectangle((x + 10, y + 2, x + 12, y + 10), fill=shadow)
            draw.line((x + 3, y + 12, x + 12, y + 12), fill=base)
        elif name in {"plate", "hoof"}:
            draw.rectangle((x + 3, y + 3, x + 12, y + 11), outline=highlight)
            draw.line((x + 4, y + 5, x + 11, y + 5), fill=shadow)
            draw.point((x + 5, y + 8), fill=highlight)
            draw.point((x + 10, y + 8), fill=highlight)
        elif name in {"crimson", "rust", "leather", "strap"}:
            draw.line((x + 4, y + 2, x + 4, y + 13), fill=highlight)
            draw.line((x + 11, y + 2, x + 11, y + 13), fill=shadow)
            draw.rectangle((x + 6, y + 5, x + 9, y + 9), fill=base)
            draw.point((x + 7, y + 7), fill=highlight)
        elif name in {"gold", "spectral", "rune"}:
            draw.polygon([(x + 8, y + 2), (x + 13, y + 7), (x + 8, y + 13), (x + 3, y + 7)],
                         outline=highlight, fill=base)
            draw.point((x + 8, y + 7), fill=highlight)
            draw.point((x + 7, y + 8), fill=(232, 255, 220, 255))
    output = io.BytesIO()
    image.save(output, format="PNG", optimize=True)
    return output.getvalue()


def add_steed_bone(model: dict, template_bone: dict, name: str,
                   origin: tuple[float, float, float], parent_name: str = "root") -> dict:
    parent = find_group(model.get("outliner", []), parent_name)
    if parent is None:
        raise RuntimeError(f"{model.get('name')} has no '{parent_name}' parent for steed bone '{name}'")
    bone = copy.deepcopy(template_bone)
    bone["uuid"] = str(uuid.uuid4())
    bone["name"] = name
    bone["origin"] = list(origin)
    bone["rotation"] = [0, 0, 0]
    bone["children"] = []
    bone["selected"] = False
    bone["primary_selected"] = False
    bone["isOpen"] = False
    parent.setdefault("children", []).append(bone)
    return bone


def add_steed_cube(model: dict, template_cube: dict, parent_name: str, name: str,
                   start: tuple[float, float, float], end: tuple[float, float, float],
                   tile: str, rotation: tuple[float, float, float] = (0, 0, 0)) -> None:
    parent = find_group(model.get("outliner", []), parent_name)
    if parent is None:
        raise RuntimeError(f"{model.get('name')} has no '{parent_name}' bone for steed part '{name}'")
    cube = copy.deepcopy(template_cube)
    cube_uuid = str(uuid.uuid4())
    cube["uuid"] = cube_uuid
    cube["name"] = name
    cube["from"] = list(start)
    cube["to"] = list(end)
    cube["origin"] = [(start[i] + end[i]) / 2 for i in range(3)]
    cube["rotation"] = list(rotation)
    u, v, width, height = NICK_STEED_UVS[tile]
    cube["faces"] = {
        face: {"uv": [u, v, u + width, v + height], "texture": 0}
        for face in ("north", "east", "south", "west", "up", "down")
    }
    model.setdefault("elements", []).append(cube)
    parent.setdefault("children", []).append(cube_uuid)


def create_nick_steed_model() -> None:
    """Generate a separate four-legged undead warhorse blueprint for Headless Nick."""
    base_path = MODEL_DIR / "halloween_cursed_zombie.bbmodel"
    model = json.loads(base_path.read_text(encoding="utf-8"))
    source_bone_template = find_group(model.get("outliner", []), "body")
    source_cube_template = copy.deepcopy(model["elements"][0])
    if source_bone_template is None:
        raise RuntimeError("Cannot generate Nick's steed: missing compatible Blockbench bone template")
    template_animation_names = {animation.get("name") for animation in model.get("animations", [])}

    # Rebuild a clean skeleton instead of leaving any human/undead geometry visible.
    root = copy.deepcopy(model["outliner"][0])
    root["uuid"] = str(uuid.uuid4())
    root["name"] = "root"
    root["origin"] = [0, 0, 0]
    root["children"] = []
    root["selected"] = False
    root["primary_selected"] = False
    model["outliner"] = [root]
    model["elements"] = []

    bones = (
        ("body", (0, 15, 0), "root"),
        ("neck", (0, 20, -10), "root"),
        ("head", (0, 24, -15), "neck"),
        ("front_left_leg", (-4.5, 12, -7), "root"),
        ("front_right_leg", (4.5, 12, -7), "root"),
        ("rear_left_leg", (-4.5, 12, 6), "root"),
        ("rear_right_leg", (4.5, 12, 6), "root"),
        ("mane", (0, 22, -6), "root"),
        ("tail", (0, 15, 9), "root"),
        ("barding", (0, 15, 0), "root"),
        ("bridle", (0, 24, -15), "head"),
    )
    for name, origin, parent in bones:
        add_steed_bone(model, source_bone_template, name, origin, parent)

    # Four distinct articulated legs and hooves; the rider's mount is not the Nightmare model.
    cubes = (
        ("body", "steed_barrel", (-7, 10, -10), (7, 20, 9), "hide", (0, 0, 0)),
        ("body", "steed_rib_cage", (-6.7, 11.4, -7.5), (6.7, 18.0, 5.8), "rib", (0, 0, 0)),
        ("body", "steed_flank_plate_left", (-7.8, 11.5, -6.2), (-6.7, 17.5, 5.7), "plate", (0, 0, 0)),
        ("body", "steed_flank_plate_right", (6.7, 11.5, -6.2), (7.8, 17.5, 5.7), "plate", (0, 0, 0)),
        ("neck", "steed_neck", (-4.2, 15.8, -15.3), (4.2, 24.8, -7.5), "hide_light", (0, 0, 0)),
        ("head", "steed_skull", (-3.6, 21.5, -19.3), (3.6, 28.5, -12.7), "hide", (0, 0, 0)),
        ("head", "steed_muzzle", (-3.25, 19.8, -22.0), (3.25, 23.8, -17.5), "bone_dark", (0, 0, 0)),
        ("head", "steed_jaw", (-2.9, 20.0, -20.2), (2.9, 21.5, -17.0), "bone", (0, 0, 0)),
        ("head", "steed_ear_left", (-2.8, 27.5, -16.7), (-1.3, 32.5, -14.9), "bone_dark", (0, 0, -8)),
        ("head", "steed_ear_right", (1.3, 27.5, -16.7), (2.8, 32.5, -14.9), "bone_dark", (0, 0, 8)),
        ("head", "steed_eye_left", (-3.72, 24.5, -17.3), (-3.25, 25.8, -15.8), "spectral", (0, 0, 0)),
        ("head", "steed_eye_right", (3.25, 24.5, -17.3), (3.72, 25.8, -15.8), "spectral", (0, 0, 0)),
        ("front_left_leg", "steed_front_left_shin", (-6.0, 1.8, -9.1), (-3.0, 13.0, -5.2), "hide_light", (0, 0, -2)),
        ("front_right_leg", "steed_front_right_shin", (3.0, 1.8, -9.1), (6.0, 13.0, -5.2), "hide_light", (0, 0, 2)),
        ("rear_left_leg", "steed_rear_left_shin", (-6.1, 1.8, 5.0), (-3.0, 13.0, 9.1), "hide_light", (0, 0, -2)),
        ("rear_right_leg", "steed_rear_right_shin", (3.0, 1.8, 5.0), (6.1, 13.0, 9.1), "hide_light", (0, 0, 2)),
        ("front_left_leg", "steed_front_left_hoof", (-6.3, 0.0, -9.5), (-2.8, 3.0, -4.8), "hoof", (0, 0, 0)),
        ("front_right_leg", "steed_front_right_hoof", (2.8, 0.0, -9.5), (6.3, 3.0, -4.8), "hoof", (0, 0, 0)),
        ("rear_left_leg", "steed_rear_left_hoof", (-6.3, 0.0, 4.7), (-2.8, 3.0, 9.5), "hoof", (0, 0, 0)),
        ("rear_right_leg", "steed_rear_right_hoof", (2.8, 0.0, 4.7), (6.3, 3.0, 9.5), "hoof", (0, 0, 0)),
        ("mane", "steed_mane_front", (-1.5, 22.5, -14.5), (1.5, 29.0, -7.0), "mane", (0, 0, 0)),
        ("mane", "steed_mane_middle", (-2.0, 17.5, -6.8), (2.0, 24.0, 2.0), "mane", (0, 0, 0)),
        ("mane", "steed_mane_left_tuft", (-3.4, 19.0, -9.0), (-1.5, 23.0, -4.8), "shadow", (0, 0, 13)),
        ("mane", "steed_mane_right_tuft", (1.5, 19.0, -8.3), (3.4, 23.0, -4.2), "shadow", (0, 0, -13)),
        ("tail", "steed_tail_root", (-1.2, 13.0, 8.0), (1.2, 19.0, 13.5), "mane", (0, 0, 0)),
        ("tail", "steed_tail_tip", (-2.0, 8.0, 12.0), (2.0, 14.0, 17.0), "shadow", (0, 0, 0)),
        ("barding", "steed_saddle_pad", (-4.5, 19.2, -5.8), (4.5, 21.0, 4.8), "leather", (0, 0, 0)),
        ("barding", "steed_saddle_rim", (-4.8, 20.0, -5.0), (4.8, 22.0, 3.8), "crimson", (0, 0, 0)),
        ("barding", "steed_chest_barding", (-5.2, 14.0, -11.0), (5.2, 19.0, -8.2), "plate", (0, 0, 0)),
        ("barding", "steed_barding_gold_trim", (-5.3, 17.3, -11.2), (5.3, 18.3, -8.0), "gold", (0, 0, 0)),
        ("barding", "steed_side_strap_left", (-7.9, 12.0, -6.5), (-7.0, 17.5, 6.0), "strap", (0, 0, 0)),
        ("barding", "steed_side_strap_right", (7.0, 12.0, -6.5), (7.9, 17.5, 6.0), "strap", (0, 0, 0)),
        ("barding", "steed_crimson_saddle_cloth", (-5.2, 17.8, -5.2), (5.2, 19.0, 4.0), "crimson", (0, 0, 0)),
        ("barding", "steed_saddle_emblem", (-1.2, 18.9, -5.5), (1.2, 20.2, -4.4), "rune", (0, 0, 0)),
        ("bridle", "steed_forehead_braid", (-0.8, 25.5, -19.6), (0.8, 27.2, -12.5), "bone", (0, 0, 0)),
        ("bridle", "steed_muzzle_strap", (-3.5, 22.0, -22.2), (3.5, 23.2, -21.5), "leather", (0, 0, 0)),
        ("bridle", "steed_cheek_strap_left", (-3.8, 21.5, -18.0), (-3.0, 26.2, -14.8), "strap", (0, 0, 0)),
        ("bridle", "steed_cheek_strap_right", (3.0, 21.5, -18.0), (3.8, 26.2, -14.8), "strap", (0, 0, 0)),
        ("bridle", "steed_rein_left", (-4.6, 19.0, -14.8), (-3.9, 20.2, -8.0), "leather", (0, 0, -8)),
        ("bridle", "steed_rein_right", (3.9, 19.0, -14.8), (4.6, 20.2, -8.0), "leather", (0, 0, 8)),
    )
    for parent, name, start, end, tile, rotation in cubes:
        add_steed_cube(model, source_cube_template, parent, name, start, end, tile, rotation)

    encoded_png = paint_nick_steed_texture()
    texture = model["textures"][0]
    texture["uuid"] = str(uuid.uuid4())
    texture["name"] = "halloween_nick_steed.png"
    texture["path"] = "halloween_nick_steed.png"
    texture["relative_path"] = "halloween_nick_steed.png"
    texture["width"] = texture["height"] = 128
    texture["uv_width"] = texture["uv_height"] = 128
    texture["source"] = "data:image/png;base64," + base64.b64encode(encoded_png).decode("ascii")
    model["name"] = "halloween_nick_steed"
    model["model_identifier"] = "halloween_nick_steed"
    model["resolution"] = {"width": 128, "height": 128}
    # Preserve valid sample keyframes for both position and rotation while authoring new clips.
    # Rename the non-attack templates so they cannot collide with the steed's own animation names.
    source_model = json.loads(base_path.read_text(encoding="utf-8"))
    source_animations = source_model.get("animations", [])
    if not any(animation.get("name") == "attack" for animation in source_animations):
        raise RuntimeError("Cannot generate Nick's steed: source model has no animation template")
    model["animations"] = []
    for source_animation in source_animations:
        template_animation = copy.deepcopy(source_animation)
        if template_animation.get("name") != "attack":
            template_animation["name"] = "__template_" + str(template_animation.get("name", "clip"))
        model["animations"].append(template_animation)

    add_animation_clip(model, "idle", 1.6, {
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.8, (-1.5, 0, 0)), (1.6, (0, 0, 0))]),
        "body_position": ("position", [(0.0, (0, 0, 0)), (0.8, (0, 0.3, 0)), (1.6, (0, 0, 0))]),
        "head": ("rotation", [(0.0, (0, 0, 0)), (0.6, (-2, 0, 1)), (1.2, (2, 0, -1)), (1.6, (0, 0, 0))]),
        "tail": ("rotation", [(0.0, (0, 0, 0)), (0.4, (0, 0, 10)), (0.8, (0, 0, -8)), (1.2, (0, 0, 6)), (1.6, (0, 0, 0))]),
    }, loop="loop")
    add_animation_clip(model, "walk", 0.8, {
        "front_left_leg": ("rotation", [(0.0, (18, 0, 0)), (0.2, (0, 0, 0)), (0.4, (-18, 0, 0)), (0.6, (0, 0, 0)), (0.8, (18, 0, 0))]),
        "rear_right_leg": ("rotation", [(0.0, (-17, 0, 0)), (0.2, (0, 0, 0)), (0.4, (17, 0, 0)), (0.6, (0, 0, 0)), (0.8, (-17, 0, 0))]),
        "front_right_leg": ("rotation", [(0.0, (-17, 0, 0)), (0.2, (0, 0, 0)), (0.4, (17, 0, 0)), (0.6, (0, 0, 0)), (0.8, (-17, 0, 0))]),
        "rear_left_leg": ("rotation", [(0.0, (18, 0, 0)), (0.2, (0, 0, 0)), (0.4, (-18, 0, 0)), (0.6, (0, 0, 0)), (0.8, (18, 0, 0))]),
        "tail": ("rotation", [(0.0, (0, 0, 0)), (0.4, (0, 0, 5)), (0.8, (0, 0, 0))]),
    }, loop="loop")
    add_animation_clip(model, "trot", 0.65, {
        "front_left_leg": ("rotation", [(0.0, (25, 0, 0)), (0.16, (0, 0, 0)), (0.32, (-25, 0, 0)), (0.49, (0, 0, 0)), (0.65, (25, 0, 0))]),
        "rear_right_leg": ("rotation", [(0.0, (25, 0, 0)), (0.16, (0, 0, 0)), (0.32, (-25, 0, 0)), (0.49, (0, 0, 0)), (0.65, (25, 0, 0))]),
        "front_right_leg": ("rotation", [(0.0, (-25, 0, 0)), (0.16, (0, 0, 0)), (0.32, (25, 0, 0)), (0.49, (0, 0, 0)), (0.65, (-25, 0, 0))]),
        "rear_left_leg": ("rotation", [(0.0, (-25, 0, 0)), (0.16, (0, 0, 0)), (0.32, (25, 0, 0)), (0.49, (0, 0, 0)), (0.65, (-25, 0, 0))]),
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.32, (-2, 0, 0)), (0.65, (0, 0, 0))]),
    }, loop="loop")
    add_animation_clip(model, "gallop", 0.55, {
        "front_left_leg": ("rotation", [(0.0, (45, 0, 0)), (0.14, (-12, 0, 0)), (0.27, (-38, 0, 0)), (0.41, (24, 0, 0)), (0.55, (45, 0, 0))]),
        "front_right_leg": ("rotation", [(0.0, (-35, 0, 0)), (0.14, (26, 0, 0)), (0.27, (38, 0, 0)), (0.41, (-14, 0, 0)), (0.55, (-35, 0, 0))]),
        "rear_left_leg": ("rotation", [(0.0, (-32, 0, 0)), (0.14, (30, 0, 0)), (0.27, (43, 0, 0)), (0.41, (-20, 0, 0)), (0.55, (-32, 0, 0))]),
        "rear_right_leg": ("rotation", [(0.0, (34, 0, 0)), (0.14, (-25, 0, 0)), (0.27, (-40, 0, 0)), (0.41, (18, 0, 0)), (0.55, (34, 0, 0))]),
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.27, (-4, 0, 0)), (0.55, (0, 0, 0))]),
        "head": ("rotation", [(0.0, (5, 0, 0)), (0.27, (-7, 0, 0)), (0.55, (5, 0, 0))]),
        "tail": ("rotation", [(0.0, (0, 0, 0)), (0.27, (0, 0, 16)), (0.55, (0, 0, 0))]),
    }, loop="loop")
    add_animation_clip(model, "charge", 0.75, {
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.25, (9, 0, 0)), (0.5, (-7, 0, 0)), (0.75, (0, 0, 0))]),
        "neck": ("rotation", [(0.0, (0, 0, 0)), (0.25, (-14, 0, 0)), (0.5, (11, 0, 0)), (0.75, (0, 0, 0))]),
        "front_left_leg": ("rotation", [(0.0, (0, 0, 0)), (0.25, (55, 0, 0)), (0.5, (-30, 0, 0)), (0.75, (0, 0, 0))]),
        "front_right_leg": ("rotation", [(0.0, (0, 0, 0)), (0.25, (55, 0, 0)), (0.5, (-30, 0, 0)), (0.75, (0, 0, 0))]),
    })
    add_animation_clip(model, "rear", 0.9, {
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.3, (-15, 0, 0)), (0.65, (12, 0, 0)), (0.9, (0, 0, 0))]),
        "neck": ("rotation", [(0.0, (0, 0, 0)), (0.35, (25, 0, 0)), (0.65, (12, 0, 0)), (0.9, (0, 0, 0))]),
        "front_left_leg": ("rotation", [(0.0, (0, 0, 0)), (0.35, (70, 0, -8)), (0.65, (55, 0, -5)), (0.9, (0, 0, 0))]),
        "front_right_leg": ("rotation", [(0.0, (0, 0, 0)), (0.35, (70, 0, 8)), (0.65, (55, 0, 5)), (0.9, (0, 0, 0))]),
    })
    add_animation_clip(model, "hit_reaction", 0.3, {
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.08, (0, 0, 8)), (0.3, (0, 0, 0))]),
        "head": ("rotation", [(0.0, (0, 0, 0)), (0.08, (-12, 0, -4)), (0.3, (0, 0, 0))]),
    })
    add_animation_clip(model, "death", 1.1, {
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.35, (0, 0, 16)), (1.1, (0, 0, 64))]),
        "body_position": ("position", [(0.0, (0, 0, 0)), (0.4, (0, -1.5, 0)), (1.1, (0, -7, 0))]),
        "head": ("rotation", [(0.0, (0, 0, 0)), (0.4, (6, 0, 0)), (1.1, (24, 0, 0))]),
        "front_left_leg": ("rotation", [(0.0, (0, 0, 0)), (0.4, (42, 0, 0)), (1.1, (60, 0, 0))]),
        "front_right_leg": ("rotation", [(0.0, (0, 0, 0)), (0.4, (42, 0, 0)), (1.1, (60, 0, 0))]),
    })
    add_animation_clip(model, "turn", 0.6, {
        "head": ("rotation", [(0.0, (0, 0, 0)), (0.2, (0, 18, 0)), (0.4, (0, -18, 0)), (0.6, (0, 0, 0))]),
        "neck": ("rotation", [(0.0, (0, 0, 0)), (0.2, (0, 10, 0)), (0.4, (0, -10, 0)), (0.6, (0, 0, 0))]),
    })

    # Remove the source humanoid clips and retain only the new horse-specific animation set.
    model["animations"] = [
        animation for animation in model["animations"]
        if not str(animation.get("name", "")).startswith("__template_")
    ]
    # If the source has any non-standard animation names, the allow-list above keeps the new clips only.
    horse_clip_names = {"idle", "walk", "trot", "gallop", "charge", "rear", "hit_reaction", "death", "turn"}
    model["animations"] = [animation for animation in model["animations"]
                           if animation.get("name") in horse_clip_names]

    (MODEL_DIR / "halloween_nick_steed.bbmodel").write_text(
        json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (MODEL_DIR / "halloween_nick_steed.png").write_bytes(encoded_png)
    print("Generated halloween_nick_steed.bbmodel and halloween_nick_steed.png with 11 steed bones and 9 animations")

def main() -> None:
    for definition in MODELS:
        create_model(*definition)

    # Nick's mount has its own equine skeleton and model; it is not the Nightmare's mesh.
    create_nick_steed_model()

    # The repository stores five editable templates and generates the other five in CI.
    # Enrich all ten source/generated models with unique UUIDs before validation/packaging.
    required = {
        "halloween_cursed_zombie", "halloween_void_reaper", "halloween_gravekeeper",
        "halloween_frost_stalker", "halloween_blood_spider", "halloween_pumpkin_wraith",
        "halloween_hex_witch", "halloween_nightmare", "halloween_goblin_king",
        "halloween_headless_nick",
    }
    for mob_id in sorted(required):
        model_path = MODEL_DIR / f"{mob_id}.bbmodel"
        if not model_path.is_file():
            raise RuntimeError(f"Required Halloween animation model missing: {model_path.name}")
        model = json.loads(model_path.read_text(encoding="utf-8"))
        before = len(model.get("animations", []))
        enrich_animation_set(model)
        if len(model.get("animations", [])) < before + 2:
            raise RuntimeError(f"Could not enrich {mob_id} with custom damage/death animations")
        model_path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Enriched {mob_id}: {len(model.get('animations', []))} animations including hit/death and encounter-specific clips")


if __name__ == "__main__":
    main()
