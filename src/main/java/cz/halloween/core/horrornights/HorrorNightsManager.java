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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Independent implementation of generic horror-night gameplay ideas.
 * No HorrorNights source code, bytecode, original strings, or assets are used.
 */
public final class HorrorNightsManager implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey nightEntityKey;
    private final NamespacedKey buffedMobKey;
    private final Random random = new Random();
    private final Set<UUID> activeWorlds = new HashSet<>();
    private final Map<UUID, Long> checkedNightByWorld = new HashMap<>();
    private final Map<UUID, Long> nextSpawnByWorld = new HashMap<>();
    private final Map<UUID, Long> manualEndByWorld = new HashMap<>();
    private BukkitTask ticker;

    public HorrorNightsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nightEntityKey = new NamespacedKey(plugin, "independent_horror_night_entity");
        this.buffedMobKey = new NamespacedKey(plugin, "independent_horror_night_buffed");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        stopTickerOnly();
        if (!plugin.getConfig().getBoolean("horrorNights.enabled", true)) {
            for (World world : Bukkit.getWorlds()) stopWorld(world, false);
            return;
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 100L);
    }

    public void stop() {
        stopTickerOnly();
        for (World world : Bukkit.getWorlds()) stopWorld(world, false);
        activeWorlds.clear();
        manualEndByWorld.clear();
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

    public void startManually(World world) {
        if (world == null) return;
        activate(world, true);
        long durationSeconds = Math.max(30L,
                plugin.getConfig().getLong("horrorNights.manualDurationSeconds", 240L));
        manualEndByWorld.put(world.getUID(), world.getFullTime() + durationSeconds * 20L);
    }

    public void stopWorld(World world, boolean announce) {
        if (world == null) return;
        UUID id = world.getUID();
        boolean wasActive = activeWorlds.remove(id);
        manualEndByWorld.remove(id);
        nextSpawnByWorld.remove(id);
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (entity.getPersistentDataContainer().has(nightEntityKey, PersistentDataType.BYTE)) {
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
            if (!isWorldAllowed(world)) {
                if (activeWorlds.contains(world.getUID())) stopWorld(world, true);
                continue;
            }
            long worldIdTime = world.getFullTime();
            long timeOfDay = world.getTime();
            UUID id = world.getUID();
            if (activeWorlds.contains(id)) {
                Long manualEnd = manualEndByWorld.get(id);
                if ((manualEnd != null && worldIdTime >= manualEnd)
                        || (manualEnd == null && timeOfDay < 13000L)) {
                    stopWorld(world, true);
                    continue;
                }
                long next = nextSpawnByWorld.getOrDefault(id, 0L);
                if (worldIdTime >= next) {
                    spawnForPlayers(world);
                    nextSpawnByWorld.put(id, worldIdTime
                            + Math.max(40L, plugin.getConfig().getLong("horrorNights.spawnIntervalTicks", 240L)));
                }
                produceAmbientEffects(world);
            } else if (timeOfDay >= 13000L && timeOfDay < 23000L) {
                long nightNumber = worldIdTime / 24000L;
                if (checkedNightByWorld.getOrDefault(id, -1L) != nightNumber) {
                    checkedNightByWorld.put(id, nightNumber);
                    double chance = clamp(plugin.getConfig().getDouble("horrorNights.chancePerNight", 0.10));
                    if (random.nextDouble() < chance) activate(world, false);
                }
            }
        }
    }

    private void activate(World world, boolean manual) {
        UUID id = world.getUID();
        activeWorlds.add(id);
        nextSpawnByWorld.put(id, world.getFullTime() + 20L);
        if (!manual) manualEndByWorld.remove(id);
        world.getPlayers().forEach(player -> player.playSound(player.getLocation(),
                Sound.ENTITY_WARDEN_HEARTBEAT, 0.35f, 0.65f));
        Bukkit.broadcastMessage("§8§l[§5Halloween§8] §5§lNastala strašidelná noc! §7Svět: §f"
                + world.getName() + "§7. Dávej pozor, co se skrývá ve tmě.");
    }

    private boolean isWorldAllowed(World world) {
        List<String> worlds = plugin.getConfig().getStringList("horrorNights.worlds");
        return worlds.isEmpty() || worlds.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    private void spawnForPlayers(World world) {
        if (!plugin.getConfig().getBoolean("horrorNights.mobs.enabled", true)) return;
        List<EntityType> types = configuredMobTypes();
        if (types.isEmpty()) return;
        int min = Math.max(0, Math.min(6, plugin.getConfig().getInt("horrorNights.mobs.minPerPlayer", 1)));
        int max = Math.max(min, Math.min(6, plugin.getConfig().getInt("horrorNights.mobs.maxPerPlayer", 2)));
        int radius = Math.max(6, Math.min(64,
                plugin.getConfig().getInt("horrorNights.mobs.spawnRadius", 18)));
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            int amount = min + random.nextInt(max - min + 1);
            for (int i = 0; i < amount; i++) {
                Location at = findSpawnLocation(player, radius);
                if (at == null) continue;
                EntityType type = types.get(random.nextInt(types.size()));
                Entity entity = world.spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                if (!(entity instanceof LivingEntity living)) {
                    entity.remove();
                    continue;
                }
                applyMobChanges(living, true);
                entity.getPersistentDataContainer().set(nightEntityKey, PersistentDataType.BYTE, (byte) 1);
            }
            if (plugin.getConfig().getBoolean("horrorNights.playerEffects.enabled", true)
                    && random.nextDouble() < clamp(plugin.getConfig().getDouble("horrorNights.playerEffects.chance", 0.12))) {
                int duration = Math.max(20, plugin.getConfig().getInt("horrorNights.playerEffects.durationTicks", 60));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0, true, false, true));
            }
        }
    }

    private Location findSpawnLocation(Player player, int radius) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = origin.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
            int z = origin.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
            if (Math.abs(x - origin.getBlockX()) < 5 && Math.abs(z - origin.getBlockZ()) < 5) continue;
            int y = world.getHighestBlockYAt(x, z) + 1;
            if (y < world.getMinHeight() || y >= world.getMaxHeight() - 2) continue;
            if (!world.getBlockAt(x, y, z).isPassable()
                    || !world.getBlockAt(x, y + 1, z).isPassable()) continue;
            Material floor = world.getBlockAt(x, y - 1, z).getType();
            if (floor == Material.LAVA || floor == Material.MAGMA_BLOCK || floor == Material.CACTUS
                    || floor == Material.CAMPFIRE || floor == Material.SOUL_CAMPFIRE) continue;
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private List<EntityType> configuredMobTypes() {
        List<EntityType> result = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList("horrorNights.mobs.types")) {
            try {
                EntityType type = EntityType.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
                if (type.getEntityClass() != null
                        && LivingEntity.class.isAssignableFrom(type.getEntityClass())
                        && Monster.class.isAssignableFrom(type.getEntityClass())) {
                    result.add(type);
                }
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMonsterSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster monster)) return;
        if (!activeWorlds.contains(event.getLocation().getWorld().getUID())) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        applyMobChanges(monster, false);
    }

    private void applyMobChanges(LivingEntity entity, boolean markForCleanup) {
        if (!(entity instanceof Monster)) return;
        if (entity.getPersistentDataContainer().has(buffedMobKey, PersistentDataType.BYTE)) return;
        double healthMultiplier = Math.max(1.0,
                Math.min(8.0, plugin.getConfig().getDouble("horrorNights.mobs.healthMultiplier", 1.7)));
        double damageMultiplier = Math.max(1.0,
                Math.min(8.0, plugin.getConfig().getDouble("horrorNights.mobs.damageMultiplier", 1.4)));
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double maxHealth = Math.min(2048.0, health.getBaseValue() * healthMultiplier);
            health.setBaseValue(maxHealth);
            entity.setHealth(Math.min(maxHealth, Math.max(1.0, entity.getHealth() * healthMultiplier)));
        }
        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(Math.min(256.0, damage.getBaseValue() * damageMultiplier));
        entity.getPersistentDataContainer().set(buffedMobKey, PersistentDataType.BYTE, (byte) 1);
        if (markForCleanup) {
            double invisibleChance = clamp(plugin.getConfig().getDouble("horrorNights.mobs.invisibleChance", 0.20));
            if (random.nextDouble() < invisibleChance) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60 * 5, 0, true, false, true));
                entity.getPersistentDataContainer().set(nightEntityKey, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    private void produceAmbientEffects(World world) {
        if (!plugin.getConfig().getBoolean("horrorNights.ambience.enabled", true)) return;
        for (Player player : world.getPlayers()) {
            if (random.nextDouble() < clamp(plugin.getConfig().getDouble("horrorNights.ambience.soundChancePerPulse", 0.18))) {
                float volume = (float) clamp(plugin.getConfig().getDouble("horrorNights.ambience.volume", 0.45));
                float pitch = 0.55f + random.nextFloat() * 0.65f;
                player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, volume, pitch);
            }
            if (random.nextDouble() < 0.65) {
                Location loc = player.getLocation().add(random.nextDouble() * 8 - 4, 1.0 + random.nextDouble() * 2,
                        random.nextDouble() * 8 - 4);
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.18, 0.25, 0.18, 0.005);
            }
        }
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(nightEntityKey, PersistentDataType.BYTE)
                    && !entity.isDead() && random.nextDouble() < 0.5) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation().add(0, 1.0, 0),
                        2, 0.22, 0.3, 0.22, 0.005);
            }
        }
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
