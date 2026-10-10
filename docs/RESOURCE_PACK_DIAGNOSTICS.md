# HalloweenCore resource-pack and live test checklist

The build validates files and creates deployable artifacts. It cannot verify a client's actual pack-download status or rendering against a live Hostify server. Use this checklist on a staging copy before enabling the event for everyone.

## Install the pack and import the mob models

1. Stop the server and back up the world plus `plugins/HalloweenCore/`, `plugins/ItemsAdder/`, `plugins/MythicMobs/` and `plugins/ModelEngine/`.
2. Replace `plugins/HalloweenCore.jar` with the current `HalloweenCore.jar` artifact.
3. Extract `WarriorLand-Halloween-ItemsAdder.zip` and place its `warriorland_halloween` directory in `plugins/ItemsAdder/contents/`. This content includes Halloween looks for pumpkins, jack-o'-lanterns, cobwebs and red candles in the vanilla texture namespace.
4. Extract `HalloweenCore-Special-Mobs-ModelEngine.zip` over the server's `plugins/` folder. The `.bbmodel` blueprints go into `plugins/ModelEngine/blueprints/`; the YAML definitions go into `plugins/MythicMobs/Mobs/`.
5. Make sure ModelEngine and MythicMobs are installed and compatible with the server. Leave these model IDs unchanged: `halloween_cursed_zombie`, `halloween_gravekeeper`, `halloween_blood_spider`, `halloween_pumpkin_wraith`, and `halloween_hex_witch`.
6. Run `/meg reload models` (some ModelEngine versions use `/meg reload`). Check the console and confirm all five models load. This generates `plugins/ModelEngine/resource pack/` from the `.bbmodel` files; merely copying them is not enough.
7. Run `/mm reload` and check that the five MythicMobs definitions have no errors.
8. In the existing ItemsAdder `config.yml`, append `ModelEngine/resource pack` to `merge_other_plugins_resourcepacks_folders`. **Preserve all existing entries.** If the directory is missing after ModelEngine reload, do not run `/iazip` yet; resolve the ModelEngine import/startup problem first.
9. Only after ModelEngine has generated its pack and ItemsAdder is configured to merge it, run `/iazip`. Then use `/iainfo` to verify that the generated pack has a reachable URL. Reconnect and accept the newest pack.

The required order is **copy blueprints → `/meg reload models` → `/mm reload` → configure ItemsAdder merge → `/iazip` → reconnect and accept the pack**. Rebuilding ItemsAdder before ModelEngine has generated its assets can produce a pack with Halloween block textures/audio but no custom mob rendering assets.

## Client-side verification

- Reconnect with the resource-pack prompt enabled and accept the newly generated pack. Reconnecting after `/iazip` avoids testing against a cached, earlier pack.
- Run `/halloween debug` and check the ItemsAdder status, the configured Halloween sound ID, special-model dependencies and Blood Moon settings. This checks server-side conditions; it does not prove a client has accepted the pack.
- Test custom audio with `/playsound warriorland_halloween:haunted_theme ambient @s` and `/playsound warriorland_halloween:blood_moon_rise ambient @s`. Both should be heard with the current pack installed.
- Test a vanilla background-music event with `/playsound minecraft:music.game master @s`. With the correct resource pack, the overridden event should be silent. The plugin also sends a MUSIC-category stop once per second while Halloween ambience is active.
- The override covers 31 background-music event IDs from the Minecraft 1.21.10 pack list. It is not intended to mute jukebox/music-disc tracks or ordinary gameplay sound effects.
- Start `/halloween event start soulstorm` and verify the Cursed Zombie, Gravekeeper and Blood Spider models and particles. Then start `/halloween event start blood-moon-invasion`: confirm the larger waves, Captain spawn in the final two minutes, 3× damage from hostile monsters, and cleanup after `/halloween event stop`.
- Walk through several new natural-terrain chunks and confirm pumpkins, jack-o'-lanterns, cobwebs and red candles appear without replacing buildings, chests, or other existing solid blocks.

## Known boundaries

- HalloweenCore can use vanilla named mobs as a fallback when MythicMobs/ModelEngine or a custom definition is absent. If an enemy appears as a vanilla entity rather than its unique 3D model, check the model import and resource-pack merge before reporting the Java event code as broken.
- A green CI result confirms that the source compiles, model/texture files and audio assets pass structural checks, and the deployment zips can be built. It does not certify visual rendering, hosted pack delivery, or sound playback on a real Hostify client.
- Do not enable the Vampire King production model gate until that separate 10-block model has passed its own client test.
