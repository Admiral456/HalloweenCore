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

After adding or changing ItemsAdder content, rebuild the server resource pack with /iazip.

## Music licensing

Plánovaná hlavní Halloween hudba je externí asset, ne AI-generovaná hudba.

- Candidate track: **Creepy** — TokyoGeisha
- Source: https://opengameart.org/content/creepy
- License: **CC0**
- The source page describes it as a creepy horror loop and states that no credit is necessary.
- The repository currently does **not** bundle the audio binary yet; the custom sound ID remains prepared as `halloween:haunted_theme`.

Do resource packu nepřidávat hudbu z náhodného YouTube uploadu. YouTube Audio Library je určena především pro videa a u standardních licencí může být omezená samostatná distribuce audio souboru. Pro Minecraft pack proto preferujeme zdroj s explicitními právy k redistribuci, například CC0.
## Vampire model assets

Plánované soubory pro finální 3D model patří do samostatného ModelEngine asset balíku. Dokud není potvrzena finální UV mapa, samotný model ani jeho textury se nepovažují za produkčně hotové. Konceptový vizuál vznikl v rámci návrhu, ale není vydáván jako finální UV texture atlas.
