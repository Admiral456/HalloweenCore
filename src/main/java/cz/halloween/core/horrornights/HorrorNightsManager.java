package cz.halloween.core.horrornights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Independently written horror-night feature based on the publicly documented feature list.
 * No HorrorNights source, bytecode, original strings, or assets are used.
 */
public final class HorrorNightsManager implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey ownedEntityKey;
    private final NamespacedKey invisibleTrailKey;
    private final NamespacedKey buffedMobKey;
    private final Random random = new Random();
    private final Set<UUID> activeWorlds = new HashSet<>();
    private final Map<UUID, Long> checkedNightByWorld = new HashMap<>();
    private final Map<UUID, Long> nextSpawnByWorld = new HashMap<>();
    private final Map<UUID, Long> manualEndMillisByWorld = new HashMap<>();
    private BukkitTask ticker;

    public HorrorNightsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.ownedEntityKey = new NamespacedKey(plugin, "independent_horror_night_entity");
        this.invisibleTrailKey = new NamespacedKey(plugin, "independent_horror_invisible_trail");
        this.buffedMobKey = new NamespacedKey(plugin, "independent_horror_buffed_mob");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        stopTickerOnly();
        if (!plugin.getConfig().getBoolean("horrorNights.enabled", true)) {
            for (World world : Bukkit.getWorlds()) stopWorld(world, false);
            return;
        }
        // Check every five seconds; spawn cadence is separately configurable.
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 100L);
    }

    public void stop() {
        stopTickerOnly();
        for (World world : Bukkit.getWorlds()) stopWorld(world, false);
        activeWorlds.clear();
        manualEndMillisByWorld.clear();
    }

    private void stopTickerOnly() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    public boolean isActiveAnywhere() {
        return !activeWorlds.isEmpty();
    }

    /** Starts a manual event only when the feature is enabled and the world is allowed. */
    public boolean startManually(World world) {
        if (world == null || !plugin.getConfig().getBoolean("horrorNights.enabled", true) || !isWorldAllowed(world)) {
            return false;
        }
        activate(world);
        long durationSeconds = Math.max(30L,
                Math.min(3600L, plugin.getConfig().getLong("horrorNights.manualDurationSeconds", 240L)));
        manualEndMillisByWorld.put(world.getUID(), System.currentTimeMillis() + durationSeconds * 1000L);
        return true;
    }

    public void stopWorld(World world, boolean announce) {
        if (world == null) return;
        UUID id = world.getUID();
        boolean wasActive = activeWorlds.remove(id);
        manualEndMillisByWorld.remove(id);
        nextSpawnByWorld.remove(id);
        checkedNightByWorld.remove(id);
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (entity.getPersistentDataContainer().has(ownedEntityKey, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
        if (announce && wasActive) {
            Bukkit.broadcastMessage("§8§l[§5Halloween§8] §7Strašidelná noc ve světě §f"
                    + world.getName() + " §7skončila.");
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("horrorNights.enabled", true)) {
            for (World world : Bukkit.getWorlds()) stopWorld(world, false);
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            UUID id = world.getUID();
            if (!isWorldAllowed(world)) {
                if (activeWorlds.contains(id)) stopWorld(world, true);
                continue;
            }

            long worldTime = world.getTime();
            if (activeWorlds.contains(id)) {
                Long manualEnd = manualEndMillisByWorld.get(id);
                if ((manualEnd != null && System.currentTimeMillis() >= manualEnd)
                        || (manualEnd == null && worldTime < 13000L)) {
                    stopWorld(world, true);
                    continue;
                }

                long next = nextSpawnByWorld.getOrDefault(id, 0L);
                if (world.getFullTime() >= next) {
                    spawnWave(world);
                    long interval = Math.max(40L,
                            Math.min(1200L, plugin.getConfig().getLong("horrorNights.spawnIntervalTicks", 60L)));
                    nextSpawnByWorld.put(id, world.getFullTime() + interval);
                }
                produceAmbientEffects(world);
            } else if (worldTime >= 13000L && worldTime < 23000L) {
                long nightNumber = Math.floorDiv(world.getFullTime(), 24000L);
                if (checkedNightByWorld.getOrDefault(id, Long.MIN_VALUE) != nightNumber) {
                    checkedNightByWorld.put(id, nightNumber);
                    double chance = clamp(plugin.getConfig().getDouble("horrorNights.chancePerNight", 0.10));
                    if (random.nextDouble() < chance) activate(world);
                }
            }
        }
    }

    private void activate(World world) {
        UUID id = world.getUID();
        if (!activeWorlds.add(id)) return;
        nextSpawnByWorld.put(id, world.getFullTime() + 20L);
        manualEndMillisByWorld.remove(id);

        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.35f, 0.65f);
        }
        Bukkit.broadcastMessage("§8§l[§5Halloween§8] §5§lNastala strašidelná noc! §7Svět: §f"
                + world.getName() + "§7. Dávej pozor, co se skrývá ve tmě.");
    }

    private boolean isWorldAllowed(World world) {
        List<String> worlds = plugin.getConfig().getStringList("horrorNights.worlds");
        return worlds.isEmpty() || worlds.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    private void spawnWave(World world) {
        if (!plugin.getConfig().getBoolean("horrorNights.effects.spawnCustomMobs", true)) return;
        if (!plugin.getConfig().getBoolean("horrorNights.mobs.enabled", true)) return;
        List<Player> players = world.getPlayers().stream()
                .filter(player -> !player.isDead() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR)
                .toList();
        List<EntityType> types = configuredMobTypes();
        if (players.isEmpty() || types.isEmpty()) return;

        int amount = Math.max(1, Math.min(10, plugin.getConfig().getInt("horrorNights.mobs.spawnCount", 3)));
        int minRadius = Math.max(8, Math.min(96, plugin.getConfig().getInt("horrorNights.mobs.spawnMinRadius", 20)));
        int maxRadius = Math.max(minRadius, Math.min(128,
                plugin.getConfig().getInt("horrorNights.mobs.spawnMaxRadius", 40)));

        for (int i = 0; i < amount; i++) {
            Player anchor = players.get(random.nextInt(players.size()));
            int radius = minRadius + random.nextInt(maxRadius - minRadius + 1);
            Location spawnAt = findSpawnLocation(anchor, radius);
            if (spawnAt == null) continue;

            EntityType type = types.get(random.nextInt(types.size()));
            Entity entity = world.spawnEntity(spawnAt, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (!(entity instanceof Monster monster)) {
                entity.remove();
                continue;
            }
            entity.getPersistentDataContainer().set(ownedEntityKey, PersistentDataType.BYTE, (byte) 1);
            applyMobChanges(monster);
        }

        if (plugin.getConfig().getBoolean("horrorNights.playerEffects.enabled", true)
                && random.nextDouble() < clamp(plugin.getConfig().getDouble("horrorNights.playerEffects.chance", 0.12))) {
            for (Player player : players) {
                List<PotionEffectType> effects = configuredEffects();
                if (effects.isEmpty()) break;
                PotionEffectType effect = effects.get(random.nextInt(effects.size()));
                int duration = Math.max(20,
                        Math.min(20 * 60, plugin.getConfig().getInt("horrorNights.playerEffects.durationTicks", 100)));
                player.addPotionEffect(new PotionEffect(effect, duration, 0, true, false, true));
            }
        }
    }

    private Location findSpawnLocation(Player player, int radius) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = Math.max(1, radius + random.nextInt(9) - 4);
            int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z) + 1;
            if (y < world.getMinHeight() || y >= world.getMaxHeight() - 2) continue;
            if (!world.getBlockAt(x, y, z).isPassable()
                    || !world.getBlockAt(x, y + 1, z).isPassable()) continue;
            Material floor = world.getBlockAt(x, y - 1, z).getType();
            if (floor == Material.LAVA || floor == Material.MAGMA_BLOCK || floor == Material.CACTUS
                    || floor == Material.CAMPFIRE || floor == Material.SOUL_CAMPFIRE
                    || floor == Material.FIRE || floor == Material.SOUL_FIRE) continue;
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private List<EntityType> configuredMobTypes() {
        List<EntityType> result = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList("horrorNights.mobs.types")) {
            try {
                EntityType type = EntityType.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
                Class<?> entityClass = type.getEntityClass();
                if (entityClass != null && Monster.class.isAssignableFrom(entityClass)) result.add(type);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Neznámý typ moba v horrorNights.mobs.types: " + name);
            }
        }
        if (result.isEmpty()) {
            result.add(EntityType.ZOMBIE);
            result.add(EntityType.SKELETON);
            result.add(EntityType.SPIDER);
        }
        return result;
    }

    private void applyMobChanges(Monster entity) {
        if (entity.getPersistentDataContainer().has(buffedMobKey, PersistentDataType.BYTE)) return;
        double healthMultiplier = clampAtLeastOne(plugin.getConfig().getDouble("horrorNights.mobs.healthMultiplier", 2.0), 8.0);
        double damageMultiplier = clampAtLeastOne(plugin.getConfig().getDouble("horrorNights.mobs.damageMultiplier", 1.4), 8.0);

        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double original = health.getBaseValue();
            double boosted = Math.min(1024.0, original * healthMultiplier);
            health.setBaseValue(boosted);
            entity.setHealth(Math.min(boosted, Math.max(1.0, entity.getHealth() * healthMultiplier)));
        }
        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(Math.min(256.0, damage.getBaseValue() * damageMultiplier));

        if (plugin.getConfig().getBoolean("horrorNights.mobs.speedBoost", true)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 5, 0, true, false, true));
        }
        if (plugin.getConfig().getBoolean("horrorNights.mobs.strengthBoost", true)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 5, 0, true, false, true));
        }
        if (plugin.getConfig().getBoolean("horrorNights.mobs.glowing", true)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 60 * 5, 0, true, false, true));
        }
        if (plugin.getConfig().getBoolean("horrorNights.mobs.showCustomName", true)) {
            String prefix = plugin.getConfig().getString("horrorNights.mobs.namePrefix", "&4&lHORROR ");
            String name = prefix + entity.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            entity.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
            entity.setCustomNameVisible(true);
        }

        entity.getPersistentDataContainer().set(buffedMobKey, PersistentDataType.BYTE, (byte) 1);
        double invisibleChance = clamp(plugin.getConfig().getDouble("horrorNights.mobs.invisibleChance", 0.30));
        if (random.nextDouble() < invisibleChance) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60 * 5, 0, true, false, true));
            entity.getPersistentDataContainer().set(invisibleTrailKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private List<PotionEffectType> configuredEffects() {
        List<PotionEffectType> result = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList("horrorNights.playerEffects.types")) {
            String key = name.trim().toUpperCase(java.util.Locale.ROOT);
            if (key.equals("CONFUSION")) key = "NAUSEA";
            PotionEffectType type = PotionEffectType.getByName(key);
            if (type != null) result.add(type);
        }
        return result;
    }

    private void produceAmbientEffects(World world) {
        if (!plugin.getConfig().getBoolean("horrorNights.ambience.enabled", true)) return;
        float volume = (float) clamp(plugin.getConfig().getDouble("horrorNights.ambience.volume", 0.45));
        List<Sound> sounds = configuredSounds();
        for (Player player : world.getPlayers()) {
            if (!sounds.isEmpty()
                    && random.nextDouble() < clamp(plugin.getConfig().getDouble("horrorNights.ambience.soundChancePerPulse", 0.18))) {
                Sound sound = sounds.get(random.nextInt(sounds.size()));
                player.playSound(player.getLocation(), sound, SoundCategory.AMBIENT, volume, 0.55f + random.nextFloat() * 0.65f);
            }
        }

        Particle trail = configuredTrailParticle();
        int count = Math.max(1, Math.min(10, plugin.getConfig().getInt("horrorNights.mobs.invisibleParticleCount", 3)));
        for (Entity entity : world.getEntities()) {
            if (!entity.getPersistentDataContainer().has(invisibleTrailKey, PersistentDataType.BYTE)
                    || entity.isDead()) continue;
            Location at = entity.getLocation().add(0.0, 1.0, 0.0);
            try {
                world.spawnParticle(trail, at, count, 0.18, 0.25, 0.18, 0.005);
            } catch (IllegalArgumentException ignored) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, count, 0.18, 0.25, 0.18, 0.005);
            }
        }
    }

    private List<Sound> configuredSounds() {
        List<Sound> result = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList("horrorNights.ambience.sounds")) {
            try {
                result.add(Sound.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Neznámý zvuk v horrorNights.ambience.sounds: " + name);
            }
        }
        return result;
    }

    private Particle configuredTrailParticle() {
        String name = plugin.getConfig().getString("horrorNights.mobs.invisibleParticle", "SOUL_FIRE_FLAME");
        try {
            return Particle.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Particle.SOUL_FIRE_FLAME;
        }
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double clampAtLeastOne(double value, double max) {
        return Math.max(1.0, Math.min(max, value));
    }

}
