#!/usr/bin/env python3
"""Generate deterministic starter textures and item-model JSON for the native mod.

These are valid source assets with separate item, armor, elytra and mob textures.
They are deliberately labelled as first-pass art: bespoke animated mob meshes and
the articulated Cowl/Batwing render layers are a separate remaining task.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "mod-neoforge/src/main/resources/assets/halloweencore"

INK = (13, 11, 18, 255)
SHADOW = (28, 26, 36, 255)
IRON = (63, 66, 81, 255)
STEEL = (131, 141, 158, 255)
BONE = (211, 201, 179, 255)
CRIMSON = (135, 15, 42, 255)
RUBY = (215, 35, 62, 255)
GOLD = (226, 166, 61, 255)
PURPLE = (90, 47, 132, 255)
CLEAR = (0, 0, 0, 0)

ITEM_IDS = [
    "cursed_talisman", "nocturne_sovereign_cowl", "batwing_elytra",
    "crimson_warden_helmet", "crimson_warden_chestplate", "crimson_warden_leggings",
    "crimson_warden_boots", "crimson_warden_sword", "crimson_warden_pickaxe",
    "crimson_warden_axe", "crimson_warden_shovel", "crimson_warden_hoe",
    "crimson_warden_shield", "halloween_token", "cursed_candy", "haunted_map"
]
MOB_IDS = [
    "cursed_zombie", "void_reaper", "gravekeeper", "frost_stalker",
    "blood_spider", "pumpkin_wraith", "hex_witch", "nightmare",
    "headless_nick", "goblin_king", "vampire_king"
]

def icon_for(item_id: str) -> Image.Image:
    image = Image.new("RGBA", (32, 32), CLEAR)
    d = ImageDraw.Draw(image)
    d.rectangle((3, 3, 28, 28), fill=SHADOW, outline=STEEL, width=1)
    d.rectangle((5, 5, 26, 26), outline=INK, width=1)
    if item_id == "cursed_talisman":
        d.ellipse((9, 8, 23, 24), fill=IRON, outline=GOLD, width=2)
        d.polygon([(16, 10), (21, 16), (16, 22), (11, 16)], fill=RUBY, outline=BONE)
        d.point((15, 13), fill=(255, 240, 200, 255))
    elif "cowl" in item_id:
        d.polygon([(8, 21), (7, 11), (12, 6), (21, 6), (26, 12), (24, 22), (20, 26), (12, 26)],
                  fill=INK, outline=STEEL)
        d.rectangle((11, 14, 21, 19), fill=SHADOW, outline=BONE)
        d.point((13, 16), fill=RUBY); d.point((19, 16), fill=RUBY)
        d.polygon([(16, 7), (19, 12), (16, 15), (13, 12)], fill=GOLD, outline=CRIMSON)
    elif "elytra" in item_id:
        d.polygon([(15, 7), (10, 11), (5, 8), (7, 20), (13, 26), (16, 22)],
                  fill=CRIMSON, outline=STEEL)
        d.polygon([(17, 7), (22, 11), (27, 8), (25, 20), (19, 26), (16, 22)],
                  fill=CRIMSON, outline=STEEL)
        d.line((7, 10, 12, 16, 13, 23), fill=BONE, width=1)
        d.line((25, 10, 20, 16, 19, 23), fill=BONE, width=1)
    elif "sword" in item_id:
        d.polygon([(22, 5), (25, 8), (14, 20), (11, 17)], fill=STEEL, outline=BONE)
        d.line((10, 18, 17, 24), fill=GOLD, width=3)
        d.line((8, 23, 12, 27), fill=CRIMSON, width=3)
    elif "pickaxe" in item_id or "axe" in item_id or "shovel" in item_id or "hoe" in item_id:
        d.line((11, 10, 23, 25), fill=GOLD, width=3)
        if "pickaxe" in item_id:
            d.line((5, 10, 20, 7, 26, 12), fill=STEEL, width=4)
        elif "axe" in item_id:
            d.polygon([(7, 6), (18, 8), (15, 17), (8, 15)], fill=CRIMSON, outline=BONE)
        elif "shovel" in item_id:
            d.polygon([(7, 6), (16, 8), (17, 15), (10, 18), (6, 13)], fill=STEEL, outline=BONE)
        else:
            d.line((7, 8, 25, 7), fill=STEEL, width=3)
            d.line((14, 8, 10, 15), fill=CRIMSON, width=3)
    elif "shield" in item_id:
        d.polygon([(8, 7), (24, 7), (23, 19), (16, 27), (9, 19)], fill=IRON, outline=STEEL, width=2)
        d.polygon([(16, 10), (20, 16), (16, 22), (12, 16)], fill=CRIMSON, outline=GOLD)
    elif "helmet" in item_id:
        d.pieslice((7, 6, 25, 25), 180, 360, fill=IRON, outline=STEEL, width=2)
        d.rectangle((7, 15, 25, 21), fill=CRIMSON, outline=STEEL)
        d.rectangle((11, 13, 13, 18), fill=BONE); d.rectangle((19, 13, 21, 18), fill=BONE)
    elif "chestplate" in item_id:
        d.polygon([(9, 7), (13, 5), (19, 5), (23, 7), (27, 14), (22, 17),
                   (22, 25), (10, 25), (10, 17), (5, 14)], fill=IRON, outline=STEEL, width=2)
        d.polygon([(16, 9), (20, 14), (16, 20), (12, 14)], fill=CRIMSON, outline=GOLD)
    elif "leggings" in item_id:
        d.polygon([(7, 6), (25, 6), (23, 15), (21, 26), (16, 26), (15, 17),
                   (12, 26), (7, 26), (8, 15)], fill=IRON, outline=STEEL, width=2)
    elif "boots" in item_id:
        d.polygon([(8, 6), (15, 6), (16, 20), (25, 22), (25, 26), (9, 26)],
                  fill=IRON, outline=STEEL, width=2)
        d.line((10, 21, 22, 21), fill=CRIMSON, width=2)
    elif "map" in item_id:
        d.polygon([(7, 7), (14, 5), (21, 8), (26, 6), (25, 24), (19, 27), (13, 24), (7, 26)],
                  fill=BONE, outline=GOLD)
        d.line((11, 11, 22, 19), fill=CRIMSON, width=2)
        d.ellipse((17, 12, 21, 16), fill=PURPLE)
    elif "candy" in item_id:
        d.polygon([(11, 13), (4, 8), (4, 22), (11, 18)], fill=GOLD, outline=BONE)
        d.rectangle((10, 11, 23, 20), fill=CRIMSON, outline=BONE)
        d.polygon([(22, 11), (28, 8), (27, 22), (22, 20)], fill=GOLD, outline=BONE)
    else:
        d.ellipse((9, 7, 23, 25), fill=CRIMSON, outline=GOLD, width=2)
        d.rectangle((12, 12, 20, 18), fill=STEEL, outline=INK)
    for x, y in ((6, 6), (25, 25)):
        d.point((x, y), fill=RUBY)
    return image

def equipment_texture(kind: str, size=(64, 32)) -> Image.Image:
    image = Image.new("RGBA", size, CLEAR)
    d = ImageDraw.Draw(image)
    w, h = size
    for y in range(h):
        for x in range(w):
            if ((x // 4) + (y // 4)) % 5 == 0:
                image.putpixel((x, y), IRON)
            elif ((x // 8) + (y // 8)) % 2 == 0:
                image.putpixel((x, y), SHADOW)
            else:
                image.putpixel((x, y), INK)
    for x in range(0, w, 8):
        d.line((x, 0, x, h-1), fill=STEEL if x % 16 else CRIMSON, width=1)
    for y in range(0, h, 8):
        d.line((0, y, w-1, y), fill=INK, width=1)
    if kind in ("crimson_warden", "crimson_warden_leggings"):
        d.rectangle((18, 5, 29, 14), fill=CRIMSON, outline=GOLD)
        d.polygon([(24, 6), (28, 9), (24, 13), (20, 9)], fill=RUBY, outline=BONE)
        d.line((4, 20, 14, 27, 26, 20), fill=BONE, width=2)
    elif kind == "nocturne_cowl":
        d.rectangle((8, 1, 55, 28), fill=INK, outline=STEEL)
        d.arc((12, 0, 51, 26), 180, 360, fill=BONE, width=2)
        d.rectangle((19, 11, 27, 15), fill=RUBY)
        d.rectangle((37, 11, 45, 15), fill=RUBY)
        d.polygon([(32, 5), (38, 11), (32, 18), (26, 11)], fill=GOLD, outline=CRIMSON)
    elif kind == "batwing_elytra":
        d.polygon([(31, 1), (17, 6), (4, 2), (7, 20), (18, 30), (31, 23),
                   (33, 23), (46, 30), (57, 20), (60, 2), (47, 6)], fill=CRIMSON, outline=BONE)
        d.line((10, 5, 17, 19, 28, 25), fill=GOLD, width=2)
        d.line((54, 5, 47, 19, 36, 25), fill=GOLD, width=2)
    else:
        d.rectangle((8, 6, 55, 27), outline=CRIMSON, width=2)
    return image

def entity_texture(mob_id: str) -> Image.Image:
    image = Image.new("RGBA", (64, 64), INK)
    d = ImageDraw.Draw(image)
    palettes = {
        "cursed_zombie": (53, 95, 62, 255), "void_reaper": (46, 25, 65, 255),
        "gravekeeper": (73, 74, 81, 255), "frost_stalker": (95, 173, 196, 255),
        "blood_spider": (90, 13, 33, 255), "pumpkin_wraith": (221, 100, 24, 255),
        "hex_witch": (88, 47, 117, 255), "nightmare": (24, 17, 30, 255),
        "headless_nick": (49, 44, 52, 255), "goblin_king": (101, 123, 55, 255),
        "vampire_king": (84, 15, 42, 255)
    }
    base = palettes.get(mob_id, CRIMSON)
    d.rectangle((0, 0, 63, 63), fill=base)
    for y in range(0, 64, 8):
        for x in range(0, 64, 8):
            if ((x // 8) + (y // 8)) % 3 == 0:
                d.rectangle((x, y, x + 3, y + 4), fill=SHADOW)
    d.rectangle((8, 8, 23, 23), fill=SHADOW, outline=STEEL)
    d.rectangle((40, 8, 55, 23), fill=SHADOW, outline=STEEL)
    d.point((12, 13), fill=RUBY); d.point((19, 13), fill=RUBY)
    d.point((44, 13), fill=RUBY); d.point((51, 13), fill=RUBY)
    d.line((8, 29, 25, 32, 40, 32, 55, 29), fill=INK, width=3)
    for x in range(4, 64, 12):
        d.line((x, 42, x + 6, 49, x + 2, 56), fill=GOLD if mob_id in ("goblin_king", "vampire_king") else STEEL, width=2)
    return image

def save_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def main() -> None:
    item_texture_dir = ASSETS / "textures/item"
    item_model_dir = ASSETS / "models/item"
    item_def_dir = ASSETS / "items"
    for item_id in ITEM_IDS:
        image = icon_for(item_id)
        target = item_texture_dir / (item_id + ".png")
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target, format="PNG", optimize=True)
        save_json(item_model_dir / (item_id + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "halloweencore:item/" + item_id}
        })
        save_json(item_def_dir / (item_id + ".json"), {
            "model": {"type": "minecraft:model", "model": "halloweencore:item/" + item_id}
        })

    equipment_root = ASSETS / "textures/entity/equipment"
    texture_map = {
        "humanoid/crimson_warden.png": equipment_texture("crimson_warden"),
        "humanoid_leggings/crimson_warden_leggings.png": equipment_texture("crimson_warden_leggings"),
        "humanoid/nocturne_cowl.png": equipment_texture("nocturne_cowl"),
        "wings/batwing_elytra.png": equipment_texture("batwing_elytra")
    }
    for relative, image in texture_map.items():
        target = equipment_root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target, format="PNG", optimize=True)

    entity_root = ASSETS / "textures/entity"
    for mob_id in MOB_IDS:
        target = entity_root / (mob_id + ".png")
        target.parent.mkdir(parents=True, exist_ok=True)
        entity_texture(mob_id).save(target, format="PNG", optimize=True)

    # Validate every generated PNG by decoding its pixels and checking dimensions.
    for path in ASSETS.rglob("*.png"):
        with Image.open(path) as image:
            image.load()
            if image.width <= 0 or image.height <= 0:
                raise RuntimeError(f"Invalid generated texture: {path}")
    print(f"Generated {len(ITEM_IDS)} item icons/models, {len(texture_map)} equipment textures, and {len(MOB_IDS)} first-pass mob textures.")

if __name__ == "__main__":
    main()
