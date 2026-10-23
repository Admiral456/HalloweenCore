#!/usr/bin/env python3
from pathlib import Path
import re
import yaml

root = Path(__file__).resolve().parents[1]
cfg = yaml.safe_load((root / "src/main/resources/config.yml").read_text(encoding="utf-8"))
for required in ["activeWindow", "horrorNights", "raid", "trickOrTreat", "bloodRain",
                 "hauntedNight", "halloweenBoss", "season", "daily-quest", "quest-rewards", "shop"]:
    if required not in cfg:
        raise SystemExit(f"Missing configuration section: {required}")
for retired in ["secret-rooms", "random-events", "grave-encounters", "automatic-world-generation"]:
    if retired in cfg:
        raise SystemExit(f"Retired configuration section remains: {retired}")
if "key-chances" in (root / "src/main/resources/config.yml").read_text(encoding="utf-8"):
    raise SystemExit("Obsolete key reward configuration remains")

rewards = cfg["quest-rewards"]
bounds = {"light": (3, 5), "medium": (8, 12), "hard": (18, 28), "very-hard": (32, 48), "extreme": (55, 80)}
if rewards.get("default-difficulty") not in bounds:
    raise SystemExit("Invalid default quest difficulty")
for name, expected in bounds.items():
    tier = rewards.get("difficulties", {}).get(name, {})
    if (tier.get("min-fragments"), tier.get("max-fragments")) != expected:
        raise SystemExit(f"Invalid quest reward range: {name}")
for quest_id, entry in rewards.get("quests", {}).items():
    if not re.fullmatch(r"[a-z0-9_-]{1,48}", str(quest_id)) or not isinstance(entry, dict):
        raise SystemExit(f"Invalid quest reward override: {quest_id}")

legacy_reward = cfg["daily-quest"].get("reward-fragments")
if not isinstance(legacy_reward, int) or legacy_reward < 0:
    raise SystemExit("Invalid legacy daily-quest migration reward")

# The daily system now offers four simultaneous contracts. Check that the implementation
# contains a healthy pool across all four difficulty tiers and still supports configurable rewards.
quest_source = (root / "src/main/java/cz/halloween/core/DailyQuestManager.java").read_text(encoding="utf-8")
if quest_source.count("new Task(") < 80:
    raise SystemExit("Expected at least 80 daily contract definitions")
for difficulty in ['"light"', '"medium"', '"hard"', '"extreme"']:
    if difficulty not in quest_source:
        raise SystemExit(f"Missing quest difficulty: {difficulty}")
if quest_source.count("new ContractSlot(") < 4:
    raise SystemExit("Expected four daily contract tiers")
if 'new ContractSlot("daily-quests.extreme", "extreme", "EXTRÉMNÍ")' not in quest_source:
    raise SystemExit("Extreme contracts must use the actual extreme fragment tier")
prices = cfg["shop"].get("prices", {})
required_prices = [
    "candy", "iron", "gold", "diamond", "emerald", "netherite-ingot", "netherite-scrap",
    "golden-apple", "enchanted-golden-apple", "totem", "trident", "mace", "netherite-sword",
    "netherite-axe", "netherite-helmet", "netherite-chestplate", "netherite-leggings",
    "netherite-boots", "elytra", "netherite-spear", "coal", "copper", "raw-iron", "raw-gold",
    "redstone", "lapis", "quartz", "cobblestone", "stone", "deepslate", "oak-logs", "glass",
    "obsidian", "quartz-block", "glowstone", "slime-block", "tnt", "bread", "cooked-beef",
    "baked-potato", "golden-carrot", "pumpkin-pie", "honey-bottle", "ender-pearl", "blaze-rod",
    "arrow", "firework-rocket", "experience-bottle", "ender-chest", "shulker-box", "anvil",
    "name-tag", "saddle", "water-bucket", "lead", "beacon", "nether-star", "iron-pickaxe",
    "diamond-sword", "diamond-pickaxe", "diamond-axe", "netherite-pickaxe", "netherite-shovel",
    "netherite-hoe", "bow", "crossbow", "shield", "iron-helmet", "iron-chestplate",
    "iron-leggings", "iron-boots", "diamond-helmet", "diamond-chestplate", "diamond-leggings",
    "diamond-boots", "netherite-pickaxe", "netherite-shovel", "netherite-hoe",
    "amethyst-shard", "prismarine-shard", "prismarine-crystals", "slime-ball", "magma-cream",
    "phantom-membrane", "ghast-tear", "shulker-shell", "turtle-scute", "nautilus-shell",
    "heart-of-the-sea", "crying-obsidian", "blaze-powder", "ender-eye", "dragon-breath",
    "netherite-template", "dark-oak-logs", "prismarine-bricks", "sea-lantern", "terracotta",
    "orange-concrete", "lantern", "campfire", "chain", "compass", "clock", "recovery-compass",
    "spyglass", "fishing-rod", "shears", "flint-and-steel", "dragon-head", "end-crystal",
    "conduit", "diamond-shovel", "diamond-hoe", "golden-horse-armor", "diamond-horse-armor",
]
for item in required_prices:
    if not isinstance(prices.get(item), int) or prices[item] < 0:
        raise SystemExit(f"Invalid shop price: {item}")
for item, price in prices.items():
    if not isinstance(price, int) or price < 0:
        raise SystemExit(f"Invalid configured shop price: {item}")
shop_source = (root / "src/main/java/cz/halloween/core/HalloweenShopManager.java").read_text(encoding="utf-8")
for item in prices:
    if f'"{item}"' not in shop_source:
        raise SystemExit(f"Shop has a configured price without an offer: {item}")
if prices["elytra"] <= prices["diamond"] or prices["mace"] <= prices["iron"] or prices["netherite-spear"] <= prices["diamond"]:
    raise SystemExit("Endgame prices are not meaningfully higher than basic resources")
if prices["enchanted-golden-apple"] < 700 or prices["enchanted-golden-apple"] <= prices["golden-apple"]:
    raise SystemExit("Enchanted golden apples must cost at least 700 fragments and far more than normal golden apples")
if prices["beacon"] <= prices["netherite-ingot"] or prices["nether-star"] <= prices["diamond"]:
    raise SystemExit("Rare endgame items must have premium prices")
if prices["elytra"] < 1800 or prices["beacon"] < 1400 or prices["mace"] < 1200:
    raise SystemExit("Elytra, beacon and mace must remain long-term endgame goals")
if shop_source.count("offers.add(") < 100:
    raise SystemExit("Expected at least 100 shop catalogue offers")

window = cfg["activeWindow"]
if (window.get("startMonth"), window.get("startDay"), window.get("endMonth"), window.get("endDay")) != (10, 23, 11, 2):
    raise SystemExit("Wrong season window")
if cfg["season"].get("enabled") is not True:
    raise SystemExit("Season is disabled")
print("Quest tier rewards, four-tier quest pool, expanded shop prices and seasonal dates validated.")
