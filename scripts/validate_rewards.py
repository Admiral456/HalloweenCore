#!/usr/bin/env python3
"""Validate HalloweenCore's configurable vanilla-only reward tables."""
from pathlib import Path
import re
import sys

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required: python3 -m pip install PyYAML") from exc

ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / "src/main/resources/config.yml"

VANILLA_MATERIALS = {
    "COAL", "IRON_INGOT", "GOLD_INGOT", "EMERALD", "BREAD", "COOKED_BEEF",
    "REDSTONE", "DIAMOND", "ENCHANTED_BOOK", "DIAMOND_PICKAXE", "DIAMOND_SWORD",
    "GOLDEN_APPLE", "DIAMOND_CHESTPLATE", "NETHERITE_SCRAP", "TOTEM_OF_UNDYING",
}
VANILLA_ENCHANTMENTS = {"unbreaking", "efficiency", "protection", "sharpness", "mending"}
RARITIES = ("common", "rare", "legendary")
DIFFICULTIES = ("light", "medium", "hard", "very-hard")


def fail(message: str) -> None:
    raise SystemExit(f"Reward validation failed: {message}")


def number(value, label: str, minimum: float, maximum: float) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        fail(f"{label} must be numeric")
    parsed = float(value)
    if parsed < minimum or parsed > maximum:
        fail(f"{label} must be in range {minimum}..{maximum}, got {value}")
    return parsed


def validate_range(section: dict, prefix: str, min_key: str, max_key: str) -> None:
    low = number(section.get(min_key), f"{prefix}.{min_key}", 0, 2_000_000_000)
    high = number(section.get(max_key), f"{prefix}.{max_key}", 0, 2_000_000_000)
    if low > high:
        fail(f"{prefix}: {min_key} ({low}) must not exceed {max_key} ({high})")


def main() -> None:
    if not CONFIG_PATH.is_file():
        fail(f"missing {CONFIG_PATH}")
    try:
        config = yaml.safe_load(CONFIG_PATH.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        fail(f"config.yml is not valid YAML: {exc}")
    if not isinstance(config, dict):
        fail("config.yml must contain a YAML mapping")

    rooms = config.get("secret-rooms")
    if not isinstance(rooms, dict):
        fail("secret-rooms section is missing")
    chance = number(rooms.get("chance-per-new-chunk"), "secret-rooms.chance-per-new-chunk", 0, 1)
    if "Spawn" not in rooms.get("excluded-worlds", []):
        fail("secret-rooms.excluded-worlds must include Spawn")

    weights = rooms.get("rarity-weights")
    if not isinstance(weights, dict):
        fail("secret-rooms.rarity-weights is missing")
    weight_values = []
    for rarity in RARITIES:
        weight = number(weights.get(rarity), f"secret-rooms.rarity-weights.{rarity}", 0, 1_000_000)
        if not float(weight).is_integer():
            fail(f"secret-rooms.rarity-weights.{rarity} must be an integer")
        weight_values.append(int(weight))
    if sum(weight_values) <= 0:
        fail("at least one room rarity weight must be above zero")

    loot = rooms.get("loot")
    if not isinstance(loot, dict):
        fail("secret-rooms.loot section is missing")
    for rarity in RARITIES:
        tier = loot.get(rarity)
        if not isinstance(tier, dict):
            fail(f"secret-rooms.loot.{rarity} is missing")
        validate_range(tier, f"secret-rooms.loot.{rarity}", "min-fragments", "max-fragments")
        entries = tier.get("items")
        if not isinstance(entries, list) or not entries:
            fail(f"secret-rooms.loot.{rarity}.items must be a non-empty list")
        for index, entry in enumerate(entries):
            prefix = f"secret-rooms.loot.{rarity}.items[{index}]"
            if not isinstance(entry, dict):
                fail(f"{prefix} must be a mapping")
            material = entry.get("material")
            if not isinstance(material, str) or material not in VANILLA_MATERIALS:
                fail(f"{prefix}.material must be an approved vanilla material, got {material!r}")
            low = number(entry.get("min", 1), f"{prefix}.min", 1, 64)
            high = number(entry.get("max", low), f"{prefix}.max", 1, 64)
            if low > high:
                fail(f"{prefix}.min must not exceed max")
            number(entry.get("chance", 1), f"{prefix}.chance", 0, 1)
            enchantments = entry.get("enchantments", [])
            if not isinstance(enchantments, list):
                fail(f"{prefix}.enchantments must be a list")
            for enchantment in enchantments:
                if not isinstance(enchantment, str) or not re.fullmatch(r"[a-z_]+:[1-5]", enchantment):
                    fail(f"{prefix} has malformed enchantment {enchantment!r}")
                name = enchantment.rsplit(":", 1)[0]
                if name not in VANILLA_ENCHANTMENTS:
                    fail(f"{prefix} uses an unapproved enchantment {name!r}")

    rewards = config.get("quest-rewards")
    if not isinstance(rewards, dict):
        fail("quest-rewards section is missing")
    default_difficulty = rewards.get("default-difficulty")
    if default_difficulty not in DIFFICULTIES:
        fail(f"quest-rewards.default-difficulty must be one of {DIFFICULTIES}")

    difficulties = rewards.get("difficulties")
    if not isinstance(difficulties, dict):
        fail("quest-rewards.difficulties section is missing")
    expected_ranges = {
        "light": (3, 5),
        "medium": (6, 9),
        "hard": (10, 15),
        "very-hard": (16, 25),
    }
    for difficulty in DIFFICULTIES:
        tier = difficulties.get(difficulty)
        if not isinstance(tier, dict):
            fail(f"quest-rewards.difficulties.{difficulty} is missing")
        validate_range(tier, f"quest-rewards.difficulties.{difficulty}", "min-fragments", "max-fragments")
        actual = (tier["min-fragments"], tier["max-fragments"])
        if actual != expected_ranges[difficulty]:
            fail(f"{difficulty} fragment range must remain {expected_ranges[difficulty]}, got {actual}")

        probabilities = tier.get("key-chances")
        if not isinstance(probabilities, dict):
            fail(f"quest-rewards.difficulties.{difficulty}.key-chances is missing")
        total = 0.0
        for rarity in RARITIES:
            total += number(probabilities.get(rarity),
                            f"quest-rewards.difficulties.{difficulty}.key-chances.{rarity}", 0, 1)
        if total > 1:
            fail(f"key chances for {difficulty} sum to more than 100% ({total:.4f})")

    # This project deliberately has no predefined quest IDs/story. It only stores overrides.
    quest_overrides = rewards.get("quests")
    if not isinstance(quest_overrides, dict):
        fail("quest-rewards.quests must be a mapping")
    for quest_id, override in quest_overrides.items():
        if not re.fullmatch(r"[a-z0-9_-]{1,48}", str(quest_id)):
            fail(f"invalid quest override ID {quest_id!r}")
        if not isinstance(override, dict):
            fail(f"quest override {quest_id} must be a mapping")
        if "difficulty" in override and override["difficulty"] not in DIFFICULTIES:
            fail(f"quest override {quest_id} has an invalid difficulty")
        if "fragments" in override:
            number(override["fragments"], f"quest override {quest_id}.fragments", 0, 2_000_000_000)
        elif "min-fragments" in override or "max-fragments" in override:
            validate_range(override, f"quest override {quest_id}", "min-fragments", "max-fragments")
        if "key-chances" in override:
            chances = override["key-chances"]
            if not isinstance(chances, dict):
                fail(f"quest override {quest_id}.key-chances must be a mapping")
            total = 0.0
            for rarity in RARITIES:
                if rarity in chances:
                    total += number(chances[rarity], f"quest override {quest_id}.key-chances.{rarity}", 0, 1)
            if total > 1:
                fail(f"quest override {quest_id} key chances sum to more than 100%")

    code = (ROOT / "src/main/java/cz/halloween/core/SecretRoomManager.java").read_text(encoding="utf-8")
    required_room_safety = ("hasMatchingKey", "consumeKey", "rollRoomRarity", "populateChest",
                            ".set(path + \".loot-generated\", true)", "InventoryMoveItemEvent")
    for marker in required_room_safety:
        if marker not in code:
            fail(f"SecretRoomManager is missing expected safety/reward hook {marker!r}")

    print(
        "Reward validation passed: 3 room rarities, vanilla-only loot, "
        "fragment ranges and all quest key chances are internally consistent. "
        f"New-chunk room attempt chance: {chance:.1%}."
    )


if __name__ == "__main__":
    main()
