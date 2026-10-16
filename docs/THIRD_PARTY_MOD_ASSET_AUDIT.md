# Audit of Halloween mod JARs supplied for HalloweenCore

Audit date: 2026-10-15. The supplied JARs are Fabric/Forge/NeoForge mods, not Paper plugins. Their Java classes, entities and recipes do not run in HalloweenCore without a separate Paper implementation.

## Assets actually imported

### Stev's Halloween 1.0.0 — MIT
- Project: https://www.curseforge.com/minecraft/mc-mods/stevs-halloween
- License page: https://www.curseforge.com/minecraft/mc-mods/stevs-halloween/license
- Upstream source/license: https://github.com/Stev51/stevs-halloween-mod
- Author: Stev51. The supplied JAR declares MIT and the upstream project contains the matching MIT text.
- Imported 13 16×16 item PNGs: apple core, candy apple, candy bag, caramel, caramel apple, chocolate bar, frosted sugar cookie, gumdrop, hard candy, lollipop, peanut-butter cup, sugar cookie, and toasted pumpkin seeds.
- SHA-256 and source/target paths for all 15 imported target files (14 distinct upstream assets) are recorded in third-party-assets/stevs-halloween/asset-source-manifest.json; CI verifies the copied files still match the source JAR.
- ItemsAdder definitions use new `stev_*` IDs. The candies and apple-leaf block are in the admin test bundle `/halloween give <player> all`; place and break the leaf block on staging before release. This does not import the donor mod's Java behavior or recipes.
- MIT attribution and full permission notice are included in the delivered ItemsAdder content.

## New project-owned furniture asset

- Added three original ItemsAdder furniture props: Gothic Gravestone, Broken Gravestone and Graveyard Candle. Each has a separate 32×32 icon, custom 3D Java item model made from Minecraft's vanilla block textures, floor-only placement and a 1×1×1 hitbox.
- These are new project-owned designs, not copied from Allhallows, ManyIdeas Halloween or another supplied mod. They provide placeable cemetery décor; they do not by themselves enable automatic random graveyard placement in world generation.
- Their model paths are registered and structurally validated by CI. Visual placement, model transforms, hitboxes and break/drop behavior still require the staging-client check.

## Remaining supplied mods — not copied into public asset output

| Supplied mod | Observed asset/format | Disposition |
|---|---|---|
| Allhallows Fabric 1.3.1 | Gravestones, Pumpkling/plant textures, costumes and models. JAR metadata says CC0 while project page says All Rights Reserved. | Licence terms conflict. Request author clarification before copying files. |
| Luminous: Halloween 1.2 | Headsman, minions, gear; GeckoLib geometry and animation. | All Rights Reserved; don't redistribute without permission. GeckoLib geometry is not a ModelEngine blueprint. |
| Hollow Steve 4.1.0 | Horror entity model/texture/animation. | All Rights Reserved; don't redistribute without permission. |
| Spooky Mobs 1.0.3 | GeckoLib models, atlases and animations. | No clear redistribution licence in the supplied JAR; permission needed. |
| Pumpkillager's Quest 4.8 | No conventional assets texture/model directory in this archive. | No texture files available to import; JAR licence is All Rights Reserved. |
| Halloween Mood 1.0.14 | Candy, foliage and particle-related textures. | JAR includes CC0 licensing information, but project-level distribution terms/version provenance should be reconciled before copying from this release. Not copied. |
| ManyIdeas Halloween 2.0.2 | Many blockstates and JSON models for scarecrows, graves and decorations. | All Rights Reserved; don't redistribute without permission. JSON models are useful as implementation references only. |
| Rav's Halloween 1.0.0 | Item/block textures and GeckoLib models/animations. | No clear redistribution licence in the supplied JAR; permission needed. |
| Halloween_Softici 3.4.0 | Compiled plugin classes and YAML, no embedded textures/models. | No art to extract. Distinct mechanics are audited separately; avoid duplicating HalloweenCore systems. |

## Compatibility boundaries
- Direct item PNG registration through ItemsAdder is feasible. The 13 imported icons are verified 16×16 PNGs.
- Mob texture atlases cannot be assumed compatible with different UV layouts. Existing ModelEngine models must be tested with their current atlases/animations.
- GeckoLib geometry/animation JSON cannot be dropped directly into ModelEngine; explicit conversion or a new model with verified UVs is required.
- ItemsAdder vanilla block overrides affect matching vanilla blocks globally for clients accepting the pack. Verify this scope on staging.
- Donor mods include Minecraft 1.21.1 and 1.21.2 builds, but HalloweenCore's verified output profile remains Minecraft 1.21.10 until its own Paper/ItemsAdder/ModelEngine/client tests pass.
- External-author assets Void Reaper / Ženec prázdnoty, Frost Stalker / Ledový stopař and Nightmare / Noční můra are protected from generation, recoloring and replacement.
