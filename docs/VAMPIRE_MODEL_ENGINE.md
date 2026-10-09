# Vampire King — ModelEngine integration and quality checklist

## Blueprint
- `mythicmobs/models/vampire_king.bbmodel`: editable Blockbench Generic Model blueprint.
- `mythicmobs/models/vampire_king.png`: matching 128×128 atlas embedded byte-for-byte in the blueprint.
- Model ID: `vampire_king`.
- Components: layered obsidian armour, crimson coat, skull-like face, crown/horns, claws, ceremonial blade, split cape and broad bat wings.
- Animation tracks: `idle`, `walk`, `attack`, `fly`.

## Rig and ModelEngine restrictions
Cubes are individually UV-mapped. Cube rotations are restricted to 0°, ±22.5° or ±45° around one axis. The rig hierarchy keeps head, arms, legs, wings and cloak under the body; the ceremonial blade follows the right arm; hitbox and shadow remain root children. The hitbox pivot is placed at the vampire's upper body so the tall model looks at players correctly.

Automated validation checks PNG integrity, embedded/external texture parity, UV bounds, cube rotation constraints, bone references/parenting, animation tracks and the 10-block height / 8-block wing-span contract:
`python3 scripts/validate_vampire_model.py`.

## Import and runtime verification
1. Back up the server and use staging.
2. Copy the blueprint into `plugins/ModelEngine/blueprints/vampire_king.bbmodel` or a MythicMobs pack's `models/` folder.
3. Run `/meg reload models`; verify there are no blueprint parsing errors.
4. Host/distribute the generated resource pack and accept it on a test client.
5. Install the MythicMobs mob/skills YAML and run `/mm reload`.
6. Verify model scale, wing animation, blade attachment, texture, hitbox, idle and attack.
7. Keep `bosses.vampire.model.ready: false` until all live checks pass.

All four live attacks share the preview's animation path; damage resolves only at impact, and the callback exits if the encounter has ended.
