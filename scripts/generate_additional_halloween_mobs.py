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
            ("left_wing", "goblin_wing_left_rib_inner", (-10.0, 25.0, 3.0), (-11.2, 37.0, 4.2), "wing_rib", (0, 0, 22)),
            ("left_wing", "goblin_wing_left_rib_middle", (-9.5, 25.0, 3.0), (-10.7, 35.0, 4.2), "wing_rib", (0, 0, 43)),
            ("left_wing", "goblin_wing_left_rib_outer", (-9.0, 25.0, 3.0), (-10.2, 32.0, 4.2), "wing_rib", (0, 0, 62)),
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
    """Add authored damage/death and encounter-specific clips to all ten special models."""
    add_animation_clip(model, "hit_reaction", 0.24, {
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.08, (-12, 0, 6)), (0.24, (0, 0, 0))]),
    })
    add_animation_clip(model, "death", 0.82, {
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.3, (28, 0, 0)), (0.82, (72, 0, 0))]),
        "body_position": ("position", [(0.0, (0, 0, 0)), (0.3, (0, -2, 0)), (0.82, (0, -7, 0))]),
    })
    mob_id = model.get("model_identifier", model.get("name", ""))
    if mob_id == "halloween_goblin_king":
        add_animation_clip(model, "heavy_attack", 0.95, {
            "right_arm": ("rotation", [(0.0, (0, 0, 0)), (0.25, (-78, 0, 0)),
                                       (0.48, (52, 0, 0)), (0.95, (0, 0, 0))]),
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.25, (0, 0, -8)),
                                 (0.5, (0, 0, 10)), (0.95, (0, 0, 0))]),
        })
        add_animation_clip(model, "phase_transition", 1.4, {
            "body_position": ("position", [(0.0, (0, 0, 0)), (0.3, (0, 1.5, 0)),
                                          (0.8, (0, -2, 0)), (1.4, (0, 0, 0))]),
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


def main() -> None:
    for definition in MODELS:
        create_model(*definition)

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
