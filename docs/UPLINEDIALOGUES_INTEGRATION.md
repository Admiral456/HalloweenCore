# UplineDialogues integration

Use UplineDialogues as the dialogue editor and HalloweenCore as the persistent quest state holder. UplineDialogues is a separate plugin; HalloweenCore does not bundle or copy its code.

## Setup

1. Install UplineDialogues on the server and restart. The SpigotMC resource page for version 3.0.0 describes its local editor and command actions: https://www.spigotmc.org/resources/uplinedialogues.112285/
2. Run `/updialog panel` and open the local editor URL printed by the plugin. UplineDialogues 3.x documents an in-plugin flowchart editor, templates, response branches, conditions, command actions and inventory actions; see https://www.spigotmc.org/resources/uplinedialogues.112285/.
3. Attach a dialogue to the appropriate existing villager/NPC entity.
4. Build response choices and branch each option to the next dialogue node in the editor.
5. Where a branch should start a quest, add a command action run as the player: halloween quest start <quest-id>.
6. Where a branch verifies that an item was provided, first configure the UplineDialogues condition and inventory action. Only on the successful branch add the command action halloween quest progress <quest-id> 1 and then halloween quest complete <quest-id>. Set these state-changing actions to run as OP; normal players cannot directly alter quest progress or completion.
7. Do not set a fragment amount or grant an item/key until that reward is explicitly agreed. A quest with no reward configuration pays no fragments and gives no items.

## Quest commands

- /halloween quest start <id> starts an unfinished quest for the command's player.
- /halloween quest progress <id> <amount> advances an active quest; admin/OP action only.
- /halloween quest complete <id> marks an active quest completed once its configured target is met; admin/OP action only.
- /halloween quest status [id] shows progress and reward status.
- /halloween quest claim <id> claims a configured reward once if the quest was completed before a reward was configured.
- /halloween fragments shows the player's accumulated fragments.

IDs may contain lowercase letters, numbers, dashes and underscores, up to 48 characters. If no definition exists, the title is based on the ID, target defaults to 1 and reward-fragments defaults to 0.

To define a title, target or reward amount, add a definition under quests.definitions in config.yml. Leave the section empty while the quest design is still being agreed. Optional fields are title, target, reward-fragments and reward-items. reward-items accepts configured item IDs and amounts, but do not populate it until the exact items and amounts are agreed. A configured reward is paid when the quest is completed and is protected against duplicate payment. If a reward is configured only after the quest was completed, the player can claim it once with /halloween quest claim <id>.

## Placeholders (PlaceholderAPI optional)

- %halloween_fragments%
- %halloween_quest_<id>_started%
- %halloween_quest_<id>_progress%
- %halloween_quest_<id>_completed%

UplineDialogues conditions can use PlaceholderAPI placeholders. PlaceholderAPI must be installed for these placeholders; dialogue commands work without it.

## Secret rooms: generation and locate

- New Overworld chunks have a 5% configured chance to try generating one room. This applies only when Minecraft first generates a chunk; already generated chunks are not retroactively scanned.
- The generator now makes up to 64 safe-volume attempts per eligible chunk, but terrain can still reject every attempt. A successful room is saved in `plugins/HalloweenCore/secret-rooms.yml`.
- `/halloween rooms status` shows the configured chance and the number of recorded rooms.
- `/locate secretroom` (also `/locate room` and `/locate rooms`) finds the nearest recorded room in the player's current world and prints its coordinates. Other `/locate` arguments are forwarded to Minecraft's vanilla locate command.
- `/halloween locate room` is also available.
- Admins can test generation with `/halloween rooms generate` while standing in a suitable underground chunk. It uses the same natural-stone safety check and records successful rooms.
- This is a plugin-specific locate command rather than overriding vanilla `/locate`, so ordinary vanilla structure locating remains intact.

## Suggested dialogue and quest blueprint (author these in /updialog panel)

These are story/content templates, not pre-populated rewards. Use the UplineDialogues editor to create the dialogue nodes and response branches; save them to NPCs. For each quest, use an ID from this list, set the objective/conditions in the editor, and only add the stated HalloweenCore actions after the condition is true. No reward amount or item payout is configured here.

### Easy quests
1. `first_contact` — Old villager welcomes the player and asks them to return with 8 wheat. Verify inventory in UplineDialogues, remove wheat only on the success branch, then run `halloween quest start first_contact`, `halloween quest progress first_contact 1`, and `halloween quest complete first_contact` as OP.
2. `lost_letter` — Find a letter in an agreed existing location and return to the villager. The letter/location must be placed by an admin; no new structure is assumed.
3. `sweet_delivery` — Bring 3 of the already-retained ItemsAdder sweets. Check the exact custom item IDs in ItemsAdder; don't use guessed IDs.
4. `farmers_request` — Bring 12 carrots or potatoes. Offer separate branches for either item.
5. `village_rumour` — Speak to two existing NPCs in sequence; use dialogue branches/conditions to guide the player.

### Medium quests
6. `herbalist_notes` — Bring 6 specified vanilla ingredients; the NPC lists what is still missing.
7. `night_watch` — Visit two admin-chosen locations after dark; check world/time conditions in UplineDialogues where supported.
8. `sealed_message` — Take a message from one NPC to another and choose which response to report back.
9. `hidden_clue` — Solve a short riddle whose answer unlocks a follow-up dialogue node.
10. `lost_key_report` — Ask three NPCs for clues about a missing key; this quest does not itself grant a key.

### Hard quests
11. `supply_chain` — Complete the farmer, herbalist and messenger quests in sequence. Gate the final dialogue on their completed placeholders.
12. `three_clues` — Find three admin-placed clues, each unlocking the next branch.
13. `deep_secret` — Locate a recorded secret room with `/halloween locate room`, report the coordinates to an NPC, and return. This does not decide chest contents or grant a key.
14. `final_test` — Complete the previous story branches and pass a multi-step inventory/choice check. Keep it as story completion only until the final reward is agreed.

For every quest, create these nodes in the editor: greeting, explanation, accept, progress/reminder, success, already-completed and goodbye. Branch success only after the actual inventory/condition check; otherwise players could repeatedly trigger completion. For a one-step quest the configured target is 1. Multi-step quests should use distinct IDs per step or a deliberately configured target. Avoid console/admin command actions on options visible to untrusted players; restrict them to successful branches and test with a non-OP account.

## Secret room key

HalloweenCore exposes the test/admin command /halloween key give <amount> (or /halloween key give <player> <amount>). It creates a tagged key and uses the ItemsAdder icon if ItemsAdder is loaded. This command is not a decision about how players should normally acquire keys.

If the future dialogue story is explicitly meant to issue a key, use an OP command action to execute halloween key give 1 on the successful branch. Do not add this action to a dialogue until key distribution is agreed.
