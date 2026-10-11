# Vampire King arena builder

## Safe usage

1. Choose the world and position for the arena floor.
2. To choose the block under your feet, run `/halloween buildvampirearena here` (or `/halloween buildvampirearena` if no saved ungenerated center is active). To use a center saved via `/halloween setvampirearena`, run `/halloween buildvampirearena selected`.
3. The command loads the chunks in the 97-block-diameter footprint, counts the blocks that will be replaced and prints the exact center.
4. Review the preview, then run `/halloween buildvampirearena confirm`. Confirmation reuses the saved preview, even if the admin has moved or changed position in the meantime.

**Destructive operation:** confirmation deliberately levels the circle, clears blocks above the chosen floor (including trees and existing buildings), fills low spots, and then places the patterned floor, towers and gates. A backup is required before confirming. The arena builder does not excavate a cave or summoning pit; create any such hole manually after the arena is built.

The confirmed centre is also saved as the Vampire King arena/spawn point.

## Generated layout — ultimate gothic raid arena

- 97-block diameter circular floor with layered blackstone/deepslate rings, occult compass rays, crimson inlays and a clear central boss spawn circle
- eight hollow 7×7 watchtowers with interior levels, narrow red-glass slits, gilded mouldings, crenellated crowns and soul-lantern beacons
- circular curtain wall, eight external lantern buttresses and four monumental gatehouses with side pylons and blood-glass details
- 17×9 royal sanctuary: giant gothic blood-moon portal, five-wide crimson-glass heart, gilded trim, hanging chains, lantern pinnacles and a raised Vampire King's throne
- four raised sarcophagi on the outer ritual ring, outside the main boss spawn space
- the central 15-block spawn area remains clear of raised pillars; cardinal rune pylons start outside it
- 24-block vertical preflight clearance covers the tallest tower crowns and portal ornaments

World decorations are independent of the temporary event on/off toggle. New chunks can receive random pumpkins, cobwebs, dead plants, 3D mushrooms and graves. The procedural generator separately has a low chance to place a small haunted village or graveyard on flat, clear natural terrain; it never excavates holes and excludes the arena footprint and spawn safety radius.

The arena can be previewed/built without progress. Building it does not enable the normal finale. `/halloween boss test` bypasses the progress/finale/model-ready gates for an admin test only; it requires the configured arena and a working MythicMobs `vampire-king` definition and gives no rewards or finale completion. `bosses.vampire.model.ready` remains `false` until ModelEngine import and client resource-pack testing pass.
