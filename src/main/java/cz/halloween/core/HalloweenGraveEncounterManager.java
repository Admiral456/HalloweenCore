package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Three-wave encounter started by lighting a candle near a recorded graveyard.
 * Mob encounters are tracked independently from scheduled events, so /halloween event stop
 * cannot accidentally delete the encounter or grant its rewards twice.
 */
public final class HalloweenGraveEncounterManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey encounterMobKey;
    private final Map<String, Encounter> activeBySite = new HashMap<>();
    private final Map<UUID, Encounter> encounterByMob = new HashMap<>();
    private BukkitTask tickTask;

    public HalloweenGraveEncounterManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.encounterMobKey = new NamespacedKey(plugin, "grave_encounter_mob");
    }

    public void start() {
        if (tickTask != null) return;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Encounter encounter : new ArrayList<>(activeBySite.values())) {
            finish(encounter, false, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCandleInteraction(PlayerInteractEvent event) {
        if (!plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("grave-encounters.enabled", true)
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!plugin.isEligibleGameplayPlayer(player)
                || !plugin.isAutomaticHalloweenWorld(player.getWorld())) return;

        Block block = event.getClickedBlock();
        if (block == null || !block.getType().name().endsWith("CANDLE")) return;
        if (!(block.getBlockData() instanceof Lightable lightable) || lightable.isLit()) return;

        // Bukkit applies the vanilla flint-and-steel candle interaction after this event.
        // Inspecting the block on the next tick avoids treating an ordinary right-click as ignition.
        Location candleLocation = block.getLocation().clone().add(0.5D, 0.0D, 0.5D);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEventEnabled() || !player.isOnline()
                    || !plugin.getConfig().getBoolean("grave-encounters.enabled", true)
                    || !candleLocation.getWorld().isChunkLoaded(
                    candleLocation.getBlockX() >> 4, candleLocation.getBlockZ() >> 4)) return;
            Block current = candleLocation.getBlock();
            if (!(current.getBlockData() instanceof Lightable currentLightable)
                    || !currentLightable.isLit()) return;
            if (!isGraveyardSite(candleLocation)) return;
            startEncounter(player, candleLocation, false);
        });
    }

    @EventHandler
    public void onEncounterMobDeath(EntityDeathEvent event) {
        Encounter encounter = encounterByMob.remove(event.getEntity().getUniqueId());
        if (encounter == null) return;
        encounter.mobs.remove(event.getEntity().getUniqueId());

        if (encounter.mobs.isEmpty() && encounter.pendingAdvance == null) {
            encounter.pendingAdvance = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                encounter.pendingAdvance = null;
                if (!activeBySite.containsKey(encounter.siteKey)) return;
                if (encounter.wave >= 3) {
                    finish(encounter, true, true);
                } else {
                    startWave(encounter, encounter.wave + 1);
                }
            }, 40L);
        }
    }

    public boolean startTestEncounter(Player player) {
        if (player == null || !player.isOnline() || !plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("grave-encounters.enabled", true)
                || !plugin.isAutomaticHalloweenWorld(player.getWorld())) return false;
        return startEncounter(player, player.getLocation().getBlock().getLocation().add(0.5D, 0.0D, 0.5D), true);
    }

    public List<String> tabComplete(String prefix) {
        String value = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return List.of("test").stream().filter(s -> s.startsWith(value)).toList();
    }

    private boolean isGraveyardSite(Location location) {
        double radius = Math.max(8.0D, Math.min(80.0D,
                plugin.getConfig().getDouble("grave-encounters.graveyard-radius-blocks", 28.0D)));
        if (plugin.getStructureManager() != null
                && plugin.getStructureManager().isNearRecordedGraveyard(location, radius)) return true;
        if (!plugin.getConfig().getBoolean("grave-encounters.allow-decorated-graves", true)) return false;

        // Fallback for naturally scattered grave decorations that predate the structure index.
        int markers = 0;
        int scan = Math.max(3, Math.min(12,
                plugin.getConfig().getInt("grave-encounters.marker-scan-radius", 7)));
        for (int dx = -scan; dx <= scan; dx++) {
            for (int dz = -scan; dz <= scan; dz++) {
                if (dx * dx + dz * dz > scan * scan) continue;
                Material type = location.getWorld().getBlockAt(
                        location.getBlockX() + dx, location.getBlockY(), location.getBlockZ() + dz).getType();
                if (type == Material.MOSSY_COBBLESTONE_WALL
                        || type == Material.COBBLESTONE_WALL
                        || type == Material.MOSSY_STONE_BRICK_WALL
                        || type == Material.STONE_BRICK_WALL
                        || type == Material.POLISHED_BLACKSTONE_BRICK_WALL) {
                    markers++;
                    if (markers >= 4) return true;
                }
            }
        }
        return false;
    }

    private boolean startEncounter(Player player, Location center, boolean adminTest) {
        if (center == null || center.getWorld() == null || player == null || !player.isOnline()) return false;
        int maximum = Math.max(1, Math.min(8,
                plugin.getConfig().getInt("grave-encounters.max-active", 3)));
        if (activeBySite.size() >= maximum) {
            player.sendMessage(plugin.color("&cPrávě probíhá příliš mnoho hřbitovních událostí."));
            return false;
        }

        String siteKey = center.getWorld().getUID() + ":" + center.getBlockX() + ":"
                + center.getBlockY() + ":" + center.getBlockZ();
        if (activeBySite.containsKey(siteKey)) {
            player.sendMessage(plugin.color("&7Tato svíčka už probudila něco z podzemí."));
            return false;
        }

        Encounter encounter = new Encounter(siteKey, center.clone(), adminTest);
        encounter.participants.add(player.getUniqueId());
        activeBySite.put(siteKey, encounter);
        center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.85f, 0.6f);
        center.getWorld().spawnParticle(org.bukkit.Particle.SOUL, center.clone().add(0.0D, 0.8D, 0.0D),
                36, 0.8D, 0.45D, 0.8D, 0.03D);
        Bukkit.broadcastMessage(plugin.color("&8&lHŘBITOVNÍ RITUÁL &8» &7Na hřbitově vzplanula svíčka. První vlna nemrtvých se probouzí!"));
        startWave(encounter, 1);
        return true;
    }

    private void startWave(Encounter encounter, int wave) {
        if (!activeBySite.containsKey(encounter.siteKey)) return;
        Player target = findNearbyPlayer(encounter);
        if (target == null) {
            finish(encounter, false, false);
            return;
        }

        encounter.wave = wave;
        encounter.waveDeadline = System.currentTimeMillis()
                + Math.max(20L, plugin.getConfig().getLong("grave-encounters.wave-timeout-seconds", 90L)) * 1000L;
        encounter.mobs.clear();
        if (encounter.bossBar != null) {
            encounter.bossBar.removeAll();
            encounter.bossBar.setVisible(false);
            encounter.bossBar = null;
        }

        switch (wave) {
            case 1 -> {
                announce(encounter, "&8Vlna 1/3 &7— kostlivci a prokletí zombie!");
                spawnGroup(encounter, target, new String[]{"cursed-zombie", "gravekeeper"}, 4, false);
            }
            case 2 -> {
                announce(encounter, "&8Vlna 2/3 &7— krvaví pavouci a dýňoví přízraci!");
                spawnGroup(encounter, target, new String[]{"blood-spider", "pumpkin-wraith", "hex-witch"}, 5, false);
            }
            case 3 -> {
                announce(encounter, "&4Vlna 3/3 &c— STRÁŽCE HROBU povstal!");
                spawnGroup(encounter, target, new String[]{"gravekeeper"}, 1, true);
                spawnGroup(encounter, target, new String[]{"cursed-zombie", "blood-spider"}, 2, false);
                createBossBar(encounter);
            }
            default -> finish(encounter, false, false);
        }

        if (encounter.mobs.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeBySite.containsKey(encounter.siteKey) && encounter.mobs.isEmpty()) {
                    if (encounter.wave >= 3) finish(encounter, true, true);
                    else startWave(encounter, encounter.wave + 1);
                }
            }, 40L);
        }
    }

    private void spawnGroup(Encounter encounter, Player target, String[] types, int count, boolean boss) {
        for (int i = 0; i < count; i++) {
            String mobId = types[ThreadLocalRandom.current().nextInt(types.length)];
            LivingEntity entity = plugin.getMobManager().spawnEncounterMob(target, mobId);
            if (entity == null) continue;

            encounter.mobs.add(entity.getUniqueId());
            encounterByMob.put(entity.getUniqueId(), encounter);
            entity.setGlowing(true);
            if (boss) {
                double maxHealth = Math.max(80.0D,
                        plugin.getConfig().getDouble("grave-encounters.boss-health", 160.0D));
                var attribute = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attribute != null) {
                    attribute.setBaseValue(maxHealth);
                    entity.setHealth(maxHealth);
                }
                entity.setCustomName(plugin.color("&4&lSTRÁŽCE HROBU"));
                entity.setCustomNameVisible(true);
                encounter.boss = entity;
                encounter.bossMaxHealth = maxHealth;
            }
        }
    }

    private void createBossBar(Encounter encounter) {
        if (encounter.boss == null || encounter.boss.isDead()) return;
        encounter.bossBar = Bukkit.createBossBar(
                plugin.color("&4&lSTRÁŽCE HROBU"), BarColor.PURPLE, BarStyle.SEGMENTED_10);
        encounter.bossBar.setProgress(1.0D);
        encounter.bossBar.setVisible(true);
        updateBossBar(encounter);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Encounter> iterator = new ArrayList<>(activeBySite.values()).iterator();
        while (iterator.hasNext()) {
            Encounter encounter = iterator.next();
            if (!encounter.center.getChunk().isLoaded() || !plugin.isEventEnabled()) {
                finish(encounter, false, false);
                continue;
            }

            updateBossBar(encounter);
            if (now >= encounter.waveDeadline) {
                removeWaveMobs(encounter);
                if (encounter.wave >= 3) finish(encounter, false, false);
                else startWave(encounter, encounter.wave + 1);
            }
        }
    }

    private void updateBossBar(Encounter encounter) {
        if (encounter.bossBar == null || encounter.boss == null || encounter.boss.isDead()
                || !encounter.boss.isValid()) return;
        double max = Math.max(1.0D, encounter.boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? encounter.bossMaxHealth
                : encounter.boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        encounter.bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, encounter.boss.getHealth() / max)));

        Set<Player> nearby = new HashSet<>();
        double radius = Math.max(16.0D, plugin.getConfig().getDouble("grave-encounters.boss-bar-radius-blocks", 48.0D));
        double radiusSquared = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)
                    || !player.getWorld().getUID().equals(encounter.center.getWorld().getUID())) continue;
            if (player.getLocation().distanceSquared(encounter.center) > radiusSquared) continue;
            nearby.add(player);
            encounter.participants.add(player.getUniqueId());
            if (!encounter.bossBar.getPlayers().contains(player)) encounter.bossBar.addPlayer(player);
        }
        for (Player viewer : new HashSet<>(encounter.bossBar.getPlayers())) {
            if (!nearby.contains(viewer)) encounter.bossBar.removePlayer(viewer);
        }
    }

    private Player findNearbyPlayer(Encounter encounter) {
        double radius = Math.max(12.0D, plugin.getConfig().getDouble("grave-encounters.participant-radius-blocks", 40.0D));
        double radiusSquared = radius * radius;
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)
                    || !player.getWorld().getUID().equals(encounter.center.getWorld().getUID())) continue;
            double distance = player.getLocation().distanceSquared(encounter.center);
            if (distance <= radiusSquared && distance < best) {
                best = distance;
                nearest = player;
                encounter.participants.add(player.getUniqueId());
            }
        }
        return nearest;
    }

    private void announce(Encounter encounter, String message) {
        for (UUID uuid : new HashSet<>(encounter.participants)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) player.sendMessage(plugin.color("&8&lHŘBITOV &8» " + message));
        }
        encounter.center.getWorld().playSound(encounter.center, Sound.ENTITY_WARDEN_ROAR, 0.8f,
                encounter.wave == 3 ? 0.55f : 0.8f);
        encounter.center.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME,
                encounter.center.clone().add(0.0D, 0.7D, 0.0D), 30, 1.1D, 0.6D, 1.1D, 0.035D);
    }

    private void finish(Encounter encounter, boolean success, boolean reward) {
        if (activeBySite.remove(encounter.siteKey) == null) return;
        if (encounter.pendingAdvance != null) {
            encounter.pendingAdvance.cancel();
            encounter.pendingAdvance = null;
        }
        removeWaveMobs(encounter);

        if (encounter.bossBar != null) {
            encounter.bossBar.removeAll();
            encounter.bossBar.setVisible(false);
        }
        if (success && reward) {
            long fragments = Math.max(0L, plugin.getConfig().getLong("grave-encounters.reward-fragments", 250L));
            Bukkit.broadcastMessage(plugin.color("&6&lHŘBITOV OČIŠTĚN &8» &7Strážce hrobu padl. Účastníci získávají odměnu!"));
            if (!encounter.adminTest && fragments > 0L) {
                for (UUID uuid : new HashSet<>(encounter.participants)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        plugin.getService().addFragments(uuid, fragments, "grave-encounter");
                    }
                }
            }
        } else if (!encounter.adminTest) {
            Bukkit.broadcastMessage(plugin.color("&8&lHŘBITOV &8» &7Rituál utichl dřív, než byl dokončen."));
        }
    }

    private void removeWaveMobs(Encounter encounter) {
        for (UUID uuid : new HashSet<>(encounter.mobs)) {
            encounterByMob.remove(uuid);
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid()) entity.remove();
        }
        encounter.mobs.clear();
        encounter.boss = null;
    }

    private static final class Encounter {
        private final String siteKey;
        private final Location center;
        private final boolean adminTest;
        private final Set<UUID> participants = new HashSet<>();
        private final Set<UUID> mobs = new HashSet<>();
        private int wave;
        private long waveDeadline;
        private double bossMaxHealth = 160.0D;
        private LivingEntity boss;
        private BossBar bossBar;
        private org.bukkit.scheduler.BukkitTask pendingAdvance;

        private Encounter(String siteKey, Location center, boolean adminTest) {
            this.siteKey = siteKey;
            this.center = center;
            this.adminTest = adminTest;
        }
    }
}
