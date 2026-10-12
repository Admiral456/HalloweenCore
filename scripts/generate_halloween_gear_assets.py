#!/usr/bin/env python3
"""Build crisp, hand-authored 32x32 pixel-art icons for HalloweenCore gear.

The art is drawn on the native pixel grid (no blur, gradients or resampling) and
written directly to the ItemsAdder resource-pack source directory. The script
is deterministic, uses Pillow already installed by the CI workflow, and does not
modify the 64x32 armor-layer atlas supplied with the current release.
"""
from __future__ import annotations

from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "item"
SIZE = 32

# Deliberately small, high-contrast material palette for a sharp pixel-art look.
INK = (12, 10, 17, 255)
SHADOW = (25, 22, 31, 255)
IRON_DARK = (48, 49, 62, 255)
IRON = (99, 104, 119, 255)
STEEL = (157, 164, 177, 255)
HIGHLIGHT = (225, 229, 233, 255)
CRIMSON_DARK = (76, 8, 26, 255)
CRIMSON = (142, 16, 43, 255)
RUBY = (205, 28, 53, 255)
EMBER = (255, 81, 43, 255)
GOLD_DARK = (111, 64, 23, 255)
GOLD = (207, 143, 48, 255)
GOLD_LIGHT = (255, 215, 111, 255)
PURPLE_DARK = (48, 25, 77, 255)
PURPLE = (122, 67, 182, 255)
LILAC = (201, 146, 255, 255)
BONE = (220, 211, 187, 255)
PARCHMENT = (203, 166, 105, 255)
PARCHMENT_LIGHT = (242, 213, 156, 255)
GREEN = (60, 117, 76, 255)
CYAN = (79, 213, 222, 255)
CLEAR = (0, 0, 0, 0)


def canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    return image, ImageDraw.Draw(image)


def px(draw: ImageDraw.ImageDraw, x: int, y: int, color: tuple[int, int, int, int]) -> None:
    draw.point((x, y), fill=color)


def save(name: str, image: Image.Image) -> None:
    if image.size != (SIZE, SIZE):
        raise ValueError(f"{name}: expected {SIZE}x{SIZE}, got {image.size}")
    image.save(OUT / name, format="PNG", optimize=True, compress_level=9)


def add_edge_glints(draw: ImageDraw.ImageDraw, coords: list[tuple[int, int]]) -> None:
    for x, y in coords:
        px(draw, x, y, HIGHLIGHT)


def hunter_mask() -> Image.Image:
    image, d = canvas()
    d.polygon([(4, 11), (5, 6), (10, 8), (12, 3), (16, 7), (20, 3), (22, 8), (27, 6), (28, 13),
               (25, 23), (21, 27), (11, 27), (7, 23)], fill=INK, outline=CRIMSON_DARK)
    d.polygon([(6, 12), (9, 8), (12, 12), (16, 9), (20, 12), (23, 8), (26, 12),
               (23, 21), (19, 25), (12, 25), (8, 21)], fill=IRON_DARK, outline=IRON)
    d.rectangle((10, 14, 14, 17), fill=CRIMSON_DARK)
    d.rectangle((18, 14, 22, 17), fill=CRIMSON_DARK)
    d.rectangle((11, 14, 13, 16), fill=EMBER)
    d.rectangle((19, 14, 21, 16), fill=EMBER)
    d.polygon([(14, 18), (18, 18), (16, 22)], fill=INK)
    d.line([(12, 23), (14, 22), (16, 23), (18, 22), (20, 23)], fill=BONE, width=1)
    d.polygon([(4, 11), (1, 6), (7, 8)], fill=IRON_DARK, outline=STEEL)
    d.polygon([(28, 11), (31, 6), (25, 8)], fill=IRON_DARK, outline=STEEL)
    d.line([(7, 12), (9, 10), (10, 12)], fill=STEEL)
    d.line([(22, 12), (23, 10), (25, 12)], fill=STEEL)
    add_edge_glints(d, [(8, 18), (9, 20), (11, 23), (22, 20), (23, 18), (16, 25)])
    return image


def necklace() -> Image.Image:
    image, d = canvas()
    # Broken oval chain: each link remains a discrete, readable pixel cluster.
    for x, y in [(8, 5), (11, 4), (14, 4), (17, 4), (20, 5), (23, 7), (25, 10),
                 (25, 13), (23, 16), (21, 18), (18, 19), (15, 19), (12, 18), (10, 16)]:
        d.rectangle((x, y, x + 1, y + 1), fill=GOLD_DARK)
        px(d, x, y, GOLD_LIGHT)
    d.line([(8, 7), (8, 11), (9, 14)], fill=GOLD, width=2)
    d.line([(24, 8), (27, 12), (25, 16)], fill=GOLD, width=2)
    d.polygon([(16, 13), (22, 18), (20, 25), (16, 29), (12, 25), (10, 18)],
              fill=INK, outline=GOLD_LIGHT)
    d.polygon([(16, 15), (20, 19), (18, 24), (16, 26), (13, 23), (12, 19)],
              fill=PURPLE_DARK, outline=PURPLE)
    d.polygon([(16, 16), (19, 19), (16, 23), (14, 20)], fill=PURPLE)
    px(d, 15, 18, LILAC)
    px(d, 16, 19, HIGHLIGHT)
    d.line([(14, 24), (16, 27), (18, 24)], fill=RUBY, width=1)
    d.rectangle((15, 11, 17, 13), fill=GOLD)
    return image


def halloween_token() -> Image.Image:
    image, d = canvas()
    d.ellipse((3, 3, 28, 28), fill=INK, outline=GOLD_DARK, width=2)
    d.ellipse((5, 5, 26, 26), fill=CRIMSON_DARK, outline=GOLD)
    d.ellipse((8, 8, 23, 23), fill=SHADOW, outline=EMBER)
    d.polygon([(16, 8), (19, 13), (24, 14), (20, 18), (21, 23), (16, 20), (11, 23), (12, 18),
               (8, 14), (13, 13)], fill=GOLD, outline=GOLD_LIGHT)
    d.rectangle((14, 12, 18, 19), fill=CRIMSON)
    d.rectangle((13, 14, 19, 16), fill=RUBY)
    add_edge_glints(d, [(9, 9), (10, 7), (22, 22), (23, 20)])
    return image


def cursed_candy() -> Image.Image:
    image, d = canvas()
    d.polygon([(2, 11), (7, 14), (7, 18), (2, 21), (5, 16)], fill=GOLD_DARK, outline=INK)
    d.polygon([(30, 11), (25, 14), (25, 18), (30, 21), (27, 16)], fill=GOLD, outline=INK)
    d.polygon([(5, 11), (25, 11), (28, 16), (25, 21), (5, 21), (2, 16)], fill=CRIMSON_DARK, outline=INK)
    d.polygon([(7, 12), (24, 12), (26, 16), (23, 19), (7, 19), (5, 16)], fill=RUBY)
    d.line([(8, 13), (12, 16), (8, 19)], fill=GOLD_LIGHT, width=2)
    d.line([(24, 13), (20, 16), (24, 19)], fill=GOLD_LIGHT, width=2)
    d.polygon([(16, 12), (19, 16), (16, 20), (13, 16)], fill=PURPLE_DARK, outline=LILAC)
    px(d, 16, 15, HIGHLIGHT)
    d.line([(11, 10), (14, 8), (16, 10)], fill=EMBER, width=1)
    return image


def haunted_map() -> Image.Image:
    image, d = canvas()
    d.polygon([(5, 3), (14, 5), (23, 2), (27, 5), (27, 26), (22, 28), (14, 25), (5, 28)],
              fill=PARCHMENT, outline=INK)
    d.polygon([(7, 5), (14, 7), (14, 23), (7, 25)], fill=PARCHMENT_LIGHT)
    d.polygon([(16, 7), (22, 4), (25, 6), (25, 24), (22, 25), (16, 23)], fill=(163, 121, 70, 255))
    d.line([(8, 8), (12, 10), (10, 13), (13, 15), (10, 19), (13, 22)], fill=CRIMSON_DARK, width=2)
    d.line([(16, 10), (20, 12), (18, 16), (22, 20)], fill=PURPLE_DARK, width=1)
    d.polygon([(21, 15), (25, 18), (21, 23), (18, 18)], fill=CRIMSON, outline=INK)
    d.rectangle((20, 18, 22, 20), fill=EMBER)
    d.line([(6, 3), (6, 1), (11, 2)], fill=GOLD_LIGHT, width=1)
    return image


def armor_helmet() -> Image.Image:
    image, d = canvas()
    d.polygon([(5, 12), (6, 7), (10, 4), (14, 5), (16, 2), (18, 5), (22, 4), (26, 7),
               (27, 12), (24, 23), (20, 26), (12, 26), (8, 23)], fill=INK, outline=CRIMSON)
    d.polygon([(7, 12), (8, 8), (12, 7), (16, 9), (20, 7), (24, 8), (25, 12),
               (22, 20), (19, 23), (13, 23), (10, 20)], fill=IRON_DARK, outline=STEEL)
    d.polygon([(8, 12), (13, 11), (16, 14), (19, 11), (24, 12), (22, 16), (18, 17), (14, 17), (10, 16)],
              fill=SHADOW, outline=CRIMSON_DARK)
    d.rectangle((10, 13, 14, 15), fill=EMBER)
    d.rectangle((18, 13, 22, 15), fill=EMBER)
    d.rectangle((11, 13, 12, 14), fill=GOLD_LIGHT)
    d.rectangle((19, 13, 20, 14), fill=GOLD_LIGHT)
    d.polygon([(15, 17), (17, 17), (18, 21), (14, 21)], fill=CRIMSON)
    d.line([(9, 19), (11, 22), (13, 23)], fill=STEEL)
    d.line([(23, 19), (21, 22), (19, 23)], fill=STEEL)
    d.polygon([(5, 12), (1, 5), (8, 8)], fill=IRON_DARK, outline=STEEL)
    d.polygon([(27, 12), (31, 5), (24, 8)], fill=IRON_DARK, outline=STEEL)
    return image


def armor_chest() -> Image.Image:
    image, d = canvas()
    d.polygon([(8, 4), (13, 3), (16, 7), (19, 3), (24, 4), (28, 11), (24, 15),
               (25, 26), (20, 29), (12, 29), (7, 26), (8, 15), (4, 11)],
              fill=INK, outline=CRIMSON)
    d.polygon([(9, 6), (13, 5), (16, 9), (19, 5), (23, 6), (25, 11), (21, 14),
               (22, 24), (19, 27), (13, 27), (10, 24), (11, 14), (7, 11)],
              fill=IRON_DARK, outline=STEEL)
    d.polygon([(13, 7), (16, 11), (19, 7), (18, 16), (16, 20), (14, 16)], fill=CRIMSON, outline=RUBY)
    d.line([(10, 9), (12, 13), (11, 20)], fill=STEEL, width=1)
    d.line([(22, 9), (20, 13), (21, 20)], fill=STEEL, width=1)
    d.rectangle((14, 22, 18, 24), fill=CRIMSON_DARK)
    d.rectangle((15, 22, 17, 23), fill=EMBER)
    for y in (16, 19, 25):
        px(d, 12, y, RUBY)
        px(d, 20, y, RUBY)
    return image


def armor_leggings() -> Image.Image:
    image, d = canvas()
    d.polygon([(6, 4), (26, 4), (25, 10), (23, 28), (17, 29), (16, 15),
               (15, 29), (9, 28), (7, 10)], fill=INK, outline=CRIMSON)
    d.polygon([(8, 6), (14, 6), (15, 15), (13, 26), (10, 26), (9, 10)], fill=IRON_DARK, outline=STEEL)
    d.polygon([(18, 6), (24, 6), (23, 10), (22, 26), (19, 26), (17, 15)], fill=IRON_DARK, outline=STEEL)
    d.rectangle((8, 5, 24, 8), fill=CRIMSON_DARK, outline=GOLD_DARK)
    d.line([(11, 10), (12, 14), (11, 19)], fill=CRIMSON, width=2)
    d.line([(21, 10), (20, 14), (21, 19)], fill=CRIMSON, width=2)
    d.polygon([(16, 8), (18, 12), (16, 15), (14, 12)], fill=RUBY)
    px(d, 16, 11, GOLD_LIGHT)
    d.line([(10, 23), (12, 25)], fill=STEEL)
    d.line([(20, 23), (22, 25)], fill=STEEL)
    return image


def armor_boots() -> Image.Image:
    image, d = canvas()
    d.polygon([(5, 3), (13, 3), (13, 17), (15, 20), (15, 26), (4, 27), (2, 24),
               (7, 20), (7, 12)], fill=INK, outline=CRIMSON)
    d.polygon([(19, 3), (27, 3), (25, 12), (25, 20), (30, 24), (28, 27), (17, 26),
               (17, 20), (19, 17)], fill=INK, outline=CRIMSON)
    d.polygon([(7, 5), (11, 5), (11, 17), (13, 21), (12, 24), (5, 24), (7, 20)],
              fill=IRON_DARK, outline=STEEL)
    d.polygon([(21, 5), (25, 5), (23, 20), (25, 24), (19, 24), (19, 21), (21, 17)],
              fill=IRON_DARK, outline=STEEL)
    d.rectangle((5, 20, 12, 23), fill=CRIMSON_DARK)
    d.rectangle((20, 20, 27, 23), fill=CRIMSON_DARK)
    d.line([(8, 9), (10, 12), (8, 15)], fill=RUBY)
    d.line([(24, 9), (22, 12), (24, 15)], fill=RUBY)
    add_edge_glints(d, [(7, 6), (22, 6), (10, 25), (22, 25)])
    return image


def sword() -> Image.Image:
    image, d = canvas()
    d.polygon([(5, 24), (9, 28), (25, 8), (29, 2), (23, 5)], fill=INK, outline=INK)
    d.polygon([(7, 23), (10, 25), (25, 6), (27, 3), (23, 6)], fill=IRON, outline=STEEL)
    d.polygon([(9, 22), (11, 23), (25, 6), (26, 4)], fill=HIGHLIGHT)
    d.line([(13, 18), (16, 17), (18, 14)], fill=CRIMSON, width=2)
    d.line([(11, 20), (14, 19)], fill=EMBER, width=1)
    d.polygon([(6, 20), (12, 24), (14, 21), (9, 17)], fill=CRIMSON_DARK, outline=GOLD_DARK)
    d.line([(8, 20), (11, 22)], fill=GOLD)
    d.line([(6, 26), (12, 20)], fill=IRON_DARK, width=3)
    d.line([(5, 25), (9, 29)], fill=GOLD_DARK, width=4)
    d.line([(4, 28), (7, 31)], fill=INK, width=3)
    d.rectangle((4, 27, 6, 29), fill=GOLD)
    px(d, 25, 5, RUBY)
    return image


def pickaxe() -> Image.Image:
    image, d = canvas()
    d.line([(9, 24), (22, 11)], fill=INK, width=6)
    d.line([(10, 23), (22, 11)], fill=GOLD_DARK, width=3)
    d.line([(6, 27), (12, 21)], fill=IRON_DARK, width=3)
    d.polygon([(3, 10), (8, 6), (15, 7), (19, 4), (28, 7), (29, 11),
               (23, 12), (18, 10), (14, 14), (9, 12)], fill=INK, outline=CRIMSON)
    d.polygon([(5, 9), (9, 7), (15, 8), (19, 5), (26, 7), (27, 9), (22, 10),
               (18, 8), (14, 12), (9, 10)], fill=IRON, outline=STEEL)
    d.line([(8, 8), (14, 9), (18, 6), (24, 8)], fill=HIGHLIGHT, width=1)
    d.polygon([(16, 9), (20, 10), (17, 13), (14, 12)], fill=CRIMSON, outline=EMBER)
    d.line([(12, 21), (16, 17), (18, 15)], fill=CRIMSON, width=2)
    return image


def axe() -> Image.Image:
    image, d = canvas()
    d.line([(9, 25), (21, 12)], fill=INK, width=6)
    d.line([(10, 24), (21, 12)], fill=GOLD_DARK, width=3)
    d.polygon([(12, 13), (14, 7), (21, 3), (29, 4), (30, 12), (25, 18),
               (19, 17), (16, 13)], fill=INK, outline=CRIMSON)
    d.polygon([(15, 11), (17, 7), (22, 5), (27, 6), (28, 11), (24, 15),
               (20, 14), (17, 12)], fill=IRON, outline=STEEL)
    d.line([(18, 8), (22, 6), (26, 7)], fill=HIGHLIGHT)
    d.polygon([(17, 12), (21, 10), (23, 13), (20, 15)], fill=RUBY)
    d.line([(12, 20), (15, 17), (18, 15)], fill=CRIMSON, width=2)
    return image


def shovel() -> Image.Image:
    image, d = canvas()
    d.line([(8, 27), (23, 10)], fill=INK, width=6)
    d.line([(9, 26), (23, 10)], fill=GOLD_DARK, width=3)
    d.polygon([(18, 3), (24, 4), (29, 10), (26, 16), (21, 17), (17, 12), (16, 8)],
              fill=INK, outline=CRIMSON)
    d.polygon([(20, 5), (24, 6), (27, 10), (25, 14), (22, 14), (19, 11), (18, 8)],
              fill=IRON, outline=STEEL)
    d.polygon([(21, 7), (24, 8), (25, 11), (23, 12), (21, 10)], fill=CRIMSON)
    d.line([(22, 8), (24, 10)], fill=EMBER)
    d.line([(10, 24), (13, 21), (15, 19)], fill=CRIMSON, width=2)
    return image


def hoe() -> Image.Image:
    image, d = canvas()
    d.line([(7, 27), (22, 12)], fill=INK, width=6)
    d.line([(8, 26), (22, 12)], fill=GOLD_DARK, width=3)
    d.polygon([(6, 5), (12, 3), (16, 6), (19, 10), (16, 14), (12, 11), (10, 8)],
              fill=INK, outline=CRIMSON)
    d.polygon([(8, 5), (12, 4), (15, 7), (16, 10), (14, 11), (11, 8)],
              fill=IRON, outline=STEEL)
    d.line([(9, 5), (12, 5), (14, 7)], fill=HIGHLIGHT)
    d.polygon([(12, 10), (16, 11), (19, 13), (16, 15), (13, 13)], fill=CRIMSON, outline=EMBER)
    d.line([(11, 22), (14, 19), (17, 16)], fill=RUBY, width=2)
    return image


def shield() -> Image.Image:
    image, d = canvas()
    d.polygon([(5, 4), (13, 3), (16, 5), (19, 3), (27, 4), (28, 17), (23, 25),
               (16, 30), (9, 25), (4, 17)], fill=INK, outline=GOLD_DARK)
    d.polygon([(7, 6), (14, 5), (16, 7), (18, 5), (25, 6), (25, 17), (21, 23),
               (16, 27), (11, 23), (7, 17)], fill=CRIMSON_DARK, outline=RUBY)
    d.polygon([(10, 8), (15, 8), (16, 10), (18, 8), (22, 9), (22, 17), (19, 21),
               (16, 24), (12, 21), (10, 17)], fill=IRON_DARK, outline=STEEL)
    d.polygon([(16, 9), (19, 14), (16, 21), (13, 14)], fill=CRIMSON, outline=EMBER)
    d.line([(16, 11), (16, 20)], fill=GOLD_LIGHT, width=2)
    d.line([(12, 14), (20, 14)], fill=GOLD, width=2)
    d.rectangle((14, 13, 18, 16), fill=RUBY)
    px(d, 16, 14, HIGHLIGHT)
    d.line([(8, 8), (8, 16), (11, 21)], fill=GOLD, width=1)
    d.line([(24, 8), (24, 16), (21, 21)], fill=CRIMSON, width=1)
    return image



MODEL_OUT = ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "models" / "item"
ARMOR_MODEL_OUT = ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "models" / "armor"
MODEL_TEXTURE_IDS = {
    "crimson_warden_sword": "item/crimson_warden_sword",
    "crimson_warden_pickaxe": "item/crimson_warden_pickaxe",
    "crimson_warden_axe": "item/crimson_warden_axe",
    "crimson_warden_shovel": "item/crimson_warden_shovel",
    "crimson_warden_hoe": "item/crimson_warden_hoe",
    "crimson_warden_shield": "item/crimson_warden_shield",
    "cursed_talisman": "item/cursed_talisman",
}


def voxel_elements(image: Image.Image, depth: tuple[float, float]) -> list[dict]:
    """Extrude opaque same-colour pixel rectangles into small, UV-mapped cubes.

    Every source pixel remains crisp. Greedy rectangle merging keeps the model
    compact while preserving the original silhouette and face colours.
    """
    image = image.convert("RGBA")
    width, height = image.size
    if width != SIZE or height != SIZE:
        raise ValueError(f"3D model source must be {SIZE}x{SIZE}, got {width}x{height}")

    pixels = image.load()
    used = [[False for _ in range(width)] for _ in range(height)]
    elements: list[dict] = []

    for y in range(height):
        for x in range(width):
            color = pixels[x, y]
            if used[y][x] or color[3] < 128:
                used[y][x] = True
                continue

            # Greedily form a solid rectangle of this exact palette colour.
            rect_width = 1
            while (x + rect_width < width and not used[y][x + rect_width]
                   and pixels[x + rect_width, y] == color):
                rect_width += 1

            rect_height = 1
            while y + rect_height < height:
                row_ok = True
                for xx in range(x, x + rect_width):
                    if used[y + rect_height][xx] or pixels[xx, y + rect_height] != color:
                        row_ok = False
                        break
                if not row_ok:
                    break
                rect_height += 1

            for yy in range(y, y + rect_height):
                for xx in range(x, x + rect_width):
                    used[yy][xx] = True

            # Minecraft model UVs use a 0..16 coordinate space; this atlas is
            # 32x32 so every art pixel corresponds to 0.5 UV units and 0.5 blocks.
            u0, v0 = x / 2, y / 2
            u1, v1 = (x + rect_width) / 2, (y + rect_height) / 2
            left_u1 = (x + 1) / 2
            right_u0 = (x + rect_width - 1) / 2
            top_v1 = (y + 1) / 2
            bottom_v0 = (y + rect_height - 1) / 2

            def uv(values: tuple[float, float, float, float]) -> list[float]:
                return [round(value, 3) for value in values]

            def face(region: tuple[float, float, float, float]) -> dict:
                return {"uv": uv(region), "texture": "#layer0"}

            model_y0 = (height - y - rect_height) / 2
            model_y1 = (height - y) / 2
            elements.append({
                "from": [round(x / 2, 3), round(model_y0, 3), depth[0]],
                "to": [round((x + rect_width) / 2, 3), round(model_y1, 3), depth[1]],
                "faces": {
                    "north": face((u0, v0, u1, v1)),
                    "south": face((u0, v0, u1, v1)),
                    "west": face((u0, v0, left_u1, v1)),
                    "east": face((right_u0, v0, u1, v1)),
                    "up": face((u0, v0, u1, top_v1)),
                    "down": face((u0, bottom_v0, u1, v1)),
                },
            })

    if not elements:
        raise ValueError("3D icon contained no visible pixels")
    # Hundreds of tiny cubes are unnecessary if the source art is accidentally
    # changed to a dense photograph; fail before shipping an oversized model.
    if len(elements) > 700:
        raise ValueError(f"model has {len(elements)} elements; simplify the pixel art")
    return elements


def save_voxel_model(item_id: str, parent: str, depth: tuple[float, float]) -> int:
    """Create a Minecraft item JSON model from one of the generated pixel icons."""
    source = OUT / f"{item_id}.png"
    if not source.is_file():
        raise FileNotFoundError(f"Missing model texture {source}")
    with Image.open(source) as image:
        elements = voxel_elements(image, depth)

    model = {
        "credit": "HalloweenCore - generated crisp pixel-art voxel model",
        "parent": parent,
        "textures": {
            "layer0": f"warriorland_halloween:item/{item_id}",
            "particle": f"warriorland_halloween:item/{item_id}",
        },
        "elements": elements,
    }
    MODEL_OUT.mkdir(parents=True, exist_ok=True)
    destination = MODEL_OUT / f"{item_id}.json"
    destination.write_text(json.dumps(model, separators=(",", ":"), ensure_ascii=False) + "\n", encoding="utf-8")
    # Read it back instead of trusting a partially written JSON file.
    parsed = json.loads(destination.read_text(encoding="utf-8"))
    if parsed.get("textures", {}).get("layer0") != model["textures"]["layer0"]:
        raise RuntimeError(f"Texture reference check failed for {item_id}")
    print(f"Generated 3D model {destination.name}: {len(elements)} UV-mapped voxel elements")
    return len(elements)


def generate_voxel_models() -> None:
    """Create custom 3D in-hand geometry for combat gear and the necklace."""
    for item_id in (
        "crimson_warden_sword",
        "crimson_warden_pickaxe",
        "crimson_warden_axe",
        "crimson_warden_shovel",
        "crimson_warden_hoe",
    ):
        save_voxel_model(item_id, "item/handheld", (7.1, 8.9))

    # The shield has distinct normal and actively-blocking states as supported by
    # ItemsAdder's modern 1.21.4+ graphics.models property.
    save_voxel_model("crimson_warden_shield", "item/generated", (7.0, 9.0))
    blocked_model = MODEL_OUT / "crimson_warden_shield_blocking.json"
    standard_model = MODEL_OUT / "crimson_warden_shield.json"
    if not standard_model.is_file():
        raise FileNotFoundError("Normal shield model was not written")
    # Keep separate state model files even though their texture source is shared;
    # the blocking state uses a heavier/deeper extrusion so the silhouette remains bold.
    with Image.open(OUT / "crimson_warden_shield.png") as image:
        blocking = {
            "credit": "HalloweenCore - generated blocking-state voxel shield",
            "parent": "item/generated",
            "textures": {
                "layer0": "warriorland_halloween:item/crimson_warden_shield",
                "particle": "warriorland_halloween:item/crimson_warden_shield",
            },
            "elements": voxel_elements(image, (6.2, 9.8)),
        }
    blocked_model.write_text(json.dumps(blocking, separators=(",", ":") , ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Generated blocking shield model: {len(blocking['elements'])} UV-mapped voxel elements")

    save_voxel_model("cursed_talisman", "item/generated", (7.25, 8.75))

    # ItemsAdder 4.x permits an actual 3D head model only for the helmet slot.
    # This separate asset is used by the worn helmet model; the equipment set ID
    # must intentionally be omitted from that one item so its model_path is honored.
    with Image.open(OUT / "crimson_warden_helmet.png") as image:
        helmet = {
            "credit": "HalloweenCore - Crimson Warden wearable 3D helmet",
            "parent": "item/generated",
            "textures": {
                "layer0": "warriorland_halloween:item/crimson_warden_helmet",
                "particle": "warriorland_halloween:item/crimson_warden_helmet",
            },
            "elements": voxel_elements(image, (6.2, 9.8)),
        }
    ARMOR_MODEL_OUT.mkdir(parents=True, exist_ok=True)
    helmet_path = ARMOR_MODEL_OUT / "crimson_warden_helmet_3d.json"
    helmet_path.write_text(
        json.dumps(helmet, separators=(",", ":"), ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    parsed_helmet = json.loads(helmet_path.read_text(encoding="utf-8"))
    if parsed_helmet.get("textures", {}).get("layer0") != helmet["textures"]["layer0"] or not parsed_helmet.get("elements"):
        raise RuntimeError("Crimson Warden wearable helmet model failed its source checks")
    print(f"Generated worn 3D helmet model: {helmet_path.relative_to(ROOT)} ({len(helmet['elements'])} voxel elements)")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    art = {
        "hunter_mask.png": hunter_mask(),
        "cursed_talisman.png": necklace(),
        "halloween_token.png": halloween_token(),
        "cursed_candy.png": cursed_candy(),
        "haunted_map.png": haunted_map(),
        "crimson_warden_helmet.png": armor_helmet(),
        "crimson_warden_chestplate.png": armor_chest(),
        "crimson_warden_leggings.png": armor_leggings(),
        "crimson_warden_boots.png": armor_boots(),
        "crimson_warden_sword.png": sword(),
        "crimson_warden_pickaxe.png": pickaxe(),
        "crimson_warden_axe.png": axe(),
        "crimson_warden_shovel.png": shovel(),
        "crimson_warden_hoe.png": hoe(),
        "crimson_warden_shield.png": shield(),
    }
    for name, image in art.items():
        save(name, image)
        with Image.open(OUT / name) as check:
            if check.size != (32, 32) or check.mode != "RGBA":
                raise RuntimeError(f"Invalid icon generated: {name}")
        print(f"Generated {name}: 32x32 RGBA ({(OUT / name).stat().st_size} bytes)")
    print(f"Validated {len(art)} crisp Halloween item icons in {OUT.relative_to(ROOT)}")
    generate_voxel_models()


if __name__ == "__main__":
    main()
