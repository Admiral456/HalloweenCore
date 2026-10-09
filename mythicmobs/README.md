# HalloweenCore MythicMobs pack

- `mobs/vampire-king.yml`: boss definition and ModelEngine attachment.
- `skills/vampire-king.yml`: reusable animation state skills.
- `models/vampire_king.bbmodel`: editable Blockbench model with embedded texture and animations.
- `models/vampire_king.png`: external copy of the texture atlas.

HalloweenCore owns boss spawning, health bar, attack phases, rewards and cleanup. Import the blueprint to ModelEngine, run `/meg reload models`, distribute the generated ItemsAdder resource pack, then install the mob/skill YAML in `plugins/MythicMobs/Mobs/` and `plugins/MythicMobs/Skills/` and run `/mm reload`. Verify the model and all four attack telegraphs on a real client in staging. Admin test mode: after the arena center is configured and the MythicMobs mob is loaded, use `/halloween boss test` to spawn the boss without progress. The test never rewards players or marks the finale complete; if ModelEngine is not imported yet, you may see only the base mob rather than the intended model. Do not enable `bosses.vampire.model.ready` until the client model test passes.
