# UplineDialogues integration

Use UplineDialogues as the dialogue editor and HalloweenCore as the persistent quest state holder. UplineDialogues is a separate plugin; HalloweenCore does not bundle or copy its code.

## Setup

1. Install UplineDialogues on the server and restart. The SpigotMC resource page for version 3.0.0 describes its local editor and command actions: https://www.spigotmc.org/resources/uplinedialogues.112285/
2. Run /updialog panel and open the local editor URL printed by the plugin.
3. Attach a dialogue to the appropriate existing villager/NPC entity.
4. Build response choices and branch each option to the next dialogue node in the editor.
5. Where a branch should start a quest, add a command action run as the player: halloween quest start <quest-id>.
6. Where a branch verifies that an item was provided, first configure the UplineDialogues condition and inventory action. Only on the successful branch add the command action halloween quest progress <quest-id> 1 and then halloween quest complete <quest-id>. Set these state-changing actions to run as OP; normal players cannot directly alter quest progress or completion.
7. Do not set a fragment amount or grant an item/key until that reward is explicitly agreed. A quest with no reward configuration pays zero fragments.

## Quest commands

- /halloween quest start <id> starts an unfinished quest for the command's player.
- /halloween quest progress <id> <amount> advances an active quest; admin/OP action only.
- /halloween quest complete <id> marks an active quest completed once its configured target is met; admin/OP action only.
- /halloween quest status [id] shows progress.
- /halloween fragments shows the player's accumulated fragments.

IDs may contain lowercase letters, numbers, dashes and underscores, up to 48 characters. If no definition exists, the title is based on the ID, target defaults to 1 and reward-fragments defaults to 0.

To define a title, target or reward amount, add a definition under quests.definitions in config.yml. Leave the section empty while the quest design is still being agreed. A configured reward-fragments value is paid when the quest is completed and is protected against duplicate payment.

## Placeholders (PlaceholderAPI optional)

- %halloween_fragments%
- %halloween_quest_<id>_started%
- %halloween_quest_<id>_progress%
- %halloween_quest_<id>_completed%

UplineDialogues conditions can use PlaceholderAPI placeholders. PlaceholderAPI must be installed for these placeholders; dialogue commands work without it.

## Secret room key

HalloweenCore exposes the test/admin command /halloween key give <amount> (or /halloween key give <player> <amount>). It creates a tagged key and uses the ItemsAdder icon if ItemsAdder is loaded. This command is not a decision about how players should normally acquire keys.

If the future dialogue story is explicitly meant to issue a key, use an OP command action to execute halloween key give 1 on the successful branch. Do not add this action to a dialogue until key distribution is agreed.
