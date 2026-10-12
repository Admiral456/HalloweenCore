#!/usr/bin/env python3
"""Generate valid, consistent 64x32 Crimson Warden armor UV atlases."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "itemsadder" / "contents" / "warriorland_halloween"
SOURCE = CONTENT / "textures" / "armor" / "crimson_warden"
PACK = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "armor" / "crimson_warden"

INK = (12, 10, 17, 255)
SHADOW = (25, 22, 31, 255)
IRON_DARK = (48, 49, 62, 255)
IRON = (82, 87, 101, 255)
STEEL = (143, 151, 164, 255)
HIGHLIGHT = (206, 211, 218, 255)
CRIMSON_DARK = (76, 8, 26, 255)
CRIMSON = (142, 16, 43, 255)
RUBY = (205, 28, 53, 255)
EMBER = (255, 81, 43, 255)
GOLD_DARK = (111, 64, 23, 255)
GOLD = (207, 143, 48, 255)
GOLD_LIGHT = (255, 215, 111, 255)
BONE = (220, 211, 187, 255)
CLEAR = (0, 0, 0, 0)


def line(d, points, color, width=1):
    d.line(points, fill=color, width=width)


def plate(d, box, base=IRON_DARK, edge=INK, highlight=STEEL):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=base, outline=edge)
    if x1 - x0 > 3 and y1 - y0 > 3:
        line(d, [(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], highlight)
        line(d, [(x0 + 1, y0 + 1), (x0 + 1, y1 - 1)], SHADOW)
        line(d, [(x0 + 1, y1 - 1), (x1 - 1, y1 - 1)], INK)


def gem(d, cx, cy, scale=1):
    d.polygon([(cx, cy - 3 * scale), (cx + 2 * scale, cy),
               (cx, cy + 3 * scale), (cx - 2 * scale, cy)], fill=CRIMSON, outline=GOLD_DARK)
    d.point((cx, cy - 1 * scale), fill=GOLD_LIGHT)
    d.point((cx - 1 * scale, cy), fill=RUBY)


def draw_head(d):
    d.rectangle((0, 0, 31, 15), fill=SHADOW)
    for x in (0, 8, 16, 24):
        d.rectangle((x, 0, min(x + 7, 31), 15), fill=IRON_DARK if x in (8, 24) else SHADOW)
        line(d, [(x, 0), (min(x + 7, 31), 0)], STEEL if x == 8 else IRON)
        line(d, [(x, 1), (x, 14)], INK)
        line(d, [(min(x + 7, 31), 2), (min(x + 7, 31), 13)], INK)
    plate(d, (8, 8, 15, 15), base=INK, edge=IRON, highlight=IRON)
    line(d, [(8, 8), (10, 7), (13, 7), (15, 8)], STEEL)
    line(d, [(9, 10), (10, 10)], EMBER, 2)
    line(d, [(13, 10), (14, 10)], EMBER, 2)
    line(d, [(10, 13), (13, 13)], CRIMSON)
    plate(d, (9, 1, 14, 6), base=IRON_DARK, edge=INK, highlight=STEEL)
    gem(d, 11, 3, 1)
    line(d, [(2, 9), (4, 8), (6, 10), (6, 13)], IRON)
    line(d, [(18, 10), (20, 8), (22, 10), (22, 13)], IRON)
    line(d, [(25, 4), (27, 6), (28, 10)], CRIMSON_DARK)
    for x in (3, 5, 19, 21, 27):
        d.point((x, 14), fill=GOLD_DARK)


def draw_arm(d, x0, y0, flip=False):
    d.rectangle((x0, y0, x0 + 15, y0 + 15), fill=SHADOW)
    plate(d, (x0 + 2, y0 + 1, x0 + 13, y0 + 14), base=IRON_DARK, edge=INK, highlight=IRON)
    for x in (x0 + 4, x0 + 7, x0 + 10):
        line(d, [(x, y0 + 3), (x, y0 + 12)], IRON if x != x0 + 7 else SHADOW)
    line(d, [(x0 + 2, y0 + 4), (x0 + 13, y0 + 4)], STEEL)
    line(d, [(x0 + 2, y0 + 10), (x0 + 13, y0 + 10)], CRIMSON_DARK, 2)
    line(d, [(x0 + 4, y0 + 8), (x0 + 7, y0 + 6), (x0 + 10, y0 + 8)], RUBY)
    for x in (x0 + 4, x0 + 11):
        d.point((x, y0 + 2), fill=GOLD_LIGHT)
        d.point((x, y0 + 13), fill=STEEL)
    if flip:
        line(d, [(x0 + 13, y0 + 1), (x0 + 10, y0 + 5), (x0 + 13, y0 + 8)], HIGHLIGHT)


def draw_torso(d):
    d.rectangle((16, 16, 39, 31), fill=SHADOW)
    for x in (16, 20, 28, 32, 36):
        line(d, [(x, 17), (x, 30)], INK)
    plate(d, (17, 17, 19, 30), base=IRON_DARK, edge=INK, highlight=IRON)
    plate(d, (20, 17, 27, 30), base=IRON_DARK, edge=INK, highlight=STEEL)
    plate(d, (28, 17, 31, 30), base=SHADOW, edge=INK, highlight=IRON)
    plate(d, (32, 17, 39, 30), base=IRON_DARK, edge=INK, highlight=IRON)
    line(d, [(21, 19), (26, 19)], HIGHLIGHT)
    line(d, [(21, 20), (21, 25)], STEEL)
    line(d, [(26, 20), (26, 25)], INK)
    d.polygon([(23, 20), (25, 22), (24, 26), (22, 24)], fill=CRIMSON_DARK, outline=GOLD_DARK)
    gem(d, 24, 23, 1)
    line(d, [(20, 27), (22, 29), (24, 28), (26, 29), (27, 27)], BONE)
    line(d, [(18, 20), (19, 23), (18, 26)], CRIMSON)
    line(d, [(30, 20), (29, 23), (30, 26)], CRIMSON_DARK)
    line(d, [(20, 30), (27, 30)], STEEL)
    for x in (18, 30, 34, 38):
        d.point((x, 18), fill=GOLD_LIGHT)


def draw_boot(d, x0, y0, reverse=False):
    d.rectangle((x0, y0, x0 + 15, y0 + 15), fill=SHADOW)
    plate(d, (x0 + 2, y0 + 1, x0 + 13, y0 + 14), base=IRON_DARK, edge=INK, highlight=IRON)
    line(d, [(x0 + 3, y0 + 3), (x0 + 12, y0 + 3)], STEEL)
    line(d, [(x0 + 3, y0 + 6), (x0 + 12, y0 + 6)], IRON)
    line(d, [(x0 + 3, y0 + 10), (x0 + 12, y0 + 10)], CRIMSON_DARK, 2)
    line(d, [(x0 + 2, y0 + 12), (x0 + 13, y0 + 12)], INK, 2)
    line(d, [(x0 + 3, y0 + 14), (x0 + 12, y0 + 14)], GOLD_DARK)
    if not reverse:
        gem(d, x0 + 8, y0 + 8, 1)
    else:
        line(d, [(x0 + 5, y0 + 8), (x0 + 8, y0 + 5), (x0 + 11, y0 + 8)], RUBY)
        line(d, [(x0 + 5, y0 + 8), (x0 + 8, y0 + 11), (x0 + 11, y0 + 8)], CRIMSON)


def generate_layer_1():
    image = Image.new("RGBA", (64, 32), INK)
    for y in range(32):
        for x in range(64):
            image.putpixel((x, y), SHADOW if (x // 8 + y // 8) % 3 else IRON_DARK)
    d = ImageDraw.Draw(image)
    draw_head(d)
    draw_arm(d, 32, 0, flip=True)
    draw_torso(d)
    draw_arm(d, 40, 16, flip=False)
    draw_boot(d, 0, 16, reverse=False)
    draw_boot(d, 48, 16, reverse=True)
    for x in (0, 16, 32, 40, 48, 56, 63):
        line(d, [(x, 0), (x, 31)], INK)
    for y in (0, 8, 15, 16, 24, 31):
        line(d, [(0, y), (63, y)], INK)
    line(d, [(8, 8), (15, 8)], STEEL)
    line(d, [(8, 12), (15, 12)], IRON)
    gem(d, 24, 23, 1)
    return image


def generate_layer_2():
    image = Image.new("RGBA", (64, 32), CLEAR)
    for y in range(32):
        for x in range(48):
            image.putpixel((x, y), SHADOW if (x // 8 + y // 8) % 2 else IRON_DARK)
    d = ImageDraw.Draw(image)
    for x0 in (0, 16):
        plate(d, (x0 + 2, 1, x0 + 13, 14), base=IRON_DARK, edge=INK, highlight=STEEL)
        line(d, [(x0 + 4, 3), (x0 + 11, 3)], IRON)
        line(d, [(x0 + 4, 6), (x0 + 11, 6)], CRIMSON_DARK, 2)
        line(d, [(x0 + 4, 10), (x0 + 11, 10)], IRON)
        gem(d, x0 + 8, 8, 1)
    plate(d, (34, 1, 45, 14), base=SHADOW, edge=INK, highlight=STEEL)
    line(d, [(36, 4), (43, 4)], GOLD_DARK)
    gem(d, 40, 7, 1)
    line(d, [(36, 11), (43, 11)], CRIMSON_DARK, 2)
    for x0 in (0, 16):
        plate(d, (x0 + 2, 17, x0 + 13, 30), base=IRON_DARK, edge=INK, highlight=STEEL)
        line(d, [(x0 + 4, 19), (x0 + 4, 28)], IRON)
        line(d, [(x0 + 8, 18), (x0 + 8, 29)], CRIMSON, 2)
        line(d, [(x0 + 11, 19), (x0 + 11, 28)], IRON)
        line(d, [(x0 + 3, 30), (x0 + 12, 30)], GOLD_DARK)
    plate(d, (34, 17, 45, 30), base=SHADOW, edge=INK, highlight=IRON)
    line(d, [(36, 19), (43, 19)], STEEL)
    line(d, [(36, 23), (43, 23)], CRIMSON_DARK, 2)
    line(d, [(36, 28), (43, 28)], GOLD_DARK)
    return image


def main():
    SOURCE.mkdir(parents=True, exist_ok=True)
    PACK.mkdir(parents=True, exist_ok=True)
    for name, image in (("layer_1.png", generate_layer_1()), ("layer_2.png", generate_layer_2())):
        for folder in (SOURCE, PACK):
            target = folder / name
            image.save(target, format="PNG", optimize=True, compress_level=9)
            with Image.open(target) as check:
                check.load()
                if check.size != (64, 32) or check.mode != "RGBA":
                    raise RuntimeError(f"Invalid generated armor layer {target}: {check.size}/{check.mode}")
        print(f"Generated valid 64x32 RGBA armor layer: {name}")


if __name__ == "__main__":
    main()
