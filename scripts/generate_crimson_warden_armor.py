#!/usr/bin/env python3
"""Build the Crimson Warden's bespoke crimson/obsidian armor atlas and legacy emissive overlays.

The base alpha maps define the exact vanilla equipment UV islands. We recolour those islands,
retain their silhouettes, add dark plate edges, burgundy enamel, restrained gold rivets and a
separate emissive mask for legacy ItemsAdder renderers. Keeping 64x32 avoids HD atlas issues.
"""
from pathlib import Path
from PIL import Image
import sys

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "itemsadder" / "contents" / "warriorland_halloween"
SOURCE = CONTENT / "textures" / "armor" / "crimson_warden"
RESOURCE = CONTENT / "resourcepack" / "assets" / "warriorland_halloween" / "textures" / "armor" / "crimson_warden"

PALETTE = {
    "edge": (13, 8, 17),
    "obsidian": (27, 13, 27),
    "shadow": (43, 12, 27),
    "oxblood": (75, 12, 31),
    "plate": (111, 16, 39),
    "crimson": (151, 22, 47),
    "highlight": (199, 42, 59),
    "hot": (239, 66, 73),
    "gold_shadow": (105, 61, 28),
    "gold": (177, 116, 48),
    "gold_light": (232, 181, 86),
}

def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)

def transparent_pixel() -> tuple[int, int, int, int]:
    return (0, 0, 0, 0)

def is_boundary(alpha, x: int, y: int) -> bool:
    if alpha[y][x] == 0:
        return False
    for nx, ny in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
        if nx < 0 or nx >= 64 or ny < 0 or ny >= 32 or alpha[ny][nx] == 0:
            return True
    return False

def make_layer(path: Path, layer_index: int) -> tuple[Image.Image, Image.Image]:
    if not path.is_file():
        fail(f"Missing original UV mask: {path.relative_to(ROOT)}")
    source = Image.open(path).convert("RGBA")
    if source.size != (64, 32):
        fail(f"{path.name} must be 64x32, got {source.size}")
    src = list(source.getdata())
    alpha = [[src[y * 64 + x][3] for x in range(64)] for y in range(32)]
    base_pixels = []
    glow_pixels = []

    for y in range(32):
        for x in range(64):
            a = alpha[y][x]
            if a == 0:
                base_pixels.append(transparent_pixel())
                glow_pixels.append(transparent_pixel())
                continue

            # A dark metallic edge makes armour pieces read as layered plates, not flat leather.
            edge = is_boundary(alpha, x, y)
            region = (x // 8 + y // 4 + layer_index) % 7
            grain = (x * 17 + y * 11 + x * y * 3 + layer_index * 5) % 19
            if edge:
                color = PALETTE["edge"]
            elif (x + 2 * y + layer_index * 3) % 29 in (0, 1):
                color = PALETTE["gold_light"]
            elif (x * 3 + y + layer_index) % 31 == 0:
                color = PALETTE["gold"]
            elif region in (0, 4):
                color = PALETTE["oxblood"] if grain < 10 else PALETTE["plate"]
            elif region in (1, 5):
                color = PALETTE["shadow"] if grain < 8 else PALETTE["crimson"]
            elif region == 2:
                color = PALETTE["plate"] if grain < 12 else PALETTE["highlight"]
            else:
                color = PALETTE["obsidian"] if grain < 7 else PALETTE["oxblood"]

            # Hairline crimson seams and angled plate highlights create a forged-metal finish.
            if not edge and ((x + y + layer_index) % 13 == 0):
                color = PALETTE["highlight"]
            if not edge and ((x - y + 64) % 17 == 0):
                color = PALETTE["hot"]
            base_pixels.append((*color, a))

            # Legacy ItemsAdder renderer glows only the sigils, vents and small rivets.
            # The modern equipment renderer has its own base atlas and receives an in-world
            # soul-flame aura from HalloweenCore, so no emissive feature is required there.
            ix = x % 16
            iy = y % 8
            rune = (ix in (2, 3, 12, 13) and iy in (2, 3, 4, 5)) or (
                iy in (1, 6) and ix in (4, 5, 10, 11)
            )
            seam = (x + 2 * y + layer_index) % 23 == 0
            rivet = (x * 7 + y * 5 + layer_index) % 37 == 0
            glow = (rune and (x + y + layer_index) % 3 != 0) or seam or rivet
            glow_color = PALETTE["hot"] if rune else PALETTE["gold_light"] if rivet else PALETTE["highlight"]
            glow_pixels.append((*glow_color, a if glow else 0))

    base = Image.new("RGBA", (64, 32))
    base.putdata(base_pixels)
    emissive = Image.new("RGBA", (64, 32))
    emissive.putdata(glow_pixels)
    return base, emissive

def main() -> None:
    SOURCE.mkdir(parents=True, exist_ok=True)
    RESOURCE.mkdir(parents=True, exist_ok=True)
    produced = 0
    for filename, layer_index in (("layer_1.png", 0), ("layer_2.png", 1)):
        base, emissive = make_layer(SOURCE / filename, layer_index)
        base.save(SOURCE / filename, optimize=True)
        base.save(RESOURCE / filename, optimize=True)
        emissive_name = "emissive_1.png" if filename == "layer_1.png" else "emissive_2.png"
        emissive.save(SOURCE / emissive_name, optimize=True)
        emissive.save(RESOURCE / emissive_name, optimize=True)
        visible = sum(1 for p in emissive.getdata() if p[3] > 0)
        if visible < 20:
            fail(f"{emissive_name} has too few glow pixels ({visible}); check the armor UV mask")
        produced += 1
        print(f"Generated {filename}, {emissive_name} ({visible} emissive pixels)")
    print(f"PASS Crimson Warden custom armour textures: {produced} atlases, 2 emissive layers, 64x32")

if __name__ == "__main__":
    main()
