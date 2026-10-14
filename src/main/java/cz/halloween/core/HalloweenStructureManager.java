package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Manual, administrator-triggered FORCE structure placement.
 *
 * Structures are centred automatically a few blocks in front of the caller and
 * rotated to the nearest cardinal direction. All writes are planned inside a
 * bounded footprint, performed in tick-sized batches, and have BlockState
 * snapshots for rollback if application fails.
 */
public final class HalloweenStructureManager implements Listener {
    private static final int FORWARD_BLOCKS = 8;
    private static final int BLOCKS_PER_TICK = 700;
    private static final int MAX_PLANNED_BLOCKS = 210_000;

    private final HalloweenCore plugin;
    private final HalloweenRegionProtection regionProtection;
    private final NamespacedKey checkedChunkKey;
    private final NamespacedKey structureChunkKey;
    private final Set<String> generatedStructureIndex = new HashSet<>();
    private boolean building;
    private BukkitTask activeTask;
    private BuildJob activeJob;

    private enum StructureType {
        CEMETERY("hřbitov", 10, 8, false, Material.MOSSY_COBBLESTONE),
        VILLAGE("vesnice", 15, 10, false, Material.GRASS_BLOCK),
        ARENA("aréna", 18, 16, true, Material.POLISHED_BLACKSTONE),
        VAMPIRE_ARENA("vampirearena", 48, 24, true, Material.POLISHED_BLACKSTONE_BRICKS),
        GOBLIN_ARENA("goblinarena", 24, 20, true, Material.MOSSY_STONE_BRICKS),
        AUTO_CEMETERY("auto-hřbitov", 7, 8, false, Material.MOSSY_COBBLESTONE),
        AUTO_VILLAGE("auto-vesnice", 7, 10, false, Material.GRASS_BLOCK);

        final String displayId;
        final int radius;
        final int clearHeight;
        final boolean circular;
        final Material floor;

        StructureType(String displayId, int radius, int clearHeight, boolean circular, Material floor) {
            this.displayId = displayId;
            this.radius = radius;
            this.clearHeight = clearHeight;
            this.circular = circular;
            this.floor = floor;
        }
    }

    private record BlockPos(int x, int y, int z) {}

    private static final class BuildJob {
        final Player player;
        final World world;
        final StructureType type;
        final int centerX;
        final int floorY;
        final int centerZ;
        final LinkedHashMap<BlockPos, Material> plan;
        final List<Map.Entry<BlockPos, Material>> operations;
        final LinkedHashMap<BlockPos, BlockState> snapshots = new LinkedHashMap<>();
        List<Map.Entry<BlockPos, BlockState>> rollbackEntries = List.of();
        int cursor;
        int rollbackCursor;
        long lastProgressNotice;
        String failure;
        boolean rollingBack;
        final boolean automatic;

        BuildJob(Player player, World world, StructureType type, int centerX, int floorY,
                 int centerZ, LinkedHashMap<BlockPos, Material> plan, boolean automatic) {
            this.player = player;
            this.world = world;
            this.type = type;
            this.centerX = centerX;
            this.floorY = floorY;
            this.centerZ = centerZ;
            this.plan = plan;
            this.operations = new ArrayList<>(plan.entrySet());
            this.automatic = automatic;
        }
    }

    public HalloweenStructureManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.regionProtection = new HalloweenRegionProtection(plugin);
        this.checkedChunkKey = new NamespacedKey(plugin, "halloween_structure_attempted_v1");
        this.structureChunkKey = new NamespacedKey(plugin, "halloween_structure_anchor_v1");
        this.generatedStructureIndex.addAll(plugin.getConfig().getStringList("world-generation.generated-structure-index"));
    }

    public boolean isBuilding() {
        return building;
    }

    /**
     * One command, one action. No location setup, centre selection, or confirmation
     * command is required. This path intentionally does not apply the automatic-world
     * blacklist, because an explicit admin build in Spawn is allowed.
     */
    public boolean build(Player player, String requestedType) {
        if (player == null || !player.isOnline()) return false;
        if (building) {
            player.sendMessage(plugin.color("&cJiná Halloween stavba už probíhá. Počkej na její dokončení."));
            return false;
        }

        StructureType type = resolveType(requestedType);
        if (type == null) {
            player.sendMessage(plugin.color("&cNeznámá struktura. Použij: /halloween spawn <hřbitov|vesnice|aréna|vampirearena|goblinarena>"));
            return false;
        }

        World world = player.getWorld();
        Location look = player.getLocation();
        Vector direction = look.getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001D) direction = new Vector(0, 0, 1);
        direction.normalize();
        int centerX = (int) Math.floor(look.getX() + direction.getX() * FORWARD_BLOCKS + 0.5D);
        int centerZ = (int) Math.floor(look.getZ() + direction.getZ() * FORWARD_BLOCKS + 0.5D);
        int floorY = look.getBlockY() - 1;

        if (floorY - 2 < world.getMinHeight() || floorY + type.clearHeight >= world.getMaxHeight()) {
            player.sendMessage(plugin.color("&cPřed tebou není v této výšce dost prostoru pro bezpečné umístění struktury."));
            return false;
        }
        if (!insideBorder(world, centerX, centerZ, type.radius)) {
            player.sendMessage(plugin.color("&cCelá struktura by se nevešla do hranice světa. Udělej pár kroků od okraje a příkaz zopakuj."));
            return false;
        }

        // Clearly warn, but do not demand a second confirmation command. Only the
        // structure's exact footprint and declared clearance volume will be written.
        player.sendMessage(plugin.color("&6HALLOWEEN &8» &eSpouštím FORCE stavbu &6" + type.displayId
                + "&e před tebou. Bloky i kontejnery uvnitř půdorysu mohou být přepsány; při chybě se stavba vrátí zpět."));
        player.sendMessage(plugin.color("&7Svět: &f" + world.getName() + " &8• &7Změny po dávkách, mimo půdorys se nesahá."));
        plugin.getLogger().warning("Manual FORCE structure build requested by " + player.getName()
                + ": type=" + type.displayId + ", world=" + world.getName()
                + ", center=" + centerX + "," + floorY + "," + centerZ
                + ", radius=" + type.radius);

        building = true;
        int minChunkX = (centerX - type.radius) >> 4;
        int maxChunkX = (centerX + type.radius) >> 4;
        int minChunkZ = (centerZ - type.radius) >> 4;
        int maxChunkZ = (centerZ + type.radius) >> 4;

        try {
            world.getChunksAtAsync(minChunkX, minChunkZ, maxChunkX, maxChunkZ, true, () -> {
                if (!plugin.isEnabled() || !building) return;
                try {
                    LinkedHashMap<BlockPos, Material> plan = createPlan(type, centerX, floorY, centerZ, snappedRotation(look.getYaw()));
                    if (plan.isEmpty() || plan.size() > MAX_PLANNED_BLOCKS) {
                        building = false;
                        player.sendMessage(plugin.color("&cStavbu jsem bezpečně zastavil: její plán překračuje limit "
                                + MAX_PLANNED_BLOCKS + " bloků."));
                        plugin.getLogger().warning("Rejected Halloween structure plan: type=" + type.displayId
                                + ", plannedBlocks=" + plan.size() + ", limit=" + MAX_PLANNED_BLOCKS);
                        return;
                    }
                    activeJob = new BuildJob(player, world, type, centerX, floorY, centerZ, plan, false);
                    activeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processBatch, 1L, 1L);
                } catch (RuntimeException ex) {
                    building = false;
                    plugin.getLogger().severe("Could not prepare Halloween structure " + type.displayId + ": " + ex);
                    player.sendMessage(plugin.color("&cStavbu se nepodařilo připravit. Svět nebyl změněn."));
                }
            });
        } catch (RuntimeException ex) {
            building = false;
            plugin.getLogger().severe("Could not load chunks for Halloween structure " + type.displayId + ": " + ex);
            player.sendMessage(plugin.color("&cOblast stavby se nepodařilo načíst. Svět nebyl změněn."));
            return false;
        }
        return true;
    }

    /**
     * Automatic, non-FORCE structure generation is allowed only for brand-new chunks
     * in explicitly listed worlds. Each automatic template is 15x15 and stays inside
     * its anchor chunk; trees, tile entities, unusual terrain and player-made surfaces
     * reject the candidate rather than being overwritten.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNewChunk(ChunkLoadEvent event) {
        if (!event.isNewChunk() || building || !plugin.isEventEnabled()) return;
        if (!plugin.getConfig().getBoolean("world-generation.structures.enabled", true)) return;
        World world = event.getWorld();
        if (!plugin.isEligibleAutomaticHalloweenWorld(world)) return;

        Chunk chunk = event.getChunk();
        if (chunk.getPersistentDataContainer().has(checkedChunkKey, PersistentDataType.BYTE)) return;
        // Mark before sampling: a failed/low-probability attempt is never repeated on
        // an old chunk later, even after restart or config reload.
        chunk.getPersistentDataContainer().set(checkedChunkKey, PersistentDataType.BYTE, (byte) 1);

        int oneIn = Math.max(64, Math.min(10_000,
                plugin.getConfig().getInt("world-generation.structures.one-in-new-chunks", 256)));
        long seed = world.getSeed()
                ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L)
                ^ 0x48414C4C53545255L;
        Random random = new Random(seed);
        if (random.nextInt(oneIn) != 0) return;

        int centerX = (chunk.getX() << 4) + 8;
        int centerZ = (chunk.getZ() << 4) + 8;
        if (!HalloweenStructureGeometry.footprintFitsChunk(
                chunk.getX(), chunk.getZ(), centerX, centerZ, 7)) return;
        int surfaceY = world.getHighestBlockYAt(centerX, centerZ);
        Biome surfaceBiome = world.getBlockAt(centerX, surfaceY, centerZ).getBiome();
        List<String> allowedBiomes = plugin.getConfig().getStringList("world-generation.allowed-biomes");
        if (!allowedBiomes.isEmpty()) {
            boolean listed = allowedBiomes.stream().anyMatch(name -> name != null
                    && name.trim().equalsIgnoreCase(surfaceBiome.name()));
            if (!listed) return;
        } else if (!isSuitableBiome(surfaceBiome)) {
            return;
        }

        StructureType type = random.nextBoolean() ? StructureType.AUTO_CEMETERY : StructureType.AUTO_VILLAGE;
        int floorY = surfaceY;
        if (!insideBorder(world, centerX, centerZ, type.radius)
                || floorY - 2 < world.getMinHeight()
                || floorY + type.clearHeight >= world.getMaxHeight()) return;
        if (!hasMinimumStructureDistance(world, centerX, centerZ)) return;
        if (!isSafeNaturalFootprint(world, type, centerX, floorY, centerZ)) return;

        LinkedHashMap<BlockPos, Material> plan;
        try {
            plan = createPlan(type, centerX, floorY, centerZ, 0);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Automatic Halloween structure plan failed in " + world.getName()
                    + " chunk " + chunk.getX() + "," + chunk.getZ() + ": " + ex.getMessage());
            return;
        }
        if (plan.isEmpty() || plan.size() > 10_000) return;

        building = true;
        plugin.getLogger().info("Automatic Halloween structure accepted: " + type.displayId
                + " in " + world.getName() + " at " + centerX + "," + floorY + "," + centerZ);
        activeJob = new BuildJob(null, world, type, centerX, floorY, centerZ, plan, true);
        activeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processBatch, 1L, 1L);
    }

    private boolean isSuitableBiome(Biome biome) {
        String name = biome.name();
        return name.contains("PLAINS") || name.contains("FOREST") || name.contains("TAIGA")
                || name.contains("SWAMP") || name.contains("MEADOW") || name.contains("GROVE")
                || name.contains("DARK_FOREST") || name.contains("WINDSWEPT_FOREST");
    }

    private boolean hasMinimumStructureDistance(World world, int x, int z) {
        int minDistance = Math.max(64, Math.min(4096,
                plugin.getConfig().getInt("world-generation.structures.minimum-distance-blocks", 384)));
        long minDistanceSquared = (long) minDistance * minDistance;
        String prefix = world.getUID().toString() + ";";
        for (String saved : generatedStructureIndex) {
            if (!saved.startsWith(prefix)) continue;
            String[] parts = saved.split(";");
            if (parts.length < 4) continue;
            try {
                int oldX = Integer.parseInt(parts[1]);
                int oldZ = Integer.parseInt(parts.length >= 5 ? parts[3] : parts[2]);
                long dx = (long) oldX - x;
                long dz = (long) oldZ - z;
                if (dx * dx + dz * dz < minDistanceSquared) return false;
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Ignoring malformed world-generation structure index entry: " + saved);
            }
        }
        return true;
    }

    private boolean isSafeNaturalFootprint(World world, StructureType type, int centerX, int floorY, int centerZ) {
        int minimumGround = Integer.MAX_VALUE;
        int maximumGround = Integer.MIN_VALUE;
        int maxSlope = Math.max(0, Math.min(2,
                plugin.getConfig().getInt("world-generation.structures.maximum-surface-variation", 2)));
        for (int dx = -type.radius; dx <= type.radius; dx++) {
            for (int dz = -type.radius; dz <= type.radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int groundY = world.getHighestBlockYAt(x, z);
                Material groundType = world.getBlockAt(x, groundY, z).getType();
                if (!isAutomaticNaturalGround(groundType)) return false;
                // WorldGuard is optional. If active, refuse an automatic structure
                // when the ground or any sampled level of its planned volume is protected.
                int[] protectionLevels = {floorY - 2, groundY, floorY + type.clearHeight / 2,
                        floorY + type.clearHeight - 1};
                for (int protectionY : protectionLevels) {
                    if (regionProtection.isProtected(new Location(world, x + 0.5D, protectionY, z + 0.5D))) {
                        return false;
                    }
                }
                Block ground = world.getBlockAt(x, groundY, z);
                if (ground.getState() instanceof TileState) return false;
                if (groundY < floorY - 2 || groundY > floorY + maxSlope) return false;
                minimumGround = Math.min(minimumGround, groundY);
                maximumGround = Math.max(maximumGround, groundY);

                // Reject trees, buildings, chests, fences and other obstructions above
                // natural ground. Small ground plants can be replaced safely.
                for (int y = groundY + 1; y < floorY + type.clearHeight; y++) {
                    Material material = world.getBlockAt(x, y, z).getType();
                    if (material.isAir() || isReplaceableGroundPlant(material)) continue;
                    return false;
                }
                // Never clear inventories, signs, skulls or other block entities
                // anywhere in the planned write volume, including hidden containers.
                for (int y = Math.max(world.getMinHeight(), floorY - 2);
                     y < Math.min(world.getMaxHeight(), floorY + type.clearHeight); y++) {
                    if (world.getBlockAt(x, y, z).getState() instanceof TileState) return false;
                }
            }
        }
        return maximumGround - minimumGround <= maxSlope;
    }

    private boolean isAutomaticNaturalGround(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM,
                    MOSS_BLOCK, MUD, CLAY, SAND, RED_SAND, GRAVEL, SNOW_BLOCK -> true;
            default -> false;
        };
    }

    private boolean isReplaceableGroundPlant(Material material) {
        return material == Material.SHORT_GRASS || material == Material.TALL_GRASS
                || material == Material.FERN || material == Material.LARGE_FERN
                || material == Material.DEAD_BUSH || material == Material.DANDELION
                || material == Material.POPPY || material == Material.BLUE_ORCHID
                || material == Material.ALLIUM || material == Material.AZURE_BLUET
                || material == Material.RED_TULIP || material == Material.ORANGE_TULIP
                || material == Material.WHITE_TULIP || material == Material.PINK_TULIP
                || material == Material.OXEYE_DAISY || material == Material.CORNFLOWER
                || material == Material.LILY_OF_THE_VALLEY || material == Material.SNOW;
    }

    private void processBatch() {
        BuildJob job = activeJob;
        if (job == null) {
            finishTask();
            return;
        }

        if (job.rollingBack) {
            rollbackBatch(job);
            return;
        }

        try {
            int start = job.cursor;
            int end = Math.min(job.operations.size(), start + BLOCKS_PER_TICK);
            for (int index = start; index < end; index++) {
                Map.Entry<BlockPos, Material> entry = job.operations.get(index);
                BlockPos pos = entry.getKey();
                Block block = job.world.getBlockAt(pos.x(), pos.y(), pos.z());
                Material desired = entry.getValue();
                if (block.getType() == desired) continue;
                job.snapshots.putIfAbsent(pos, block.getState());
                block.setType(desired, false);
            }

            job.cursor = end;
            long now = System.currentTimeMillis();
            if (job.player != null && job.player.isOnline() && now - job.lastProgressNotice >= 5_000L && job.cursor < job.plan.size()) {
                job.lastProgressNotice = now;
                int percent = (int) ((long) job.cursor * 100L / job.plan.size());
                job.player.sendMessage(plugin.color("&7Stavba " + job.type.displayId + ": &e" + percent + "%"));
            }
            if (job.cursor >= job.plan.size()) completeBuild(job);
        } catch (RuntimeException ex) {
            beginRollback(job, "chyba při zápisu bloků: " + ex.getClass().getSimpleName());
            plugin.getLogger().severe("Halloween structure build failed; rolling back " + job.type.displayId
                    + " in " + job.world.getName() + ": " + ex);
        }
    }

    private void rollbackBatch(BuildJob job) {
        int end = Math.min(job.rollbackEntries.size(), job.rollbackCursor + BLOCKS_PER_TICK);
        try {
            for (int i = job.rollbackCursor; i < end; i++) {
                Map.Entry<BlockPos, BlockState> entry = job.rollbackEntries.get(i);
                try {
                    entry.getValue().update(true, false);
                } catch (RuntimeException ex) {
                    plugin.getLogger().severe("Failed to restore a block while rolling back Halloween structure "
                            + job.type.displayId + " at " + entry.getKey() + ": " + ex.getMessage());
                }
            }
        } finally {
            job.rollbackCursor = end;
        }
        if (job.rollbackCursor >= job.rollbackEntries.size()) {
            if (job.player != null && job.player.isOnline()) {
                job.player.sendMessage(plugin.color("&cStavba " + job.type.displayId
                        + " selhala a rollback byl dokončen. Důvod: " + job.failure));
            }
            plugin.getLogger().warning("Halloween structure rollback finished: " + job.type.displayId
                    + ", restoredBlocks=" + job.rollbackEntries.size());
            finishTask();
        }
    }

    private void beginRollback(BuildJob job, String reason) {
        job.failure = reason;
        job.rollingBack = true;
        job.rollbackEntries = new ArrayList<>(job.snapshots.entrySet());
        Collections.reverse(job.rollbackEntries);
        job.rollbackCursor = 0;
        if (job.rollbackEntries.isEmpty()) {
            if (job.player != null && job.player.isOnline()) job.player.sendMessage(plugin.color("&cStavba se zastavila ještě před změnou bloků: " + reason));
            finishTask();
        }
    }

    private void completeBuild(BuildJob job) {
        try {
            if (job.automatic) {
                String indexEntry = job.world.getUID() + ";" + job.centerX + ";" + (job.floorY + 1)
                        + ";" + job.centerZ + ";" + job.type.name();
                generatedStructureIndex.add(indexEntry);
                plugin.getConfig().set("world-generation.generated-structure-index", new ArrayList<>(generatedStructureIndex));
                Chunk anchor = job.world.getChunkAt(job.centerX >> 4, job.centerZ >> 4);
                anchor.getPersistentDataContainer().set(structureChunkKey, PersistentDataType.STRING, job.type.name());
            } else if (job.type == StructureType.VILLAGE) {
                plugin.getConfig().set("haunted-village.enabled", true);
                plugin.getConfig().set("haunted-village.world", job.world.getName());
                plugin.getConfig().set("haunted-village.x", job.centerX + 0.5D);
                plugin.getConfig().set("haunted-village.y", job.floorY + 1.0D);
                plugin.getConfig().set("haunted-village.z", job.centerZ + 0.5D);
            } else if (job.type == StructureType.VAMPIRE_ARENA) {
                plugin.getConfig().set("bosses.vampire.arena.configured", true);
                plugin.getConfig().set("bosses.vampire.arena.generated", true);
                plugin.getConfig().set("bosses.vampire.arena.world", job.world.getName());
                plugin.getConfig().set("bosses.vampire.arena.x", job.centerX + 0.5D);
                plugin.getConfig().set("bosses.vampire.arena.y", job.floorY + 1.0D);
                plugin.getConfig().set("bosses.vampire.arena.z", job.centerZ + 0.5D);
            } else if (job.type == StructureType.GOBLIN_ARENA) {
                plugin.getConfig().set("bosses.goblin.arena.configured", true);
                plugin.getConfig().set("bosses.goblin.arena.generated", true);
                plugin.getConfig().set("bosses.goblin.arena.world", job.world.getName());
                plugin.getConfig().set("bosses.goblin.arena.x", job.centerX + 0.5D);
                plugin.getConfig().set("bosses.goblin.arena.y", job.floorY + 1.0D);
                plugin.getConfig().set("bosses.goblin.arena.z", job.centerZ + 0.5D);
            }
            plugin.saveConfig();
            plugin.getLogger().info((job.automatic ? "Automatic" : "Manual")
                    + " Halloween structure build completed: " + job.type.displayId
                    + ", world=" + job.world.getName() + ", changedBlocks=" + job.snapshots.size());
            if (job.player != null && job.player.isOnline()) {
                job.player.sendMessage(plugin.color("&aHotovo! &e" + job.type.displayId + " &aje skutečně postaven."));
                job.player.sendMessage(plugin.color("&7Svět: &f" + job.world.getName() + " &8• &7Střed: &f"
                        + job.centerX + " " + (job.floorY + 1) + " " + job.centerZ
                        + " &8• &7Změněné bloky: &f" + job.snapshots.size()));
                if (job.type == StructureType.VAMPIRE_ARENA) {
                    job.player.sendMessage(plugin.color("&7Aréna je postavená; samotný boss vyžaduje platnou konfiguraci MythicMobs/ModelEngine."));
                }
                if (job.type == StructureType.GOBLIN_ARENA) {
                    job.player.sendMessage(plugin.color("&7Aréna je postavená. Samostatný Goblin King encounter zatím není tímto příkazem automaticky spuštěn."));
                }
            }
            finishTask();
        } catch (RuntimeException ex) {
            beginRollback(job, "chyba při ukládání konfigurace: " + ex.getClass().getSimpleName());
            plugin.getLogger().severe("Could not finalize Halloween structure " + job.type.displayId + ": " + ex);
        }
    }

    private void finishTask() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
        activeJob = null;
        building = false;
    }

    public void stop() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
        BuildJob job = activeJob;
        activeJob = null;
        building = false;
        if (job == null || job.snapshots.isEmpty()) return;

        // Plugin shutdown cancels scheduled tasks, so restore synchronously rather
        // than leave half a FORCE build behind. This path runs only on server disable.
        List<Map.Entry<BlockPos, BlockState>> restore = new ArrayList<>(job.snapshots.entrySet());
        Collections.reverse(restore);
        for (Map.Entry<BlockPos, BlockState> entry : restore) {
            try {
                entry.getValue().update(true, false);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Shutdown rollback failed for " + entry.getKey() + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().warning("Plugin disabled during Halloween build; attempted rollback of "
                + restore.size() + " changed blocks.");
    }

    private boolean insideBorder(World world, int centerX, int centerZ, int radius) {
        WorldBorderCheck:
        {
            if (!world.getWorldBorder().isInside(new Location(world, centerX - radius, 0, centerZ))) break WorldBorderCheck;
            if (!world.getWorldBorder().isInside(new Location(world, centerX + radius, 0, centerZ))) break WorldBorderCheck;
            if (!world.getWorldBorder().isInside(new Location(world, centerX, 0, centerZ - radius))) break WorldBorderCheck;
            return world.getWorldBorder().isInside(new Location(world, centerX, 0, centerZ + radius));
        }
        return false;
    }

    private int snappedRotation(float yaw) {
        return Math.floorMod(Math.round(yaw / 90.0F), 4);
    }

    private StructureType resolveType(String id) {
        if (id == null) return null;
        return switch (id.trim().toLowerCase(Locale.ROOT)) {
            case "hřbitov", "hrbitov", "cemetery", "graveyard" -> StructureType.CEMETERY;
            case "vesnice", "village", "haunted-village", "hauntedvillage" -> StructureType.VILLAGE;
            case "aréna", "arena" -> StructureType.ARENA;
            case "vampirearena", "vampire-arena", "vampire" -> StructureType.VAMPIRE_ARENA;
            case "goblinarena", "goblin-arena", "goblin" -> StructureType.GOBLIN_ARENA;
            default -> null;
        };
    }

    private LinkedHashMap<BlockPos, Material> createPlan(StructureType type, int cx, int floorY, int cz, int rotation) {
        LinkedHashMap<BlockPos, Material> plan = new LinkedHashMap<>();
        int r = type.radius;

        // Hard-bounded FORCE volume: two foundation layers, a known clearance
        // column, then deterministic floor and structure layers.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (!insideFootprint(type, x, z)) continue;
                put(plan, type, cx, floorY, cz, rotation, x, floorY - 2, z,
                        type == StructureType.CEMETERY || type == StructureType.AUTO_CEMETERY
                                ? Material.DEEPSLATE_BRICKS : foundation(type));
                put(plan, type, cx, floorY, cz, rotation, x, floorY - 1, z, foundation(type));
                for (int dy = 0; dy < type.clearHeight; dy++) {
                    put(plan, type, cx, floorY, cz, rotation, x, floorY + dy, z, Material.AIR);
                }
                Material floor = floorAt(type, x, z);
                put(plan, type, cx, floorY, cz, rotation, x, floorY, z, floor);
            }
        }

        switch (type) {
            case CEMETERY -> buildCemetery(plan, type, cx, floorY, cz, rotation);
            case AUTO_CEMETERY -> buildAutoCemetery(plan, type, cx, floorY, cz, rotation);
            case VILLAGE -> buildVillage(plan, type, cx, floorY, cz, rotation);
            case AUTO_VILLAGE -> buildAutoVillage(plan, type, cx, floorY, cz, rotation);
            case ARENA, VAMPIRE_ARENA, GOBLIN_ARENA -> buildArena(plan, type, cx, floorY, cz, rotation);
        }
        return plan;
    }

    private boolean insideFootprint(StructureType type, int x, int z) {
        return HalloweenStructureGeometry.insideFootprint(x, z, type.radius, type.circular);
    }

    private Material foundation(StructureType type) {
        return type == StructureType.VILLAGE || type == StructureType.AUTO_VILLAGE ? Material.DIRT
                : type == StructureType.CEMETERY || type == StructureType.AUTO_CEMETERY ? Material.DEEPSLATE_BRICKS
                : type == StructureType.GOBLIN_ARENA ? Material.COBBLED_DEEPSLATE
                : Material.POLISHED_BLACKSTONE;
    }

    private Material floorAt(StructureType type, int x, int z) {
        if (type == StructureType.VILLAGE || type == StructureType.AUTO_VILLAGE) {
            if (Math.abs(x) <= 1 || Math.abs(z) <= 1) return Material.COARSE_DIRT;
            if (Math.abs(x) <= 2 && Math.abs(z) <= 2) return Material.PODZOL;
            return Material.GRASS_BLOCK;
        }
        if (type == StructureType.CEMETERY || type == StructureType.AUTO_CEMETERY) {
            if (Math.abs(x) == type.radius || Math.abs(z) == type.radius) return Material.MOSSY_STONE_BRICKS;
            if ((x * 3 + z * 5) % 11 == 0) return Material.CRACKED_STONE_BRICKS;
            return Material.MOSSY_COBBLESTONE;
        }
        int d2 = x * x + z * z;
        int ring = (int) Math.sqrt(d2);
        if (type == StructureType.GOBLIN_ARENA) {
            if (ring % 7 <= 1) return Material.MOSSY_STONE_BRICKS;
            return Material.COBBLED_DEEPSLATE;
        }
        if (ring % 8 <= 1) return Material.POLISHED_BLACKSTONE_BRICKS;
        return type == StructureType.VAMPIRE_ARENA ? Material.DEEPSLATE_TILES : Material.POLISHED_BLACKSTONE;
    }

    private void buildCemetery(LinkedHashMap<BlockPos, Material> plan, StructureType type, int cx, int y, int cz, int rot) {
        int r = type.radius;
        for (int p = -r; p <= r; p++) {
            put(plan, type, cx, y, cz, rot, p, y + 1, -r, Material.MOSSY_STONE_BRICK_WALL);
            put(plan, type, cx, y, cz, rot, p, y + 1, r, Material.MOSSY_STONE_BRICK_WALL);
            put(plan, type, cx, y, cz, rot, -r, y + 1, p, Material.MOSSY_STONE_BRICK_WALL);
            put(plan, type, cx, y, cz, rot, r, y + 1, p, Material.MOSSY_STONE_BRICK_WALL);
        }
        // Open front gate.
        for (int x = -1; x <= 1; x++) {
            put(plan, type, cx, y, cz, rot, x, y + 1, -r, Material.AIR);
        }

        // Grave rows with headstones and candles.
        for (int x : new int[]{-6, -3, 0, 3, 6}) {
            for (int z : new int[]{-5, 0, 5}) {
                if (x == 0 && z == 0) continue;
                put(plan, type, cx, y, cz, rot, x, y + 1, z, Material.COBBLESTONE_WALL);
                put(plan, type, cx, y, cz, rot, x, y + 2, z, Material.COBBLESTONE_WALL);
                put(plan, type, cx, y, cz, rot, x, y + 1, z + 1, Material.SOUL_LANTERN);
            }
        }
        // Compact crypt/mausoleum at the rear.
        for (int x = -3; x <= 3; x++) {
            for (int z = 4; z <= 8; z++) {
                if (Math.abs(x) == 3 || z == 4 || z == 8) {
                    for (int dy = 1; dy <= 3; dy++) {
                        if (z == 4 && x == 0 && dy <= 2) continue;
                        put(plan, type, cx, y, cz, rot, x, y + dy, z, Material.DEEPSLATE_BRICKS);
                    }
                }
                if (Math.abs(x) <= 3 && z >= 4 && z <= 8) {
                    put(plan, type, cx, y, cz, rot, x, y + 4, z, Material.DEEPSLATE_TILES);
                }
            }
        }
        put(plan, type, cx, y, cz, rot, 0, y + 1, -9, Material.SOUL_LANTERN);
    }

    private void buildAutoCemetery(LinkedHashMap<BlockPos, Material> plan, StructureType type,
                                   int cx, int y, int cz, int rot) {
        int r = type.radius;
        for (int p = -r; p <= r; p++) {
            put(plan, type, cx, y, cz, rot, p, y + 1, -r, Material.MOSSY_STONE_BRICK_WALL);
            put(plan, type, cx, y, cz, rot, p, y + 1, r, Material.MOSSY_STONE_BRICK_WALL);
            put(plan, type, cx, y, cz, rot, -r, y + 1, p, Material.MOSSY_STONE_BRICK_WALL);
            put(plan, type, cx, y, cz, rot, r, y + 1, p, Material.MOSSY_STONE_BRICK_WALL);
        }
        for (int x = -1; x <= 1; x++) {
            put(plan, type, cx, y, cz, rot, x, y + 1, -r, Material.AIR);
        }
        // Compact 15x15 variant: all graves and the crypt stay inside its footprint.
        for (int x : new int[]{-4, -1, 2, 5}) {
            for (int z : new int[]{-3, 1, 5}) {
                put(plan, type, cx, y, cz, rot, x, y + 1, z, Material.COBBLESTONE_WALL);
                put(plan, type, cx, y, cz, rot, x, y + 2, z, Material.COBBLESTONE_WALL);
                put(plan, type, cx, y, cz, rot, x, y + 1, z + 1, Material.SOUL_LANTERN);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = 3; z <= 6; z++) {
                if (Math.abs(x) == 2 || z == 3 || z == 6) {
                    for (int dy = 1; dy <= 2; dy++) {
                        if (z == 3 && x == 0) continue;
                        put(plan, type, cx, y, cz, rot, x, y + dy, z, Material.DEEPSLATE_BRICKS);
                    }
                }
                if (Math.abs(x) <= 2) {
                    put(plan, type, cx, y, cz, rot, x, y + 3, z, Material.DEEPSLATE_TILES);
                }
            }
        }
        put(plan, type, cx, y, cz, rot, 0, y + 1, -6, Material.SOUL_LANTERN);
    }

    private void buildVillage(LinkedHashMap<BlockPos, Material> plan, StructureType type, int cx, int y, int cz, int rot) {
        // A central well and four distinct cottages; village paths and small props
        // are part of the deterministic template, not random scattered blocks.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    put(plan, type, cx, y, cz, rot, x, y + 1, z, Material.STONE_BRICKS);
                } else {
                    put(plan, type, cx, y, cz, rot, x, y + 1, z, Material.WATER);
                }
            }
        }
        for (int x : new int[]{-8, 8}) {
            for (int z : new int[]{-8, 8}) {
                int variant = Math.abs(x + z) % 3;
                buildCottage(plan, type, cx, y, cz, rot, x, z, variant);
            }
        }
        for (int x = -13; x <= 13; x += 2) {
            put(plan, type, cx, y, cz, rot, x, y + 1, -13, Material.SPRUCE_FENCE);
            put(plan, type, cx, y, cz, rot, x, y + 1, 13, Material.SPRUCE_FENCE);
        }
        for (int z = -13; z <= 13; z += 2) {
            put(plan, type, cx, y, cz, rot, -13, y + 1, z, Material.SPRUCE_FENCE);
            put(plan, type, cx, y, cz, rot, 13, y + 1, z, Material.SPRUCE_FENCE);
        }
        for (int x : new int[]{-12, -4, 4, 12}) {
            put(plan, type, cx, y, cz, rot, x, y + 1, 0, Material.JACK_O_LANTERN);
        }
        put(plan, type, cx, y, cz, rot, -11, y + 1, -11, Material.PUMPKIN);
        put(plan, type, cx, y, cz, rot, 11, y + 1, 11, Material.PUMPKIN);
        put(plan, type, cx, y, cz, rot, -11, y + 1, 11, Material.HAY_BLOCK);
        put(plan, type, cx, y, cz, rot, 11, y + 1, -11, Material.HAY_BLOCK);
    }

    private void buildAutoVillage(LinkedHashMap<BlockPos, Material> plan, StructureType type,
                                  int cx, int y, int cz, int rot) {
        // Compact two-house hamlet that fits completely inside its brand-new 16x16 chunk.
        for (int x = -6; x <= 6; x++) {
            put(plan, type, cx, y, cz, rot, x, y + 1, 0, Material.COARSE_DIRT);
            put(plan, type, cx, y, cz, rot, 0, y + 1, x, Material.COARSE_DIRT);
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                put(plan, type, cx, y, cz, rot, x, y + 1, z,
                        Math.abs(x) == 1 || Math.abs(z) == 1 ? Material.STONE_BRICKS : Material.WATER);
            }
        }
        buildAutoCottage(plan, type, cx, y, cz, rot, -3, -3, Material.SPRUCE_PLANKS);
        buildAutoCottage(plan, type, cx, y, cz, rot, 3, 3, Material.DARK_OAK_PLANKS);
        put(plan, type, cx, y, cz, rot, -6, y + 1, 6, Material.JACK_O_LANTERN);
        put(plan, type, cx, y, cz, rot, 6, y + 1, -6, Material.PUMPKIN);
        put(plan, type, cx, y, cz, rot, -6, y + 1, -6, Material.HAY_BLOCK);
        put(plan, type, cx, y, cz, rot, 6, y + 1, 6, Material.HAY_BLOCK);
        for (int x = -6; x <= 6; x++) {
            if (Math.abs(x) > 1) {
                put(plan, type, cx, y, cz, rot, x, y + 1, -7, Material.SPRUCE_FENCE);
                put(plan, type, cx, y, cz, rot, x, y + 1, 7, Material.SPRUCE_FENCE);
            }
        }
        for (int z = -6; z <= 6; z++) {
            if (Math.abs(z) > 1) {
                put(plan, type, cx, y, cz, rot, -7, y + 1, z, Material.SPRUCE_FENCE);
                put(plan, type, cx, y, cz, rot, 7, y + 1, z, Material.SPRUCE_FENCE);
            }
        }
    }

    private void buildAutoCottage(LinkedHashMap<BlockPos, Material> plan, StructureType type,
                                  int cx, int y, int cz, int rot, int ox, int oz, Material planks) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    for (int dy = 1; dy <= 3; dy++) {
                        put(plan, type, cx, y, cz, rot, ox + dx, y + dy, oz + dz, Material.SPRUCE_LOG);
                    }
                }
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    for (int dy = 1; dy <= 3; dy++) {
                        if (dy == 2 && (dx == 0 || dz == 0)) continue;
                        put(plan, type, cx, y, cz, rot, ox + dx, y + dy, oz + dz, planks);
                    }
                }
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    put(plan, type, cx, y, cz, rot, ox + dx, y + 1, oz + dz, Material.SPRUCE_PLANKS);
                }
            }
        }
        for (int layer = 0; layer <= 1; layer++) {
            int span = 2 - layer;
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    if (Math.abs(dx) == span || Math.abs(dz) == span || layer == 1) {
                        put(plan, type, cx, y, cz, rot, ox + dx, y + 4 + layer, oz + dz, planks);
                    }
                }
            }
        }
        put(plan, type, cx, y, cz, rot, ox, y + 1, oz - 2, Material.LANTERN);
    }

    private void buildCottage(LinkedHashMap<BlockPos, Material> plan, StructureType type, int cx, int y, int cz,
                              int rot, int ox, int oz, int variant) {
        Material log = variant == 0 ? Material.SPRUCE_LOG : variant == 1 ? Material.DARK_OAK_LOG : Material.STRIPPED_SPRUCE_LOG;
        Material planks = variant == 0 ? Material.SPRUCE_PLANKS : variant == 1 ? Material.DARK_OAK_PLANKS : Material.OAK_PLANKS;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
                    for (int dy = 1; dy <= 3; dy++) put(plan, type, cx, y, cz, rot, ox + dx, y + dy, oz + dz, log);
                }
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                    for (int dy = 1; dy <= 3; dy++) {
                        if (dz == -3 && dx == 0) continue; // open entrance, no door state mismatch
                        if (dy == 2 && (Math.abs(dx) == 3 && dz == 0 || Math.abs(dz) == 3 && dx == 0)) continue;
                        put(plan, type, cx, y, cz, rot, ox + dx, y + dy, oz + dz, planks);
                    }
                }
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                    put(plan, type, cx, y, cz, rot, ox + dx, y + 1, oz + dz, Material.SPRUCE_PLANKS);
                }
            }
        }
        // Two simple roof layers create a cottage silhouette without requiring a pack.
        for (int layer = 0; layer <= 2; layer++) {
            int span = 3 - layer;
            int yy = y + 4 + layer;
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    if (Math.abs(dx) == span || Math.abs(dz) == span || layer == 2) {
                        put(plan, type, cx, y, cz, rot, ox + dx, yy, oz + dz, planks);
                    }
                }
            }
        }
    }

    private void buildArena(LinkedHashMap<BlockPos, Material> plan, StructureType type,
                            int cx, int y, int cz, int rot) {
        int r = type.radius;
        Material wall = type == StructureType.GOBLIN_ARENA ? Material.MOSSY_STONE_BRICK_WALL
                : Material.POLISHED_BLACKSTONE_BRICK_WALL;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (!insideFootprint(type, x, z)) continue;
                double dist = Math.sqrt(x * x + z * z);
                if (Math.abs(dist - r) > 0.72D) continue;
                boolean gate = (Math.abs(x) <= 1 && Math.abs(z) >= r - 1)
                        || (Math.abs(z) <= 1 && Math.abs(x) >= r - 1);
                if (gate) continue;
                for (int dy = 1; dy <= 3; dy++) {
                    put(plan, type, cx, y, cz, rot, x, y + dy, z, wall);
                }
                if (((x + z) & 1) == 0) {
                    put(plan, type, cx, y, cz, rot, x, y + 4, z,
                            type == StructureType.GOBLIN_ARENA ? Material.MOSSY_STONE_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }

        int towerCount = type == StructureType.VAMPIRE_ARENA ? 8
                : type == StructureType.GOBLIN_ARENA ? 6 : 4;
        double towerRadius = r * 0.68D;
        for (int i = 0; i < towerCount; i++) {
            double angle = (Math.PI * 2.0D * i / towerCount) + Math.PI / 8.0D;
            int tx = (int) Math.round(Math.cos(angle) * towerRadius);
            int tz = (int) Math.round(Math.sin(angle) * towerRadius);
            Material tower = type == StructureType.GOBLIN_ARENA ? Material.COBBLED_DEEPSLATE
                    : Material.POLISHED_BLACKSTONE_BRICKS;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        for (int dy = 1; dy <= (type == StructureType.VAMPIRE_ARENA ? 8 : 6); dy++) {
                            put(plan, type, cx, y, cz, rot, tx + dx, y + dy, tz + dz, tower);
                        }
                    } else if (dx == 0 && dz == 0) {
                        put(plan, type, cx, y, cz, rot, tx + dx, y + 1, tz + dz,
                                type == StructureType.VAMPIRE_ARENA ? Material.SOUL_LANTERN : Material.JACK_O_LANTERN);
                    }
                }
            }
        }

        // Raised boss dais and four ritual lights.
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    put(plan, type, cx, y, cz, rot, x, y + 1, z, wall);
                } else {
                    put(plan, type, cx, y, cz, rot, x, y + 1, z,
                            type == StructureType.GOBLIN_ARENA ? Material.MOSSY_STONE_BRICKS : Material.DEEPSLATE_TILES);
                }
            }
        }
        for (int[] point : new int[][]{{-5,-5},{-5,5},{5,-5},{5,5}}) {
            put(plan, type, cx, y, cz, rot, point[0], y + 1, point[1],
                    type == StructureType.GOBLIN_ARENA ? Material.JACK_O_LANTERN : Material.SOUL_LANTERN);
        }
    }

    private void put(LinkedHashMap<BlockPos, Material> plan, StructureType type,
                     int cx, int floorY, int cz, int rot, int lx, int y, int lz, Material material) {
        // Rotation is snapped to cardinal directions; the entire plan remains in the
        // exact square/circle footprint. No code path writes outside those bounds.
        HalloweenStructureGeometry.Offset offset = HalloweenStructureGeometry.rotate(rot, lx, lz);
        int rx = offset.x();
        int rz = offset.z();
        if (Math.abs(rx) > type.radius || Math.abs(rz) > type.radius
                || (type.circular && rx * rx + rz * rz > type.radius * type.radius)) return;
        if (y < floorY - 2 || y >= floorY + type.clearHeight) return;
        plan.put(new BlockPos(cx + rx, y, cz + rz), material);
    }
}
