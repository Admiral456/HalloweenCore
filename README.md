# HalloweenCore

HalloweenCore is a Java 21 plugin for Paper/Purpur 1.21.x. This branch removes the old custom-asset Halloween implementation before a vanilla-first quest, dialogue, event, and underground-cache system is added.

## Kept in the core

- Server-wide fragment progress and milestones.
- Fragment rewards for eligible gameplay actions such as mob kills, mining, farming, and fishing.
- Curse progression, player statistics, daily challenge and return rewards.
- Reward shop backed by vanilla item fallbacks.
- Optional ItemsAdder integration for cursed candy and the retained pumpkin texture set for the planned pumpkin-headed villager.
- Configuration validation and the existing player-data store.

## Disabled or removed during the rebuild

- The old random event lineup and old story quests.
- The old special-mob system and its MythicMobs/ModelEngine model pack, including Pumpkin Wraith.
- Haunted Village and old secret-discovery locations tied to graves/ritual sites.
- Automatic Halloween world decoration.
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
- `/halloween on|off`

Legacy commands for random events, story quests, the old village/discovery system, shader editing, custom mobs, and the vampire model encounter are retired pending replacement.

## Pumpkin villager

The pumpkin villager is a vanilla villager wearing a carved pumpkin helmet; it requires no custom model or additional texture. Its entity marker is stored in persistent data, so it survives restarts with its chunk. Right-clicking it opens a short Czech dialogue with clickable links to the existing rewards menu, daily challenge and server progress. The NPC is invulnerable, has no AI, cannot pick up items, and does not drop its pumpkin.

The feature is controlled by `pumpkin-villager.enabled` and `pumpkin-villager.interact-cooldown-millis` in `config.yml`.

## Build

Requires Java 21 and Maven:

```bash
mvn -B clean package
```

GitHub Actions builds `target/HalloweenCore.jar` and an ItemsAdder content ZIP containing only the candy item and the three retained pumpkin texture files. It no longer runs asset/audio generators or creates custom-mob model packs.
