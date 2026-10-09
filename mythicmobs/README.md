# HalloweenCore MythicMobs pack

- `mobs/vampire-king.yml`: boss definition and ModelEngine attachment.
- `skills/vampire-king.yml`: reusable animation state skills.
- `models/vampire_king.bbmodel`: editable Blockbench model with embedded texture and animations.
- `models/vampire_king.png`: external copy of the texture atlas.

HalloweenCore owns boss spawning, health bar, attack phases, rewards and cleanup. Import the blueprint to ModelEngine, run `/meg reload models`, distribute the generated resource pack, then install the mob/skill YAML and run `/mm reload`. Verify the model and all four attack telegraphs on a real client in staging. Do not enable `bosses.vampire.model.ready` until that test passes.
