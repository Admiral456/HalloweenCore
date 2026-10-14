# Vampire King arena — one-step manual generation

## Command

Run this as an operator in the target world:

`/halloween spawn vampirearena`

The command chooses a position in front of the player's horizontal look direction. The arena radius is 48 blocks (97-block diameter); it does not require a player-selected center, a separate setter command, pre-confirmation, or an empty tree-free clearing. A short best-effort root shortcut is `/spawn vampirearena`, but another plugin such as Essentials may own `/spawn`; `/halloween spawn vampirearena` is the reliable form.

## Placement and safeguards

The plugin snapshots the exact planned block changes before mutation, bounds the footprint and block count, loads only chunks that overlap the build, then writes in per-tick batches. It clears/overwrites natural terrain only inside the precomputed footprint. Block entities (for example chests or signs) that would be overwritten abort the operation before the first block change and are logged. If a write fails or the plugin shuts down mid-build, it attempts to restore the snapshot.

Rollback is best-effort and is not a substitute for a backup against a JVM crash, disk failure, or a rollback write failure. Make a world backup before first use on a production-like world. Builds exceeding `structures.max-block-changes`, height bounds, or a terrain-height delta greater than 32 blocks are rejected before changes. A completion message is sent only after every planned change has been written.

World `Spawn` is allowed for an explicit admin build. However, an arena there is not marked as an eligible Vampire King arena and the boss cannot start in that reserved world. Automatic world generation is separately opt-in by exact world name and never runs in `Spawn`.

## Layout and configuration

The vanilla placeholder geometry includes:
- 97-block diameter circular stone floor with concentric rings
- outer parapet with four openings
- four guarded towers with gold-like trim and soul lanterns
- four runic markers around the central summon point

Adjust only the operational limits in `config.yml`:
- `structures.blocks-per-tick` — blocks placed per scheduler tick (clamped in code)
- `structures.max-block-changes` — hard block-change cap (clamped in code)

Boss readiness remains gated independently by campaign completion, global progress, the generated arena, MythicMobs and a model marked ready. The test command `/halloween boss test` still bypasses progression/model readiness for an admin-only test, but cannot start inside `Spawn`. Actual Paper/Purpur and multiplayer placement tests are still required before production use.
