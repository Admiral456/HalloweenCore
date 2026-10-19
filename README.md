# HalloweenCore

HalloweenCore is a Java 21 plugin for Paper/Purpur 1.21.x. This branch replaces the previous custom-asset Halloween implementation with a vanilla-first foundation: timed vanilla events, a pumpkin villager NPC, locked underground caches, and sparse new-chunk Halloween features.

## Kept in the core

- Server-wide fragment progress and milestones.
- Three vanilla-only timed events, with fragment multipliers and a hostile-damage modifier during Blood Moon.
- Fragment rewards for eligible gameplay actions such as mob kills, mining, farming, and fishing.
- Curse progression, player statistics, daily challenge and return rewards.
- Reward shop backed by vanilla item fallbacks. The one-time `cursed-candy` reward costs 500 fragments; its short consumable effects also work when ItemsAdder is unavailable.
- Optional ItemsAdder integration for cursed candy and the retained pumpkin block textures.
- Configuration validation and the existing player-data store.

## Disabled or removed during the rebuild

- The old random-event lineup and custom-mob story objectives; the retained nine-chapter campaign now uses vanilla gameplay actions only.
- The old custom model pack; eight rare elite variants now use vanilla zombies, husks, skeletons, strays, spiders, creepers, witches, and phantoms, with built-in particle/sound effects and no MythicMobs/ModelEngine dependency.
- The old Haunted Village discovery/map system is disabled. Three vanilla-only hidden discoveries (Easter eggs) are retained separately, without custom models or audio.
- The old automatic Halloween world-decoration engine; the replacement below only modifies newly generated Overworld chunks.
- The custom soundtrack, ItemsAdder sound definitions, and sky shader.
- Crimson Warden custom armor/weapons and other old custom item textures.

## Retained ItemsAdder images

- `resourcepack/assets/warriorland_halloween/textures/item/cursed_candy.png`
- `resourcepack/assets/minecraft/textures/block/pumpkin_face.png`
- `resourcepack/assets/minecraft/textures/block/pumpkin_side.png`
- `resourcepack/assets/minecraft/textures/block/pumpkin_top.png`

The three pumpkin files are intentionally retained as the requested pumpkin-themed exception. Because they override vanilla Minecraft pumpkin block textures, they affect those pumpkins anywhere in the generated resource pack. The implemented NPC is a vanilla villager wearing a vanilla carved-pumpkin helmet, with no custom mob model. The retained block-texture overrides do not change the helmet's carved-pumpkin item texture.

Old custom-content classes are kept only where still needed for compatibility; the elite-mob manager is active but uses vanilla entities exclusively. The story campaign listens only for vanilla gameplay actions. Other active gameplay listeners include the vanilla secret-discovery system, pumpkin villager, locked caches, new-chunk world features, timed events, and ambient cues. The fragment/progression core, daily rewards, shop, and vanilla reward perks remain active. Cursed candy's consume effect remains active; retired Haunted Village map behavior was removed.

## Commands

- `/halloween stats`
- `/halloween progress`
- `/halloween curse`
- `/halloween challenge`
- `/halloween quests` — resumes the nine-chapter vanilla story campaign
- `/halloween quests reset <player>` (admin; online players only) — resets a player's story progress
- `/halloween rewards`
- `/halloween claim <id>`
- `/halloween top`
- `/halloween secrets` — lists discovered hidden places and gives hints without exposing coordinates.
- `/halloween setsecret <id>` (admin) — sets one hidden discovery at the admin's current position.
- `/halloween event [status|start <id>|stop]` — status is public; start/stop are admin-only.
- `/halloween ambience [status|on|off|test]` — status is available to users; on/off and the immediate one-player test pulse require admin permission. The test command previews nearby vanilla particles/sound without waiting for the next randomized pulse.
- `/halloween reload`
- `/halloween debug`
- `/halloween give <hráč> <počet>` (admin)
- `/halloween npc spawn <jméno>` — creates a persistent vanilla villager wearing a carved pumpkin (admin).
- `/halloween npc remove` — removes the nearest managed pumpkin villager within 8 blocks (admin).
- `/halloween cache spawn` — creates a test cache safely in front of the admin (admin).
- `/halloween cache key <hráč> [počet]` — gives 1–64 cache keys to an online player (admin).
- `/halloween on|off`

Legacy commands for the old random events, story quests, Haunted Village map/loot system, shader editing, custom mobs, and vampire model encounter remain retired. The vanilla-only `secrets` and `setsecret` commands are active.

## Rare vanilla Halloween elite mobs

The plugin can mark a small percentage of naturally spawned hostile mobs as rare elite variants: Cursed Zombie, Void Reaper, Gravekeeper, Frost Stalker, Blood Spider, Nightmare, Pumpkin Wraith, and Hex Witch. They remain ordinary vanilla entity types with a themed name, glow outline, bounded health/speed/strength boosts, and selected abilities using potion effects, particles, and built-in Minecraft sounds. Pumpkin Wraith explosions clear the block-damage list so they do not grief builds. Defeating an elite gives a small bonus-fragment reward and has a low chance to drop Cursed Candy; when ItemsAdder is absent, the vanilla pumpkin-pie fallback still works.

The defaults are a 4% elite chance and an 8% Cursed Candy drop chance. During Witching Hour and Blood Moon the elite chance is multiplied by `special-mobs.event-chance-multiplier` (default 1.5, bounded in code to 1–3). Admins may turn the feature off with `special-mobs.enabled: false`. Custom models are forcibly kept off and Haunted Village maps remain disabled. Existing server configs get a one-time `migrations.vanilla-elite-mobs-v1` migration that adds missing profiles while preserving defined profile settings. No MythicMobs, ModelEngine, additional mob textures, or custom sounds are required.

## Nine-chapter vanilla story campaign

The original nine-chapter story is retained without requiring retired MythicMobs/ModelEngine creatures. `/halloween quests` shows the active chapter, objective progress, and fragment reward. Chapters use real vanilla actions: hostile-mob kills, configured mining blocks, mature crop harvests, fishing, discovering a configured hidden location, and unlocking a real protected underground cache. Secret-discovery chapters do not progress on unconfigured placeholder locations, and cache chapters progress only after the cache has been successfully unlocked. Progress and completed chapter IDs use the existing player-data store and survive restarts. Admins can reset an online player's progress with `/halloween quests reset <player>`.

The campaign is controlled by `story-quests.enabled` in `config.yml`. Existing cleanup-branch configs are migrated once to enable it; after that, administrators can disable it without the startup migration turning it back on. The campaign needs no custom mob models, custom audio, or additional textures.

## Vanilla timed events

The replacement event scheduler uses existing Minecraft assets only. Automatic events begin after `vanilla-events.first-delay-seconds`, then repeat at a random interval between `interval-minutes.min` and `interval-minutes.max`. Each event lasts `duration-minutes`. Admins can test one without waiting using `/halloween event start <id>`; `/halloween event status` shows the active event and time remaining.

- `pumpkin-harvest` (**Dýňová sklizeň**) multiplies farming fragment rewards.
- `witching-hour` (**Čarodějná hodina**) multiplies mob-kill fragment rewards and plays a vanilla witch ambience sound at event start.
- `blood-moon` (**Krvavý měsíc**) multiplies mob-kill fragment rewards and increases damage from hostile mobs against eligible players.

Use `/halloween event stop` to end the current event early. `/halloween off` stops the event scheduler along with fragment progression; `/halloween on` starts a fresh schedule. Event multipliers and timing can be adjusted under `vanilla-events` in `config.yml`. The retired custom-mob events remain disabled and are not reactivated by this scheduler.

## Vanilla ambient atmosphere

The replacement ambience adds occasional, localized cues using only Minecraft's built-in ash and soul particles plus vanilla cave, witch, and phantom sounds. It is deliberately sparse: each pulse chooses eligible players individually, uses a randomized location nearby, and never stops or replaces Minecraft's normal background music. No custom sound files, ItemsAdder sound registration, resource pack, shader, or model is required.

Tune `vanilla-atmosphere` in `config.yml`: `min-interval-seconds` and `max-interval-seconds` set the interval between pulses (default 90–180 seconds), `player-chance` sets the probability a given eligible player receives a cue on each pulse (default 0.35), `particles-enabled` and `sounds-enabled` can be switched off independently, and `sound-volume` controls ambience volume. It pauses when Halloween is turned off and restarts cleanly after `/halloween reload`. Both sound and particle cues respond to the running event: happy-villager sparkles and occasional composter sounds during Pumpkin Harvest, witch particles/sounds during Witching Hour, and soul-fire flames with phantom/Wither ambience during Blood Moon.

## Hidden discoveries and Easter eggs

The vanilla discovery system preserves three hidden locations from the original plugin: `blood-altar` (Krvavý oltář), `witch-den` (Doupě hexové čarodějky), and `forgotten-grave` (Zapomenutý hrob). They use only built-in particles and sounds—no custom mobs, models, shader, or resource-pack assets.

An administrator stands at each intended spot and runs `/halloween setsecret <id>`. The coordinates are saved in the plugin config, but players never see them. Players use `/halloween secrets` to see how many places they have found and read the hints for undiscovered places. Entering the configured radius grants that player the location's one-time fragment reward (250, 300, or 350 by default). The find and reward are saved immediately to `plugins/HalloweenCore/data.yml`.

All three locations ship as unconfigured, so nothing rewards players at a placeholder coordinate. Migration v2 merges any missing stock definitions into existing server configurations—even if the previous migration flag is already set—while preserving custom locations and their configured coordinates, names, radii, and rewards.

## Vanilla world features

On newly generated Overworld chunks only, the plugin can rarely add small natural pumpkin patches in open grassland/forest biomes, a few cobwebs inside naturally enclosed cave pockets, and a tiny ruined ancient altar made from mossy cobblestone, cracked stone bricks, and a soul lantern. The altar chance defaults to 2% per new chunk. It uses vanilla blocks only, does not add custom mobs or custom models, and does not scan older chunks or replace terrain blocks. Surface placement requires natural ground with a clear 5×5 area; local tile-entity checks guard each placement. Cave-web placement is limited by configured Y levels and requires natural stone around the candidate.

Settings live under `world-features` in `config.yml`:
- `pumpkin-patch-chance-per-chunk`: chance to try a small pumpkin patch per newly generated Overworld chunk (default 6%).
- `cave-web-patch-chance-per-chunk`: chance to try a cave-web patch (default 4%).
- `ancient-altar-chance-per-chunk`: chance to try a tiny ruined altar on a newly generated chunk (default 2%).
- `altar-attempts-per-feature`: bounded number of safe surface locations to check for an altar.
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

## Release verification

Before deploying to a live server, follow [the staging release verification checklist](docs/RELEASE_VERIFICATION_CHECKLIST.md). CI confirms compilation and source/content invariants; it does not prove in-game Paper/Purpur behavior.

## Build

Requires Java 21 and Maven:

```bash
mvn -B clean package
```

GitHub Actions builds `target/HalloweenCore.jar` and an ItemsAdder content ZIP containing only the candy item and the three retained pumpkin texture files. It no longer runs asset/audio generators or creates custom-mob model packs.
