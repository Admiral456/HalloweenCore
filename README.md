# HalloweenCore

HalloweenCore is a Java 21 plugin for Paper/Purpur 1.21.x. This branch replaces the previous custom-asset Halloween implementation with a vanilla-first foundation: a pumpkin villager NPC, locked underground caches, and sparse new-chunk Halloween features.

## Kept in the core

- Server-wide fragment progress and milestones.
- Fragment rewards for eligible gameplay actions such as mob kills, mining, farming, and fishing.
- Curse progression, player statistics, daily challenge and return rewards.
- Reward shop backed by vanilla item fallbacks.
- Optional ItemsAdder integration for cursed candy and the retained pumpkin block textures.
- Configuration validation and the existing player-data store.

## Disabled or removed during the rebuild

- The old random event lineup and old story quests.
- The old special-mob system and its MythicMobs/ModelEngine model pack, including Pumpkin Wraith.
- Haunted Village and old secret-discovery locations tied to graves/ritual sites.
- The old automatic Halloween world-decoration engine; the replacement below only modifies newly generated Overworld chunks.
- The custom soundtrack, ItemsAdder sound definitions, and sky shader.
- Crimson Warden custom armor/weapons and other old custom item textures.

## Retained ItemsAdder images

- `resourcepack/assets/warriorland_halloween/textures/item/cursed_candy.png`
- `resourcepack/assets/minecraft/textures/block/pumpkin_face.png`
- `resourcepack/assets/minecraft/textures/block/pumpkin_side.png`
- `resourcepack/assets/minecraft/textures/block/pumpkin_top.png`

The three pumpkin files are intentionally retained as the requested pumpkin-themed exception. Because they override vanilla Minecraft pumpkin block textures, they affect those pumpkins anywhere in the generated resource pack. The implemented NPC is a vanilla villager wearing a vanilla carved-pumpkin helmet, with no custom mob model. The retained block-texture overrides do not change the helmet's carved-pumpkin item texture.

The existing Java classes for retired systems are kept temporarily for migration safety, but their listeners are not registered and their timers are not started. The random-event manager now schedules no ticker while events are disabled, and task-owning managers cancel their tasks on stop. The fragment/progression core, daily rewards, shop, and vanilla reward perks remain active. Cursed candy's consume effect remains active; retired Haunted Village map behavior was removed.

## Commands

- `/halloween stats`
- `/halloween progress`
- `/halloween curse`
- `/halloween challenge`
- `/halloween rewards`
- `/halloween claim <id>`
- `/halloween top`
- `/halloween reload`
- `/halloween debug`
- `/halloween give <hráč> <počet>` (admin)
- `/halloween npc spawn <jméno>` — creates a persistent vanilla villager wearing a carved pumpkin (admin).
- `/halloween npc remove` — removes the nearest managed pumpkin villager within 8 blocks (admin).
- `/halloween cache spawn` — creates a test cache safely in front of the admin (admin).
- `/halloween cache key <hráč> [počet]` — gives 1–64 cache keys to an online player (admin).
- `/halloween on|off`

Legacy commands for random events, story quests, the old village/discovery system, shader editing, custom mobs, and the vampire model encounter are retired pending replacement.

## Vanilla world features

On newly generated Overworld chunks only, the plugin can very rarely add small natural pumpkin patches in open grassland/forest biomes and a few cobwebs inside naturally enclosed cave pockets. It uses vanilla blocks only, does not add custom mobs or custom models, and does not scan older chunks. Surface placement requires a patch of natural ground with clear space; local tile-entity checks guard each placement. Cave-web placement is limited by configured Y levels and requires natural stone around the candidate.

Settings live under `world-features` in `config.yml`:
- `pumpkin-patch-chance-per-chunk`: chance to try a small pumpkin patch per newly generated Overworld chunk (default 6%).
- `cave-web-patch-chance-per-chunk`: chance to try a cave-web patch (default 4%).
- `max-pumpkins-per-patch` and `max-cave-webs-per-patch`: strict placement caps.
- `cave-webs-min-y` / `cave-webs-max-y`: depth range for cave cobwebs.

These are new vanilla features, separate from the retired `world-decorations` system. Turning Halloween off pauses generation in future chunks; already generated features remain in place. Disabling `world-features.enabled` also stops new generation but does not remove existing blocks.

## Underground caches

The plugin can generate a single locked chest in a small number of newly generated Overworld chunks. It does not scan or rewrite existing chunks, reducing the risk of touching player builds. Caches use vanilla chest blocks and ordinary vanilla loot. A real key is a tripwire hook with a plugin-only persistent-data marker, so renaming a normal hook does not make a valid key.

Locked caches cannot be opened without a key, broken, blown up, pushed by pistons, or used by hoppers until unlocked. Using a key consumes one and permanently unlocks that chest. Hostile mobs have a configurable small chance to drop keys. Turning `/halloween off` pauses new cache generation and key drops but does not block existing caches from being opened with keys. Setting `underground-caches.enabled: false` lets a locked cache automatically become an ordinary chest the next time it is opened, so disabling the feature never traps existing loot. Admins can test the system with `/halloween cache spawn` and `/halloween cache key <hráč> [počet]`.

Settings are under `underground-caches` in `config.yml`. `generation.chance-per-new-chunk` controls the generation chance; `generation.min-y` and `generation.max-y` limit the underground depth; `key-drop.chance-per-hostile-kill` controls the chance of a key drop. This system does not need custom textures or ModelsEngine/MythicMobs.

## Pumpkin villager

The pumpkin villager is a vanilla villager wearing a carved pumpkin helmet; it requires no custom model or additional texture. Its entity marker is stored in persistent data, so it survives restarts with its chunk. Right-clicking it opens a short Czech dialogue with clickable links to the existing rewards menu, daily challenge and server progress. The NPC is invulnerable, has no AI, cannot pick up items, and does not drop its pumpkin.

The feature is controlled by `pumpkin-villager.enabled` and `pumpkin-villager.interact-cooldown-millis` in `config.yml`.

## Build

Requires Java 21 and Maven:

```bash
mvn -B clean package
```

GitHub Actions builds `target/HalloweenCore.jar` and an ItemsAdder content ZIP containing only the candy item and the three retained pumpkin texture files. It no longer runs asset/audio generators or creates custom-mob model packs.
