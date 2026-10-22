#!/usr/bin/env python3
from pathlib import Path
import hashlib
import json
import struct
import yaml

root = Path(__file__).resolve().parents[1]
base = root / "itemsadder/contents/warriorland_halloween"
textures = base / "resourcepack/assets/warriorland_halloween/textures/item/stev_candy"
expected_candies = {
    "apple_core.png", "candy_apple.png", "candy_bag.png", "caramel.png",
    "caramel_apple.png", "chocolate_bar.png", "frosted_sugar_cookie.png",
    "gumdrop.png", "hard_candy.png", "lollipop.png", "peanut_butter_cup.png",
    "sugar_cookie.png", "toasted_pumpkin_seeds.png",
}
actual_candies = {p.name for p in textures.glob("*.png")}
if actual_candies != expected_candies:
    raise SystemExit(f"Candy texture mismatch: missing={sorted(expected_candies-actual_candies)}, extra={sorted(actual_candies-expected_candies)}")

manifest_path = root / "third-party-assets/stevs-halloween/asset-source-manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
assets = manifest.get("assets", [])
if len(assets) != 13:
    raise SystemExit(f"Expected 13 licensed candy entries, got {len(assets)}")
for asset in assets:
    target = root / asset["target_path"]
    if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest() != asset["sha256"]:
        raise SystemExit(f"Candy asset missing or hash mismatch: {target}")
    with target.open("rb") as stream:
        header = stream.read(24)
    if header[:8] != bytes([137, 80, 78, 71, 13, 10, 26, 10]) or struct.unpack(">II", header[16:24]) != (16, 16):
        raise SystemExit(f"Expected valid 16x16 PNG: {target}")

items = (base / "configs/items.yml").read_text(encoding="utf-8")
candy_ids = [
    "stev_apple_core", "stev_candy_apple", "stev_candy_bag", "stev_caramel",
    "stev_caramel_apple", "stev_chocolate_bar", "stev_frosted_sugar_cookie",
    "stev_gumdrop", "stev_hard_candy", "stev_lollipop", "stev_peanut_butter_cup",
    "stev_sugar_cookie", "stev_toasted_pumpkin_seeds",
]
for item_id in candy_ids:
    if f"  {item_id}:" not in items:
        raise SystemExit(f"Missing ItemsAdder candy definition: {item_id}")
if "secret_key" in items or (base / "resourcepack/assets/warriorland_halloween/textures/item/secret_key.png").exists():
    raise SystemExit("Retired secret key item/texture must not be present")

java = root / "src/main/java"
required_java = [
    "cz/halloween/core/HalloweenCore.java",
    "cz/halloween/core/DailyQuestManager.java",
    "cz/halloween/core/DialogQuestManager.java",
    "cz/halloween/core/HalloweenShopManager.java",
    "cz/halloween/core/HalloweenPlaceholders.java",
    "cz/halloween/core/HorrorNightManager.java",
    "org/example/SpookySeason.java",
    "org/example/feature/BloodRainManager.java",
    "org/example/feature/HauntedNightManager.java",
    "org/example/feature/HalloweenBossManager.java",
    "org/example/feature/PumpkinRainManager.java",
    "org/example/feature/TrickOrTreatListener.java",
    "org/example/feature/raid/RaidManager.java",
]
for relative in required_java:
    if not (java / relative).is_file():
        raise SystemExit(f"Full event source missing: {relative}")
for retired in ["cz/halloween/core/KeyItemFactory.java", "cz/halloween/core/SecretRoomManager.java"]:
    if (java / retired).exists():
        raise SystemExit(f"Retired room/key source remains: {retired}")

horror = (java / "cz/halloween/core/HorrorNightManager.java").read_text(encoding="utf-8")
if any(token in horror for token in ["setStorm(true)", "setThundering(true)", "setWeatherDuration(", "setThunderDuration("]):
    raise SystemExit("HorrorNights must not force world weather or sky")
blood = (java / "org/example/feature/BloodRainManager.java").read_text(encoding="utf-8")
if any(token in blood for token in ["setPlayerWeather(", "resetPlayerWeather(", "WeatherType.DOWNFALL"]):
    raise SystemExit("Blood rain must remain particle-only and must not override player weather")

cfg = yaml.safe_load((root / "src/main/resources/config.yml").read_text(encoding="utf-8"))
for section in ["activeWindow", "trickOrTreat", "hauntedNight", "pumpkinRain", "bloodRain",
                "halloweenBoss", "raid", "horrorNights", "season", "daily-quest",
                "quest-rewards", "shop"]:
    if section not in cfg:
        raise SystemExit(f"Required restored config section missing: {section}")
if any(section in cfg for section in ["secret-rooms", "random-events", "grave-encounters", "automatic-world-generation"]):
    raise SystemExit("A retired room/key or failed world-generation section remains")
if "key-chances" in (root / "src/main/resources/config.yml").read_text(encoding="utf-8"):
    raise SystemExit("Obsolete key reward configuration remains")
window = cfg["activeWindow"]
if (window.get("startMonth"), window.get("startDay"), window.get("endMonth"), window.get("endDay")) != (10, 23, 11, 2):
    raise SystemExit("Season must run October 23 through November 2 inclusive")
if cfg["season"].get("enabled") is not True:
    raise SystemExit("Season must be enabled")

plugin = yaml.safe_load((root / "src/main/resources/plugin.yml").read_text(encoding="utf-8"))
required_commands = {"halloween", "quest", "horrornights", "pumpkinrain", "bloodrain",
                     "spooky", "spookyvolume", "spookyboss", "spookyraid"}
if not required_commands.issubset(set(plugin.get("commands", {}))):
    raise SystemExit(f"Missing full event commands: {sorted(required_commands-set(plugin.get('commands', {})))}")
core = (java / "cz/halloween/core/HalloweenCore.java").read_text(encoding="utf-8")
for wiring in ["org.example.SpookySeason.enable(this)", "new HorrorNightManager(this)",
               "new DailyQuestManager(this)", 'case "shop" -> shopManager.open(sender);']:
    if wiring not in core:
        raise SystemExit(f"Missing plugin wiring: {wiring}")
shop = (java / "cz/halloween/core/HalloweenShopManager.java").read_text(encoding="utf-8")
for item_id in candy_ids:
    if f'"{item_id}"' not in shop:
        raise SystemExit(f"Candy not connected to shop: {item_id}")
print("Full event wiring, no-weather rule, season dates, daily/shop systems and all 13 candy textures validated.")
