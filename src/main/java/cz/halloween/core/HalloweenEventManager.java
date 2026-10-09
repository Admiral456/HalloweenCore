package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class HalloweenEventManager {
    private final HalloweenCore plugin;
    private String activeEventId;
    private long activeUntil;
    private long nextEventAt;
    private boolean started;
    private long lastSurgeAt;
    private boolean invasionCaptainSpawned;

    public HalloweenEventManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (started) return;
        started = true;
        if (plugin.getConfig().getBoolean("random-events.enabled", true) && plugin.isEventEnabled()) {
            scheduleFirstEvent();
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void reloadSchedule() {
        clearActiveEvent();
        if (started && plugin.isEventEnabled() && plugin.getConfig().getBoolean("random-events.enabled", true)) {
            scheduleNextEvent();
        } else {
            nextEventAt = 0L;
        }
    }

    /** Schedule a short, visible first event when the administrator turns Halloween on. */
    public void scheduleFirstEvent() {
        clearActiveEvent();
        if (started && plugin.isEventEnabled() && plugin.getConfig().getBoolean("random-events.enabled", true)) {
            int delay = Math.max(5, plugin.getConfig().getInt("random-events.start-delay-seconds", 30));
            nextEventAt = System.currentTimeMillis() + delay * 1000L;
        } else {
            nextEventAt = 0L;
        }
    }

    private void clearActiveEvent() {
        if (activeEventId != null && plugin.getMobManager() != null) {
            plugin.getMobManager().cleanupEventMobs();
        }
        activeEventId = null;
        activeUntil = 0L;
        lastSurgeAt = 0L;
        invasionCaptainSpawned = false;
    }

    public boolean startEventNow(String requestedId) {
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("random-events.enabled", true)) return false;
        if (activeEventId != null) return false;

        String eventId = requestedId == null ? "" : requestedId.trim().toLowerCase(Locale.ROOT);
        if (eventId.isEmpty() || eventId.equals("random")) {
            String[] events = getKnownEventIds().toArray(String[]::new);
            eventId = events[ThreadLocalRandom.current().nextInt(events.length)];
        }
        if (!getKnownEventIds().contains(eventId)) return false;
        beginEvent(eventId);
        return true;
    }

    public boolean stopActiveEventNow() {
        if (activeEventId == null) return false;
        endEvent();
        return true;
    }

    public java.util.List<String> getKnownEventIds() {
        return java.util.List.of("soulstorm", "witching-hour", "cursed-harvest", "blood-moon-invasion", "pumpkin-apocalypse", "graveyard-rising");
    }

    public void stop() {
        if (activeEventId != null && plugin.getMobManager() != null) {
            plugin.getMobManager().cleanupEventMobs();
        }
        activeEventId = null;
        activeUntil = 0L;
        nextEventAt = 0L;
        lastSurgeAt = 0L;
        invasionCaptainSpawned = false;
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("random-events.enabled", true)) {
            if (activeEventId != null) endEvent();
            return;
        }
        if (!plugin.isEventEnabled()) return;

        long now = System.currentTimeMillis();
        if (activeEventId != null && now >= activeUntil) {
            endEvent();
            return;
        }

        if (activeEventId != null) {
            runActiveEventEffects(now);
        }

        if (activeEventId == null && now >= nextEventAt && !Bukkit.getOnlinePlayers().isEmpty()) {
            startRandomEvent();
        }
    }

    private void scheduleNextEvent() {
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("random-events.enabled", true)) {
            nextEventAt = 0L;
            return;
        }
        int phase = getGlobalPhase();
        int baseMin = Math.max(1, plugin.getConfig().getInt("random-events.interval-minutes.min", 15));
        int baseMax = Math.max(baseMin, plugin.getConfig().getInt("random-events.interval-minutes.max", 24));
        int min = Math.max(8, baseMin - phase * 2);
        int max = Math.max(min, baseMax - phase * 2);
        int minutes = ThreadLocalRandom.current().nextInt(min, max + 1);
        nextEventAt = System.currentTimeMillis() + minutes * 60_000L;
    }

    private void startRandomEvent() {
        String[] events = getKnownEventIds().toArray(String[]::new);
        String selected = events[ThreadLocalRandom.current().nextInt(events.length)];
        beginEvent(selected);
    }

    private void beginEvent(String eventId) {
        if (eventId == null || eventId.isBlank() || activeEventId != null) return;
        activeEventId = eventId;
        int phase = getGlobalPhase();
        int duration = Math.max(1, plugin.getConfig().getInt("random-events.duration-minutes", 5)
                + Math.min(3, phase / 2));
        activeUntil = System.currentTimeMillis() + duration * 60_000L;
        lastSurgeAt = 0L;
        invasionCaptainSpawned = false;

        String path = "random-events.types." + activeEventId;
        String name = plugin.getConfig().getString(path + ".name", activeEventId);
        String message = plugin.getConfig().getString(path + ".start-message",
                "&6&lHALLOWEEN &8» &f" + name + " začíná!");
        String announcement = plugin.color(message.replace("%duration%", Integer.toString(duration)));
        Bukkit.broadcastMessage(announcement);
        plugin.getLogger().info("Halloween event started: " + activeEventId + " (duration=" + duration + "m)");
        playEventSound();
    }

    private void endEvent() {
        String path = "random-events.types." + activeEventId;
        String message = plugin.getConfig().getString(path + ".end-message",
                "&6&lHALLOWEEN &8» &7Halloween událost skončila.");
        Bukkit.broadcastMessage(plugin.color(message));
        if (plugin.getMobManager() != null) {
            plugin.getMobManager().cleanupEventMobs();
        }

        activeEventId = null;
        activeUntil = 0L;
        invasionCaptainSpawned = false;
        scheduleNextEvent();
    }

    private void playEventSound() {
        String eventPath = "random-events.types." + activeEventId + ".sound";
        String configured = plugin.getConfig().getString(eventPath,
                plugin.getConfig().getString("random-events.sound", ""));
        if (configured == null || configured.isBlank()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.playSound(player.getLocation(), configured.toLowerCase(Locale.ROOT), SoundCategory.AMBIENT, 0.9f, 1.0f);
            } catch (Exception ignored) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.7f);
            }
        }
    }

    private void runActiveEventEffects(long now) {
        long surgeIntervalSeconds = Math.max(10L,
                plugin.getConfig().getLong("random-events.surge-interval-seconds", 25L));
        if (now - lastSurgeAt < surgeIntervalSeconds * 1000L) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        Player[] players = Bukkit.getOnlinePlayers().stream()
                .filter(plugin::isEligibleGameplayPlayer)
                .filter(player -> plugin.isEligibleGameplayWorld(player.getWorld()))
                .toArray(Player[]::new);
        if (players.length == 0) return;
        Player target = players[ThreadLocalRandom.current().nextInt(players.length)];

        switch (activeEventId.toLowerCase(Locale.ROOT)) {
            case "soulstorm" -> {
                int spawned = spawnWave(target, new String[]{"cursed-zombie", "gravekeeper", "blood-spider"}, 3);
                target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().clone().add(0, 1.0D, 0),
                        38, 1.8D, 1.1D, 1.8D, 0.04D);
                target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().clone().add(0, 0.5D, 0),
                        16, 1.3D, 0.5D, 1.3D, 0.025D);
                if (spawned > 0) target.sendMessage(plugin.color("&5Duše se shlukují... &7Z mlhy vyrazilo několik prokletých lovců."));
            }
            case "witching-hour" -> {
                int spawned = spawnWave(target, new String[]{"hex-witch", "blood-spider", "cursed-zombie"}, 3);
                target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().clone().add(0, 1.0D, 0),
                        32, 1.4D, 1.0D, 1.4D, 0.08D);
                target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().clone().add(0, 0.7D, 0),
                        28, 1.2D, 0.7D, 1.2D, 0.15D);
                if (ThreadLocalRandom.current().nextBoolean()) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 35, 0, true, false, false));
                    target.sendMessage(plugin.color("&5Čarodějnická hodina &8» &7čarodějnice ti na okamžik zastřela zrak."));
                } else if (spawned > 0) {
                    target.sendMessage(plugin.color("&5Čarodějnická hodina &8» &7Z temnoty se vynořila lovící smečka."));
                }
            }
            case "cursed-harvest" -> {
                int cropsGrown = empowerNearbyCrops(target, 8, 5);
                int spawned = spawnWave(target, new String[]{"blood-spider", "pumpkin-wraith"}, 2);
                target.getWorld().spawnParticle(Particle.COMPOSTER, target.getLocation().clone().add(0, 1.0D, 0),
                        24, 1.5D, 0.8D, 1.5D, 0.04D);
                target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().clone().add(0, 1.0D, 0),
                        14, 1.1D, 0.7D, 1.1D, 0.02D);
                if (cropsGrown > 0) target.sendMessage(plugin.color("&aProkletá sklizeň &8» &7Temná magie popohnala růst okolních plodin."));
                if (spawned > 0) target.sendMessage(plugin.color("&aProkletá sklizeň &8» &7Ze záhonů vylézají prokletí strážci."));
            }
            case "blood-moon-invasion" -> {
                if (!invasionCaptainSpawned && activeUntil - now <= 120_000L
                        && plugin.getMobManager().spawnInvasionCaptain(target)) {
                    invasionCaptainSpawned = true;
                    target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                            target.getLocation().clone().add(0, 1.0D, 0), 70, 1.8D, 1.2D, 1.8D, 0.045D);
                    target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_ROAR,
                            SoundCategory.AMBIENT, 0.85f, 0.55f);
                    Bukkit.broadcastMessage(plugin.color("&4&lKRVAVÝ MĚSÍC &8» &cKapitán invaze se probudil! Silný nepřítel se objevil poblíž jednoho z hráčů."));
                }

                int waveSize = Math.max(1, Math.min(8,
                        plugin.getConfig().getInt("random-events.invasion-mobs-per-surge", 4)));
                int spawned = spawnWave(target,
                        new String[]{"cursed-zombie", "gravekeeper", "blood-spider", "pumpkin-wraith", "hex-witch"},
                        waveSize);
                target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        target.getLocation().clone().add(0, 1.0D, 0), 34, 1.5D, 1.0D, 1.5D, 0.035D);
                target.getWorld().spawnParticle(Particle.ASH,
                        target.getLocation().clone().add(0, 1.0D, 0), 30, 1.4D, 0.8D, 1.4D, 0.015D);
                if (spawned > 0) target.sendMessage(plugin.color("&4Krvavý měsíc &8» &cDalší vlna nepřátel útočí! Veškerá monstra udělují trojnásobné poškození."));
            }
            case "pumpkin-apocalypse" -> {
                int spawned = spawnWave(target, new String[]{"pumpkin-wraith", "pumpkin-wraith", "cursed-zombie"}, 5);
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().clone().add(0, 1.0D, 0),
                        48, 1.8D, 1.0D, 1.8D, 0.035D);
                target.getWorld().spawnParticle(Particle.LAVA, target.getLocation().clone().add(0, 0.5D, 0),
                        12, 1.4D, 0.5D, 1.4D, 0.0D);
                target.getWorld().spawnParticle(Particle.ASH, target.getLocation().clone().add(0, 1.0D, 0),
                        25, 1.3D, 0.8D, 1.3D, 0.02D);
                if (spawned > 0) target.sendMessage(plugin.color("&6&lDÝŇOVÁ APOKALYPSA &8» &7Z pukajících dýní vyrazila další vlna přízraků."));
            }
            case "graveyard-rising" -> {
                int spawned = spawnWave(target, new String[]{"gravekeeper", "gravekeeper", "cursed-zombie", "blood-spider"}, 4);
                target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().clone().add(0, 0.3D, 0),
                        44, 1.8D, 0.15D, 1.8D, 0.025D);
                target.getWorld().spawnParticle(Particle.ASH, target.getLocation().clone().add(0, 1.0D, 0),
                        22, 1.2D, 0.8D, 1.2D, 0.015D);
                if (spawned > 0) target.sendMessage(plugin.color("&8&lHŘBITOV VSTÁVÁ &8» &7Náhrobky se otřásají a mrtví vstávají ze země."));
            }
            default -> {
                // Unknown custom event IDs are ignored safely; known IDs are validated before activation.
            }
        }
        lastSurgeAt = now;
    }

    private int spawnWave(Player target, String[] mobIds, int count) {
        int spawned = 0;
        if (target == null || mobIds == null || mobIds.length == 0) return 0;
        for (int i = 0; i < count; i++) {
            String mobId = mobIds[ThreadLocalRandom.current().nextInt(mobIds.length)];
            if (plugin.getMobManager().spawnEventMob(target, mobId)) spawned++;
        }
        return spawned;
    }

    /**
     * The harvest surge makes the event visible in the world without replacing blocks:
     * it advances a few nearby crop growth stages and summons guardians from the fields.
     */
    private int empowerNearbyCrops(Player target, int radius, int maximum) {
        if (target == null || target.getWorld() == null) return 0;
        int grown = 0;
        for (int dx = -radius; dx <= radius && grown < maximum; dx++) {
            for (int dz = -radius; dz <= radius && grown < maximum; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int x = target.getLocation().getBlockX() + dx;
                int z = target.getLocation().getBlockZ() + dz;
                if (!target.getWorld().isChunkLoaded(x >> 4, z >> 4)) continue;
                Block crop = target.getWorld().getHighestBlockAt(x, z);
                if (!(crop.getBlockData() instanceof Ageable ageable)) continue;
                if (ageable.getAge() >= ageable.getMaximumAge()) continue;
                ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
                crop.setBlockData(ageable, false);
                grown++;
            }
        }
        return grown;
    }

    public int getGlobalPhase() {
        int phase = 0;
        long total = plugin.getService().getServerFragments();
        for (long milestone : plugin.getConfig().getLongList("global-milestones")) {
            if (milestone > 0L && total >= milestone) phase++;
        }
        return phase;
    }

    public boolean isActive(String id) {
        return id != null && id.equalsIgnoreCase(activeEventId);
    }

    public double getMultiplier(String source) {
        if (activeEventId == null) return 1.0D;
        String path = "random-events.types." + activeEventId + ".multipliers." + source;
        return Math.max(1.0D, plugin.getConfig().getDouble(path, 1.0D));
    }

    public String getActiveEventId() {
        return activeEventId;
    }

    public long getRemainingSeconds() {
        if (activeEventId == null) return 0L;
        return Math.max(0L, (activeUntil - System.currentTimeMillis()) / 1000L);
    }

    public long getNextEventSeconds() {
        if (activeEventId != null) return 0L;
        if (nextEventAt <= 0L) return -1L;
        return Math.max(0L, (nextEventAt - System.currentTimeMillis()) / 1000L);
    }
}
