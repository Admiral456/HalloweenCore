package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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
    private final java.util.Set<String> warnedSurgeErrors = new java.util.HashSet<>();
    private final Set<String> processedFarmlandBlocks = new HashSet<>();
    private final Set<String> scannedHarvestChunkKeys = new HashSet<>();
    private final Set<String> scannedPumpkinChunkKeys = new HashSet<>();
    private final Set<UUID> bloodMoonNightWorlds = new HashSet<>();
    private int loadedChunkScanCursor;

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
        resetEventCaches();
    }

    private void resetEventCaches() {
        processedFarmlandBlocks.clear();
        scannedHarvestChunkKeys.clear();
        scannedPumpkinChunkKeys.clear();
        bloodMoonNightWorlds.clear();
    }

    public boolean startEventNow(String requestedId) {
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("random-events.enabled", true)) return false;
        if (activeEventId != null) return false;
        if (Bukkit.getOnlinePlayers().stream().noneMatch(player ->
                plugin.isEligibleGameplayPlayer(player) && plugin.isAutomaticHalloweenWorld(player.getWorld()))) return false;

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

        if (activeEventId == null && now >= nextEventAt && Bukkit.getOnlinePlayers().stream().anyMatch(player ->
                plugin.isEligibleGameplayPlayer(player) && plugin.isAutomaticHalloweenWorld(player.getWorld()))) {
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
        double intervalMultiplier = plugin.getSeasonManager() == null
                ? 1.0D : plugin.getSeasonManager().getEventIntervalMultiplier();
        min = Math.max(1, (int) Math.round(min * intervalMultiplier));
        max = Math.max(min, (int) Math.round(max * intervalMultiplier));
        int minutes = ThreadLocalRandom.current().nextInt(min, max + 1);
        nextEventAt = System.currentTimeMillis() + minutes * 60_000L;
    }

    /** Recalculate the pending timer when Witching Hour begins or ends. */
    public void rescheduleForTimeWindow() {
        if (activeEventId == null) scheduleNextEvent();
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
        resetEventCaches();
        if ("blood-moon-invasion".equals(activeEventId)) prepareBloodMoonNight();

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
        resetEventCaches();
        scheduleNextEvent();
    }

    private void playEventSound() {
        String eventPath = "random-events.types." + activeEventId + ".sound";
        String configured = plugin.getConfig().getString(eventPath,
                plugin.getConfig().getString("random-events.sound", ""));
        if (configured == null || configured.isBlank()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player) || !plugin.isAutomaticHalloweenWorld(player.getWorld())) continue;
            try {
                player.playSound(player.getLocation(), configured.toLowerCase(Locale.ROOT), SoundCategory.AMBIENT, 0.9f, 1.0f);
            } catch (Exception ignored) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.7f);
            }
        }
    }

    private void runActiveEventEffects(long now) {
        if ("blood-moon-invasion".equals(activeEventId)) {
            prepareBloodMoonNight();
            displayBloodMoonToPlayers();
        }
        long interval = Math.max(10L, plugin.getConfig().getLong("random-events.surge-interval-seconds", 25L));
        if (now - lastSurgeAt < interval * 1000L) return;
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(plugin::isEligibleGameplayPlayer)
                .filter(player -> plugin.isAutomaticHalloweenWorld(player.getWorld()))
                .toList();
        if (players.isEmpty()) return;

        if ("cursed-harvest".equals(activeEventId)) {
            int spawned = spawnFromLoadedFarmland();
            if (spawned > 0) Bukkit.broadcastMessage(plugin.color(
                    "&a&lPROKLETÁ SKLIZEŇ &8» &2Na polích se probudilo " + spawned + " prokletých bytostí."));
            lastSurgeAt = now;
            return;
        }
        if ("graveyard-rising".equals(activeEventId)) {
            spawnFromRecordedGraveyards();
            lastSurgeAt = now;
            return;
        }
        if ("pumpkin-apocalypse".equals(activeEventId)) decorateLoadedChunksWithPumpkins();
        for (Player target : players) {
            try {
                applySurgeToPlayer(target, now);
            } catch (RuntimeException ex) {
                String key = activeEventId + ":" + ex.getClass().getName();
                if (warnedSurgeErrors.add(key)) plugin.getLogger().warning(
                        "Halloween event surge '" + activeEventId + "' failed (" + ex.getClass().getSimpleName()
                                + "); further errors of this type for this event are suppressed.");
            }
        }
        lastSurgeAt = now;
    }

    private void prepareBloodMoonNight() {
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.isAutomaticHalloweenWorld(world)) continue;
            boolean eligible = Bukkit.getOnlinePlayers().stream()
                    .anyMatch(player -> player.getWorld().getUID().equals(world.getUID())
                            && plugin.isEligibleGameplayPlayer(player));
            if (eligible && bloodMoonNightWorlds.add(world.getUID())) world.setTime(13_000L);
        }
    }

    /** Event-only red particle moon; Bukkit cannot dynamically tint the vanilla moon texture. */
    private void displayBloodMoonToPlayers() {
        Particle.DustOptions blood = new Particle.DustOptions(Color.fromRGB(190, 5, 16), 1.8F);
        Particle.DustOptions glow = new Particle.DustOptions(Color.fromRGB(255, 48, 32), 1.1F);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player) || !plugin.isAutomaticHalloweenWorld(player.getWorld())) continue;
            Vector forward = player.getEyeLocation().getDirection().setY(0.0D);
            if (forward.lengthSquared() < 0.001D) forward = new Vector(0.0D, 0.0D, 1.0D);
            forward.normalize();
            Vector right = new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();
            Location center = player.getEyeLocation().clone().add(forward.multiply(23.0D)).add(0.0D, 7.0D, 0.0D);
            for (int i = 0; i < 36; i++) {
                double angle = Math.PI * 2.0D * i / 36.0D;
                Location point = center.clone().add(right.clone().multiply(Math.cos(angle) * 2.4D))
                        .add(0.0D, Math.sin(angle) * 2.4D, 0.0D);
                player.spawnParticle(Particle.DUST, point, 1, 0.0D, 0.0D, 0.0D, 0.0D, blood);
            }
            for (int i = 0; i < 18; i++) {
                double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
                double radius = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * 2.0D;
                Location point = center.clone().add(right.clone().multiply(Math.cos(angle) * radius))
                        .add(0.0D, Math.sin(angle) * radius, 0.0D);
                player.spawnParticle(Particle.DUST, point, 1, 0.0D, 0.0D, 0.0D, 0.0D, glow);
            }
        }
    }

    private int spawnFromRecordedGraveyards() {
        if (plugin.getStructureManager() == null || plugin.getMobManager() == null) return 0;
        int siteBudget = Math.max(1, Math.min(128,
                plugin.getConfig().getInt("random-events.graveyard-rising.max-sites-per-surge", 64)));
        int perSite = Math.max(1, Math.min(6,
                plugin.getConfig().getInt("random-events.graveyard-rising.mobs-per-site-per-surge", 3)));
        double radius = Math.max(16.0D, Math.min(256.0D,
                plugin.getConfig().getDouble("random-events.graveyard-rising.aggro-radius-blocks", 128.0D)));
        List<Location> sites = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) if (plugin.isAutomaticHalloweenWorld(world))
            sites.addAll(plugin.getStructureManager().getRecordedGraveyardLocations(world));
        Collections.shuffle(sites);
        int spawned = 0, visited = 0;
        for (Location site : sites) {
            if (visited++ >= siteBudget) break;
            Player target = nearestEligiblePlayer(site, radius);
            if (target == null) continue;
            int atSite = 0;
            for (int i = 0; i < perSite; i++) {
                String[] pool = {"gravekeeper", "gravekeeper", "cursed-zombie", "blood-spider"};
                if (plugin.getMobManager().spawnEventMobAt(site, pool[ThreadLocalRandom.current().nextInt(pool.length)], target)) atSite++;
            }
            if (atSite > 0) {
                spawned += atSite;
                site.getWorld().spawnParticle(Particle.SOUL, site.clone().add(0.0D, 1.0D, 0.0D),
                        36, 1.7D, 1.0D, 1.7D, 0.02D);
                target.sendMessage(plugin.color("&8&lHŘBITOV VSTÁVÁ &8» &7Nemrtví vyrazili z nejbližšího hřbitova."));
            }
        }
        return spawned;
    }

    private int spawnFromLoadedFarmland() {
        if (plugin.getMobManager() == null) return 0;
        int chunkBudget = Math.max(1, Math.min(512,
                plugin.getConfig().getInt("random-events.cursed-harvest.field-scan-chunks-per-surge", 64)));
        int mobBudget = Math.max(1, Math.min(256,
                plugin.getConfig().getInt("random-events.cursed-harvest.max-field-mobs-per-surge", 48)));
        double radius = Math.max(16.0D, Math.min(256.0D,
                plugin.getConfig().getDouble("random-events.cursed-harvest.field-target-radius-blocks", 192.0D)));
        int spawned = 0;
        for (Chunk chunk : nextLoadedChunkBatch(chunkBudget, null)) {
            boolean needsRetry = false;
            boolean budgetReached = false;
            World world = chunk.getWorld();
            for (int x = chunk.getX() << 4; x < (chunk.getX() << 4) + 16 && !budgetReached; x++) {
                for (int z = chunk.getZ() << 4; z < (chunk.getZ() << 4) + 16 && !budgetReached; z++) {
                    for (Block farmland : findFarmlandBlocks(world, x, z)) {
                        String tile = blockKey(farmland);
                        if (processedFarmlandBlocks.contains(tile)) continue;
                        if (spawned >= mobBudget) {
                            needsRetry = true;
                            budgetReached = true;
                            break;
                        }
                        Location anchor = farmland.getLocation().add(0.5D, 1.0D, 0.5D);
                        Player target = nearestEligiblePlayer(anchor, radius);
                        if (target == null) {
                            // Leave this tile pending; a later surge retries after a player approaches.
                            needsRetry = true;
                            continue;
                        }
                        String mobId = ThreadLocalRandom.current().nextBoolean() ? "blood-spider" : "pumpkin-wraith";
                        if (!plugin.getMobManager().spawnEventMobAt(anchor, mobId, target)) {
                            needsRetry = true;
                            int cap = plugin.getConfig().getInt("random-events.max-event-mobs", 256);
                            if (plugin.getMobManager().countEventMobs(world) >= cap) {
                                budgetReached = true;
                                break;
                            }
                            continue;
                        }
                        processedFarmlandBlocks.add(tile);
                        spawned++;
                        world.spawnParticle(Particle.SOUL, anchor, 12, 0.45D, 0.3D, 0.45D, 0.02D);
                        if (spawned >= mobBudget) {
                            needsRetry = true;
                            budgetReached = true;
                            break;
                        }
                    }
                }
            }
            if (budgetReached) break;
        }
        return spawned;
    }

    /** Finds every farmland block in the upper 8 blocks beneath the surface. */
    private List<Block> findFarmlandBlocks(World world, int x, int z) {
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) return List.of();
        Block top = world.getHighestBlockAt(x, z);
        int depth = Math.max(1, Math.min(16,
                plugin.getConfig().getInt("random-events.cursed-harvest.field-column-scan-depth-blocks", 8)));
        int bottomY = Math.max(world.getMinHeight(), top.getY() - depth);
        List<Block> result = new ArrayList<>();
        for (int y = top.getY(); y >= bottomY; y--) {
            Block candidate = world.getBlockAt(x, y, z);
            if (candidate.getType() == Material.FARMLAND) result.add(candidate);
        }
        return result;
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private Player nearestEligiblePlayer(Location location, double radius) {
        if (location == null || location.getWorld() == null) return null;
        double limit = radius * radius;
        Player nearest = null;
        double best = limit;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || !plugin.isEligibleGameplayPlayer(player)
                    || !plugin.isAutomaticHalloweenWorld(player.getWorld())
                    || !player.getWorld().getUID().equals(location.getWorld().getUID())) continue;
            double distance = player.getLocation().distanceSquared(location);
            if (distance < best) { best = distance; nearest = player; }
        }
        return nearest;
    }

    private int decorateLoadedChunksWithPumpkins() {
        int budget = Math.max(1, Math.min(512, plugin.getConfig().getInt("random-events.pumpkin-apocalypse.chunks-per-surge", 128)));
        int count = 0;
        for (Chunk chunk : nextLoadedChunkBatch(budget, scannedPumpkinChunkKeys)) {
            decoratePumpkinChunk(chunk);
            scannedPumpkinChunkKeys.add(chunkKey(chunk));
            count++;
        }
        return count;
    }

    private int decoratePumpkinChunk(Chunk chunk) {
        if (chunk == null || !plugin.isAutomaticHalloweenWorld(chunk.getWorld())) return 0;
        World world = chunk.getWorld();
        int target = Math.max(0, Math.min(12,
                plugin.getConfig().getInt("random-events.pumpkin-apocalypse.additional-pumpkins-per-chunk", 3)));
        int attempts = Math.max(20, Math.min(160, Math.max(1, target) * 30));
        Random random = new Random(world.getSeed() ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L) ^ 0x424C4F4F4450554DL);
        int placed = 0;
        for (int i = 0; i < attempts && placed < target; i++) {
            int x = (chunk.getX() << 4) + random.nextInt(16), z = (chunk.getZ() << 4) + random.nextInt(16);
            Block ground = world.getHighestBlockAt(x, z);
            if (!isNaturalPumpkinGround(ground.getType())) continue;
            Block air = ground.getRelative(0, 1, 0);
            if (!air.getType().isAir() || air.isLiquid() || !world.getWorldBorder().isInside(air.getLocation())) continue;
            Material type = switch (random.nextInt(5)) {
                case 0 -> Material.JACK_O_LANTERN;
                case 1 -> Material.CARVED_PUMPKIN;
                default -> Material.PUMPKIN;
            };
            air.setType(type, false);
            placed++;
        }
        return placed;
    }

    private boolean isNaturalPumpkinGround(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM, MOSS_BLOCK,
                    MUD, CLAY, SAND, RED_SAND, GRAVEL, SNOW_BLOCK -> true;
            default -> false;
        };
    }

    private List<Chunk> nextLoadedChunkBatch(int maximum, Set<String> processed) {
        List<Chunk> all = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.isAutomaticHalloweenWorld(world)) continue;
            boolean players = Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.isOnline()
                    && p.getWorld().getUID().equals(world.getUID()) && plugin.isEligibleGameplayPlayer(p));
            if (players) Collections.addAll(all, world.getLoadedChunks());
        }
        all.sort(java.util.Comparator.comparing((Chunk c) -> c.getWorld().getName())
                .thenComparingInt(Chunk::getX).thenComparingInt(Chunk::getZ));
        if (all.isEmpty()) return List.of();
        int start = Math.floorMod(loadedChunkScanCursor, all.size()), scanned = 0;
        List<Chunk> result = new ArrayList<>();
        while (scanned < all.size() && result.size() < maximum) {
            Chunk c = all.get((start + scanned) % all.size()); scanned++;
            if (processed == null || !processed.contains(chunkKey(c))) result.add(c);
        }
        loadedChunkScanCursor = (start + Math.max(1, scanned)) % all.size();
        return result;
    }

    private String chunkKey(Chunk chunk) { return chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ(); }

    private void applySurgeToPlayer(Player target, long now) {
        switch (activeEventId.toLowerCase(Locale.ROOT)) {
            case "soulstorm" -> {
                int spawned = spawnWave(target, new String[]{"cursed-zombie", "gravekeeper", "blood-spider"}, 2);
                target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().clone().add(0, 1.0D, 0),
                        38, 1.8D, 1.1D, 1.8D, 0.04D);
                target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().clone().add(0, 0.5D, 0),
                        16, 1.3D, 0.5D, 1.3D, 0.025D);
                if (spawned > 0) target.sendMessage(plugin.color("&5Duše se shlukují... &7Z mlhy vyrazilo několik prokletých lovců."));
            }
            case "witching-hour" -> {
                int spawned = spawnWave(target, new String[]{"hex-witch", "blood-spider", "cursed-zombie"}, 2);
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
                // FARMLAND is handled by the global scan while this event is active.
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
                        plugin.getConfig().getInt("random-events.invasion-mobs-per-surge", 2)));
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
                int spawned = spawnWave(target, new String[]{"pumpkin-wraith", "pumpkin-wraith", "cursed-zombie"}, 3);
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().clone().add(0, 1.0D, 0),
                        48, 1.8D, 1.0D, 1.8D, 0.035D);
                target.getWorld().spawnParticle(Particle.LAVA, target.getLocation().clone().add(0, 0.5D, 0),
                        12, 1.4D, 0.5D, 1.4D, 0.0D);
                target.getWorld().spawnParticle(Particle.ASH, target.getLocation().clone().add(0, 1.0D, 0),
                        25, 1.3D, 0.8D, 1.3D, 0.02D);
                if (spawned > 0) target.sendMessage(plugin.color("&6&lDÝŇOVÁ APOKALYPSA &8» &7Z pukajících dýní vyrazila další vlna přízraků."));
            }
            case "graveyard-rising" -> {
                // This event spawns only at recorded cemetery locations.
            }
            default -> {
                // Unknown custom event IDs are ignored safely; known IDs are validated before activation.
            }
        }
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
