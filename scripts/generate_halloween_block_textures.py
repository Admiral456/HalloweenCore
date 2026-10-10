#!/usr/bin/env python3
"""Generate deterministic Halloween block textures and pixel-art grave markers.

Uses only the Python standard library. Block textures are 16x16; ItemsAdder grave
marker sprites are 32x32 RGBA textures generated into the same resource-pack build.
"""
from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "itemsadder" / "contents" / "warriorland_halloween"
OUT = CONTENT / "resourcepack" / "assets" / "minecraft" / "textures" / "block"
ITEM_OUT = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "item"
W = H = 16

PALETTE = {
    "1": (79, 25, 45, 255), "2": (128, 35, 37, 255),
    "3": (191, 55, 23, 255), "4": (237, 91, 24, 255),
    "5": (255, 145, 34, 255), "6": (255, 192, 73, 255),
    "7": (43, 77, 35, 255), "8": (73, 112, 44, 255),
    "9": (119, 145, 57, 255), "A": (28, 17, 24, 255),
    "B": (255, 216, 105, 255), "C": (242, 132, 25, 255),
    ".": (0, 0, 0, 0),
}


def png_chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def save_pixels(name: str, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    if len(pixels) != H or any(len(row) != W for row in pixels):
        raise ValueError(f"{name}: texture must be exactly {W}x{H}")
    raw = b"".join(b"\x00" + b"".join(bytes(pixel) for pixel in row) for row in pixels)
    ihdr = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", zlib.compress(raw, 9)) + png_chunk(b"IEND", b"")
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_bytes(png)


def save_sized_pixels(out_dir: Path, name: str, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    """Write an RGBA PNG at the dimensions provided by the pixel-art canvas."""
    height = len(pixels)
    width = len(pixels[0]) if height else 0
    if width < 1 or height < 1 or any(len(row) != width for row in pixels):
        raise ValueError(f"{name}: inconsistent or empty pixel canvas")
    raw = b"".join(b"\x00" + b"".join(bytes(pixel) for pixel in row) for row in pixels)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", zlib.compress(raw, 9)) + png_chunk(b"IEND", b"")
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / name).write_bytes(png)


def seasonal_mushroom(red: bool) -> None:
    """Draw a creepy, shaded mushroom sprite while preserving transparent corners."""
    clear = (0, 0, 0, 0)
    pixels = [[clear for _ in range(16)] for _ in range(16)]
    cap_rows = {1: (7, 8), 2: (5, 10), 3: (3, 12), 4: (2, 13), 5: (2, 13), 6: (3, 12), 7: (5, 10)}
    for y, (left, right) in cap_rows.items():
        for x in range(left, right + 1):
            edge = x in (left, right) or y in (1, 7)
            if red:
                color = (43, 20, 37, 255) if edge else (143, 34, 48, 255)
                if not edge and (x + y) % 7 == 0:
                    color = (235, 102, 96, 255)
                if (x, y) in {(5, 3), (10, 3), (4, 5), (11, 5)}:
                    color = (255, 185, 126, 255)
                if (x, y) in {(6, 4), (9, 4)}:
                    color = (36, 20, 34, 255)
            else:
                color = (30, 30, 38, 255) if edge else (91, 73, 57, 255)
                if not edge and (x * 2 + y) % 5 == 0:
                    color = (123, 99, 65, 255)
                if (x, y) in {(5, 3), (10, 3), (4, 5), (11, 5)}:
                    color = (82, 137, 81, 255)
                if (x, y) in {(6, 4), (9, 4)}:
                    color = (37, 22, 32, 255)
            pixels[y][x] = color
    for x in range(5, 11):
        pixels[8][x] = (48, 25, 39, 255) if red else (40, 37, 43, 255)
    for y in range(8, 15):
        left, right = (6, 9) if y < 11 else (5, 10)
        for x in range(left, right + 1):
            if x == left:
                color = (65, 48, 57, 255)
            elif x == right:
                color = (40, 29, 42, 255)
            else:
                color = (171, 151, 130, 255) if red else (141, 145, 116, 255)
            if (x, y) in {(7, 10), (8, 12), (6, 13)}:
                color = (199, 85, 87, 255) if red else (99, 170, 103, 255)
            pixels[y][x] = color
    save_pixels("red_mushroom.png" if red else "brown_mushroom.png", pixels)


def dead_bush() -> None:
    """A dry, thorny burgundy-grey shrub for sandy/deserted patches."""
    clear = (0, 0, 0, 0)
    pixels = [[clear for _ in range(16)] for _ in range(16)]

    def line(a: tuple[int, int], b: tuple[int, int], color: tuple[int, int, int, int]) -> None:
        x0, y0 = a
        x1, y1 = b
        dx, dy = abs(x1 - x0), abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx - dy
        while True:
            if 0 <= x0 < 16 and 0 <= y0 < 16:
                pixels[y0][x0] = color
            if x0 == x1 and y0 == y1:
                break
            twice = 2 * err
            if twice > -dy:
                err -= dy
                x0 += sx
            if twice < dx:
                err += dx
                y0 += sy

    branch = (108, 76, 79, 255)
    highlight = (169, 117, 103, 255)
    for a, b in [((8, 15), (8, 2)), ((8, 12), (3, 7)), ((8, 10), (13, 5)),
                 ((8, 8), (5, 3)), ((8, 13), (12, 10)), ((8, 6), (10, 2)),
                 ((6, 10), (2, 10)), ((10, 8), (14, 8))]:
        line(a, b, branch)
    for a, b in [((8, 15), (8, 2)), ((8, 12), (3, 7)), ((8, 10), (13, 5)),
                 ((8, 8), (5, 3)), ((8, 13), (12, 10))]:
        for x, y in (a, b):
            if 0 <= x < 16 and 0 <= y < 16:
                pixels[y][x] = highlight
    thorn_points = [(6, 12), (5, 11), (4, 10), (5, 9), (3, 8), (5, 5), (6, 6),
                    (10, 10), (11, 8), (12, 7), (12, 4), (10, 5), (7, 4), (9, 3), (13, 8)]
    for x, y in thorn_points:
        pixels[y][x] = (42, 30, 43, 255)
    pixels[14][7] = (82, 42, 55, 255)
    pixels[14][8] = (126, 58, 61, 255)
    pixels[13][9] = (192, 91, 73, 255)
    save_pixels("dead_bush.png", pixels)


def gravestone_texture(cracked: bool) -> None:
    """Draw a 32x32 vertical gravestone sprite for ItemDisplay world decorations."""
    clear = (0, 0, 0, 0)
    pixels = [[clear for _ in range(32)] for _ in range(32)]

    def put(x: int, y: int, color: tuple[int, int, int, int]) -> None:
        if 0 <= x < 32 and 0 <= y < 32:
            pixels[y][x] = color

    def rect(x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int, int]) -> None:
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                put(xx, yy, color)

    outline = (21, 23, 33, 255)
    shadow = (42, 46, 58, 255)
    stone = (76, 84, 99, 255)
    light = (122, 132, 143, 255)
    gleam = (174, 177, 172, 255)
    deep = (30, 30, 43, 255)
    moss = (50, 76, 56, 255)
    ember = (204, 59, 59, 255) if cracked else (225, 94, 42, 255)
    glow = (255, 173, 83, 255) if not cracked else (184, 108, 233, 255)

    profile = {
        3: (15, 16), 4: (14, 17), 5: (12, 19), 6: (10, 21),
        7: (9, 22), 8: (8, 23), 9: (8, 23), 10: (8, 23),
        11: (8, 23), 12: (8, 23), 13: (8, 23), 14: (8, 23),
        15: (8, 23), 16: (8, 23), 17: (8, 23), 18: (8, 23),
        19: (8, 23), 20: (8, 23), 21: (8, 23), 22: (8, 23),
        23: (8, 23), 24: (8, 23), 25: (9, 22), 26: (9, 22),
    }
    for y, (left, right) in profile.items():
        for x in range(left, right + 1):
            if x in (left, right) or y in (3, 26):
                color = outline
            elif x == left + 1:
                color = gleam if y < 12 else light
            elif x >= right - 1:
                color = shadow
            else:
                color = stone if (x + 2 * y) % 7 else light
            put(x, y, color)

    rect(10, 8, 21, 22, deep)
    for x in range(11, 21):
        put(x, 8, shadow)
        put(x, 22, shadow)
    for y in range(9, 22):
        put(10, y, light if y < 14 else shadow)
        put(21, y, outline)
    for x, y in [(12, 9), (13, 10), (12, 11), (19, 20), (18, 21), (20, 19)]:
        put(x, y, light if not cracked else (83, 95, 119, 255))

    if cracked:
        for y in range(10, 15):
            for x in range(14, 19):
                dx, dy = x - 16, y - 12
                if dx * dx + dy * dy <= 5:
                    put(x, y, glow if dx * dx + dy * dy <= 2 else (91, 59, 127, 255))
        for a, b in [((12, 13), (14, 16)), ((14, 16), (12, 19)),
                     ((19, 11), (17, 16)), ((17, 16), (19, 19))]:
            x0, y0 = a
            x1, y1 = b
            steps = max(abs(x1 - x0), abs(y1 - y0))
            for i in range(steps + 1):
                x = round(x0 + (x1 - x0) * i / max(1, steps))
                y = round(y0 + (y1 - y0) * i / max(1, steps))
                put(x, y, outline)
    else:
        rect(14, 10, 17, 21, outline)
        rect(12, 13, 19, 16, outline)
        rect(15, 11, 16, 20, glow)
        rect(13, 14, 18, 15, glow)
        put(15, 12, (255, 226, 135, 255))
        put(16, 17, ember)
        rect(12, 19, 13, 19, light)
        rect(18, 19, 19, 19, light)

    for x, y in [(10, 23), (11, 24), (12, 23), (19, 24), (20, 23), (21, 24)]:
        put(x, y, moss)
    for x, y in [(9, 12), (9, 18), (22, 14), (22, 20), (11, 6), (20, 6)]:
        put(x, y, gleam if not cracked else light)
    rect(6, 27, 25, 28, outline)
    rect(5, 29, 26, 30, shadow)
    rect(7, 29, 24, 29, light)
    for x in (7, 11, 16, 21, 25):
        put(x, 30, outline)
    save_sized_pixels(ITEM_OUT, "grave_marker_cracked.png" if cracked else "grave_marker.png", pixels)


def rows_to_pixels(rows: list[str]) -> list[list[tuple[int, int, int, int]]]:
    if len(rows) != H or any(len(row) != W for row in rows):
        raise ValueError("pixel-art rows must be 16x16")
    return [[PALETTE[ch] for ch in row] for row in rows]


def pumpkin_side() -> None:
    rows = [
        "1111111111111111",
        "1223231223231221",
        "2334342334342332",
        "2345432345432342",
        "2345432345432342",
        "2334342334342332",
        "1223231223231221",
        "2334342334342332",
        "2345432345432342",
        "2345432345432342",
        "2334342334342332",
        "1223231223231221",
        "2334342334342332",
        "2345432345432342",
        "1223231223231221",
        "1111111111111111",
    ]
    save_pixels("pumpkin_side.png", rows_to_pixels(rows))


def pumpkin_top() -> None:
    rows = []
    for y in range(H):
        row = ""
        for x in range(W):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 1.6:
                c = "8" if (x + y) % 2 else "7"
            elif d < 3.4:
                c = "9" if (x + y) % 3 else "8"
            elif d < 5.8:
                c = "5" if (x + y) % 4 else "4"
            elif d < 7.5:
                c = "3" if (x + y) % 3 else "2"
            else:
                c = "1"
            row += c
        rows.append(row)
    save_pixels("pumpkin_top.png", rows_to_pixels(rows))


def pumpkin_face(lit: bool) -> None:
    dark = {(4, 4), (5, 4), (4, 5), (5, 5), (10, 4), (11, 4), (10, 5), (11, 5),
            (5, 10), (6, 11), (7, 11), (8, 11), (9, 11), (10, 10), (6, 12), (7, 12), (8, 12), (9, 12)}
    highlights = {(3, 3), (9, 3), (12, 3), (3, 7), (12, 7), (3, 9), (12, 9)}
    rows = []
    for y in range(H):
        row = ""
        for x in range(W):
            if x in (0, 15) or y in (0, 15):
                c = "1"
            elif x in (1, 14) or y in (1, 14):
                c = "2"
            elif (x, y) in dark:
                c = "B" if lit else "A"
            elif (x, y) in highlights:
                c = "6" if lit else "5"
            else:
                c = ("5" if (x + y) % 4 else "6") if lit else ("4" if (x + y) % 5 else "5")
            row += c
        rows.append(row)
    save_pixels("jack_o_lantern.png" if lit else "pumpkin_face.png", rows_to_pixels(rows))


def candle() -> None:
    """Draw the texture referenced by both lit and unlit vanilla candle models."""
    pixels = [[PALETTE["."] for _ in range(W)] for _ in range(H)]
    body = {
        5: (84, 11, 28, 255),
        6: (139, 18, 39, 255),
        7: (181, 31, 49, 255),
        8: (168, 24, 44, 255),
        9: (118, 16, 34, 255),
        10: (67, 10, 24, 255),
    }
    for y in range(4, H):
        for x, color in body.items():
            pixels[y][x] = color
    # Uneven wax rim and a few glossy wax pixels; flame is separate block-model geometry.
    for x, color in enumerate([
        (92, 14, 30, 255), (160, 25, 45, 255), (218, 48, 63, 255),
        (194, 36, 53, 255), (126, 18, 35, 255), (71, 11, 24, 255),
    ], 5):
        pixels[4][x] = color
    pixels[3][7] = pixels[3][8] = (45, 22, 20, 255)
    pixels[7][7] = (240, 79, 75, 255)
    pixels[8][7] = (232, 65, 70, 255)
    pixels[12][8] = (198, 35, 50, 255)
    save_pixels("red_candle.png", pixels)


def cobweb() -> None:
    clear = (0, 0, 0, 0)
    dark = (105, 18, 38, 255)
    light = (220, 91, 101, 255)
    pixels = [[clear for _ in range(W)] for _ in range(H)]
    for i in range(W):
        for x, y in ((i, 0), (0, i), (i, i), (15 - i, i)):
            if 0 <= x < W and 0 <= y < H:
                pixels[y][x] = dark
    for radius in (3, 5, 7):
        low, high = max(0, 8 - radius), min(15, 8 + radius)
        for x in range(low, high + 1):
            for y in (low, high):
                if (x + y + radius) % 4:
                    pixels[y][x] = dark
        for y in range(low, high + 1):
            for x in (low, high):
                if (x + y + radius) % 4:
                    pixels[y][x] = dark
    for x, y in ((0, 0), (3, 3), (5, 5), (7, 7), (8, 8), (10, 5), (5, 10), (15, 0), (0, 15), (15, 15)):
        pixels[y][x] = light
    save_pixels("cobweb.png", pixels)


def main() -> None:
    pumpkin_side()
    pumpkin_top()
    pumpkin_face(lit=False)
    pumpkin_face(lit=True)
    candle()
    cobweb()
    seasonal_mushroom(red=True)
    seasonal_mushroom(red=False)
    dead_bush()
    gravestone_texture(cracked=False)
    gravestone_texture(cracked=True)
    # Remove a legacy texture name not referenced by vanilla 1.21.10 candle models.
    (OUT / "red_candle_lit.png").unlink(missing_ok=True)
    for path in sorted(OUT.glob("*.png")) + sorted(ITEM_OUT.glob("grave_marker*.png")):
        print(f"Generated {path.relative_to(ROOT)} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
