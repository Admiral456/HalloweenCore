#!/usr/bin/env python3
"""Static contract checks for critical HalloweenCore runtime rules.

These checks catch regressions in command routing, world separation, migration defaults,
and scheduler/rollback safeguards. They are not a substitute for live Paper/Purpur tests.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required source/config file: {relative}")
    return path.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


core = read("src/main/java/cz/halloween/core/HalloweenCore.java")
command = read("src/main/java/cz/halloween/core/HalloweenCommand.java")
structures = read("src/main/java/cz/halloween/core/HalloweenStructureManager.java")
decorator = read("src/main/java/cz/halloween/core/HalloweenWorldDecorator.java")
mobs = read("src/main/java/cz/halloween/core/HalloweenMobManager.java")
events = read("src/main/java/cz/halloween/core/HalloweenEventManager.java")
quests = read("src/main/java/cz/halloween/core/HalloweenQuestManager.java")
passives = read("src/main/java/cz/halloween/core/HalloweenPassiveEffectManager.java")
config = read("src/main/resources/config.yml")
plugin_yml = read("src/main/resources/plugin.yml")
readme = read("README.md")

# Automatic world generation must fail closed and never touch the named lobby.
for marker in (
    'world.getName().equalsIgnoreCase("Spawn")',
    'automatic-world-generation.enabled',
    'automatic-world-generation.allowed-worlds',
):
    if marker not in core:
        fail(f"Automatic world safety guard is missing: {marker}")
if "automatic-world-generation:\n  enabled: true\n  allowed-worlds: []" not in config:
    fail("Default automatic world allow-list must be empty (fail-closed)")
if "event.isNewChunk()" not in structures:
    fail("Natural structures must only be attempted on newly generated chunks")
if "plugin.isAutomaticHalloweenWorld(event.getWorld())" not in structures:
    fail("Natural structures are missing the explicit world allow-list guard")
if "plugin.isAutomaticHalloweenWorld(event.getWorld())" not in decorator:
    fail("Chunk decorations are missing the explicit world allow-list guard")
if "world-decorations.decorate-existing-chunks" not in decorator or "decorate-existing-chunks: false" not in config:
    fail("Existing chunks must not be retrofitted by default")
if "halloween_auto_structure_reserved_v1" not in structures or "isReservedForNaturalGeneration" not in decorator:
    fail("Candidate structures and chunk decoration need race protection")

# Manual FORCE operations are distinct from auto generation, bounded, batched and recoverable.
for marker in (
    "implements Listener",
    "Phase { CLEAR, SUPPORT, PLACE, ROLLBACK }",
    "runTaskTimer(plugin, () -> tick(job), 1L, 1L)",
    "structures.max-block-changes",
    "structures.blocks-per-tick",
    "rollbackStates",
    "insideWorldBorder(",
    "Blocks inside the declared footprint may be replaced",
):
    if marker not in structures:
        fail(f"Manual structure safety contract is missing: {marker}")
for alias in ("hřbitov", "vesnice", "aréna", "vampirearena", "goblinarena"):
    if alias not in command and alias not in structures:
        fail(f"Missing requested structure command: {alias}")
if 'args[0].equalsIgnoreCase("spawn")' not in command:
    fail("The /halloween spawn command is not routed")
if re.search(r"(?m)^  spawn:", plugin_yml):
    fail("Do not register a bare /spawn root command that may conflict with another server plugin")
if "setVampireArena(sender)" in command or "buildVampireArena(sender, args)" in command:
    fail("Legacy center-selection / confirmation arena commands must not be active")
if "createAdminRewardItems()" not in read("src/main/java/cz/halloween/core/HalloweenRewardManager.java"):
    fail("Admin give-all must include all configured shop items and portable relics")
if "args[2].equalsIgnoreCase(\"all\")" not in command:
    fail("The /halloween give <player> all flow is missing")
relics = read("src/main/java/cz/halloween/core/HalloweenRelicAbilityManager.java")
for marker in ("BATWING_COOLDOWN_MS", "COWL_COOLDOWN_MS", "PlayerToggleSneakEvent",
               "PotionEffectType.NIGHT_VISION", "useBatwingDash", "useCowlPulse"):
    if marker not in relics:
        fail(f"Portable relic ability contract is missing: {marker}")
if "getRelicAbilityManager()" not in core or "relicAbilityManager.start()" not in core:
    fail("Portable relic ability manager is not wired into the plugin lifecycle")

# All eight requested base mob IDs must support an administrator test-spawn path.
for mob_id in (
    "cursed-zombie", "void-reaper", "gravekeeper", "frost-stalker",
    "blood-spider", "pumpkin-wraith", "hex-witch", "nightmare",
):
    if mob_id not in mobs:
        fail(f"Missing special-mob implementation or test-spawn ID: {mob_id}")
if "spawnManualMob(Player player, String input)" not in mobs:
    fail("Manual admin mob spawn implementation is missing")
if "getManualMobIds()" not in command or "spawnManualMob(player, args[1])" not in command:
    fail("Command router is not connected to the manual mob spawn implementation")

# Blood Moon has one bounded damage multiplication in the central damage listener.
if mobs.count("event.setDamage(event.getDamage() * multiplier);") != 1:
    fail("Blood Moon must apply its damage multiplier at exactly one central location")
if "isEligibleGameplayWorld(target.getWorld())" not in mobs:
    fail("Mob damage mechanics are missing gameplay-world filtering")
if "isAutomaticHalloweenWorld(player.getWorld())" not in events:
    fail("Scheduled event targeting must use the explicit automatic-world allow-list")
if "isAutomaticHalloweenWorld(entity.getWorld())" not in mobs:
    fail("Natural special-mob conversion must use the explicit automatic-world allow-list")

# The current spec is one additional heart (+2 max health); old installs migrate once.
if "bonus-health: 2.0" not in config:
    fail("Default cursed talisman bonus must be +2 max health (one heart)")
if "migrateCursedTalismanHealth()" not in core or "migrations.cursed-talisman-single-heart-v1" not in core:
    fail("Legacy talisman config must migrate once to the current one-heart spec")
if "Attribute.MAX_HEALTH" not in passives or "talismanHealthKey" not in passives:
    fail("Talisman health modifier must use a named, reconciled attribute modifier")
if "setHealth(Math.min(player.getHealth()" not in passives:
    fail("Talisman removal must safely clamp current health after removing the modifier")

# Save/award logic remains present; static checks do not claim full quest gameplay success.
if "completeStoryQuest(playerId, quest.id())" not in quests or "plugin.getDataManager().save();" not in quests:
    fail("Story quest completion must be idempotent and persisted")

for item in (
    "/halloween spawn <hřbitov|vesnice|aréna|vampirearena|goblinarena>",
    "/halloween status",
    "automatic-world-generation.allowed-worlds",
):
    if item not in readme:
        fail(f"README missing operational instructions: {item}")

print("Runtime-rule static validation passed:")
print("- fail-closed automatic world allow-list for structures, events and natural special mobs; Spawn exclusions; no old-chunk retrofit by default")
print("- manual structure footprint, tick batching, rollback and world-border checks")
print("- command route for all eight admin mob spawns and five manual structures")
print("- single-location Blood Moon damage multiplier and eligible-world filtering")
print("- one-heart talisman migration and persisted idempotent story quest completion")
print("Note: this is static validation only; actual Paper/Purpur and client rendering still need live tests.")
