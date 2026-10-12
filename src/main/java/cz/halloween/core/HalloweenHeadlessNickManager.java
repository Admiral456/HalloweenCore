package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Headless Nick is a separate, private-target stalker encounter. The custom rider
 * is supplied by the optional MythicMobs/ModelEngine pack; a vanilla wither-skeleton
 * rider and vanilla skeleton horse remain a usable fallback.
 */
public final class HalloweenHeadlessNickManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey nickKey;
    private final NamespacedKey horseKey;
    private UUID riderId;
    private UUID horseId;
    private UUID targetId;
    private BukkitTask brainTask;
    private BukkitTask naturalSpawnTask;
    private long nextAttackAt;
    private long nextMessageAt;
    private long lastNaturalSpawnAt;
    private long lastSafeRegionWarningAt;
    private String lastState = "neaktivní";
    private boolean stopping;
    private boolean testEncounter;

    public HalloweenHeadlessNickManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.nickKey = new NamespacedKey(plugin, "headless_nick_rider");
        this.horseKey = new NamespacedKey(plugin, "headless_nick_horse");
    }

    public void start() {
        removeLoadedOrphans();
        if (naturalSpawnTask == null) {
            naturalSpawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::maybeNaturalSpawn, 100L, 100L);
        }
    }

    public boolean isActive() {
        Entity rider = riderId == null ? null : Bukkit.getEntity(riderId);
        Entity horse = horseId == null ? null : Bukkit.getEntity(horseId);
        return rider instanceof LivingEntity living && living.isValid() && !living.isDead()
                && horse instanceof SkeletonHorse mount && mount.isValid() && !mount.isDead();
    }

    public String statusText() {
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        return "Bezhlavý Nick " + (isActive() ? "aktivní" : "neaktivní")
                + " | cíl=" + (target == null ? "žádný" : target.getName())
                + " | stav=" + lastState
                + " | test=" + testEncounter
                + " | zprávy pouze soukromě";
    }

    public boolean spawnFor(Player requestedTarget, boolean testOnly) {
        if (isActive() || !plugin.getConfig().getBoolean("stalkers.headless-nick.enabled", true)
                || requestedTarget == null || !requestedTarget.isOnline()
                || !plugin.isEligibleGameplayPlayer(requestedTarget) || isSafeZone(requestedTarget)) return false;
        if (!testOnly && plugin.getConfig().getBoolean("stalkers.headless-nick.night-only", true)
                && !isNight(requestedTarget.getWorld())) return false;

        Location spawn = findSpawnLocation(requestedTarget);
        if (spawn == null) return false;
        World world = spawn.getWorld();
        if (world == null || !world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) return false;

        Entity mountEntity = world.spawnEntity(spawn, EntityType.SKELETON_HORSE);
        if (!(mountEntity instanceof SkeletonHorse horse)) {
            mountEntity.remove();
            return false;
        }
        horse.setPersistent(true);
        horse.setRemoveWhenFarAway(false);
        horse.setCustomNameVisible(false);
        horse.setCanPickupItems(false);
        horse.getPersistentDataContainer().set(horseKey, PersistentDataType.BYTE, (byte) 1);

        LivingEntity rider = plugin.getMobManager().spawnEncounterEntity(
                spawn.clone().add(0.0D, 1.0D, 0.0D), "headless-nick", EntityType.WITHER_SKELETON);
        if (rider == null) {
            horse.remove();
            return false;
        }
        rider.getPersistentDataContainer().set(nickKey, PersistentDataType.BYTE, (byte) 1);
        rider.setCustomName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "BEZHLAVÝ NICK");
        rider.setCustomNameVisible(false);
        rider.setPersistent(true);
        rider.setRemoveWhenFarAway(false);
        rider.setCanPickupItems(false);
        AttributeInstance maxHealth = rider.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.max(20.0D,
                    plugin.getConfig().getDouble("stalkers.headless-nick.health", 120.0D)));
            rider.setHealth(maxHealth.getValue());
        }
        AttributeInstance attack = rider.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(Math.max(1.0D,
                plugin.getConfig().getDouble("stalkers.headless-nick.damage", 10.0D)));
        EntityEquipment equipment = rider.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            equipment.setItemInMainHandDropChance(0.0F);
        }
        if (!horse.addPassenger(rider)) {
            rider.remove();
            horse.remove();
            return false;
        }

        riderId = rider.getUniqueId();
        horseId = horse.getUniqueId();
        targetId = requestedTarget.getUniqueId();
        testEncounter = testOnly;
        nextAttackAt = System.currentTimeMillis() + 5_000L;
        nextMessageAt = 0L;
        lastState = "stalkuje " + requestedTarget.getName();
        stopping = false;
        requestedTarget.sendMessage("Vidím tě.");
        nextMessageAt = System.currentTimeMillis() + Math.max(20L,
                plugin.getConfig().getLong("stalkers.headless-nick.message-interval-seconds", 45L)) * 1000L;
        brainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickStalker, 10L, 10L);
        return true;
    }

    public boolean assignTarget(Player target) {
        if (!isActive() || target == null || !target.isOnline()
                || !plugin.isEligibleGameplayPlayer(target) || isSafeZone(target)) return false;
        targetId = target.getUniqueId();
        nextMessageAt = 0L;
        target.sendMessage("Vidím tě.");
        nextMessageAt = System.currentTimeMillis() + Math.max(20L,
                plugin.getConfig().getLong("stalkers.headless-nick.message-interval-seconds", 45L)) * 1000L;
        lastState = "přiřazen cíl " + target.getName();
        return true;
    }

    public boolean forceAttack() {
        LivingEntity rider = currentRider();
        SkeletonHorse horse = currentHorse();
        Player target = currentTarget();
        if (rider == null || horse == null || target == null
                || !rider.getWorld().getUID().equals(target.getWorld().getUID())
                || !rider.hasLineOfSight(target)) return false;
        spectralCharge(rider, horse, target, true);
        return true;
    }

    private void maybeNaturalSpawn() {
        if (stopping || isActive()
                || !plugin.getConfig().getBoolean("stalkers.headless-nick.natural-spawn.enabled", true)
                || !plugin.isEventEnabled()
                || System.currentTimeMillis() - lastNaturalSpawnAt
                < Math.max(60L, plugin.getConfig().getLong("stalkers.headless-nick.natural-spawn.cooldown-seconds", 720L)) * 1000L) {
            return;
        }
        double chance = Math.max(0.0D, Math.min(0.25D,
                plugin.getConfig().getDouble("stalkers.headless-nick.natural-spawn.chance-per-check", 0.012D)));
        if (Math.random() >= chance) return;

        List<Player> candidates = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player) || player.isDead()
                    || !isNight(player.getWorld()) || isSafeZone(player)) continue;
            if (!plugin.isEligibleGameplayWorld(player.getWorld())) continue;
            candidates.add(player);
        }
        if (candidates.isEmpty()) return;
        Player chosen = candidates.get((int) (Math.random() * candidates.size()));
        if (spawnFor(chosen, false)) {
            lastNaturalSpawnAt = System.currentTimeMillis();
            plugin.getLogger().info("Headless Nick has begun a stalking encounter near an eligible player.");
        }
    }

    private void tickStalker() {
        if (stopping || riderId == null || horseId == null) return;
        LivingEntity rider = currentRider();
        SkeletonHorse horse = currentHorse();
        if (rider == null || horse == null || !rider.isValid() || rider.isDead()
                || !horse.isValid() || horse.isDead()) {
            endEncounter(true);
            return;
        }
        Player target = currentTarget();
        if (target == null || !target.isOnline() || target.isDead()
                || !plugin.isEligibleGameplayPlayer(target) || !target.getWorld().getUID().equals(horse.getWorld().getUID())
                || isSafeZone(target)) {
            if (target != null && isSafeZone(target)) {
                targetId = null;
                lastState = "ustoupil do bezpečné zóny";
            } else if (target != null) {
                targetId = null;
                lastState = "ztratil cíl";
            }
            Player replacement = nearestEligibleTarget(horse);
            if (replacement == null) {
                retreatOrDespawn(horse, rider);
                return;
            }
            targetId = replacement.getUniqueId();
            nextMessageAt = 0L;
            replacement.sendMessage("Vidím tě.");
            nextMessageAt = System.currentTimeMillis() + Math.max(20L,
                    plugin.getConfig().getLong("stalkers.headless-nick.message-interval-seconds", 45L)) * 1000L;
            lastState = "nový cíl " + replacement.getName();
            target = replacement;
        }

        if (plugin.getConfig().getBoolean("stalkers.headless-nick.night-only", true)
                && !isNight(target.getWorld())) {
            lastState = "čeká ve stínu na noc";
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= nextMessageAt) {
            target.sendMessage("Sleduji tě.");
            nextMessageAt = now + Math.max(20L,
                    plugin.getConfig().getLong("stalkers.headless-nick.message-interval-seconds", 45L)) * 1000L;
        }

        double distance = horse.getLocation().distance(target.getLocation());
        boolean lineOfSight = rider.hasLineOfSight(target);
        if (!lineOfSight) {
            lastState = "obchází překážku";
            // Never teleport through a wall. The horse uses native pathfinding if exposed by this server.
            if (horse instanceof Mob horseMob) {
                horseMob.getPathfinder().moveTo(target, 1.0D);
            }
            return;
        }

        if (distance > plugin.getConfig().getDouble("stalkers.headless-nick.ideal-stalk-distance", 12.0D)) {
            lastState = "přibližuje se";
            if (horse instanceof Mob horseMob) {
                horseMob.getPathfinder().moveTo(target, plugin.getConfig().getDouble("stalkers.headless-nick.movement-speed", 1.05D));
            } else if (distance < 28.0D) {
                Vector direction = target.getLocation().toVector().subtract(horse.getLocation().toVector());
                direction.setY(0.0D);
                if (direction.lengthSquared() > 0.01D) horse.setVelocity(direction.normalize().multiply(0.18D));
            }
        } else if (distance > 6.0D) {
            lastState = "vyčkává na přepad";
        } else {
            lastState = "v dosahu útoku";
        }

        if (now < nextAttackAt || !lineOfSight) return;
        double chargeMax = plugin.getConfig().getDouble("stalkers.headless-nick.spectral-charge-range", 14.0D);
        if (distance >= 4.5D && distance <= chargeMax) {
            spectralCharge(rider, horse, target, false);
            nextAttackAt = now + Math.max(1L, plugin.getConfig().getLong(
                    "stalkers.headless-nick.spectral-charge-cooldown-seconds", 28L)) * 1000L;
        } else if (distance < 4.5D) {
            executionersStrike(rider, horse, target);
            nextAttackAt = now + Math.max(1L, plugin.getConfig().getLong(
                    "stalkers.headless-nick.executioner-cooldown-seconds", 20L)) * 1000L;
        } else if (distance < 18.0D) {
            chainsOfTheHeadless(rider, horse, target);
            nextAttackAt = now + Math.max(1L, plugin.getConfig().getLong(
                    "stalkers.headless-nick.chains-cooldown-seconds", 35L)) * 1000L;
        }
    }

    private void spectralCharge(LivingEntity rider, SkeletonHorse horse, Player target, boolean forced) {
        Location mark = target.getLocation().clone();
        World world = mark.getWorld();
        if (world == null) return;
        double radius = Math.max(1.5D, Math.min(5.0D,
                plugin.getConfig().getDouble("stalkers.headless-nick.charge-hit-radius", 3.0D)));
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, mark.clone().add(0.0D, 0.2D, 0.0D),
                40, 0.7D, 0.15D, 0.7D, 0.03D);
        world.playSound(mark, Sound.ENTITY_SKELETON_HORSE_GALLOP, 1.0F, 0.7F);
        target.sendTitle(ChatColor.DARK_RED + "KOPYTA!", ChatColor.GRAY + "Spectral Charge — uskoč stran.", 5, 18, 7);
        if (forced) target.sendMessage(ChatColor.GRAY + "Admin test: testuje se útok Spectral Charge.");
        long delay = Math.max(10L, Math.min(35L,
                plugin.getConfig().getLong("stalkers.headless-nick.charge-warning-ticks", 22L)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validStrike(rider, horse, target, world, mark)) return;
            world.spawnParticle(Particle.SWEEP_ATTACK, mark.clone().add(0.0D, 1.0D, 0.0D),
                    20, 0.8D, 0.5D, 0.8D, 0.0D);
            world.playSound(mark, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
            if (target.getLocation().distanceSquared(mark) <= radius * radius) {
                target.damage(plugin.getConfig().getDouble("stalkers.headless-nick.charge-damage", 10.0D), rider);
                Vector away = target.getLocation().toVector().subtract(horse.getLocation().toVector());
                away.setY(0);
                if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(0.95D).setY(0.35D));
            }
        }, delay);
    }

    private void executionersStrike(LivingEntity rider, SkeletonHorse horse, Player target) {
        Location mark = target.getLocation().clone();
        World world = mark.getWorld();
        if (world == null) return;
        mark.getWorld().spawnParticle(Particle.CRIT, mark.clone().add(0, 0.15D, 0),
                28, 0.65D, 0.1D, 0.65D, 0.02D);
        target.sendMessage(ChatColor.DARK_RED + "Nick zvedá popravčí čepel — uskoč!");
        world.playSound(mark, Sound.ENTITY_WITHER_SKELETON_HURT, 0.9F, 0.55F);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validStrike(rider, horse, target, world, mark)) return;
            if (target.getLocation().distanceSquared(mark) <= 2.4D * 2.4D) {
                target.damage(plugin.getConfig().getDouble("stalkers.headless-nick.executioner-damage", 9.0D), rider);
                target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.WEAKNESS, 40, 0, true, true, false));
            }
        }, 14L);
    }

    private void chainsOfTheHeadless(LivingEntity rider, SkeletonHorse horse, Player target) {
        Location mark = target.getLocation().clone();
        World world = mark.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.REVERSE_PORTAL, mark.clone().add(0, 0.8D, 0),
                32, 0.6D, 0.5D, 0.6D, 0.03D);
        target.sendMessage(ChatColor.DARK_GRAY + "Přízračné řetězy se stahují kolem tvých nohou!");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validStrike(rider, horse, target, world, mark)) return;
            if (target.getLocation().distanceSquared(mark) <= 10.0D * 10.0D) {
                target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 1, true, true, false));
                Vector toward = horse.getLocation().toVector().subtract(target.getLocation().toVector());
                toward.setY(0);
                if (toward.lengthSquared() > 0.001D) target.setVelocity(toward.normalize().multiply(0.25D).setY(0.12D));
            }
        }, 18L);
    }

    private boolean validStrike(LivingEntity rider, SkeletonHorse horse, Player target, World world, Location mark) {
        return !stopping && riderId != null && rider.isValid() && !rider.isDead()
                && horseId != null && horse.isValid() && !horse.isDead()
                && target.isOnline() && !target.isDead() && plugin.isEligibleGameplayPlayer(target)
                && targetId != null && targetId.equals(target.getUniqueId())
                && target.getWorld().getUID().equals(world.getUID()) && !isSafeZone(target)
                && world.isChunkLoaded(mark.getBlockX() >> 4, mark.getBlockZ() >> 4);
    }

    private Player nearestEligibleTarget(SkeletonHorse horse) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : horse.getWorld().getPlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player) || player.isDead() || isSafeZone(player)) continue;
            if (!plugin.isEligibleGameplayWorld(player.getWorld())) continue;
            double distance = player.getLocation().distanceSquared(horse.getLocation());
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Location findSpawnLocation(Player target) {
        World world = target.getWorld();
        int minimum = Math.max(5, plugin.getConfig().getInt("stalkers.headless-nick.spawn-distance-min", 10));
        int maximum = Math.max(minimum + 1, Math.min(32,
                plugin.getConfig().getInt("stalkers.headless-nick.spawn-distance-max", 19)));
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = Math.random() * Math.PI * 2.0D;
            double distance = minimum + Math.random() * (maximum - minimum);
            int x = target.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = target.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = target.getLocation().getBlockY();
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Location location = new Location(world, x + 0.5D, y, z + 0.5D);
            if (isSafeSpawnLocation(location) && !location.getBlock().isLiquid()
                    && location.getBlock().getType().isAir()
                    && location.clone().add(0, 1, 0).getBlock().isPassable()
                    && location.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                return location;
            }
        }
        return null;
    }

    private boolean isSafeSpawnLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        double radius = Math.max(0.0D, plugin.getConfig().getDouble("stalkers.headless-nick.safe-spawn-radius-blocks", 32.0D));
        return location.distanceSquared(world.getSpawnLocation()) > radius * radius;
    }

    private boolean isSafeZone(Player player) {
        if (!isSafeSpawnLocation(player.getLocation())) return true;
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) return false;
        try {
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object worldEditLocation = adapterClass.getMethod("adapt", Location.class).invoke(null, player.getLocation());
            Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object wgPlugin = wgPluginClass.getMethod("inst").invoke(null);
            Object localPlayer = wgPluginClass.getMethod("wrapPlayer", Player.class).invoke(wgPlugin, player);
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object pvpFlag = flagsClass.getField("PVP").get(null);
            for (Method method : query.getClass().getMethods()) {
                if (!method.getName().equals("testState") || method.getParameterCount() != 3
                        || !method.getParameterTypes()[0].isInstance(worldEditLocation)
                        || !method.getParameterTypes()[1].isInstance(localPlayer)
                        || !method.getParameterTypes()[2].isArray()) continue;
                Object flags = Array.newInstance(method.getParameterTypes()[2].getComponentType(), 1);
                Array.set(flags, 0, pvpFlag);
                Object answer = method.invoke(query, worldEditLocation, localPlayer, flags);
                return !Boolean.TRUE.equals(answer);
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (System.currentTimeMillis() - lastSafeRegionWarningAt > 60_000L) {
                plugin.getLogger().warning("[HeadlessNick] WorldGuard PVP state could not be checked; treating the location as safe: "
                        + exception.getClass().getSimpleName());
                lastSafeRegionWarningAt = System.currentTimeMillis();
            }
            return true;
        }
    }

    private boolean isNight(World world) {
        long time = world.getTime() % 24_000L;
        long start = plugin.getConfig().getLong("stalkers.headless-nick.night-start-tick", 13_000L);
        long end = plugin.getConfig().getLong("stalkers.headless-nick.night-end-tick", 23_000L);
        return start <= end ? time >= start && time <= end : time >= start || time <= end;
    }

    private void retreatOrDespawn(SkeletonHorse horse, LivingEntity rider) {
        long timeout = Math.max(30L, plugin.getConfig().getLong("stalkers.headless-nick.no-target-timeout-seconds", 180L));
        if (lastState.equals("bez cíle")) {
            if (System.currentTimeMillis() - lastNaturalSpawnAt > timeout * 1000L) endEncounter(true);
            return;
        }
        lastState = "bez cíle";
        if (horse instanceof Mob horseMob) horseMob.getPathfinder().moveTo(horse.getWorld().getSpawnLocation(), 1.0D);
        if (plugin.getConfig().getBoolean("stalkers.headless-nick.despawn-without-target", true)) {
            endEncounter(true);
        }
    }

    private LivingEntity currentRider() {
        Entity entity = riderId == null ? null : Bukkit.getEntity(riderId);
        return entity instanceof LivingEntity living ? living : null;
    }

    private SkeletonHorse currentHorse() {
        Entity entity = horseId == null ? null : Bukkit.getEntity(horseId);
        return entity instanceof SkeletonHorse horse ? horse : null;
    }

    private Player currentTarget() {
        return targetId == null ? null : Bukkit.getPlayer(targetId);
    }

    public void stopEncounter() {
        endEncounter(true);
    }

    private void endEncounter(boolean removeEntities) {
        stopping = true;
        if (brainTask != null) {
            brainTask.cancel();
            brainTask = null;
        }
        if (removeEntities) {
            Entity rider = riderId == null ? null : Bukkit.getEntity(riderId);
            Entity horse = horseId == null ? null : Bukkit.getEntity(horseId);
            if (rider != null && rider.isValid()) rider.remove();
            if (horse != null && horse.isValid()) horse.remove();
            removeLoadedOrphans();
        }
        riderId = null;
        horseId = null;
        targetId = null;
        testEncounter = false;
        lastState = "neaktivní";
        stopping = false;
    }

    private void removeLoadedOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity.getPersistentDataContainer().has(nickKey, PersistentDataType.BYTE)
                        || entity.getPersistentDataContainer().has(horseKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNickDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(nickKey, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            Entity horse = horseId == null ? null : Bukkit.getEntity(horseId);
            if (horse != null && horse.isValid()) horse.remove();
            endEncounter(false);
        } else if (entity.getPersistentDataContainer().has(horseKey, PersistentDataType.BYTE)) {
            Entity rider = riderId == null ? null : Bukkit.getEntity(riderId);
            if (rider != null && rider.isValid()) rider.remove();
            endEncounter(false);
        }
    }

    @EventHandler
    public void onTargetQuit(PlayerQuitEvent event) {
        if (targetId != null && targetId.equals(event.getPlayer().getUniqueId())) {
            targetId = null;
            lastState = "cílový hráč se odpojil";
        }
    }

    @EventHandler
    public void onTargetWorldChange(PlayerChangedWorldEvent event) {
        if (targetId != null && targetId.equals(event.getPlayer().getUniqueId())) {
            targetId = null;
            lastState = "cílový hráč změnil svět";
        }
    }

    @EventHandler
    public void onNickChunkUnload(ChunkUnloadEvent event) {
        if ((riderId != null && event.getChunk().getEntities().length >= 0)
                && chunkContainsNick(event.getChunk().getEntities())) {
            endEncounter(true);
        }
    }

    private boolean chunkContainsNick(Entity[] entities) {
        for (Entity entity : entities) {
            if ((riderId != null && riderId.equals(entity.getUniqueId()))
                    || (horseId != null && horseId.equals(entity.getUniqueId()))) return true;
        }
        return false;
    }

    public void shutdown() {
        if (naturalSpawnTask != null) {
            naturalSpawnTask.cancel();
            naturalSpawnTask = null;
        }
        endEncounter(true);
    }
}
