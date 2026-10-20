# WarriorLand retained ItemsAdder content

This content intentionally contains only:
- the original custom secret-room key icon, a transparent 32×32 pixel-art PNG with a gold key and purple gem;
- thirteen 16×16 sweet/item sprites imported unchanged from Stev's Halloween under the MIT licence.

## Key

- Item ID: warriorland_halloween:secret_room_key
- Texture path: resourcepack/assets/warriorland_halloween/textures/item/room_key.png
- Registered in configs/items.yml as graphics.texture: item/room_key.
- PNG: 32×32 RGBA, transparent background. It works as an ordinary ItemsAdder item texture; no OptiFine is required for the icon.

## Retained sweets from Stev's Halloween

The files under resourcepack/assets/warriorland_halloween/textures/item/stev_candy/ are unchanged upstream 16×16 sprites:
apple_core, candy_apple, candy_bag, caramel, caramel_apple, chocolate_bar, frosted_sugar_cookie, gumdrop, hard_candy, lollipop, peanut_butter_cup, sugar_cookie and toasted_pumpkin_seeds.

Attribution, licence and source hashes are retained in THIRD_PARTY_NOTICES.md, THIRD_PARTY_LICENSES/stevs-halloween-MIT.txt and the Stev's Halloween source manifest.

## Intentionally not included

No custom mob models, bosses, event audio, shader/sky assets, recoloured vanilla block textures, custom armour/weapons, graveyard furniture or old Halloween relic icons are packaged.

Copy this namespace into plugins/ItemsAdder/contents/ and run /iazip. Check the generated delivery with /iainfo.
