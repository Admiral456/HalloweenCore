# New HalloweenCore scope

## Included

1. Villager/NPC dialogue is authored with UplineDialogues. The dialogue tree decides what the NPC says, which answer branches are visible, and when an action is allowed.
2. HalloweenCore records quest start, progress and completion, persists it per player, and prevents repeat completion. Optional fragment and item rewards are paid once only when configured. The default reward is zero fragments and no items because the reward plan is not final.
3. A new Overworld chunk has a 2.5% chance to receive one hidden 7x5x7 room below ground. Generation checks a whole solid-natural-stone volume before changing blocks and skips the protected world named Spawn.
4. The room has one tagged chest. It remains locked until a specially tagged secret-room key is used. One key is consumed on first unlock; later openings do not require another key.
5. ItemsAdder retains thirteen imported sweet sprites from Stev's Halloween, plus the original 32x32 room-key icon.

## Not included

The legacy mob, boss, random-event, seasonal, grave-encounter, old quest-campaign, shop, passive-effect, world-decoration, audio and shader systems are removed from active code, configuration and packaged resources. The repository no longer packages the old MythicMobs/ModelEngine definitions or assets.

## Unresolved on purpose

- Exact NPC dialogue text, quest objectives and quest list.
- Which quests pay fragments, and the amount for each quest.
- What goes inside secret-room chests.
- How ordinary players obtain keys. The admin command only exists for controlled testing. No default drop, shop price, NPC payout or quest reward grants a key.
