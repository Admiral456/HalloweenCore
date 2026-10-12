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

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "mythicmobs" / "models"

# New ID, template ID, hue shift, saturation multiplier, brightness multiplier, highlight hue.
MODELS = (
    ("halloween_void_reaper", "halloween_gravekeeper", 0.72, 1.30, 0.72, 0.78),
    ("halloween_frost_stalker", "halloween_gravekeeper", 0.52, 0.85, 1.18, 0.50),
    ("halloween_nightmare", "halloween_hex_witch", 0.94, 1.40, 0.78, 0.96),
    ("halloween_goblin_king", "halloween_gravekeeper", 0.27, 1.40, 0.88, 0.13),
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
    elif mob_id == "halloween_goblin_king":
        # A crown, heavy pauldron and exaggerated cleaver create a royal bruiser silhouette.
        for name, start, end, parent in (
            ("goblin_crown_band", (-5.5, 39.5, -2.0), (5.5, 42.5, 2.0), "head"),
            ("goblin_crown_spike_left", (-4.5, 42.0, -1.5), (-2.5, 47.0, 1.5), "head"),
            ("goblin_crown_spike_center", (-1.2, 42.0, -1.5), (1.2, 48.0, 1.5), "head"),
            ("goblin_crown_spike_right", (2.5, 42.0, -1.5), (4.5, 47.0, 1.5), "head"),
            ("goblin_heavy_pauldron", (-9.5, 31.0, -2.5), (-5.0, 36.0, 2.5), "body"),
            ("goblin_cleaver_blade", (10.0, 25.0, -2.0), (17.0, 32.0, 1.5), "weapon"),
            ("goblin_cleaver_tip", (14.0, 30.0, -1.5), (18.0, 36.0, 1.5), "weapon"),
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
    for bone_name, (channel, frames) in tracks.items():
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
        clip["animators"][bone["uuid"]] = {
            "name": bone_name,
            "type": "bone",
            "rotation_global": False,
            "quaternion_interpolation": False,
            "keyframes": keyframes,
        }
    model.setdefault("animations", []).append(clip)


def enrich_animation_set(model: dict) -> None:
    """Add authored damage/death and encounter-specific clips to all ten special models."""
    common_tracks = {
        "hit_reaction": {
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.08, (-12, 0, 6)), (0.22, (0, 0, 0))]),
        },
        "death": {
            "body": ("rotation", [(0.0, (0, 0, 0)), (0.3, (28, 0, 0)), (0.8, (72, 0, 0))]),
            "body_position": ("position", [(0.0, (0, 0, 0)), (0.3, (0, -2, 0)), (0.8, (0, -7, 0))]),
        },
    }
    add_animation_clip(model, "hit_reaction", 0.24, common_tracks["hit_reaction"])
    # Use a separate key for the position channel while still targeting the same body bone.
    add_animation_clip(model, "death", 0.82, {
        "body": ("rotation", [(0.0, (0, 0, 0)), (0.3, (28, 0, 0)), (0.82, (72, 0, 0))]),
        "body_lower": ("position", [(0.0, (0, 0, 0)), (0.3, (0, -2, 0)), (0.82, (0, -7, 0))]),
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
