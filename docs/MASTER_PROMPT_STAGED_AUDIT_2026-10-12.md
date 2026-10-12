# HalloweenCore — audit staged delivery (12. 10. 2026)

## Stage 0 — repository audit

**Status: PASS for audit completeness. NOT TESTED in a live Minecraft runtime.** This document records the baseline before feature work under the staged master prompt. It does not mean the full expansion or later release gates are complete.

### Verified project baseline

- Repository: `Admiral456/HalloweenCore`; working branch: `feat/haunted-world-gear-vfx`; review PR: [#51](https://github.com/Admiral456/HalloweenCore/pull/51). Default branch `main` was not modified.
- Server target declared by the project: Paper/Purpur Minecraft 1.21.10. Java release in `pom.xml`: 21. Fabric client companion targets Minecraft Java 1.21.10. ItemsAdder minimum version declared in config: 4.0.13.
- Optional integrations are declared as soft dependencies: ItemsAdder, MythicMobs, ModelEngine, BattlePass, WorldGuard and PlaceholderAPI. The core plugin is a Java/Paper implementation; external mob geometry needs its matching MythicMobs/ModelEngine runtime.
- Baseline CI run #1121, run ID `38216221425`, completed successfully. Its job reports success for compilation, GLSL validation, generated assets, model validation, Fabric client build, Maven build and archive packaging. This is an automated build baseline, not proof of in-game appearance or server-side gameplay.
- The CI model validator requires definitions for all eight special mobs and compatible Blockbench/ModelEngine model files with embedded/external 128×128 atlases and required animation tracks. Five source model/texture pairs are checked into the repository; the build generates the other three from source templates and adds distinctive geometry before validation/packaging.

### Existing functionality found

- Existing progression, fragments, quests, seasonal events, Blood Moon, Vampire King progression hooks and Vampire arena builder are present. Preserve these systems and their IDs unless a verified defect requires a documented correction.
- The special-mob manager maps all eight requested types: Cursed Zombie, Void Reaper, Gravekeeper, Frost Stalker, Blood Spider, Pumpkin Wraith, Hex Witch and Nightmare. It implements spawn chances, configurable health/effects, per-entity ability cooldowns, effects for all eight types, drops/cleanup for event mobs, and a vanilla fallback when MythicMobs/ModelEngine cannot be used. The external model path remains conditional on those plugins and their configuration.
- Blood Moon damage scaling is implemented in `HalloweenMobManager.applyBloodMoonDamageMultiplier`, using the configured `random-events.blood-moon-damage-multiplier` (default 3.0) and bounded to 1.0–5.0. The full gameplay multiplier still requires runtime regression testing to prove it is applied once for every supported damage source.
- The Vampire arena has a read-only preflight and explicit `confirm` step; it requires the chosen region to be clear and relevant chunks to be loaded. It has no automatic terrain-clearing fallback. The normal Vampire King encounter deliberately remains gated by final progress, arena, integration and model readiness.
- ItemsAdder supplies an armor atlas, equipment entries, icons/models for Crimson Warden gear and custom block/environment assets. Java passive effects and combat procs exist, but the equipped appearance and final attributes have not been confirmed in a live client.
- Random villages/graveyards are handled by a chunk-load queue that avoids forcing distant chunks and has terrain/footprint checks. This is runtime decoration/building as chunks are explored; it does not retrofit vanilla structure-start data into already generated chunks.

### Gaps and risks discovered

1. **Goblin King is absent as a dedicated boss implementation.** No Goblin King manager, MythicMobs boss definition/model, separate goblin arena builder, boss-specific config or goblin admin workflow was found in the current tree. Do not alter or rename the Vampire King to fill this gap.
2. **Headless Nick is absent as a dedicated encounter.** No Nick stalker/target state, private Czech messages, rider/horse lifecycle, configuration or admin spawn/target/despawn commands were found.
3. **Armor attribute configuration and displayed lore disagree.** The desired/lore values are helmet 6, chestplate 12, leggings 9, boots 6 armor points, but `itemsadder/contents/warriorland_halloween/configs/items.yml` currently declares slot armor modifiers as 3, 4, 3 and 3 respectively. This must be reconciled against the actual ItemsAdder/Minecraft attribute behavior before calling Stage 2 complete. Requested durability values (900/1300/1150/950) are declared separately.
4. **Monster combat depth is incomplete against the new prompt.** The current manager has a notable ability/effect for each type, but its presence alone does not demonstrate multiple distinct attacks, telegraphs, counterplay and lifecycle tests for every mob. Stage 1 needs an explicit per-mob matrix and stronger verification.
5. **Model validation is structural, not visual.** It proves file/format/path/animation invariants only; it does not prove ModelEngine imported the assets correctly, that the models appear on the correct entities, or that every animation is visually appropriate in a live client.
6. **Custom-equipped armor is not yet release-verified.** Existing ItemsAdder armor-layer textures are present, but there is no verified recording/test result for the worn model, item attributes, relog/death/unequip cleanup or full-set bonus.
7. **Arena rollback/backup is not automatic.** The Vampire builder performs a clear-area check and requires confirmation; the operator must make a world backup before generation. The new Goblin arena must use independent bounds and the same or stronger safety bar.
8. **Runtime testing is externally blocked in this workspace.** No live Paper/Purpur server plus exact ItemsAdder, MythicMobs, ModelEngine and Iris/Sodium client was available to this audit. Do not describe visual or combat tests as passed until run on a staging server.

### Version and integration report

| Component | Project declaration / current boundary | Verification |
|---|---|---|
| Java | 21 | CI compilation passed |
| Server API | Paper API `1.21.10-R0.1-SNAPSHOT`; Paper/Purpur 1.21.10 documented | Compile passed; no live server |
| Client event companion | Fabric client mod, Minecraft 1.21.10 | Gradle build passed; no real-client event test |
| Shader | Standalone Iris/Sodium shader pack | GLSL compiler passed; live render not tested |
| ItemsAdder | Minimum `4.0.13` in core config | Asset structure generated/validated; live pack import not tested |
| ModelEngine/MythicMobs | Optional soft dependencies, needed for custom mob presentation | Definition/asset validators passed; external runtime import not tested |
| World protection | Arena preflight + explicit confirmation; world structure generator checks footprints | Static inspection only; no world-level destructive test |

### Baseline build/test report

| Check | Status | Evidence |
|---|---|---|
| Existing CI baseline | PASS | Run #1121 / `38216221425` |
| Maven plugin build | PASS | CI job step “Build” |
| Fabric client companion | PASS | CI step “Build Fabric client companion” |
| GLSL source compile | PASS | CI step “Compile Iris shader sources” |
| Special mob model/assets validator | PASS | CI steps “Generate additional Halloween mob models” and “Validate custom Halloween mob models” |
| ItemsAdder/gear/environment asset validator | PASS | CI step “Validate Halloween assets” |
| Package archives | PASS | all five artifacts uploaded by the run |
| In-game combat, player lifecycle, equipped armor appearance | NOT TESTED | Requires staging server/client |
| ModelEngine/MythicMobs imports on target versions | NOT TESTED | Requires target integrations installed |
| Arena creation/rollback under real protection rules | NOT TESTED | Requires disposable staging world |

### Prioritized continuation checklist

- [x] Stage 0: inspect repository, target versions, current integrations/assets, baseline CI and missing requirements.
- [ ] Stage 1: audit/complete eight mobs; ensure two or more distinct, fair abilities per mob where supported; verify one-time Blood Moon scaling, cleanup and spawn/drop test matrix.
- [ ] Stage 2: reconcile armor modifiers/lore; verify real equipped appearance, durability, sword/tool attributes, ability cooldowns and set bonus without attribute stacking.
- [ ] Stage 3: create distinct Goblin King, custom supported model assets, phased fight, safe dedicated fortress arena with dry-run/confirm/cancel and clear bounds.
- [ ] Stage 4: create visibly headless Nick with retained target, private “Vidím tě.” / “Sleduji tě.” messages, fair stalking/ambush, admin controls and full rider/horse cleanup.
- [ ] Stage 5: finish asset-reference and distribution audit; separate generated structures from decorative placement; state shader/client requirements accurately.
- [ ] Stage 6: integrate commands/config/reload/performance and run regressions.
- [ ] Stage 7: build, validate packages, provide installation/rollback guide and explicit PASS/FAIL/BLOCKED/NOT TESTED handover.

## Stage 0 release gate

**PASS:** the current tree, declared versions, existing systems, green automated baseline and concrete gaps are documented before feature changes. No game content was destructively changed during this audit.

**Next:** continue with Stage 1, then Stage 2; the audited armor attribute mismatch should be corrected before any Stage 2 release claim. Keep all work on the feature branch and keep PR #51 unmerged until later release gates are satisfied.
