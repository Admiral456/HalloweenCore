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


def main() -> None:
    pumpkin_side()
    pumpkin_top()
    pumpkin_face(lit=False)
    pumpkin_face(lit=True)
    candle(lit=False)
    candle(lit=True)
    cobweb()
    for path in sorted(OUT.glob("*.png")):
        print(f"Generated {path.relative_to(ROOT)} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
