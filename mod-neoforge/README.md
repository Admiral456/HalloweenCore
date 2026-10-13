# HalloweenCore Native — NeoForge 1.21.10

This directory contains the native Minecraft mod source. The legacy Paper/Purpur plugin at the repository root is a separate edition; its JAR is not a NeoForge mod.

## Build target

- Minecraft 1.21.10
- NeoForge 21.10.64
- Java 21
- NeoForge ModDevGradle 2.0.148

Generate textures and item model JSON, then build and run unit tests:

    python3 -m pip install Pillow
    python3 scripts/generate_native_mod_assets.py
    gradle -p mod-neoforge clean test build

The native artifact is produced in mod-neoforge/build/libs/. The dedicated native workflow also uploads the JAR as a GitHub Actions artifact.

## World safety defaults

The common configuration is halloweencore-common.toml. The explicit allowed-world-ids list defaults to empty, so world-changing features, event waves, admin spawns and talisman activation are rejected until an operator configures the intended dimension ID. An identifier or runtime level name equal to Spawn is always rejected where the active ServerLevel is available.

Important limitation: vanilla/NeoForge dimensions do not expose Multiverse-Core's distinct Bukkit world identities as separate IDs when multiple Bukkit worlds share a dimension. No hybrid compatibility or absolute protection claim is made for that scenario; a hybrid adapter and a test against the exact server build are required before use. Do not deploy this to the production WarriorLand server.

## What this increment contains

- Native mod metadata and NeoForge registries.
- Registered native item identities for the Cursed Talisman, Nocturne Cowl, Batwing Elytra, Crimson Warden armor, sword, pickaxe, axe, shovel, hoe, shield and seasonal items.
- An idempotent full Crimson Warden set bonus (+4 armor toughness and +10% knockback resistance) which is removed when a piece is removed or the player leaves an allowed world.
- Eleven registered entity identities, individual health/damage/speed values and server-side debuff behavior.
- Server-authoritative commands for status, starting/stopping events, spawning mobs and giving items.
- Persistent `SavedData` for player fragments, special-mob kills, boss defeats, ore mining, mature-crop harvesting and the server-wide total of collected fragments.
- `/halloween progress` reports the persistent progression counters from an allowed world.
- Fragment reward rules: 1 for a normal special mob, 25 for a named boss, 1 every 4 eligible ore blocks, and 1 every 8 fully grown crops harvested.
- Six event IDs, an initial automatic event schedule, bounded event waves, a single-point three-times Blood Moon damage multiplier for common and special hostile mobs, and a harvest event that reacts to ripe crops.
- A time-limited +1-heart Cursed Talisman modifier and generated art/model resources.
- A native biome-modifier/placed-feature path for rare surface decorations, gated by the explicit world policy.
- Initial automated tests for the fail-closed allow-list and reserved Spawn identifier.

## Known incomplete work

This is an initial native foundation, not the complete ultimate prompt implementation. In particular:

- all custom entities currently share the Zombie base AI and first-pass Zombie renderer; the generated individual texture art is not yet attached to unique renderers;
- Goblin King and Vampire King still need unique models, AI state machines, phases, boss arenas and loot;
- Headless Nick still needs a truly headless rider model and full undead-horse lifecycle;
- Nocturne Cowl needs custom wearable geometry and its final Grave Sight, Veilstep and Nocturnal Aegis abilities;
- Batwing Elytra has the native glider/equipment components but not the articulated wing renderer or all abilities;
- the timed talisman state is currently memory-only; durable persistence across a server restart is not complete;
- the nine-step story-quest sequence, quest-completion persistence, daily/comeback rewards, curse levels, mining/fishing reward parity and the final Rift/Vampire King unlock are not yet ported;
- safe terrain-adaptive arena generation and full haunted village/graveyard structures are not yet ported;
- no dedicated-server launch, multiplayer rendering, hybrid compatibility or in-game Spawn-isolation test has been performed.

Do not treat successful compilation as proof of in-game behavior.
