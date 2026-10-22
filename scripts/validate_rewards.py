#!/usr/bin/env python3
from pathlib import Path
import re
import yaml

root = Path(__file__).resolve().parents[1]
config = yaml.safe_load((root / "src/main/resources/config.yml").read_text())
if "secret-rooms" in config:
    raise SystemExit("Removed secret-room feature remains in config")

rewards = config.get("quest-rewards", {})
bounds = {
    "light": (3, 5),
    "medium": (6, 9),
    "hard": (10, 15),
    "very-hard": (16, 25),
}
if rewards.get("default-difficulty") not in bounds:
    raise SystemExit("Invalid default quest difficulty")
for name, expected in bounds.items():
    tier = rewards.get("difficulties", {}).get(name, {})
    actual = (tier.get("min-fragments"), tier.get("max-fragments"))
    if actual != expected or "key-chances" in tier:
        raise SystemExit(f"Invalid reward range for {name}")
for quest_id, entry in rewards.get("quests", {}).items():
    if not re.fullmatch(r"[a-z0-9_-]{1,48}", str(quest_id)):
        raise SystemExit(f"Invalid quest ID: {quest_id}")
    if not isinstance(entry, dict) or "key-chances" in entry:
        raise SystemExit(f"Invalid quest override: {quest_id}")

if config.get("daily-quest", {}).get("reward-fragments") != 1:
    raise SystemExit("Daily quest reward default should be 1 fragment")

prices = config.get("shop", {}).get("prices", {})
required_prices = [
    "candy", "iron", "gold", "diamond", "emerald", "netherite-ingot",
    "netherite-scrap", "golden-apple", "enchanted-golden-apple", "totem",
    "trident", "mace", "netherite-sword", "netherite-axe", "netherite-helmet",
    "netherite-chestplate", "netherite-leggings", "netherite-boots", "elytra",
    "netherite-spear",
]
for key in required_prices:
    if not isinstance(prices.get(key), int) or prices[key] < 0:
        raise SystemExit(f"Invalid shop price: {key}")
if prices["elytra"] <= prices["diamond"] or prices["mace"] <= prices["iron"]:
    raise SystemExit("Rare items must cost more than basic resources")

window = config.get("activeWindow", {})
dates = (
    window.get("startMonth"), window.get("startDay"),
    window.get("endMonth"), window.get("endDay"),
)
if dates != (10, 23, 11, 2):
    raise SystemExit("Wrong season window")
if config.get("fog", {}).get("enabled") is not False:
    raise SystemExit("Legacy fog effect must be disabled")

print("Reward and shop validation passed.")
