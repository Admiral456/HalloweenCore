# HalloweenCore MythicMobs and ModelEngine pack

## Files

- `mobs/vampire-king.yml`: the separate Vampire King boss definition.
- `skills/vampire-king.yml`: reusable boss-animation state skills.
- `mobs/halloween-special-mobs.yml`: definitions for the eight special Halloween enemies plus the custom Goblin King boss and Headless Nick.
- `models/halloween_*.bbmodel` and `models/halloween_*.png`: individual editable Blockbench blueprints and 128×128 RGBA atlases for the eight special enemies, the winged/crowned Goblin King, and Headless Nick. The Goblin King gets original horns, crown, slate armor, crimson cape, animated wings, royal staff, and emerald orb geometry.
- `models/vampire_king.bbmodel` and `models/vampire_king.png`: the existing boss blueprint and texture.

The feature build publishes `HalloweenCore-Special-Mobs-ModelEngine.zip`, containing the custom mob/boss definitions under `MythicMobs/Mobs/` and all generated editable model/texture files under `ModelEngine/blueprints/`.

## Installation for staging

1. Stop the server and back up `plugins/HalloweenCore/`, `plugins/ItemsAdder/`, `plugins/MythicMobs/` and `plugins/ModelEngine/`.
2. Install the new `HalloweenCore.jar`, copy `warriorland_halloween` to `plugins/ItemsAdder/contents/`, then extract `HalloweenCore-Special-Mobs-ModelEngine.zip` over `plugins/`.
3. Confirm that all ten generated `halloween_*.bbmodel` files and their PNG source atlases from the pack are in `plugins/ModelEngine/blueprints/`, and the YAML is in `plugins/MythicMobs/Mobs/halloween-special-mobs.yml`. The Goblin King’s source model is generated during the asset build from the editable rig template and then enriched with his custom geometry, atlas and animation clips.
4. Run `/meg reload models` (or `/meg reload` if that is the command for your installed version). ModelEngine must report all ten model IDs as loaded and generate `plugins/ModelEngine/resource pack/`.
5. Run `/mm reload`. Verify that there are no errors for the eight definitions and their `model{mid=...}` skills.
6. In the existing ItemsAdder config, add `ModelEngine/resource pack` to `merge_other_plugins_resourcepacks_folders` without deleting existing folders. Then run `/iazip` **after** ModelEngine has generated its resource pack.
7. Run `/iainfo` and verify the final pack URL is reachable. Disconnect/reconnect and accept the rebuilt pack. The Halloween pumpkin/web/candle overrides should be visible, and special mobs should use their ModelEngine models rather than vanilla base entities. The Goblin King must fail his test-spawn if his custom model dependency is missing, rather than appear as an ordinary Piglin Brute.
8. Test `/halloween event start soulstorm` and `/halloween event start blood-moon-invasion`. A successful Java build does not replace the live-client rendering check.

## Fallback and readiness

HalloweenCore falls back to named vanilla special mobs if MythicMobs or ModelEngine is not enabled or the corresponding MythicMob definition cannot be resolved. This makes the event playable if the optional visual dependencies are missing, but it is not the intended final visual result. The plugin cannot prove that the client accepted a resource pack just because ModelEngine is installed. Do not mark the model setup as production-ready until the mob textures and animation states have been confirmed in a real Minecraft client.

## Goblin King — Stage A check

The Goblin King is intentionally a dedicated boss, separate from the Vampire King and the Vampire arena. His spawn path is custom-model-only: if MythicMobs/ModelEngine or the `halloween_goblin_king` model is missing, the command fails instead of substituting an ordinary Piglin Brute.

After importing the pack and generating/merging the client resource pack, use a disposable staging world and verify:
1. `/halloween goblin dry-run <world> <x> <y> <z>` to inspect the arena volume without editing anything.
2. `/halloween goblin arena <world> <x> <y> <z>`, inspect the reported bounds, then `/halloween goblin confirm` to build the separate throne fortress.
3. `/halloween goblin spawn` to spawn a no-reward model/combat test; `/halloween goblin status` reports whether the encounter is active.
4. Confirm the horns, crown, cape, armor, wings and orb are visible, then inspect entrance, wing-spread, attack and death animations.

A successful CI build only verifies model structure, UV/texture files, animation wiring, plugin compilation and packaging; it does not verify an actual ModelEngine import or client render. Do not mark Stage A complete until the live client displays the correct model.

The separate Vampire King boss and arena remain intact, independently configured, and gated by the existing readiness checks.
