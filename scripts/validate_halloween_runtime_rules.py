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
grave_encounters = read("src/main/java/cz/halloween/core/HalloweenGraveEncounterManager.java")
quests = read("src/main/java/cz/halloween/core/HalloweenQuestManager.java")
passives = read("src/main/java/cz/halloween/core/HalloweenPassiveEffectManager.java")
config = read("src/main/resources/config.yml")
plugin_yml = read("src/main/resources/plugin.yml")
readme = read("README.md")
item_texture_generator = read("scripts/generate_halloween_item_textures.py")
block_texture_generator = read("scripts/generate_halloween_block_textures.py")
build_workflow = read(".github/workflows/build.yml")

# Texture generators must run before validators and before ItemsAdder packaging.
for filename in (
    "hunter_mask.png", "cursed_talisman.png", "halloween_token.png", "cursed_candy.png",
    "haunted_map.png", "crimson_warden_helmet.png", "crimson_warden_chestplate.png",
    "crimson_warden_leggings.png", "crimson_warden_boots.png", "crimson_warden_sword.png",
    "crimson_warden_pickaxe.png", "crimson_warden_axe.png", "crimson_warden_shovel.png",
    "crimson_warden_hoe.png",
):
    if filename not in item_texture_generator:
        fail(f"Project-owned item texture generator is missing output: {filename}")
for filename in ("red_mushroom.png", "brown_mushroom.png", "dead_bush.png", "soul_lantern.png"):
    if filename not in block_texture_generator:
        fail(f"World-decoration texture generator is missing output: {filename}")
for marker in ("run: python3 scripts/generate_halloween_block_textures.py",
               "run: python3 scripts/generate_halloween_item_textures.py",
               "name: Validate Halloween assets"):
    if marker not in build_workflow:
        fail(f"Build workflow is missing texture build step: {marker}")
if build_workflow.index("scripts/generate_halloween_block_textures.py") > build_workflow.index("Validate Halloween assets"):
    fail("Block textures must be generated before resource-pack validation")
if build_workflow.index("scripts/generate_halloween_item_textures.py") > build_workflow.index("Validate Halloween assets"):
    fail("Item textures must be generated before resource-pack validation")

# Automatic world generation must fail closed and never touch the named lobby.
for marker in (
    'world.getName().equalsIgnoreCase("Spawn")',
    'automatic-world-generation.enabled',
    'automatic-world-generation.allowed-worlds',
):
    if marker not in core:
        fail(f"Automatic world safety guard is missing: {marker}")
if 'automatic-world-generation:\n  enabled: true\n  allowed-worlds: ["*"]' not in config:
    fail("Default automatic world generation must cover all non-Spawn worlds")
if "boolean includeExisting = plugin.getConfig().getBoolean(" not in structures or "(!event.isNewChunk() && !includeExisting)" not in structures:
    fail("Natural structures must respect the existing-chunk setting instead of silently skipping explored chunks")
if "plugin.isAutomaticHalloweenWorld(event.getWorld())" not in structures:
    fail("Natural structures are missing the explicit world allow-list guard")
if "plugin.isAutomaticHalloweenWorld(event.getWorld())" not in decorator:
    fail("Chunk decorations are missing the explicit world allow-list guard")
if "world-decorations.decorate-existing-chunks" not in decorator or "decorate-existing-chunks: true" not in config:
    fail("Safe add-only decoration must also run in explored chunks")
if "halloween_decorated_v3" not in decorator:
    fail("Decoration version must allow older chunks to receive the new atmospheric accent layer")
for feature in ("placeMushrooms(", "placeGraves(", "placeSoulLanterns(", "placeDeadBushes("):
    if feature not in decorator:
        fail(f"Atmospheric world generation is missing feature layer: {feature}")
if "structures-on-existing-chunks: true" not in config or "automatic-world-generation.structures-on-existing-chunks" not in structures:
    fail("Natural graveyard/village generation must also run on eligible explored chunks")
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
if 'args[0].equalsIgnoreCase("locate")' not in command:
    fail("The /halloween locate command is not routed")
for marker in ("PlayerCommandPreprocessEvent", 'equalsIgnoreCase("/locate")',
               "isLocateTarget(target)", "locateTabComplete(args[1])"):
    if marker not in command:
        fail(f"Custom /locate shortcut/tab completion is missing: {marker}")
for marker in ("handleLocateCommand(", "locations.getMapList(\"locations\")",
               "locations.save(locationsFile)", "recordLocation(job.kind",
               "recordLocation(kind, world, centerX + 0.5D"):
    if marker not in structures:
        fail(f"Persistent structure locating contract is missing: {marker}")
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
    fail("Natural special-mob conversion must use the automatic-world allow-list")
if mobs.count("isAutomaticHalloweenWorld(world)") < 2:
    fail("Event mobs and Blood Moon captain must stay inside enabled Halloween worlds")
if "isAutomaticHalloweenWorld(target.getWorld())" not in mobs:
    fail("Blood Moon damage must stay inside enabled Halloween worlds")

# Every configured event must be selectable and each surge must reach multiple players.
for event_id in ("soulstorm", "witching-hour", "cursed-harvest",
                 "blood-moon-invasion", "pumpkin-apocalypse", "graveyard-rising"):
    if f'"{event_id}"' not in events:
        fail(f"Event manager is missing known event ID: {event_id}")
    if f"    {event_id}:" not in config:
        fail(f"Config is missing event type: {event_id}")
for marker in ("applySurgeToPlayer(target, now)",
               "for (Player target : players)",
               "cleanupEventMobs()", "scheduleNextEvent()"):
    if marker not in events:
        fail(f"Event lifecycle/surge contract is missing: {marker}")
if 'getInt("random-events.players-per-surge"' in events:
    fail("Event waves must target every eligible player, not a random player subset")
if 'getInt("random-events.spawn-radius-blocks", 25)' not in mobs:
    fail("Player-proximity event spawn radius must be explicitly capped at 25 blocks")
if "spawnFromLoadedFarmland()" not in events or "Material.FARMLAND" not in events:
    fail("Cursed Harvest must scan real Farmland blocks")
if "spawnFromRecordedGraveyards()" not in events or "getRecordedGraveyardLocations" not in structures:
    fail("Graveyard Rising must spawn from recorded cemetery sites")
if "displayBloodMoonToPlayers()" not in events or "world.setTime(13_000L)" not in events:
    fail("Blood Moon must set night and show its event-only red visual")
if "migrateEventSpawnRules()" not in core or "migrations.event-spawn-rules-v1" not in core:
    fail("Older server configs must migrate the new radius, entity cap and field/structure scan budgets")
if "decorateLoadedChunksWithPumpkins()" not in events:
    fail("Pumpkin Apocalypse must decorate the active world's loaded natural chunks")

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

# Seasonal schedule and Witching Hour runtime wiring.
season_manager = read("src/main/java/cz/halloween/core/HalloweenSeasonManager.java")
join_listener = read("src/main/java/cz/halloween/core/HalloweenJoinListener.java")
for marker in ("migrateSeasonAndWitchingDefaults()", "seasonManager.start()",
               "seasonManager.refresh()", "getSeasonManager()"):
    if marker not in core:
        fail(f"Season scheduler is not fully wired into HalloweenCore: {marker}")
for marker in ("season.runtime-last-in-season", "sendCountdownIfNeeded(",
               "witching-hour.interval-multiplier", "reschedule-on-transition",
               "ambient-interval-seconds", "isWitchingHourActive(World world)"):
    if marker not in season_manager:
        fail(f"Season/Witching Hour behavior is missing: {marker}")
if "sendCountdownIfNeeded(player)" not in join_listener:
    fail("Joining players are not shown the pre-season countdown")
if "getEventIntervalMultiplier()" not in events or "rescheduleForTimeWindow()" not in events:
    fail("Witching Hour does not affect/reschedule the random event timer")
if "season:\n  enabled: true" not in config or "witching-hour:\n  enabled: true" not in config:
    fail("Default config must enable the scheduled Halloween season and Witching Hour")
if "## Automatická sezóna a Čarodějnická hodina" not in readme:
    fail("README is missing season/Witching Hour configuration documentation")

# Candle-triggered grave ritual is integrated, bounded, and rewards only a completed final wave.
for marker in ("onCandleInteraction(", "isGraveyardSite(", "startWave(encounter, 1)",
               "case 1 ->", "case 2 ->", "case 3 ->", "createBossBar(encounter)",
               "finish(encounter, true, true)", "grave-encounter",
               "graveEncounterMobKey", "max-active", "wave-timeout-seconds"):
    if marker not in grave_encounters and marker not in mobs:
        fail(f"Three-wave grave encounter is missing required behavior: {marker}")
if "spawnEncounterMob(Player player, String mobId)" not in mobs:
    fail("Grave encounter mobs must use a separate mob-tracking path")
if "isNearRecordedGraveyard(Location location, double radiusBlocks)" not in structures:
    fail("Candle-triggered rituals are missing recorded-graveyard proximity checks")
if "migrateGraveEncounterDefaults()" not in core or "graveEncounterManager.start()" not in core:
    fail("Grave encounter manager is not fully wired into startup/config migration")
if "migrateWitchingBatDefaults()" not in core:
    fail("Witching Hour bat settings are missing the one-time migration")
if "onBatDeath(EntityDeathEvent event)" not in mobs:
    fail("The Witching Hour bat-to-witch surprise is missing")
for marker in ('"witching-hour.bat-transformation-enabled"', '"witching-hour.bat-transformation-chance"',
               'isWitchingHourActive(bat.getWorld())', 'spawnMythicModelMob(origin, "hex-witch")'):
    if marker not in mobs:
        fail(f"Bat-to-witch transformation is missing required guard or fallback: {marker}")
if "bat-transformation-chance: 0.08" not in config:
    fail("Config must define the guarded Witching Hour bat transformation chance")
if 'args[0].equalsIgnoreCase("grave")' not in command or "startTestEncounter(player)" not in command:
    fail("The admin grave encounter test command is not routed")
if "grave-encounters:\n  enabled: true" not in config:
    fail("Default config must include enabled grave encounters")
if "/halloween grave test" not in readme:
    fail("README is missing the grave encounter test command")

for item in (
    "/halloween spawn <hřbitov|vesnice|aréna|vampirearena|goblinarena>",
    "/halloween status",
    "/halloween locate <hřbitov|vesnice|aréna|vampirearena|goblinarena|all>",
    "automatic-world-generation.allowed-worlds",
):
    if item not in readme:
        fail(f"README missing operational instructions: {item}")

print("Runtime-rule static validation passed:")
print("- all non-Spawn worlds by default; safe add-only atmosphere and random graveyards/villages in explored and new chunks")
print("- mushrooms, grave markers, soul lanterns, dead brush, pumpkins and cobwebs")
print("- event IDs/config parity, multi-player surges, cleanup and allowed-world damage filtering")
print("- manual structure footprint, tick batching, rollback and world-border checks")
print("- command route for all eight admin mob spawns and five manual structures")
print("- persistent nearest-structure locating for all five Halloween structure types")
print("- single-location Blood Moon damage multiplier and eligible-world filtering")
print("- one-heart talisman migration and persisted idempotent story quest completion")
print("- calendar season transitions/countdown and real-time or Minecraft-time Witching Hour")
print("- candle-triggered three-wave grave ritual, final boss bar, separate mob tracking and gated rewards")
print("- rare Hex Witch transformation during the configured Witching Hour")
print("- reproducible project-owned pixel-art icons and themed mushroom/dead-bush/soul-lantern textures")
print("Note: this is static validation only; actual Paper/Purpur and client rendering still need live tests.")
