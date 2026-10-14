#!/usr/bin/env python3
"""Static regression checks for Halloween structure/world safety.

These checks complement, but do not replace, in-game Paper/Purpur testing.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


core = read("src/main/java/cz/halloween/core/HalloweenCore.java")
decorator = read("src/main/java/cz/halloween/core/HalloweenWorldDecorator.java")
structures = read("src/main/java/cz/halloween/core/HalloweenStructureManager.java")
commands = read("src/main/java/cz/halloween/core/HalloweenStructureCommand.java")
halloween_command = read("src/main/java/cz/halloween/core/HalloweenCommand.java")
events = read("src/main/java/cz/halloween/core/HalloweenEventManager.java")
quests = read("src/main/java/cz/halloween/core/HalloweenQuestManager.java")
atmosphere = read("src/main/java/cz/halloween/core/HalloweenAtmosphere.java")
config = read("src/main/resources/config.yml")
plugin_yml = read("src/main/resources/plugin.yml")

# Hard safety boundary: configurable world lists cannot allow the Spawn world.
require(
    re.search(r'equalsIgnoreCase\(world\.getName\(\)\).*?return false;', core, re.S)
    is not None and '"Spawn".equalsIgnoreCase(world.getName())' in core,
    "HalloweenCore must hard-block the world named Spawn."
)
require('"Spawn".equalsIgnoreCase(world.getName())' in decorator,
        "Automatic ground decoration must hard-block Spawn.")
require('"Spawn".equalsIgnoreCase(world.getName())' in structures,
        "Natural structure generation must hard-block Spawn.")
require('!plugin.isEligibleGameplayWorld(world)' in structures,
        "Natural structures must also respect gameplay world eligibility.")
require('plugin.isEligibleGameplayWorld(player.getWorld())' in events,
        "Event player selection must exclude Spawn.")
require('!plugin.isEligibleGameplayWorld(player.getWorld())' in atmosphere,
        "Atmosphere must exclude Spawn.")

# Automatic world edits are fail-closed and must not retroactively decorate old chunks by default.
def yaml_section(section_name: str) -> str:
    lines = config.splitlines()
    start = next((i for i, line in enumerate(lines) if line.strip() == section_name + ":"), None)
    if start is None:
        return ""
    collected = [lines[start]]
    for line in lines[start + 1:]:
        if line and not line[0].isspace() and line.rstrip().endswith(":"):
            break
        collected.append(line)
    return "\\n".join(collected)


decorations_config = yaml_section("world-decorations")
generation_config = yaml_section("world-generation")
require("allowed-worlds: []" in decorations_config,
        "world-decorations.allowed-worlds must default to an empty list.")
require("allow-existing-chunks: false" in decorations_config,
        "Existing chunks must not be retroactively decorated by default.")
require("allowed-worlds: []" in generation_config,
        "world-generation.allowed-worlds must default to an empty list.")
require('allow-existing-chunks: false' in config,
        "Existing chunks must not be retroactively decorated by default.")
require('!event.isNewChunk()' in structures,
        "Natural structures must only be considered for new chunks.")

# Manual structures: single type argument, bounded footprint, block snapshots and rollback.
for token, message in [
    ("buildBeforePlayer(player, args[0])", "Root command must invoke direct placement before the player."),
    ("buildBeforePlayer(player, args[1])", "Halloween command must invoke direct placement before the player."),
    ("TileState", "FORCE placement must inspect block entities before mutation."),
    ("max-block-changes", "Structure changes need a configurable hard cap."),
    ("blocks-per-tick", "Large structures must be generated in bounded batches."),
    ("rollback(job)", "Structure errors must attempt rollback."),
    ("stopAndRollback()", "Shutdown must stop/rollback unfinished structures."),
    ("suppressAutomaticGeneration", "Command-driven chunk loads must not trigger overlapping automatic structures."),
]:
    require(token in structures + commands + halloween_command, message)

require('halloweencore.structures' in plugin_yml,
        "Structure command must have a dedicated permission.")
require('spawn' in plugin_yml,
        "The root structure command must be declared.")
require('args.length != 1' in commands,
        "Root structure command must accept only the structure type argument.")
require('args.length != 2' in halloween_command,
        "/halloween spawn must accept only the structure type argument.")

# Runtime state: event intervals and partial quest progress must be handled safely.
require('scheduleNextEvent();' in events,
        "Normal startup must use the configured event interval.")
require('hasEligibleGameplayPlayers()' in events,
        "Events must require at least one eligible gameplay-world player.")
require('pendingProgressSave' in quests and '}, 100L);' in quests,
        "Partial quest progress must be saved via a coalesced delayed save.")
require('completeStoryQuest(playerId, quest.id())' in quests,
        "Quest completion must have an idempotency guard.")

print("PASS: Halloween Spawn safety, allowlists, direct structure commands, FORCE bounds, rollback, event eligibility and quest persistence static checks.")
print("NOTE: This script is not a substitute for launching the plugin on a test Paper/Purpur server.")
