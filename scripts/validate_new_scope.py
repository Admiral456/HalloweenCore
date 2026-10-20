#!/usr/bin/env python3
from pathlib import Path
import hashlib
import json
import struct

root = Path(__file__).resolve().parents[1]
base = root / "itemsadder/contents/warriorland_halloween"
texture_root = base / "resourcepack/assets/warriorland_halloween/textures/item"
expected = {
    "apple_core.png", "candy_apple.png", "candy_bag.png", "caramel.png",
    "caramel_apple.png", "chocolate_bar.png", "frosted_sugar_cookie.png",
    "gumdrop.png", "hard_candy.png", "lollipop.png", "peanut_butter_cup.png",
    "sugar_cookie.png", "toasted_pumpkin_seeds.png"
}
candy_dir = texture_root / "stev_candy"
actual = {p.name for p in candy_dir.glob("*.png")} if candy_dir.exists() else set()
if actual != expected:
    raise SystemExit(f"Candy texture set mismatch; missing={sorted(expected-actual)}, extra={sorted(actual-expected)}")
key_path = texture_root / "secret_key.png"
if not key_path.is_file():
    raise SystemExit("Missing secret_key.png")
with key_path.open("rb") as stream:
    header = stream.read(24)
if header[:8] != bytes([137, 80, 78, 71, 13, 10, 26, 10]):
    raise SystemExit("secret_key.png is not a PNG file")
if struct.unpack(">II", header[16:24]) != (32, 32):
    raise SystemExit("secret_key.png must be 32x32")

# Ensure the imported candy art remains unchanged and that its license manifest is still accurate.
manifest_path = root / "third-party-assets/stevs-halloween/asset-source-manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
assets = manifest.get("assets", [])
if len(assets) != 13:
    raise SystemExit(f"Expected 13 licensed candy manifest entries, got {len(assets)}")
for asset in assets:
    relative_target = Path(asset["target_path"])
    target = root / relative_target
    if not target.is_file():
        raise SystemExit(f"Missing licensed target: {relative_target}")
    actual_hash = hashlib.sha256(target.read_bytes()).hexdigest()
    if actual_hash != asset["sha256"]:
        raise SystemExit(f"Imported candy texture hash changed: {relative_target}")
    with target.open("rb") as stream:
        png_header = stream.read(24)
    if png_header[:8] != bytes([137, 80, 78, 71, 13, 10, 26, 10]):
        raise SystemExit(f"Invalid PNG: {relative_target}")
    if struct.unpack(">II", png_header[16:24]) != (16, 16):
        raise SystemExit(f"Expected a 16x16 candy sprite: {relative_target}")

items = (base / "configs/items.yml").read_text(encoding="utf-8")
for name in ("stev_apple_core", "stev_candy_apple", "stev_candy_bag", "stev_caramel",
             "stev_caramel_apple", "stev_chocolate_bar", "stev_frosted_sugar_cookie",
             "stev_gumdrop", "stev_hard_candy", "stev_lollipop", "stev_peanut_butter_cup",
             "stev_sugar_cookie", "stev_toasted_pumpkin_seeds", "secret_key"):
    if f"  {name}:" not in items:
        raise SystemExit(f"Missing ItemsAdder item definition: {name}")
java_dir = root / "src/main/java/cz/halloween/core"
expected_classes = {"HalloweenCore.java", "DialogQuestManager.java", "KeyItemFactory.java",
                    "SecretRoomManager.java", "HalloweenPlaceholders.java"}
actual_classes = {p.name for p in java_dir.glob("*.java")}
if actual_classes != expected_classes:
    raise SystemExit(f"Unexpected Java classes: {sorted(actual_classes)}")
for forbidden in ("mythicmobs", "VAMPIRE_BOSS.md", "MUSIC_LICENSE.md"):
    if (root / forbidden).exists():
        raise SystemExit(f"Old system content remains: {forbidden}")
config = (root / "src/main/resources/config.yml").read_text(encoding="utf-8").lower()
for legacy in ("random-events:", "grave-encounters:", "witching-hour:", "automatic-world-generation:"):
    if legacy in config:
        raise SystemExit(f"Legacy configuration remains: {legacy}")

# Guard the agreed reward economy so a later edit cannot accidentally remove a tier or
# introduce a custom-texture dependency into the loot tables.
if "\\nquest-rewards:" not in config:
    raise SystemExit("Missing quest-rewards configuration")
room_config = config.split("\\nquest-rewards:", 1)[0]
reward_config = config.split("\\nquest-rewards:", 1)[1]
for required in (
    "rarity-weights:",
    "min-fragments: 8", "max-fragments: 16",
    "min-fragments: 20", "max-fragments: 35",
    "min-fragments: 40", "max-fragments: 70",
    "material: coal", "material: iron_ingot", "material: diamond",
    "material: enchanted_book", "material: netherite_scrap",
):
    if required not in room_config:
        raise SystemExit(f"Secret-room loot table missing required setting: {required}")
if "warriorland_halloween:" in room_config:
    raise SystemExit("Secret-room loot must use vanilla items; do not add custom-texture items")
for required in (
    "default-difficulty: light",
    "light:", "medium:", "hard:", "very-hard:",
    "min-fragments: 3", "max-fragments: 5",
    "min-fragments: 6", "max-fragments: 9",
    "min-fragments: 10", "max-fragments: 15",
    "min-fragments: 16", "max-fragments: 25",
    "key-chances:",
):
    if required not in reward_config:
        raise SystemExit(f"Quest reward configuration missing required setting: {required}")

print("New-scope validation passed: 13 imported candy textures, one shared key texture, tiered vanilla loot and quest rewards.")
