# Vampire King — runtime checklist

This is a staging checklist. Production spawn remains disabled until all checks pass.

## Required server components
- Purpur/Paper 1.21.10 target (confirm against the actual live server)
- MythicMobs enabled
- ModelEngine enabled
- HalloweenCore `/halloween debug` reports dependencies
- Resource pack is hosted/distributed and accepted by the test client

## Model checklist
1. Run `python3 scripts/validate_vampire_model.py` in the repo/CI and verify PASS.
2. Import `vampire_king.bbmodel` into `plugins/ModelEngine/blueprints`.
3. Run `/meg reload models` and inspect logs.
4. Verify the vampire's texture, crown, sword, cape and both wings.
5. Confirm idle, walk (by previewing the animation in the editor), attack and fly animation tracks.
6. Verify the weapon remains attached to the right arm and the wings articulate from the torso.
7. Check the model's 10-block height, minimum 8-block wing span, eye height and collision box.
8. Install the MythicMobs YAML files and run `/mm reload`; spawn in a staging world only.
9. Test with the resource pack accepted and rejected to confirm expected fallback/visibility.
10. Leave `bosses.vampire.model.ready: false` until all checks have passed.

## Encounter
1. Configure arena using `/halloween setvampirearena` at the exact floor center.
2. Confirm `/halloween boss status` shows the correct readiness gate.
3. Unlock the global finale and verify the omen and countdown.
4. Verify exactly one boss appears at the saved center.
5. Check boss bar appearance and removal beyond 96 blocks.
6. At each phase, move out of the marked impact area; moving away should avoid damage.
7. Confirm damage only lands after the visual animation finishes.
8. End/reload/disable the encounter during a telegraph and confirm delayed damage is cancelled.
9. Verify projectile and melee damage contributions and the reward threshold.
10. Kill the boss and check one-time rewards and persisted victory state.

## Safety
- `/halloween off` and `/halloween reload` safely clean up the boss and bar.
- Timeout does not grant victory rewards.
- Regular mob-kill rewards are not paid for the final boss.
