# Vampire King arena builder

## Safe usage

1. Travel to a suitable clear location in the target world.
2. Stand on the intended center-floor block and run `/halloween setvampirearena`.
3. Run `/halloween buildvampirearena` for a read-only preflight.
4. Review the world, center, 97-block diameter, and warning.
5. Only run `/halloween buildvampirearena confirm` after verifying the location.

The builder requires the configured world and all chunks around a 48-block radius to be loaded. It scans the circular build footprint from the future floor to 20 blocks above it and aborts if it finds non-air blocks or unavailable chunks. It never clears trees or buildings automatically. The confirmation replaces the top terrain layer inside the 97-block circular arena with its floor. Make a world backup before confirming; the check/build scans a large field and may cause a short main-thread pause.

## Generated layout

- 97-block diameter circular blackstone/deepslate floor
- concentric crying-obsidian rings and cardinal/diagonal red runes
- outer 2-block parapet with four 3-block-wide openings
- eight dark stone towers, gold/obsidian trim and soul lanterns
- central spawn floor remains flat and open for the boss

The arena can be previewed/built without progress. Building it does not enable the normal finale. `/halloween boss test` bypasses the progress/finale/model-ready gates for an admin test only; it requires the configured arena and a working MythicMobs `vampire-king` definition and gives no rewards or finale completion. `bosses.vampire.model.ready` remains `false` until ModelEngine import and client resource-pack testing pass.
