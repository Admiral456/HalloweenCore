# Vampire King — ModelEngine integration

The repo contains an editable Blockbench Generic Model blueprint:
- `mythicmobs/models/vampire_king.bbmodel`
- matching 128×128 atlas `mythicmobs/models/vampire_king.png` (also embedded in the model)
- model ID `vampire_king`
- animation tracks `idle`, `walk`, `attack` and `fly`

The design uses obsidian armour, crimson cloth, a gilded crown, skeletal face and red eyes, claws, ceremonial blade, split cape and wide articulated bat wings. Static dimensions are validated against the 10-block height / 8-block wingspan contract.

Install on staging only:
1. Copy the blueprint to `plugins/ModelEngine/blueprints/vampire_king.bbmodel` (or a MythicMobs pack's `models/` folder).
2. Run `/meg reload models` and inspect parse logs.
3. Distribute and accept the generated resource pack on a client.
4. Install the mob and skill YAML in MythicMobs, then run `/mm reload`.
5. Spawn the mob in a staging world and verify scale, wing pose, blade, texture, hitbox and animation playback.
6. Keep `bosses.vampire.model.ready: false` until the runtime check passes.

The four live attacks and `/halloween bosseffects <1|2|3|4>` share animated telegraphs: charging runic rings, expanding blood shockwave, three mirror portals and pulsing eclipse circles with flame pillars. Damage resolves at the animation impact. CI validates files and Java compilation, not client-side resource-pack delivery.
