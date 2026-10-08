# HalloweenCore MythicMobs pack

## Files

- `mobs/vampire-king.yml` — dormantní základní mob definition
- `skills/vampire-king.yml` — vyhrazený skill namespace pro budoucí rozšíření

## Important

HalloweenCore rozhoduje, kdy a kde se finální boss může objevit. Proto tento pack neobsahuje vlastní spawner.

The MythicMobs boss bar is disabled in the mob definition. HalloweenCore owns the visible boss bar so HP, phases, participation and cleanup stay synchronized.

The 3D model is not activated here yet. The final model is a separate ModelEngine asset and must satisfy the locked 10-block height / 8-block wing-span requirement before `bosses.vampire.model.ready` is changed to `true`.

## Installation target

Copy the contents into the server's MythicMobs content directory and reload MythicMobs after the files are in place.

Before production activation, verify:

1. `/mm m spawn vampire-king` works in a test world.
2. The base entity has the correct HP/damage.
3. The ModelEngine model attaches correctly.
4. The model remains at least 10 blocks tall and 8 blocks wide including wings.
5. `/halloween boss status` reports the expected dependencies.
6. Only then enable the HalloweenCore boss configuration.
