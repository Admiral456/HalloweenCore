#!/usr/bin/env python3
"""Generate three extra editable Blockbench/ModelEngine mobs from existing Halloween model bases."""
from __future__ import annotations

import base64
import colorsys
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
    ("halloween_frost_stalker", "halloween_blood_spider", 0.52, 0.85, 1.18, 0.50),
    ("halloween_nightmare", "halloween_hex_witch", 0.94, 1.40, 0.78, 0.96),
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
