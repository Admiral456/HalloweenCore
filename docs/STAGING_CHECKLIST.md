# HalloweenCore staging and release checklist

This is a required gate before merging a resource-pack or content-registry change into `main`, and again before installing it on the production server. CI success is necessary but does not replace a real Minecraft-client test.

## 1. Build provenance

- [ ] Record release candidate commit SHA and pull request.
- [ ] Confirm target Minecraft version is exactly `1.21.10`; do not reuse a resource pack built for another minor version without a compatibility review.
- [ ] Record Java runtime, Paper/Purpur build, ItemsAdder version, MythicMobs version and ModelEngine version.
- [ ] Confirm GitHub Actions passed all generated-texture, registry, model, runtime-rule, asset-manifest, test, Maven and ZIP validation steps.
- [ ] Save the SHA-256 of `ASSET_MANIFEST.json`, plugin JAR and each packaged ZIP in the staging record.
- [ ] Verify the same commit produces identical ItemsAdder and ModelEngine ZIP SHA-256 values on a second build.

## 2. Install to a disposable staging server

- [ ] Take a backup of the staging world, `plugins/HalloweenCore/`, ItemsAdder contents and existing resource-pack hosting config.
- [ ] Install the candidate plugin JAR and only the artifacts produced by the same workflow run.
- [ ] Ensure ItemsAdder, MythicMobs, ModelEngine and any configured integrations match the recorded versions.
- [ ] Copy the ItemsAdder content directory, run `/iazip`, then check `/iainfo` for generated/hosted pack status.
- [ ] Confirm the pack host returns the expected ZIP and that a fresh client receives and accepts it.
- [ ] Inspect client logs for missing textures, invalid item models, unsupported shader syntax, sound registration failures and rejected pack downloads.
- [ ] Confirm server startup/reload logs contain no registry ID, ItemsAdder lookup, model import or YAML errors.

## 3. Canonical item registry and texture review

- [ ] Every logical item ID resolves to one ItemsAdder ID, one runtime identity, one fallback material and one texture key in `content-registry.yml`.
- [ ] `/halloween give <hráč> all` gives the Crimson Warden gear, existing relics, both new relics and all thirteen imported test candies plus the apple-leaf decoration block.
- [ ] Compare every inventory icon against the asset manifest. Confirm there are no missing or unregistered item textures.
- [ ] Fetch `/iaget warriorland_halloween:decoration_grave_marker`, place the Gothic Gravestone on grass/stone, inspect its 3D shape and rotation, then break it and confirm it drops exactly one item with no invisible leftover entity.
- [ ] Obtain `warriorland_halloween:stev_apple_leaves` via `/halloween give <hráč> all`, place and break it, and inspect leaf transparency/model and drops on a client with the pack accepted.
- [ ] Equip the Crimson Warden helmet/chestplate/leggings/boots and inspect the actual player model from front/back/side; an inventory icon is not evidence that worn armor renders.
- [ ] Wear the Batwing Elytra and check the actual wing model/texture while gliding. Confirm the dash cooldown and player-eligibility filters work.
- [ ] Wear the Nocturne Sovereign Cowl; check night vision, hostile-mob pulse, cooldown and opt-out behavior.
- [ ] Eat each imported candy; confirm expected nutrition, stack limits, inventory behavior and no unexpected custom potion effects.
- [ ] Verify each imported icon remains 16x16 and its SHA-256 matches `third-party-assets/stevs-halloween/asset-source-manifest.json`.

## 4. World, entities and safety review

- [ ] Preview each required mob model and test idle, walk, attack and applicable fly animations using the project's staging commands.
- [ ] Confirm model import, external texture atlas, UV mapping, hitbox, passenger mounts and entity death cleanup in the client.
- [ ] Test graves/structures on a disposable world with replace/skip policies, region protection and chunk boundaries.
- [ ] Confirm creative/spectator/vanished/opted-out players are filtered correctly where required.
- [ ] Test event start/stop, season transitions, restart persistence, rewards, cooldowns, WorldGuard restrictions and shutdown cleanup.
- [ ] Test with ItemsAdder absent to verify intended vanilla fallbacks and without ModelEngine to verify documented mob fallbacks.
- [ ] Check logs after at least one restart and a full event cycle.

## 5. Release record

Fill this out for every candidate:

| Field | Value |
|---|---|
| Test date/time and tester | |
| Commit SHA / PR | |
| Minecraft client version | |
| Paper/Purpur build + Java | |
| ItemsAdder version | |
| MythicMobs version | |
| ModelEngine version | |
| Resource-pack URL / delivery status | |
| Asset-manifest SHA-256 | |
| Plugin JAR / pack ZIP SHA-256 | |
| Result (PASS / FAIL / BLOCKED) | |
| Known issues and log excerpts | |
| Rollback artifact and procedure | |

## 6. Rollback

- [ ] Keep the last known-good plugin JAR and matching resource-pack ZIP.
- [ ] If textures fail, restore the previous hosted pack and its URL/hash, then prompt clients to reload/reconnect.
- [ ] If gameplay fails, stop the event/plugin cleanly, restore the previous JAR/config backup and restart staging before attempting production.
- [ ] Do not declare a release ready while any critical client-rendering or resource-pack-delivery checkbox is unchecked.
