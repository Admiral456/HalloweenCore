# HalloweenCore / ItemsAdder assets

The Java core is intentionally kept independent from the ItemsAdder compile-time API.

Place this content under:
plugins/ItemsAdder/contents/warriorland_halloween/

The current ItemsAdder layout expects item textures under:
plugins/ItemsAdder/contents/warriorland_halloween/textures/item/

The seasonal sky shader lives under the content pack resourcepack folder and is merged into the generated pack.

After adding or changing the content, run /iazip and make sure the generated resource pack is delivered to clients.

See configs/items.yml, ASSETS.md and SKY.md for the asset definitions.

## Portable Relics (independent namespace)

The separate `HalloweenCore-Portable-Relics.zip` contains `contents/halloween_relics/` and `HalloweenPortableRelics.jar`. It is designed for Paper/Purpur 1.21.10 with Java 21 and ItemsAdder 4.0.18+. Copy its content folder to `plugins/ItemsAdder/contents/halloween_relics/`, generate the merged pack with `/iazip`, and then restart with the standalone relic JAR installed. Do not merge this namespace into `warriorland_halloween` or replace another namespace. See the archive README and MANIFEST for the exact inventory, controls, requirements, test checklist and uninstall path. Live game/client acceptance is not claimed by CI.
