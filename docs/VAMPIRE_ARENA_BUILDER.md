# Vampire King arena builder

## Safe usage

1. Choose a mostly clear site in the intended world.
2. To use the block under your feet, run `/halloween buildvampirearena here` (or the shorter `/halloween buildvampirearena`). To use a centre already saved with `/halloween setvampirearena`, run `/halloween buildvampirearena selected`.
3. The command gradually loads the 7×7 chunks covering the arena, checks the 97-block diameter build footprint and prints the chosen centre.
4. Review the read-only preflight, then run `/halloween buildvampirearena confirm`. Confirmation reuses the saved preview location rather than reading a new player position.

The preflight aborts if it encounters solid overhead obstructions or deep terrain gaps. It does not automatically clear trees/buildings. Arena construction replaces the surface inside the 97-block circle with a flat patterned floor and may cause a brief server pause; back up the world first. The command does not dig a cave or summoning pit — make that hole yourself afterward if your design needs one.

The confirmed centre is also saved as the Vampire King arena/spawn point.

## Generated layout

- 97-block diameter circular blackstone/deepslate floor
- concentric crying-obsidian rings and cardinal/diagonal red runes
- outer 2-block parapet with four 3-block-wide openings
- eight dark stone towers, gold/obsidian trim and soul lanterns
- central spawn floor remains flat and open for the boss

World decorations are independent of the temporary event on/off toggle. New chunks can receive random pumpkins, cobwebs, dead plants, 3D mushrooms and graves. The procedural generator separately has a low chance to place a small haunted village or graveyard on flat, clear natural terrain; it never excavates holes and excludes the arena footprint and spawn safety radius.

The arena can be previewed/built without progress. Building it does not enable the normal finale. `/halloween boss test` bypasses the progress/finale/model-ready gates for an admin test only; it requires the configured arena and a working MythicMobs `vampire-king` definition and gives no rewards or finale completion. `bosses.vampire.model.ready` remains `false` until ModelEngine import and client resource-pack testing pass.
