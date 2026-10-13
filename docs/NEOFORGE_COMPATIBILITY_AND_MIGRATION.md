# HalloweenCore — native migration audit (2026-10-13)

## Platform decision

The native module targets Minecraft 1.21.10 with NeoForge 21.10.64 and Java 21 using the official ModDevGradle development path. It is deliberately separate from the existing Paper/Purpur plugin.

No hybrid runtime has been selected. A plain NeoForge server does not automatically provide Bukkit/Paper plugin APIs. Public evidence found during this pass did not establish a verified Minecraft 1.21.10 hybrid build that can safely run the complete WarriorLand plugin set. Therefore existing plugins must not be assumed compatible and the native mod must not be deployed to production.

## Migration inventory

| Existing subsystem | Native target | Status in this increment |
|---|---|---|
| Paper command/event layer | Brigadier commands and NeoForge events | PARTIAL |
| Player fragments, curse levels, daily streak, comeback rewards | Server-authoritative saved player state | NOT PORTED |
| Shared server fragments/milestones | Persistent SavedData service | NOT PORTED |
| Story quest chain/rewards/history | Server-authoritative persistent quest service | NOT PORTED |
| Six timed event types | Native event scheduling, waves, stop and cleanup | PARTIAL; active state is memory-only |
| Eight special mobs | Eight native entity IDs and their first-pass debuffs | PARTIAL; shared Zombie AI and renderer |
| Headless Nick and undead horse | Custom rider/horse AI and model | PARTIAL; bespoke behavior not implemented |
| Goblin King and Vampire King | Unique models, phases, arena, loot and unlock gate | NOT COMPLETE |
| Crimson Warden armor/weapons/tools/shield | Native item IDs and item components | PARTIAL; generated first-pass visuals; in-game attributes/rendering untested |
| Cursed Talisman | Right-click activation and temporary +2 max health | PARTIAL; expiration not persisted over restart |
| Nocturne Sovereign Cowl | Native equippable and +6 armor attribute | PARTIAL; special abilities and bespoke worn model not complete |
| Batwing Elytra | Native glider component and wings equipment asset | PARTIAL; animated wing model and abilities incomplete |
| Natural decorations | Native placed feature, explicit allowed-dimension list | PARTIAL; rare small surface decorations only |
| Haunted villages and graveyards | Native structures, spacing, loot and idempotent generation | NOT PORTED |
| 97-block Vampire arena | Terrain simulation, safe edit/rollback, region integration | NOT PORTED |
| Resource pack/models | Native item/equipment JSON and generated valid PNG files | PARTIAL; initial generator only |
| Existing data migration | Backup/import tool from legacy plugin data | NOT PORTED |

## Plugin compatibility matrix

These labels refer to compatibility with the proposed native NeoForge runtime, not to the existing Paper server. No integration tests have run, so none is marked VERIFIED.

| Plugin family | Status | Reason |
|---|---|---|
| ItemsAdder, ModelEngine, MythicMobs, MythicLib | NOT VERIFIED | Not required by native mod; their Bukkit APIs are not present on a plain NeoForge server. |
| LuckPerms, Vault, EssentialsX, PlaceholderAPI | NOT VERIFIED | Requires a specific hybrid runtime and a working API bridge. |
| WorldGuard, FAWE, CoreProtect, Residence, Multiverse-Core | HIGH RISK / NOT VERIFIED | Region checks, block edits, audit hooks and distinct-world identities need an adapter tested against the exact platform. |
| ProtocolLib, PacketEvents, TAB, ViaVersion, ViaBackwards | NOT VERIFIED | No packet/protocol integration test on an exact hybrid build. |
| Skript, SkBee, Skellett, skRayFall | NOT VERIFIED | Requires a supported scripting/plugin API layer. |
| DiscordSRV, LiteBans, BattlePass, AuraSkills, EconomyShopGUI, ExcellentCrates, PlayerPoints, PlayerWarps, QuickShop-Hikari, Shopkeepers, ChestShop, VotingPlugin, InventoryRollbackPlus, FancyNpcs, FancyHolograms, DecentHolograms and other listed plugins | NOT VERIFIED | No exact integrated test has run. The live Paper/Purpur server is untouched. |

## Safety and test status

- Production WarriorLand changes: NONE.
- Native Gradle build: PENDING CI.
- Automated policy unit tests: PENDING CI.
- Native JAR loading on a dedicated server: NOT TESTED.
- Client renderer, textures and equipment: NOT TESTED in Minecraft.
- World generation and Spawn-isolation: NOT TESTED in a running game.
- Hybrid/plugin compatibility: NOT TESTED.
- Multiplayer, persistence across restart, quest completion and boss fights: NOT TESTED.

Before any deployment, copy the server and worlds into an isolated test instance, back up all player/world/plugin data, choose the exact server platform, configure the explicit allow-list, verify that the world named Spawn is rejected, and run the integration matrix against the exact JAR builds.
