package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Night-time Headless Nick stalker with a separate horse entity, target-only messages,
 * readable attack telegraphs, bounded lifetime, cleanup and an optional ModelEngine/
 * MythicMobs integration seam. Final headless rider/horse models are external assets.
 */
public final class HalloweenHeadlessNickManager implements Listener {
    private static final String RIDER_MODEL_ID = "headless-nick";
    private static final String HORSE_MODEL_ID = "headless-nick-horse";

    private final HalloweenCore plugin;
    private final NamespacedKey riderKey;
    private final NamespacedKey horseKey;
    private final Map<UUID, Encounter> encounters = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask task;
    private long nextSpawnCheck;

    public HalloweenHeadlessNickManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.riderKey = new NamespacedKey(plugin, "headless_nick_rider");
        this.horseKey = new NamespacedKey(plugin, "headless_nick_horse");
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Encounter encounter : new ArrayList<>(encounters.values())) cleanup(encounter, false);
    }

    public boolean spawnForAdmin(Player target) {
        if (target == null || !target.isOnline() || !plugin.isEligibleGameplayWorld(target.getWorld())) return false;
        if (hasEncounterInWorld(target.getWorld())) return false;
        Location location = findSpawnLocation(target, 8, 14);
        return location != null && spawnEncounter(target, location);
    }

    public String statusLine() {
        return "Headless Nick: " + encounters.size() + " active encounter(s)";
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (Encounter encounter : new ArrayList<>(encounters.values())) {
            if (encounter.targetId.equals(event.getPlayer().getUniqueId())) cleanup(encounter, false);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        for (Encounter encounter : new ArrayList<>(encounters.values())) {
            if (encounter.rider.getUniqueId().equals(entityId) || encounter.horse.getUniqueId().equals(entityId)) {
                cleanup(encounter, true);
            }
        }
    }

    private void tick() {
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("headless-nick.enabled", true)) {
            for (Encounter encounter : new ArrayList<>(encounters.values())) cleanup(encounter, false);
            return;
        }

        long now = System.currentTimeMillis();
        for (Encounter encounter : new ArrayList<>(encounters.values())) tickEncounter(encounter, now);

        long interval = Math.max(40L, plugin.getConfig().getLong("headless-nick.check-interval-ticks", 200L)) * 50L;
        if (now < nextSpawnCheck) return;
        nextSpawnCheck = now + interval;

        List<Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOnline)
                .filter(plugin::isEligibleGameplayPlayer)
                .filter(player -> plugin.isAutomaticHalloweenWorld(player.getWorld()))
                .filter(this::isNight)
                .filter(player -> !hasEncounterInWorld(player.getWorld()))
                .toList();
        if (targets.isEmpty()) return;

        double chance = Math.max(0.0D, Math.min(1.0D,
                plugin.getConfig().getDouble("headless-nick.chance-per-check", 0.10D)));
        if (random.nextDouble() >= chance) return;

        Player target = targets.get(random.nextInt(targets.size()));
        int minDistance = Math.max(8, plugin.getConfig().getInt("headless-nick.min-spawn-distance-blocks", 18));
        int maxDistance = Math.max(minDistance, Math.min(48,
                plugin.getConfig().getInt("headless-nick.max-spawn-distance-blocks", 28)));
        Location location = findSpawnLocation(target, minDistance, maxDistance);
        if (location != null) spawnEncounter(target, location);
    }

    private boolean isNight(Player player) {
        long time = player.getWorld().getTime();
        return time >= 13000L && time <= 23000L;
    }

    private boolean hasEncounterInWorld(World world) {
        return encounters.values().stream().anyMatch(encounter -> encounter.worldId.equals(world.getUID()));
    }

    private Location findSpawnLocation(Player target, int minimumDistance, int maximumDistance) {
        World world = target.getWorld();
        Location origin = target.getLocation();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int distance = minimumDistance + random.nextInt(Math.max(1, maximumDistance - minimumDistance + 1));
            int x = (int) Math.floor(origin.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(origin.getZ() + Math.sin(angle) * distance);
            int groundY = world.getHighestBlockYAt(x, z);
            if (groundY <= world.getMinHeight() + 1 || groundY + 3 >= world.getMaxHeight()) continue;

            Location candidate = new Location(world, x + 0.5D, groundY + 1.0D, z + 0.5D,
                    (float) Math.toDegrees(angle) + 180.0f, 0.0f);
            if (!world.getWorldBorder().isInside(candidate)) continue;
            if (world.getBlockAt(x, groundY, z).isLiquid()) continue;
            if (!candidate.getBlock().isPassable()
                    || !candidate.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable()) continue;
            if (!candidate.clone().add(0.0D, 2.0D, 0.0D).getBlock().isPassable()) continue;
            if (candidate.distanceSquared(origin) < (double) minimumDistance * minimumDistance) continue;
            return candidate;
        }
        return null;
    }

    private boolean spawnEncounter(Player target, Location location) {
        LivingEntity horse = plugin.getMobManager().spawnMythicModelMob(location, HORSE_MODEL_ID);
        if (horse == null) {
            Entity base = location.getWorld().spawnEntity(location, EntityType.SKELETON_HORSE);
            if (!(base instanceof LivingEntity living)) {
                base.remove();
                return false;
            }
            horse = living;
        }

        LivingEntity rider = plugin.getMobManager().spawnMythicModelMob(location.clone().add(0.0D, 1.0D, 0.0D), RIDER_MODEL_ID);
        if (rider == null) {
            Entity base = location.getWorld().spawnEntity(location.clone().add(0.0D, 1.0D, 0.0D), EntityType.SKELETON);
            if (!(base instanceof LivingEntity living)) {
                base.remove();
                horse.remove();
                return false;
            }
            rider = living;
        }

        try {
            if (horse instanceof Mob mob) mob.setAI(false);
            if (rider instanceof Mob mob) {
                mob.setAI(false);
                mob.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                mob.getEquipment().setItemInMainHandDropChance(0.0f);
            }
            horse.setCustomName(plugin.color("&8&lKŮŇ BEZHLAVÉHO NICKA"));
            horse.setCustomNameVisible(false);
            rider.setCustomName(plugin.color("&4&lBEZHLAVÝ NICK"));
            rider.setCustomNameVisible(true);
            horse.setSilent(true);
            rider.setSilent(true);
            horse.getPersistentDataContainer().set(horseKey, PersistentDataType.BYTE, (byte) 1);
            rider.getPersistentDataContainer().set(riderKey, PersistentDataType.BYTE, (byte) 1);
            if (!horse.addPassenger(rider)) {
                rider.remove();
                horse.remove();
                return false;
            }
        } catch (RuntimeException ex) {
            rider.remove();
            horse.remove();
            plugin.getLogger().warning("Headless Nick encounter could not mount rider on horse: " + ex.getClass().getSimpleName());
            return false;
        }

        long lifetime = Math.max(20L, plugin.getConfig().getLong("headless-nick.lifetime-seconds", 90L)) * 1000L;
        Encounter encounter = new Encounter(target.getUniqueId(), location.getWorld().getUID(), horse, rider,
                System.currentTimeMillis() + lifetime);
        encounters.put(rider.getUniqueId(), encounter);

        // These messages are intentionally private to the selected victim and sent once per encounter.
        target.sendMessage(plugin.color("&4&lBEZHLAVÝ NICK &8» &cVidím tě."));
        encounter.seenMessageSent = true;
        target.playSound(target.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 0.8f, 0.55f);
        target.getWorld().spawnParticle(Particle.ASH, target.getLocation().clone().add(0.0D, 1.0D, 0.0D),
                24, 0.9D, 0.7D, 0.9D, 0.025D);
        plugin.getLogger().info("Headless Nick encounter started for " + target.getName()
                + " in world " + target.getWorld().getName() + ". Model assets available="
                + (plugin.getServer().getPluginManager().isPluginEnabled("MythicMobs")
                && plugin.getServer().getPluginManager().isPluginEnabled("ModelEngine")) + ".");
        return true;
    }

    private void tickEncounter(Encounter encounter, long now) {
        Player target = Bukkit.getPlayer(encounter.targetId);
        if (target == null || !target.isOnline()
                || !plugin.isEligibleGameplayPlayer(target)
                || !plugin.isEligibleGameplayWorld(target.getWorld())
                || !target.getWorld().getUID().equals(encounter.worldId)
                || !encounter.horse.isValid() || encounter.horse.isDead()
                || !encounter.rider.isValid() || encounter.rider.isDead()
                || now >= encounter.expiresAt) {
            cleanup(encounter, true);
            return;
        }

        long elapsed = now - encounter.createdAt;
        if (!encounter.stalkingMessageSent && elapsed >= 12_000L) {
            target.sendMessage(plugin.color("&4&lBEZHLAVÝ NICK &8» &cSleduji tě."));
            encounter.stalkingMessageSent = true;
        }

        Location horseLocation = encounter.horse.getLocation();
        Location targetLocation = target.getLocation();
        Vector direction = targetLocation.toVector().subtract(horseLocation.toVector());
        double distance = direction.length();
        Vector horizontal = direction.clone().setY(0.0D);
        if (horizontal.lengthSquared() > 0.01D) {
            horizontal.normalize();
            if (distance > 2.8D) {
                encounter.horse.setVelocity(horizontal.multiply(0.30D));
                encounter.horse.setRotation((float) Math.toDegrees(Math.atan2(-horizontal.getX(), horizontal.getZ())), 0.0f);
            } else {
                encounter.horse.setVelocity(new Vector(0.0D, encounter.horse.getVelocity().getY(), 0.0D));
            }
        }

        if (distance <= 3.1D && Math.abs(targetLocation.getY() - horseLocation.getY()) <= 3.0D) {
            processAttackTelegraph(encounter, target, now);
        } else {
            encounter.telegraphPending = false;
        }
    }

    private void processAttackTelegraph(Encounter encounter, Player target, long now) {
        if (!encounter.telegraphPending) {
            if (now < encounter.nextAttackAt) return;
            encounter.telegraphPending = true;
            encounter.attackAt = now + 900L;
            target.sendActionBar(plugin.color("&4Nick pozvedá čepel — uhni!"));
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().clone().add(0.0D, 1.0D, 0.0D),
                    18, 0.35D, 0.55D, 0.35D, 0.06D);
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.75f, 0.55f);
            return;
        }
        if (now < encounter.attackAt) return;

        double damage = Math.max(1.0D, Math.min(12.0D, plugin.getConfig().getDouble("headless-nick.attack-damage", 5.0D)));
        target.damage(damage, encounter.rider);
        Vector knockback = target.getLocation().toVector().subtract(encounter.horse.getLocation().toVector()).setY(0.0D);
        if (knockback.lengthSquared() > 0.001D) target.setVelocity(knockback.normalize().multiply(0.55D).setY(0.20D));
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().clone().add(0.0D, 1.0D, 0.0D),
                12, 0.35D, 0.4D, 0.35D, 0.02D);
        encounter.telegraphPending = false;
        encounter.nextAttackAt = now + Math.max(1000L,
                plugin.getConfig().getLong("headless-nick.attack-cooldown-ms", 2500L));
    }

    private void cleanup(Encounter encounter, boolean notifyTarget) {
        encounters.remove(encounter.rider.getUniqueId());
        Player target = Bukkit.getPlayer(encounter.targetId);
        if (notifyTarget && target != null && target.isOnline()) {
            target.sendMessage(plugin.color("&8Z dálky se ozve zaržání. Bezhlavý Nick zmizel ve tmě."));
        }
        if (encounter.rider.isValid()) encounter.rider.remove();
        if (encounter.horse.isValid()) encounter.horse.remove();
    }

    private static final class Encounter {
        private final UUID targetId;
        private final UUID worldId;
        private final LivingEntity horse;
        private final LivingEntity rider;
        private final long createdAt = System.currentTimeMillis();
        private final long expiresAt;
        private boolean seenMessageSent;
        private boolean stalkingMessageSent;
        private boolean telegraphPending;
        private long attackAt;
        private long nextAttackAt;

        private Encounter(UUID targetId, UUID worldId, LivingEntity horse, LivingEntity rider, long expiresAt) {
            this.targetId = targetId;
            this.worldId = worldId;
            this.horse = horse;
            this.rider = rider;
            this.expiresAt = expiresAt;
        }
    }
}
