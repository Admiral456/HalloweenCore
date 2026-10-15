# HalloweenCore — mandatory staging and release sign-off

**A green CI build is not a release approval.** The release owner must complete this checklist on a staging server running the exact target Minecraft version, record tester/date/server version/resource-pack SHA, and attach proof of actual client rendering before enabling the update for all players.

Release record:
- Release/commit:
- Target Minecraft version (currently verified: 1.21.10 only):
- Paper build:
- ItemsAdder version:
- ModelEngine version:
- MythicMobs version:
- Tester and date:
- Resource-pack URL:
- Resource-pack SHA-256:

## 1. Build and asset integrity
- [ ] Java compile and existing runtime-rule checks passed.
- [ ] Canonical content registry validator passed; ItemsAdder IDs match the registry.
- [ ] All generated and imported item icons are registered; no orphan/unused item textures remain.
- [ ] Model blueprints, model PNG atlases, armor layers, decoration textures and OGGs match the central inventory.
- [ ] The manifest records SHA-256 values and the archive is generated reproducibly for the exact target profile.
- [ ] Third-party attributions/licences are included in the delivered ItemsAdder archive.
- [ ] Void Reaper / Ženec prázdnoty, Frost Stalker / Ledový stopař, and Nightmare / Noční můra were not overwritten, regenerated or recolored.

## 2. Install and generate the pack
- [ ] Back up world data plus plugins/HalloweenCore/, plugins/ItemsAdder/, plugins/MythicMobs/ and plugins/ModelEngine/.
- [ ] Install the matching HalloweenCore JAR and warriorland_halloween ItemsAdder content.
- [ ] Confirm startup has no YAML, duplicate-ID, missing-model, or ItemsAdder parsing errors.
- [ ] Run /meg reload models (or the installed version's equivalent) and confirm each expected blueprint loads.
- [ ] Run /mm reload and verify the Halloween special mob definitions.
- [ ] Ensure ItemsAdder's merged resource-pack folders include ModelEngine/resource pack; preserve all existing entries.
- [ ] Run /iazip only after ModelEngine produced its pack.
- [ ] Run /iainfo; confirm a reachable resource-pack URL and a new pack hash.
- [ ] Reconnect a clean test client, accept the newest resource pack, and confirm the client has loaded it.

## 3. Visual proof on a real client
- [ ] Open an inventory and check each imported Stev candy icon at normal GUI scale.
- [ ] Equip every Crimson Warden armor slot and confirm the custom worn texture appears on the player model.
- [ ] Test Batwing Elytra while gliding; its equipped wings must be checked separately from the inventory icon.
- [ ] Wear Nocturne Sovereign Cowl and inspect the third-person appearance and first-person view.
- [ ] Spawn Blood Spider, Cursed Zombie, Gravekeeper, Hex Witch and Pumpkin Wraith; verify real 3D model, texture and animation (vanilla fallback is a failure).
- [ ] Keep Vampire King disabled for production until model import, UV mapping and rendering are signed off separately.
- [ ] Inspect generated terrain/decorations at day/night; verify no protected builds, chests or claimed regions are replaced.
- [ ] Play the ambient loop and each event sting; check music suppression after pack acceptance.
- [ ] Run an event start/stop cycle and confirm cleanup of boss bars, minions, particles and temporary blocks.
- [ ] Confirm /halloween disable and /halloween enable persist after relog/restart and respect personal effects/rewards.

## 4. Resource-pack delivery and release decision
- [ ] Verify pack URL works from a client network, not just localhost/console.
- [ ] Check logs for rejected downloads, hash mismatch, cache issues and missing-texture/model warnings.
- [ ] Attach screenshots of worn armor, both relics, imported candy icons and all five production mob models.
- [ ] Every section is marked complete or has a linked, explicitly accepted exception before release.

**Release decision:** PASS / BLOCKED  
**Approver:**  
**Date:**  
**Known exceptions and evidence links:**  
