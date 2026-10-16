#!/usr/bin/env python3
"""Static contract checks for the custom Halloween achievement system and book command."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = (ROOT / "src/main/java/cz/halloween/core/HalloweenDataManager.java").read_text(encoding="utf-8")
CORE = (ROOT / "src/main/java/cz/halloween/core/HalloweenCore.java").read_text(encoding="utf-8")
COMMAND = (ROOT / "src/main/java/cz/halloween/core/HalloweenCommand.java").read_text(encoding="utf-8")
STRUCTURES = (ROOT / "src/main/java/cz/halloween/core/HalloweenStructureManager.java").read_text(encoding="utf-8")
VILLAGE = (ROOT / "src/main/java/cz/halloween/core/HalloweenVillageDiscoveryManager.java").read_text(encoding="utf-8")
VAMPIRE = (ROOT / "src/main/java/cz/halloween/core/HalloweenVampireEncounterManager.java").read_text(encoding="utf-8")
GRAVE = (ROOT / "src/main/java/cz/halloween/core/HalloweenGraveEncounterManager.java").read_text(encoding="utf-8")
NICK = (ROOT / "src/main/java/cz/halloween/core/HalloweenHeadlessNickManager.java").read_text(encoding="utf-8")
MOBS = (ROOT / "src/main/java/cz/halloween/core/HalloweenMobManager.java").read_text(encoding="utf-8")
BOOK = (ROOT / "src/main/java/cz/halloween/core/HalloweenGuideBook.java").read_text(encoding="utf-8")
DOCS = (ROOT / "docs/HALLOWEEN_PLAYER_GUIDE.md").read_text(encoding="utf-8")
MANAGER = (ROOT / "src/main/java/cz/halloween/core/HalloweenAchievementManager.java").read_text(encoding="utf-8")
PLUGIN = (ROOT / "src/main/resources/plugin.yml").read_text(encoding="utf-8")

checks = {
    "achievement system lifecycle": (
        "new HalloweenAchievementManager(this)" in CORE
        and "registerEvents(achievementManager, this)" in CORE
        and "achievementManager.start()" in CORE
        and "achievementManager.stop()" in CORE
    ),
    "achievement data is persisted": (
        'data.getStringList(base + ".achievements")' in DATA
        and 'data.set(base + ".achievements"' in DATA
        and "public boolean unlockAchievement(UUID uuid, String achievementId)" in DATA
    ),
    "recorded village and graveyard discoveries": (
        "getRecordedStructureLocations(World world, String requestedType)" in STRUCTURES
        and 'nearAnyRecordedStructure(structures, world, playerLocation, "VILLAGE"' in MANAGER
        and 'nearAnyRecordedStructure(structures, world, playerLocation, "GRAVEYARD"' in MANAGER
        and 'award(player, "find_village")' in VILLAGE
    ),
    "vampire achievement is awarded only in the real encounter": (
        'award(killer, "boss_vampire_king")' in VAMPIRE
        and VAMPIRE[VAMPIRE.rfind("public void onBossDeath"):].find("if (testEncounter)") <
            VAMPIRE[VAMPIRE.rfind("public void onBossDeath"):].find('award(killer, "boss_vampire_king")')
    ),
    "grave guardian achievement ignores admin test rituals": (
        "encounter.boss.getUniqueId().equals(deadId) && !encounter.adminTest" in GRAVE
        and 'award(killer, "boss_grave_guardian")' in GRAVE
    ),
    "Headless Nick death hook exists": 'award(killer, "boss_headless_nick")' in NICK,
    "Blood Moon captain has a marker and achievement hook": (
        '"blood_moon_captain"' in MOBS and 'award(killer, "boss_blood_moon_captain")' in MANAGER
    ),
    "full Crimson Warden armor set uses canonical item IDs": all(value in MANAGER for value in (
        "gear_crimson_warden_helmet", "gear_crimson_warden_chestplate",
        "gear_crimson_warden_leggings", "gear_crimson_warden_boots",
        'award(player, "full_crimson_warden_set")'
    )),
    "master hunter requires all four main boss achievements": (
        "BOSS_ACHIEVEMENT_IDS.stream().allMatch" in MANAGER and 'award(player, "boss_hunter")' in MANAGER
    ),
    "achievement command and restricted tutorial-book shortcut exist": (
        'equalsIgnoreCase("achievements")' in COMMAND
        and "getAchievementCatalog()" in COMMAND
        and 'parts[0].equalsIgnoreCase("/give")' in COMMAND
        and 'parts[1].equalsIgnoreCase("tutorial_book")' in COMMAND
        and "Bukkit.getPlayerExact(parts[2])" in COMMAND
        and 'HalloweenGuideBook.give(plugin, target)' in COMMAND
        and "guide|achievements" in PLUGIN
    ),
    "guide and docs include achievement instructions": (
        "14 • ÚSPĚCHY" in BOOK and "/halloween achievements" in BOOK
        and "## Úspěchy" in DOCS
    ),
}
failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + " - " + name)
if failed:
    raise SystemExit(f"{len(failed)} achievement contract checks failed")
print(f"All {len(checks)} achievement contract checks passed.")
