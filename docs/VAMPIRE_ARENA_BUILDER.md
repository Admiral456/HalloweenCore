# Vampire King arena builder

## Safe usage

1. Travel to a suitable clear location in the target world.
2. Stand on the intended center-floor block and run `/halloween setvampirearena`.
3. Run `/halloween buildvampirearena` for a read-only preflight.
4. Review the world, center, 45-block diameter, and warning.
5. Only run `/halloween buildvampirearena confirm` after verifying the location.

The builder requires the configured world and nearby loaded chunks. It scans the circular build footprint from floor level up through the top of the towers and aborts if it sees non-air blocks or unavailable chunks. It never clears trees or buildings automatically. The confirmation replaces the top terrain layer inside the circular arena with its floor.

## Generated layout

- 45-block diameter circular blackstone/deepslate floor
- concentric crying-obsidian rings and cardinal/diagonal red runes
- outer 2-block parapet with four 3-block-wide openings
- eight dark stone towers, gold/obsidian trim and soul lanterns
- central spawn floor remains flat and open for the boss

Building the arena does not enable the boss. `bosses.vampire.model.ready` remains `false` until ModelEngine import and client resource-pack testing pass.
