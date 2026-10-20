from pathlib import Path
import json
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
    "apple_core.png", "candy_apple.png", "candy_bag.png", "caramel.png",
    "caramel_apple.png", "chocolate_bar.png", "frosted_sugar_cookie.png", "gumdrop.png",
    "hard_candy.png", "lollipop.png", "peanut_butter_cup.png", "sugar_cookie.png",
    "toasted_pumpkin_seeds.png",
}
ASSET_ROOT = ROOT / "itemsadder/contents/warriorland_halloween"
ITEM_TEX = ASSET_ROOT / "resourcepack/assets/warriorland_halloween/textures/item"
ALLOWED_CONTENT_FILES = {
    "ASSETS.md",
    "THIRD_PARTY_NOTICES.md",
    "THIRD_PARTY_LICENSES/README.txt",
    "THIRD_PARTY_LICENSES/stevs-halloween-MIT.txt",
    "THIRD_PARTY_LICENSES/stevs-halloween-source-manifest.json",
    "configs/items.yml",
    "resourcepack/assets/warriorland_halloween/textures/item/room_key.png",
    *{f"resourcepack/assets/warriorland_halloween/textures/item/stev_candy/{name}" for name in CANDIES},
}

def fail(message):
    print(f"Scope validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)

actual_java = {p.name for p in JAVA_DIR.glob("*.java")}
if actual_java != EXPECTED_JAVA:
    fail(f"unexpected Java source set: {sorted(actual_java)}")

for retired in ("mythicmobs", "MUSIC_LICENSE.md", "VAMPIRE_BOSS.md"):
    if (ROOT / retired).exists():
        fail(f"retired system file/folder remains: {retired}")

if (ASSET_ROOT / "configs/sounds.yml").exists():
    fail("old Halloween event sound registration must be removed")

actual_content_files = {
    p.relative_to(ASSET_ROOT).as_posix()
    for p in ASSET_ROOT.rglob("*") if p.is_file()
}
if actual_content_files != ALLOWED_CONTENT_FILES:
    fail("unexpected ItemsAdder package file set: " + repr(sorted(actual_content_files ^ ALLOWED_CONTENT_FILES)))

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
data = key.read_bytes()
if data[:8] != b"\x89PNG\r\n\x1a\n":
    fail("room_key.png is not PNG")
width, height = struct.unpack(">II", data[16:24])
if (width, height) != (32, 32):
    fail(f"room key should be 32x32, got {width}x{height}")

for manifest_path in (
    ASSET_ROOT / "THIRD_PARTY_LICENSES/stevs-halloween-source-manifest.json",
    ROOT / "third-party-assets/stevs-halloween/asset-source-manifest.json",
):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if len(manifest.get("assets", [])) != 13:
        fail(f"source manifest must list 13 retained sweets: {manifest_path}")
    if any("apple_leaves_with_apples.png" in item.get("target_path", "") for item in manifest["assets"]):
        fail(f"non-sweet decorative leaf texture should not be included: {manifest_path}")

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
