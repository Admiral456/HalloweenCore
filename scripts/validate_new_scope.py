#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,struct,xml.etree.ElementTree as ET
import yaml

root=Path(__file__).resolve().parents[1]
base=root/"itemsadder/contents/warriorland_halloween"
tex=base/"resourcepack/assets/warriorland_halloween/textures/item/stev_candy"
expected={"apple_core.png","candy_apple.png","candy_bag.png","caramel.png","caramel_apple.png","chocolate_bar.png","frosted_sugar_cookie.png","gumdrop.png","hard_candy.png","lollipop.png","peanut_butter_cup.png","sugar_cookie.png","toasted_pumpkin_seeds.png"}
actual={p.name for p in tex.glob("*.png")}
if actual!=expected:
    raise SystemExit("Candy texture set mismatch")

manifest=json.loads((root/"third-party-assets/stevs-halloween/asset-source-manifest.json").read_text())
if len(manifest.get("assets",[]))!=13:
    raise SystemExit("Expected 13 candy assets")
for asset in manifest["assets"]:
    path=root/asset["target_path"]
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=asset["sha256"]:
        raise SystemExit(f"Hash mismatch: {path}")
    with path.open("rb") as stream:
        header=stream.read(24)
    if header[:8]!=bytes([137,80,78,71,13,10,26,10]) or struct.unpack(">II",header[16:24])!=(16,16):
        raise SystemExit(f"Invalid PNG: {path}")

items=(base/"configs/items.yml").read_text()
for item_id in [
    "stev_apple_core","stev_candy_apple","stev_candy_bag","stev_caramel","stev_caramel_apple",
    "stev_chocolate_bar","stev_frosted_sugar_cookie","stev_gumdrop","stev_hard_candy",
    "stev_lollipop","stev_peanut_butter_cup","stev_sugar_cookie","stev_toasted_pumpkin_seeds"
]:
    if f"  {item_id}:" not in items:
        raise SystemExit(f"Missing candy item: {item_id}")
if "secret_key" in items or (base/"resourcepack/assets/warriorland_halloween/textures/item/secret_key.png").exists():
    raise SystemExit("Key remains")
java=root/"src/main/java/cz/halloween/core"
if (java/"KeyItemFactory.java").exists() or (java/"SecretRoomManager.java").exists():
    raise SystemExit("Room/key class remains")
legacy=root/"src/main/java/cz/halloween/core/spooky"
if legacy.exists() and any(legacy.rglob("*.java")):
    raise SystemExit("Legacy mob/event source remains")
if (java/"horrornights"/"HorrorNightsManager.java").exists():
    raise SystemExit("Legacy HorrorNights source remains")
if (root/"src/main/resources/lang").exists() and any((root/"src/main/resources/lang").glob("*.yml")):
    raise SystemExit("Legacy event language files remain")
if (root/"src/main/resources/META-INF/licenses/spookyseason-MIT.txt").exists():
    raise SystemExit("Removed event license remains bundled")

cfg=yaml.safe_load((root/"src/main/resources/config.yml").read_text())
if set(cfg.keys())!={"season","activeWindow","quest-rewards","daily-quest","shop"}:
    raise SystemExit(f"Unexpected config section remains: {set(cfg.keys())}")
window=cfg["activeWindow"]
if (window.get("startMonth"),window.get("startDay"),window.get("endMonth"),window.get("endDay"))!=(10,23,11,2):
    raise SystemExit("Wrong season dates")
if cfg["season"].get("enabled") is not True:
    raise SystemExit("Season should be enabled")
if not (java/"DailyQuestManager.java").is_file() or not (java/"HalloweenShopManager.java").is_file():
    raise SystemExit("Quest/shop missing")

plugin=yaml.safe_load((root/"src/main/resources/plugin.yml").read_text())
if set(plugin.get("commands",{}))!={"halloween","quest"}:
    raise SystemExit("Legacy mob/event commands remain registered")
if set(plugin.get("permissions",{}))!={"halloweencore.use","halloweencore.admin","halloweencore.quest.dialogue"}:
    raise SystemExit("Legacy event permissions remain registered")

pom=ET.parse(root/"pom.xml").getroot()
ns={"m":"http://maven.apache.org/POM/4.0.0"}
includes={node.text for node in pom.findall(".//m:plugin[m:artifactId='maven-compiler-plugin']/m:configuration/m:includes/m:include",ns)}
needed={"cz/halloween/core/HalloweenCore.java","cz/halloween/core/DailyQuestManager.java","cz/halloween/core/DialogQuestManager.java","cz/halloween/core/HalloweenShopManager.java"}
if includes!=needed:
    raise SystemExit(f"Unexpected Maven compilation scope: {includes}")
print("Content scope validation passed.")
