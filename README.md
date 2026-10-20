# HalloweenCore — dialogue quests and secret rooms

HalloweenCore is being narrowed to the new agreed scope for Paper/Purpur 1.21.10 and Java 21.

## What remains

- UplineDialogues handles NPC conversations, answer choices and branching dialogue trees.
- HalloweenCore stores quest state and progress, prevents repeat completions, and supports one-time configured fragment and item rewards. The default quest list is empty, fragment rewards default to 0, and item rewards default to an empty list.
- Newly generated Overworld chunks can rarely contain a small sealed underground room built only into solid natural stone.
- Each generated room contains one locked chest. A special key is required the first time it is opened; one key is consumed and the chest remains unlocked afterwards.
- The ItemsAdder content keeps the 13 imported Stev's Halloween sweet sprites under their MIT licence, plus the new custom key texture.

## Explicitly removed

The old Halloween mob system, custom mob models, bosses, grave encounters, random Halloween events, seasonal scheduler, Halloween shop/gear, old fragment farming, world decoration, Halloween audio, sky shader, haunted-village discovery and the old nine-chapter combat story are not part of this version. They are not re-enabled by configuration and are not packaged.

Existing tagged Halloween mobs and bosses are removed when their chunks are loaded. Old world blocks/structures are not mass-deleted: automatic block removal could destroy player builds. Do not use old cemetery/village generators; remove any already-built old structures manually after taking a world backup.

## Dialogue integration

Install UplineDialogues separately; it is not bundled into HalloweenCore. Version 3 provides an in-plugin editor at /updialog panel, branched dialogue maps, conditions, inventory actions and command actions. Follow docs/UPLINEDIALOGUES_INTEGRATION.md to connect a dialogue node to a quest command.

Optional PlaceholderAPI integration exposes:
- %halloween_fragments%
- %halloween_quest_<id>_started%
- %halloween_quest_<id>_progress%
- %halloween_quest_<id>_completed%

## Quest rewards are deliberately unset

The default configuration has no quest definitions. A later-defined quest may optionally configure reward-fragments and reward-items, but defaults to 0 fragments and no items. If a quest is completed before its reward is agreed, completion is saved without payment; once a reward is configured, it can be claimed once with /halloween quest claim <id>. No money, equipment, chest loot, candy payout or key source is assumed. The exact contents and reward must be chosen before they are configured.

## Secret rooms and keys

Rooms are considered only when a new Overworld chunk is generated. Default generation chance is 2.5% per new chunk, restricted to solid natural stone between Y=-36 and Y=24, and excluded from the world named Spawn. It will not carve caves, ores, liquids, block entities, or existing blocks other than natural stone in a new chunk.

The chest intentionally has no configured loot. The key is created by HalloweenCore using the ItemsAdder item ID warriorland_halloween:secret_room_key when ItemsAdder is installed, with a named vanilla fallback otherwise. The admin key command is for testing only; no drop, NPC payout or automatic acquisition source is configured until the agreed system specifies it.

## Build and install

1. Build with Java 21 using Maven: mvn -B clean package.
2. Install target/HalloweenCore.jar in plugins and restart the server.
3. Copy the warriorland_halloween folder from the ItemsAdder ZIP into plugins/ItemsAdder/contents/.
4. Run /iazip and verify delivery using /iainfo.
5. Install UplineDialogues separately and build NPC conversation trees in its panel.

The CI build validates the retained asset set, compiles the plugin and packages the ItemsAdder content. A live Purpur/ItemsAdder/UplineDialogues test is still required before production rollout.
