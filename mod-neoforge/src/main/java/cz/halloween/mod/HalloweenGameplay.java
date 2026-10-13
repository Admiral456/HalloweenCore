package cz.halloween.mod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HalloweenGameplay {
    private static final AttributeModifier TALISMAN_HEALTH = new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath(HalloweenCoreMod.MOD_ID, "cursed_talisman_health"),
            2.0D, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier WARDEN_SET_TOUGHNESS = new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath(HalloweenCoreMod.MOD_ID, "crimson_warden_set_toughness"),
            4.0D, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier WARDEN_SET_KNOCKBACK = new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath(HalloweenCoreMod.MOD_ID, "crimson_warden_set_knockback"),
            0.10D, AttributeModifier.Operation.ADD_VALUE);
    private static String activeEventId = "";
    private static long eventEndsAtTick;
    private static long nextWaveAtTick;
    private static final List<String> EVENT_IDS = List.of(
            "soulstorm", "witching_hour", "cursed_harvest",
            "blood_moon_invasion", "pumpkin_apocalypse", "graveyard_rising");
    private static final List<String> ITEM_IDS = List.of(
            "cursed_talisman", "nocturne_sovereign_cowl", "batwing_elytra",
            "crimson_warden_helmet", "crimson_warden_chestplate", "crimson_warden_leggings",
            "crimson_warden_boots", "crimson_warden_sword", "crimson_warden_pickaxe",
            "crimson_warden_axe", "crimson_warden_shovel", "crimson_warden_hoe",
            "crimson_warden_shield", "halloween_token", "cursed_candy", "haunted_map");
    private static final List<String> MOB_IDS = List.of(
            "cursed_zombie", "void_reaper", "gravekeeper", "frost_stalker", "blood_spider",
            "pumpkin_wraith", "hex_witch", "nightmare", "headless_nick", "goblin_king", "vampire_king");

    private HalloweenGameplay() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        var eventRoot = Commands.literal("event")
                .then(Commands.literal("start")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("event", StringArgumentType.word())
                                .executes(context -> startEvent(context.getSource(),
                                        StringArgumentType.getString(context, "event")))))
                .then(Commands.literal("stop")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> stopEvent(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> listEvents(context.getSource())));

        var spawnRoot = Commands.literal("spawn")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("mob", StringArgumentType.word())
                        .executes(context -> spawnMob(context.getSource(),
                                StringArgumentType.getString(context, "mob"), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> spawnMob(context.getSource(),
                                        StringArgumentType.getString(context, "mob"),
                                        EntityArgument.getPlayer(context, "player")))));

        var itemForPlayer = Commands.argument("item", StringArgumentType.word())
                .executes(context -> giveItem(context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        StringArgumentType.getString(context, "item")));
        var playerForGive = Commands.argument("player", EntityArgument.player()).then(itemForPlayer);
        var giveRoot = Commands.literal("give")
                .requires(source -> source.hasPermission(2))
                .then(playerForGive);

        var listRoot = Commands.literal("list")
                .then(Commands.literal("items").executes(context -> listItems(context.getSource())))
                .then(Commands.literal("mobs").executes(context -> listMobs(context.getSource())))
                .then(Commands.literal("events").executes(context -> listEvents(context.getSource())));

        var assetsRoot = Commands.literal("assets")
                .then(Commands.literal("status").executes(context -> showAssetsStatus(context.getSource())))
                .then(Commands.literal("validate")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> validateAssets(context.getSource())));

        var atmosphereRoot = Commands.literal("atmosphere")
                .then(Commands.literal("status").executes(context -> showAtmosphereStatus(context.getSource())))
                .then(Commands.literal("preview")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> previewAtmosphere(context.getSource(), false)))
                .then(Commands.literal("test")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> previewAtmosphere(context.getSource(), true)));

        var spawnTesting = Commands.literal("mob")
                .then(Commands.argument("id", StringArgumentType.word())
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> spawnMob(context.getSource(),
                                StringArgumentType.getString(context, "id"), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> spawnMob(context.getSource(),
                                        StringArgumentType.getString(context, "id"),
                                        EntityArgument.getPlayer(context, "player")))));

        var spawnNick = Commands.literal("nick")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> spawnMob(context.getSource(), "headless_nick",
                                EntityArgument.getPlayer(context, "player"))));

        spawnRoot.then(spawnTesting).then(spawnNick);

        var helpRoot = Commands.literal("help")
                .executes(context -> showHelp(context.getSource()));

        var questsRoot = Commands.literal("quests")
                .executes(context -> showQuests(context.getSource()))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> resetQuests(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));

        var root = Commands.literal("halloween")
                .executes(context -> showStatus(context.getSource()))
                .then(Commands.literal("status").executes(context -> showStatus(context.getSource())))
                .then(helpRoot)
                .then(listRoot)
                .then(assetsRoot)
                .then(eventRoot)
                .then(spawnRoot)
                .then(giveRoot)
                .then(Commands.literal("progress").executes(context -> showProgress(context.getSource())))
                .then(questsRoot)
                .then(atmosphereRoot);
        event.getDispatcher().register(root);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        long tick = overworld.getGameTime();
        long nowMs = System.currentTimeMillis();
        HalloweenProgressData saved = HalloweenProgressData.get(server);

        // Rebuild the in-memory timer state after a restart using durable wall-clock deadlines.
        if (activeEventId.isBlank()) {
            long savedCode = saved.getGlobalValue("active_event_code");
            long savedExpiry = saved.getGlobalValue("active_event_until_epoch_ms");
            if (savedCode > 0L && savedExpiry > nowMs) {
                String restored = eventIdForCode(savedCode);
                if (!restored.isBlank()) {
                    activeEventId = restored;
                    eventEndsAtTick = tick + millisecondsToTicks(savedExpiry - nowMs);
                    long nextWaveMs = saved.getGlobalValue("active_event_next_wave_epoch_ms");
                    nextWaveAtTick = tick + millisecondsToTicks(Math.max(0L, nextWaveMs - nowMs));
                    saved.setGlobalValue("next_event_at_epoch_ms", 0L);
                    HalloweenCoreMod.LOGGER.info("Restored active Halloween event {} after restart.", activeEventId);
                } else {
                    clearPersistedActiveEvent(saved);
                    if (saved.getGlobalValue("next_event_at_epoch_ms") <= nowMs) {
                        saved.setGlobalValue("next_event_at_epoch_ms",
                                nowMs + 1000L * HalloweenConfig.EVENT_INTERVAL_SECONDS.get());
                    }
                }
            } else if (savedCode > 0L) {
                clearPersistedActiveEvent(saved);
                if (saved.getGlobalValue("next_event_at_epoch_ms") <= nowMs) {
                    saved.setGlobalValue("next_event_at_epoch_ms",
                            nowMs + 1000L * HalloweenConfig.EVENT_INTERVAL_SECONDS.get());
                }
            }
        }

        if (!HalloweenConfig.EVENTS_ENABLED.get()) {
            if (!activeEventId.isBlank()) stopEventForServer(server);
            return;
        }
        if (!activeEventId.isBlank() && tick >= eventEndsAtTick) stopEventForServer(server);

        // The initial delay and subsequent schedule are stored so a restart does not reset the event clock.
        if (activeEventId.isBlank() && nextWaveAtTick <= 0L) {
            long savedNextEvent = saved.getGlobalValue("next_event_at_epoch_ms");
            if (savedNextEvent > 0L) {
                nextWaveAtTick = tick + millisecondsToTicks(Math.max(0L, savedNextEvent - nowMs));
            } else {
                long initialDelayMs = 1000L * HalloweenConfig.INITIAL_DELAY_SECONDS.get();
                nextWaveAtTick = tick + millisecondsToTicks(initialDelayMs);
                saved.setGlobalValue("next_event_at_epoch_ms", nowMs + initialDelayMs);
            }
        }

        if (activeEventId.isBlank() && nextWaveAtTick > 0 && tick >= nextWaveAtTick) {
            List<ServerPlayer> players = eligiblePlayers(server);
            if (!players.isEmpty()) {
                startEventInternal(server, randomEventId(), tick);
            } else {
                nextWaveAtTick = tick + 200L;
                saved.setGlobalValue("next_event_at_epoch_ms", nowMs + 10_000L);
            }
        }
        if (!activeEventId.isBlank() && tick >= nextWaveAtTick) {
            List<ServerPlayer> players = eligiblePlayers(server);
            if (!players.isEmpty()) runEventWave(players);
            nextWaveAtTick = tick + 20L * 25L;
            saved.setGlobalValue("active_event_next_wave_epoch_ms", System.currentTimeMillis() + 25_000L);
        }
    }

    @SubscribeEvent
    public static void preventHalloweenEntitiesInForbiddenWorlds(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof HalloweenMonster
                && !HalloweenWorldPolicy.isAllowed(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void modifyBloodMoonDamage(LivingDamageEvent.Pre event) {
        if (!"blood_moon_invasion".equals(activeEventId)) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !HalloweenWorldPolicy.isAllowed(player.level())) return;
        Entity attacker = event.getSource().getEntity();
        boolean hostile = attacker instanceof Monster || attacker instanceof EnderDragon
                || attacker instanceof WitherBoss || attacker instanceof Ghast
                || attacker instanceof Phantom || attacker instanceof Shulker || attacker instanceof Slime;
        if (hostile && HalloweenWorldPolicy.isAllowed(attacker.level())) {
            // This is the single central multiplier point; do not also multiply
            // in the event wave handler or per-mob attack code.
            event.setNewDamage(Math.min(1000.0F, event.getNewDamage() * 3.0F));
        }
    }

    @SubscribeEvent
    public static void onHarvest(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!HalloweenWorldPolicy.isAllowed(player.level())) return;
        var state = event.getState();
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop)
                || !state.hasProperty(net.minecraft.world.level.block.CropBlock.AGE)
                || state.getValue(net.minecraft.world.level.block.CropBlock.AGE) < crop.getMaxAge()) return;

        HalloweenProgressData progress = progressDataFor(player);
        long harvested = progress.addPlayerValue(player.getUUID(), "mature_crops_harvested", 1L);
        recordStoryAction(player, "farming");
        int fragments = HalloweenProgressRewards.cropFragments(harvested);
        if (fragments > 0) {
            long total = progress.addFragments(player.getUUID(), fragments);
            player.displayClientMessage(Component.literal(
                    "HalloweenCore: +" + fragments + " fragment za sklizeň (celkem " + total + ")."), true);
        }

        // Keep the event effect separate from permanent progression accounting:
        // ripe crops advance the player's statistics even when Cursed Harvest is inactive.
        if (!"cursed_harvest".equals(activeEventId)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        spawnWaveAround(level, player, 2);
    }

    @SubscribeEvent
    public static void onOreMined(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!HalloweenWorldPolicy.isAllowed(player.level())) return;
        var state = event.getState();
        if (!(state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES))) return;

        HalloweenProgressData progress = progressDataFor(player);
        long mined = progress.addPlayerValue(player.getUUID(), "ores_mined", 1L);
        int fragments = HalloweenProgressRewards.oreFragments(mined);
        if (fragments > 0) {
            long total = progress.addFragments(player.getUUID(), fragments);
            player.displayClientMessage(Component.literal(
                    "HalloweenCore: +" + fragments + " fragment za těžbu (celkem " + total + ")."), true);
        }
    }

    @SubscribeEvent
    public static void onHalloweenMobDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        var dead = event.getEntity();
        if (!HalloweenWorldPolicy.isAllowed(player.level())
                || !HalloweenWorldPolicy.isAllowed(dead.level()) || !isHostileEntity(dead)) return;

        if (dead instanceof HalloweenMonster mob) {
            boolean boss = HalloweenEntities.isBoss(mob.getType());
            HalloweenProgressData progress = progressDataFor(player);
            progress.addPlayerValue(player.getUUID(), "special_mob_kills", 1L);
            if (boss) progress.addPlayerValue(player.getUUID(), "bosses_defeated", 1L);
            int reward = HalloweenProgressRewards.mobKillFragments(boss);
            long total = progress.addFragments(player.getUUID(), reward);
            player.displayClientMessage(Component.literal(
                    "HalloweenCore: +" + reward + " fragment" + (reward == 1 ? "" : "ů")
                            + " za poražení " + (boss ? "bosse" : "speciálního moba")
                            + " (celkem " + total + ")."), true);
            String mobId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
            recordStoryMobKill(player, mobId, true);
            return;
        }

        // Preserve the original first chapter objective: any hostile vanilla mob kill counts,
        // but only for the currently active story chapter.
        recordStoryMobKill(player, null, true);
    }

    private static boolean isHostileEntity(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof Monster || entity instanceof EnderDragon || entity instanceof WitherBoss
                || entity instanceof Ghast || entity instanceof Phantom || entity instanceof Shulker
                || entity instanceof Slime;
    }

    private static void recordStoryAction(ServerPlayer player, String action) {
        if (!HalloweenConfig.STORY_QUESTS_ENABLED.get() || !HalloweenWorldPolicy.isAllowed(player.level())) return;
        HalloweenProgressData data = progressDataFor(player);
        int index = HalloweenStoryQuests.normalizeIndex(
                data.getPlayerValue(player.getUUID(), "story_quest_index"));
        if (index >= HalloweenStoryQuests.ALL.size()) return;
        HalloweenStoryQuests.Quest quest = HalloweenStoryQuests.ALL.get(index);
        if (!HalloweenStoryQuests.matchesAction(quest, action)) return;
        advanceStoryQuest(player, data, quest, index);
    }

    private static void recordStoryMobKill(ServerPlayer player, String mobId, boolean hostile) {
        if (!HalloweenConfig.STORY_QUESTS_ENABLED.get() || !HalloweenWorldPolicy.isAllowed(player.level())) return;
        HalloweenProgressData data = progressDataFor(player);
        int index = HalloweenStoryQuests.normalizeIndex(
                data.getPlayerValue(player.getUUID(), "story_quest_index"));
        if (index >= HalloweenStoryQuests.ALL.size()) return;
        HalloweenStoryQuests.Quest quest = HalloweenStoryQuests.ALL.get(index);
        if (!HalloweenStoryQuests.matchesMobKill(quest, mobId, hostile)) return;
        advanceStoryQuest(player, data, quest, index);
    }

    private static void advanceStoryQuest(ServerPlayer player, HalloweenProgressData data,
                                          HalloweenStoryQuests.Quest quest, int index) {
        long progress = data.addPlayerValue(player.getUUID(), "story_quest_progress", 1L);
        if (progress < quest.target()) {
            if (progress == 1L || progress == quest.target() - 1L) {
                player.displayClientMessage(Component.literal(
                        "Quest " + (index + 1) + "/" + HalloweenStoryQuests.ALL.size() + ": "
                                + quest.title() + " (" + progress + "/" + quest.target() + ")"), true);
            }
            return;
        }

        data.setPlayerValue(player.getUUID(), "story_quest_index", index + 1L);
        data.setPlayerValue(player.getUUID(), "story_quest_progress", 0L);
        long total = data.addFragments(player.getUUID(), quest.reward());
        player.sendSystemMessage(Component.literal(
                "§6§lKAPITOLA DOKONČENA §8» §f" + quest.title() + " §8• §e+" + quest.reward() + " fragmentů"));
        player.sendSystemMessage(Component.literal("HalloweenCore: zůstatek " + total + " fragmentů."));
        if (index + 1 < HalloweenStoryQuests.ALL.size()) {
            HalloweenStoryQuests.Quest next = HalloweenStoryQuests.ALL.get(index + 1);
            player.sendSystemMessage(Component.literal("Další úkol odemčen: " + next.title()
                    + ". Použij /halloween quests."));
        } else {
            player.sendSystemMessage(Component.literal("§6§lPŘÍBĚH DOKONČEN §8» §eDěkujeme za záchranu světa."));
        }
    }

    public static void activateTalisman(ServerPlayer player) {
        if (!HalloweenWorldPolicy.isAllowed(player.level())) return;
        long now = System.currentTimeMillis();
        HalloweenProgressData progress = progressDataFor(player);
        long until = progress.getPlayerValue(player.getUUID(), "talisman_until_epoch_ms");
        if (until > now) {
            player.displayClientMessage(Component.literal(
                    "Prokletý talisman už je aktivní ještě " + ((until - now) / 1000L) + " s."), true);
            return;
        }
        progress.setPlayerValue(player.getUUID(), "talisman_until_epoch_ms", now + 60_000L);
        applyTalismanModifier(player);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) player.setHealth((float) Math.min(maxHealth.getValue(), player.getHealth() + 2.0F));
        player.displayClientMessage(Component.literal("Prokletý talisman: +1 srdce na 60 sekund."), true);
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        if (player.tickCount % 20 != 0) return;

        boolean allowedWorld = HalloweenWorldPolicy.isAllowed(player.level());
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        long now = System.currentTimeMillis();
        HalloweenProgressData progress = allowedWorld ? progressDataFor(player) : null;
        long until = progress == null ? 0L
                : progress.getPlayerValue(player.getUUID(), "talisman_until_epoch_ms");
        if (until > now && allowedWorld && maxHealth != null) {
            applyTalismanModifier(player);
        } else {
            // Do not touch global progression storage from a forbidden world; only remove
            // temporary effects there. The saved expiry remains available if the player returns.
            if (progress != null && until > 0L && until <= now) {
                progress.setPlayerValue(player.getUUID(), "talisman_until_epoch_ms", 0L);
            }
            if (maxHealth != null) {
                maxHealth.removeModifier(TALISMAN_HEALTH.id());
                if (player.getHealth() > maxHealth.getValue()) player.setHealth((float) maxHealth.getValue());
            }
        }

        boolean fullWardenSet = allowedWorld
                && player.getItemBySlot(EquipmentSlot.HEAD).is(HalloweenItems.CRIMSON_WARDEN_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(HalloweenItems.CRIMSON_WARDEN_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(HalloweenItems.CRIMSON_WARDEN_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(HalloweenItems.CRIMSON_WARDEN_BOOTS.get());
        updateSetModifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), WARDEN_SET_TOUGHNESS, fullWardenSet);
        updateSetModifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), WARDEN_SET_KNOCKBACK, fullWardenSet);
    }

    private static void updateSetModifier(AttributeInstance attribute, AttributeModifier modifier, boolean active) {
        if (attribute == null) return;
        if (active) attribute.addOrUpdateTransientModifier(modifier);
        else attribute.removeModifier(modifier.id());
    }

    private static HalloweenProgressData progressDataFor(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Halloween progression can only be accessed from a server level.");
        }
        return HalloweenProgressData.get(serverLevel.getServer());
    }

    private static void applyTalismanModifier(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) attribute.addOrUpdateTransientModifier(TALISMAN_HEALTH);
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("HalloweenCore — nativní příkazy"), false);
        source.sendSuccess(() -> Component.literal("/halloween status | progress | help"), false);
        source.sendSuccess(() -> Component.literal("/halloween list items | mobs | events"), false);
        source.sendSuccess(() -> Component.literal("/halloween give <hráč> <id-předmětu|all>"), false);
        source.sendSuccess(() -> Component.literal("/halloween spawn <mob-id> <hráč>"), false);
        source.sendSuccess(() -> Component.literal("/halloween spawn mob <mob-id> [hráč] | nick <hráč>"), false);
        source.sendSuccess(() -> Component.literal("/halloween event start <id> | stop | list"), false);
        source.sendSuccess(() -> Component.literal("/halloween assets status | validate"), false);
        source.sendSuccess(() -> Component.literal("/halloween quests | quests reset <hráč>"), false);
        source.sendSuccess(() -> Component.literal("/halloween atmosphere status | preview | test"), false);
        source.sendSuccess(() -> Component.literal("Všechny změny světa fungují jen v explicitně povolených dimenzích; seznam je ve výchozím stavu prázdný."), false);
        return 1;
    }

    private static int listItems(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("HalloweenCore item ID (" + ITEM_IDS.size() + "):"), false);
        for (String id : ITEM_IDS) source.sendSuccess(() -> Component.literal(" • " + id), false);
        return ITEM_IDS.size();
    }

    private static int listMobs(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("HalloweenCore mob ID (" + MOB_IDS.size() + "):"), false);
        for (String id : MOB_IDS) source.sendSuccess(() -> Component.literal(" • " + id), false);
        source.sendSuccess(() -> Component.literal(
                "Poznámka: modely/AI jsou zatím první fáze; vlastní animované modely a koňský Nick ještě nejsou dokončeny."), false);
        return MOB_IDS.size();
    }

    private static int showAtmosphereStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("HalloweenCore — stav atmosféry"), false);
        source.sendSuccess(() -> Component.literal("Povolené dimenze: " + HalloweenConfig.ALLOWED_WORLD_IDS.get()), false);
        source.sendSuccess(() -> Component.literal("Povrchové dekorace: "
                + (HalloweenConfig.WORLD_DECORATIONS_ENABLED.get() ? "zapnuté v povolených dimenzích" : "vypnuté")), false);
        source.sendSuccess(() -> Component.literal("Předgenerované vesnice a hřbitovy: zatím nejsou v nativním módu implementované."), false);
        source.sendSuccess(() -> Component.literal("Globální Halloween textura země a povinný shader: zatím nejsou součástí nativního buildu."), false);
        source.sendSuccess(() -> Component.literal("Preview/test umí vyzkoušet jen ohraničené částice; neumisťuje bloky."), false);
        return 1;
    }

    private static int previewAtmosphere(CommandSourceStack source, boolean fullTest) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Tento vizuální test spusť přímo ve hře, nikoli z konzole."));
            return 0;
        }
        if (!HalloweenWorldPolicy.isAllowed(player.level())) {
            source.sendFailure(Component.literal("Atmosférický test odmítnut: tento svět není explicitně povolen."));
            return 0;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Atmosférický test je dostupný pouze na serverovém světě."));
            return 0;
        }

        double radius = fullTest ? 3.0D : 2.0D;
        int points = fullTest ? 24 : 12;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i) / points;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            level.sendParticles(fullTest ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL,
                    x, player.getY() + 0.15D, z, 1, 0.03D, 0.06D, 0.03D, 0.005D);
        }
        if (fullTest) {
            level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1.0D, player.getZ(),
                    24, 0.45D, 0.5D, 0.45D, 0.01D);
            level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0D, player.getZ(),
                    12, 0.3D, 0.4D, 0.3D, 0.01D);
            source.sendSuccess(() -> Component.literal(
                    "Omezený test částic dokončen. Nebyly umístěny ani změněny žádné bloky."), true);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "Atmosféra preview: vizuální kruh částic; žádné bloky nebyly umístěny ani změněny."), false);
        }
        return points;
    }

    private static int showAssetsStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("HalloweenCore assets: " + ITEM_IDS.size()
                + " item IDs, " + MOB_IDS.size() + " entity IDs, " + EVENT_IDS.size() + " event IDs."), false);
        source.sendSuccess(() -> Component.literal("První fáze: jednotlivé item ikony a entity textury; všechny moby zatím sdílejí zombie geometrii."), false);
        source.sendSuccess(() -> Component.literal("World decorations enabled: " + HalloweenConfig.WORLD_DECORATIONS_ENABLED.get()), false);
        source.sendSuccess(() -> Component.literal("Allowed dimension IDs: " + HalloweenConfig.ALLOWED_WORLD_IDS.get()), false);
        source.sendSuccess(() -> Component.literal("ItemsAdder/ModelEngine/Paper bridge: v nativním NeoForge buildu není součástí."), false);
        return 1;
    }

    private static int validateAssets(CommandSourceStack source) {
        List<String> expected = new ArrayList<>();
        expected.add("/assets/halloweencore/lang/cs_cz.json");
        expected.add("/assets/halloweencore/equipment/crimson_warden.json");
        for (String id : ITEM_IDS) {
            expected.add("/assets/halloweencore/textures/item/" + id + ".png");
            expected.add("/assets/halloweencore/models/item/" + id + ".json");
        }
        for (String id : MOB_IDS) expected.add("/assets/halloweencore/textures/entity/" + id + ".png");
        expected.add("/assets/halloweencore/textures/entity/equipment/humanoid/crimson_warden.png");
        expected.add("/assets/halloweencore/textures/entity/equipment/humanoid_leggings/crimson_warden_leggings.png");
        expected.add("/assets/halloweencore/textures/entity/equipment/humanoid/nocturne_cowl.png");
        expected.add("/assets/halloweencore/textures/entity/equipment/wings/batwing_elytra.png");

        List<String> missing = new ArrayList<>();
        for (String resource : expected) {
            if (HalloweenCoreMod.class.getResource(resource) == null) missing.add(resource);
        }
        if (missing.isEmpty()) {
            source.sendSuccess(() -> Component.literal("HalloweenCore assets VALID: ověřeno " + expected.size()
                    + " záznamů classpath (modely, textury a český překlad)."), true);
            return expected.size();
        }
        source.sendFailure(Component.literal("HalloweenCore assets INVALID: chybí " + missing.size()
                + " z " + expected.size() + " souborů."));
        for (String path : missing) source.sendFailure(Component.literal(" • " + path));
        return 0;
    }

    private static int showQuests(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Příběhové questy zobrazíš přímo ve hře: /halloween quests."));
            return 0;
        }
        if (!HalloweenWorldPolicy.isAllowed(player.level())) {
            source.sendFailure(Component.literal("HalloweenCore: příběh není v tomto světě povolen."));
            return 0;
        }
        if (!HalloweenConfig.STORY_QUESTS_ENABLED.get()) {
            source.sendFailure(Component.literal("Příběhové questy jsou vypnuté v konfiguraci."));
            return 0;
        }
        HalloweenProgressData data = progressDataFor(player);
        int index = HalloweenStoryQuests.normalizeIndex(
                data.getPlayerValue(player.getUUID(), "story_quest_index"));
        long progress = data.getPlayerValue(player.getUUID(), "story_quest_progress");
        source.sendSuccess(() -> Component.literal("§6§lPŘÍBĚHOVÉ HALLOWEEN QUESTY"), false);
        source.sendSuccess(() -> Component.literal("Dokončeno: " + index + "/" + HalloweenStoryQuests.ALL.size()), false);
        if (index >= HalloweenStoryQuests.ALL.size()) {
            source.sendSuccess(() -> Component.literal("§aVšechny kapitoly jsou dokončeny."), false);
            return 1;
        }
        HalloweenStoryQuests.Quest quest = HalloweenStoryQuests.ALL.get(index);
        long safeProgress = Math.min(progress, quest.target());
        source.sendSuccess(() -> Component.literal("Kapitola " + (index + 1) + ": " + quest.title()), false);
        source.sendSuccess(() -> Component.literal(quest.description()), false);
        source.sendSuccess(() -> Component.literal("Postup: " + safeProgress + "/" + quest.target()), false);
        source.sendSuccess(() -> Component.literal("Odměna: " + quest.reward() + " fragmentů"), false);
        return 1;
    }

    private static int resetQuests(CommandSourceStack source, ServerPlayer player) {
        if (!HalloweenWorldPolicy.isAllowed(player.level())) {
            source.sendFailure(Component.literal("Quest reset odmítnut: cílový hráč je v zakázaném/nepovoleném světě."));
            return 0;
        }
        HalloweenProgressData data = progressDataFor(player);
        data.setPlayerValue(player.getUUID(), "story_quest_index", 0L);
        data.setPlayerValue(player.getUUID(), "story_quest_progress", 0L);
        source.sendSuccess(() -> Component.literal("Příběhové questy hráče " + player.getName().getString()
                + " byly resetovány. Fragmenty a ostatní statistiky zůstaly zachovány."), true);
        return 1;
    }

    private static int showProgress(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Přehled postupu je dostupný přímo hráči ve hře."));
            return 0;
        }
        if (!HalloweenWorldPolicy.isAllowed(player.level())) {
            source.sendFailure(Component.literal("HalloweenCore: tento svět není povolen pro Halloween obsah."));
            return 0;
        }
        HalloweenProgressData data = progressDataFor(player);
        source.sendSuccess(() -> Component.literal("HalloweenCore — tvůj trvalý postup"), false);
        source.sendSuccess(() -> Component.literal("Fragmenty: " + data.getPlayerValue(player.getUUID(), "fragments")), false);
        source.sendSuccess(() -> Component.literal("Speciální mobů poraženo: " + data.getPlayerValue(player.getUUID(), "special_mob_kills")), false);
        source.sendSuccess(() -> Component.literal("Bossové poraženi: " + data.getPlayerValue(player.getUUID(), "bosses_defeated")), false);
        source.sendSuccess(() -> Component.literal("Rudy vytěženy: " + data.getPlayerValue(player.getUUID(), "ores_mined")), false);
        source.sendSuccess(() -> Component.literal("Dospělé plodiny sklizeny: " + data.getPlayerValue(player.getUUID(), "mature_crops_harvested")), false);
        source.sendSuccess(() -> Component.literal("Společné fragmenty serveru: " + data.getGlobalValue("fragments_collected")), false);
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("HalloweenCore Native 1.21.10 / NeoForge 21.10.64"), false);
        source.sendSuccess(() -> Component.literal(
                "Event: " + (activeEventId.isBlank() ? "NEAKTIVNÍ" : activeEventId)), false);
        source.sendSuccess(() -> Component.literal("Povolené dimenze: " + HalloweenConfig.ALLOWED_WORLD_IDS.get()), false);
        source.sendSuccess(() -> Component.literal("Příběhové questy: " + HalloweenConfig.STORY_QUESTS_ENABLED.get()), false);
        source.sendSuccess(() -> Component.literal("První event: " + HalloweenConfig.INITIAL_DELAY_SECONDS.get() + " s; další interval: " + HalloweenConfig.EVENT_INTERVAL_SECONDS.get() + " s."), false);
        source.sendSuccess(() -> Component.literal(
                "Prázdný allow-list = zakázat vše; svět Spawn je vždy blokován."), false);
        source.sendSuccess(() -> Component.literal(
                "Hybridní kompatibilita s Bukkit pluginy WarriorLandu není ověřena."), false);
        return 1;
    }

    private static int listEvents(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Eventy: soulstorm, witching_hour, cursed_harvest, blood_moon_invasion, pumpkin_apocalypse, graveyard_rising."), false);
        return 1;
    }

    private static int startEvent(CommandSourceStack source, String requested) {
        String id = requested.toLowerCase(Locale.ROOT);
        if (!EVENT_IDS.contains(id)) {
            source.sendFailure(Component.literal("Neznámý event. Použij /halloween event list."));
            return 0;
        }
        if (!activeEventId.isBlank()) {
            source.sendFailure(Component.literal("Event už běží: " + activeEventId));
            return 0;
        }
        MinecraftServer server = source.getServer();
        if (eligiblePlayers(server).isEmpty()) {
            source.sendFailure(Component.literal(
                    "Není online žádný hráč v povoleném světě. Nejdřív nastav allowed-world-ids."));
            return 0;
        }
        startEventInternal(server, id, server.overworld().getGameTime());
        return 1;
    }

    private static void startEventInternal(MinecraftServer server, String id, long tick) {
        activeEventId = id;
        int durationSeconds = HalloweenConfig.EVENT_DURATION_SECONDS.get();
        eventEndsAtTick = tick + 20L * durationSeconds;
        nextWaveAtTick = tick + 20L * 3L;
        HalloweenProgressData saved = HalloweenProgressData.get(server);
        long now = System.currentTimeMillis();
        saved.setGlobalValue("active_event_code", eventCodeForId(id));
        saved.setGlobalValue("active_event_until_epoch_ms", now + 1000L * durationSeconds);
        saved.setGlobalValue("active_event_next_wave_epoch_ms", now + 3_000L);
        saved.setGlobalValue("next_event_at_epoch_ms", 0L);
        broadcastAllowed(server, "[HalloweenCore] " + id + " začíná!");
        HalloweenCoreMod.LOGGER.info("Halloween event {} started.", id);
    }

    private static int stopEvent(CommandSourceStack source) {
        if (activeEventId.isBlank()) {
            source.sendFailure(Component.literal("Žádný event neběží."));
            return 0;
        }
        stopEventForServer(source.getServer());
        return 1;
    }

    private static void stopEventForServer(MinecraftServer server) {
        String ended = activeEventId;
        activeEventId = "";
        eventEndsAtTick = 0L;
        int intervalSeconds = HalloweenConfig.EVENT_INTERVAL_SECONDS.get();
        nextWaveAtTick = server.overworld().getGameTime() + 20L * intervalSeconds;
        HalloweenProgressData saved = HalloweenProgressData.get(server);
        clearPersistedActiveEvent(saved);
        saved.setGlobalValue("next_event_at_epoch_ms", System.currentTimeMillis() + 1000L * intervalSeconds);
        broadcastAllowed(server, "[HalloweenCore] " + ended + " skončil.");
        HalloweenCoreMod.LOGGER.info("Halloween event {} stopped.", ended);
    }

    private static void clearPersistedActiveEvent(HalloweenProgressData saved) {
        saved.setGlobalValue("active_event_code", 0L);
        saved.setGlobalValue("active_event_until_epoch_ms", 0L);
        saved.setGlobalValue("active_event_next_wave_epoch_ms", 0L);
    }

    private static long eventCodeForId(String id) {
        int index = EVENT_IDS.indexOf(id);
        return index < 0 ? 0L : index + 1L;
    }

    private static String eventIdForCode(long code) {
        return code < 1L || code > EVENT_IDS.size() ? "" : EVENT_IDS.get((int) code - 1);
    }

    private static long millisecondsToTicks(long milliseconds) {
        if (milliseconds <= 0L) return 1L;
        return Math.max(1L, (milliseconds + 49L) / 50L);
    }

    private static void runEventWave(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (!HalloweenWorldPolicy.isAllowed(player.level()) || !(player.level() instanceof ServerLevel level)) continue;
            if ("witching_hour".equals(activeEventId)) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DARKNESS, 100, 0));
            }
            int count = switch (activeEventId) {
                case "pumpkin_apocalypse" -> 4;
                case "blood_moon_invasion" -> 3;
                case "graveyard_rising" -> 3;
                case "cursed_harvest" -> 1;
                default -> 2;
            };
            spawnWaveAround(level, player, count);
        }
    }

    private static void spawnWaveAround(ServerLevel level, ServerPlayer player, int count) {
        if (!HalloweenWorldPolicy.isAllowed(level)) return;
        int existing = 0;
        for (Entity loaded : level.getAllEntities()) {
            if (loaded instanceof HalloweenMonster) existing++;
        }
        int limit = HalloweenConfig.MAX_ACTIVE_HALLOWEEN_MOBS.get();
        if (existing >= limit) return;

        for (int index = 0; index < count && existing + index < limit; index++) {
            String id = switch (activeEventId) {
                case "blood_moon_invasion" -> List.of("cursed_zombie", "void_reaper", "gravekeeper",
                        "blood_spider", "pumpkin_wraith", "hex_witch").get(Math.floorMod(player.tickCount + index, 6));
                case "pumpkin_apocalypse" -> "pumpkin_wraith";
                case "graveyard_rising" -> "gravekeeper";
                case "cursed_harvest" -> "blood_spider";
                case "witching_hour" -> "hex_witch";
                case "soulstorm" -> "void_reaper";
                default -> "cursed_zombie";
            };
            BlockPos position = findSpawnPos(level, player, index);
            if (position == null) continue;
            EntityType<HalloweenMonster> type = HalloweenEntities.byId(id);
            if (type == null) continue;
            HalloweenMonster mob = type.create(level, null, position, EntitySpawnReason.COMMAND, false, false);
            if (mob == null) continue;
            mob.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
            mob.setYRot(level.random.nextFloat() * 360.0F);
            mob.setXRot(0.0F);
            mob.setTarget(player);
            level.addFreshEntity(mob);
        }
    }

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, int offset) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = (level.random.nextDouble() * Math.PI * 2.0D) + offset * 0.6D;
            int radius = 12 + level.random.nextInt(13);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, player.getBlockY(), z));
            if (!level.getWorldBorder().isWithinBounds(surface)) continue;
            if (!level.getBlockState(surface).isAir() || !level.getBlockState(surface.above()).isAir()) continue;
            if (!level.getBlockState(surface.below()).blocksMotion()
                    || !level.getFluidState(surface.below()).isEmpty()) continue;
            if (!HalloweenWorldPolicy.isAllowed(level)) return null;
            return surface;
        }
        return null;
    }

    private static boolean isBoss(String id) {
        return "headless_nick".equals(id) || "goblin_king".equals(id) || "vampire_king".equals(id);
    }

    private static String randomEventId() {
        return EVENT_IDS.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(EVENT_IDS.size()));
    }

    private static List<ServerPlayer> eligiblePlayers(MinecraftServer server) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (HalloweenWorldPolicy.isAllowed(player.level())) result.add(player);
        }
        return result;
    }

    private static void broadcastAllowed(MinecraftServer server, String message) {
        for (ServerPlayer player : eligiblePlayers(server)) player.sendSystemMessage(Component.literal(message));
    }

    private static int spawnMob(CommandSourceStack source, String requested, ServerPlayer targetOverride) {
        ServerPlayer target = targetOverride;
        if (target == null && source.getEntity() instanceof ServerPlayer serverPlayer) target = serverPlayer;
        if (target == null) {
            source.sendFailure(Component.literal("Z konzole použij /halloween spawn <mob> <hráč>."));
            return 0;
        }
        if (!HalloweenWorldPolicy.isAllowed(target.level())) {
            source.sendFailure(Component.literal("Spawn odmítnut: cílový hráč je v zakázaném/nepovoleném světě."));
            return 0;
        }
        String id = requested.toLowerCase(Locale.ROOT);
        EntityType<HalloweenMonster> type = HalloweenEntities.byId(id);
        if (type == null) {
            source.sendFailure(Component.literal("Neznámý mob. ID: cursed_zombie, void_reaper, gravekeeper, frost_stalker, blood_spider, pumpkin_wraith, hex_witch, nightmare, headless_nick, goblin_king, vampire_king."));
            return 0;
        }
        ServerLevel level = (ServerLevel) target.level();
        BlockPos position = findSpawnPos(level, target, 0);
        if (position == null) {
            source.sendFailure(Component.literal("Nebyla nalezena bezpečná spawnovací pozice."));
            return 0;
        }
        HalloweenMonster mob = type.create(level, null, position, EntitySpawnReason.COMMAND, false, false);
        if (mob == null) {
            source.sendFailure(Component.literal("Entitu se nepodařilo vytvořit."));
            return 0;
        }
        mob.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        mob.setYRot(level.random.nextFloat() * 360.0F);
        mob.setXRot(0.0F);
        mob.setTarget(target);
        mob.setCustomNameVisible(isBoss(id));
        if (!level.addFreshEntity(mob)) {
            source.sendFailure(Component.literal("Server odmítl vložit entitu do světa."));
            return 0;
        }
        final ServerPlayer spawnedFor = target;
        source.sendSuccess(() -> Component.literal(
                "Spawnuto: " + id + " poblíž " + spawnedFor.getName().getString()), true);
        return 1;
    }

    private static Item itemById(String requested) {
        if (requested == null) return null;
        return switch (requested.toLowerCase(Locale.ROOT)) {
            case "cursed_talisman" -> HalloweenItems.CURSED_TALISMAN.get();
            case "nocturne_sovereign_cowl" -> HalloweenItems.NOCTURNE_SOVEREIGN_COWL.get();
            case "batwing_elytra" -> HalloweenItems.BATWING_ELYTRA.get();
            case "crimson_warden_helmet" -> HalloweenItems.CRIMSON_WARDEN_HELMET.get();
            case "crimson_warden_chestplate" -> HalloweenItems.CRIMSON_WARDEN_CHESTPLATE.get();
            case "crimson_warden_leggings" -> HalloweenItems.CRIMSON_WARDEN_LEGGINGS.get();
            case "crimson_warden_boots" -> HalloweenItems.CRIMSON_WARDEN_BOOTS.get();
            case "crimson_warden_sword" -> HalloweenItems.CRIMSON_WARDEN_SWORD.get();
            case "crimson_warden_pickaxe" -> HalloweenItems.CRIMSON_WARDEN_PICKAXE.get();
            case "crimson_warden_axe" -> HalloweenItems.CRIMSON_WARDEN_AXE.get();
            case "crimson_warden_shovel" -> HalloweenItems.CRIMSON_WARDEN_SHOVEL.get();
            case "crimson_warden_hoe" -> HalloweenItems.CRIMSON_WARDEN_HOE.get();
            case "crimson_warden_shield" -> HalloweenItems.CRIMSON_WARDEN_SHIELD.get();
            case "halloween_token" -> HalloweenItems.HALLOWEEN_TOKEN.get();
            case "cursed_candy" -> HalloweenItems.CURSED_CANDY.get();
            case "haunted_map" -> HalloweenItems.HAUNTED_MAP.get();
            default -> null;
        };
    }

    private static int giveItem(CommandSourceStack source, ServerPlayer player, String requested) {
        if (!HalloweenWorldPolicy.isAllowed(player.level())) {
            source.sendFailure(Component.literal("Předmět nebyl předán: hráč je v zakázaném/nepovoleném světě."));
            return 0;
        }
        if ("all".equalsIgnoreCase(requested)) {
            int delivered = 0;
            int failed = 0;
            for (String id : ITEM_IDS) {
                Item candidate = itemById(id);
                if (candidate == null) {
                    failed++;
                    continue;
                }
                ItemStack itemStack = new ItemStack(candidate);
                if (!player.getInventory().add(itemStack)) player.drop(itemStack, false);
                delivered++;
            }
            final int successCount = delivered;
            final int failureCount = failed;
            source.sendSuccess(() -> Component.literal(
                    "HalloweenCore: hráči " + player.getName().getString() + " předáno "
                            + successCount + "/" + ITEM_IDS.size() + " unikátních předmětů"
                            + (failureCount > 0 ? "; chyby: " + failureCount : "")
                            + ". Plný inventář: zbylé předměty byly bezpečně vyhozeny."), true);
            return delivered;
        }
        Item item = itemById(requested);
        if (item == null) {
            source.sendFailure(Component.literal("Neznámý předmět. Zkontroluj ID předmětu."));
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        source.sendSuccess(() -> Component.literal(
                "Předáno " + requested + " hráči " + player.getName().getString()), true);
        return 1;
    }
}
