# HalloweenCore / ItemsAdder assets

The Java core is intentionally kept independent from the ItemsAdder compile-time API.

Place this content under:
plugins/ItemsAdder/contents/warriorland_halloween/

The current ItemsAdder layout expects item textures under:
plugins/ItemsAdder/contents/warriorland_halloween/textures/item/

The seasonal sky shader lives under the content pack resourcepack folder and is merged into the generated pack.

After adding or changing the content, run /iazip and make sure the generated resource pack is delivered to clients.

The Koto red-sky artwork is assembled during the reproducible build; the build artifact includes the generated Java custom-sky atlas. Vanilla clients keep the core-shader fallback, while OptiFine or a compatible custom-sky loader reads the skybox files. See SKY.md and ../../third-party-assets/koto-sky/NOTICE.md for compatibility and attribution.\n\nSee configs/items.yml, ASSETS.md and SKY.md for the asset definitions.