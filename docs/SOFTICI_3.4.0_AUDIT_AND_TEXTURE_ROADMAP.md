# Halloween_Softici 3.4.0 — audit for HalloweenCore

## Source package verification

The two user-provided files, `halloween-plugin-3.4.0.jar` and `halloween-plugin-3.4.0 (1).jar`, have the same SHA-256:
`a6efe2cad8f983e44deb5ee7cc790b77559b9ecfaaa5e7500cea3a6644586357`.
They are duplicate copies of the same Java plugin. The JAR contains compiled classes and YAML configuration/messages, but no PNG textures, model files, or resource-pack assets to reuse directly.

## Candidate mechanics found in the JAR

- Per-player opt-out/opt-in commands (`/halloween disable|enable`) and persistent player preferences.
- Pumpkin Witch boss, with teleporting, minion spawning, and boss rewards.
- Pumpkin Zombie and Pumpkin Skeleton boss mechanics with per-player cooldowns.
- Pumpkin Villager/trader with configurable trades.
- Random pumpkin mobs and pumpkin protector.
- Herobrine and Slenderman scare tasks, plus darkness, fire, pumpkin, and lightning scare tasks.
- Grave encounters with waves, boss bars, reward chests, participant tracking, timeouts, and cooldowns.
- Candy items and consumption effects.
- Player statistics and leaderboards.
- Discord notifications for boss/grave/season/top events.
- Season scheduler and Witching Hour behavior.
- Optional Vault, PlaceholderAPI, and WorldGuard integrations.

## Existing HalloweenCore overlap

HalloweenCore already has its own season scheduler, Witching Hour, bat-to-witch transformation, Halloween fragment/curses progression, candy drops, special mobs and their attacks, six event types, graveyard ritual waves, quest/challenge progress, global milestones, and a custom ItemsAdder/ModelEngine pipeline. Those should be retained rather than replaced by duplicate implementations.

## Recommended integration order

1. Review and implement player opt-out as a persistent per-player preference without disabling the global event for everyone.
2. Port only distinct, configurable mechanics after checking compatibility: Pumpkin Witch-style boss/minions, trader exchanges, and optional scare encounters. Avoid copying the donor plugin's commands/config wholesale because both plugins use the `/halloween` namespace.
3. Add optional Discord announcements only if the existing server configuration can safely supply a webhook and rate limiting is in place.
4. Keep all effects bounded, respect protected worlds/regions and spectator/vanish filters, and ensure opt-out players are not targeted by personal scare effects.
5. Rebuild and run CI/static validation after each integration. A successful build must not be described as proof of live Paper/ItemsAdder/ModelEngine rendering.

## Texture/model follow-up

The source JAR contains no artwork to extract. Custom visuals must be created as new assets or taken from the existing HalloweenCore resource pack and then deliberately redesigned. Keep the three externally authored mob assets (Void Reaper, Frost Stalker, Nightmare) out of any automatic generation/recoloring pipeline.

Priority order for new visual work:
1. Validate/rework existing five MythicMobs/ModelEngine mob atlases and UV maps.
2. Finish the Vampire King model/UV/readiness gate; do not enable the encounter until the actual model has been tested.
3. Create separate Headless Nick rider and horse models/textures.
4. Redesign all Crimson Warden item icons and equipped armor layers, then relic/item icons.
5. Create 3D decorative assets for graves, mushrooms, lanterns and structure details; flat icons alone are not substitutes for world geometry.
6. Validate resource-pack paths, texture dimensions/transparency, model references and in-game appearance.

## Status

This file is an audit/plan only. It does not claim that candidate mechanics have already been ported or that textures have been created. The current working PR remains a draft until integration and server/client validation are complete.
