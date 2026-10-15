# Uploaded Halloween mod asset audit

Audit scope: the nine JAR filenames supplied in this task. Direct asset imports are deliberately narrower than technical inspection: a file being inside a mod JAR does not grant redistribution rights.

## Import decision

| Mod JAR | Extracted in this workspace | License / provenance status | Decision |
|---|---:|---|---|
| `stevs_halloween_mod-1.0.0.jar` | Yes; 336 archive entries, 38 PNGs, 27 model JSON files | MIT in `META-INF/neoforge.mods.toml`; author Stev51; JAR SHA-256 recorded in `third-party-assets/stevs-halloween/asset-source-manifest.json` | **Imported 13 original 16×16 candy/item icons**, unchanged; hashes and attribution are CI-checked. No mod classes or Java gameplay code were imported. |
| `allhallows-fabric-1.3.1_1.21.1.jar` | Current mounted file is 0 bytes, so archive validation fails | Prior metadata/source information is inconsistent about reuse terms; needs a fresh valid JAR and explicit licence review | Not imported. |
| `Luminous Halloween V1.2 - NeoForge 1.21.1.jar` | Current mounted file is 0 bytes, so archive validation fails | Reported as All Rights Reserved; not cleared for redistribution | Not imported. |
| `hollow-steve-1.21.10-neoforge-4.1.0.jar` | Current mounted file is 0 bytes, so archive validation fails | Reported as restrictive; not cleared for redistribution | Not imported. |
| `spooky_mobs-1.0.3-neoforge-1.21.1.jar` | Yes; 89 archive entries, 9 PNGs, 13 model/animation JSON files | Both legacy and NeoForge mod metadata say license “Not specified” | Not imported; obtain a clear licence or permission from the asset owner first. |
| `pumpkillagersquest-1.21.10-4.8.jar` | Yes; 200 archive entries, no `assets/` tree | Mod metadata says All rights reserved | No art files to reuse from this JAR; gameplay is reference-only. |
| `halloween_mood-neoforge-1.21-1.0.14.jar` | Yes; 176 archive entries, 26 PNGs, 7 model JSON files | JAR says CC0-1.0 but the project page says GPLv3; conflicting signals | Not imported until the author/source licence discrepancy is resolved. |
| `ManyIdeasHalloween-1.21.2-2.0.2.jar` | Yes; 210 archive entries, 25 PNGs, 56 model JSON files | Mod metadata says All Rights Reserved | Not imported; use only for technical study/reference unless permission is obtained. |
| `ravs_halloween-1.0.0-neoforge-1.21.1.jar` | Yes; 202 archive entries, 37 PNGs, 26 model JSON files | Mod metadata says Not specified | Not imported; obtain a clear licence or permission first. |

**File integrity note:** the current mounted copies of Allhallows, Luminous Halloween, and Hollow Steve are zero-byte files. Any earlier thumbnail/preview is not a substitute for the original JAR, so these three archives must be uploaded again before a repeatable binary inventory or texture extraction can be done.

## What is already reused

The 13 redistributed icons live under `itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/textures/item/stev_candy/`:

- apple_core, candy_apple, candy_bag, caramel, caramel_apple
- chocolate_bar, frosted_sugar_cookie, gumdrop, hard_candy, lollipop
- peanut_butter_cup, sugar_cookie, toasted_pumpkin_seeds

The files are preserved unchanged at 16×16. `third-party-assets/stevs-halloween/LICENSE`, `README.md` and `asset-source-manifest.json` carry the licence notice, source JAR fingerprint, provenance and SHA-256 for each imported file. The canonical content registry and ItemsAdder config register a matching item for each icon.

## Porting constraints

Minecraft mods targeting Fabric/NeoForge are not Paper plugins. Resource assets may be portable after licence and format checks, but Java/Kotlin mod classes, mixins, block/entity registration and GeckoLib/loader-specific runtime are not directly loadable by HalloweenCore. Model JSON can require UV, geometry, animation and renderer conversion to ModelEngine/Blockbench formats. Flat item sprites never count as 3D world decoration.

No direct assets from Allhallows, Luminous Halloween, Hollow Steve, Spooky Mobs, Pumpkillager's Quest, Halloween Mood, ManyIdeas or Rav's Halloween are included by this change.
