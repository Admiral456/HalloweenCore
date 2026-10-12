#!/usr/bin/env python3
"""Build crisp, hand-authored 32x32 pixel-art icons for HalloweenCore gear.

The art is drawn on the native pixel grid (no blur, gradients or resampling) and
written directly to the ItemsAdder resource-pack source directory. The script
is deterministic, uses Pillow already installed by the CI workflow, and does not
modify the 64x32 armor-layer atlas supplied with the current release.
"""
from __future__ import annotations

from pathlib import Path
from io import BytesIO
import base64
import json
import uuid
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "itemsadder" / "contents" / "warriorland_halloween"
OUT = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "item"
SOURCE_ITEM_OUT = CONTENT / "textures" / "item"
WINGS_OUT = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "entity" / "equipment" / "wings"
SOURCE_WINGS_OUT = CONTENT / "textures" / "entity" / "equipment" / "wings"
SOURCE_MODEL_OUT = CONTENT / "models" / "source"
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
    SOURCE_ITEM_OUT.mkdir(parents=True, exist_ok=True)
    image.save(SOURCE_ITEM_OUT / name, format="PNG", optimize=True, compress_level=9)


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



def batwing_elytra() -> Image.Image:
    image, d = canvas()
    left = [(16, 8), (12, 5), (8, 4), (5, 7), (2, 7), (4, 12), (1, 15),
            (5, 18), (3, 22), (8, 23), (10, 27), (14, 24), (16, 28)]
    membrane = [(15, 10), (11, 8), (8, 7), (6, 10), (4, 10), (6, 14), (3, 16),
                (7, 18), (5, 21), (9, 21), (11, 24), (14, 22), (15, 25)]
    d.polygon(left, fill=INK, outline=PURPLE)
    d.polygon(membrane, fill=PURPLE_DARK, outline=CRIMSON)
    d.polygon([(31-x, y) for x, y in left], fill=INK, outline=PURPLE)
    d.polygon([(31-x, y) for x, y in membrane], fill=PURPLE_DARK, outline=CRIMSON)
    d.line([(16,8),(12,11),(8,13),(6,17),(10,19),(12,23),(14,24)], fill=BONE)
    d.line([(15,10),(12,14),(8,15)], fill=RUBY)
    d.line([(15,15),(11,17),(9,20)], fill=CRIMSON)
    d.line([(15,20),(13,22),(12,24)], fill=GOLD)
    d.line([(16,8),(20,11),(24,13),(26,17),(22,19),(20,23),(18,24)], fill=BONE)
    d.line([(16,11),(20,14),(24,15)], fill=RUBY)
    d.line([(16,15),(20,17),(22,20)], fill=CRIMSON)
    d.line([(16,20),(18,22),(20,24)], fill=GOLD)
    d.polygon([(14,8),(18,8),(20,12),(18,17),(16,20),(14,17),(12,12)], fill=SHADOW, outline=GOLD_DARK)
    d.rectangle((15,10,17,15), fill=CRIMSON)
    px(d, 16, 12, GOLD_LIGHT)
    return image


def batwing_elytra_broken() -> Image.Image:
    image = batwing_elytra()
    d = ImageDraw.Draw(image)
    d.polygon([(4,19),(8,18),(10,22),(7,25),(4,23)], fill=INK)
    d.polygon([(22,18),(26,17),(28,21),(25,24)], fill=INK)
    d.line([(7,18),(10,20),(8,23)], fill=GOLD_DARK)
    d.line([(23,19),(25,21)], fill=GOLD_DARK)
    return image


def nocturne_sovereign_cowl() -> Image.Image:
    image, d = canvas()
    d.polygon([(2,12),(1,4),(8,7),(12,2),(16,7),(21,3),(24,8),(31,5),(29,15),
               (27,24),(23,28),(18,27),(16,31),(13,27),(8,28),(4,22)],
              fill=INK, outline=IRON_DARK)
    d.polygon([(4,12),(4,7),(9,10),(12,5),(16,10),(21,6),(24,11),(28,8),(27,15),
               (24,23),(20,25),(17,24),(15,28),(12,24),(8,25),(6,21)],
              fill=SHADOW, outline=PURPLE_DARK)
    d.polygon([(8,14),(12,12),(16,15),(20,12),(24,14),(22,21),(18,24),(14,24),(10,21)],
              fill=INK, outline=IRON_DARK)
    d.polygon([(10,16),(14,15),(15,18),(12,19)], fill=CYAN)
    d.polygon([(18,15),(22,16),(20,19),(17,18)], fill=CYAN)
    px(d,12,17,HIGHLIGHT); px(d,20,17,HIGHLIGHT)
    d.line([(6,14),(7,20),(10,23)], fill=CRIMSON)
    d.line([(26,14),(25,20),(22,23)], fill=PURPLE)
    d.polygon([(10,23),(12,25),(11,29),(8,27)], fill=PURPLE_DARK, outline=PURPLE)
    d.polygon([(22,23),(24,26),(21,30),(20,26)], fill=CRIMSON_DARK, outline=CRIMSON)
    d.line([(11,11),(13,10),(16,12),(19,10),(21,11)], fill=BONE)
    d.rectangle((14,21,18,25), fill=GOLD_DARK, outline=GOLD_LIGHT)
    d.rectangle((15,22,17,24), fill=PURPLE)
    px(d,15,22,HIGHLIGHT); px(d,16,24,INK)
    return image


def batwing_wings_texture() -> Image.Image:
    image = Image.new("RGBA", (64, 32), CLEAR)
    d = ImageDraw.Draw(image)
    left = [(31,3),(26,2),(22,5),(18,4),(13,7),(8,7),(5,11),(2,11),(4,15),
            (1,18),(6,21),(4,25),(9,25),(13,29),(18,27),(23,30),(27,27),(31,23)]
    inner = [(30,6),(25,5),(22,8),(18,7),(14,10),(10,10),(7,13),(5,14),(8,17),
             (5,19),(9,21),(8,23),(13,23),(17,26),(22,26),(25,25),(29,22)]
    d.polygon(left, fill=INK, outline=GOLD_DARK)
    d.polygon(inner, fill=PURPLE_DARK, outline=CRIMSON)
    d.polygon([(63-x,y) for x,y in left], fill=INK, outline=GOLD_DARK)
    d.polygon([(63-x,y) for x,y in inner], fill=PURPLE_DARK, outline=CRIMSON)
    for x in (31,32):
        d.line([(x,4),(x,22)], fill=BONE)
        d.line([(x,6),(x-5,13),(x-10,17),(x-16,20),(x-23,21)], fill=STEEL)
        d.line([(x,9),(x-4,16),(x-9,22),(x-14,25)], fill=GOLD_DARK)
    for points in ([(26,9),(22,12),(17,14),(12,17),(8,19)],
                   [(23,18),(20,20),(17,22),(14,24)],
                   [(37,9),(41,12),(46,14),(51,17),(55,19)],
                   [(40,18),(43,20),(46,22),(49,24)]):
        d.line(points, fill=RUBY)
    d.rectangle((29,14,34,19), fill=INK)
    d.polygon([(31,13),(34,16),(32,20),(29,16)], fill=PURPLE)
    px(d,32,16,CYAN)
    return image


def save_blockbench_source(item_id: str, image: Image.Image) -> None:
    buffer = BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    runtime_elements = voxel_elements(image, (6.2, 9.8))
    elements, children = [], []
    for index, part in enumerate(runtime_elements):
        identifier = str(uuid.uuid4())
        frm, to = part["from"], part["to"]
        faces = {name: {"uv": [round(float(v)*2.0, 3) for v in face["uv"]], "texture": 0}
                 for name, face in part["faces"].items()}
        elements.append({
            "name": f"{item_id}_voxel_{index:03d}", "uuid": identifier,
            "from": frm, "to": to, "origin": [(frm[i]+to[i])/2 for i in range(3)],
            "rotation": [0,0,0], "faces": faces, "type": "cube", "autouv": 0, "color": 0,
        })
        children.append(identifier)
    source_model = {
        "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
        "name": item_id, "model_identifier": item_id, "visible_box": [16,16,0],
        "resolution": {"width": SIZE, "height": SIZE}, "elements": elements,
        "outliner": [{"name":"root","origin":[0,0,0],"uuid":str(uuid.uuid4()),"export":True,
                      "isOpen":True,"locked":False,"visibility":True,"autouv":0,"children":children}],
        "textures": [{"path":f"textures/{item_id}.png","name":f"{item_id}.png","folder":"textures",
                      "namespace":"","id":0,"particle":True,"render_mode":"normal","visible":True,
                      "mode":"bitmap","saved":True,"uuid":str(uuid.uuid4()),
                      "source":"data:image/png;base64,"+base64.b64encode(buffer.getvalue()).decode("ascii"),
                      "width":SIZE,"height":SIZE,"uv_width":SIZE,"uv_height":SIZE}],
        "animations": [],
    }
    SOURCE_MODEL_OUT.mkdir(parents=True, exist_ok=True)
    (SOURCE_MODEL_OUT / f"{item_id}.bbmodel").write_text(
        json.dumps(source_model, ensure_ascii=False, separators=(",", ":"))+"\n", encoding="utf-8"
    )


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




def nocturne_sovereign_cowl_worn_atlas() -> Image.Image:
    """Return the 64x64 face/side/back/top atlas for the genuinely 3D worn hood."""
    image = Image.new("RGBA", (64, 64), INK)
    d = ImageDraw.Draw(image)

    # Each 16x16 tile maps to a 4x4 UV square in Minecraft model coordinates.
    # The atlas deliberately uses charcoal cloth, restrained silver/bone trim,
    # a shadowed face and small crimson/spectral accents instead of a purple wash.
    tile_bases = (
        (INK, SHADOW, INK, IRON_DARK),
        (STEEL, INK, PURPLE_DARK, CRIMSON_DARK),
        (IRON_DARK, SHADOW, GOLD_DARK, CRIMSON_DARK),
        (INK, IRON_DARK, PURPLE_DARK, SHADOW),
    )
    for row in range(4):
        for col in range(4):
            x, y = col * 16, row * 16
            d.rectangle((x, y, x + 15, y + 15), fill=tile_bases[row][col])

    def line(col: int, row: int, points: list[tuple[int, int]], color: tuple[int, int, int, int], width: int = 1) -> None:
        x0, y0 = col * 16, row * 16
        d.line([(x0 + x, y0 + y) for x, y in points], fill=color, width=width)

    def rect(col: int, row: int, coords: tuple[int, int, int, int], color: tuple[int, int, int, int], outline=None) -> None:
        x0, y0 = col * 16, row * 16
        x1, y1, x2, y2 = coords
        d.rectangle((x0 + x1, y0 + y1, x0 + x2, y0 + y2), fill=color, outline=outline)

    # Front shell: folded obsidian fabric with low-contrast edge stitching.
    line(0, 0, [(1, 15), (3, 11), (4, 5), (7, 1)], IRON_DARK)
    line(0, 0, [(5, 0), (6, 6), (9, 11), (8, 15)], SHADOW, 2)
    line(0, 0, [(12, 1), (10, 6), (12, 10), (14, 14)], IRON_DARK)
    line(0, 0, [(1, 14), (4, 13), (6, 14)], CRIMSON_DARK)
    line(0, 0, [(10, 2), (12, 4), (13, 8)], STEEL)

    # Side cloth: diagonal folds that remain visible on the head's silhouette.
    line(1, 0, [(0, 2), (4, 5), (7, 11), (8, 15)], IRON_DARK, 2)
    line(1, 0, [(7, 0), (9, 4), (11, 8), (15, 12)], SHADOW, 2)
    line(1, 0, [(2, 13), (6, 12), (9, 14)], CRIMSON_DARK)
    line(1, 0, [(12, 2), (11, 5), (13, 8)], STEEL)

    # Rear drape and crown-top.
    line(2, 0, [(2, 0), (3, 6), (2, 15)], IRON_DARK, 2)
    line(2, 0, [(7, 1), (6, 7), (8, 15)], SHADOW, 2)
    line(2, 0, [(12, 0), (10, 7), (13, 15)], IRON_DARK, 2)
    line(2, 0, [(1, 14), (5, 13), (8, 15)], CRIMSON_DARK)
    line(3, 0, [(0, 9), (4, 6), (7, 3), (10, 5), (15, 10)], INK, 2)
    line(3, 0, [(2, 11), (6, 8), (8, 6), (12, 8), (14, 11)], STEEL)
    line(3, 0, [(4, 12), (8, 10), (12, 12)], PURPLE_DARK)

    # Bone/silver trim tile.
    line(0, 1, [(1, 14), (3, 4), (6, 1)], IRON_DARK, 2)
    line(0, 1, [(2, 12), (4, 4), (6, 2)], BONE)
    line(0, 1, [(7, 2), (9, 7), (14, 12)], HIGHLIGHT)
    line(0, 1, [(2, 14), (8, 14), (13, 12)], STEEL)
    for x in (4, 8, 12):
        d.point((x, 16 + 5), fill=GOLD_DARK)
        d.point((x, 16 + 6), fill=GOLD_LIGHT)

    # Face aperture: nearly black, deliberately without an opaque purple glow.
    rect(1, 1, (0, 0, 15, 15), INK)
    rect(1, 1, (2, 1, 13, 14), SHADOW)
    line(1, 1, [(3, 3), (6, 2), (9, 3)], IRON_DARK)
    line(1, 1, [(2, 12), (6, 13), (11, 12)], PURPLE_DARK)

    # Jewel/rune, ragged hem, and supporting textures.
    d.polygon([(2 * 16 + 8, 1 * 16 + 1), (2 * 16 + 13, 1 * 16 + 7),
               (2 * 16 + 8, 1 * 16 + 14), (2 * 16 + 3, 1 * 16 + 7)],
              fill=CRIMSON, outline=GOLD_LIGHT)
    d.polygon([(2 * 16 + 8, 1 * 16 + 4), (2 * 16 + 10, 1 * 16 + 7),
               (2 * 16 + 8, 1 * 16 + 11), (2 * 16 + 6, 1 * 16 + 7)],
              fill=RUBY, outline=EMBER)
    d.point((2 * 16 + 7, 1 * 16 + 6), fill=HIGHLIGHT)

    line(3, 1, [(0, 2), (4, 5), (7, 11), (10, 8), (15, 14)], INK, 2)
    line(3, 1, [(1, 5), (4, 8), (6, 13)], CRIMSON)
    line(3, 1, [(10, 1), (9, 5), (12, 9), (13, 14)], PURPLE_DARK, 2)

    line(0, 2, [(1, 2), (4, 5), (6, 2)], BONE)
    line(0, 2, [(1, 11), (5, 8), (8, 10), (14, 6)], STEEL)
    for x, y in ((3, 4), (7, 9), (12, 6), (11, 12)):
        d.point((x, 32 + y), fill=GOLD_LIGHT)
    line(1, 2, [(1, 0), (4, 5), (3, 11), (7, 15)], IRON_DARK, 2)
    line(1, 2, [(9, 0), (8, 4), (11, 9), (14, 15)], SHADOW, 2)
    line(2, 2, [(1, 13), (5, 5), (8, 13), (12, 3), (15, 12)], BONE)
    line(2, 2, [(2, 14), (6, 6), (8, 14)], GOLD_DARK)
    line(3, 2, [(1, 2), (5, 4), (7, 8), (11, 10), (14, 13)], CRIMSON)
    line(3, 2, [(2, 11), (5, 8), (7, 9)], RUBY)
    for x, y in ((4, 3), (7, 8), (11, 10), (13, 13)):
        d.point((48 + x, 32 + y), fill=GOLD_LIGHT)

    # Underside and seams. Keep every pixel opaque; each mapped tile has a clear role.
    line(0, 3, [(0, 4), (5, 8), (7, 15)], SHADOW, 2)
    line(0, 3, [(7, 0), (10, 5), (12, 10), (15, 12)], IRON_DARK, 2)
    line(1, 3, [(1, 0), (4, 4), (3, 11), (8, 15)], BONE)
    line(1, 3, [(8, 0), (10, 5), (13, 8), (15, 14)], STEEL)
    line(2, 3, [(2, 1), (5, 4), (3, 8), (7, 11), (4, 14)], PURPLE)
    line(2, 3, [(10, 1), (8, 6), (12, 10), (10, 14)], PURPLE_DARK, 2)
    line(3, 3, [(0, 2), (5, 6), (8, 10), (13, 12)], IRON_DARK, 2)
    line(3, 3, [(1, 13), (5, 11), (9, 14)], CRIMSON_DARK)
    return image


def nocturne_cowl_model_elements() -> list[dict]:
    """Build a shaped hood shell with face aperture, drape, ornaments and a deep gem."""
    # Each element is a true head-shaped cuboid, not an extrusion of every icon pixel.
    geometry = [
        ([2.0, 8.0, 5.0], [14.0, 16.0, 14.0], "cloth"),
        ([3.0, 14.0, 3.0], [13.0, 16.0, 13.0], "cloth"),
        ([1.5, 8.0, 3.0], [4.5, 15.0, 11.0], "cloth"),
        ([11.5, 8.0, 3.0], [14.5, 15.0, 11.0], "cloth"),
        ([3.0, 6.0, 10.0], [13.0, 12.0, 15.0], "cloth"),
        ([4.0, 6.0, 12.0], [6.0, 9.0, 15.0], "hem"),
        ([10.0, 6.0, 12.0], [12.0, 9.0, 15.0], "hem"),
        ([4.0, 14.0, 2.0], [12.0, 16.0, 6.0], "trim"),
        ([2.5, 9.0, 2.0], [5.5, 14.0, 7.0], "trim"),
        ([10.5, 9.0, 2.0], [13.5, 14.0, 7.0], "trim"),
        ([5.0, 7.0, 2.0], [11.0, 9.0, 7.0], "trim"),
        ([5.5, 9.0, 2.8], [10.5, 13.2, 5.0], "face"),
        ([2.0, 6.0, 4.0], [4.5, 9.0, 10.0], "hem"),
        ([11.5, 6.0, 4.0], [14.0, 9.0, 10.0], "hem"),
        ([4.0, 12.0, 1.8], [5.5, 13.5, 3.1], "runes"),
        ([10.5, 12.0, 1.8], [12.0, 13.5, 3.1], "runes"),
        ([6.0, 10.1, 1.7], [7.5, 11.4, 2.8], "eyes"),
        ([8.5, 10.1, 1.7], [10.0, 11.4, 2.8], "eyes"),
        ([6.9, 13.0, 1.7], [9.1, 15.0, 3.2], "gem"),
        ([7.5, 13.6, 1.2], [8.5, 14.5, 2.0], "ruby"),
        ([5.5, 14.0, 1.8], [6.7, 15.0, 2.8], "trim"),
        ([9.3, 14.0, 1.8], [10.5, 15.0, 2.8], "trim"),
        ([6.0, 9.3, 1.8], [10.0, 9.8, 2.9], "hem"),
        ([4.0, 8.0, 8.0], [5.5, 10.0, 12.0], "hem"),
        ([10.5, 8.0, 8.0], [12.0, 10.0, 12.0], "hem"),
    ]
    face_tiles = {
        "cloth": {"north": (0, 0), "south": (2, 0), "east": (1, 0), "west": (1, 0), "up": (3, 0), "down": (3, 3)},
        "trim": {"north": (0, 1), "south": (0, 2), "east": (0, 2), "west": (0, 2), "up": (2, 2), "down": (1, 3)},
        "face": {"north": (1, 1), "south": (1, 1), "east": (1, 1), "west": (1, 1), "up": (1, 1), "down": (1, 1)},
        "eyes": {"north": (1, 2), "south": (1, 1), "east": (1, 2), "west": (1, 2), "up": (1, 2), "down": (1, 2)},
        "gem": {"north": (2, 1), "south": (2, 2), "east": (2, 2), "west": (2, 2), "up": (2, 2), "down": (2, 3)},
        "ruby": {"north": (2, 1), "south": (2, 2), "east": (2, 2), "west": (2, 2), "up": (2, 2), "down": (2, 3)},
        "runes": {"north": (3, 2), "south": (0, 2), "east": (2, 2), "west": (2, 2), "up": (0, 1), "down": (0, 2)},
        "hem": {"north": (3, 1), "south": (0, 3), "east": (1, 3), "west": (1, 3), "up": (1, 3), "down": (1, 3)},
    }
    elements = []
    for frm, to, role in geometry:
        faces = {}
        for face_name, (col, row) in face_tiles[role].items():
            faces[face_name] = {
                "uv": [col * 4, row * 4, (col + 1) * 4, (row + 1) * 4],
                "texture": "#layer0",
            }
        elements.append({"from": frm, "to": to, "faces": faces})
    return elements


def save_worn_cowl_assets() -> int:
    atlas = nocturne_sovereign_cowl_worn_atlas()
    OUT.mkdir(parents=True, exist_ok=True)
    SOURCE_ITEM_OUT.mkdir(parents=True, exist_ok=True)
    atlas_path = OUT / "nocturne_sovereign_cowl_worn.png"
    source_texture_path = SOURCE_ITEM_OUT / "nocturne_sovereign_cowl_worn.png"
    atlas.save(atlas_path, format="PNG", optimize=True, compress_level=9)
    atlas.save(source_texture_path, format="PNG", optimize=True, compress_level=9)
    if atlas.size != (64, 64) or atlas.mode != "RGBA":
        raise RuntimeError("The wearable Nocturne Cowl atlas must be 64x64 RGBA")

    elements = nocturne_cowl_model_elements()
    model = {
        "credit": "HalloweenCore - shaped Nocturne Sovereign Cowl with separate face/side/back/top atlas",
        "parent": "item/generated",
        "textures": {
            "layer0": "warriorland_halloween:item/nocturne_sovereign_cowl_worn",
            "particle": "warriorland_halloween:item/nocturne_sovereign_cowl_worn",
        },
        "elements": elements,
    }
    ARMOR_MODEL_OUT.mkdir(parents=True, exist_ok=True)
    model_path = ARMOR_MODEL_OUT / "nocturne_sovereign_cowl_3d.json"
    model_path.write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    check = json.loads(model_path.read_text(encoding="utf-8"))
    if check.get("textures", {}).get("layer0") != "warriorland_halloween:item/nocturne_sovereign_cowl_worn":
        raise RuntimeError("Wearable cowl points to the wrong texture atlas")

    # Keep an editable Blockbench source with full 64x64 pixel-space UVs and
    # the exact same geometry as the Minecraft JSON model.
    source_elements, children = [], []
    for index, part in enumerate(elements):
        identifier = str(uuid.uuid4())
        frm, to = part["from"], part["to"]
        faces = {
            name: {
                "uv": [round(float(value) * 4.0, 3) for value in face["uv"]],
                "texture": 0,
            }
            for name, face in part["faces"].items()
        }
        source_elements.append({
            "name": f"cowl_piece_{index:02d}",
            "uuid": identifier,
            "from": frm,
            "to": to,
            "origin": [(float(frm[i]) + float(to[i])) / 2.0 for i in range(3)],
            "rotation": [0, 0, 0],
            "faces": faces,
            "type": "cube",
            "autouv": 0,
            "color": 0,
        })
        children.append(identifier)

    source_model = {
        "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
        "name": "Nocturne Sovereign Cowl",
        "model_identifier": "nocturne_sovereign_cowl",
        "visible_box": [16, 16, 0],
        "resolution": {"width": 64, "height": 64},
        "elements": source_elements,
        "outliner": [{
            "name": "cowl_root", "origin": [8, 8, 8], "uuid": str(uuid.uuid4()),
            "export": True, "isOpen": True, "locked": False, "visibility": True,
            "autouv": 0, "children": children,
        }],
        "textures": [{
            "path": "textures/nocturne_sovereign_cowl_worn.png",
            "name": "nocturne_sovereign_cowl_worn.png",
            "folder": "textures",
            "namespace": "",
            "id": 0,
            "particle": True,
            "render_mode": "normal",
            "visible": True,
            "mode": "bitmap",
            "saved": True,
            "uuid": str(uuid.uuid4()),
            "source": "data:image/png;base64," + base64.b64encode(atlas_path.read_bytes()).decode("ascii"),
            "width": 64,
            "height": 64,
            "uv_width": 64,
            "uv_height": 64,
        }],
        "animations": [],
    }
    SOURCE_MODEL_OUT.mkdir(parents=True, exist_ok=True)
    (SOURCE_MODEL_OUT / "nocturne_sovereign_cowl.bbmodel").write_text(
        json.dumps(source_model, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    print(f"Generated shaped 3D Nocturne Cowl: {len(elements)} pieces, 64x64 UV atlas")
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
        "batwing_elytra.png": batwing_elytra(),
        "batwing_elytra_broken.png": batwing_elytra_broken(),
        "nocturne_sovereign_cowl.png": nocturne_sovereign_cowl(),
    }
    for name, image in art.items():
        save(name, image)
        with Image.open(OUT / name) as check:
            if check.size != (32, 32) or check.mode != "RGBA":
                raise RuntimeError(f"Invalid icon generated: {name}")
        print(f"Generated {name}: 32x32 RGBA ({(OUT / name).stat().st_size} bytes)")
    print(f"Validated {len(art)} crisp Halloween item icons in {OUT.relative_to(ROOT)}")
    WINGS_OUT.mkdir(parents=True, exist_ok=True)
    SOURCE_WINGS_OUT.mkdir(parents=True, exist_ok=True)
    wings = batwing_wings_texture()
    wings.save(WINGS_OUT / "batwing_elytra.png", format="PNG", optimize=True, compress_level=9)
    wings.save(SOURCE_WINGS_OUT / "batwing_elytra.png", format="PNG", optimize=True, compress_level=9)
    if wings.size != (64, 32) or wings.mode != "RGBA":
        raise RuntimeError(f"Invalid Batwing Elytra wings atlas: {wings.size} {wings.mode}")
    print("Generated 64x32 Batwing Elytra equipment-wing atlas")
    generate_voxel_models()
    # Keep the small inventory icon as its own item model; the worn head slot
    # uses a separate spatial mesh and face/side/back/top texture atlas.
    save_voxel_model("nocturne_sovereign_cowl", "item/generated", (7.1, 8.9))
    worn_cowl_elements = save_worn_cowl_assets()
    print("Generated wearable Nocturne Sovereign Cowl with " + str(worn_cowl_elements) + " shaped 3D elements")


if __name__ == "__main__":
    main()
