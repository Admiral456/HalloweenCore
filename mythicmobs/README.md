# HalloweenCore MythicMobs and ModelEngine pack

## Files

- `mobs/vampire-king.yml`: the separate Vampire King boss definition.
- `skills/vampire-king.yml`: reusable boss-animation state skills.
- `mobs/halloween-special-mobs.yml`: definitions for the five Halloween enemy types used by HalloweenCore.
- `models/halloween_*.bbmodel` and `models/halloween_*.png`: individual editable Blockbench blueprints and unique 128×128 RGBA pixel-art atlases for the Cursed Zombie, Gravekeeper, Blood Spider, Pumpkin Wraith and Hex Witch.
- `models/vampire_king.bbmodel` and `models/vampire_king.png`: the existing boss blueprint and texture.

The feature build publishes `HalloweenCore-Special-Mobs-ModelEngine.zip`, containing the five enemy definitions under `MythicMobs/Mobs/` and the editable model/texture files under `ModelEngine/blueprints/`.

## Installation for staging

1. Stop the server and back up `plugins/HalloweenCore/`, `plugins/ItemsAdder/`, `plugins/MythicMobs/` and `plugins/ModelEngine/`.
2. Install the new `HalloweenCore.jar` in `plugins/`, copy `warriorland_halloween` to `plugins/ItemsAdder/contents/`, then extract `HalloweenCore-Special-Mobs-ModelEngine.zip` over the server's `plugins/` folder.
3. Import the five Blockbench blueprints through the installed ModelEngine version. Keep the IDs exactly as the filenames: `halloween_cursed_zombie`, `halloween_gravekeeper`, `halloween_blood_spider`, `halloween_pumpkin_wraith`, `halloween_hex_witch`.
4. Reload ModelEngine and MythicMobs using the commands supported by the installed versions (commonly `/meg reload models` and `/mm reload`). Check their consoles for missing model IDs or invalid skills.
5. In the existing ItemsAdder config, append `ModelEngine/resource pack` to `merge_other_plugins_resourcepacks_folders` without deleting any folders already listed. Restart if the plugin version requires it.
6. Run `/iazip`, then `/iainfo` and verify that the generated ZIP has a reachable hosting URL. Accept the pack from the client, disconnect/reconnect, and test custom sounds/models.
7. Use `/halloween event start soulstorm` and `/halloween event start blood-moon-invasion` in a staging area. Confirm that each special mob renders with the intended texture, that ability particles play, that waves clean up after the event, and that every hostile monster deals 3× damage during Blood Moon.

## Fallback and readiness

HalloweenCore falls back to named vanilla special mobs if MythicMobs or ModelEngine is not enabled or the corresponding MythicMob definition cannot be resolved. This makes the event playable if the optional visual dependencies are missing, but it is not the intended final visual result. The plugin cannot prove that the client accepted a resource pack just because ModelEngine is installed. Do not mark the model setup as production-ready until the mob textures and animation states have been confirmed in a real Minecraft client.

The separate Vampire King boss requires its own import/testing and still respects the boss model readiness gate in `config.yml`. Boss availability is not implied by the five special mob models.
