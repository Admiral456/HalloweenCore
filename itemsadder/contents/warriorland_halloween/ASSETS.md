# WarriorLand Halloween 2026 asset manifest

Namespace:
- warriorland_halloween

Included item textures (32x32 PNG):
- resourcepack/assets/warriorland_halloween/textures/item/hunter_mask.png
- resourcepack/assets/warriorland_halloween/textures/item/cursed_talisman.png
- resourcepack/assets/warriorland_halloween/textures/item/halloween_token.png
- resourcepack/assets/warriorland_halloween/textures/item/cursed_candy.png
- resourcepack/assets/warriorland_halloween/textures/item/haunted_map.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_helmet.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_chestplate.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_leggings.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_boots.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_sword.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_shovel.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_pickaxe.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_axe.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_hoe.png
- resourcepack/assets/warriorland_halloween/models/item/crimson_warden_sword.json
- resourcepack/assets/warriorland_halloween/models/item/crimson_warden_shovel.json
- resourcepack/assets/warriorland_halloween/models/item/crimson_warden_pickaxe.json
- resourcepack/assets/warriorland_halloween/models/item/crimson_warden_axe.json
- resourcepack/assets/warriorland_halloween/models/item/crimson_warden_hoe.json
- resourcepack/assets/warriorland_halloween/textures/armor/crimson_warden/layer_1.png
- resourcepack/assets/warriorland_halloween/textures/armor/crimson_warden/layer_2.png

Included client/audio assets:
- resourcepack/assets/minecraft/shaders/core/sky.fsh — seasonal orange/crimson sky tint
- resourcepack/assets/minecraft/sounds.json — replaces vanilla music events with a silent OGG while the Halloween pack is active
- resourcepack/assets/warriorland_halloween/sounds/halloween_silence.ogg — one-second silent replacement file
- textures/armor/crimson_warden/layer_1.png and layer_2.png — ItemsAdder equipment source layers
- sounds/haunted_theme.ogg — Spooky Fester ambient loop (duration measured at build time) (mono OGG/Vorbis)
- sounds/event_sting.ogg — original 5-second Halloween event cue (mono OGG/Vorbis)
- configs/sounds.yml — ItemsAdder sound registration

Ambient music is Spooky Fester by Eldritch Grim from OpenGameArt (CC0); it is downloaded and converted to OGG during CI. The short event cue is generated locally. Rebuild the ItemsAdder resource pack with /iazip after installing/updating these contents.
The custom armor uses a shared Crimson Warden equipment texture set, built from 64x32 layer_1/layer_2 atlases. Its four shop icons have been redrawn in a consistent black/crimson/gold style. The five tools use dedicated `minecraft:item/handheld` model JSON files so their icons also render in-hand with the normal tool pose. Place those equipment layers in the source path `plugins/ItemsAdder/contents/warriorland_halloween/textures/armor/crimson_warden/`; item icons, sounds.json, and the sky shader are emitted from `resourcepack/assets/`. It uses Netherite armor materials with +1 armor per piece (24 armor points total for the full set versus 20 for full vanilla Netherite) and extended durability, while retaining normal enchantability.

ItemsAdder combines all content namespaces into one generated server resource pack; `warriorland_halloween` is a namespace within the pack, not a second pack in the Minecraft resource-pack menu. After `/iazip`, check `/iainfo` for the hosted URL and request/status. For ItemsAdder 4.0.17+, `resource-pack.hosting.simple_self_host.enabled: true` with `server_address: auto` is often the simplest hosting choice. Set the installed language file's `resourcepack-popup-message` to a branded message such as `&6WarriorLand Halloween 2026` if you want players to recognize the pack prompt.

The Java plugin / shop uses these ItemsAdder IDs:
- warriorland_halloween:hunter_mask
- warriorland_halloween:cursed_talisman
- warriorland_halloween:halloween_token
- warriorland_halloween:cursed_candy
- warriorland_halloween:haunted_map
- warriorland_halloween:crimson_warden_helmet
- warriorland_halloween:crimson_warden_chestplate
- warriorland_halloween:crimson_warden_leggings
- warriorland_halloween:crimson_warden_boots
- warriorland_halloween:crimson_warden_sword
- warriorland_halloween:crimson_warden_shovel
- warriorland_halloween:crimson_warden_pickaxe
- warriorland_halloween:crimson_warden_axe
- warriorland_halloween:crimson_warden_hoe

Sound IDs used by the plugin:
- `warriorland_halloween:haunted_theme` — background loop, played at the generated track duration
- `warriorland_halloween:event_sting` — short cue played when a random event starts

The audio generator is `scripts/generate_halloween_audio.py`. It downloads and converts the CC0 Spooky Fester track, generates the event sting, and creates the silent vanilla-music replacement OGG for CI/local packaging. After installing/updating the content, run `/iazip` and make sure players receive the rebuilt server resource pack.
## Crimson Warden gear

The shop includes four armor items and five upgraded netherite-based tools (sword, shovel, pickaxe, axe, hoe). All have custom 32×32 pixel-art icons, increased durability and no blocked enchantments. The tools set higher main-hand damage/attack speed modifiers; normal vanilla enchantments remain available on the underlying netherite material.

## Vampire model assets

Plánované soubory pro finální 3D model patří do samostatného ModelEngine asset balíku. Dokud není potvrzena finální UV mapa, samotný model ani jeho textury se nepovažují za produkčně hotové. Konceptový vizuál vznikl v rámci návrhu, ale není vydáván jako finální UV texture atlas.
