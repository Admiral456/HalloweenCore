# Král upírů — runtime checklist

This checklist is intentionally for a test/staging server. The production boss stays disabled until every item is verified.

## Dependency check

- Purpur/Paper 1.21.10
- MythicMobs present
- ModelEngine present
- ItemsAdder present
- HalloweenCore `/halloween debug` reports all required providers

## Model check

- `bosses.vampire.model.id` is `vampire_king`
- `bosses.vampire.model.ready` remains `false` until the real model is installed
- model is at least 10 blocks tall
- full wing span is at least 8 blocks
- smallest animated pose still respects the minimum
- model has working idle, walk, attack and wing animations

## Arena / encounter

1. Stand on the central floor block and run `/halloween setvampirearena`; confirm saved X/Z end in .5 and Y is floor-level.
2. Verify the world is loaded.
3. Verify `/halloween boss status` reports final readiness.
4. Only after the model is verified, enable the boss and set `model.ready: true` in a test environment.
5. Unlock the global finale and wait through the omen/countdown; no admin spawn command should be needed.
6. Confirm exactly one Vampire entity spawns at the configured centre; `/halloween boss start` is admin-only test override.
7. Confirm the custom boss bar appears to nearby eligible players.
8. Walk outside the boss-bar radius and confirm the bar disappears.
9. Pull the boss outside the arena and confirm it is returned.
10. Check each phase at approximately 70%, 40% and 15% HP.
11. Confirm phase I sigils, phase II blood pulse, phase III three deceptive runes and phase IV outer-ring telegraph.
12. Move out of each marked danger zone and confirm the delayed strike misses.
13. Deal damage with melee and projectiles; verify both count toward contribution.
14. Keep one player near the arena without attacking and confirm they do not qualify for participation or top-damage rewards.
15. Kill the boss and verify only players meeting time and damage thresholds are rewarded.
16. Confirm the same final boss cannot be started again after victory.
17. Restart the server and confirm the defeated state is still stored.

## Safety checks

- `/halloween off` removes the boss and boss bar.
- `/halloween reload` removes the active boss encounter safely.
- no regular mob-kill reward is paid for the Vampire itself
- offline qualifying participants still receive their fragment reward
- the encounter times out without granting victory rewards
- no second boss bar appears from MythicMobs
