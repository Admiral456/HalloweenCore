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
checks = {
    "all eligible players receive event waves": 'getInt("random-events.players-per-surge"' not in EVENT and "for (Player target : players)" in EVENT,
    "event spawning is capped at 25 blocks": 'getInt("random-events.spawn-radius-blocks", 25)' in MOBS and "spawn-radius-blocks: 25" in CONFIG,
    "event mobs have a bounded lifetime": "scheduleEventMobCleanup(living)" in MOBS and "event-mob-lifetime-seconds: 45" in CONFIG,
    "blood moon forces night and displays red particles": "world.setTime(13_000L)" in EVENT and "Particle.DUST" in EVENT and "blood-moon-damage-multiplier: 3.0" in CONFIG,
    "cursed harvest scans actual FARMLAND": "Material.FARMLAND" in EVENT and "spawnFromLoadedFarmland" in EVENT,
    "harvest mobs spawn beside fields and target nearest players": "spawnEventMobAt(anchor, mobId, target)" in EVENT and "nearestEligiblePlayer(anchor, radius)" in EVENT,
    "pumpkin apocalypse adds pumpkins in loaded natural chunks and per-event cache": "decorateLoadedChunksWithPumpkins" in EVENT and "scannedPumpkinChunkKeys" in EVENT and "Material.JACK_O_LANTERN" in EVENT,
    "graveyard event uses recorded cemetery sites": "getRecordedGraveyardLocations" in STRUCTURES and "spawnFromRecordedGraveyards" in EVENT,
    "event mobs receive explicit targets": "mobEntity.setTarget(target)" in MOBS,
    "written in-game guide book command exists": "Material.WRITTEN_BOOK" in BOOK and 'equalsIgnoreCase("guide")' in COMMAND,
}
failed=[name for name, ok in checks.items() if not ok]
for name, ok in checks.items(): print(("PASS" if ok else "FAIL")+" - "+name)
if failed: raise SystemExit(f"{len(failed)} event behavior checks failed")
print(f"All {len(checks)} event behavior checks passed.")
