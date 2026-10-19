# HalloweenCore release verification checklist

Run these checks on a **staging Paper/Purpur server**, not directly on the production world. The CI build and source smoke checks do not replace a live-server test.

## Before testing

- [ ] Back up the world folders, plugin folder, plugins/HalloweenCore/config.yml, and plugins/HalloweenCore/data.yml.
- [ ] Use Java 21 and a server/API version compatible with the build (Paper 1.21.10).
- [ ] Install the built JAR. If testing custom candy textures, install the generated ItemsAdder content ZIP under the ItemsAdder contents directory and regenerate the resource pack using the procedure for the installed ItemsAdder version.
- [ ] Start the server and check the console for startup errors. ItemsAdder and PlaceholderAPI are optional integrations; MythicMobs, ModelEngine, BattlePass, and WorldGuard must not be required by this vanilla-first build.

## 1. Cursed candy and reward shop

- [ ] Run /halloween debug and verify the plugin and retained optional integrations are reported correctly.
- [ ] Run /halloween rewards. Verify that **Prokleté cukroví** is listed for 500 fragments and can be claimed only once per player.
- [ ] Try claiming it with insufficient fragments and with a full inventory. The balance must not be charged and the item must not be lost.
- [ ] Claim it with at least 500 fragments. Verify the balance is reduced by exactly 500, the candy is delivered, and a second claim is rejected.
- [ ] Eat the candy. Verify Speed for about 20 seconds, Night Vision for about 45 seconds, and Glowing for about 12 seconds.
- [ ] Repeat on a staging copy without ItemsAdder. The vanilla pumpkin-pie fallback should still trigger the same effects.
- [ ] Test config migration on a copy of an older server config that does not contain rewards.shop.cursed-candy. It should be added once. After migration, deleting the reward intentionally must not cause it to be re-added at the next reload/restart.

## 2. Vanilla events and lifecycle

- [ ] Run /halloween event status and confirm status/remaining-time output is sensible when no event is active.
- [ ] Run /halloween event start pumpkin-harvest. Confirm a clear announcement/title and a larger fragment reward for mature farm crops; then run /halloween event stop.
- [ ] Run /halloween event start witching-hour. Confirm the vanilla witch sound/announcement and the configured mob-kill fragment multiplier; then stop it.
- [ ] Run /halloween event start blood-moon. On a test player, compare incoming damage from a hostile mob against a control hit under the same conditions; confirm the configured multiplier is applied. Test a hostile projectile as well; then stop it.
- [ ] Run /halloween off. Confirm fragment progression and new events pause. Run /halloween on and confirm a fresh event schedule starts without duplicate timers.
- [ ] Run /halloween reload several times and verify there are no duplicate event announcements/tickers or errors.

- [ ] Leave vanilla-atmosphere enabled on a staging server and wait through one configured pulse interval (90–180 seconds by default). Verify occasional ash/soul particles and one of the built-in cave, witch, or phantom ambience sounds appear near eligible players without stopping normal Minecraft music.
- [ ] Confirm cues are not forced on players in excluded worlds or unsupported gamemodes. Test that sounds-enabled and particles-enabled can each be switched off independently.
- [ ] Run /halloween ambience status, then /halloween ambience test as an administrator while in an eligible world/gamemode. Confirm it previews the nearby effect immediately rather than waiting up to 180 seconds.
- [ ] Run /halloween ambience off and /halloween ambience on as an administrator. Status must reflect the configured/running state; toggling must persist across restart and remain separate from /halloween on|off.
- [ ] Run /halloween off, wait past a due pulse, and verify no new ambience cues occur. Run /halloween on and /halloween reload and confirm ambience resumes without duplicate timers.

## 3. Hidden discoveries and Easter eggs

- [ ] Confirm the config contains the three default IDs: blood-altar, witch-den, and forgotten-grave; all are unconfigured before an admin places them.
- [ ] In a staging world, stand at each desired location and run /halloween setsecret <id>. Confirm the world and location are saved in config.yml and the plugin reports the name of the configured secret.
- [ ] Run /halloween secrets as a normal player. It should show discovered count and clue text, never coordinates. Unconfigured locations should not count as discoverable locations.
- [ ] Visit a configured discovery with one player. Verify the one-time fragment reward, title, chime, and vanilla particles. Leave and re-enter the radius and confirm there is no second reward.
- [ ] Visit the same location with a second player. That player should receive their own one-time reward, independent of other players' discoveries.
- [ ] Restart after discovering a location. Verify the discovery stays marked in data.yml and cannot be farmed again.
- [ ] Test a copy of a config from the previous cleanup build with secret-discoveries disabled and no locations. The migration should restore the three definitions.
- [ ] Test a copy with one custom secret location and migrations.vanilla-secret-discoveries-v1 already set to true. Migration v2 should add any missing stock definitions while preserving the custom location's world, coordinates, name, radius, and reward.

## 4. Fragment and save safety

- [ ] Earn fragments by mob kills, mining, farming, and fishing. Confirm configured reward values, allowed gamemodes/worlds, and per-minute caps.
- [ ] Reach a mob-kill cap, disconnect/reconnect within the same minute, and verify reconnecting does not reset the cap.
- [ ] Grant fragments using the admin command. Confirm they increase the spendable balance but do not increase lifetime-earned fragments or curse level, including after a server restart.
- [ ] Earn ordinary fragments and complete/claim a daily challenge. Restart the server and verify balance, lifetime totals, curse level, challenge claim state, and claimed reward state are preserved.
- [ ] On a disposable test copy only, test malformed data.yml and unsupported schema versions. Startup must refuse to overwrite the original data and must report a backup.

## 5. Pumpkin villager and underground caches

- [ ] Run /halloween npc spawn <name> in a clear area. Verify the villager wears a carved-pumpkin helmet, remains persistent through restart, cannot be damaged, and does not open the vanilla trading interface while enabled.
- [ ] Right-click the NPC and test all clickable dialogue links: rewards, daily challenge, and server progress.
- [ ] Run /halloween npc remove and verify only a managed pumpkin villager nearby is removed.
- [ ] Run /halloween cache spawn, then /halloween cache key <player> 1. Verify an ordinary renamed Tripwire Hook does not unlock it, a real plugin key does, and the key is consumed only once.
- [ ] Confirm a locked cache cannot be broken, blown up, moved by pistons, or accessed by hoppers before unlocking. Confirm it can be opened normally after unlocking.
- [ ] On a new test world, explore newly generated Overworld chunks. Pumpkin patches (6%), cave cobwebs (4%), and ancient altars (2%) are intentionally sparse, and only generated for new chunks; existing chunks are not retroactively scanned. Do not use the absence of a structure in one chunk as a failed test.
- [ ] When an ancient altar appears, verify it consists of a few mossy/cracked stone blocks and a soul lantern above untouched natural terrain. Confirm it is only generated in a clear natural area and never overwrites blocks or tile entities.
- [ ] With underground-caches.enabled: false, verify an existing locked cache becomes a normal accessible chest on its next open instead of trapping its loot.

## 7. Vanilla story campaign

- [ ] Run /halloween quests on a fresh staging player. Confirm chapter 1 of 9 appears with an objective, progress, and fragment reward.
- [ ] Complete a chapter, restart the server, and confirm the next chapter and completion count persist without duplicate rewards.
- [ ] Verify mob-kill chapters count hostile vanilla creatures only; mining/farming/fishing chapters count only configured, eligible gameplay actions.
- [ ] Configure one hidden secret and complete the discovery chapter; confirm the chapter advances once, while unconfigured placeholder locations never advance it.
- [ ] Unlock a real test cache with a valid plugin key and confirm the cache chapter advances only after successful unlock; a failed key attempt must not advance it.
- [ ] Run /halloween quests reset <online player> as an admin and verify progress is cleared. Confirm a non-admin cannot reset anyone's campaign.
- [ ] Test an older config without migrations.vanilla-story-quests-v1. Startup should enable the vanilla campaign once; after an admin disables it and saves the config, a reload/restart must keep it disabled.

## 8. Release artifacts

- [ ] Confirm GitHub Actions passes the Maven build, smoke checks, ItemsAdder ZIP package test, and both artifact uploads.
- [ ] Inspect the ItemsAdder ZIP. It should contain only the cursed-candy content and the three retained Minecraft pumpkin block texture overrides. There should be no retired shader, OGG files, or old MythicMobs content.
- [ ] Do not merge or deploy until all staging checks pass and the production backup has been verified.
