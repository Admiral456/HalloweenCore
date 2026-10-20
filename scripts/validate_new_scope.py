from pathlib import Path
import hashlib
import json
import re
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
if data[25] != 6:
    fail("room_key.png must retain an RGBA alpha channel")

manifest_paths = (
    ASSET_ROOT / "THIRD_PARTY_LICENSES/stevs-halloween-source-manifest.json",
    ROOT / "third-party-assets/stevs-halloween/asset-source-manifest.json",
)
manifests = []
for manifest_path in manifest_paths:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifests.append(manifest)
    if len(manifest.get("assets", [])) != 13:
        fail(f"source manifest must list 13 retained sweets: {manifest_path}")
    if any("apple_leaves_with_apples.png" in item.get("target_path", "") for item in manifest["assets"]):
        fail(f"non-sweet decorative leaf texture should not be included: {manifest_path}")
    for item in manifest["assets"]:
        target = ROOT / item["target_path"]
        if not target.is_file():
            fail(f"manifest target is missing: {target}")
        actual_hash = hashlib.sha256(target.read_bytes()).hexdigest()
        if actual_hash != item.get("sha256"):
            fail(f"imported sweet texture hash differs from its source manifest: {target}")
if manifests[0] != manifests[1]:
    fail("the source manifest shipped with ItemsAdder must match the repository manifest")

items_yml = (ASSET_ROOT / "configs/items.yml").read_text(encoding="utf-8")
for old in ("crimson_warden", "hunter_mask", "cursed_talisman", "halloween_token",
            "haunted_map", "cursed_candy", "decoration_grave_marker"):
    if old in items_yml:
        fail(f"retired item still registered: {old}")
if "secret_room_key:" not in items_yml:
    fail("common secret-room key is not registered in ItemsAdder")
for item_id in ("secret_room_key_rare:", "secret_room_key_legendary:"):
    if item_id not in items_yml:
        fail(f"rarity key is not registered in ItemsAdder: {item_id}")

config = (ROOT / "src/main/resources/config.yml").read_text(encoding="utf-8").lower()
for old in ("random-events:", "special-mobs:", "grave-encounters:", "headless-nick:",
            "witching-hour:", "atmosphere:", "rewards:", "season:"):
    if old in config:
        fail(f"retired system remains in default config: {old}")

plugin_yml = (ROOT / "src/main/resources/plugin.yml").read_text(encoding="utf-8")
for old in ("MythicMobs", "ModelEngine", "halloweencore.event.manage", "bosses"):
    if old in plugin_yml:
        fail(f"retired integration remains in plugin.yml: {old}")

config_text = (ROOT / "src/main/resources/config.yml").read_text(encoding="utf-8")
quest_block = config_text.split("quests:\n", 1)[1].split("\nrooms:", 1)[0]
quest_ids = re.findall(r"^    ([a-z][a-z0-9_-]*):$", quest_block, flags=re.MULTILINE)
expected_quests = {
    "fresh_harvest", "stone_for_repairs", "paper_trail", "food_for_watch", "village_library",
    "copper_samples", "iron_requisition", "amethyst_study", "alchemist_components",
    "archivist_records", "blacksmith_order", "deep_expedition", "hidden_room_report",
    "sealed_archive", "night_research", "mushroom_samples", "wool_for_bedding", "river_catch",
    "clay_for_potter", "lantern_supplies", "honey_for_healer", "forest_materials",
    "leather_repair", "map_and_compass", "miner_samples",
}
if set(quest_ids) != expected_quests:
    fail(f"quest catalogue mismatch: found {sorted(quest_ids)}")
for difficulty in ("Lehký", "Střední", "Těžký", "Velmi těžký"):
    if difficulty not in quest_block:
        fail(f"quest catalogue is missing the {difficulty} difficulty tier")
if "reward-fragments:" in quest_block or "reward-items:" in quest_block:
    fail("quest rewards were configured before being agreed")

rooms_config = config_text.split("rooms:\n", 1)[1]
if "chance-per-new-chunk: 0.025" not in rooms_config:
    fail("default secret-room generation chance must remain 2.5% per new chunk")
if "min-y: -36" not in rooms_config or "max-y: -8" not in rooms_config:
    fail("default room generation height should stay underground at Y=-36 through Y=-8")
for rarity_weight in ("common: 70", "rare: 25", "legendary: 5"):
    if rarity_weight not in rooms_config:
        fail(f"room rarity weights are incomplete: {rarity_weight}")
for drop_chance in ("common: 0.40", "rare: 0.10", "legendary: 0.05"):
    if drop_chance not in config_text:
        fail(f"villager quest key drop chance is missing: {drop_chance}")
if "key-drop-chance: 0.15" in config:
    fail("obsolete global key-drop chance is still configured")
room_java = (JAVA_DIR / "SecretRoomManager.java").read_text(encoding="utf-8")
for feature in (
    "event.isNewChunk()", "chunkAttemptedKey", "isSafeRoomVolume", "recordGeneratedRoom",
    "generateTestRoom", "findAccessTunnel", "isSafeTunnelCeiling", "world.isChunkLoaded", "isNaturalCaveOpening", "decorateEntranceArch", "locateNearest", "locateAll", "onPlayerEnterSecretRoom",
    "setDecorationIfAir", "secret_room_locked", "secret_room_rarity", "secret_room_managed", "consumeOne",
    "migrateLegacyRoomRecords", "rollRarity", "roomShellMaterial", "getChestRarity",
    "protectRecordedChestsInLoadedChunks", "verifyRecordedRooms", "onProtectedChestBreak",
    "onProtectedChestBurn", "onEntityExplosion", "onBlockExplosion", "onProtectedChestPistonExtend",
    "onProtectedChestPistonRetract", "onProtectedChestTransfer", "InventoryMoveItemEvent",
    "BlockPistonExtendEvent", "BlockPistonRetractEvent", "isProtectedChest", "isProtectedInventory"
):
    if feature not in room_java:
        fail(f"secret-room runtime contract is missing: {feature}")

core_java = (JAVA_DIR / "HalloweenCore.java").read_text(encoding="utf-8")
for alias in ("PlayerCommandPreprocessEvent", 'command.equals("/locate")',
              'command.equals("/locateall")', "onSecretRoomLocateAlias"):
    if alias not in core_java:
        fail(f"locate alias contract is missing: {alias}")

quest_java = (JAVA_DIR / "HalloweenQuestManager.java").read_text(encoding="utf-8")
for feature in ("isConfiguredQuest", "TurnInResult turnIn", "required-items",
                ".removeItem(", "objective-missing"):
    if feature not in quest_java:
        fail(f"quest turn-in contract is missing: {feature}")
for feature in ("rollBonusKeyDrop", "key-drop-chances.common", "key-drop-chances.rare",
                "key-drop-chances.legendary", "clampProbability"):
    if feature not in quest_java:
        fail(f"villager quest bonus key drop is missing: {feature}")
command_java = (JAVA_DIR / "HalloweenCommand.java").read_text(encoding="utf-8")
for feature in ('action.equals("turnin")', "rooms generate", "private boolean handleLocate",
                "/locate halloweenroom"):
    if feature not in command_java:
        fail(f"required player/admin command is missing: {feature}")
if "verifyRecordedRooms" not in command_java or 'action.equals("verify")' not in command_java:
    fail("admin room integrity verification command is missing")

dialogue_library = ROOT / "docs/DIALOGUE_LIBRARY_CZ.md"
if not dialogue_library.is_file():
    fail("the Czech dialogue catalogue is missing")
dialogue_text = dialogue_library.read_text(encoding="utf-8")
if len(re.findall(r"^## \d+\.", dialogue_text, flags=re.MULTILINE)) != 25:
    fail("the Czech dialogue catalogue must contain all 25 quest dialogues")
for quest_id in expected_quests:
    if quest_id not in dialogue_text:
        fail(f"dialogue catalogue is missing quest {quest_id}")

print("Scope check passed: 25 dialogue quests, 40/10/5 percent key drops, protected tiered rooms/chests and retained sweet textures.")
