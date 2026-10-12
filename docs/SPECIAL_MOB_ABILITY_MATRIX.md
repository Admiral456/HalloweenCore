# Special Halloween mob — ability and manual test matrix

Status legend: **IMPLEMENTED** means the current feature branch contains the corresponding code/config/model references. **BUILD PENDING** means the current branch revision still needs CI confirmation. **NOT TESTED** means no live Paper/Purpur + MythicMobs/ModelEngine staging server/client was available; it is not a successful in-game test.

## Shared behavior

- Natural special-mob chance, phase bonus, event chance multiplier, health/speed/strength, ability cooldowns and the secondary-ability chance are configurable in `config.yml`.
- The secondary ability chance defaults to 0.5. Each mob's shared special-ability cooldown prevents the primary/secondary variants from firing continuously.
- Delayed ground strikes display particles/sound and wait before impact. They check that the target is alive, online, eligible, in the original world, and that the marked chunk is already loaded. They never force-load a chunk. The player only takes strike damage if still inside the marked radius.
- Pumpkin Wraith explosions clear the block list and set yield to zero; its alternate ember ring applies status/knockback/fire but does not edit blocks.
- Admin test commands: `/halloween mob spawn <id> [player]`, `/halloween mob status`, `/halloween mob cleanup`. A test-spawn mob is tagged for cleanup and its death does not award fragments, relics or progression.
- Event spawn pool now lists all eight IDs. If MythicMobs/ModelEngine or a requested external definition is unavailable, the plugin uses its existing vanilla-entity fallback. That fallback does not prove the intended 3D model is loaded.

## Per-mob matrix

| Mob ID | Existing ability | Added secondary variant | Intended counterplay / restriction | Code-level status |
|---|---|---|---|---|
| `cursed-zombie` | Soul Siphon: Darkness + Weakness | Frenzy: temporary Speed/Strength, with sound/particles and warning message | Frenzy is time-limited; cooldown limits repeat pressure | IMPLEMENTED; runtime NOT TESTED |
| `void-reaper` | Reaper Gloom: Darkness + Weakness | Void Rift: marked ground area with delayed damage/Slowness | Move out of a 2.2-block marked radius before the 22-tick impact | IMPLEMENTED; runtime NOT TESTED |
| `gravekeeper` | Grave Mark: delayed ground mark | Ground Pulse: separate marked area and delayed hit | Escape the marked radius before the 20-tick impact | IMPLEMENTED; runtime NOT TESTED |
| `frost-stalker` | Frostbite: Slowness + Weakness from its projectile hit | Ice Lance: delayed snowflake-marked area | Move away before the 18-tick impact; short Slowness applies only if hit | IMPLEMENTED; runtime NOT TESTED |
| `blood-spider` | Blood Web: Poison + Slowness | Blood Drain: heals the spider within its health cap and poisons the target | Distinct heal/poison move is bounded by cooldown and configurable heal/duration | IMPLEMENTED; runtime NOT TESTED |
| `pumpkin-wraith` | Vanilla creeper explosion with safe no-block-damage handling plus Ash Flash | Ember Ring: short fire, Slowness and radial knockback instead of the Ash Flash status package | Explosion radius/configuration remains bounded; no block griefing | IMPLEMENTED; runtime NOT TESTED |
| `hex-witch` | Potion splash with randomized Darkness/Slowness/Weakness | Hex Mark: delayed marked area with damage/Slowness | Step out of the 2.25-block area before the 20-tick impact | IMPLEMENTED; runtime NOT TESTED |
| `nightmare` | Night Terror: Darkness + Nausea | Spectral Slam: bounded knockback + temporary Weakness | Short range; effects and push strength are configurable | IMPLEMENTED; runtime NOT TESTED |

## Automated/manual checks

| Check | Expected result | Status |
|---|---|---|
| Compile plugin against declared Paper 1.21.10 API | No compile errors | BUILD PENDING after latest code changes |
| Asset validator for all eight model IDs, embedded textures and animations | All model references resolve structurally | Previous baseline PASS; rerun required after latest branch changes |
| Spawn each ID with `/halloween mob spawn <id> [player]` | Correct special type/name, safe nearby spawn, model if integrations are installed | NOT TESTED in a live server |
| Trigger primary and secondary attack for each mob at least twice | Variants visibly differ; cooldown respected | NOT TESTED in a live server |
| Escape each marked area before/after impact | No area damage if outside radius; hit if inside | NOT TESTED in a live server |
| Kill an admin test spawn | No fragment/relic/progression reward | Code path implemented; NOT TESTED in a live server |
| `/halloween mob cleanup` | Removes only tagged event/test entities from loaded worlds | Code path implemented; NOT TESTED in a live server |
| Start/stop Blood Moon and compare ordinary hostile attacks | Configured multiplier is applied exactly once, including projectile and delayed attack paths | NOT TESTED in a live server |
| Repeat plugin reload and shutdown with pending marked strikes | Pending plugin tasks stop; no console spam or orphaned entities | Scheduler ownership reviewed; NOT TESTED in a live server |

## Stage 1 gate

**Status: IN PROGRESS / BLOCKED for release sign-off.** Secondary attack branches, configurable tuning, complete event-pool mapping, no-reward admin test spawns, and a manual test matrix are now represented in code/docs. Do not mark Stage 1 fully complete until CI passes at the new branch head and the runtime checks above are performed on a staging server.
