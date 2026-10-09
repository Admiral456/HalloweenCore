# Vampire King arena builder

## Safe usage

1. Travel to a suitable clear location in the target world.
2. Stand on the intended center-floor block and run `/halloween setvampirearena`.
3. Run `/halloween buildvampirearena` for a read-only preflight.
4. Review the world, center, 97-block diameter, and warning.
5. Only run `/halloween buildvampirearena confirm` after verifying the location.

The builder requires the configured world and all chunks around a 48-block radius to be loaded. It scans the circular build footprint from the future floor to 20 blocks above it and aborts if it finds non-air blocks, terrain more than six blocks below the intended floor, or unavailable chunks. It never clears trees or buildings automatically. The confirmation replaces the top terrain layer inside the 97-block circular arena with its floor. Make a world backup before confirming; the check/build scans a large field and may cause a short main-thread pause.

## Generated layout

- 97-block diameter circular blackstone/deepslate floor
- concentric crying-obsidian rings and cardinal/diagonal red runes
- outer 2-block battlement with four 5-block-wide entrances
- eight 5×5 gothic towers with gilded blackstone, crying obsidian and soul-lantern crowns
- four monumental gatehouses plus eight inner summoning obelisks and four cardinal rune pylons
- central 13-block boss spawn circle remains open and flat

The arena can be previewed/built without progress. Building it modifies approximately a full 97-block circular footprint and its towers, so take a world backup first. The preview is read-only; only `confirm` changes blocks. Building it does not enable the normal finale. `/halloween boss test` bypasses the progress/finale/model-ready gates for an admin test only; it requires the configured arena and a working MythicMobs `vampire-king` definition and gives no rewards or finale completion. `bosses.vampire.model.ready` remains `false` until ModelEngine import and client resource-pack testing pass.
