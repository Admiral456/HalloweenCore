# WarriorLand Halloween 2026 asset manifest

Namespace:
- warriorland_halloween

Included item textures (32x32 PNG):
- resourcepack/assets/warriorland_halloween/textures/item/hunter_mask.png
- resourcepack/assets/warriorland_halloween/textures/item/cursed_talisman.png
- resourcepack/assets/warriorland_halloween/textures/item/halloween_token.png
- resourcepack/assets/warriorland_halloween/textures/item/cursed_candy.png
- resourcepack/assets/warriorland_halloween/textures/item/haunted_map.png

Included client/audio assets:
- resourcepack/assets/minecraft/shaders/core/sky.fsh
- sounds/haunted_theme.ogg — original 64-second ambient loop (mono OGG/Vorbis)
- sounds/event_sting.ogg — original 5-second Halloween event cue (mono OGG/Vorbis)
- configs/sounds.yml — ItemsAdder sound registration

The original sounds use no third-party recordings or samples. Rebuild the ItemsAdder resource pack with /iazip after installing/updating these contents.
The Java plugin uses these IDs:
- warriorland_halloween:hunter_mask
- warriorland_halloween:cursed_talisman
- warriorland_halloween:halloween_token
- warriorland_halloween:cursed_candy
- warriorland_halloween:haunted_map

Sound IDs used by the plugin:
- `warriorland_halloween:haunted_theme` — background loop, played every 64 seconds
- `warriorland_halloween:event_sting` — short cue played when a random event starts

The audio generator is `scripts/generate_halloween_audio.py`. It recreates both original OGG files for CI and local packaging. After installing/updating the content, run `/iazip` and make sure players receive the rebuilt server resource pack.
## Vampire model assets

Plánované soubory pro finální 3D model patří do samostatného ModelEngine asset balíku. Dokud není potvrzena finální UV mapa, samotný model ani jeho textury se nepovažují za produkčně hotové. Konceptový vizuál vznikl v rámci návrhu, ale není vydáván jako finální UV texture atlas.
