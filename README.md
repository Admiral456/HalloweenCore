# HalloweenCore

HalloweenCore is a Java 21 plugin for Paper/Purpur 1.21.x. This branch is a cleanup pass that removes the old custom-asset Halloween implementation before a vanilla-first quest, dialogue, event, and underground-cache system is added.

## Kept in the core

- Server-wide fragment progress and milestones.
- Fragment rewards for eligible gameplay actions such as mob kills, mining, farming, and fishing.
- Curse progression, player statistics, daily challenge and return rewards.
- Reward shop backed by vanilla item fallbacks.
- Optional ItemsAdder integration for the single retained custom item texture: cursed candy.
- Configuration validation and the existing player-data store.

## Disabled or removed during the rebuild

- The old random event lineup and old story quests.
- The old special-mob system and its MythicMobs/ModelEngine model pack.
- Haunted Village and old secret-discovery locations tied to graves/ritual sites.
- Automatic Halloween world decoration.
- The custom soundtrack, ItemsAdder sound definitions, vanilla texture overrides, and the orange/red sky shader.
- The Crimson Warden custom armor and weapon textures.

The only retained custom image is `itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/textures/item/cursed_candy.png`. Legacy Java scaffolding is kept temporarily to reduce risk while its replacement is designed, but the old features are forcibly disabled on startup and configuration reload. The former admin commands for those systems report that they have been retired.

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
- `/halloween on|off`

Legacy commands for random events, story quests, the old village/discovery system, shader editing, custom mobs, and the vampire model encounter are retired pending replacement.

## Build

Requires Java 21 and Maven:

```bash
mvn -B clean package
```

GitHub Actions builds `target/HalloweenCore.jar` and a minimal ItemsAdder content ZIP containing the candy definition and its retained texture. It no longer runs asset/audio generators or creates custom-mob model packs.
