#!/usr/bin/env python3
from pathlib import Path
import re,yaml
root=Path(__file__).resolve().parents[1];cfg=yaml.safe_load((root/"src/main/resources/config.yml").read_text())
if "secret-rooms" in cfg:raise SystemExit("Rooms remain")
r=cfg.get("quest-rewards",{});bounds={"light":(3,5),"medium":(6,9),"hard":(10,15),"very-hard":(16,25)}
if r.get("default-difficulty") not in bounds:raise SystemExit("Invalid difficulty")
for n,b in bounds.items():
 t=r.get("difficulties",{}).get(n,{})
 if (t.get("min-fragments"),t.get("max-fragments"))!=b or "key-chances" in t:raise SystemExit(f"Invalid reward range {n}")
for q,e in r.get("quests",{}).items():
 if not re.fullmatch(r"[a-z0-9_-]{1,48}",str(q)) or not isinstance(e,dict) or "key-chances" in e:raise SystemExit(f"Invalid quest override {q}")
if cfg.get("daily-quest",{}).get("reward-fragments")!=1:raise SystemExit("Daily reward default should be 1")
p=cfg.get("shop",{}).get("prices",{});need=["candy","iron","gold","diamond","emerald","netherite-ingot","netherite-scrap","golden-apple","enchanted-golden-apple","totem","trident","mace","netherite-sword","netherite-axe","netherite-helmet","netherite-chestplate","netherite-leggings","netherite-boots","elytra","netherite-spear"]
for k in need:
 if not isinstance(p.get(k),int) or p[k]<0:raise SystemExit(f"Invalid price {k}")
if p["elytra"]<=p["diamond"] or p["mace"]<=p["iron"]:raise SystemExit("Rare prices too low")
w=cfg.get("activeWindow",{})
if (w.get("startMonth"),w.get("startDay"),w.get("endMonth"),w.get("endDay"))!=(10,23,11,2):raise SystemExit("Wrong season window")
if cfg.get("fog",{}).get("enabled") is not False:raise SystemExit("Fog enabled")
print("Reward validation passed.")
