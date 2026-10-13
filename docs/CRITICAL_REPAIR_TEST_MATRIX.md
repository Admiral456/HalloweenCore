# HalloweenCore — Critical Repair Test Matrix

Updated for branch `fix/critical-halloween-repairs-2026-10-12`.

## Ground rules

- **PASS** means the named check ran and produced evidence. Source code existing or compiling is not proof of gameplay behavior.
- **FAIL** means a reproducible defect was observed or a build gate failed.
- **NOT TESTED** means a real Paper/Purpur 1.21.10 server and/or Minecraft client was not available for that runtime scenario.
- Destructive arena/structure tests must use a backup world, never the production world.

## Confirmed defect and code-repair status

| Area | Finding before repair | Code/static repair | Acceptance status |
|---|---|---|---|
| Crimson Warden armor PNG | `layer_1.png` had plausible PNG metadata but undecodable image data; the old validator missed it. | Generator recreates 64×32 RGBA layers; source and packed copies are byte-identical; CI decodes PNG pixel data with Pillow verify/load. | **PASS** on CI run [38315048077](https://github.com/Admiral456/HalloweenCore/actions/runs/38315048077). This run predates the latest Cowl registry adjustment. |
| Nocturne Cowl | Old generation extruded a small inventory icon into a shallow voxel plate, and the registry was not using the documented worn 3D-helmet model path. | Separate 64×64 worn atlas and shaped cuboid model; registry uses `resource.generate: false` and `resource.model_path: armor/nocturne_sovereign_cowl_3d`; validator checks mapping and rejects overriding `equipment.id`. | Latest head CI **PENDING**; in-client render **NOT TESTED**. |
| Ordinary moon/sky | Persistent orange moon and excessive purple/orange sky tint. | Neutral ash-silver moon texture and restrained charcoal/blue shader palette; crimson only during event-synchronized client state. | Shader compilation/assets passed on the preceding repair build; Iris/GPU/client result **NOT TESTED**. |
| Talisman health | Max-health value could increase without filling the newly available hearts, appearing broken in the HUD. | Positive +20 max-health modifier remains keyed uniquely; activation fills health under a persistent one-minute re-equip cooldown; lore explicitly means +20 health points = +10 hearts. | In-game attribute, HUD and cooldown behavior **NOT TESTED**. |
| Haunted village/graveyard terrain | Surface-height spread was capped at 2 blocks, rejecting ordinary terrain. | Configurable `max-terrain-relief`, default 8 blocks, and operator-visible skip reason. | Real manual/natural placements, spacing and duplicate protection **NOT TESTED**. |
| Vampire arena terrain | Preflight rejected blocks above the floor without distinguishing natural terrain from player builds. | Restricted cut/fill of whitelisted natural blocks; rejects tile entities and unknown/player-built material; checks WorldGuard when enabled; repeats preflight before writes. | Flat/uneven build and protected-region test **NOT TESTED**. |
| Event starts | A countdown/announcement could appear even when no eligible player/world or successful mob spawn existed. | Eligible-player gate, per-event enable keys, explicit failure reason, actual wave spawn count and throttled diagnostics. | Java build passed on earlier repair head; six actual event lifecycles **NOT TESTED**. |
| Story/progression | Original quest list ended at Nightmare and did not link the Blood Moon event to the Vampire King finale. | Two final stages connect a real Blood Moon wave to finale unlock and a real encounter victory to boss quest completion; admin status/debug and confirmed reset are provided. | Fresh-profile end-to-end storyline, persistence and second-player isolation **NOT TESTED**. |

## Staging tests still outstanding

| Test | Status |
|---|---|
| Shield inventory icon, hand/offhand and blocking pose use shield models/textures and never Elytra mapping | **NOT TESTED** |
| Batwing Elytra icon, broken state, worn wings, normal glide and firework boost | **NOT TESTED** |
| All four armor pieces apply/remove attributes correctly; incoming-damage tests versus Netherite | **NOT TESTED** |
| Talisman max-health attribute/HUD, main hand/offhand, swap, relog, death/respawn and cooldown | **NOT TESTED** |
| Each of six events is force-started and real effects, duration, cooldown, rewards and cleanup are observed | **NOT TESTED** |
| Blood Moon has exactly 3× hostile-mob damage once and restores baseline at event end | **NOT TESTED** |
| Manual and natural haunted-village/graveyard placement; spacing and player-build avoidance | **NOT TESTED** |
| Goblin/Vampire arena preview and build on flat/uneven terrain; retry and WorldGuard refusal | **NOT TESTED** |
| Fresh player completes every quest through Blood Moon, real Vampire King and final reward; progress survives restart | **NOT TESTED** |
| Second profile does not inherit another player's individual quest progress/rewards | **NOT TESTED** |
| ModelEngine render/animation for all custom mobs, Headless Nick and both bosses; no missing-texture magenta | **NOT TESTED** |
| ItemsAdder resource-pack delivery on Hostify and shader result with/without Iris | **NOT TESTED** |

## Release decision

Do **not** call this a verified live release until the latest head's CI is green and every staging row above has evidence. A successful JAR build alone is not enough.
