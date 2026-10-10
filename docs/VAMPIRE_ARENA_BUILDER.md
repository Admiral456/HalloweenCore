# Vampire King arena builder

## Safe usage

1. Travel to a suitable, mostly clear location in the target world and stand on the block where the arena floor should be.
2. Run `/halloween buildvampirearena`. The command automatically chooses the center from the block under your feet; you do not need to run `/halloween setvampirearena` or find an exact preconfigured center.
3. Review the read-only preflight, world, chosen block, 97-block diameter and warning.
4. Only run `/halloween buildvampirearena confirm` after verifying the location. Confirmation uses the saved preview location, so it cannot silently shift the build if you move.

The builder requires all chunks around the 48-block radius to be loaded. It scans the circular build footprint from the future floor to 20 blocks above it and aborts if it finds non-air blocks, unavailable chunks or terrain holes deeper than six blocks. It never clears trees/buildings automatically. Confirmation replaces the top terrain layer inside the 97-block circular arena with its floor. Make a world backup before confirming; the check/build scans a large field and may cause a short main-thread pause.

After the build is confirmed, the generated center is also saved as the Vampire King spawn point. `/halloween setvampirearena` remains available only for admins who want to manually change the boss spawn point.

## Generated layout

- 97-block diameter circular blackstone/deepslate floor
- concentric crying-obsidian rings and cardinal/diagonal red runes
- outer 2-block parapet with four 3-block-wide openings
- eight dark stone towers, gold/obsidian trim and soul lanterns
- central spawn floor remains flat and open for the boss

The arena can be previewed/built without progress. Building it does not enable the normal finale. `/halloween boss test` bypasses the progress/finale/model-ready gates for an admin test only; it requires the configured arena and a working MythicMobs `vampire-king` definition and gives no rewards or finale completion. `bosses.vampire.model.ready` remains `false` until ModelEngine import and client resource-pack testing pass.
