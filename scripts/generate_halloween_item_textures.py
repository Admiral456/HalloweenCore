#!/usr/bin/env python3
"""Generate project-owned 32x32 Halloween item sprites with deterministic pixel art.

The generator only writes shop/gear item icons. ModelEngine mob atlases supplied by
artists are deliberately left untouched. It uses the Python standard library only.
"""
from __future__ import annotations

import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "item"
SIZE = 32
CLEAR = (0, 0, 0, 0)

# Unified Crimson Warden palette: obsidian outlines, dark steel, crimson enamel,
# antique gold and restrained violet magic. Every icon is authored on a 32px grid.
C = {
    "outline": (14, 9, 17, 255),
    "void": (27, 15, 26, 255),
    "dark": (48, 18, 32, 255),
    "shade": (78, 22, 38, 255),
    "wine": (112, 24, 43, 255),
    "crimson": (160, 28, 47, 255),
    "red": (203, 42, 51, 255),
    "glow": (242, 75, 66, 255),
    "ember": (255, 132, 68, 255),
    "gold_dark": (105, 58, 32, 255),
    "gold": (177, 112, 46, 255),
    "gold_light": (231, 171, 72, 255),
    "pale_gold": (255, 218, 133, 255),
    "steel_dark": (38, 43, 55, 255),
    "steel": (81, 95, 111, 255),
    "steel_light": (151, 170, 181, 255),
    "silver": (207, 216, 209, 255),
    "purple_dark": (48, 24, 79, 255),
    "purple": (91, 43, 136, 255),
    "violet": (156, 59, 171, 255),
    "pink": (224, 102, 198, 255),
    "bone": (208, 192, 153, 255),
    "paper_dark": (121, 82, 47, 255),
    "paper": (205, 176, 119, 255),
    "paper_light": (244, 220, 158, 255),
    "green_dark": (36, 67, 43, 255),
    "green": (75, 123, 57, 255),
}

Pixel = tuple[int, int, int, int]


class Canvas:
    def __init__(self, size: int = SIZE):
        self.size = size
        self.pixels: list[list[Pixel]] = [[CLEAR for _ in range(size)] for _ in range(size)]

    def set(self, x: int, y: int, color: Pixel) -> None:
        if 0 <= x < self.size and 0 <= y < self.size:
            self.pixels[y][x] = color

    def rect(self, x1: int, y1: int, x2: int, y2: int, color: Pixel, outline: Pixel | None = None) -> None:
        for y in range(max(0, y1), min(self.size, y2 + 1)):
            for x in range(max(0, x1), min(self.size, x2 + 1)):
                self.set(x, y, color)
        if outline:
            self.line(x1, y1, x2, y1, outline)
            self.line(x2, y1, x2, y2, outline)
            self.line(x2, y2, x1, y2, outline)
            self.line(x1, y2, x1, y1, outline)

    def line(self, x1: int, y1: int, x2: int, y2: int, color: Pixel, width: int = 1) -> None:
        dx, dy = abs(x2 - x1), abs(y2 - y1)
        sx = 1 if x1 < x2 else -1
        sy = 1 if y1 < y2 else -1
        error = dx - dy
        x, y = x1, y1
        while True:
            radius = width // 2
            for yy in range(y - radius, y + radius + 1):
                for xx in range(x - radius, x + radius + 1):
                    self.set(xx, yy, color)
            if x == x2 and y == y2:
                break
            twice = error * 2
            if twice > -dy:
                error -= dy
                x += sx
            if twice < dx:
                error += dx
                y += sy

    def poly(self, points: list[tuple[int, int]], color: Pixel, outline: Pixel | None = None) -> None:
        min_x = max(0, min(p[0] for p in points))
        max_x = min(self.size - 1, max(p[0] for p in points))
        min_y = max(0, min(p[1] for p in points))
        max_y = min(self.size - 1, max(p[1] for p in points))
        for y in range(min_y, max_y + 1):
            for x in range(min_x, max_x + 1):
                inside = False
                j = len(points) - 1
                for i, (xi, yi) in enumerate(points):
                    xj, yj = points[j]
                    if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / ((yj - yi) or 1) + xi):
                        inside = not inside
                    j = i
                if inside:
                    self.set(x, y, color)
        if outline:
            for i, (x1, y1) in enumerate(points):
                x2, y2 = points[(i + 1) % len(points)]
                self.line(x1, y1, x2, y2, outline)

    def ellipse(self, x1: int, y1: int, x2: int, y2: int, color: Pixel,
                outline: Pixel | None = None) -> None:
        cx, cy = (x1 + x2) / 2.0, (y1 + y2) / 2.0
        rx, ry = max(0.5, (x2 - x1) / 2.0), max(0.5, (y2 - y1) / 2.0)
        for y in range(max(0, y1), min(self.size, y2 + 1)):
            for x in range(max(0, x1), min(self.size, x2 + 1)):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
                if d <= 1.0:
                    self.set(x, y, color)
                elif outline and d <= 1.32:
                    self.set(x, y, outline)

    def diamond(self, cx: int, cy: int, radius_x: int, radius_y: int,
                color: Pixel, outline: Pixel | None = None) -> None:
        pts = [(cx, cy - radius_y), (cx + radius_x, cy), (cx, cy + radius_y), (cx - radius_x, cy)]
        self.poly(pts, color, outline)

    def save(self, filename: str) -> None:
        png = encode_png(self.pixels)
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / filename).write_bytes(png)


def chunk(tag: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def encode_png(pixels: list[list[Pixel]]) -> bytes:
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\0" + b"".join(bytes(px) for px in row) for row in pixels)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def hunter_mask() -> Canvas:
    a = Canvas()
    a.poly([(4, 11), (8, 8), (10, 3), (14, 8), (18, 8), (22, 3), (24, 9), (28, 12),
            (26, 24), (21, 28), (11, 28), (6, 24)], C["outline"])
    a.poly([(5, 12), (9, 9), (11, 5), (14, 10), (18, 10), (22, 5), (24, 11), (27, 13),
            (25, 23), (20, 26), (12, 26), (7, 23)], C["wine"], C["gold_dark"])
    a.poly([(7, 13), (12, 11), (15, 14), (15, 19), (12, 22), (8, 20)], C["dark"], C["outline"])
    a.poly([(17, 14), (20, 11), (25, 13), (24, 20), (20, 22), (17, 19)], C["dark"], C["outline"])
    a.line(8, 14, 13, 16, C["glow"], 2)
    a.line(19, 16, 24, 14, C["glow"], 2)
    a.line(10, 16, 12, 16, C["pale_gold"])
    a.line(21, 16, 23, 16, C["pale_gold"])
    a.poly([(12, 22), (15, 20), (17, 20), (20, 22), (19, 25), (13, 25)], C["steel_dark"], C["outline"])
    for x in (14, 17):
        a.line(x, 22, x, 24, C["bone"])
    a.line(9, 12, 12, 10, C["glow"])
    a.line(20, 10, 23, 12, C["glow"])
    return a


def cursed_talisman() -> Canvas:
    a = Canvas()
    a.line(13, 4, 13, 8, C["gold_dark"], 2)
    a.line(13, 4, 17, 4, C["gold_light"])
    a.line(17, 4, 17, 8, C["gold_dark"], 2)
    a.ellipse(5, 7, 27, 29, C["gold_dark"], C["outline"])
    a.ellipse(7, 8, 25, 26, C["gold"], C["pale_gold"])
    a.ellipse(9, 10, 23, 24, C["dark"], C["gold_dark"])
    a.diamond(16, 16, 6, 9, C["purple"], C["outline"])
    a.diamond(16, 15, 4, 6, C["violet"], C["pink"])
    a.line(15, 11, 17, 14, C["pink"])
    a.set(16, 15, C["pale_gold"])
    a.line(11, 20, 13, 22, C["glow"])
    a.line(19, 22, 21, 20, C["glow"])
    a.set(7, 14, C["pale_gold"])
    a.set(24, 18, C["gold_light"])
    a.line(13, 28, 16, 31, C["gold_light"])
    a.line(16, 31, 19, 28, C["gold_light"])
    return a


def halloween_token() -> Canvas:
    a = Canvas()
    a.ellipse(5, 5, 27, 27, C["outline"], C["gold_dark"])
    a.ellipse(7, 6, 25, 24, C["gold"], C["pale_gold"])
    a.ellipse(9, 8, 23, 22, C["gold_dark"], C["gold_light"])
    a.ellipse(10, 9, 22, 21, C["crimson"], C["gold"])
    a.poly([(15, 10), (18, 10), (19, 13), (21, 15), (20, 18), (18, 20),
            (14, 20), (12, 18), (11, 14), (13, 12)], (239, 111, 34, 255), C["outline"])
    a.rect(14, 8, 17, 11, C["green"], C["green_dark"])
    a.rect(12, 13, 14, 15, C["outline"])
    a.rect(18, 13, 20, 15, C["outline"])
    a.rect(13, 17, 19, 18, C["outline"])
    a.set(7, 12, C["pale_gold"])
    a.set(24, 16, C["pale_gold"])
    return a


def cursed_candy() -> Canvas:
    a = Canvas()
    a.poly([(5, 12), (11, 14), (11, 19), (5, 22), (7, 17)], C["outline"])
    a.poly([(27, 12), (21, 14), (21, 19), (27, 22), (25, 17)], C["outline"])
    a.poly([(5, 13), (10, 15), (10, 18), (6, 21), (7, 17)], C["violet"], C["pink"])
    a.poly([(27, 13), (22, 15), (22, 18), (26, 21), (25, 17)], C["crimson"], C["glow"])
    a.poly([(9, 11), (13, 9), (19, 9), (23, 12), (24, 18), (20, 23),
            (13, 23), (8, 19)], C["outline"])
    a.poly([(10, 12), (14, 10), (19, 10), (22, 13), (22, 18), (19, 21),
            (13, 21), (10, 18)], C["wine"], C["glow"])
    a.line(11, 13, 16, 11, C["red"], 2)
    a.line(16, 11, 20, 14, C["purple"], 3)
    a.line(12, 18, 17, 21, C["violet"], 2)
    a.line(17, 21, 21, 17, C["pink"], 2)
    a.set(15, 14, C["pale_gold"])
    a.set(16, 14, C["pale_gold"])
    a.line(13, 25, 18, 25, C["gold_dark"])
    return a


def haunted_map() -> Canvas:
    a = Canvas()
    a.poly([(7, 3), (23, 4), (26, 8), (25, 25), (21, 28), (7, 26), (5, 22), (6, 8)],
           C["outline"])
    a.poly([(8, 5), (22, 6), (24, 9), (23, 24), (20, 26), (8, 24), (7, 21), (8, 9)],
           C["paper"], C["paper_dark"])
    a.poly([(8, 5), (14, 6), (13, 12), (8, 15), (7, 11)], C["paper_light"])
    a.line(8, 18, 12, 17, C["paper_dark"])
    a.line(12, 17, 14, 20, C["paper_dark"])
    a.line(14, 20, 18, 18, C["paper_dark"])
    a.line(18, 18, 21, 20, C["paper_dark"])
    a.line(9, 9, 12, 10, C["green_dark"], 2)
    a.line(10, 11, 12, 13, C["green"], 2)
    a.line(16, 8, 17, 11, C["paper_dark"])
    a.line(17, 11, 20, 12, C["paper_dark"])
    a.line(11, 22, 15, 21, C["crimson"])
    a.line(15, 21, 18, 23, C["crimson"])
    a.line(18, 23, 21, 21, C["crimson"])
    a.line(18, 20, 22, 24, C["glow"], 2)
    a.line(22, 20, 18, 24, C["glow"], 2)
    a.line(22, 7, 22, 10, C["paper_light"])
    return a


def helmet() -> Canvas:
    a = Canvas()
    a.poly([(7, 10), (9, 6), (14, 4), (18, 4), (23, 6), (26, 10),
            (25, 22), (21, 27), (11, 27), (7, 22)], C["outline"])
    a.poly([(8, 10), (10, 7), (14, 5), (18, 5), (22, 7), (25, 10),
            (23, 21), (20, 25), (12, 25), (9, 21)], C["steel_dark"], C["steel"])
    a.poly([(10, 9), (14, 7), (18, 7), (21, 9), (20, 15), (12, 15)], C["wine"], C["outline"])
    a.poly([(7, 11), (10, 10), (12, 17), (10, 22), (8, 19)], C["crimson"], C["gold_dark"])
    a.poly([(25, 11), (22, 10), (20, 17), (22, 22), (24, 19)], C["crimson"], C["gold_dark"])
    a.poly([(10, 17), (13, 15), (19, 15), (22, 17), (21, 21), (18, 24),
            (14, 24), (11, 21)], C["steel_dark"], C["outline"])
    a.line(12, 18, 20, 18, C["glow"], 2)
    a.line(14, 20, 18, 20, C["gold_light"])
    a.poly([(13, 3), (16, 0), (19, 3), (18, 7), (14, 7)], C["crimson"], C["outline"])
    a.line(16, 1, 16, 4, C["pale_gold"])
    a.set(9, 14, C["gold_light"])
    a.set(23, 14, C["gold_light"])
    return a


def chestplate() -> Canvas:
    a = Canvas()
    a.poly([(5, 7), (10, 4), (14, 6), (18, 6), (22, 4), (27, 7),
            (26, 15), (23, 17), (23, 26), (9, 26), (9, 17), (6, 15)], C["outline"])
    a.poly([(6, 8), (10, 5), (14, 7), (18, 7), (22, 5), (26, 8),
            (24, 14), (21, 16), (21, 25), (11, 25), (11, 16), (8, 14)],
           C["steel_dark"], C["steel"])
    a.poly([(9, 8), (13, 8), (15, 11), (14, 17), (10, 16), (8, 13)],
           C["wine"], C["outline"])
    a.poly([(19, 11), (21, 8), (24, 9), (24, 13), (22, 16), (18, 17)],
           C["crimson"], C["outline"])
    a.poly([(14, 8), (18, 8), (20, 13), (18, 19), (14, 19), (12, 13)],
           C["dark"], C["gold_dark"])
    a.diamond(16, 13, 3, 5, C["crimson"], C["glow"])
    a.diamond(16, 13, 1, 3, C["ember"])
    a.line(12, 21, 20, 21, C["steel"])
    a.line(12, 23, 20, 23, C["wine"])
    a.set(10, 10, C["gold_light"])
    a.set(22, 10, C["gold_light"])
    return a


def leggings() -> Canvas:
    a = Canvas()
    a.poly([(7, 5), (12, 4), (16, 7), (20, 4), (25, 5), (24, 14),
            (21, 17), (22, 27), (17, 29), (16, 18), (15, 29), (10, 27),
            (11, 17), (8, 14)], C["outline"])
    a.poly([(8, 6), (12, 5), (15, 8), (14, 15), (11, 17), (12, 26),
            (10, 26), (9, 17), (8, 13)], C["steel_dark"], C["steel"])
    a.poly([(20, 5), (24, 6), (23, 13), (21, 17), (22, 26), (20, 26),
            (19, 17), (17, 15), (17, 8)], C["steel_dark"], C["steel"])
    a.line(10, 7, 13, 8, C["crimson"], 2)
    a.line(20, 8, 23, 7, C["crimson"], 2)
    a.line(13, 10, 13, 14, C["gold_dark"])
    a.line(19, 10, 19, 14, C["gold_dark"])
    a.diamond(16, 13, 3, 4, C["wine"], C["glow"])
    a.line(10, 20, 11, 24, C["steel"])
    a.line(21, 20, 20, 24, C["steel"])
    a.set(10, 27, C["gold_light"])
    a.set(21, 27, C["gold_light"])
    return a


def boots() -> Canvas:
    a = Canvas()
    a.poly([(5, 6), (13, 6), (14, 17), (12, 21), (15, 25), (15, 28),
            (4, 28), (3, 25), (6, 20)], C["outline"])
    a.poly([(18, 6), (26, 6), (25, 20), (28, 24), (28, 28),
            (17, 28), (17, 25), (20, 21), (18, 17)], C["outline"])
    a.poly([(6, 8), (11, 8), (12, 17), (10, 21), (13, 25), (12, 26),
            (5, 26), (5, 24), (8, 20)], C["steel_dark"], C["steel"])
    a.poly([(19, 8), (24, 8), (23, 20), (26, 24), (26, 26),
            (19, 26), (18, 25), (21, 21), (19, 17)], C["steel_dark"], C["steel"])
    a.line(6, 10, 11, 10, C["crimson"], 2)
    a.line(19, 10, 24, 10, C["crimson"], 2)
    a.line(8, 14, 11, 15, C["gold_dark"])
    a.line(20, 15, 23, 14, C["gold_dark"])
    a.line(6, 25, 12, 25, C["glow"], 2)
    a.line(19, 25, 26, 25, C["glow"], 2)
    return a


def sword() -> Canvas:
    a = Canvas()
    a.line(8, 25, 26, 7, C["outline"], 6)
    a.poly([(23, 5), (29, 2), (27, 9), (12, 24), (9, 21)], C["outline"])
    a.poly([(24, 5), (28, 3), (26, 8), (12, 22), (10, 21)], C["steel_light"], C["steel_dark"])
    a.line(24, 6, 13, 18, C["silver"], 2)
    a.line(11, 20, 23, 8, C["steel"])
    a.line(7, 22, 12, 27, C["gold_dark"], 4)
    a.line(7, 22, 12, 27, C["gold_light"], 2)
    a.line(4, 23, 11, 16, C["outline"], 4)
    a.line(5, 22, 10, 17, C["crimson"], 2)
    a.rect(10, 24, 13, 27, C["wine"], C["outline"])
    a.set(24, 5, C["pale_gold"])
    a.set(14, 16, C["glow"])
    return a


def pickaxe() -> Canvas:
    a = Canvas()
    a.line(7, 26, 25, 8, C["outline"], 5)
    a.line(8, 25, 24, 9, C["gold_dark"], 3)
    a.line(10, 24, 21, 13, C["gold_light"])
    a.poly([(4, 10), (6, 6), (14, 5), (20, 8), (24, 7), (29, 10),
            (26, 13), (19, 12), (15, 10), (9, 12)], C["outline"])
    a.poly([(5, 9), (7, 7), (14, 6), (19, 9), (24, 8), (27, 10),
            (25, 11), (19, 11), (15, 9), (9, 11)], C["steel_light"], C["steel_dark"])
    a.line(7, 8, 13, 7, C["silver"], 2)
    a.line(19, 9, 25, 9, C["crimson"])
    a.set(8, 10, C["gold_light"])
    a.line(6, 25, 10, 21, C["wine"], 2)
    a.set(23, 10, C["glow"])
    return a


def axe() -> Canvas:
    a = Canvas()
    a.line(7, 26, 24, 9, C["outline"], 5)
    a.line(8, 25, 23, 10, C["gold_dark"], 3)
    a.line(10, 23, 20, 13, C["gold_light"])
    a.poly([(12, 5), (18, 3), (25, 5), (29, 10), (27, 17),
            (23, 20), (20, 15), (22, 10), (17, 9), (13, 12), (10, 10)], C["outline"])
    a.poly([(13, 6), (18, 4), (24, 6), (27, 10), (25, 16),
            (23, 17), (21, 13), (23, 9), (17, 8), (13, 10), (11, 9)], C["crimson"], C["steel_dark"])
    a.line(15, 6, 21, 6, C["glow"], 2)
    a.line(25, 11, 24, 15, C["gold_light"])
    a.line(5, 25, 9, 21, C["wine"], 2)
    a.set(22, 9, C["silver"])
    return a


def shovel() -> Canvas:
    a = Canvas()
    a.line(8, 7, 24, 23, C["outline"], 5)
    a.line(9, 8, 23, 22, C["gold_dark"], 3)
    a.line(11, 9, 20, 18, C["gold_light"])
    a.poly([(4, 3), (11, 3), (14, 7), (13, 12), (10, 15),
            (6, 12), (3, 8)], C["outline"])
    a.poly([(5, 4), (10, 4), (13, 7), (12, 11), (9, 13),
            (6, 11), (4, 8)], C["steel_light"], C["steel_dark"])
    a.line(6, 6, 10, 6, C["silver"], 2)
    a.line(22, 22, 25, 25, C["wine"], 2)
    a.set(11, 11, C["crimson"])
    a.set(8, 7, C["glow"])
    return a


def hoe() -> Canvas:
    a = Canvas()
    a.line(7, 26, 23, 10, C["outline"], 5)
    a.line(8, 25, 22, 11, C["gold_dark"], 3)
    a.line(10, 23, 20, 13, C["gold_light"])
    a.poly([(9, 5), (14, 5), (18, 8), (22, 4), (27, 4),
            (27, 8), (22, 8), (18, 12), (14, 10), (9, 10)], C["outline"])
    a.poly([(10, 6), (14, 6), (17, 9), (21, 5), (26, 5),
            (26, 7), (22, 7), (18, 11), (14, 9), (10, 9)], C["steel_light"], C["steel_dark"])
    a.line(22, 5, 25, 5, C["silver"], 2)
    a.line(11, 7, 14, 7, C["crimson"])
    a.set(19, 10, C["glow"])
    return a


def batwing_elytra() -> Canvas:
    """Inventory icon for the bat-wing gliding relic; project-owned pixel art."""
    a = Canvas()
    # Wide scalloped wings with distinct membranes and a narrow central spine.
    a.poly([(15, 13), (10, 7), (3, 5), (5, 12), (2, 16), (7, 17),
            (5, 23), (11, 21), (15, 26), (17, 26), (21, 21), (27, 23),
            (25, 17), (30, 16), (27, 12), (29, 5), (22, 7), (17, 13)],
           C["outline"])
    a.poly([(15, 15), (10, 9), (5, 7), (7, 13), (5, 15), (10, 16),
            (8, 20), (12, 19), (15, 23)], C["purple_dark"], C["purple"])
    a.poly([(17, 15), (22, 9), (27, 7), (25, 13), (27, 15), (22, 16),
            (24, 20), (20, 19), (17, 23)], C["dark"], C["crimson"])
    a.line(15, 14, 11, 12, C["violet"], 2)
    a.line(14, 18, 10, 18, C["pink"])
    a.line(17, 14, 21, 12, C["glow"], 2)
    a.line(18, 18, 22, 18, C["red"])
    a.poly([(13, 8), (16, 5), (19, 8), (18, 18), (16, 22), (14, 18)],
           C["steel_dark"], C["gold_dark"])
    a.line(16, 8, 16, 17, C["gold_light"])
    a.diamond(16, 14, 2, 3, C["violet"], C["pink"])
    a.set(7, 11, C["glow"])
    a.set(25, 11, C["glow"])
    return a


def nocturne_sovereign_cowl() -> Canvas:
    """Inventory icon for the shadow cowl with violet eye glow and gold trim."""
    a = Canvas()
    a.poly([(5, 12), (8, 6), (13, 3), (19, 3), (24, 6), (27, 12),
            (25, 19), (22, 25), (16, 29), (10, 25), (7, 19)],
           C["outline"])
    a.poly([(7, 12), (10, 7), (14, 5), (18, 5), (22, 7), (25, 12),
            (23, 18), (21, 23), (16, 27), (11, 23), (9, 18)],
           C["void"], C["purple"])
    a.poly([(8, 12), (12, 9), (16, 10), (20, 9), (24, 12),
            (22, 17), (18, 19), (14, 19), (10, 17)],
           C["dark"], C["gold_dark"])
    a.line(9, 13, 14, 15, C["violet"], 2)
    a.line(18, 15, 23, 13, C["pink"], 2)
    a.set(12, 14, C["pale_gold"])
    a.set(20, 14, C["pale_gold"])
    a.poly([(13, 20), (16, 18), (19, 20), (17, 24), (15, 24)],
           C["purple_dark"], C["crimson"])
    a.line(12, 24, 16, 27, C["gold"])
    a.line(20, 24, 16, 27, C["gold"])
    a.line(11, 8, 14, 6, C["steel"])
    a.line(18, 6, 21, 8, C["steel"])
    return a


def grave_marker() -> Canvas:
    """Original pixel-art icon for a placeable Gothic Gravestone furniture item."""
    a = Canvas()
    # Chipped silhouette and offset stone layers give the marker depth at 32px.
    a.poly([(7, 7), (11, 3), (22, 3), (26, 7), (27, 13), (25, 24),
            (7, 24), (5, 13)], C["outline"])
    a.poly([(8, 8), (12, 5), (21, 5), (24, 8), (25, 13), (23, 22),
            (9, 22), (7, 13)], C["steel_dark"], C["steel"])
    a.line(11, 7, 21, 7, C["steel_light"])
    a.line(9, 10, 9, 14, C["steel"])
    a.line(23, 12, 22, 17, C["void"])
    # Carved crimson cross with antique-gold inlay, wholly original artwork.
    a.poly([(14, 9), (18, 9), (18, 13), (21, 13), (21, 17),
            (18, 17), (18, 21), (14, 21), (14, 17), (11, 17),
            (11, 13), (14, 13)], C["outline"])
    a.poly([(15, 10), (17, 10), (17, 14), (20, 14), (20, 16),
            (17, 16), (17, 20), (15, 20), (15, 16), (12, 16),
            (12, 14), (15, 14)], C["gold"])
    a.line(15, 11, 16, 11, C["pale_gold"])
    a.diamond(16, 15, 1, 1, C["glow"], C["wine"])
    # Heavy plinth and a small mossy edge.
    a.rect(4, 24, 28, 28, C["outline"])
    a.rect(5, 24, 27, 26, C["steel"])
    a.line(6, 25, 13, 25, C["steel_light"], 2)
    a.set(22, 24, C["green"])
    a.set(23, 24, C["green_dark"])
    a.line(8, 20, 10, 19, C["gold_dark"])
    a.line(22, 9, 24, 11, C["silver"])
    return a


def broken_grave_marker() -> Canvas:
    """Original pixel-art icon for a cracked, partially collapsed grave marker."""
    a = Canvas()
    a.poly([(5, 8), (10, 5), (18, 4), (21, 7), (20, 11), (17, 14),
            (18, 24), (7, 24), (5, 16)], C["outline"])
    a.poly([(7, 9), (11, 7), (17, 6), (19, 8), (18, 11), (15, 14),
            (16, 22), (9, 22), (7, 15)], C["steel_dark"], C["steel"])
    # Fallen fragment gives the silhouette a visibly different outline.
    a.poly([(20, 5), (25, 6), (27, 10), (25, 14), (21, 13), (19, 10)],
           C["outline"])
    a.poly([(21, 7), (24, 7), (25, 10), (24, 12), (21, 11)],
           C["steel"], C["steel_light"])
    a.line(12, 8, 11, 12, C["void"], 2)
    a.line(11, 12, 14, 15, C["outline"], 2)
    a.line(14, 15, 13, 19, C["void"], 2)
    a.line(16, 9, 16, 12, C["gold_dark"])
    a.rect(4, 24, 26, 28, C["outline"])
    a.rect(5, 24, 25, 26, C["steel"])
    a.set(22, 24, C["green"])
    a.set(23, 24, C["green_dark"])
    return a


def grave_candle() -> Canvas:
    """Original pixel-art icon for a candle placed on a Gothic cemetery plinth."""
    a = Canvas()
    # Dark outline around the flame; orange outer fire with a pale inner core.
    a.poly([(15, 2), (12, 7), (9, 10), (10, 14), (13, 16), (20, 15),
            (23, 11), (20, 7), (19, 4), (17, 8)], C["outline"])
    a.poly([(15, 4), (13, 8), (11, 11), (13, 14), (18, 14),
            (21, 11), (18, 8), (17, 6)], C["ember"], C["red"])
    a.poly([(15, 7), (14, 10), (15, 12), (18, 12), (18, 10)], C["pale_gold"])
    a.rect(10, 14, 21, 24, C["outline"])
    a.rect(11, 14, 20, 23, C["wine"], C["gold_dark"])
    a.line(12, 16, 12, 21, C["red"])
    a.line(19, 16, 19, 21, C["dark"])
    a.line(13, 16, 18, 16, C["gold"])
    a.rect(7, 23, 24, 26, C["outline"])
    a.rect(8, 23, 23, 25, C["steel_dark"], C["gold_dark"])
    a.line(10, 24, 13, 24, C["gold_light"])
    a.diamond(16, 19, 2, 2, C["gold_light"], C["outline"])
    a.set(16, 19, C["pale_gold"])
    return a


def main() -> None:
    sprites = {
        "hunter_mask.png": hunter_mask,
        "cursed_talisman.png": cursed_talisman,
        "halloween_token.png": halloween_token,
        "cursed_candy.png": cursed_candy,
        "haunted_map.png": haunted_map,
        "batwing_elytra.png": batwing_elytra,
        "nocturne_sovereign_cowl.png": nocturne_sovereign_cowl,
        "grave_marker.png": grave_marker,
        "broken_grave_marker.png": broken_grave_marker,
        "grave_candle.png": grave_candle,
        "crimson_warden_helmet.png": helmet,
        "crimson_warden_chestplate.png": chestplate,
        "crimson_warden_leggings.png": leggings,
        "crimson_warden_boots.png": boots,
        "crimson_warden_sword.png": sword,
        "crimson_warden_pickaxe.png": pickaxe,
        "crimson_warden_axe.png": axe,
        "crimson_warden_shovel.png": shovel,
        "crimson_warden_hoe.png": hoe,
    }
    for filename, draw_sprite in sprites.items():
        canvas = draw_sprite()
        if len(canvas.pixels) != 32 or any(len(row) != 32 for row in canvas.pixels):
            raise ValueError(f"{filename}: expected a 32x32 sprite")
        canvas.save(filename)
        print(f"Generated {filename} (32x32, transparent PNG)")
    print(f"Generated {len(sprites)} Crimson Warden/Halloween item icons in {OUT}")


if __name__ == "__main__":
    main()
