#!/usr/bin/env python3
"""Static regression guard for Halloween event behavior."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
EVENT = (ROOT / "src/main/java/cz/halloween/core/HalloweenEventManager.java").read_text(encoding="utf-8")
MOBS = (ROOT / "src/main/java/cz/halloween/core/HalloweenMobManager.java").read_text(encoding="utf-8")
STRUCTURES = (ROOT / "src/main/java/cz/halloween/core/HalloweenStructureManager.java").read_text(encoding="utf-8")
CONFIG = (ROOT / "src/main/resources/config.yml").read_text(encoding="utf-8")
COMMAND = (ROOT / "src/main/java/cz/halloween/core/HalloweenCommand.java").read_text(encoding="utf-8")
BOOK = (ROOT / "src/main/java/cz/halloween/core/HalloweenGuideBook.java").read_text(encoding="utf-8")
FONT_CONFIG = (ROOT / "itemsadder/contents/warriorland_halloween/guide_font_images.yml").read_text(encoding="utf-8")
FONT_GENERATOR = (ROOT / "scripts/generate_halloween_guide_textures.py").read_text(encoding="utf-8")
FONT_TEXTURES = ROOT / "itemsadder/contents/warriorland_halloween/textures/font"
checks = {
    "all eligible players receive event waves": 'getInt("random-events.players-per-surge"' not in EVENT and "for (Player target : players)" in EVENT,
    "event spawning is capped at 25 blocks": 'getInt("random-events.spawn-radius-blocks", 25)' in MOBS and "spawn-radius-blocks: 25" in CONFIG,
    "safe location search never reads unloaded chunk terrain": "if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;" in MOBS,
    "event spawn radius includes vertical distance": "location.distanceSquared(origin) > allowedDistance * allowedDistance" in MOBS and "double allowedDistance = maximumRadius + 1.0D;" in MOBS,
    "event mobs have a bounded lifetime": "scheduleEventMobCleanup(living)" in MOBS and "event-mob-lifetime-seconds: 45" in CONFIG,
    "blood moon forces night and displays red particles": "world.setTime(13_000L)" in EVENT and "Particle.DUST" in EVENT and "blood-moon-damage-multiplier: 3.0" in CONFIG,
    "cursed harvest scans actual FARMLAND": "Material.FARMLAND" in EVENT and "spawnFromLoadedFarmland" in EVENT,
    "harvest mobs spawn beside fields and target nearest players": "spawnEventMobAt(anchor, mobId, target)" in EVENT and "nearestEligiblePlayer(anchor, radius)" in EVENT,
    "pumpkin apocalypse adds pumpkins in loaded natural chunks and per-event cache": "decorateLoadedChunksWithPumpkins" in EVENT and "scannedPumpkinChunkKeys" in EVENT and "Material.JACK_O_LANTERN" in EVENT,
    "graveyard event uses recorded cemetery sites": "getRecordedGraveyardLocations" in STRUCTURES and "spawnFromRecordedGraveyards" in EVENT,
    "event mobs receive explicit targets": "mobEntity.setTarget(target)" in MOBS,
    "written in-game guide book command exists": "Material.WRITTEN_BOOK" in BOOK and 'equalsIgnoreCase("guide")' in COMMAND,
    "illustrated book has ItemsAdder bitmap glyphs for cover and all six events": all(
        symbol in BOOK and symbol in FONT_CONFIG
        for symbol in (r"\uE1F0", r"\uE1F1", r"\uE1F2", r"\uE1F3", r"\uE1F4", r"\uE1F5", r"\uE1F6")
    ) and all(name in FONT_CONFIG and name in FONT_GENERATOR for name in (
        "guide_cover", "guide_soulstorm", "guide_witching", "guide_harvest",
        "guide_blood_moon", "guide_pumpkin", "guide_graveyard"
    )),
    "illustrated book glyphs are generated as 64x64 PNGs": all(
        (FONT_TEXTURES / name).is_file()
        and (FONT_TEXTURES / name).read_bytes()[12:16] == b"IHDR"
        and __import__("struct").unpack(">II", (FONT_TEXTURES / name).read_bytes()[16:24]) == (64, 64)
        for name in (
            "guide_cover.png", "guide_soulstorm.png", "guide_witching.png",
            "guide_harvest.png", "guide_blood_moon.png", "guide_pumpkin.png", "guide_graveyard.png"
        )
    ),
}
failed=[name for name, ok in checks.items() if not ok]
for name, ok in checks.items(): print(("PASS" if ok else "FAIL")+" - "+name)
if failed: raise SystemExit(f"{len(failed)} event behavior checks failed")
print(f"All {len(checks)} event behavior checks passed.")
