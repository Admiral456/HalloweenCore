# Král upírů — technický kontrakt

## Pevné podmínky
- at least 10 blocks visible height
- at least 8 blocks static wing span
- wings remain mandatory
- HalloweenCore owns the encounter lifecycle and reward logic
- finale stays gated by global progress, configured arena, MythicMobs, ModelEngine and model readiness

## Model
Original Blockbench Generic Model: `mythicmobs/models/vampire_king.bbmodel`; texture atlas `mythicmobs/models/vampire_king.png`. Includes body armour, skeletal crowned head, articulated limbs, ceremonial blade, split cape, wide bat wings, hitbox and shadow bones; animations `idle`, `walk`, `attack`, `fly`. Validate with `python3 scripts/validate_vampire_model.py`.

## Signature attacks
- Phase I — Falešná kořist: rune ring charges then detonates after its warning.
- Phase II — Krvavý puls: expanding shock ring marks the knockback/damage zone.
- Phase III — Zrcadlový výpad: three animated portals; one is randomly selected for impact.
- Phase IV — Zatmění: three concentric rings pulse at different rates; outer radius is the actual danger radius.

Live combat and the admin preview use the same animated telegraphs. Damage applies only at the corresponding impact; it is ignored if the encounter has ended.

## Installation checklist
1. Back up and use a staging server.
2. Put the model in ModelEngine `blueprints/` or a MythicMobs pack `models/` directory.
3. Run `/meg reload models`; inspect reload logs.
4. Distribute the generated resource pack and verify it is accepted by clients.
5. Install MythicMobs mob/skill YAML and run `/mm reload`.
6. Verify model, dimensions, texture, hitbox and animations in-game.
7. Keep `bosses.vampire.model.ready: false` until runtime verification passes.
