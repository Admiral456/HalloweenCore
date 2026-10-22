#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,struct,yaml
root=Path(__file__).resolve().parents[1];base=root/"itemsadder/contents/warriorland_halloween";tex=base/"resourcepack/assets/warriorland_halloween/textures/item"
expected={"apple_core.png","candy_apple.png","candy_bag.png","caramel.png","caramel_apple.png","chocolate_bar.png","frosted_sugar_cookie.png","gumdrop.png","hard_candy.png","lollipop.png","peanut_butter_cup.png","sugar_cookie.png","toasted_pumpkin_seeds.png"}
actual={p.name for p in (tex/"stev_candy").glob("*.png")}
if actual!=expected:raise SystemExit("Candy texture set mismatch")
manifest=json.loads((root/"third-party-assets/stevs-halloween/asset-source-manifest.json").read_text())
if len(manifest.get("assets",[]))!=13:raise SystemExit("Expected 13 candy assets")
for a in manifest["assets"]:
 p=root/a["target_path"]
 if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest()!=a["sha256"]:raise SystemExit(f"Hash mismatch: {p}")
 with p.open("rb") as s:h=s.read(24)
 if h[:8]!=bytes([137,80,78,71,13,10,26,10]) or struct.unpack(">II",h[16:24])!=(16,16):raise SystemExit(f"Invalid PNG {p}")
items=(base/"configs/items.yml").read_text()
for n in ["stev_apple_core","stev_candy_apple","stev_candy_bag","stev_caramel","stev_caramel_apple","stev_chocolate_bar","stev_frosted_sugar_cookie","stev_gumdrop","stev_hard_candy","stev_lollipop","stev_peanut_butter_cup","stev_sugar_cookie","stev_toasted_pumpkin_seeds"]:
 if f"  {n}:" not in items:raise SystemExit(f"Missing candy {n}")
if "secret_key" in items or (tex/"secret_key.png").exists():raise SystemExit("Key remains")
java=root/"src/main/java/cz/halloween/core"
if (java/"KeyItemFactory.java").exists() or (java/"SecretRoomManager.java").exists():raise SystemExit("Room/key class remains")
cfg=yaml.safe_load((root/"src/main/resources/config.yml").read_text())
if "secret-rooms" in cfg or "key-chances" in str(cfg):raise SystemExit("Room/key config remains")
w=cfg.get("activeWindow",{})
if (w.get("startMonth"),w.get("startDay"),w.get("endMonth"),w.get("endDay"))!=(10,23,11,2):raise SystemExit("Wrong season dates")
if cfg.get("fog",{}).get("enabled") is not False:raise SystemExit("Fog enabled")
if not (java/"DailyQuestManager.java").is_file() or not (java/"HalloweenShopManager.java").is_file():raise SystemExit("Quest/shop missing")
print("Scope validation passed.")
