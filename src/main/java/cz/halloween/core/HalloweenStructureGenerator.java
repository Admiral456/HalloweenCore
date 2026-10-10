package cz.halloween.core;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
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
 * Generates small, genuinely built Halloween landmarks into new/existing eligible
 * Overworld chunks. All structures fit inside one chunk, use a natural flat site,
 * and never dig a pit or tunnel. A chunk marker makes generation deterministic and
 * prevents duplicates after reconnects, reloads, and restarts.
 */
public final class HalloweenStructureGenerator implements Listener {
    private static final int PER_THOUSAND = 1000;
    private final HalloweenCore plugin;
    private final NamespacedKey checkedKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey xKey;
    private final NamespacedKey yKey;
    private final NamespacedKey zKey;
    private final Queue<Chunk> pending = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private BukkitTask task;

    public HalloweenStructureGenerator(HalloweenCore plugin) {
        this.plugin = plugin;
        this.checkedKey = new NamespacedKey(plugin, "halloween_structure_checked_v1");
        this.typeKey = new NamespacedKey(plugin, "halloween_structure_type");
        this.xKey = new NamespacedKey(plugin, "halloween_structure_x");
        this.yKey = new NamespacedKey(plugin, "halloween_structure_y");
        this.zKey = new NamespacedKey(plugin, "halloween_structure_z");
    }

    public void start() {
        if (task != null || !isEnabled()) return;
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
            if (world.getEnvironment() != World.Environment.NORMAL || !plugin.isEligibleGameplayWorld(world)) continue;
            for (Chunk chunk : world.getLoadedChunks()) enqueue(chunk);
        }
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processQueue, 1L, 20L);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (isEnabled()) enqueue(event.getChunk());
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("random-structures.enabled", true);
    }

    private void enqueue(Chunk chunk) {
        if (chunk == null || chunk.getWorld().getEnvironment() != World.Environment.NORMAL
                || !plugin.isEligibleGameplayWorld(chunk.getWorld())
                || chunk.getPersistentDataContainer().has(checkedKey, PersistentDataType.BYTE)) {
            return;
        }
        String id = chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
        if (queued.add(id)) pending.offer(chunk);
    }

    private void processQueue() {
        if (!isEnabled()) {
            pending.clear();
            queued.clear();
            if (task != null) {
                task.cancel();
                task = null;
            }
            return;
        }
        int budget = Math.max(1, Math.min(3, plugin.getConfig().getInt("random-structures.chunks-per-tick", 1)));
        for (int i = 0; i < budget; i++) {
            Chunk chunk = pending.poll();
            if (chunk == null) return;
            queued.remove(id(chunk));
            if (!chunk.isLoaded() || chunk.getPersistentDataContainer().has(checkedKey, PersistentDataType.BYTE)) continue;
            try {
                generateInChunk(chunk);
                chunk.getPersistentDataContainer().set(checkedKey, PersistentDataType.BYTE, (byte) 1);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Halloween structure generation failed in "
                        + chunk.getWorld().getName() + " chunk " + chunk.getX() + "," + chunk.getZ()
                        + ": " + ex.getClass().getSimpleName());
                // Do not mark on unexpected errors: a later chunk reload may retry.
            }
        }
    }

    private String id(Chunk chunk) {
        return chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private void generateInChunk(Chunk chunk) {
        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL || !plugin.isEligibleGameplayWorld(world)) return;

        long seed = world.getSeed()
                ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L)
                ^ 0x48414C4C53545255L;
        int spawnExclusion = bounded("minimum-distance-from-world-spawn-blocks", 256, 0, 10000);
        Location spawn = world.getSpawnLocation();
        double dxSpawn = (chunk.getX() << 4) + 8.0D - spawn.getX();
        double dzSpawn = (chunk.getZ() << 4) + 8.0D - spawn.getZ();
        if (dxSpawn * dxSpawn + dzSpawn * dzSpawn < (double) spawnExclusion * spawnExclusion) return;

        Random random = new Random(seed);
        int villageChance = bounded("village-chance-per-thousand", 18, 0, PER_THOUSAND);
        int graveyardChance = bounded("graveyard-chance-per-thousand", 45, 0, PER_THOUSAND);
        int roll = random.nextInt(PER_THOUSAND);

        if (roll < villageChance) {
            if (tryVillage(chunk, random)) return;
            tryGraveyard(chunk, random);
            return;
        }
        if (roll < villageChance + graveyardChance) {
            if (tryGraveyard(chunk, random)) return;
            tryVillage(chunk, random);
        }
    }

    private boolean tryVillage(Chunk chunk, Random random) {
        int chunkBaseX = chunk.getX() << 4;
        int chunkBaseZ = chunk.getZ() << 4;
        for (int attempt = 0; attempt < 8; attempt++) {
            int centerX = chunkBaseX + 6 + random.nextInt(4);
            int centerZ = chunkBaseZ + 6 + random.nextInt(4);
            Block ground = findNaturalGround(chunk.getWorld(), centerX, centerZ);
            if (ground == null) continue;
            Integer platformY = findFlatPlatformY(chunk, centerX, centerZ, 6, 7);
            if (platformY == null) continue;
            int baseY = platformY;
            preparePlatform(chunk.getWorld(), centerX, baseY, centerZ, 6);
            buildHauntedVillage(chunk.getWorld(), centerX, baseY, centerZ);
            markStructure(chunk, "village", centerX, baseY + 1, centerZ);
            plugin.getLogger().info("Generated random Haunted Village at " + chunk.getWorld().getName()
                    + " " + centerX + " " + (baseY + 1) + " " + centerZ);
            return true;
        }
        return false;
    }

    private boolean tryGraveyard(Chunk chunk, Random random) {
        int chunkBaseX = chunk.getX() << 4;
        int chunkBaseZ = chunk.getZ() << 4;
        for (int attempt = 0; attempt < 8; attempt++) {
            int centerX = chunkBaseX + 5 + random.nextInt(6);
            int centerZ = chunkBaseZ + 5 + random.nextInt(6);
            Block ground = findNaturalGround(chunk.getWorld(), centerX, centerZ);
            if (ground == null) continue;
            Integer platformY = findFlatPlatformY(chunk, centerX, centerZ, 5, 6);
            if (platformY == null) continue;
            int baseY = platformY;
            preparePlatform(chunk.getWorld(), centerX, baseY, centerZ, 5);
            buildGraveyard(chunk.getWorld(), centerX, baseY, centerZ);
            markStructure(chunk, "graveyard", centerX, baseY + 1, centerZ);
            plugin.getLogger().info("Generated random Haunted Graveyard at " + chunk.getWorld().getName()
                    + " " + centerX + " " + (baseY + 1) + " " + centerZ);
            return true;
        }
        return false;
    }

    private Block findNaturalGround(World world, int x, int z) {
        int highest = world.getHighestBlockYAt(x, z);
        int lowest = Math.max(world.getMinHeight(), highest - 64);
        for (int y = highest; y >= lowest; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!isNaturalGround(ground.getType()) || !ground.getType().isSolid()
                    || ground.getState() instanceof TileState) continue;
            return ground;
        }
        return null;
    }

    /**
     * Find a mostly-level natural platform without cutting terrain. The generator
     * allows a gentle step of at most two blocks and fills the lower side up to the
     * highest natural point; it never digs a hole, trench, shaft or underground room.
     */
    private Integer findFlatPlatformY(Chunk chunk, int centerX, int centerZ, int radius, int clearance) {
        World world = chunk.getWorld();
        int localMinX = chunk.getX() << 4;
        int localMinZ = chunk.getZ() << 4;
        if (centerX - radius < localMinX || centerX + radius > localMinX + 15
                || centerZ - radius < localMinZ || centerZ + radius > localMinZ + 15) return null;

        int highestGroundY = Integer.MIN_VALUE;
        int lowestGroundY = Integer.MAX_VALUE;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                Block ground = findNaturalGround(world, x, z);
                if (ground == null || ground.getState() instanceof TileState) return null;
                highestGroundY = Math.max(highestGroundY, ground.getY());
                lowestGroundY = Math.min(lowestGroundY, ground.getY());
            }
        }
        if (highestGroundY - lowestGroundY > 2) return null;

        // Only fill upwards; the generated structures must remain above the terrain.
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                Block ground = findNaturalGround(world, x, z);
                if (ground == null) return null;
                // Do not overwrite a trunk, leaf canopy, player build, container or
                // other object while filling a two-block slope. The entire column
                // above its natural ground through the structure clearance must be air.
                for (int y = ground.getY() + 1; y <= highestGroundY + clearance; y++) {
                    if (!world.getBlockAt(x, y, z).getType().isAir()) return null;
                }
            }
        }
        return highestGroundY;
    }

    private void preparePlatform(World world, int centerX, int baseY, int centerZ, int radius) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                Block ground = findNaturalGround(world, x, z);
                if (ground == null || ground.getY() >= baseY) continue;
                for (int y = ground.getY() + 1; y <= baseY; y++) {
                    Material fill = ((x + z + y) & 1) == 0 ? Material.COARSE_DIRT : Material.GRAVEL;
                    set(world, x, y, z, fill);
                }
            }
        }
    }

    private void buildHauntedVillage(World world, int cx, int groundY, int cz) {
        int floorY = groundY + 1;
        // Sparse broken fence, with obvious entrances on all four sides.
        for (int step = -6; step <= 6; step++) {
            if (Math.abs(step) > 1) {
                set(world, cx + step, floorY, cz - 6, Material.SPRUCE_FENCE);
                set(world, cx + step, floorY, cz + 6, Material.SPRUCE_FENCE);
                set(world, cx - 6, floorY, cz + step, Material.SPRUCE_FENCE);
                set(world, cx + 6, floorY, cz + step, Material.SPRUCE_FENCE);
            }
        }

        // Worn paths meet at a raised red-blackstone altar, not a well or pit.
        for (int step = -5; step <= 5; step++) {
            Material path = (Math.abs(step) % 3 == 0) ? Material.GRAVEL : Material.COARSE_DIRT;
            set(world, cx + step, floorY, cz, path);
            set(world, cx, floorY, cz + step, path);
        }
        buildHouse(world, cx - 3, floorY, cz - 3);
        buildHouse(world, cx + 3, floorY, cz + 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(world, cx + dx, floorY, cz + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        set(world, cx, floorY + 1, cz, Material.CHISELED_POLISHED_BLACKSTONE);
        set(world, cx, floorY + 2, cz, Material.CRYING_OBSIDIAN);
        set(world, cx, floorY + 3, cz, Material.SOUL_LANTERN);
        set(world, cx - 4, floorY, cz + 1, Material.PUMPKIN);
        set(world, cx + 4, floorY, cz - 1, Material.JACK_O_LANTERN);
        set(world, cx - 5, floorY, cz - 3, Material.COARSE_DIRT);
        set(world, cx + 5, floorY, cz + 3, Material.COARSE_DIRT);
        set(world, cx - 5, floorY + 1, cz - 4, Material.SOUL_LANTERN);
        set(world, cx + 5, floorY + 1, cz + 4, Material.SOUL_LANTERN);
    }

    private void buildHouse(World world, int cx, int floorY, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(world, cx + dx, floorY, cz + dz, Material.SPRUCE_PLANKS);
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (!edge) continue;
                for (int dy = 1; dy <= 3; dy++) {
                    boolean doorway = dz == -2 && dx == 0 && dy <= 2;
                    boolean window = dy == 2 && ((dz == 2 && Math.abs(dx) == 1)
                            || (Math.abs(dx) == 2 && dz == 1));
                    if (doorway || window) continue;
                    Material wall = (Math.abs(dx) == 2 && Math.abs(dz) == 2)
                            ? Material.STRIPPED_SPRUCE_LOG : Material.DARK_OAK_PLANKS;
                    set(world, cx + dx, floorY + dy, cz + dz, wall);
                }
            }
        }
        // Broad, layered roof gives the huts a recognisable silhouette without needing
        // custom entities or an external structure file.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(world, cx + dx, floorY + 4, cz + dz, Material.DARK_OAK_SLAB);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            set(world, cx + dx, floorY + 5, cz, Material.DARK_OAK_PLANKS);
        }
    }

    private void buildGraveyard(World world, int cx, int groundY, int cz) {
        int floorY = groundY + 1;
        // Low wall and gate—no excavation, shaft, grave hole or underground room is created.
        for (int step = -5; step <= 5; step++) {
            if (step != 0) {
                set(world, cx + step, floorY, cz - 5, Material.COBBLED_DEEPSLATE_WALL);
                set(world, cx + step, floorY, cz + 5, Material.COBBLED_DEEPSLATE_WALL);
                set(world, cx - 5, floorY, cz + step, Material.COBBLED_DEEPSLATE_WALL);
                set(world, cx + 5, floorY, cz + step, Material.COBBLED_DEEPSLATE_WALL);
            }
            if (step % 2 == 0) {
                set(world, cx + step, floorY + 1, cz - 5, Material.COBBLED_DEEPSLATE_WALL);
            }
        }
        // Lanterned corner posts.
        for (int dx : new int[]{-5, 5}) {
            for (int dz : new int[]{-5, 5}) {
                set(world, cx + dx, floorY, cz + dz, Material.POLISHED_BLACKSTONE_BRICKS);
                set(world, cx + dx, floorY + 1, cz + dz, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                set(world, cx + dx, floorY + 2, cz + dz, Material.SOUL_LANTERN);
            }
        }
        for (int step = -4; step <= 4; step++) {
            set(world, cx, floorY, cz + step, (Math.abs(step) % 2 == 0) ? Material.GRAVEL : Material.COARSE_DIRT);
        }
        int[][] graves = {{-3, -3}, {3, -3}, {-3, 0}, {3, 0}, {-3, 3}, {3, 3}};
        for (int[] grave : graves) buildStoneGrave(world, cx + grave[0], floorY, cz + grave[1]);
        set(world, cx, floorY, cz - 4, Material.POLISHED_BLACKSTONE_BRICKS);
        set(world, cx, floorY + 1, cz - 4, Material.CRYING_OBSIDIAN);
        set(world, cx, floorY + 2, cz - 4, Material.SOUL_LANTERN);
        set(world, cx - 1, floorY, cz - 4, Material.RED_CANDLE);
        set(world, cx + 1, floorY, cz - 4, Material.RED_CANDLE);
        set(world, cx - 4, floorY, cz + 2, Material.WITHER_ROSE);
        set(world, cx + 4, floorY, cz - 2, Material.WITHER_ROSE);
    }

    private void buildStoneGrave(World world, int x, int floorY, int z) {
        set(world, x, floorY, z, Material.DEEPSLATE_BRICK_SLAB);
        set(world, x, floorY, z + 1, Material.DEEPSLATE_BRICK_SLAB);
        set(world, x, floorY + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
        set(world, x, floorY + 2, z, Material.CHISELED_POLISHED_BLACKSTONE);
        set(world, x - 1, floorY + 2, z, Material.POLISHED_BLACKSTONE_BRICKS);
        set(world, x + 1, floorY + 2, z, Material.POLISHED_BLACKSTONE_BRICKS);
        set(world, x, floorY + 3, z, Material.POLISHED_BLACKSTONE_BRICKS);
        set(world, x, floorY + 4, z, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        set(world, x, floorY + 1, z + 1, Material.RED_CANDLE);
    }

    private void markStructure(Chunk chunk, String kind, int x, int y, int z) {
        var data = chunk.getPersistentDataContainer();
        data.set(typeKey, PersistentDataType.STRING, kind);
        data.set(xKey, PersistentDataType.INTEGER, x);
        data.set(yKey, PersistentDataType.INTEGER, y);
        data.set(zKey, PersistentDataType.INTEGER, z);
    }

    private int bounded(String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, plugin.getConfig().getInt("random-structures." + key, fallback)));
    }

    private boolean isNaturalGround(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM,
                    MOSS_BLOCK, MUD, CLAY, STONE, ANDESITE, DIORITE, GRANITE,
                    DEEPSLATE, TUFF, CALCITE, GRAVEL, SAND, RED_SAND, SNOW_BLOCK -> true;
            default -> false;
        };
    }

    private void set(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) block.setType(material, false);
    }
}
