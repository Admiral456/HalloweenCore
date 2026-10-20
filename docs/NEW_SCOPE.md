# New HalloweenCore scope

## Included

1. UplineDialogues owns the editable NPC conversations, response options and branching dialogue graph. The repository contains a Czech library of 15 dialogue scripts to enter in its UI; its local data format is not assumed or fabricated.
2. HalloweenCore supplies 15 configured quests spanning Lehký, Střední, Těžký and Velmi těžký. Progress and completion are stored per player; configured requirements are checked together before any items are removed. Completion is one-time.
3. Quest rewards are intentionally unset. The configured IDs and objectives are ready, but no fragment amount, item payout, chest loot or normal key source is invented.
4. A newly generated Overworld chunk has a 2.5% chance to attempt a 7x5x7 underground room. Up to 48 candidates are checked by default. The entire outer shell must be natural stone; safe interior caves/ores are preserved rather than destroying player builds or ores. World named Spawn is excluded.
5. Each room has a tagged chest which is locked with a persistent data marker. A tagged key is consumed on first unlock; the chest remains unlocked afterwards. A room record is saved for the locate commands.
6. Commands provide actual status and test surfaces: /halloween rooms status, /halloween rooms generate, /locate halloweenroom, /locateall halloweenroom and /halloween locate room.
7. ItemsAdder retains thirteen imported sweet sprites from Stev's Halloween under the MIT licence, plus the original 32x32 transparent pixel-art key icon.

## Not included

The legacy mob, boss, random-event, seasonal, grave-encounter, combat-story, shop, passive-effect, world-decoration, audio and shader systems are removed from the active Java source, config and distributed asset pack. The repository does not package the old MythicMobs/ModelEngine models or definitions. Existing player-built blocks are not mass-deleted.

## Needs live staging validation

- The new chunk event and the random site search need live testing on the target Purpur server. Existing chunks are not retroactively scanned; use /halloween rooms generate as an OP test in a permitted Overworld to verify the actual placement code.
- UplineDialogues conversations must be created/saved in its own UI on the server by using docs/DIALOGUE_LIBRARY_CZ.md. The Markdown library cannot automatically populate UplineDialogues' private local storage.
- Confirm final rewards, secret-chest loot and how normal players should acquire keys before adding any payout.
