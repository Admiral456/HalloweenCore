#!/usr/bin/env python3
from pathlib import Path
import hashlib
import json
import struct
import yaml

root = Path(__file__).resolve().parents[1]
content = root / "itemsadder/contents/warriorland_halloween"
textures = content / "resourcepack/assets/warriorland_halloween/textures/item"
java = root / "src/main/java/cz/halloween/core"

candy_ids = [
    "stev_apple_core",
    "stev_candy_apple",
    "stev_candy_bag",
    "stev_caramel",
    "stev_caramel_apple",
    "stev_chocolate_bar",
    "stev_frosted_sugar_cookie",
    "stev_gumdrop",
    "stev_hard_candy",
    "stev_lollipop",
    "stev_peanut_butter_cup",
    "stev_sugar_cookie",
    "stev_toasted_pumpkin_seeds",
]
expected_textures = {name + ".png" for name in [
    "apple_core", "candy_apple", "candy_bag", "caramel", "caramel_apple",
    "chocolate_bar", "frosted_sugar_cookie", "gumdrop", "hard_candy",
    "lollipop", "peanut_butter_cup", "sugar_cookie", "toasted_pumpkin_seeds",
]}
actual_textures = {path.name for path in (textures / "stev_candy").glob("*.png")}
if actual_textures != expected_textures:
    raise SystemExit("Candy texture set mismatch")

manifest = json.loads(
    (root / "third-party-assets/stevs-halloween/asset-source-manifest.json").read_text()
)
if len(manifest.get("assets", [])) != 13:
    raise SystemExit("Expected 13 candy assets")
for asset in manifest["assets"]:
    path = root / asset["target_path"]
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != asset["sha256"]:
        raise SystemExit(f"Hash mismatch: {path}")
    with path.open("rb") as stream:
        header = stream.read(24)
    if header[:8] != bytes([137, 80, 78, 71, 13, 10, 26, 10]):
        raise SystemExit(f"Invalid PNG signature: {path}")
    if struct.unpack(">II", header[16:24]) != (16, 16):
        raise SystemExit(f"Invalid PNG dimensions: {path}")

items_file = content / "configs/items.yml"
items = items_file.read_text()
shop_source = (java / "HalloweenShopManager.java").read_text()
for item_id in candy_ids:
    if f"  {item_id}:" not in items:
        raise SystemExit(f"Missing ItemsAdder candy definition: {item_id}")
    if f'"{item_id}"' not in shop_source:
        raise SystemExit(f"Candy is not listed in the shop: {item_id}")

if "secret_key" in items or (textures / "secret_key.png").exists():
    raise SystemExit("Secret key item/texture remains")
if (java / "KeyItemFactory.java").exists() or (java / "SecretRoomManager.java").exists():
    raise SystemExit("Secret-room/key class remains")

config = yaml.safe_load((root / "src/main/resources/config.yml").read_text())
if "secret-rooms" in config or "key-chances" in str(config):
    raise SystemExit("Secret-room/key configuration remains")

window = config.get("activeWindow", {})
if (window.get("startMonth"), window.get("startDay"),
        window.get("endMonth"), window.get("endDay")) != (10, 23, 11, 2):
    raise SystemExit("Wrong season dates")

if config.get("fog", {}).get("enabled") is not False:
    raise SystemExit("Legacy fog effect must be disabled")
horror_effects = config.get("horrorNights", {}).get("effects", {})
if horror_effects.get("thunderstorm") is not False:
    raise SystemExit("Horror Nights must not force thunderstorms")

horror_source = (java / "horrornights/HorrorNightsManager.java").read_text()
weather_mutators = (
    "setStorm(", "setThundering(", "setWeatherDuration(",
    "setStormDuration(", "setClearWeatherDuration(",
)
for mutator in weather_mutators:
    if mutator in horror_source:
        raise SystemExit(f"Horror Nights must not change world weather: {mutator}")

if not (java / "DailyQuestManager.java").is_file():
    raise SystemExit("Daily quest manager missing")
if not (java / "HalloweenShopManager.java").is_file():
    raise SystemExit("Halloween shop manager missing")

core_source = (java / "HalloweenCore.java").read_text()
required_wiring = {
    "daily quest manager": "new DailyQuestManager(this)",
    "/quest command executor": 'getCommand("quest").setExecutor(dailyQuestManager)',
    "/halloween shop route": 'case "shop" -> shopManager.open(sender);',
}
for label, fragment in required_wiring.items():
    if fragment not in core_source:
        raise SystemExit(f"Missing command/manager wiring: {label}")

plugin_yml = (root / "src/main/resources/plugin.yml").read_text()
if "  quest:" not in plugin_yml or "  halloween:" not in plugin_yml:
    raise SystemExit("Required quest/shop commands missing from plugin.yml")

print("Scope validation passed.")
