package cz.halloween.core;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Safe, deterministic structure placement for the overworld.
 *
 * The generator only considers one candidate chunk per large region and never
 * forces chunks to load. A candidate is built only after its complete footprint
 * is loaded, the land is natural and nearly level, and the space above it is
 * clear. Every chunk touched by a structure is persistently marked so reloads
 * and overlapping village/graveyard candidates cannot duplicate or overwrite it.
 */
public final class HalloweenWorldStructureGenerator implements Listener {
    private static final int VILLAGE_REGION_CHUNKS = 10;
    private static final int GRAVEYARD_REGION_CHUNKS = 6;
    private static final int MAX_SLOPE = 2;
    private static final int MAX_RETRIES = 120;

    private final HalloweenCore plugin;
    private final NamespacedKey structureKey;
    private final Queue<Candidate> pending = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Set<String> warned = new HashSet<>();
    private BukkitTask task;
    private long villagesBuilt;
    private long graveyardsBuilt;
    private long candidatesSkipped;

    public HalloweenWorldStructureGenerator(HalloweenCore plugin) {
        this.plugin = plugin;
        this.structureKey = new NamespacedKey(plugin, "world_structure_v1");
    }

    public void start() {
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processQueue, 20L, 20L);
        }
        scanLoadedChunks();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        pending.clear();
        queued.clear();
    }

    public void scanLoadedChunks() {
        if (!isEnabled()) return;
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            for (Chunk chunk : world.getLoadedChunks()) consider(chunk);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (isEnabled()) consider(event.getChunk());
    }

    public int getPendingCount() {
        return pending.size();
    }

    public long getVillagesBuilt() {
        return villagesBuilt;
    }

    public long getGraveyardsBuilt() {
        return graveyardsBuilt;
    }

    public long getCandidatesSkipped() {
        return candidatesSkipped;
    }

    public boolean isEnabled() {
        return isEnabledByConfig() && plugin.isEventEnabled();
    }

    private boolean isEnabledByConfig() {
        return plugin.getConfig().getBoolean("world-structures.enabled", true);
    }

    private boolean isEnabled() {
        return isEnabledByConfig() && plugin.isEventEnabled();
    }

    private void consider(Chunk chunk) {
        if (chunk == null || !isEnabled() || chunk.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (chunk.getPersistentDataContainer().has(structureKey, PersistentDataType.STRING)) return;

        if (isRegionalCandidate(chunk, StructureType.HAUNTED_VILLAGE, VILLAGE_REGION_CHUNKS)) {
            enqueue(chunk, StructureType.HAUNTED_VILLAGE, 0);
        }
        if (isRegionalCandidate(chunk, StructureType.CURSED_GRAVEYARD, GRAVEYARD_REGION_CHUNKS)) {
            enqueue(chunk, StructureType.CURSED_GRAVEYARD, 0);
        }
    }

    private boolean isRegionalCandidate(Chunk chunk, StructureType type, int regionSize) {
        int regionX = Math.floorDiv(chunk.getX(), regionSize);
        int regionZ = Math.floorDiv(chunk.getZ(), regionSize);
        long salt = type == StructureType.HAUNTED_VILLAGE ? 0x4841554E544544L : 0x4752415645594152L;
        long seed = chunk.getWorld().getSeed()
                ^ (regionX * 341873128712L)
                ^ (regionZ * 132897987541L)
                ^ salt;
        Random random = new Random(seed);
        int targetX = regionX * regionSize + random.nextInt(regionSize);
        int targetZ = regionZ * regionSize + random.nextInt(regionSize);
        return chunk.getX() == targetX && chunk.getZ() == targetZ;
    }

    private void enqueue(Chunk chunk, StructureType type, int retries) {
        String key = candidateKey(chunk.getWorld(), chunk.getX(), chunk.getZ(), type);
        if (queued.add(key)) pending.offer(new Candidate(chunk.getWorld(), chunk.getX(), chunk.getZ(), type, retries, key));
    }

    private String candidateKey(World world, int chunkX, int chunkZ, StructureType type) {
        return world.getUID() + ":" + chunkX + ":" + chunkZ + ":" + type.name();
    }

    private void processQueue() {
        if (!isEnabled()) {
            pending.clear();
            queued.clear();
            return;
        }

        // One preflight/build at a time: avoid long server ticks when explorers
        // discover a lot of new chunks together.
        Candidate candidate = pending.poll();
        if (candidate == null) return;
        queued.remove(candidate.key());

        World world = candidate.world();
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) return;
        if (!world.isChunkLoaded(candidate.chunkX(), candidate.chunkZ())) return;

        int radius = candidate.type().radius;
        int centerX = candidate.chunkX() * 16 + 8;
        int centerZ = candidate.chunkZ() * 16 + 8;

        if (!footprintLoaded(world, centerX, centerZ, radius)) {
            if (candidate.retries() < MAX_RETRIES) {
                enqueueCandidate(candidate, candidate.retries() + 1);
            } else {
                candidatesSkipped++;
            }
            return;
        }

        try {
            if (touchesExistingStructure(world, centerX, centerZ, radius)) {
                candidatesSkipped++;
                return;
            }

            Terrain terrain = inspectTerrain(world, centerX, centerZ, radius, candidate.type().height);
            if (terrain == null) {
                candidatesSkipped++;
                return;
            }

            Random random = new Random(world.getSeed()
                    ^ ((long) candidate.chunkX() * 341873128712L)
                    ^ ((long) candidate.chunkZ() * 132897987541L)
                    ^ candidate.type().salt);
            int baseY = terrain.maxY() + 1;

            if (candidate.type() == StructureType.HAUNTED_VILLAGE) {
                buildVillage(world, centerX, baseY, centerZ, random);
                villagesBuilt++;
            } else {
                buildGraveyard(world, centerX, baseY, centerZ, random);
                graveyardsBuilt++;
            }
            markFootprint(world, centerX, centerZ, radius, candidate.type().name().toLowerCase(java.util.Locale.ROOT));
            plugin.getLogger().info("Generated " + candidate.type().label + " in " + world.getName()
                    + " at " + centerX + ", " + baseY + ", " + centerZ + ".");
        } catch (RuntimeException ex) {
            String warningKey = ex.getClass().getName();
            if (warned.add(warningKey)) {
                plugin.getLogger().warning("Halloween structure generation stopped safely after "
                        + warningKey + ". Further errors of this type are suppressed.");
            }
            candidatesSkipped++;
        }
    }

    private void enqueueCandidate(Candidate candidate, int retries) {
        if (!isEnabled()) return;
        if (!candidate.world().isChunkLoaded(candidate.chunkX(), candidate.chunkZ())) return;
        String key = candidateKey(candidate.world(), candidate.chunkX(), candidate.chunkZ(), candidate.type());
        if (queued.add(key)) {
            pending.offer(new Candidate(candidate.world(), candidate.chunkX(), candidate.chunkZ(),
                    candidate.type(), retries, key));
        }
    }

    private boolean footprintLoaded(World world, int centerX, int centerZ, int radius) {
        int minChunkX = Math.floorDiv(centerX - radius, 16);
        int maxChunkX = Math.floorDiv(centerX + radius, 16);
        int minChunkZ = Math.floorDiv(centerZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    private boolean touchesExistingStructure(World world, int centerX, int centerZ, int radius) {
        int minChunkX = Math.floorDiv(centerX - radius, 16);
        int maxChunkX = Math.floorDiv(centerX + radius, 16);
        int minChunkZ = Math.floorDiv(centerZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                if (chunk.getPersistentDataContainer().has(structureKey, PersistentDataType.STRING)) return true;
            }
        }
        return false;
    }

    private void markFootprint(World world, int centerX, int centerZ, int radius, String structureId) {
        int minChunkX = Math.floorDiv(centerX - radius, 16);
        int maxChunkX = Math.floorDiv(centerX + radius, 16);
        int minChunkZ = Math.floorDiv(centerZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                chunk.getPersistentDataContainer().set(structureKey, PersistentDataType.STRING, structureId);
            }
        }
    }

    private Terrain inspectTerrain(World world, int centerX, int centerZ, int radius, int structureHeight) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int width = radius * 2 + 1;
        int[][] heights = new int[width][width];

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int surfaceY = findNaturalSurface(world, x, z);
                if (surfaceY == Integer.MIN_VALUE) return null;
                heights[dx + radius][dz + radius] = surfaceY;
                minY = Math.min(minY, surfaceY);
                maxY = Math.max(maxY, surfaceY);
            }
        }

        if (maxY - minY > MAX_SLOPE) return null;
        int safeCeiling = maxY + structureHeight + 2;
        if (safeCeiling >= world.getMaxHeight() - 2) return null;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int surfaceY = heights[dx + radius][dz + radius];
                for (int y = surfaceY + 1; y <= safeCeiling; y++) {
                    Material material = world.getBlockAt(x, y, z).getType();
                    if (!material.isAir() && !isGroundCover(material)) return null;
                }
            }
        }
        return new Terrain(minY, maxY);
    }

    private int findNaturalSurface(World world, int x, int z) {
        Block top = world.getHighestBlockAt(x, z);
        int y = top.getY();
        while (y > world.getMinHeight() && isGroundCover(world.getBlockAt(x, y, z).getType())) y--;
        Material type = world.getBlockAt(x, y, z).getType();
        if (!isNaturalSurface(type)) return Integer.MIN_VALUE;
        return y;
    }

    private boolean isNaturalSurface(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM, MOSS_BLOCK, MUD -> true;
            default -> false;
        };
    }

    private boolean isGroundCover(Material material) {
        return switch (material) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, DANDELION, POPPY,
                    BLUE_ORCHID, ALLIUM, AZURE_BLUET, RED_TULIP, ORANGE_TULIP, WHITE_TULIP,
                    PINK_TULIP, OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY, WITHER_ROSE,
                    SNOW, VINE, GLOW_LICHEN -> true;
            default -> false;
        };
    }

    private void buildVillage(World world, int x, int y, int z, Random random) {
        // Main path crosses the clearing; small branches lead to the doors.
        buildPath(world, x, y, z, 0, -12, 0, 12, Material.DEEPSLATE_BRICKS);
        buildPath(world, x, y, z, -11, 0, 11, 0, Material.POLISHED_BLACKSTONE_BRICKS);
        buildPath(world, x, y, z, -6, -3, -6, -3, Material.MOSSY_STONE_BRICKS);
        buildPath(world, x, y, z, 6, -3, 6, -3, Material.MOSSY_STONE_BRICKS);
        buildPath(world, x, y, z, -6, 9, -6, 9, Material.MOSSY_STONE_BRICKS);
        buildPath(world, x, y, z, 6, 9, 6, 9, Material.MOSSY_STONE_BRICKS);

        Material[] wallPalette = {Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, Material.STRIPPED_DARK_OAK_WOOD};
        Material wall = wallPalette[random.nextInt(wallPalette.length)];
        Material roof = random.nextBoolean() ? Material.DARK_OAK_STAIRS : Material.SPRUCE_STAIRS;

        buildHouse(world, x - 6, y, z - 6, wall, roof, false);
        buildHouse(world, x + 6, y, z - 6, wall, roof, false);
        buildHouse(world, x - 6, y, z + 6, wall, roof, false);
        buildHouse(world, x + 6, y, z + 6, Material.DEEPSLATE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, true);

        buildWell(world, x - 1, y, z + 1);
        buildLamp(world, x - 10, y, z);
        buildLamp(world, x + 10, y, z);
        buildLamp(world, x, y, z - 10);
        buildLamp(world, x, y, z + 11);
        buildPumpkinPatch(world, x + 2, y, z - 10, random);

        // Moss, graveside flowers and fallen branches break up repeated block lines.
        place(world, x - 10, y, z - 10, Material.MOSS_BLOCK);
        place(world, x + 10, y, z + 10, Material.MOSS_BLOCK);
        place(world, x - 10, y, z + 10, Material.PUMPKIN);
        place(world, x + 10, y, z - 10, Material.JACK_O_LANTERN);
        place(world, x - 4, y + 1, z + 1, Material.WITHER_ROSE);
        place(world, x + 4, y + 1, z - 1, Material.WITHER_ROSE);
    }

    private void buildHouse(World world, int cx, int baseY, int cz, Material wall, Material stair, boolean chapel) {
        Material foundation = chapel ? Material.POLISHED_BLACKSTONE_BRICKS : Material.COBBLED_DEEPSLATE;
        Material floor = chapel ? Material.POLISHED_DEEPSLATE : Material.SPRUCE_PLANKS;
        Material beams = chapel ? Material.POLISHED_BLACKSTONE : Material.STRIPPED_DARK_OAK_LOG;
        Material window = chapel ? Material.RED_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;

        // 9x7 footprint. Support columns touch the natural ground without digging it out.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean perimeter = Math.abs(dx) == 4 || Math.abs(dz) == 3;
                place(world, cx + dx, baseY, cz + dz, perimeter ? foundation : floor);
                supportDown(world, cx + dx, baseY, cz + dz, foundation);
            }
        }

        for (int level = 1; level <= 4; level++) {
            int atY = baseY + level;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean perimeter = Math.abs(dx) == 4 || Math.abs(dz) == 3;
                    if (!perimeter) continue;
                    boolean corner = Math.abs(dx) == 4 && Math.abs(dz) == 3;
                    boolean frontDoor = dz == 3 && dx == 0 && level <= 2;
                    boolean sideWindow = level == 3 && (Math.abs(dx) == 4 && Math.abs(dz) == 0
                            || Math.abs(dz) == 3 && Math.abs(dx) == 2);
                    if (frontDoor || sideWindow) continue;
                    place(world, cx + dx, atY, cz + dz, corner ? beams : wall);
                }
            }
        }

        buildDoor(world, cx, baseY + 1, cz + 3, Material.SPRUCE_DOOR, BlockFace.SOUTH);
        place(world, cx - 2, baseY + 3, cz + 3, window);
        place(world, cx + 2, baseY + 3, cz + 3, window);
        place(world, cx - 4, baseY + 3, cz, window);
        place(world, cx + 4, baseY + 3, cz, window);
        place(world, cx, baseY + 4, cz, chapel ? Material.SOUL_LANTERN : Material.LANTERN);

        // Gabled roof, stepped in height towards the ridge.
        for (int dz = -3; dz <= 3; dz++) {
            int distance = Math.abs(dz);
            int roofY = baseY + 5 + (3 - distance);
            BlockFace facing = dz < 0 ? BlockFace.NORTH : BlockFace.SOUTH;
            for (int dx = -4; dx <= 4; dx++) {
                placeFacing(world, cx + dx, roofY, cz + dz, stair, facing);
                if (dx == -4 || dx == 4) place(world, cx + dx, roofY + 1, cz + dz, foundation);
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            place(world, cx + dx, baseY + 8, cz, chapel ? Material.CRYING_OBSIDIAN : Material.DARK_OAK_PLANKS);
        }
        if (chapel) {
            place(world, cx, baseY + 9, cz, Material.CRYING_OBSIDIAN);
            place(world, cx, baseY + 10, cz, Material.RED_CANDLE);
            place(world, cx, baseY + 5, cz + 3, Material.SOUL_LANTERN);
            place(world, cx, baseY + 5, cz - 3, Material.SOUL_LANTERN);
        } else {
            place(world, cx - 3, baseY + 5, cz + 3, Material.COBWEB);
            place(world, cx + 3, baseY + 5, cz - 3, Material.COBWEB);
        }
    }

    private void buildWell(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                place(world, x + dx, y, z + dz, Material.MOSSY_STONE_BRICKS);
                if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                    place(world, x + dx, y + 1, z + dz, Material.MOSSY_COBBLESTONE_WALL);
                }
            }
        }
        place(world, x, y + 1, z, Material.WATER);
        for (int dx : new int[]{-1, 1}) {
            place(world, x + dx, y + 2, z, Material.DARK_OAK_FENCE);
        }
        place(world, x, y + 2, z, Material.CHAIN);
        place(world, x, y + 3, z, Material.SOUL_LANTERN);
        supportDown(world, x, y, z, Material.COBBLED_DEEPSLATE);
    }

    private void buildLamp(World world, int x, int y, int z) {
        supportDown(world, x, y, z, Material.COBBLED_DEEPSLATE);
        place(world, x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
        place(world, x, y + 1, z, Material.DARK_OAK_FENCE);
        place(world, x, y + 2, z, Material.DARK_OAK_FENCE);
        place(world, x, y + 3, z, Material.CHAIN);
        place(world, x, y + 4, z, Material.SOUL_LANTERN);
        place(world, x + 1, y + 2, z, Material.COBWEB);
    }

    private void buildPumpkinPatch(World world, int x, int y, int z, Random random) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 1) {
                    place(world, x + dx, y, z + dz, Material.SPRUCE_FENCE);
                } else {
                    place(world, x + dx, y, z + dz, Material.FARMLAND);
                    place(world, x + dx, y + 1, z + dz, random.nextBoolean() ? Material.PUMPKIN : Material.CARVED_PUMPKIN);
                }
            }
        }
    }

    private void buildGraveyard(World world, int x, int y, int z, Random random) {
        // Low uneven wall, open gate and a clear path to the mausoleum.
        for (int offset = -8; offset <= 8; offset++) {
            place(world, x + offset, y, z - 8, Material.MOSSY_STONE_BRICK_WALL);
            place(world, x + offset, y, z + 8, Material.MOSSY_STONE_BRICK_WALL);
            place(world, x - 8, y, z + offset, Material.MOSSY_STONE_BRICK_WALL);
            place(world, x + 8, y, z + offset, Material.MOSSY_STONE_BRICK_WALL);
        }
        place(world, x - 8, y + 1, z - 8, Material.COBBLED_DEEPSLATE_WALL);
        place(world, x + 8, y + 1, z - 8, Material.COBBLED_DEEPSLATE_WALL);
        place(world, x - 8, y + 1, z + 8, Material.COBBLED_DEEPSLATE_WALL);
        place(world, x + 8, y + 1, z + 8, Material.COBBLED_DEEPSLATE_WALL);
        place(world, x - 1, y, z + 8, Material.DARK_OAK_FENCE);
        place(world, x + 1, y, z + 8, Material.DARK_OAK_FENCE);
        buildPath(world, x, y, z, 0, -6, 0, 7, Material.CRACKED_DEEPSLATE_BRICKS);
        buildPath(world, x, y, z, -6, -1, 6, -1, Material.MOSSY_STONE_BRICKS);

        int[][] graves = {
                {-5, -5}, {-2, -5}, {2, -5}, {5, -5},
                {-5, -1}, {-2, 2}, {2, 2}, {5, 2},
                {-5, 5}, {0, 5}, {5, 5}
        };
        for (int index = 0; index < graves.length; index++) {
            int[] grave = graves[index];
            buildGrave(world, x + grave[0], y, z + grave[1], index % 4, random);
        }

        buildMausoleum(world, x, y, z - 6);
        buildRitualCircle(world, x + 3, y, z + 1);
        place(world, x - 7, y, z - 7, Material.JACK_O_LANTERN);
        place(world, x + 7, y, z - 7, Material.PUMPKIN);
        place(world, x - 7, y, z + 7, Material.PUMPKIN);
        place(world, x + 7, y, z + 7, Material.JACK_O_LANTERN);
        place(world, x - 6, y + 1, z, Material.COBWEB);
        place(world, x + 6, y + 1, z - 1, Material.COBWEB);
        place(world, x - 2, y + 1, z + 6, Material.WITHER_ROSE);
        place(world, x + 4, y + 1, z - 4, Material.WITHER_ROSE);
    }

    private void buildGrave(World world, int x, int y, int z, int variant, Random random) {
        Material[] stones = {Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
                Material.POLISHED_BLACKSTONE_BRICKS, Material.COBBLED_DEEPSLATE};
        Material stone = stones[(variant + random.nextInt(stones.length)) % stones.length];
        place(world, x, y, z, stone);
        place(world, x, y, z + 1, Material.STONE_BRICK_SLAB);
        place(world, x, y, z + 2, Material.MOSSY_STONE_BRICK_SLAB);
        place(world, x, y + 1, z, stone);
        if (variant == 0 || variant == 3) {
            place(world, x, y + 2, z, Material.STONE_BRICK_WALL);
            place(world, x, y + 3, z, Material.STONE_BRICK_WALL);
            place(world, x - 1, y + 2, z, Material.STONE_BRICK_WALL);
            place(world, x + 1, y + 2, z, Material.STONE_BRICK_WALL);
        } else if (variant == 1) {
            place(world, x, y + 2, z, Material.CHISELED_POLISHED_BLACKSTONE);
            place(world, x, y + 3, z, Material.RED_CANDLE);
        } else {
            place(world, x, y + 2, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            place(world, x, y + 3, z, Material.COBWEB);
        }
    }

    private void buildMausoleum(World world, int x, int y, int z) {
        Material wall = Material.DEEPSLATE_BRICKS;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                place(world, x + dx, y, z + dz, Material.POLISHED_BLACKSTONE_BRICKS);
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 2;
                if (edge && !(dz == 2 && dx == 0)) {
                    for (int level = 1; level <= 3; level++) {
                        if ((Math.abs(dx) == 3 && Math.abs(dz) == 1 && level == 2)
                                || (Math.abs(dz) == 2 && Math.abs(dx) == 2 && level == 2)) {
                            place(world, x + dx, y + level, z + dz, Material.RED_STAINED_GLASS_PANE);
                        } else {
                            place(world, x + dx, y + level, z + dz, wall);
                        }
                    }
                }
            }
        }
        buildDoor(world, x, y + 1, z + 2, Material.DARK_OAK_DOOR, BlockFace.SOUTH);
        for (int dx = -3; dx <= 3; dx++) {
            place(world, x + dx, y + 4, z, Material.POLISHED_BLACKSTONE_BRICK_STAIRS);
            place(world, x + dx, y + 4, z - 1, Material.DEEPSLATE_BRICKS);
            place(world, x + dx, y + 4, z + 1, Material.DEEPSLATE_BRICKS);
        }
        place(world, x, y + 5, z, Material.CRYING_OBSIDIAN);
        place(world, x, y + 6, z, Material.SOUL_LANTERN);
        place(world, x - 2, y + 1, z - 1, Material.COBWEB);
        place(world, x + 2, y + 1, z - 1, Material.COBWEB);
    }

    private void buildRitualCircle(World world, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int distance = dx * dx + dz * dz;
                if (distance >= 3 && distance <= 5) {
                    place(world, x + dx, y, z + dz, Material.CRYING_OBSIDIAN);
                } else if (distance <= 2) {
                    place(world, x + dx, y, z + dz, Material.POLISHED_BLACKSTONE);
                }
            }
        }
        place(world, x, y + 1, z, Material.SOUL_LANTERN);
        place(world, x - 2, y + 1, z, Material.RED_CANDLE);
        place(world, x + 2, y + 1, z, Material.RED_CANDLE);
        place(world, x, y + 1, z - 2, Material.RED_CANDLE);
        place(world, x, y + 1, z + 2, Material.RED_CANDLE);
    }

    private void buildPath(World world, int centerX, int baseY, int centerZ,
                           int startX, int startZ, int endX, int endZ, Material material) {
        int dx = Integer.compare(endX, startX);
        int dz = Integer.compare(endZ, startZ);
        int x = startX;
        int z = startZ;
        while (true) {
            place(world, centerX + x, baseY, centerZ + z, material);
            supportDown(world, centerX + x, baseY, centerZ + z, Material.COBBLED_DEEPSLATE);
            if (x == endX && z == endZ) break;
            if (x != endX) x += dx;
            if (z != endZ) z += dz;
        }
    }

    private void supportDown(World world, int x, int baseY, int z, Material material) {
        for (int y = baseY - 1; y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !isGroundCover(block.getType())) return;
            if (!block.getType().isAir() && !isGroundCover(block.getType())) return;
            if (block.getState() instanceof TileState) return;
            block.setType(material, false);
        }
    }

    private void buildDoor(World world, int x, int y, int z, Material material, BlockFace facing) {
        Block bottom = world.getBlockAt(x, y, z);
        Block top = world.getBlockAt(x, y + 1, z);
        if ((!bottom.getType().isAir() && !isGroundCover(bottom.getType()))
                || (!top.getType().isAir() && !isGroundCover(top.getType()))) return;
        if (bottom.getState() instanceof TileState || top.getState() instanceof TileState) return;
        bottom.setType(material, false);
        top.setType(material, false);
        if (bottom.getBlockData() instanceof Door door) {
            door.setFacing(facing);
            door.setHalf(Bisected.Half.BOTTOM);
            bottom.setBlockData(door, false);
        }
        if (top.getBlockData() instanceof Door door) {
            door.setFacing(facing);
            door.setHalf(Bisected.Half.TOP);
            top.setBlockData(door, false);
        }
    }

    private void placeFacing(World world, int x, int y, int z, Material material, BlockFace facing) {
        Block block = world.getBlockAt(x, y, z);
        if (!canReplace(block)) return;
        block.setType(material, false);
        if (block.getBlockData() instanceof Directional directional) {
            directional.setFacing(facing);
            block.setBlockData(directional, false);
        }
    }

    private void place(World world, int x, int y, int z, Material material) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight()) return;
        Block block = world.getBlockAt(x, y, z);
        if (canReplace(block)) block.setType(material, false);
    }

    private boolean canReplace(Block block) {
        if (block.getState() instanceof TileState) return false;
        return block.getType().isAir() || isGroundCover(block.getType());
    }

    private enum StructureType {
        HAUNTED_VILLAGE("Haunted Village", 13, 13, 0x4841554E544544L),
        CURSED_GRAVEYARD("Cursed Graveyard", 8, 9, 0x4752415645594152L);

        private final String label;
        private final int radius;
        private final int height;
        private final long salt;

        StructureType(String label, int radius, int height, long salt) {
            this.label = label;
            this.radius = radius;
            this.height = height;
            this.salt = salt;
        }
    }

    private record Terrain(int minY, int maxY) { }

    private record Candidate(World world, int chunkX, int chunkZ, StructureType type, int retries, String key) { }
}
