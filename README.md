# HalloweenCore

HalloweenCore combines the existing quest/fragment economy, secret underground rooms with rarity-matched keys and the 13 preserved licensed candy textures with selected features integrated from SpookySeason 1.7.0 (MIT) and an independently written HorrorNights-style night system.

## Features

- Trick-or-Treat interactions with doors and villagers, configurable cooldowns, treat rewards and a leaderboard.
- Haunted nights, ghosts, bat swarms and fog/jump-scare effects.
- Blood rain, pumpkin rain, configurable Headless Horseman boss and wave-based undead raids.
- Per-player opt-out and sound volumes, a settings/leaderboard GUI, season-end rewards and optional WorldGuard, GriefPrevention, PlaceholderAPI and CustomJukebox integrations.
- Independent random horror nights with stronger/invisible mobs, particles, thunder, effects and ambient sounds.
- Existing quest progress, fragment economy and special keys; secret underground rooms continue to generate only in new chunks in configured worlds, each with a one-time locked chest reward.
- The existing 13 licensed candy textures and secret-key texture remain unchanged.

## Licensing

Selected SpookySeason 1.7.0 source files are included under the MIT License. The original MIT notice is in `THIRD_PARTY_LICENSES/SpookySeason-MIT.txt`; source files carry per-file attribution. HorrorNights is not copied or redistributed: only its public feature description was used to guide an independent implementation. See `docs/THIRD_PARTY_NOTICES.md`.

## Important configuration

- Set `secret-rooms.worlds` to exact world names. An empty list disables room generation; `Spawn` is excluded by default.
- `enabledWorlds` controls imported SpookySeason features. Empty means all worlds.
- `activeWindow.startDay` and `.endDay` control seasonal features in October. The default window is October 27–31; set either value to `0` for no calendar restriction.
- `horrorNights.enabled-worlds` empty means all worlds except `excluded-worlds`. Tune chance, spawn count, mob types and effects in `horrorNights`.
- Updates are checked against HalloweenCore GitHub Releases (an unpublished release is treated as no update). The combined plugin targets Paper/Purpur on Java 21. Folia is not enabled in this combined build.

## Commands

- `/halloween quest start <id>`, `/halloween quest progress <id> [amount]`, `/halloween quest complete <id>`, `/halloween quest status [id]`
- `/halloween fragments`, `/halloween key give [common|rare|legendary] [player] [amount]`, `/halloween rooms status`
- `/halloween horror <start|stop|status> [world]`, `/halloween reload`
- `/spooky on|off|status|reload|rewards|optout|top|menu`
- `/spookyvolume <ambient|ghost|rain|reset> [0.0-1.0]`
- `/pumpkinrain <start|stop>`, `/bloodrain <start|stop>`, `/spookyboss ...`, `/spookyraid ...`

Build and smoke-test the plugin on a test server before deploying it to a live server.