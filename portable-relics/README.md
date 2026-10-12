# HalloweenCore Portable Relics — v1.0.0

This package provides two custom items under the independent ItemsAdder namespace `halloween_relics`:

- **Batwing Elytra**: custom bat-wing equipment texture, 768 durability, normal gliding and firework boosts, reduced wear, Midnight Glide, Nightwing Surge and Bat Echo.
- **Nocturne Sovereign Cowl**: 3D head-slot model and icon, Nocturnal Perception, bounded sensing for tagged Halloween mobs, Curseguard for debuffs routed through HalloweenCore, and Veilstep.

## Supported target

- Paper/Purpur 1.21.10, Java 21
- ItemsAdder **4.0.18 or newer** (required for custom Elytra visuals)
- Client Minecraft 1.21.2 or newer, using the generated ItemsAdder resource pack

Compilation and automated structural validation are CI-testable, but this source task has **not** been verified on a live Minecraft server/client. In-game visuals, pack delivery and feel/timing of flight abilities must be recorded as NOT TESTED until executed on the target stack.

## Installation

1. Back up the server and its ItemsAdder content folder/resource-pack source.
2. Copy `contents/halloween_relics/` into `plugins/ItemsAdder/contents/halloween_relics/`. Do not merge it over another namespace.
3. Run `/iazip` and the server's usual resource-pack hosting workflow, then ensure clients receive and accept the updated merged pack.
4. Copy `plugin/HalloweenPortableRelics.jar` to `plugins/` and restart the server.
5. Review `config.yml` (inside the plugin data folder after the first start) and test in a backup world.

## Controls and effects

- Wear Batwing Elytra and glide: **F** activates Nightwing Surge; **Shift+F** activates Bat Echo. Only the relic ability gesture cancels the offhand swap.
- Wear the Cowl, not gliding in Batwing Elytra: **Shift+F** activates Veilstep.
- Midnight Glide is a small capped speed nudge on a two-second timer; ItemsAdder reduces Elytra durability wear and preserves regular fireworks/Mending.
- Cowl grants short refreshed Night Vision and brief signals for a capped number of Halloween-tagged mobs within its radius.
- Curseguard applies only to debuffs that HalloweenCore explicitly routes through the shared engine; it does not cancel vanilla, environment or other plugins' effects.
- Cooldowns, ranges and caps are configurable. The engine clears its single passive task on plugin disable and clears player cooldown state on logout, death and world change.

## Asset inventory

- `contents/halloween_relics/configs/items.yml`
- `contents/halloween_relics/textures/item/batwing_elytra.png` and `batwing_elytra_broken.png`
- `contents/halloween_relics/textures/entity/equipment/wings/batwing_elytra.png`
- `contents/halloween_relics/textures/item/nocturne_sovereign_cowl.png`
- `contents/halloween_relics/models/armor/nocturne_sovereign_cowl_3d.json`
- `source/nocturne_sovereign_cowl.bbmodel`
- `plugin/HalloweenPortableRelics.jar` and `config.yml`

The model/texture sources included in the repository are editable. No third-party JARs are redistributed. The namespace is independent from the WarriorLand namespace, and the pack does not replace vanilla core shader files.

## Clean-install test checklist

1. Confirm plugin startup and both item IDs in ItemsAdder.
2. Run `/iazip`, accept the new resource pack and inspect the Elytra item, broken icon, worn wings, and Cowl inventory/on-player view.
3. Equip the Elytra; confirm standard glide and firework use, then check durability wear and Mending.
4. While gliding, tap F once and observe a single Surge; use Shift+F once for Echo; repeat inside cooldown.
5. Equip the Cowl and use Shift+F to confirm a single Veilstep and cooldown.
6. With HalloweenCore mobs in range, verify Cowl sense only marks tagged mobs.
7. Inspect logs; repeat after death, reconnect, world change, reload/restart, and item slot movement.
8. Add the namespace beside a copy of an existing pack and confirm no unrelated namespace is overwritten.

## Uninstall

1. Stop the server and remove `plugins/HalloweenPortableRelics.jar`.
2. Optionally preserve or remove `plugins/HalloweenPortableRelics/` after saving local config changes.
3. Remove only `plugins/ItemsAdder/contents/halloween_relics/` if nothing else uses it.
4. Run `/iazip` and refresh/redistribute the merged resource pack. Do not delete the entire server-wide pack or other namespaces.
