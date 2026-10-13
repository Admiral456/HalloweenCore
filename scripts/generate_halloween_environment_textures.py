#!/usr/bin/env python3
"""Generate sharp, deterministic pixel-art Halloween environment textures and models."""
from __future__ import annotations

import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "resourcepack" / "assets" / "minecraft"
BLOCKS = PACK / "textures" / "block"
ENVIRONMENT = PACK / "textures" / "environment"
BLOCK_MODELS = PACK / "models" / "block"
BLOCKSTATES = PACK / "blockstates"

INK = (19, 12, 24, 255)
SHADOW = (53, 20, 39, 255)
DARK_RED = (93, 22, 34, 255)
RED = (156, 36, 36, 255)
ORANGE_DARK = (177, 59, 22, 255)
ORANGE = (224, 96, 25, 255)
GOLD = (255, 157, 48, 255)
GOLD_LIGHT = (255, 203, 88, 255)
PURPLE = (94, 51, 129, 255)
PURPLE_LIGHT = (166, 100, 186, 255)
GREEN = (66, 105, 54, 255)
GREEN_LIGHT = (133, 154, 70, 255)
BONE = (216, 194, 155, 255)
CLEAR = (0, 0, 0, 0)


def pumpkin_face(style: str, lit: bool) -> Image.Image:
    im = Image.new("RGBA", (16, 16), CLEAR)
    d = ImageDraw.Draw(im)
    base = ORANGE if lit else ORANGE_DARK
    highlight = GOLD_LIGHT if lit else GOLD
    d.rectangle((0, 0, 15, 15), fill=DARK_RED, outline=INK)
    d.rectangle((1, 1, 14, 14), fill=base)
    # Vertical rib shadows retain the vanilla pumpkin silhouette, but sharpen the highlights.
    for x in (2, 6, 10, 13):
        d.line((x, 2, x, 13), fill=ORANGE_DARK if lit else RED, width=1)
    for x in (3, 8, 12):
        d.line((x, 2, x, 12), fill=highlight, width=1)
    if style == "classic":
        eyes = [(4, 4), (5, 4), (4, 5), (5, 5), (10, 4), (11, 4), (10, 5), (11, 5)]
        mouth = [(5, 10), (6, 11), (7, 11), (8, 11), (9, 11), (10, 10), (6, 12), (7, 12), (8, 12), (9, 12)]
        for x, y in eyes + mouth:
            d.point((x, y), fill=GOLD_LIGHT if lit else INK)
    elif style == "bat":
        for x, y in [(2, 5), (3, 4), (4, 5), (5, 6), (6, 6), (9, 6), (10, 6), (11, 5), (12, 4), (13, 5)]:
            d.point((x, y), fill=GOLD_LIGHT if lit else INK)
        d.rectangle((6, 9, 9, 10), fill=GOLD_LIGHT if lit else INK)
        d.point((5, 11), fill=GOLD_LIGHT if lit else INK)
        d.point((10, 11), fill=GOLD_LIGHT if lit else INK)
    elif style == "grin":
        d.rectangle((3, 4, 5, 6), fill=GOLD_LIGHT if lit else INK)
        d.rectangle((10, 4, 12, 6), fill=GOLD_LIGHT if lit else INK)
        d.rectangle((4, 9, 11, 12), fill=GOLD_LIGHT if lit else INK)
        for x in (6, 8, 10):
            d.point((x, 10), fill=INK if lit else ORANGE)
        d.point((7, 12), fill=INK if lit else ORANGE)
    else:  # cracked-skull
        d.line((3, 3, 5, 5, 4, 7), fill=GOLD_LIGHT if lit else INK, width=1)
        d.line((11, 3, 9, 5, 12, 7), fill=GOLD_LIGHT if lit else INK, width=1)
        d.rectangle((4, 5, 5, 7), fill=GOLD_LIGHT if lit else INK)
        d.rectangle((10, 5, 11, 7), fill=GOLD_LIGHT if lit else INK)
        d.polygon([(7, 8), (8, 8), (9, 10), (7, 10)], fill=GOLD_LIGHT if lit else INK)
        d.rectangle((5, 11, 10, 12), fill=GOLD_LIGHT if lit else INK)
        for x in (6, 8, 10):
            d.point((x, 12), fill=INK if lit else ORANGE)
    if lit:
        # Small square glints imply emissive eyes; the shader controls sky mood separately.
        for p in [(4, 4), (10, 4), (7, 10)]:
            d.point(p, fill=(255, 237, 149, 255))
    return im


def mushroom(kind: str) -> Image.Image:
    im = Image.new("RGBA", (16, 16), CLEAR)
    d = ImageDraw.Draw(im)
    if kind == "red":
        outline, base, spots, stem = INK, PURPLE, PURPLE_LIGHT, BONE
    else:
        outline, base, spots, stem = INK, ORANGE_DARK, GOLD_LIGHT, GREEN_LIGHT
    # Compact 16x16 silhouette with deliberate one-pixel clusters, no antialiasing.
    d.rectangle((7, 8, 9, 14), fill=outline)
    d.rectangle((8, 9, 9, 13), fill=stem)
    d.rectangle((4, 5, 11, 8), fill=outline)
    d.rectangle((2, 6, 13, 7), fill=outline)
    d.rectangle((3, 5, 12, 6), fill=base)
    d.rectangle((5, 4, 10, 4), fill=base)
    d.rectangle((4, 5, 7, 5), fill=spots)
    d.rectangle((10, 6, 11, 6), fill=spots)
    d.point((7, 6), fill=GOLD_LIGHT)
    d.point((6, 7), fill=PURPLE_LIGHT if kind == "red" else GREEN)
    return im


def moon_phase(phase: int) -> Image.Image:
    im = Image.new("RGBA", (32, 32), CLEAR)
    px = im.load()
    cx = cy = 15.5
    # Integer-grid radius yields crisp, non-smoothed edges.
    for y in range(32):
        for x in range(32):
            dx, dy = x - cx, y - cy
            radius = (dx * dx + dy * dy) ** 0.5
            if radius > 12.8:
                continue
            # Match the eight-cell Minecraft cycle: full -> waning gibbous ->
            # third quarter -> waning crescent -> new -> waxing crescent ->
            # first quarter -> waxing gibbous. Keep the terminator on the right
            # for waxing phases and on the left for waning phases.
            if phase == 0:
                lit_side = True
            elif phase == 1:
                lit_side = dx <= 8.0
            elif phase == 2:
                lit_side = dx <= 0.0
            elif phase == 3:
                lit_side = dx <= -8.0
            elif phase == 4:
                lit_side = False
            elif phase == 5:
                lit_side = dx >= 8.0
            elif phase == 6:
                lit_side = dx >= 0.0
            else:
                lit_side = dx >= -8.0
            if lit_side:
                # Neutral ash-silver moon phases stay readable in ordinary nights.
                # The separate Iris shader applies crimson only while Blood Moon is active.
                if radius > 10.2:
                    color = (87, 96, 111, 255)
                elif (x + y) % 7 == 0:
                    color = (229, 224, 207, 255)
                elif (x * 3 + y * 5) % 11 < 2:
                    color = (163, 173, 186, 255)
                else:
                    color = (190, 194, 199, 255)
            else:
                color = (49, 54, 65, 255) if radius < 11.7 else INK
            # Pixel-art craters with darker rims and one-pixel highlights.
            crater_specs = [(9, 9, 2), (21, 8, 2), (18, 19, 3), (8, 22, 1), (24, 18, 1)]
            for mx, my, cr in crater_specs:
                dist = ((x - mx) ** 2 + (y - my) ** 2) ** 0.5
                if dist <= cr and radius <= 12.8:
                    color = (68, 74, 87, 255) if lit_side else (32, 35, 45, 255)
                if cr < dist <= cr + 1 and lit_side:
                    color = (143, 152, 164, 255)
            px[x, y] = color
    return im


def generate_moon_atlas() -> None:
    atlas = Image.new("RGBA", (128, 64), CLEAR)
    for phase in range(8):
        tile = moon_phase(phase)
        atlas.alpha_composite(tile, ((phase % 4) * 32, (phase // 4) * 32))
    ENVIRONMENT.mkdir(parents=True, exist_ok=True)
    atlas.save(ENVIRONMENT / "moon_phases.png", optimize=True)
    if atlas.size != (128, 64):
        raise RuntimeError("Moon phase atlas must be 128x64")


def save_model(name: str, face_texture: str) -> None:
    model = {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": "minecraft:block/pumpkin_side",
            "down": "minecraft:block/pumpkin_top",
            "up": "minecraft:block/pumpkin_top",
            "north": f"minecraft:block/{face_texture}",
            "south": "minecraft:block/pumpkin_side",
            "west": "minecraft:block/pumpkin_side",
            "east": "minecraft:block/pumpkin_side"
        }
    }
    BLOCK_MODELS.mkdir(parents=True, exist_ok=True)
    (BLOCK_MODELS / f"{name}.json").write_text(
        json.dumps(model, separators=(",", ":"), ensure_ascii=False) + "\n", encoding="utf-8"
    )


def save_blockstate(name: str, variants: list[str]) -> None:
    facing_names = ("north", "east", "south", "west")
    rotations = (0, 90, 180, 270)
    data = {"variants": {
        f"facing={facing}": {"model": f"minecraft:block/{model}", "y": rotations[index]}
        for index, (facing, model) in enumerate(zip(facing_names, variants))
    }}
    BLOCKSTATES.mkdir(parents=True, exist_ok=True)
    (BLOCKSTATES / f"{name}.json").write_text(
        json.dumps(data, separators=(",", ":"), ensure_ascii=False) + "\n", encoding="utf-8"
    )


def generate_seasonal_colormaps() -> None:
    """Apply a restrained autumnal palette across biome-tinted grass, foliage, and water."""
    colormap_root = PACK / "textures" / "colormap"
    colormap_root.mkdir(parents=True, exist_ok=True)

    grass = Image.new("RGB", (256, 256))
    foliage = Image.new("RGB", (256, 256))
    water = Image.new("RGB", (256, 256))
    gpx, fpx, wpx = grass.load(), foliage.load(), water.load()

    for y in range(256):
        humidity = 1.0 - y / 255.0
        for x in range(256):
            temperature = x / 255.0
            warm = temperature ** 1.25
            damp = humidity ** 0.75

            # Temperate biomes remain mossy olive; hot/dry biomes move toward
            # burnt sienna and muted amber instead of bright neon orange.
            gpx[x, y] = (
                min(255, int(72 + 58 * warm + 12 * damp)),
                min(255, int(72 + 29 * damp - 8 * warm)),
                min(255, int(43 + 13 * damp + 4 * (1.0 - warm))),
            )
            fpx[x, y] = (
                min(255, int(43 + 96 * warm + 10 * damp)),
                min(255, int(59 + 29 * damp - 15 * warm)),
                min(255, int(31 + 12 * damp + 4 * (1.0 - warm))),
            )
            wpx[x, y] = (
                min(255, int(33 + 24 * warm)),
                min(255, int(74 + 18 * damp)),
                min(255, int(78 + 20 * damp - 5 * warm)),
            )

    grass.save(colormap_root / "grass.png", optimize=True)
    foliage.save(colormap_root / "foliage.png", optimize=True)
    water.save(colormap_root / "water.png", optimize=True)
    print("Generated 3 seasonal 256x256 biome colormaps (grass, foliage, water).")


def main() -> None:
    BLOCKS.mkdir(parents=True, exist_ok=True)
    for style in ("classic", "bat", "grin", "skull"):
        pumpkin_face(style, lit=False).save(BLOCKS / f"pumpkin_face_{style}.png", optimize=True)
        pumpkin_face(style, lit=True).save(BLOCKS / f"jack_o_lantern_{style}.png", optimize=True)

    # Keep default texture IDs for compatibility with the existing resource pack.
    # Direction variants select different faces while preserving vanilla collision and states.
    pumpkin_models = []
    carved_models = []
    jack_models = []
    for index, style in enumerate(("classic", "bat", "grin", "skull")):
        a = f"pumpkin_halloween_{index}"
        b = f"carved_pumpkin_halloween_{index}"
        d = f"jack_o_lantern_halloween_{index}"
        save_model(a, f"pumpkin_face_{style}")
        save_model(b, f"pumpkin_face_{style}")
        save_model(d, f"jack_o_lantern_{style}")
        pumpkin_models.append(a)
        carved_models.append(b)
        jack_models.append(d)
    save_blockstate("pumpkin", pumpkin_models)
    save_blockstate("carved_pumpkin", carved_models)
    save_blockstate("jack_o_lantern", jack_models)

    mushroom("red").save(BLOCKS / "red_mushroom.png", optimize=True)
    mushroom("brown").save(BLOCKS / "brown_mushroom.png", optimize=True)
    generate_moon_atlas()
    generate_seasonal_colormaps()

    print("Generated 8 pumpkin-face variants, 12 directional block models, 3 blockstates, 2 haunted mushroom textures, a 128x64 moon phase atlas, and 3 seasonal colormaps.")
    for path in sorted([*BLOCKS.glob("*_classic.png"), *BLOCKS.glob("red_mushroom.png"), *BLOCKS.glob("brown_mushroom.png"), ENVIRONMENT / "moon_phases.png"]):
        print(f"Generated {path.relative_to(ROOT)} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
