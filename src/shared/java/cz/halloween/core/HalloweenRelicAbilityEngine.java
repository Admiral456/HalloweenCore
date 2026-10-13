package cz.halloween.core;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared implementation for the two Halloween relics. There is no compile-time
 * ItemsAdder dependency, so the same source can be compiled into the main plugin
 * and the portable relic plugin.
 */
public final class HalloweenRelicAbilityEngine implements Listener {
    private static final Set<String> HALLOWEEN_TAGS = Set.of(
            "cursed_mob", "event_mob", "headless_nick_rider", "headless_nick_horse",
            "goblin_king_boss", "goblin_king_minion");
    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> sensePulseAt = new HashMap<>();
    private BukkitTask passiveTask;
    private boolean registered;

    public HalloweenRelicAbilityEngine(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("relics.enabled", true)) return;
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        if (passiveTask != null) passiveTask.cancel();
        passiveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPassives, 40L, 40L);
        plugin.getLogger().info("Halloween relic abilities enabled (Batwing Elytra / Nocturne Sovereign Cowl).");
    }

    public void stop() {
        if (passiveTask != null) {
            passiveTask.cancel();
            passiveTask = null;
        }
        cooldowns.clear();
        sensePulseAt.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!eligible(player) || !plugin.getConfig().getBoolean("relics.enabled", true)) return;
        boolean wearingElytra = isBatwingElytra(player.getInventory().getChestplate());
        boolean wearingCowl = isSovereignCowl(player.getInventory().getHelmet());

        // While gliding: F = Surge, Shift+F = Echo. The offhand swap is intercepted only here.
        if (wearingElytra && player.isGliding()) {
            event.setCancelled(true);
            if (player.isSneaking()) activateEcho(player);
            else activateSurge(player);
            return;
        }
        // When the cowl is worn and the player is not gliding: Shift+F = Veilstep.
        if (wearingCowl && player.isSneaking()) {
            event.setCancelled(true);
            activateVeilstep(player);
        }
    }

    private void tickPassives() {
        if (!plugin.getConfig().getBoolean("relics.enabled", true)) return;
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!eligible(player)) continue;
            if (isBatwingElytra(player.getInventory().getChestplate()) && player.isGliding()) {
                double multiplier = clamp(plugin.getConfig().getDouble(
                        "relics.elytra.midnight-glide-speed-multiplier", 1.02D), 1.0D, 1.04D);
                double maxSpeed = clamp(plugin.getConfig().getDouble(
                        "relics.elytra.max-glide-velocity", 2.2D), 0.5D, 2.5D);
                Vector velocity = player.getVelocity();
                if (velocity.lengthSquared() > 0.04D && velocity.length() < maxSpeed) {
                    velocity.multiply(multiplier);
                    if (velocity.length() > maxSpeed) velocity.normalize().multiply(maxSpeed);
                    player.setVelocity(velocity);
                }
            }
            if (!isSovereignCowl(player.getInventory().getHelmet())) continue;
            if (plugin.getConfig().getBoolean("relics.cowl.nocturnal-perception-enabled", true)) {
                int seconds = (int) clamp(plugin.getConfig().getDouble(
                        "relics.cowl.night-vision-seconds", 6.0D), 2.0D, 12.0D);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        seconds * 20, 0, true, false, true));
            }
            pulseNearbyHalloweenTargets(player, now);
        }
    }

    private void pulseNearbyHalloweenTargets(Player player, long now) {
        long last = sensePulseAt.getOrDefault(player.getUniqueId(), 0L);
        long interval = Math.max(1000L, plugin.getConfig().getLong(
                "relics.cowl.sense-pulse-interval-seconds", 4L) * 1000L);
        if (now - last < interval) return;
        sensePulseAt.put(player.getUniqueId(), now);
        double radius = clamp(plugin.getConfig().getDouble(
                "relics.cowl.sense-radius-blocks", 12.0D), 3.0D, 24.0D);
        int limit = (int) clamp(plugin.getConfig().getDouble(
                "relics.cowl.sense-max-targets", 3.0D), 1.0D, 6.0D);
        int duration = (int) clamp(plugin.getConfig().getDouble(
                "relics.cowl.sense-glow-ticks", 30.0D), 10.0D, 80.0D);
        Location center = player.getLocation();
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && living != player && !living.isDead()
                    && living.isValid() && hasHalloweenTag(living)) targets.add(living);
        }
        targets.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(center)));
        int count = 0;
        for (LivingEntity target : targets) {
            if (count++ >= limit) break;
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, true, false, false));
            target.getWorld().spawnParticle(Particle.ASH, target.getLocation().add(0, 1.0D, 0),
                    5, 0.22D, 0.35D, 0.22D, 0.005D);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 1.0D, 0),
                    8, 0.6D, 0.45D, 0.6D, 0.015D);
        }
    }

    private void activateSurge(Player player) {
        long cooldown = Math.max(1L, plugin.getConfig().getLong(
                "relics.elytra.surge-cooldown-seconds", 10L)) * 1000L;
        if (!tryCooldown(player, "nightwing-surge", cooldown)) return;
        double boost = clamp(plugin.getConfig().getDouble(
                "relics.elytra.surge-velocity", 0.62D), 0.15D, 0.9D);
        double maxSpeed = clamp(plugin.getConfig().getDouble(
                "relics.elytra.max-surge-velocity", 2.35D), 0.7D, 2.6D);
        Vector velocity = player.getVelocity().add(player.getLocation().getDirection().normalize().multiply(boost));
        velocity.setY(clamp(velocity.getY(), -1.25D, 1.25D));
        if (velocity.length() > maxSpeed) velocity.normalize().multiply(maxSpeed);
        player.setVelocity(velocity);
        Location at = player.getLocation();
        at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 20, 0.25D, 0.3D, 0.25D, 0.025D);
        at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at, 12, 0.35D, 0.25D, 0.35D, 0.04D);
        at.getWorld().playSound(at, "minecraft:entity.bat.takeoff", 0.9f, 0.75f);
        actionBar(player, "Nightwing Surge");
    }

    private void activateEcho(Player player) {
        long cooldown = Math.max(1L, plugin.getConfig().getLong(
                "relics.elytra.echo-cooldown-seconds", 20L)) * 1000L;
        if (!tryCooldown(player, "bat-echo", cooldown)) return;
        double radius = clamp(plugin.getConfig().getDouble(
                "relics.elytra.echo-radius-blocks", 18.0D), 4.0D, 32.0D);
        int duration = (int) clamp(plugin.getConfig().getDouble(
                "relics.elytra.echo-duration-ticks", 50.0D), 10.0D, 100.0D);
        int limit = (int) clamp(plugin.getConfig().getDouble(
                "relics.elytra.echo-max-targets", 8.0D), 1.0D, 16.0D);
        Location center = player.getLocation();
        center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 1.0D, 0),
                64, radius / 3.0D, 0.55D, radius / 3.0D, 0.02D);
        center.getWorld().playSound(center, "minecraft:entity.bat.ambient", 1.0f, 0.65f);
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && living != player && !living.isDead() && living.isValid()
                    && (living instanceof Monster || hasHalloweenTag(living))
                    && living.getLocation().distanceSquared(center) <= radius * radius) targets.add(living);
        }
        targets.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(center)));
        int count = 0;
        for (LivingEntity target : targets) {
            if (count++ >= limit) break;
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, true, false, false));
            target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1.0D, 0),
                    6, 0.25D, 0.35D, 0.25D, 0.015D);
        }
        actionBar(player, "Bat Echo • " + Math.min(targets.size(), limit) + " cílů");
    }

    private void activateVeilstep(Player player) {
        long cooldown = Math.max(1L, plugin.getConfig().getLong(
                "relics.cowl.veilstep-cooldown-seconds", 12L)) * 1000L;
        if (!tryCooldown(player, "veilstep", cooldown)) return;
        Vector direction = player.getLocation().getDirection().setY(0.0D);
        if (direction.lengthSquared() < 0.001D) direction = new Vector(0, 0, 1);
        else direction.normalize();
        double boost = clamp(plugin.getConfig().getDouble(
                "relics.cowl.veilstep-velocity", 0.58D), 0.2D, 0.9D);
        Vector velocity = player.getVelocity().multiply(0.65D).add(direction.multiply(boost));
        velocity.setY(clamp(Math.max(velocity.getY(), 0.12D), 0.12D, 0.4D));
        if (velocity.length() > 1.45D) velocity.normalize().multiply(1.45D);
        player.setVelocity(velocity);
        int speedTicks = (int) clamp(plugin.getConfig().getDouble(
                "relics.cowl.veilstep-speed-ticks", 30.0D), 10.0D, 60.0D);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedTicks, 1, true, false, true));
        Location at = player.getLocation();
        at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1.0D, 0),
                28, 0.42D, 0.55D, 0.42D, 0.04D);
        at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 8, 0.25D, 0.3D, 0.25D, 0.015D);
        at.getWorld().playSound(at, "minecraft:entity.enderman.teleport", 0.75f, 1.4f);
        actionBar(player, "Veilstep");
    }

    /** Only HalloweenCore-owned mob debuffs routed through this method are reduced. */
    public void applyCurseEffect(Player target, PotionEffect effect) {
        if (target == null || effect == null) return;
        if (!plugin.getConfig().getBoolean("relics.enabled", true)
                || !plugin.getConfig().getBoolean("relics.cowl.curseguard-enabled", true)
                || !isSovereignCowl(target.getInventory().getHelmet())) {
            target.addPotionEffect(effect);
            return;
        }
        double multiplier = clamp(plugin.getConfig().getDouble(
                "relics.cowl.curse-duration-multiplier", 0.65D), 0.1D, 1.0D);
        int duration = effect.getDuration() < 0 ? effect.getDuration()
                : Math.max(1, (int) Math.round(effect.getDuration() * multiplier));
        int amplifier = effect.getAmplifier();
        if (plugin.getConfig().getBoolean("relics.cowl.reduce-curse-amplifier", true) && amplifier > 0) amplifier--;
        target.addPotionEffect(new PotionEffect(effect.getType(), duration, amplifier,
                effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
    }

    private boolean tryCooldown(Player player, String ability, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Map<String, Long> byAbility = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long last = byAbility.getOrDefault(ability, 0L);
        if (now - last < cooldownMillis) {
            long remaining = Math.max(1L, (cooldownMillis - (now - last) + 999L) / 1000L);
            actionBar(player, "Schopnost se obnoví za " + remaining + " s");
            return false;
        }
        byAbility.put(ability, now);
        return true;
    }

    private boolean eligible(Player player) {
        if (player == null || !player.isOnline() || player.isDead() || player.isInvulnerable()) return false;
        if (!plugin.getConfig().getBoolean("relics.require-survival", true)) return true;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return false;
        List<String> allowed = plugin.getConfig().getStringList("relics.allowed-worlds");
        if (allowed.isEmpty()) return true;
        boolean listed = allowed.stream().anyMatch(name -> name.equalsIgnoreCase(player.getWorld().getName()));
        return "WHITELIST".equalsIgnoreCase(plugin.getConfig().getString("relics.world-mode", "BLACKLIST"))
                ? listed : !listed;
    }

    private boolean isBatwingElytra(ItemStack item) {
        return isRelic(item, "batwing-elytra", "batwing_elytra");
    }

    private boolean isSovereignCowl(ItemStack item) {
        return isRelic(item, "nocturne-sovereign-cowl", "nocturne_sovereign_cowl");
    }

    private boolean isRelic(ItemStack item, String rewardId, String itemId) {
        if (item == null || item.getType().isAir()) return false;
        if (item.getItemMeta() != null) {
            PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
            for (NamespacedKey key : data.getKeys()) {
                if ("halloween_reward".equals(key.getKey())
                        && rewardId.equalsIgnoreCase(data.get(key, PersistentDataType.STRING))) return true;
            }
        }
        String customId = itemsAdderId(item);
        return customId != null && customId.toLowerCase(Locale.ROOT).endsWith(":" + itemId);
    }

    private String itemsAdderId(ItemStack item) {
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method byItemStack = customStack.getMethod("byItemStack", ItemStack.class);
            Object instance = byItemStack.invoke(null, item);
            if (instance == null) return null;
            Object id = customStack.getMethod("getNamespacedID").invoke(instance);
            return id instanceof String value ? value : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private boolean hasHalloweenTag(LivingEntity entity) {
        for (NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
            if (HALLOWEEN_TAGS.contains(key.getKey())) return true;
        }
        return false;
    }

    private void actionBar(Player player, String message) {
        player.sendActionBar(Component.text(message));
    }

    private double clamp(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { cleanup(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) { cleanup(event.getEntity().getUniqueId()); }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) { cleanup(event.getPlayer().getUniqueId()); }

    private void cleanup(UUID playerId) {
        cooldowns.remove(playerId);
        sensePulseAt.remove(playerId);
    }
}
