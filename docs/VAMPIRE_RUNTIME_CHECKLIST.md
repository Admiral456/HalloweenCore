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

1. Set the arena with `/halloween setvampirearena`.
2. Verify the world is loaded.
3. Verify `/halloween boss status` says the encounter is waiting for final readiness.
4. Only after the model is verified, enable the boss and set `model.ready: true` in a test environment.
5. Run `/halloween boss start`.
6. Confirm exactly one Vampire entity is spawned.
7. Confirm the custom boss bar appears to nearby eligible players.
8. Walk outside the boss-bar radius and confirm the bar disappears.
9. Pull the boss outside the arena and confirm it is returned.
10. Check each phase at approximately 70%, 40% and 15% HP.
11. Confirm phase abilities, sounds and particles.
12. Deal damage with melee and projectiles; verify both count toward contribution.
13. Keep one player in the arena without attacking and confirm they cannot win the top-damage bonus.
14. Kill the boss and verify participation, killer and top-damage rewards.
15. Confirm the same final boss cannot be started again after victory.
16. Restart the server and confirm the defeated state is still stored.

## Safety checks

- `/halloween off` removes the boss and boss bar.
- `/halloween reload` removes the active boss encounter safely.
- no regular mob-kill reward is paid for the Vampire itself
- offline qualifying participants still receive their fragment reward
- the encounter times out without granting victory rewards
- no second boss bar appears from MythicMobs
