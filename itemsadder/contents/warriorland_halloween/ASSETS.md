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
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_pickaxe.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_axe.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_shovel.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_hoe.png
- resourcepack/assets/warriorland_halloween/textures/item/crimson_warden_shield.png
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
The custom armor uses a shared Crimson Warden equipment texture set, built from 64x32 layer_1/layer_2 atlases. Place those equipment layers in `plugins/ItemsAdder/contents/warriorland_halloween/textures/armor/crimson_warden/`; item icons, modern JSON models, sounds.json, and the sky shader are emitted from `resourcepack/assets/`. It uses Netherite armor materials with configured armor values of 6/12/9/6 (33 armor points total for the full set versus 20 for full vanilla Netherite) and extended durability. The armor's geometry remains Minecraft's normal wearable armor mesh with its own texture overlay; the per-piece abilities and full-set ward are handled by HalloweenCore. The Crimson Warden sword, pickaxe, axe, shovel, and hoe now use their own UV-mapped voxel model generated from the crisp 32x32 item atlas, alongside a separate icon for inventories. The shield has normal/blocking-state models and the cursed talisman uses a 3D voxel necklace model while keeping the existing item ID for saved-item/effect compatibility.

ItemsAdder combines all content namespaces into one generated server resource pack; `warriorland_halloween` is a namespace within the pack, not a second pack in the Minecraft resource-pack menu. After `/iazip`, check `/iainfo` for the hosted URL and request/status. For ItemsAdder 4.0.17+, `resource-pack.hosting.simple_self_host.enabled: true` with `server_address: auto` is often the simplest hosting choice. Set the installed language file's `resourcepack-popup-message` to a branded message such as `&6WarriorLand Halloween 2026` if you want players to recognize the pack prompt.

The Java plugin uses these custom ItemsAdder IDs:
- warriorland_halloween:hunter_mask
- warriorland_halloween:cursed_talisman
- warriorland_halloween:halloween_token
- warriorland_halloween:cursed_candy
- warriorland_halloween:haunted_map
- warriorland_halloween:crimson_warden_helmet / chestplate / leggings / boots
- warriorland_halloween:crimson_warden_sword / pickaxe / axe / shovel / hoe / shield

Sound IDs used by the plugin:
- `warriorland_halloween:haunted_theme` — background loop, played at the generated track duration
- `warriorland_halloween:event_sting` — short cue played when a random event starts

The audio generator is `scripts/generate_halloween_audio.py`. It downloads and converts the CC0 Spooky Fester soundtrack, creates the five-second event cue, and generates a silent OGG for the vanilla-music overrides. The Minecraft 1.21.10 pack defines silent replacements for the exact 31 vanilla background-music events; the server-side music watchdog also stops the MUSIC category every second while Halloween ambience is active. Halloween music plays in AMBIENT at a configured volume multiplier of 3.0. After installing/updating the content, run `/iazip` and make sure players receive the rebuilt server resource pack.
## Crimson Warden gear — configured stats

These values come from `configs/items.yml` and the adjacent `components_nbt_file` JSONs. They are the attributes prepared for ItemsAdder; the rendered Minecraft tooltip/damage should still be checked once on the live server after `/iazip`.

### Armour set

| Piece | Armor points | Durability | Netherite baseline durability |
|---|---:|---:|---:|
| Helmet | 6 (Netherite 3 + 3) | 900 | 407 |
| Chestplate | 12 (Netherite 8 + 4) | 1,300 | 592 |
| Leggings | 9 (Netherite 6 + 3) | 1,150 | 555 |
| Boots | 6 (Netherite 3 + 3) | 950 | 481 |
| **Full set** | **33** | — | **20 armor points** |

The custom equipment keeps the Netherite base material/toughness and uses the configured per-piece armor values shown above. Each piece uses the original Crimson Warden 64×32 layer textures and enchantability component value 25 (Netherite's normal enchantability is 15).

### Melee weapon and tools

| Item | Configured attack-damage modifier | Attack-speed modifier | Durability | Mining speed |
|---|---:|---:|---:|---:|
| Crimson Warden Sword | 15.0 | 0.8 | 5,000 | — |
| Crimson Warden Pickaxe | 10.0 | 0.8 | 5,000 | 14 |
| Crimson Warden Axe | 14.0 | 0.6 | 5,000 | 14 |
| Crimson Warden Shovel | 10.0 | 0.8 | 4,500 | 14 |
| Crimson Warden Hoe | 7.0 | 1.0 | 4,500 | 14 |

Netherite tools use mining speed 9 for their matching tool classes and have durability 2,031. The custom tools use a Minecraft `minecraft:tool` component with speed 14, correct-drop matching rules and enchantability 25. The YAML attack values are item attribute modifiers, not a guarantee that every hit deals exactly that many health points: the final combat result also depends on the vanilla base attribute and attack cooldown.

### Relics and store

- `cursed-talisman`: **1,000,000 fragments**, +20 max health (one extra full row of hearts), +10% Halloween fragment multiplier while held in either hand.
- All four armour pieces and the five Crimson Warden weapon/tool items have component JSON files under `configs/` which explicitly preserve enchantability. Tool component files also specify the mining rules.
- Shop descriptions, configured attributes, durability values, the component JSON files and all 14 item textures are checked by `scripts/validate_halloween_assets.py`.


## World structures and equipment effects

- `HalloweenWorldDecorator` adds randomized pumpkins, cobwebs, red candles, and shaded red/brown mushrooms to eligible terrain. Existing blocks and block entities are not overwritten.
- `HalloweenWorldStructureGenerator` checks natural, nearly level terrain in loaded Overworld chunks and builds varied haunted villages or graveyards only when the full footprint is loaded and clear. It does not force-load chunks; a per-chunk persistent marker prevents overlapping/repeated placement after reloads. Density and slope are configured under `world-structures` in `config.yml`.
- `/halloween structures status` reports whether generation is active and the current session's generation counts; `/halloween structures scan` re-checks only chunks already loaded.
- Shop-tagged Crimson Warden armor has a subtle crimson soul aura as a full set, night vision on the helmet, fire resistance on the chestplate, speed on leggings and jump boost on boots. The full-set emergency ward grants short absorption with a cooldown when a hit would drop the wearer below the configured health threshold.
- Shop-tagged weapons have separate low-frequency combat procs: Blood Rend (sword), defense break (axe), grave piercer (pickaxe), nearby shockwave (shovel) and soul harvest healing (hoe). The custom shield can knock back an attacker when actively blocking. Cooldowns, radii, duration and values are configured under `gear-effects`.
- The cursed talisman retains its existing item/reward ID for save compatibility but its displayed identity is now **Prokletý náhrdelník**. Its +20 maximum health and +10% Halloween fragments effects remain.
- These Java-side effects need the custom item to carry HalloweenCore's reward marker, which is applied when claimed from the Halloween shop. Visual asset checks and Java compilation run in CI; actual rendering, client delivery and combat behaviour still need a live 1.21.10 test server.

## Vampire model assets

Plánované soubory pro finální 3D model patří do samostatného ModelEngine asset balíku. Dokud není potvrzena finální UV mapa, samotný model ani jeho textury se nepovažují za produkčně hotové. Konceptový vizuál vznikl v rámci návrhu, ale není vydáván jako finální UV texture atlas.


## Halloween event soundtrack and music suppression

The generated pack contains a CC0-converted Spooky Fester ambience plus six original procedural event cues. Each event cue is registered in `configs/sounds.yml`: `soulstorm_sting`, `witching_sting`, `harvest_sting`, `blood_moon_rise`, `pumpkin_apocalypse` and `graveyard_rising`. The Minecraft resource-pack `sounds.json` replaces 31 configured Minecraft 1.21.10 background-music events with `warriorland_halloween:halloween_silence`; the plugin also stops the client MUSIC category once per second while the Halloween ambience is active. This only takes effect after each client downloads and accepts the rebuilt pack.

## Custom enemy model files

Five ModelEngine blueprints with independent 128×128 atlases are stored in `mythicmobs/models/halloween_*.bbmodel` and `mythicmobs/models/halloween_*.png`. These files are packaged separately from ItemsAdder content in the CI artifact `HalloweenCore-Special-Mobs-ModelEngine.zip`; import the blueprints into ModelEngine and merge `ModelEngine/resource pack` into the ItemsAdder-hosted pack before testing them in-game. The model/texture structure is checked by `scripts/validate_special_mob_models.py`.


## Halloween world decoration textures

The ItemsAdder ZIP also includes seven **16×16 vanilla-block texture overrides** under `resourcepack/assets/minecraft/textures/block/`:

- `pumpkin_side.png`, `pumpkin_top.png`, `pumpkin_face.png` — pumpkin body, top and carved face.
- `jack_o_lantern.png` — glowing jack-o'-lantern face.
- `cobweb.png` — crimson web strands with transparent background.
- `red_candle.png`, `red_candle_lit.png` — dark red candles and a warmer lit state.

The HalloweenCore decorator places actual vanilla blocks into the world, so the blocks remain present even if a player declines the resource pack. The accepted pack changes their appearance instead of controlling whether they exist. These are vanilla namespace overrides and therefore affect **all** vanilla pumpkins, jack-o'-lanterns, cobwebs and red candles in resource-pack-enabled clients, not just the ones placed by HalloweenCore. They are checked by `scripts/validate_halloween_assets.py` in CI.
