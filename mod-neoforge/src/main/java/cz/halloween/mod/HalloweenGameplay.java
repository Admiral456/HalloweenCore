package cz.halloween.mod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HalloweenGameplay {
    private static final Map<UUID, Long> TALISMAN_UNTIL_TICK = new ConcurrentHashMap<>();
    private static final AttributeModifier TALISMAN_HEALTH = new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath(HalloweenCoreMod.MOD_ID, "cursed_talisman_health"),
            2.0D, AttributeModifier.Operation.ADD_VALUE);
    private static String activeEventId = "";
    private static long eventEndsAtTick;
    private static long nextWaveAtTick;

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

        var root = Commands.literal("halloween")
                .executes(context -> showStatus(context.getSource()))
                .then(Commands.literal("status").executes(context -> showStatus(context.getSource())))
                .then(eventRoot)
                .then(spawnRoot)
                .then(giveRoot);
        event.getDispatcher().register(root);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        long tick = overworld.getGameTime();

        if (!HalloweenConfig.EVENTS_ENABLED.get()) {
            if (!activeEventId.isBlank()) stopEventForServer(server);
            return;
        }
        if (!activeEventId.isBlank() && tick >= eventEndsAtTick) stopEventForServer(server);

        if (activeEventId.isBlank() && nextWaveAtTick > 0 && tick >= nextWaveAtTick) {
            List<ServerPlayer> players = eligiblePlayers(server);
            if (!players.isEmpty()) {
                startEventInternal(server, randomEventId(), tick);
            } else {
                nextWaveAtTick = tick + 200L;
            }
        }
        if (!activeEventId.isBlank() && tick >= nextWaveAtTick) {
            List<ServerPlayer> players = eligiblePlayers(server);
            if (!players.isEmpty()) runEventWave(players);
            nextWaveAtTick = tick + 20L * 25L;
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
        if (attacker instanceof Monster && HalloweenWorldPolicy.isAllowed(attacker.level())) {
            event.setNewDamage(Math.min(1000.0F, event.getNewDamage() * 3.0F));
        }
    }

    @SubscribeEvent
    public static void onHarvest(BlockEvent.BreakEvent event) {
        if (!"cursed_harvest".equals(activeEventId) || !(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!HalloweenWorldPolicy.isAllowed(player.level())) return;
        var state = event.getState();
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop)
                || !state.hasProperty(net.minecraft.world.level.block.CropBlock.AGE)
                || state.getValue(net.minecraft.world.level.block.CropBlock.AGE) < crop.getMaxAge()) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        spawnWaveAround(level, player, 2);
    }

    public static void activateTalisman(ServerPlayer player) {
        if (!HalloweenWorldPolicy.isAllowed(player.level())) return;
        long now = player.level().getGameTime();
        Long until = TALISMAN_UNTIL_TICK.get(player.getUUID());
        if (until != null && until > now) {
            player.displayClientMessage(Component.literal(
                    "Prokletý talisman už je aktivní ještě " + ((until - now) / 20L) + " s."), true);
            return;
        }
        TALISMAN_UNTIL_TICK.put(player.getUUID(), now + 20L * 60L);
        applyTalismanModifier(player);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) player.setHealth(Math.min(maxHealth.getValue(), player.getHealth() + 2.0F));
        player.displayClientMessage(Component.literal("Prokletý talisman: +1 srdce na 60 sekund."), true);
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        if (player.tickCount % 20 != 0) return;
        Long until = TALISMAN_UNTIL_TICK.get(player.getUUID());
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        long now = player.level().getGameTime();
        if (until != null && until > now && HalloweenWorldPolicy.isAllowed(player.level())) {
            applyTalismanModifier(player);
        } else {
            TALISMAN_UNTIL_TICK.remove(player.getUUID());
            maxHealth.removeModifier(TALISMAN_HEALTH.id());
            if (player.getHealth() > maxHealth.getValue()) player.setHealth(maxHealth.getValue());
        }
    }

    private static void applyTalismanModifier(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) attribute.addOrUpdateTransientModifier(TALISMAN_HEALTH);
    }

    private static int showStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("HalloweenCore Native 1.21.10 / NeoForge 21.10.64"), false);
        source.sendSuccess(() -> Component.literal(
                "Event: " + (activeEventId.isBlank() ? "NEAKTIVNÍ" : activeEventId)), false);
        source.sendSuccess(() -> Component.literal("Povolené dimenze: " + HalloweenConfig.ALLOWED_WORLD_IDS.get()), false);
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
        if (!List.of("soulstorm", "witching_hour", "cursed_harvest", "blood_moon_invasion",
                "pumpkin_apocalypse", "graveyard_rising").contains(id)) {
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
        eventEndsAtTick = tick + 20L * HalloweenConfig.EVENT_DURATION_SECONDS.get();
        nextWaveAtTick = tick + 20L * 3L;
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
        nextWaveAtTick = server.overworld().getGameTime() + 20L * HalloweenConfig.EVENT_INTERVAL_SECONDS.get();
        broadcastAllowed(server, "[HalloweenCore] " + ended + " skončil.");
        HalloweenCoreMod.LOGGER.info("Halloween event {} stopped.", ended);
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
            HalloweenMonster mob = type.create(level);
            if (mob == null) continue;
            mob.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
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
        List<String> ids = List.of("soulstorm", "witching_hour", "cursed_harvest",
                "blood_moon_invasion", "pumpkin_apocalypse", "graveyard_rising");
        return ids.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ids.size()));
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
        ServerLevel level = target.serverLevel();
        BlockPos position = findSpawnPos(level, target, 0);
        if (position == null) {
            source.sendFailure(Component.literal("Nebyla nalezena bezpečná spawnovací pozice."));
            return 0;
        }
        HalloweenMonster mob = type.create(level);
        if (mob == null) {
            source.sendFailure(Component.literal("Entitu se nepodařilo vytvořit."));
            return 0;
        }
        mob.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        mob.setTarget(target);
        mob.setCustomNameVisible(isBoss(id));
        if (!level.addFreshEntity(mob)) {
            source.sendFailure(Component.literal("Server odmítl vložit entitu do světa."));
            return 0;
        }
        final ServerPlayer spawnedFor = target;
        source.sendSuccess(() -> Component.literal(
                "Spawnuto: " + id + " poblíž " + spawnedFor.getGameProfile().getName()), true);
        return 1;
    }

    private static int giveItem(CommandSourceStack source, ServerPlayer player, String requested) {
        if (!HalloweenWorldPolicy.isAllowed(player.level())) {
            source.sendFailure(Component.literal("Předmět nebyl předán: hráč je v zakázaném/nepovoleném světě."));
            return 0;
        }
        Item item = switch (requested.toLowerCase(Locale.ROOT)) {
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
        if (item == null) {
            source.sendFailure(Component.literal("Neznámý předmět. Zkontroluj ID předmětu."));
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        source.sendSuccess(() -> Component.literal(
                "Předáno " + requested + " hráči " + player.getGameProfile().getName()), true);
        return 1;
    }
}
