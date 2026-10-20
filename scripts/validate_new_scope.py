from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA_DIR = ROOT / "src/main/java/cz/halloween/core"
EXPECTED_JAVA = {
    "HalloweenCore.java",
    "HalloweenCommand.java",
    "HalloweenQuestManager.java",
    "SecretRoomManager.java",
    "KeyItemFactory.java",
    "HalloweenPlaceholderExpansion.java",
}
CANDIES = {
    "apple_core.png",
    "candy_apple.png",
    "candy_bag.png",
    "caramel.png",
    "caramel_apple.png",
    "chocolate_bar.png",
    "frosted_sugar_cookie.png",
    "gumdrop.png",
    "hard_candy.png",
    "lollipop.png",
    "peanut_butter_cup.png",
    "sugar_cookie.png",
    "toasted_pumpkin_seeds.png",
}
ASSET_ROOT = ROOT / "itemsadder/contents/warriorland_halloween"
ITEM_TEX = ASSET_ROOT / "resourcepack/assets/warriorland_halloween/textures/item"

def fail(message):
    print(f"Scope validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)

actual_java = {p.name for p in JAVA_DIR.glob("*.java")}
if actual_java != EXPECTED_JAVA:
    fail(f"unexpected Java source set: {sorted(actual_java)}")

if (ROOT / "mythicmobs").exists():
    fail("old MythicMobs/ModelEngine package must not be shipped")

if (ASSET_ROOT / "configs/sounds.yml").exists():
    fail("old Halloween event sound registration must be removed")

candy_dir = ITEM_TEX / "stev_candy"
actual_candies = {p.name for p in candy_dir.glob("*.png")}
if actual_candies != CANDIES:
    fail(f"expected exactly 13 retained sweet sprites; found {sorted(actual_candies)}")

for filename in CANDIES:
    image = candy_dir / filename
    data = image.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        fail(f"{filename} is not PNG")
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (16, 16):
        fail(f"{filename} should remain the original 16x16 sprite, got {width}x{height}")

key = ITEM_TEX / "room_key.png"
if not key.exists():
    fail("custom room-key icon is missing")
data = key.read_bytes()
if data[:8] != b"\x89PNG\r\n\x1a\n":
    fail("room_key.png is not PNG")
width, height = struct.unpack(">II", data[16:24])
if (width, height) != (32, 32):
    fail(f"room key should be 32x32, got {width}x{height}")

items_yml = (ASSET_ROOT / "configs/items.yml").read_text(encoding="utf-8")
for old in ("crimson_warden", "hunter_mask", "cursed_talisman", "halloween_token",
            "haunted_map", "cursed_candy", "decoration_grave_marker"):
    if old in items_yml:
        fail(f"retired item still registered: {old}")
if "secret_room_key:" not in items_yml:
    fail("secret-room key is not registered in ItemsAdder")

config = (ROOT / "src/main/resources/config.yml").read_text(encoding="utf-8").lower()
for old in ("random-events:", "special-mobs:", "grave-encounters:", "headless-nick:",
            "witching-hour:", "atmosphere:", "rewards:", "season:"):
    if old in config:
        fail(f"retired system remains in default config: {old}")

plugin_yml = (ROOT / "src/main/resources/plugin.yml").read_text(encoding="utf-8")
for old in ("MythicMobs", "ModelEngine", "halloweencore.event.manage", "bosses"):
    if old in plugin_yml:
        fail(f"retired integration remains in plugin.yml: {old}")

print("Scope check passed: only dialogue quests, secret-room keys and retained sweet textures remain.")
