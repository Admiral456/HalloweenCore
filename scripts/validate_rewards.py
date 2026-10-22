#!/usr/bin/env python3
from pathlib import Path
import re,yaml

root=Path(__file__).resolve().parents[1]
cfg=yaml.safe_load((root/"src/main/resources/config.yml").read_text())
if "secret-rooms" in cfg or "key-chances" in str(cfg):
    raise SystemExit("Room/key settings remain")
allowed={"season","activeWindow","quest-rewards","daily-quest","shop"}
if set(cfg.keys())!=allowed:
    raise SystemExit("Legacy event configuration remains")
rewards=cfg.get("quest-rewards",{})
bounds={"light":(3,5),"medium":(6,9),"hard":(10,15),"very-hard":(16,25)}
if rewards.get("default-difficulty") not in bounds:
    raise SystemExit("Invalid difficulty")
for name,bound in bounds.items():
    tier=rewards.get("difficulties",{}).get(name,{})
    if (tier.get("min-fragments"),tier.get("max-fragments"))!=bound or "key-chances" in tier:
        raise SystemExit(f"Invalid reward range: {name}")
for quest_id,entry in rewards.get("quests",{}).items():
    if not re.fullmatch(r"[a-z0-9_-]{1,48}",str(quest_id)) or not isinstance(entry,dict) or "key-chances" in entry:
        raise SystemExit(f"Invalid quest override: {quest_id}")
if cfg.get("daily-quest",{}).get("reward-fragments")!=1:
    raise SystemExit("Daily reward default should be 1")
prices=cfg.get("shop",{}).get("prices",{})
required=["candy","iron","gold","diamond","emerald","netherite-ingot","netherite-scrap","golden-apple",
          "enchanted-golden-apple","totem","trident","mace","netherite-sword","netherite-axe",
          "netherite-helmet","netherite-chestplate","netherite-leggings","netherite-boots","elytra","netherite-spear"]
for key in required:
    if not isinstance(prices.get(key),int) or prices[key]<0:
        raise SystemExit(f"Invalid price: {key}")
if prices["elytra"]<=prices["diamond"] or prices["mace"]<=prices["iron"]:
    raise SystemExit("Rare prices too low")
window=cfg.get("activeWindow",{})
if (window.get("startMonth"),window.get("startDay"),window.get("endMonth"),window.get("endDay"))!=(10,23,11,2):
    raise SystemExit("Wrong season window")
print("Reward and shop validation passed.")
