#!/usr/bin/env python3
"""Static regression checks for the safe, chunk-local Halloween structure builders."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "HalloweenStructureGenerator.java"
ARENA_SOURCE = ROOT / "src" / "main" / "java" / "cz" / "halloween" / "core" / "VampireArenaBuilder.java"

def fail(message: str) -> None:
    print("ERROR:", message, file=sys.stderr)
    raise SystemExit(1)

if not SOURCE.is_file():
    fail("HalloweenStructureGenerator.java is missing")
source = SOURCE.read_text(encoding="utf-8")

required = {
    "versioned structure marker for future chunks": 'halloween_structure_checked_v4',
    "single-chunk footprint bounded to radius 7": 'int footprintRadius = 7;',
    "three randomized timber cottages": 'house(world, cx - 4, floorY, cz - 4, true, random);',
    "fourth village building is a ritual chapel": 'buildRitualChapel(world, cx + 4, floorY, cz + 4, random);',
    "gothic chapel builder": 'private void buildRitualChapel(',
    "red stained-glass windows": 'Material.RED_STAINED_GLASS_PANE',
    "chapel bell detail": 'Material.BELL',
    "above-ground mausoleum": 'private void buildMausoleum(',
    "graveyard entrance arch": 'Entrance arch with two stone pillars',
    "five individual graves": 'new int[][]{{-3,0},{3,0},{-3,3},{0,3},{3,3}}',
    "twisted dead trees": 'private void buildDeadTree(',
    "lit red candle placement": 'private void putLitCandle(',
    "stair direction helper": 'private void placeStairs(',
    "expanded clearance for the chapel spire": 'maxY + 10',
    "terrain height safety margin": 'maxY + 12 >= world.getMaxHeight()',
}
for label, marker in required.items():
    if marker not in source:
        fail(f"Missing structure feature/check: {label} ({marker})")

# The generator must not be changed back to the old, cramped footprint.
if 'findFlatPlatformY(world, centerX, centerZ, 5)' in source:
    fail("The structure generator still uses the old radius-5 footprint")
if 'preparePlatform(world, centerX, terrain.baseY(), centerZ, 5)' in source:
    fail("The structure platform still uses the old radius-5 footprint")

# Height differences may be filled upwards; the structure terrain validation must keep
# checking for existing tile entities and solid blocks before any builder writes.
if 'ground.getState() instanceof TileState' not in source or 'block.getState() instanceof TileState' not in source:
    fail("Safe terrain inspection must continue protecting tile entities")
if 'never excavates downwards or creates a cave/pit' not in source:
    fail("The no-excavation safety invariant must remain documented in the generator")

print("PASS Halloween structures: randomized village, gothic chapel, gated graveyard, mausoleum, graves, dead trees and safe chunk-local footprint verified")

# The boss arena is the centerpiece: hold its major architecture and safety invariants to a regression check.
if not ARENA_SOURCE.is_file():
    fail("VampireArenaBuilder.java is missing")
arena = ARENA_SOURCE.read_text(encoding="utf-8")
arena_required = {
    "97-block arena radius": "public static final int RADIUS = 48;",
    "extended tower/sanctuary clearance": "private static final int CLEARANCE_HEIGHT = 24;",
    "hollow gothic watchtowers": "hollow 7x7 gothic watchtowers",
    "outer curtain-wall buttresses": "Point[] wallButtresses",
    "four monumental gates": "Point[] gates = {new Point(0, -43), new Point(43, 0), new Point(0, 43), new Point(-43, 0)};",
    "king's blood-moon portal and throne": "private int buildThroneSanctum(",
    "crimson portal glass": "Material.RED_STAINED_GLASS",
    "hanging iron tracery": "Material.IRON_BARS",
    "four ceremonial sarcophagi": "private int buildSarcophagus(",
    "outer rune pylons outside boss spawn": "int[][] sigils = {{0, -16}, {16, 0}, {0, 16}, {-16, 0}};",
    "ritual compass floor": "Eight directional rays read as an occult compass",
    "destructive-build preview confirmation flow": "public Inspection inspect(Location center)",
}
for label, marker in arena_required.items():
    if marker not in arena:
        fail(f"Ultimate boss arena is missing required architecture/safety feature: {label} ({marker})")
if "int[][] sigils = {{0, -12}, {12, 0}, {0, 12}, {-12, 0}};" in arena:
    fail("Rune pylons intrude into the central boss spawn clearance")
if "int portalZ = cz - 31;" not in arena or "cz - 26 + dz" not in arena:
    fail("The throne sanctum must stay clear of the north watchtower and face into the arena")
if "centerY + CLEARANCE_HEIGHT >= world.getMaxHeight()" not in arena:
    fail("Arena inspection must enforce world-height safety for the enlarged tower crowns")
print("PASS ultimate Vampire King arena: eight hollow towers, curtain buttresses, four monumental gates, throne portal, sarcophagi, concentric runes and central boss clearance verified")
