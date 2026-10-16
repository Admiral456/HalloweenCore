#!/usr/bin/env python3
"""Generate deterministic 64x64 pixel-art glyphs for the illustrated guide book.

Only Python's standard library is used. ItemsAdder turns these PNG files into
bitmap-font glyphs so images can appear inside normal Minecraft written-book pages.
"""
from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "itemsadder/contents/warriorland_halloween/textures/font"
SIZE = 64
CLEAR = (0, 0, 0, 0)
C = {
    "ink": (16, 10, 23, 255), "shadow": (34, 16, 38, 255),
    "purple": (88, 37, 133, 255), "violet": (153, 57, 194, 255),
    "lilac": (224, 138, 255, 255), "paper": (233, 210, 161, 255),
    "green_dark": (29, 71, 43, 255), "green": (69, 133, 54, 255),
    "lime": (152, 217, 68, 255), "soil": (82, 43, 31, 255),
    "soil_light": (137, 74, 39, 255), "gold": (255, 178, 45, 255),
    "orange": (235, 98, 28, 255), "orange_light": (255, 151, 35, 255),
    "yellow": (255, 220, 82, 255), "red_dark": (89, 12, 30, 255),
    "red": (187, 22, 35, 255), "red_light": (255, 64, 54, 255),
    "stone": (76, 85, 103, 255), "stone_light": (154, 165, 177, 255),
    "stone_dark": (39, 45, 60, 255), "cyan": (47, 215, 239, 255),
    "blue": (36, 113, 177, 255), "white": (243, 235, 219, 255),
}
Pixel = tuple[int, int, int, int]


class Canvas:
    def __init__(self) -> None:
        self.pixels: list[list[Pixel]] = [[CLEAR for _ in range(SIZE)] for _ in range(SIZE)]

    def put(self, x: int, y: int, color: Pixel) -> None:
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.pixels[y][x] = color

    def rect(self, x1: int, y1: int, x2: int, y2: int, color: Pixel) -> None:
        for y in range(max(0, y1), min(SIZE - 1, y2) + 1):
            for x in range(max(0, x1), min(SIZE - 1, x2) + 1):
                self.put(x, y, color)

    def line(self, x1: int, y1: int, x2: int, y2: int, color: Pixel, width: int = 1) -> None:
        dx, dy = abs(x2 - x1), abs(y2 - y1)
        sx, sy = (1 if x1 < x2 else -1), (1 if y1 < y2 else -1)
        error, x, y = dx - dy, x1, y1
        while True:
            for yy in range(y - width // 2, y + width // 2 + 1):
                for xx in range(x - width // 2, x + width // 2 + 1):
                    self.put(xx, yy, color)
            if x == x2 and y == y2:
                break
            twice = 2 * error
            if twice > -dy:
                error -= dy
                x += sx
            if twice < dx:
                error += dx
                y += sy

    def poly(self, points: list[tuple[int, int]], color: Pixel, outline: Pixel | None = None) -> None:
        min_x = max(0, min(x for x, _ in points))
        max_x = min(SIZE - 1, max(x for x, _ in points))
        min_y = max(0, min(y for _, y in points))
        max_y = min(SIZE - 1, max(y for _, y in points))
        for y in range(min_y, max_y + 1):
            for x in range(min_x, max_x + 1):
                inside = False
                j = len(points) - 1
                for i in range(len(points)):
                    xi, yi = points[i]
                    xj, yj = points[j]
                    if (yi > y) != (yj > y):
                        crossing = (xj - xi) * (y - yi) / ((yj - yi) or 1) + xi
                        if x < crossing:
                            inside = not inside
                    j = i
                if inside:
                    self.put(x, y, color)
        if outline:
            for index, point in enumerate(points):
                nxt = points[(index + 1) % len(points)]
                self.line(point[0], point[1], nxt[0], nxt[1], outline)

    def circle(self, cx: int, cy: int, radius: int, fill: Pixel, outline: Pixel | None = None) -> None:
        outer = radius * radius
        inner = max(0, radius - 3) ** 2
        for y in range(max(0, cy - radius), min(SIZE - 1, cy + radius) + 1):
            for x in range(max(0, cx - radius), min(SIZE - 1, cx + radius) + 1):
                d = (x - cx) ** 2 + (y - cy) ** 2
                if d <= outer:
                    self.put(x, y, outline if outline and d >= inner else fill)

    def save(self, filename: str) -> None:
        raw = b"".join(b"\x00" + b"".join(bytes(pixel) for pixel in row) for row in self.pixels)
        ihdr = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
        png = (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr)
               + png_chunk(b"IDAT", zlib.compress(raw, 9)) + png_chunk(b"IEND", b""))
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / filename).write_bytes(png)


def png_chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def draw_cover() -> Canvas:
    c = Canvas()
    # Crescent moon and starry sky.
    c.circle(42, 16, 12, C["gold"], C["ink"])
    c.circle(48, 11, 10, CLEAR, CLEAR)
    c.circle(48, 11, 10, C["ink"], C["ink"])
    c.circle(21, 12, 2, C["paper"])
    c.circle(11, 25, 1, C["yellow"])
    # Castle silhouette.
    c.rect(7, 30, 57, 55, C["ink"])
    for x in (10, 22, 42, 54):
        c.rect(x, 25, x + 5, 34, C["shadow"])
        c.rect(x + 1, 23, x + 4, 28, C["ink"])
    c.rect(27, 38, 37, 55, C["purple"])
    c.rect(30, 42, 34, 53, C["ink"])
    # Pumpkin at the gate.
    c.circle(32, 48, 10, C["orange"], C["ink"])
    for x in (27, 32, 37):
        c.rect(x, 41, x + 1, 54, C["orange_light"])
    c.rect(29, 37, 35, 41, C["green_dark"])
    c.poly([(25, 46), (29, 44), (30, 49)], C["ink"])
    c.poly([(35, 44), (39, 46), (34, 49)], C["ink"])
    c.poly([(26, 51), (38, 51), (35, 55), (29, 55)], C["ink"])
    c.rect(5, 57, 59, 60, C["ink"])
    return c


def draw_soulstorm() -> Canvas:
    c = Canvas()
    # Wispy outer aura and tiny drifting souls.
    c.circle(32, 28, 25, (88, 37, 133, 40))
    for x, y, r in ((9, 19, 4), (51, 17, 3), (13, 44, 2), (53, 42, 4), (42, 8, 2)):
        c.circle(x, y, r, C["violet"])
        c.circle(x, y, max(1, r - 2), C["lilac"])
    # Ghost body and scalloped lower edge.
    c.circle(32, 25, 16, C["purple"], C["ink"])
    c.rect(16, 25, 48, 39, C["purple"])
    c.poly([(16, 37), (23, 43), (29, 37), (36, 43), (43, 37), (48, 42), (48, 47), (16, 47)], C["violet"], C["ink"])
    c.rect(20, 22, 24, 29, C["ink"])
    c.rect(38, 22, 42, 29, C["ink"])
    c.rect(21, 22, 23, 24, C["lilac"])
    c.rect(39, 22, 41, 24, C["lilac"])
    c.poly([(27, 31), (37, 31), (32, 37)], C["lilac"])
    return c


def draw_witching() -> Canvas:
    c = Canvas()
    c.circle(45, 15, 5, C["lime"])
    c.circle(45, 15, 2, C["ink"])
    c.circle(15, 20, 3, C["violet"])
    c.rect(14, 43, 50, 53, C["ink"])
    c.rect(18, 39, 46, 47, C["purple"])
    c.rect(22, 34, 42, 41, C["shadow"])
    c.rect(27, 24, 37, 35, C["paper"])
    c.poly([(13, 28), (25, 25), (33, 5), (40, 25), (54, 30), (49, 34), (17, 34)], C["ink"], C["purple"])
    c.poly([(17, 28), (25, 26), (33, 10), (39, 27), (49, 30), (45, 31), (20, 31)], C["purple"], C["violet"])
    c.rect(29, 25, 38, 29, C["lime"])
    c.rect(11, 34, 54, 38, C["ink"])
    c.rect(15, 33, 50, 35, C["purple"])
    c.rect(24, 47, 28, 49, C["violet"])
    c.rect(36, 47, 40, 49, C["violet"])
    c.rect(30, 40, 34, 43, C["lime"])
    return c


def draw_harvest() -> Canvas:
    c = Canvas()
    # Til tilled field rows.
    c.poly([(5, 42), (32, 28), (59, 42), (32, 58)], C["soil"], C["ink"])
    c.line(12, 44, 32, 33, C["soil_light"], 3)
    c.line(23, 50, 32, 45, C["soil_light"], 3)
    c.line(32, 55, 49, 45, C["soil_light"], 3)
    # Cursed hand rising out of the field.
    c.rect(27, 24, 37, 43, C["stone_dark"])
    c.rect(24, 15, 28, 30, C["stone_light"])
    c.rect(29, 11, 33, 31, C["stone"])
    c.rect(34, 15, 38, 30, C["stone_light"])
    c.rect(39, 20, 43, 31, C["stone_dark"])
    c.rect(28, 24, 38, 32, C["stone_light"])
    c.rect(30, 26, 32, 29, C["red"])
    c.rect(35, 26, 37, 29, C["red"])
    # Wheat blades and cursed glow.
    for x, y in ((11, 25), (17, 19), (48, 24), (54, 28)):
        c.line(x, 44, x, y, C["green"], 2)
        c.line(x, y + 1, x - 4, y - 2, C["gold"], 2)
        c.line(x, y + 3, x + 4, y, C["yellow"], 2)
    return c


def draw_blood_moon() -> Canvas:
    c = Canvas()
    c.circle(32, 25, 22, C["red"], C["red_dark"])
    c.circle(23, 17, 4, C["red_dark"])
    c.circle(40, 31, 5, C["red_dark"])
    c.circle(35, 14, 2, C["red_light"])
    c.rect(3, 33, 17, 37, C["shadow"])
    c.rect(43, 31, 61, 37, C["shadow"])
    # Pine silhouettes below the moon.
    for x, h in ((8, 17), (20, 12), (43, 15), (56, 21)):
        c.rect(x - 1, 64 - h, x + 1, 62, C["ink"])
        c.poly([(x, 44 - h // 5), (x - 7, 57 - h // 4), (x + 7, 57 - h // 4)], C["ink"])
        c.poly([(x, 51 - h // 5), (x - 8, 62), (x + 8, 62)], C["ink"])
    c.rect(3, 62, 61, 64, C["ink"])
    return c


def draw_pumpkin() -> Canvas:
    c = Canvas()
    c.circle(32, 36, 22, C["orange"], C["ink"])
    c.rect(29, 10, 35, 18, C["green_dark"])
    c.rect(30, 9, 34, 15, C["green"])
    for x in (20, 26, 32, 38, 44):
        c.line(x, 19, x, 51, C["orange_light"] if x in (26, 38) else C["orange"], 2)
    c.poly([(17, 29), (27, 25), (25, 36)], C["ink"])
    c.poly([(37, 25), (47, 29), (39, 36)], C["ink"])
    c.poly([(19, 40), (26, 38), (29, 43), (34, 43), (38, 38), (45, 40), (42, 48), (22, 48)], C["ink"])
    c.rect(23, 41, 26, 44, C["yellow"])
    c.rect(31, 44, 35, 46, C["yellow"])
    c.rect(39, 41, 42, 44, C["yellow"])
    c.circle(11, 23, 3, C["gold"])
    c.circle(54, 41, 2, C["yellow"])
    return c


def draw_graveyard() -> Canvas:
    c = Canvas()
    # Blue soul wisps around a stone grave.
    c.circle(13, 24, 6, C["cyan"])
    c.circle(13, 23, 3, C["blue"])
    c.circle(52, 30, 5, C["cyan"])
    c.circle(52, 29, 2, C["blue"])
    c.circle(32, 28, 17, C["stone_dark"], C["ink"])
    c.rect(15, 28, 49, 48, C["stone_dark"])
    c.rect(18, 30, 46, 45, C["stone"])
    c.poly([(18, 31), (20, 17), (27, 11), (37, 11), (44, 17), (46, 31)], C["stone"], C["stone_light"])
    c.rect(29, 16, 35, 38, C["paper"])
    c.rect(24, 22, 40, 28, C["paper"])
    c.rect(22, 45, 42, 50, C["stone_light"])
    c.rect(14, 51, 50, 55, C["ink"])
    c.rect(19, 49, 45, 52, C["stone_dark"])
    return c


def main() -> None:
    icons = {
        "guide_cover.png": draw_cover(),
        "guide_soulstorm.png": draw_soulstorm(),
        "guide_witching.png": draw_witching(),
        "guide_harvest.png": draw_harvest(),
        "guide_blood_moon.png": draw_blood_moon(),
        "guide_pumpkin.png": draw_pumpkin(),
        "guide_graveyard.png": draw_graveyard(),
    }
    for filename, canvas in icons.items():
        canvas.save(filename)
        print(f"Generated illustrated guide glyph: {OUT / filename}")


if __name__ == "__main__":
    main()
