#!/usr/bin/env python3
"""Generate deterministic, CRC-valid 16x16 Halloween vanilla block textures.

Uses only the Python standard library so CI and server admins don't need Pillow.
The outputs are included in the ItemsAdder pack under assets/minecraft/textures/block.
"""
from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "resourcepack" / "assets" / "minecraft" / "textures" / "block"
W = H = 16

PALETTE = {
    "1": (79, 25, 45, 255), "2": (128, 35, 37, 255),
    "3": (191, 55, 23, 255), "4": (237, 91, 24, 255),
    "5": (255, 145, 34, 255), "6": (255, 192, 73, 255),
    "7": (43, 77, 35, 255), "8": (73, 112, 44, 255),
    "9": (119, 145, 57, 255), "A": (28, 17, 24, 255),
    "B": (255, 216, 105, 255), "C": (242, 132, 25, 255),
    "D": (232, 204, 193, 255), "E": (65, 157, 153, 255),
    "F": (18, 12, 20, 255), "G": (180, 203, 188, 255),
    "H": (103, 151, 137, 255),
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


def candle(lit: bool) -> None:
    pixels = [[PALETTE["."] for _ in range(W)] for _ in range(H)]
    for y in range(6 if lit else 4, H):
        for x in range(5, 11):
            if lit:
                color = (94, 15, 29, 255) if x in (5, 10) else (188, 30, 46, 255) if x < 9 else (139, 20, 38, 255)
            else:
                color = (87, 15, 30, 255) if x == 5 else (164, 28, 48, 255) if x == 6 else (206, 43, 53, 255) if x in (7, 8) else (152, 22, 40, 255) if x == 9 else (80, 14, 29, 255)
            pixels[y][x] = color
    for x in range(5, 11):
        pixels[6 if lit else 4][x] = (220, 52, 58, 255) if lit else (220, 57, 65, 255)
    for y in range(7 if lit else 5, H):
        pixels[y][7] = (240, 79, 75, 255)
    if lit:
        flame = [
            "...B...",
            "..BCB..",
            "...C...",
            ".......",
        ]
        for y, row in enumerate(flame, 1):
            for x, ch in enumerate(row, 4):
                if ch == "B":
                    pixels[y][x] = (255, 221, 116, 255)
                elif ch == "C":
                    pixels[y][x] = (255, 126, 34, 255)
    else:
        pixels[3][7] = pixels[3][8] = (45, 22, 20, 255)
    save_pixels("red_candle_lit.png" if lit else "red_candle.png", pixels)


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


def red_mushroom() -> None:
    clear = PALETTE["."]
    pixels = [[clear for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, code: str) -> None:
        if 0 <= x < W and 0 <= y < H:
            pixels[y][x] = PALETTE[code]

    # Rounded crimson cap with deep red rim and pale, slightly mottled spots.
    profile = {2: (7, 8), 3: (5, 10), 4: (3, 12), 5: (2, 13),
               6: (1, 14), 7: (1, 14), 8: (2, 13), 9: (4, 11)}
    for y, (lo, hi) in profile.items():
        for x in range(lo, hi + 1):
            edge = x in (lo, hi) or y in (2, 9)
            put(x, y, "1" if edge else "2" if (x + y) % 5 else "3")
    for x, y, code in ((5, 4, "D"), (6, 4, "D"), (10, 5, "D"), (11, 5, "D"),
                       (4, 7, "D"), (5, 7, "D"), (9, 6, "D"), (10, 6, "D"),
                       (7, 3, "4"), (8, 3, "4")):
        put(x, y, code)

    # Short ivory stalk, shaded in muted sage so it reads against dark ground.
    for y in range(9, 15):
        for x in range(6, 10):
            put(x, y, "F" if x == 6 else "G" if x in (7, 8) else "H")
    for x in range(5, 11):
        put(x, 14, "1" if x in (5, 10) else "H")
    put(7, 10, "D")
    put(8, 11, "D")
    save_pixels("red_mushroom.png", pixels)


def brown_mushroom() -> None:
    clear = PALETTE["."]
    pixels = [[clear for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, code: str) -> None:
        if 0 <= x < W and 0 <= y < H:
            pixels[y][x] = PALETTE[code]

    profile = {3: (7, 8), 4: (4, 11), 5: (2, 13), 6: (1, 14),
               7: (1, 14), 8: (2, 13), 9: (4, 11)}
    for y, (lo, hi) in profile.items():
        for x in range(lo, hi + 1):
            if x in (lo, hi) or y == 9:
                code = "1"
            elif (x + y) % 4 == 0:
                code = "3"
            else:
                code = "2"
            put(x, y, code)
    for x, y, code in ((5, 5, "D"), (6, 5, "D"), (10, 5, "D"), (11, 5, "D"),
                       (4, 7, "C"), (5, 7, "D"), (9, 7, "D"), (10, 7, "C"),
                       (7, 4, "4"), (8, 4, "4")):
        put(x, y, code)
    for y in range(9, 15):
        for x in range(6, 10):
            put(x, y, "F" if x == 6 else "G" if x in (7, 8) else "H")
    for x in range(5, 11):
        put(x, 14, "1" if x in (5, 10) else "H")
    put(7, 10, "D")
    save_pixels("brown_mushroom.png", pixels)


def dead_bush() -> None:
    clear = PALETTE["."]
    pixels = [[clear for _ in range(W)] for _ in range(H)]

    def line(x1: int, y1: int, x2: int, y2: int, code: str) -> None:
        dx, dy = abs(x2 - x1), abs(y2 - y1)
        sx, sy = (1 if x1 < x2 else -1), (1 if y1 < y2 else -1)
        error, x, y = dx - dy, x1, y1
        while True:
            if 0 <= x < W and 0 <= y < H:
                pixels[y][x] = PALETTE[code]
            if x == x2 and y == y2:
                break
            twice = error * 2
            if twice > -dy:
                error -= dy
                x += sx
            if twice < dx:
                error += dx
                y += sy

    # Twisted dark burgundy branches, tiny amber thorns and dry bone tips.
    line(8, 15, 8, 7, "1")
    line(8, 10, 4, 6, "2")
    line(6, 8, 3, 9, "1")
    line(8, 12, 12, 7, "2")
    line(10, 9, 14, 9, "1")
    line(8, 13, 4, 12, "2")
    line(8, 11, 6, 4, "1")
    line(8, 7, 10, 3, "2")
    line(8, 14, 12, 13, "1")
    line(4, 6, 2, 5, "C")
    line(3, 9, 1, 10, "B")
    line(14, 9, 15, 7, "C")
    line(4, 12, 2, 14, "C")
    line(10, 3, 11, 2, "B")
    for x, y in ((7, 10), (9, 12), (5, 7), (11, 8)):
        pixels[y][x] = PALETTE["C"]
    save_pixels("dead_bush.png", pixels)


def soul_lantern() -> None:
    clear = PALETTE["."]
    pixels = [[clear for _ in range(W)] for _ in range(H)]

    def rect(x1: int, y1: int, x2: int, y2: int, code: str) -> None:
        for y in range(max(0, y1), min(H, y2 + 1)):
            for x in range(max(0, x1), min(W, x2 + 1)):
                pixels[y][x] = PALETTE[code]

    # Hanging loop, dark iron frame, pale silver edges and a teal spectral flame.
    rect(7, 1, 8, 2, "1")
    rect(6, 2, 9, 2, "2")
    rect(5, 3, 10, 4, "1")
    rect(4, 5, 11, 12, "1")
    rect(5, 5, 10, 11, "H")
    rect(6, 6, 9, 10, "E")
    rect(7, 6, 8, 8, "G")
    pixels[5][7] = PALETTE["D"]
    pixels[5][8] = PALETTE["D"]
    pixels[6][7] = PALETTE["D"]
    rect(3, 6, 4, 10, "2")
    rect(11, 6, 12, 10, "2")
    rect(4, 12, 11, 13, "1")
    rect(5, 12, 10, 12, "2")
    rect(6, 14, 9, 14, "1")
    rect(7, 15, 8, 15, "2")
    # Bright runic rivets around the glass.
    pixels[4][5] = PALETTE["B"]
    pixels[4][10] = PALETTE["B"]
    pixels[11][5] = PALETTE["B"]
    pixels[11][10] = PALETTE["B"]
    save_pixels("soul_lantern.png", pixels)


def main() -> None:
    pumpkin_side()
    pumpkin_top()
    pumpkin_face(lit=False)
    pumpkin_face(lit=True)
    candle(lit=False)
    candle(lit=True)
    cobweb()
    red_mushroom()
    brown_mushroom()
    dead_bush()
    soul_lantern()
    for path in sorted(OUT.glob("*.png")):
        print(f"Generated {path.relative_to(ROOT)} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
