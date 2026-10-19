#!/usr/bin/env python3
from pathlib import Path
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
if not header.startswith(b"\x89PNG\r\n\x1a\n"):
    raise SystemExit("secret_key.png is not a PNG file")
if struct.unpack(">II", header[16:24]) != (32, 32):
    raise SystemExit("secret_key.png must be 32x32")
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
print("New-scope validation passed: 13 imported candy textures, custom key, quest ledger and secret rooms.")
