#!/usr/bin/env python3
from pathlib import Path
import hashlib
import re
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

# The Halloween fragment has its own original pixel-art texture; unlike candy, it is not third-party.
fragment_texture = base / "resourcepack/assets/warriorland_halloween/textures/item/halloween_fragment.png"
if not fragment_texture.is_file():
    raise SystemExit("Missing custom Halloween fragment texture")
with fragment_texture.open("rb") as stream:
    fragment_header = stream.read(24)
if fragment_header[:8] != bytes([137, 80, 78, 71, 13, 10, 26, 10]) or struct.unpack(">II", fragment_header[16:24]) != (64, 64):
    raise SystemExit("Expected valid 64x64 pixel-art Halloween fragment PNG")
if "  halloween_fragment:" not in items or "texture: item/halloween_fragment" not in items:
    raise SystemExit("Custom Halloween fragment item definition is missing")
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
    "cz/halloween/core/JobsFragmentBridge.java",
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
for wired in ["trySpawnCustomMob", "horrorNights.spawn-custom-mobs",
              "horrorNights.custom-mob-ids", "horrorNights.invisible-particle-count"]:
    if wired not in horror:
        raise SystemExit(f"HorrorNights setting is not wired: {wired}")
if "if (plugin instanceof HalloweenCore core && !core.isSeasonActive()) return;" not in horror:
    raise SystemExit("Random HorrorNights must respect the 23 October–2 November season window")
blood = (java / "org/example/feature/BloodRainManager.java").read_text(encoding="utf-8")
if any(token in blood for token in ["setPlayerWeather(", "resetPlayerWeather(", "WeatherType.DOWNFALL"]):
    raise SystemExit("Blood rain must remain particle-only and must not override player weather")

cfg = yaml.safe_load((root / "src/main/resources/config.yml").read_text(encoding="utf-8"))
plugin = yaml.safe_load((root / "src/main/resources/plugin.yml").read_text(encoding="utf-8"))
for section in ["activeWindow", "trickOrTreat", "hauntedNight", "pumpkinRain", "bloodRain",
                "halloweenBoss", "raid", "horrorNights", "season", "daily-quest",
                "quest-rewards", "shop"]:
    if section not in cfg:
        raise SystemExit(f"Required restored config section missing: {section}")
if "custom-mob-ids" not in cfg["horrorNights"] or "custom-mob-chance" not in cfg["horrorNights"]:
    raise SystemExit("Optional MythicMobs configuration is incomplete")
if "MythicMobs" not in plugin.get("softdepend", []):
    raise SystemExit("MythicMobs optional integration must be declared")
if "Jobs" not in plugin.get("softdepend", []):
    raise SystemExit("Jobs Reborn must be an optional soft dependency")
pom = (root / "pom.xml").read_text(encoding="utf-8")
if "com.github.Zrips</groupId><artifactId>Jobs</artifactId><version>v5.2.6.7</version>" not in pom:
    raise SystemExit("Jobs Reborn API dependency is missing")
jobs = (java / "cz/halloween/core/JobsFragmentBridge.java").read_text(encoding="utf-8")
for token in ["JobsExpGainEvent", "jobs-fragments.max-per-day", "jobs-fragments.experience-per-fragment",
              "Europe/Prague", "PlayerQuitEvent", "public void flush()"]:
    if token not in jobs:
        raise SystemExit(f"Jobs fragment bridge is missing: {token}")
config_jobs = cfg.get("jobs-fragments", {})
if config_jobs.get("enabled") is not True or config_jobs.get("max-per-day") != 8:
    raise SystemExit("Jobs fragment bridge must be enabled with a daily cap of eight")
daily_source = (java / "cz/halloween/core/DailyQuestManager.java").read_text(encoding="utf-8")
slot_rows = re.findall(r'new ContractSlot\("([^"]+)",\s*"[^"]+",\s*"[^"]+",\s*(true|false)\)', daily_source)
if sum(weekly == "false" for _, weekly in slot_rows) != 15 or sum(weekly == "true" for _, weekly in slot_rows) != 3:
    raise SystemExit("Daily quest manager must assign exactly 15 daily and three weekly contracts")
if "weekKey()" not in daily_source or "weekly-completion-bonus" not in daily_source:
    raise SystemExit("Weekly challenges are missing a reset key or completion bonus")
if any(section in cfg for section in ["secret-rooms", "random-events", "grave-encounters", "automatic-world-generation"]):
    raise SystemExit("A retired room/key or failed world-generation section remains")
if "key-chances" in (root / "src/main/resources/config.yml").read_text(encoding="utf-8"):
    raise SystemExit("Obsolete key reward configuration remains")
window = cfg["activeWindow"]
if (window.get("startMonth"), window.get("startDay"), window.get("endMonth"), window.get("endDay")) != (10, 23, 11, 2):
    raise SystemExit("Season must run October 23 through November 2 inclusive")
if cfg["season"].get("enabled") is not True:
    raise SystemExit("Season must be enabled")

required_commands = {"halloween", "quest", "horrornights", "pumpkinrain", "bloodrain",
                     "spooky", "spookyvolume", "spookyboss", "spookyraid"}
if not required_commands.issubset(set(plugin.get("commands", {}))):
    raise SystemExit(f"Missing full event commands: {sorted(required_commands-set(plugin.get('commands', {})))}")
core = (java / "cz/halloween/core/HalloweenCore.java").read_text(encoding="utf-8")
for date_migration in ['startDay == 27 && endMonth == 10 && endDay == 31',
                       'cfg.set("activeWindow.startDay", 23)',
                       'cfg.set("activeWindow.endDay", 2)']:
    if date_migration not in core:
        raise SystemExit(f"Legacy Halloween date migration is missing: {date_migration}")
for wiring in ["org.example.SpookySeason.enable(this)", "new HorrorNightManager(this)",
               "new DailyQuestManager(this)", "new HalloweenPlaceholders(this)",
               "placeholders.register()", "placeholders.unregister()",
               "registerJobsFragmentBridge()", "flushJobsFragmentBridge()",
               'case "shop" -> shopManager.open(sender);']:
    if wiring not in core:
        raise SystemExit(f"Missing plugin wiring: {wiring}")
shop = (java / "cz/halloween/core/HalloweenShopManager.java").read_text(encoding="utf-8")
if "warriorland_halloween:halloween_fragment" not in shop or "fragmentBalanceItem(player)" not in shop:
    raise SystemExit("Shop balance display is not wired to the custom fragment item")
for item_id in candy_ids:
    if f'"{item_id}"' not in shop:
        raise SystemExit(f"Candy not connected to shop: {item_id}")
orange_script = root / "scripts/generate_orange_weapon_items.py"
orange_config_path = base / "configs/orange_weapons.yml"
orange_config = orange_config_path.read_text(encoding="utf-8") if orange_config_path.is_file() else ""
for orange_id in ["orange_trident", "orange_mace", "orange_netherite_sword",
                  "orange_netherite_axe", "orange_netherite_spear"]:
    if f"  {orange_id}:" not in orange_config:
        raise SystemExit(f"Missing orange ItemsAdder weapon definition: {orange_id}")
if not orange_script.is_file():
    raise SystemExit("Orange weapon config generator is missing")
for orange_id in ["orange_trident", "orange_mace", "orange_netherite_sword",
                  "orange_netherite_axe", "orange_netherite_spear"]:
    if f"warriorland_halloween:{orange_id}" not in shop:
        raise SystemExit(f"Orange weapon is not connected to the shop: {orange_id}")
# Keep the artifact aligned with the actual 26.1.2 Paper/Purpur deployment target.
if "26.1.2.build.74-stable" not in pom or "<release>25</release>" not in pom:
    raise SystemExit("Plugin must compile against Paper API 26.1.2 and Java 25")
if plugin.get("api-version") != "26.1.2":
    raise SystemExit("plugin.yml must declare api-version 26.1.2")
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
if "java-version: '25'" not in workflow:
    raise SystemExit("CI must build with Java 25 for Paper/Purpur 26.1.2")

print("Full event wiring, no-weather rule, season dates, 15 daily/3 weekly quests, Jobs bridge, shop, candy textures and orange weapons validated for Paper/Purpur 26.1.2.")
