# HalloweenCore resource-pack and live test checklist

The build validates files and creates deployable artifacts. It cannot verify a client's actual pack-download status or rendering against a live Hostify server. Use this checklist on a staging copy before enabling the event for everyone.

## Install the three deliverables

1. Stop the server and back up the world, `plugins/HalloweenCore/`, `plugins/ItemsAdder/`, `plugins/MythicMobs/` and `plugins/ModelEngine/`.
2. Replace `plugins/HalloweenCore.jar` with the current `HalloweenCore.jar` artifact.
3. Extract `WarriorLand-Halloween-ItemsAdder.zip` and place its `warriorland_halloween` directory in `plugins/ItemsAdder/contents/`.
4. Extract `HalloweenCore-Special-Mobs-ModelEngine.zip` over `plugins/`. This adds `MythicMobs/Mobs/halloween-special-mobs.yml` and the five model/texture blueprints under `ModelEngine/blueprints/`.
5. Import the five Blockbench models using the installed ModelEngine version. Keep these model IDs exactly:
   - `halloween_cursed_zombie`
   - `halloween_gravekeeper`
   - `halloween_blood_spider`
   - `halloween_pumpkin_wraith`
   - `halloween_hex_witch`
6. Reload models and mob definitions using the commands supported by your installed versions (commonly `/meg reload models` and `/mm reload`). Check the console for missing model IDs or invalid MythicMobs skills.
7. In the existing ItemsAdder configuration, append `ModelEngine/resource pack` to `merge_other_plugins_resourcepacks_folders`; preserve all existing entries. The option name and exact import workflow can differ between plugin versions, so confirm the setting against your installed ModelEngine/ItemsAdder builds.
8. Restart the server if the plugin versions require it. Run `/iazip`, wait for it to finish, then use `/iainfo` to make sure ItemsAdder reports a reachable resource-pack URL.

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
