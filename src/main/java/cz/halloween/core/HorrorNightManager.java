package cz.halloween.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Independent HorrorNights-style implementation integrated into HalloweenCore; it never changes world weather or sky.
 * This file does not contain copied or decompiled HorrorNights code or assets.
 */
public final class HorrorNightManager implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey mobMarker;
    private final Random random = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastRolledNight = new HashMap<>();
    private BukkitTask task;
    private long ticks;
    private long lastScheduledCheck;
    private boolean warnedMythicMobsMissing;
    private final Set<String> warnedMissingMythicIds = new HashSet<>();

    private static final class Session {
        final UUID worldId;
        final long startedAt;
        final long maxEndAt;
        final boolean endAtDaybreak;
        long nextSpawnAt;
        long nextSoundAt;
        long nextEffectsAt;

        Session(World world, long startedAt, long maxDuration, boolean endAtDaybreak) {
            worldId = world.getUID();
            this.startedAt = startedAt;
            maxEndAt = startedAt + maxDuration;
            this.endAtDaybreak = endAtDaybreak;
            nextSpawnAt = startedAt;
            nextSoundAt = startedAt;
            nextEffectsAt = startedAt;
        }
    }

    public HorrorNightManager(JavaPlugin plugin) {
        this.plugin = plugin;
        mobMarker = new NamespacedKey(plugin, "horrornights_style_mob");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        lastScheduledCheck = ticks;
    }

    public void reload() {
        stopAllSessions();
        lastRolledNight.clear();
        start();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        stopAllSessions();
        removeTaggedMobs();
    }

    public List<String> getActiveWorldNames() {
        List<String> names = new ArrayList<>();
        for (Session session : sessions.values()) {
            World world = Bukkit.getWorld(session.worldId);
            if (world != null) names.add(world.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public boolean startManually(World world) {
        if (world == null || !plugin.getConfig().getBoolean("horrorNights.enabled", true)
                || sessions.containsKey(world.getUID()) || !isAllowedWorld(world)) return false;
        long timeOfDay = world.getTime();
        // Admin-triggered starts are a force-start and may be used for daytime testing too.
        // A session only follows the dawn cutoff if it actually began during the night.
        startSession(world, timeOfDay >= 13000L && timeOfDay <= 23000L);
        return sessions.containsKey(world.getUID());
    }

    public boolean stopWorld(World world) {
        return world != null && endSession(world.getUID(), true);
    }

    private void tick() {
        ticks += 20;
        if (!plugin.getConfig().getBoolean("horrorNights.enabled", true)) {
            if (!sessions.isEmpty()) stopAllSessions();
            return;
        }
        long interval = Math.max(20L, plugin.getConfig().getLong("horrorNights.check-interval-ticks", 6000L));
        if (ticks - lastScheduledCheck >= interval) {
            lastScheduledCheck = ticks;
            checkForRandomStarts();
        }
        for (Session session : new ArrayList<>(sessions.values())) {
            World world = Bukkit.getWorld(session.worldId);
            if (world == null || !isAllowedWorld(world)) {
                endSession(session.worldId, true);
                continue;
            }
            long timeOfDay = world.getTime();
            if (ticks >= session.maxEndAt
                    || (session.endAtDaybreak && (timeOfDay > 23000L || timeOfDay < 12500L))) {
                endSession(session.worldId, true);
                continue;
            }
            if (ticks >= session.nextSpawnAt) {
                spawnHorrorMobs(world);
                session.nextSpawnAt = ticks + Math.max(20L,
                        plugin.getConfig().getLong("horrorNights.spawn-interval-ticks", 60L));
            }
            if (ticks >= session.nextSoundAt) {
                playAmbientSound(world);
                session.nextSoundAt = ticks + Math.max(20L,
                        plugin.getConfig().getLong("horrorNights.ambient-sound-interval-ticks", 200L));
            }
            if (ticks >= session.nextEffectsAt) {
                applyRandomEffects(world);
                session.nextEffectsAt = ticks + Math.max(20L,
                        plugin.getConfig().getLong("horrorNights.effect-interval-ticks", 200L));
            }
            sendMobParticles(world);
        }
    }

    private void checkForRandomStarts() {
        // The random seasonal event respects the same October 23–November 2 inclusive
        // window as SpookySeason. Manual admin starts remain available for testing.
        if (plugin instanceof HalloweenCore core && !core.isSeasonActive()) return;
        double chance = clamp(plugin.getConfig().getDouble("horrorNights.horror-night-chance", 10.0), 0.0, 100.0);
        for (World world : Bukkit.getWorlds()) {
            if (!isAllowedWorld(world) || sessions.containsKey(world.getUID())) continue;
            long time = world.getTime();
            if (time < 13000L || time > 23000L || world.getPlayers().isEmpty()) continue;
            long day = world.getFullTime() / 24000L;
            Long alreadyRolled = lastRolledNight.get(world.getUID());
            if (alreadyRolled != null && alreadyRolled.longValue() == day) continue;
            lastRolledNight.put(world.getUID(), day);
            if (random.nextDouble() * 100.0 < chance) startSession(world, true);
        }
    }

    private boolean isAllowedWorld(World world) {
        List<String> excluded = plugin.getConfig().getStringList("horrorNights.excluded-worlds");
        for (String name : excluded) if (world.getName().equalsIgnoreCase(name)) return false;
        List<String> allowed = plugin.getConfig().getStringList("horrorNights.enabled-worlds");
        if (allowed.isEmpty()) return true;
        for (String name : allowed) if (name.equals("*") || world.getName().equalsIgnoreCase(name)) return true;
        return false;
    }

    private void startSession(World world, boolean endAtDaybreak) {
        if (sessions.containsKey(world.getUID()) || !isAllowedWorld(world)) return;
        long duration = Math.max(1200L,
                plugin.getConfig().getLong("horrorNights.manual-max-duration-ticks", 12000L));
        Session session = new Session(world, ticks, duration, endAtDaybreak);
        session.nextEffectsAt = ticks + Math.max(20L,
                plugin.getConfig().getLong("horrorNights.effect-interval-ticks", 200L));
        sessions.put(world.getUID(), session);
        String message = plugin.getConfig().getString("horrorNights.messages.horror-night-start",
                "&4&lVarování: začala hororová noc!");
        world.getPlayers().forEach(player -> player.sendMessage(color(message)));
        playConfiguredStartSound(world);
        plugin.getLogger().info("Hororová noc začala ve světě " + world.getName() + ".");
    }

    private boolean endSession(UUID worldId, boolean announce) {
        Session session = sessions.remove(worldId);
        if (session == null) return false;
        World world = Bukkit.getWorld(worldId);
        if (world != null) {
            removeTaggedMobs(world);
            if (announce) {
                String message = plugin.getConfig().getString("horrorNights.messages.horror-night-end",
                        "&aHororová noc skončila… prozatím.");
                world.getPlayers().forEach(player -> player.sendMessage(color(message)));
            }
            plugin.getLogger().info("Hororová noc skončila ve světě " + world.getName() + ".");
        }
        return true;
    }

    private void stopAllSessions() {
        for (UUID worldId : new ArrayList<>(sessions.keySet())) endSession(worldId, false);
    }

    private void spawnHorrorMobs(World world) {
        int maxMobs = Math.max(0, plugin.getConfig().getInt("horrorNights.max-mobs-per-world", 30));
        if (maxMobs == 0 || countTaggedMobs(world) >= maxMobs) return;
        List<String> configuredTypes = plugin.getConfig().getStringList("horrorNights.mob-types");
        if (configuredTypes.isEmpty()) {
            configuredTypes = List.of("ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "WITCH");
        }
        int perPlayer = Math.max(1, Math.min(10, plugin.getConfig().getInt("horrorNights.spawn-count", 1)));
        int minRadius = Math.max(8, plugin.getConfig().getInt("horrorNights.spawn-min-radius", 20));
        int maxRadius = Math.max(minRadius, plugin.getConfig().getInt("horrorNights.spawn-max-radius", 40));
        for (Player player : new ArrayList<>(world.getPlayers())) {
            for (int index = 0; index < perPlayer; index++) {
                if (countTaggedMobs(world) >= maxMobs) return;
                Location location = findSpawnLocation(player, minRadius, maxRadius);
                if (location == null) continue;
                if (trySpawnCustomMob(location)) continue;

                EntityType type = validMobType(configuredTypes);
                if (type == null) break;
                try {
                    Entity entity = world.spawnEntity(location, type);
                    if (!(entity instanceof LivingEntity living)) {
                        entity.remove();
                        continue;
                    }
                    living.getPersistentDataContainer().set(mobMarker, PersistentDataType.BYTE, (byte) 1);
                    prepareMob(living);
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    plugin.getLogger().fine("Nelze vytvořit hororovou entitu " + type + ": " + exception.getMessage());
                }
            }
        }
    }


    /**
     * Optionally spawn a configured MythicMobs mob. Vanilla mobs remain the fallback,
     * so HorrorNights still works when MythicMobs is absent or no custom IDs are configured.
     */
    private boolean trySpawnCustomMob(Location location) {
        if (!plugin.getConfig().getBoolean("horrorNights.spawn-custom-mobs", true)) return false;
        List<String> ids = plugin.getConfig().getStringList("horrorNights.custom-mob-ids");
        if (ids.isEmpty()) return false;
        double chance = clamp(plugin.getConfig().getDouble("horrorNights.custom-mob-chance", 0.25), 0.0, 1.0);
        if (random.nextDouble() >= chance) return false;

        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            if (!warnedMythicMobsMissing) {
                warnedMythicMobsMissing = true;
                plugin.getLogger().warning("HorrorNights custom-mob-ids jsou nastavené, ale MythicMobs není zapnutý; použiji běžné moby.");
            }
            return false;
        }

        String id = ids.get(random.nextInt(ids.size())).trim();
        if (id.isEmpty()) return false;
        try {
            Class<?> providerClass = Class.forName("io.lumine.mythic.api.MythicProvider");
            Object provider = providerClass.getMethod("get").invoke(null);
            Object manager = provider.getClass().getMethod("getMobManager").invoke(provider);
            Object lookup = manager.getClass().getMethod("getMythicMob", String.class).invoke(manager, id);
            Object mythicMob = lookup instanceof Optional<?> optional ? optional.orElse(null) : lookup;
            if (mythicMob == null) {
                if (warnedMissingMythicIds.add(id)) {
                    plugin.getLogger().warning("HorrorNights: MythicMobs ID '" + id + "' nebylo nalezeno; ověř horrorNights.custom-mob-ids.");
                }
                return false;
            }

            Class<?> adapterClass = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
            Object adaptedLocation = null;
            for (Method method : adapterClass.getMethods()) {
                if (method.getName().equals("adapt") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(location.getClass())) {
                    adaptedLocation = method.invoke(null, location);
                    break;
                }
            }
            if (adaptedLocation == null) {
                plugin.getLogger().warning("HorrorNights: MythicMobs BukkitAdapter nedokázal převést lokaci.");
                return false;
            }

            Object activeMob = null;
            for (Method method : mythicMob.getClass().getMethods()) {
                if (!method.getName().equals("spawn") || method.getParameterCount() != 2
                        || !method.getParameterTypes()[0].isInstance(adaptedLocation)) continue;
                Class<?> levelType = method.getParameterTypes()[1];
                Object level;
                if (levelType == float.class || levelType == Float.class) level = 1.0F;
                else if (levelType == double.class || levelType == Double.class) level = 1.0D;
                else continue;
                activeMob = method.invoke(mythicMob, adaptedLocation, level);
                break;
            }
            if (activeMob == null) {
                plugin.getLogger().warning("HorrorNights: pro MythicMobs ID '" + id + "' nebyla nalezena podporovaná spawn metoda.");
                return false;
            }

            Object mythicEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
            Object bukkitEntity = mythicEntity.getClass().getMethod("getBukkitEntity").invoke(mythicEntity);
            if (bukkitEntity instanceof Entity entity) {
                entity.getPersistentDataContainer().set(mobMarker, PersistentDataType.BYTE, (byte) 1);
                return true;
            }
            plugin.getLogger().warning("HorrorNights: MythicMobs ID '" + id + "' se spustilo, ale entitu se nepodařilo označit pro úklid.");
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warnedMythicMobsMissing) {
                warnedMythicMobsMissing = true;
                plugin.getLogger().warning("HorrorNights nedokázal načíst API MythicMobs; přechází na běžné moby. Detail: "
                        + exception.getClass().getSimpleName());
            }
            return false;
        }
    }

    private EntityType validMobType(List<String> types) {
        List<EntityType> valid = new ArrayList<>();
        for (String value : types) {
            try {
                EntityType type = EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT));
                if (type.isAlive() && type.isSpawnable() && type != EntityType.PLAYER && type != EntityType.ARMOR_STAND) {
                    valid.add(type);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().fine("Neznámý typ moba v horrorNights.mob-types: " + value);
            }
        }
        return valid.isEmpty() ? null : valid.get(random.nextInt(valid.size()));
    }

    private Location findSpawnLocation(Player player, int minRadius, int maxRadius) {
        World world = player.getWorld();
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int radius = minRadius + random.nextInt(Math.max(1, maxRadius - minRadius + 1));
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            Block floor = world.getHighestBlockAt(x, z);
            if (!floor.getType().isSolid() || floor.isLiquid()) continue;
            Block feet = floor.getRelative(0, 1, 0);
            Block head = floor.getRelative(0, 2, 0);
            if (!feet.isPassable() || !head.isPassable()) continue;
            return new Location(world, x + 0.5, floor.getY() + 1.0, z + 0.5);
        }
        return null;
    }

    private void prepareMob(LivingEntity living) {
        double healthMultiplier = clamp(plugin.getConfig().getDouble("horrorNights.health-multiplier", 2.0), 1.0, 20.0);
        try {
            var maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                double current = maxHealth.getBaseValue();
                maxHealth.setBaseValue(Math.min(2048.0, Math.max(1.0, current * healthMultiplier)));
                living.setHealth(maxHealth.getValue());
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().fine("Nepodařilo se upravit zdraví hororového moba: " + exception.getMessage());
        }
        if (plugin.getConfig().getBoolean("horrorNights.speed-boost", true)) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 0, true, false, true));
        }
        if (plugin.getConfig().getBoolean("horrorNights.strength-boost", true)) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60, 0, true, false, true));
        }
        if (plugin.getConfig().getBoolean("horrorNights.glowing", true)) living.setGlowing(true);
        if (plugin.getConfig().getBoolean("horrorNights.show-custom-name", true)) {
            String prefix = plugin.getConfig().getString("horrorNights.name-prefix", "&4&lHORROR ");
            String name = prefix + living.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            living.setCustomName(color(name));
            living.setCustomNameVisible(true);
        }
        double invisibleChance = clamp(plugin.getConfig().getDouble("horrorNights.invisible-chance", 30.0), 0.0, 100.0);
        if (random.nextDouble() * 100.0 < invisibleChance) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60, 0, true, false, false));
        }
        if (living instanceof Mob mob) mob.setRemoveWhenFarAway(true);
    }

    private void applyRandomEffects(World world) {
        if (!plugin.getConfig().getBoolean("horrorNights.player-effects", true)) return;
        double chance = clamp(plugin.getConfig().getDouble("horrorNights.effect-chance", 0.35), 0.0, 1.0);
        int duration = Math.max(20, plugin.getConfig().getInt("horrorNights.effect-duration-ticks", 100));
        List<String> effectNames = plugin.getConfig().getStringList("horrorNights.effect-types");
        if (effectNames.isEmpty()) effectNames = List.of("DARKNESS", "BLINDNESS", "NAUSEA");
        for (Player player : world.getPlayers()) {
            if (random.nextDouble() >= chance) continue;
            String selected = effectNames.get(random.nextInt(effectNames.size())).toUpperCase(Locale.ROOT);
            PotionEffectType effect = switch (selected) {
                case "DARKNESS" -> PotionEffectType.DARKNESS;
                case "BLINDNESS" -> PotionEffectType.BLINDNESS;
                case "NAUSEA", "CONFUSION" -> PotionEffectType.NAUSEA;
                default -> null;
            };
            if (effect != null) player.addPotionEffect(new PotionEffect(effect, duration, 0, true, false, true));
        }
    }

    private void playAmbientSound(World world) {
        if (!plugin.getConfig().getBoolean("horrorNights.ambient-sounds", true)) return;
        List<String> configured = plugin.getConfig().getStringList("horrorNights.sound-types");
        if (configured.isEmpty()) configured = List.of("ENTITY_WITCH_AMBIENT", "ENTITY_GHAST_SCREAM", "ENTITY_ENDERMAN_SCREAM");
        List<Sound> valid = new ArrayList<>();
        for (String name : configured) {
            try {
                valid.add(Sound.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().fine("Neznámý zvuk v horrorNights.sound-types: " + name);
            }
        }
        if (valid.isEmpty()) return;
        Sound sound = valid.get(random.nextInt(valid.size()));
        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), sound, 0.6f, 0.6f + random.nextFloat() * 0.7f);
        }
    }

    private void sendMobParticles(World world) {
        String name = plugin.getConfig().getString("horrorNights.invisible-particle", "SOUL_FIRE_FLAME");
        Particle particle;
        try {
            particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            particle = Particle.SOUL_FIRE_FLAME;
        }
        int particleCount = Math.max(0, Math.min(30,
                plugin.getConfig().getInt("horrorNights.invisible-particle-count", 3)));
        if (particleCount == 0) return;
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!living.getPersistentDataContainer().has(mobMarker, PersistentDataType.BYTE)) continue;
            if (!living.hasPotionEffect(PotionEffectType.INVISIBILITY)) continue;
            world.spawnParticle(particle, living.getLocation().add(0, 0.8, 0), particleCount, 0.25, 0.35, 0.25, 0.01);
        }
    }

    private int countTaggedMobs(World world) {
        int total = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity living
                    && living.getPersistentDataContainer().has(mobMarker, PersistentDataType.BYTE)
                    && !living.isDead() && living.isValid()) total++;
        }
        return total;
    }

    private void removeTaggedMobs() {
        for (World world : Bukkit.getWorlds()) removeTaggedMobs(world);
    }

    private void removeTaggedMobs(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(mobMarker, PersistentDataType.BYTE)) entity.remove();
        }
    }

    @EventHandler
    public void onTaggedMobDeath(EntityDeathEvent event) {
        event.getEntity().getPersistentDataContainer().remove(mobMarker);
    }

    private void playConfiguredStartSound(World world) {
        for (Player player : world.getPlayers()) player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.6f, 0.65f);
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
