# HalloweenCore MythicMobs and ModelEngine pack

## Files

- `mobs/vampire-king.yml`: the separate Vampire King boss definition.
- `skills/vampire-king.yml`: reusable boss-animation state skills.
- `mobs/halloween-special-mobs.yml`: definitions for the eight Halloween enemy types used by HalloweenCore.
- `models/halloween_*.bbmodel` and `models/halloween_*.png`: individual editable Blockbench blueprints and unique 128×128 RGBA pixel-art atlases for the Cursed Zombie, Void Reaper, Gravekeeper, Frost Stalker, Blood Spider, Pumpkin Wraith, Hex Witch and Nightmare.
- `models/vampire_king.bbmodel` and `models/vampire_king.png`: the existing boss blueprint and texture.

The feature build publishes two companion archives:
- `HalloweenCore-Special-Mobs-ModelEngine.zip`: the eight enemy definitions under `MythicMobs/Mobs/` and their editable models/textures under `ModelEngine/blueprints/`.
- `HalloweenCore-Vampire-King-ModelEngine.zip`: the Vampire King mob and skill definitions under `MythicMobs/`, its Blockbench model and PNG under `ModelEngine/blueprints/`, and the staging checklists.

The boss archive intentionally does not enable the boss or bypass the global-progress, arena, MythicMobs, ModelEngine and `model.ready` gates.

## Installation for staging

1. Stop the server and back up `plugins/HalloweenCore/`, `plugins/ItemsAdder/`, `plugins/MythicMobs/` and `plugins/ModelEngine/`.
2. Install the new `HalloweenCore.jar`, copy `warriorland_halloween` to `plugins/ItemsAdder/contents/`, then extract `HalloweenCore-Special-Mobs-ModelEngine.zip` over `plugins/`.
3. Confirm that all eight `.bbmodel` files are in `plugins/ModelEngine/blueprints/` and the YAML is in `plugins/MythicMobs/Mobs/halloween-special-mobs.yml`. The texture is embedded in each Blockbench blueprint; accompanying PNGs are supplied as editable/external source copies.
4. Run `/meg reload models` (or `/meg reload` if that is the command for your installed version). ModelEngine must report all eight model IDs as loaded and generate `plugins/ModelEngine/resource pack/`.
5. Run `/mm reload`. Verify that there are no errors for the eight definitions and their `model{mid=...}` skills.
6. In the existing ItemsAdder config, add `ModelEngine/resource pack` to `merge_other_plugins_resourcepacks_folders` without deleting existing folders. Then run `/iazip` **after** ModelEngine has generated its resource pack.
7. Run `/iainfo` and verify the final pack URL is reachable. Disconnect/reconnect and accept the rebuilt pack. The Halloween pumpkin/web/candle overrides should be visible, and special mobs should use their ModelEngine models rather than vanilla base entities.
8. Test `/halloween event start soulstorm` and `/halloween event start blood-moon-invasion`. A successful Java build does not replace the live-client rendering check.

## Fallback and readiness

HalloweenCore falls back to named vanilla special mobs if MythicMobs or ModelEngine is not enabled or the corresponding MythicMob definition cannot be resolved. This makes the event playable if the optional visual dependencies are missing, but it is not the intended final visual result. The plugin cannot prove that the client accepted a resource pack just because ModelEngine is installed. Do not mark the model setup as production-ready until the mob textures and animation states have been confirmed in a real Minecraft client.

The separate Vampire King boss requires its own import/testing and still respects the boss model readiness gate in `config.yml`. Boss availability is not implied by the five special mob models.
