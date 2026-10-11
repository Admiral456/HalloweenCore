package cz.halloween.core;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Creates small, natural-looking haunted villages and graveyards in random
 * chunks. It only builds above safe natural terrain; it never excavates holes
 * or replaces existing structures/tile entities. Each chunk is evaluated once.
 */
public final class HalloweenStructureGenerator implements Listener {
    public static final String HAUNTED_VILLAGE = "haunted_village";
    public static final String HAUNTED_GRAVEYARD = "haunted_graveyard";

    private final HalloweenCore plugin;
    private final NamespacedKey checkedKey;
    private final NamespacedKey structureKey;
    private final NamespacedKey legacyStructureKey;
    private final NamespacedKey centerXKey;
    private final NamespacedKey centerYKey;
    private final NamespacedKey centerZKey;
    private final Queue<Chunk> pending = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Set<String> warned = new HashSet<>();
    private BukkitTask task;

    private record Terrain(int baseY) {}

    public HalloweenStructureGenerator(HalloweenCore plugin) {
        this.plugin = plugin;
        this.checkedKey = new NamespacedKey(plugin, "halloween_structure_checked_v3");
        this.structureKey = new NamespacedKey(plugin, "halloween_structure_type");
        this.legacyStructureKey = new NamespacedKey(plugin, "halloween_generated_structure_v1");
        this.centerXKey = new NamespacedKey(plugin, "halloween_structure_center_x");
        this.centerYKey = new NamespacedKey(plugin, "halloween_structure_center_y");
        this.centerZKey = new NamespacedKey(plugin, "halloween_structure_center_z");
    }

    public void start() {
        if (!isEnabled()) return;
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

    public void resumeWithoutScanning() {
        if (task != null || !isEnabled()) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processQueue, 1L, 10L);
    }

    public void scanLoadedChunks() {
        if (!isEnabled()) return;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) enqueue(chunk);
        }
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processQueue, 1L, 10L);
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
        if (chunk == null || chunk.getPersistentDataContainer().has(checkedKey, PersistentDataType.BYTE)) return;
        String id = chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
        if (queued.add(id)) pending.offer(chunk);
    }

    private void processQueue() {
        if (!isEnabled()) {
            pending.clear();
            queued.clear();
            return;
        }
        int budget = Math.max(1, Math.min(4, plugin.getConfig().getInt("random-structures.chunks-per-tick", 1)));
        for (int i = 0; i < budget; i++) {
            Chunk chunk = pending.poll();
            if (chunk == null) return;
            queued.remove(chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ());
            if (!chunk.isLoaded() || chunk.getPersistentDataContainer().has(checkedKey, PersistentDataType.BYTE)) continue;
            try {
                tryGenerate(chunk);
            } catch (RuntimeException ex) {
                String error = ex.getClass().getName();
                if (warned.add(error)) {
                    plugin.getLogger().warning("Halloween structure generation skipped a chunk after "
                            + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            } finally {
                // A poor biome/terrain result shouldn't be retried on every relog.
                chunk.getPersistentDataContainer().set(checkedKey, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    private void tryGenerate(Chunk chunk) {
        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        if (isExcludedWorld(world.getName()) || isNearWorldSpawn(chunk) || isReservedForArena(chunk)) return;
        // Recheck old chunks after the higher chance without duplicating existing structures.
        if (chunk.getPersistentDataContainer().has(structureKey, PersistentDataType.STRING)
                || chunk.getPersistentDataContainer().has(legacyStructureKey, PersistentDataType.STRING)) return;

        Random random = new Random(world.getSeed()
                ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L)
                ^ 0x48414C4C53545255L);
        // Evaluate the configured village/graveyard probabilities for every eligible
        // Overworld chunk instead of the obsolete single world-generation.* roll.
        int villageChance = Math.max(0, Math.min(1000,
                plugin.getConfig().getInt("random-structures.village-chance-per-thousand", 18)));
        int graveyardChance = Math.max(0, Math.min(1000 - villageChance,
                plugin.getConfig().getInt("random-structures.graveyard-chance-per-thousand", 45)));
        int roll = random.nextInt(1000);
        final String type;
        if (roll < villageChance) {
            type = HAUNTED_VILLAGE;
        } else if (roll < villageChance + graveyardChance) {
            type = HAUNTED_GRAVEYARD;
        } else {
            return;
        }

        int centerX = (chunk.getX() << 4) + 8;
        int centerZ = (chunk.getZ() << 4) + 8;
        Terrain terrain = findFlatPlatformY(world, centerX, centerZ, 5);
        if (terrain == null) return;

        // Fill only low natural spots and clear replaceable plants/leaves. This routine
        // never excavates downwards or creates a cave/pit.
        preparePlatform(world, centerX, terrain.baseY(), centerZ, 5);
        if (HAUNTED_VILLAGE.equals(type)) {
            buildHauntedVillage(world, centerX, terrain.baseY(), centerZ, random);
        } else {
            buildGraveyard(world, centerX, terrain.baseY(), centerZ, random);
        }

        var data = chunk.getPersistentDataContainer();
        data.set(structureKey, PersistentDataType.STRING, type);
        data.set(legacyStructureKey, PersistentDataType.STRING, type);
        data.set(centerXKey, PersistentDataType.INTEGER, centerX);
        data.set(centerYKey, PersistentDataType.INTEGER, terrain.baseY() + 1);
        data.set(centerZKey, PersistentDataType.INTEGER, centerZ);
        plugin.getLogger().fine("Generated " + type + " at " + world.getName() + " "
                + centerX + " " + (terrain.baseY() + 1) + " " + centerZ);
    }

    private boolean isExcludedWorld(String worldName) {
        for (String excluded : plugin.getConfig().getStringList("random-structures.excluded-worlds")) {
            if (excluded != null && excluded.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    private boolean isNearWorldSpawn(Chunk chunk) {
        int radius = Math.max(0, plugin.getConfig().getInt("random-structures.minimum-distance-from-world-spawn-blocks", 256));
        if (radius == 0) return false;
        Location spawn = chunk.getWorld().getSpawnLocation();
        double cx = (chunk.getX() << 4) + 8.0D;
        double cz = (chunk.getZ() << 4) + 8.0D;
        double dx = cx - spawn.getX();
        double dz = cz - spawn.getZ();
        return dx * dx + dz * dz < (double) radius * radius;
    }

    private boolean isReservedForArena(Chunk chunk) {
        String[] paths = {"bosses.vampire.arena.pending", "bosses.vampire.arena"};
        for (String path : paths) {
            if (!plugin.getConfig().getBoolean(path + ".configured", false)) continue;
            String worldName = plugin.getConfig().getString(path + ".world", "");
            if (worldName == null || !chunk.getWorld().getName().equals(worldName)) continue;
            double x = plugin.getConfig().getDouble(path + ".x");
            double z = plugin.getConfig().getDouble(path + ".z");
            double cx = (chunk.getX() << 4) + 8.0D;
            double cz = (chunk.getZ() << 4) + 8.0D;
            double dx = cx - x;
            double dz = cz - z;
            double radius = VampireArenaBuilder.RADIUS + 8.0D;
            if (dx * dx + dz * dz <= radius * radius) return true;
        }
        return false;
    }

    private Terrain findFlatPlatformY(World world, int centerX, int centerZ, int radius) {
        return inspectTerrain(world, centerX, centerZ, radius);
    }

    private void preparePlatform(World world, int centerX, int baseY, int centerZ, int radius) {
        fillNaturalHeightDifferences(world, centerX, baseY, centerZ, radius);
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                Block ground = findNaturalGround(world, x, z);
                if (ground == null) continue;
                for (int y = ground.getY() + 1; y <= baseY + 6; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isReplaceableVegetation(block.getType())) block.setType(Material.AIR, false);
                }
            }
        }
    }

    private Terrain inspectTerrain(World world, int centerX, int centerZ, int radius) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block ground = findNaturalGround(world, centerX + dx, centerZ + dz);
                if (ground == null || ground.getState() instanceof TileState) return null;
                minY = Math.min(minY, ground.getY());
                maxY = Math.max(maxY, ground.getY());
                if (maxY - minY > 5) return null;
            }
        }
        if (maxY <= world.getMinHeight() || maxY + 8 >= world.getMaxHeight()) return null;

        // Every block over the entire footprint must be air or natural replaceable vegetation.
        // Houses/graves never overwrite a tree, building, chest or existing structure.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block ground = findNaturalGround(world, centerX + dx, centerZ + dz);
                if (ground == null) return null;
                for (int y = ground.getY() + 1; y <= maxY + 6; y++) {
                    Block block = world.getBlockAt(centerX + dx, y, centerZ + dz);
                    if (block.getState() instanceof TileState) return null;
                    if (!block.getType().isAir() && !isReplaceableVegetation(block.getType())) return null;
                }
            }
        }
        return new Terrain(maxY);
    }

    private Block findNaturalGround(World world, int x, int z) {
        int top = world.getHighestBlockYAt(x, z);
        // Tall canopy blocks must not hide the natural ground from random structure placement.
        int bottom = Math.max(world.getMinHeight(), top - 32);
        for (int y = top; y >= bottom; y--) {
            Material material = world.getBlockAt(x, y, z).getType();
            if (isNaturalGround(material)) return world.getBlockAt(x, y, z);
        }
        return null;
    }

    private boolean isNaturalGround(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM,
                    MOSS_BLOCK, MUD, CLAY, STONE, ANDESITE, DIORITE, GRANITE,
                    DEEPSLATE, TUFF, CALCITE, SAND, RED_SAND, GRAVEL, SNOW_BLOCK,
                    MOSSY_COBBLESTONE -> true;
            default -> false;
        };
    }

    private boolean isReplaceableVegetation(Material material) {
        return switch (material) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH,
                    DANDELION, POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET,
                    RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP,
                    OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY, WITHER_ROSE,
                    SNOW, BROWN_MUSHROOM, RED_MUSHROOM, SWEET_BERRY_BUSH,
                    VINE, GLOW_LICHEN -> true;
            default -> material.name().endsWith("_LEAVES") || material.name().endsWith("_SAPLING");
        };
    }

    private void buildHauntedVillage(World world, int cx, int baseY, int cz, Random random) {
        house(world, cx - 3, baseY + 1, cz - 3, true, random);
        house(world, cx + 3, baseY + 1, cz - 3, false, random);
        house(world, cx - 3, baseY + 1, cz + 3, true, random);
        house(world, cx + 3, baseY + 1, cz + 3, false, random);

        int floorY = baseY + 1;
        // A crooked crossroad ties four ruined cottages to the central ritual shrine.
        for (int z = -4; z <= 4; z++) {
            put(world, cx, floorY, cz + z, (Math.abs(z) % 2 == 0) ? Material.COARSE_DIRT : Material.GRAVEL);
        }
        for (int x = -4; x <= 4; x++) {
            put(world, cx + x, floorY, cz + 1, (Math.abs(x) % 2 == 0) ? Material.GRAVEL : Material.COARSE_DIRT);
        }
        // The shrine is deliberately on the open square, never beneath a cottage roof.
        put(world, cx, floorY, cz, Material.COARSE_DIRT);
        put(world, cx, floorY + 1, cz, Material.CRYING_OBSIDIAN);
        put(world, cx, floorY + 2, cz, Material.CHISELED_POLISHED_BLACKSTONE);
        put(world, cx, floorY + 3, cz, Material.SOUL_LANTERN);
        put(world, cx - 1, floorY, cz, Material.JACK_O_LANTERN);
        put(world, cx + 1, floorY, cz, Material.CARVED_PUMPKIN);

        // Two crooked soul-lantern posts illuminate the entrance path.
        for (int side : new int[]{-1, 1}) {
            int x = cx + side * 4;
            put(world, x, floorY, cz, Material.SPRUCE_FENCE);
            put(world, x, floorY + 1, cz, Material.SOUL_LANTERN);
        }
        put(world, cx + 5, floorY, cz + 2, Material.SPRUCE_FENCE);
        put(world, cx + 5, floorY + 1, cz + 2, Material.COBWEB);
        put(world, cx - 5, floorY, cz - 1, Material.SPRUCE_FENCE);
        put(world, cx - 5, floorY + 1, cz - 1, Material.COBWEB);
        put(world, cx - 4, floorY, cz + 2, Material.WITHER_ROSE);
        put(world, cx + 4, floorY, cz - 2, Material.BROWN_MUSHROOM);
    }

    private void house(World world, int cx, int floorY, int cz, boolean eastDoor, Random random) {
        // A 5x5 ruined timber cottage: raised floor, exposed corner logs, dark walls and stair roof.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                put(world, cx + dx, floorY, cz + dz, edge ? Material.COBBLED_DEEPSLATE : Material.SPRUCE_PLANKS);
                for (int dy = 1; dy <= 3; dy++) {
                    if (edge) {
                        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                        boolean doorway = eastDoor ? dx == 2 && dz == 0 && dy <= 2 : dz == 2 && dx == 0 && dy <= 2;
                        boolean window = !corner && dy == 2 && ((dx == -2 && dz == 0) || (dx == 2 && dz == 0));
                        if (doorway) continue;
                        put(world, cx + dx, floorY + dy, cz + dz,
                                corner ? Material.SPRUCE_LOG : window ? Material.BLACK_STAINED_GLASS_PANE : Material.DARK_OAK_PLANKS);
                    } else {
                        put(world, cx + dx, floorY + dy, cz + dz, Material.AIR);
                    }
                }
            }
        }
        int roofY = floorY + 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edgeX = Math.abs(dx) == 2;
                boolean edgeZ = Math.abs(dz) == 2;
                if (edgeX || edgeZ) {
                    BlockFace face = edgeZ ? (dz < 0 ? BlockFace.NORTH : BlockFace.SOUTH)
                            : (dx < 0 ? BlockFace.WEST : BlockFace.EAST);
                    BlockData data = Material.DARK_OAK_STAIRS.createBlockData();
                    if (data instanceof Stairs stairs) stairs.setFacing(face);
                    world.getBlockAt(cx + dx, roofY, cz + dz).setBlockData(data, false);
                } else {
                    put(world, cx + dx, roofY, cz + dz, Material.DARK_OAK_PLANKS);
                }
            }
        }
        // Small props differ by cottage, so each house feels abandoned rather than stamped.
        put(world, cx, floorY + 1, cz, Material.SOUL_TORCH);
        if (random.nextBoolean()) put(world, cx + 1, floorY + 1, cz + 1, Material.CAULDRON);
        if (random.nextBoolean()) put(world, cx - 1, floorY + 3, cz + 1, Material.COBWEB);
        // A broken chimney and an occasional roof web add skyline detail.
        put(world, cx + 1, roofY + 1, cz + 1, Material.COBBLED_DEEPSLATE_WALL);
        put(world, cx + 1, roofY + 2, cz + 1, Material.SOUL_LANTERN);
        if (random.nextBoolean()) put(world, cx - 2, roofY + 1, cz + 2, Material.COBWEB);
    }

    private void buildGraveyard(World world, int cx, int baseY, int cz, Random random) {
        int floorY = baseY + 1;
        // Low stone boundary with an open gate; no digging, cave or hole is generated.
        for (int offset = -5; offset <= 5; offset++) {
            if (offset != 0) {
                put(world, cx + offset, floorY, cz - 5, Material.MOSSY_COBBLESTONE_WALL);
                put(world, cx + offset, floorY, cz + 5, Material.MOSSY_COBBLESTONE_WALL);
                put(world, cx - 5, floorY, cz + offset, Material.MOSSY_COBBLESTONE_WALL);
                put(world, cx + 5, floorY, cz + offset, Material.MOSSY_COBBLESTONE_WALL);
            }
        }
        for (int[] corner : new int[][]{{-5,-5},{5,-5},{-5,5},{5,5}}) {
            for (int dy = 0; dy <= 2; dy++) put(world, cx + corner[0], floorY + dy, cz + corner[1], Material.POLISHED_BLACKSTONE_BRICKS);
            put(world, cx + corner[0], floorY + 3, cz + corner[1], Material.SOUL_LANTERN);
        }
        for (int[] grave : new int[][]{{-2,-2},{2,-2},{-2,2},{2,2}}) {
            graveMarker(world, cx + grave[0], floorY, cz + grave[1], random.nextBoolean());
        }
        // A worn plus-shaped path guides visitors to the altar and separates grave plots.
        for (int offset = -4; offset <= 4; offset++) {
            if (offset == 0) continue;
            put(world, cx, floorY, cz + offset, (Math.abs(offset) % 2 == 0) ? Material.GRAVEL : Material.COARSE_DIRT);
            put(world, cx + offset, floorY, cz, (Math.abs(offset) % 2 == 0) ? Material.COARSE_DIRT : Material.GRAVEL);
        }
        put(world, cx, floorY, cz, Material.COARSE_DIRT);
        put(world, cx, floorY + 1, cz, Material.CRYING_OBSIDIAN);
        put(world, cx, floorY + 2, cz, Material.CHISELED_POLISHED_BLACKSTONE);
        put(world, cx, floorY + 3, cz, Material.SOUL_LANTERN);
        // Four ritual candles, cobwebs and dead vegetation enrich the otherwise bare plots.
        put(world, cx - 1, floorY + 1, cz, Material.RED_CANDLE);
        put(world, cx + 1, floorY + 1, cz, Material.RED_CANDLE);
        put(world, cx, floorY + 1, cz - 1, Material.RED_CANDLE);
        put(world, cx, floorY + 1, cz + 1, Material.RED_CANDLE);
        put(world, cx - 4, floorY + 2, cz - 4, Material.COBWEB);
        put(world, cx + 4, floorY + 2, cz + 4, Material.COBWEB);
        put(world, cx - 4, floorY, cz + 2, Material.WITHER_ROSE);
        put(world, cx + 4, floorY, cz - 2, Material.WITHER_ROSE);
        // Distinct wither roses identify the place as a graveyard; no hole is dug.
        put(world, cx - 3, floorY, cz + 3, Material.WITHER_ROSE);
        put(world, cx + 3, floorY, cz + 3, Material.WITHER_ROSE);
    }

    private void graveMarker(World world, int x, int floorY, int z, boolean cracked) {
        put(world, x, floorY, z, cracked ? Material.POLISHED_BLACKSTONE_BRICK_SLAB : Material.MOSSY_STONE_BRICK_SLAB);
        Material stone = cracked ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.MOSSY_STONE_BRICKS;
        put(world, x, floorY + 1, z, stone);
        put(world, x, floorY + 2, z, Material.CHISELED_POLISHED_BLACKSTONE);
        put(world, x, floorY + 2, z - 1, stone);
        put(world, x, floorY + 2, z + 1, stone);
        put(world, x, floorY + 3, z, stone);
        put(world, x, floorY + 4, z, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
    }

    private void fillNaturalHeightDifferences(World world, int cx, int baseY, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block ground = findNaturalGround(world, cx + dx, cz + dz);
                if (ground == null || ground.getY() >= baseY) continue;
                // Lift lower natural ground up to the build plane. Never remove ground or dig down.
                for (int y = ground.getY() + 1; y <= baseY; y++) {
                    Block fill = world.getBlockAt(cx + dx, y, cz + dz);
                    if (fill.getType().isAir() || isReplaceableVegetation(fill.getType())) {
                        fill.setType(Material.COBBLED_DEEPSLATE, false);
                    }
                }
            }
        }
    }

    private void put(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    public boolean isGeneratedVillageNear(Location location, double radius) {
        if (location == null || location.getWorld() == null || radius < 0) return false;
        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        int range = Math.max(1, (int) Math.ceil(radius / 16.0D) + 1);
        double radiusSquared = radius * radius;
        for (int cx = chunkX - range; cx <= chunkX + range; cx++) {
            for (int cz = chunkZ - range; cz <= chunkZ + range; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                Chunk chunk = world.getChunkAt(cx, cz);
                var data = chunk.getPersistentDataContainer();
                String type = data.get(structureKey, PersistentDataType.STRING);
                if (type == null) type = data.get(legacyStructureKey, PersistentDataType.STRING);
                if (!HAUNTED_VILLAGE.equals(type)) continue;
                int x = data.getOrDefault(centerXKey, PersistentDataType.INTEGER, (cx << 4) + 8);
                int y = data.getOrDefault(centerYKey, PersistentDataType.INTEGER, location.getBlockY());
                int z = data.getOrDefault(centerZKey, PersistentDataType.INTEGER, (cz << 4) + 8);
                double dx = location.getX() - x;
                double dz = location.getZ() - z;
                if (dx * dx + dz * dz <= radiusSquared && Math.abs(location.getY() - y) <= 12) return true;
            }
        }
        return false;
    }
}
