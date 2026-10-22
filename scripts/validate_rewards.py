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
bounds = {"light": (3, 5), "medium": (6, 9), "hard": (10, 15), "very-hard": (16, 25)}
if rewards.get("default-difficulty") not in bounds:
    raise SystemExit("Invalid default quest difficulty")
for name, expected in bounds.items():
    tier = rewards.get("difficulties", {}).get(name, {})
    if (tier.get("min-fragments"), tier.get("max-fragments")) != expected:
        raise SystemExit(f"Invalid quest reward range: {name}")
for quest_id, entry in rewards.get("quests", {}).items():
    if not re.fullmatch(r"[a-z0-9_-]{1,48}", str(quest_id)) or not isinstance(entry, dict):
        raise SystemExit(f"Invalid quest reward override: {quest_id}")

if cfg["daily-quest"].get("reward-fragments") != 1:
    raise SystemExit("Daily quest must award one fragment by default")
prices = cfg["shop"].get("prices", {})
required_prices = [
    "candy", "iron", "gold", "diamond", "emerald", "netherite-ingot", "netherite-scrap",
    "golden-apple", "enchanted-golden-apple", "totem", "trident", "mace", "netherite-sword",
    "netherite-axe", "netherite-helmet", "netherite-chestplate", "netherite-leggings",
    "netherite-boots", "elytra", "netherite-spear",
]
for item in required_prices:
    if not isinstance(prices.get(item), int) or prices[item] < 0:
        raise SystemExit(f"Invalid shop price: {item}")
if prices["elytra"] <= prices["diamond"] or prices["mace"] <= prices["iron"]:
    raise SystemExit("Endgame prices are not meaningfully higher than basic resources")

window = cfg["activeWindow"]
if (window.get("startMonth"), window.get("startDay"), window.get("endMonth"), window.get("endDay")) != (10, 23, 11, 2):
    raise SystemExit("Wrong season window")
if cfg["season"].get("enabled") is not True:
    raise SystemExit("Season is disabled")
print("Quest reward tiers, daily fragment award, shop prices and seasonal dates validated.")
