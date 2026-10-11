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
import org.bukkit.block.data.type.Candle;
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
        this.checkedKey = new NamespacedKey(plugin, "halloween_structure_checked_v4");
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
        // Keep each build within its origin chunk (16x16 blocks) to avoid forced
        // neighbour chunk loads and cross-chunk half-built structures.
        int footprintRadius = 7;
        Terrain terrain = findFlatPlatformY(world, centerX, centerZ, footprintRadius);
        if (terrain == null) return;

        // Fill only low natural spots and clear replaceable plants/leaves. This routine
        // never excavates downwards or creates a cave/pit.
        preparePlatform(world, centerX, terrain.baseY(), centerZ, footprintRadius);
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
                for (int y = ground.getY() + 1; y <= baseY + 10; y++) {
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
        if (maxY <= world.getMinHeight() || maxY + 12 >= world.getMaxHeight()) return null;

        // Every block over the entire footprint must be air or natural replaceable vegetation.
        // Houses/graves never overwrite a tree, building, chest or existing structure.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block ground = findNaturalGround(world, centerX + dx, centerZ + dz);
                if (ground == null) return null;
                for (int y = ground.getY() + 1; y <= maxY + 10; y++) {
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
        int floorY = baseY + 1;

        // Three individually dressed cottages and one gothic ritual chapel fill the four
        // corners without touching each other. All footprints stay inside this chunk.
        house(world, cx - 4, floorY, cz - 4, true, random);
        house(world, cx + 4, floorY, cz - 4, false, random);
        house(world, cx - 4, floorY, cz + 4, true, random);
        buildRitualChapel(world, cx + 4, floorY, cz + 4, random);

        // Four-arm road and a short branch that leads from the square to the chapel entrance.
        for (int offset = -7; offset <= 7; offset++) {
            Material northSouth = Math.abs(offset) % 3 == 0 ? Material.GRAVEL : Material.COARSE_DIRT;
            Material eastWest = Math.abs(offset) % 3 == 0 ? Material.COARSE_DIRT : Material.GRAVEL;
            put(world, cx, floorY, cz + offset, northSouth);
            put(world, cx + offset, floorY, cz, eastWest);
        }
        for (int x = cx; x <= cx + 2; x++) {
            put(world, x, floorY, cz + 4, x % 2 == 0 ? Material.GRAVEL : Material.COARSE_DIRT);
        }

        // Layered ritual altar with four candles and worn stones at its corners.
        put(world, cx, floorY, cz, Material.COARSE_DIRT);
        put(world, cx, floorY + 1, cz, Material.CRYING_OBSIDIAN);
        put(world, cx, floorY + 2, cz, Material.CHISELED_POLISHED_BLACKSTONE);
        put(world, cx, floorY + 3, cz, Material.SOUL_LANTERN);
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) put(world, cx + dx, floorY, cz + dz, Material.MOSSY_COBBLESTONE);
        }
        putLitCandle(world, cx - 1, floorY + 1, cz - 1);
        putLitCandle(world, cx + 1, floorY + 1, cz - 1);
        putLitCandle(world, cx - 1, floorY + 1, cz + 1);
        putLitCandle(world, cx + 1, floorY + 1, cz + 1);
        put(world, cx - 2, floorY, cz, Material.MOSSY_COBBLESTONE);
        put(world, cx + 2, floorY, cz, Material.MOSSY_COBBLESTONE);
        put(world, cx, floorY, cz - 2, Material.MOSSY_COBBLESTONE);
        put(world, cx, floorY, cz + 2, Material.MOSSY_COBBLESTONE);

        // A broken spruce-fence perimeter has four wide entrances aligned to the roads.
        for (int offset = -7; offset <= 7; offset++) {
            if (Math.abs(offset) > 1) {
                put(world, cx + offset, floorY, cz - 7, Material.SPRUCE_FENCE);
                put(world, cx + offset, floorY, cz + 7, Material.SPRUCE_FENCE);
                put(world, cx - 7, floorY, cz + offset, Material.SPRUCE_FENCE);
                put(world, cx + 7, floorY, cz + offset, Material.SPRUCE_FENCE);
            }
        }
        for (int[] gate : new int[][]{{-2,-7},{2,-7},{-2,7},{2,7},{-7,-2},{-7,2},{7,-2},{7,2}}) {
            int gx = cx + gate[0];
            int gz = cz + gate[1];
            put(world, gx, floorY, gz, Material.COBBLED_DEEPSLATE);
            put(world, gx, floorY + 1, gz, Material.POLISHED_BLACKSTONE_BRICKS);
            put(world, gx, floorY + 2, gz, Material.POLISHED_BLACKSTONE_BRICKS);
            put(world, gx, floorY + 3, gz, Material.SOUL_LANTERN);
        }

        // Optional accents are confined to the open plaza, never to house walls or roofs.
        for (int side : new int[]{-1, 1}) {
            if (random.nextBoolean()) put(world, cx + side, floorY + 4, cz, Material.COBWEB);
            if (random.nextBoolean()) put(world, cx, floorY + 4, cz + side, Material.COBWEB);
            if (random.nextInt(3) == 0) {
                put(world, cx + side, floorY, cz + side * 2,
                        random.nextBoolean() ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM);
            }
        }
    }

    private void house(World world, int cx, int floorY, int cz, boolean eastDoor, Random random) {
        // Each cottage uses a different timber/roof palette and a chance of a broken roof edge.
        Material wallMaterial = switch (random.nextInt(3)) {
            case 0 -> Material.SPRUCE_PLANKS;
            case 1 -> Material.DARK_OAK_PLANKS;
            default -> Material.MUD_BRICKS;
        };
        Material roofStairs = random.nextBoolean() ? Material.DARK_OAK_STAIRS : Material.SPRUCE_STAIRS;
        Material roofPlanks = random.nextBoolean() ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS;
        Material windowMaterial = random.nextBoolean() ? Material.BLACK_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        Material cornerLog = wallMaterial == Material.SPRUCE_PLANKS ? Material.SPRUCE_LOG : Material.DARK_OAK_LOG;

        // 5x5 cottage: stone footing, exposed log frame, stained-glass windows and raised floor.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                put(world, cx + dx, floorY, cz + dz,
                        edge ? Material.COBBLED_DEEPSLATE : wallMaterial);
                for (int dy = 1; dy <= 3; dy++) {
                    if (edge) {
                        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                        boolean doorway = eastDoor ? dx == 2 && dz == 0 && dy <= 2
                                : dz == 2 && dx == 0 && dy <= 2;
                        boolean window = !corner && dy == 2
                                && ((dx == -2 && dz == 0) || (dx == 2 && dz == 0));
                        if (doorway) continue;
                        put(world, cx + dx, floorY + dy, cz + dz,
                                corner ? cornerLog : window ? windowMaterial : wallMaterial);
                    } else {
                        put(world, cx + dx, floorY + dy, cz + dz, Material.AIR);
                    }
                }
            }
        }

        // Real gabled roof: lower eaves, raised slopes, dark ridge and two timber gable ends.
        int roofY = floorY + 4;
        for (int z = -2; z <= 2; z++) {
            placeStairs(world, cx - 2, roofY, cz + z, roofStairs, BlockFace.WEST);
            placeStairs(world, cx - 1, roofY + 1, cz + z, roofStairs, BlockFace.WEST);
            put(world, cx, roofY + 2, cz + z, roofPlanks);
            placeStairs(world, cx + 1, roofY + 1, cz + z, roofStairs, BlockFace.EAST);
            placeStairs(world, cx + 2, roofY, cz + z, roofStairs, BlockFace.EAST);
        }
        for (int z : new int[]{-2, 2}) {
            put(world, cx - 1, roofY, cz + z, wallMaterial);
            put(world, cx, roofY, cz + z, windowMaterial);
            put(world, cx + 1, roofY, cz + z, wallMaterial);
            put(world, cx, roofY + 1, cz + z, wallMaterial);
        }
        if (random.nextInt(4) == 0) {
            put(world, cx + (random.nextBoolean() ? -1 : 1), roofY + 1, cz + 2, Material.AIR);
        }

        // Interior props, chimney and one or two hanging webs make the houses less repetitive.
        put(world, cx, floorY + 1, cz, Material.SOUL_TORCH);
        if (random.nextBoolean()) put(world, cx + 1, floorY + 1, cz + 1, Material.CAULDRON);
        if (random.nextBoolean()) put(world, cx - 1, floorY + 3, cz + 1, Material.COBWEB);
        put(world, cx, roofY + 3, cz + 1, Material.COBBLED_DEEPSLATE_WALL);
        put(world, cx, roofY + 4, cz + 1, Material.SOUL_LANTERN);
        if (random.nextBoolean()) put(world, cx - 2, roofY + 1, cz + 2, Material.COBWEB);

        // A single exterior step and lantern posts make each entrance readable at night.
        if (eastDoor) {
            placeStairs(world, cx + 3, floorY, cz, Material.COBBLED_DEEPSLATE_STAIRS, BlockFace.EAST);
            for (int side : new int[]{-1, 1}) {
                put(world, cx + 3, floorY, cz + side, Material.SPRUCE_FENCE);
                put(world, cx + 3, floorY + 1, cz + side, Material.SOUL_LANTERN);
            }
        } else {
            placeStairs(world, cx, floorY, cz + 3, Material.COBBLED_DEEPSLATE_STAIRS, BlockFace.SOUTH);
            for (int side : new int[]{-1, 1}) {
                put(world, cx + side, floorY, cz + 3, Material.SPRUCE_FENCE);
                put(world, cx + side, floorY + 1, cz + 3, Material.SOUL_LANTERN);
            }
        }
    }

    private void buildRitualChapel(World world, int cx, int floorY, int cz, Random random) {
        // Compact gothic chapel: west-facing entrance, crimson windows, gabled roof and spire.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                put(world, cx + dx, floorY, cz + dz,
                        edge ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DARK_OAK_PLANKS);
                for (int dy = 1; dy <= 3; dy++) {
                    if (dx == -2 && dz == 0 && dy <= 2) continue;
                    if (!edge) {
                        put(world, cx + dx, floorY + dy, cz + dz, Material.AIR);
                        continue;
                    }
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    boolean window = dy == 2 && ((Math.abs(dx) == 2 && dz == 0)
                            || (Math.abs(dz) == 2 && dx == 0));
                    Material wall = corner ? Material.POLISHED_BLACKSTONE_BRICKS
                            : window ? Material.RED_STAINED_GLASS_PANE : Material.DARK_OAK_PLANKS;
                    put(world, cx + dx, floorY + dy, cz + dz, wall);
                }
            }
        }

        // Four steep roof lines meet at a raised dark ridge.
        for (int z = -2; z <= 2; z++) {
            placeStairs(world, cx - 2, floorY + 4, cz + z, Material.DARK_OAK_STAIRS, BlockFace.WEST);
            placeStairs(world, cx - 1, floorY + 5, cz + z, Material.DARK_OAK_STAIRS, BlockFace.WEST);
            put(world, cx, floorY + 6, cz + z, Material.DARK_OAK_PLANKS);
            placeStairs(world, cx + 1, floorY + 5, cz + z, Material.DARK_OAK_STAIRS, BlockFace.EAST);
            placeStairs(world, cx + 2, floorY + 4, cz + z, Material.DARK_OAK_STAIRS, BlockFace.EAST);
        }
        for (int z : new int[]{-2, 2}) {
            put(world, cx - 1, floorY + 4, cz + z, Material.DARK_OAK_PLANKS);
            put(world, cx + 1, floorY + 4, cz + z, Material.DARK_OAK_PLANKS);
            put(world, cx, floorY + 4, cz + z, Material.RED_STAINED_GLASS_PANE);
            put(world, cx, floorY + 5, cz + z, Material.POLISHED_BLACKSTONE_BRICKS);
        }

        // External buttresses, candlelit altar and a bell under the ridge.
        for (int side : new int[]{-1, 1}) {
            put(world, cx - 3, floorY + 1, cz + side, Material.COBBLED_DEEPSLATE_WALL);
            put(world, cx - 3, floorY + 2, cz + side, Material.SOUL_LANTERN);
        }
        put(world, cx, floorY + 3, cz, Material.BELL);
        put(world, cx, floorY + 1, cz - 1, Material.CRYING_OBSIDIAN);
        put(world, cx, floorY + 2, cz - 1, Material.CHISELED_POLISHED_BLACKSTONE);
        putLitCandle(world, cx - 1, floorY + 1, cz - 1);
        putLitCandle(world, cx + 1, floorY + 1, cz - 1);
        put(world, cx, floorY + 7, cz - 1, Material.POLISHED_BLACKSTONE_BRICKS);
        put(world, cx, floorY + 8, cz - 1, Material.IRON_BARS);
        put(world, cx, floorY + 9, cz - 1, Material.SOUL_LANTERN);
        if (random.nextBoolean()) put(world, cx + 1, floorY + 1, cz + 1, Material.CAULDRON);
    }

    private void buildGraveyard(World world, int cx, int baseY, int cz, Random random) {
        int floorY = baseY + 1;

        // Heavy, uneven cemetery wall with one clear three-block entrance in the south.
        for (int offset = -6; offset <= 6; offset++) {
            put(world, cx + offset, floorY, cz - 6, Material.MOSSY_COBBLESTONE_WALL);
            if (Math.abs(offset) > 1) put(world, cx + offset, floorY, cz + 6, Material.MOSSY_COBBLESTONE_WALL);
            put(world, cx - 6, floorY, cz + offset, Material.MOSSY_COBBLESTONE_WALL);
            put(world, cx + 6, floorY, cz + offset, Material.MOSSY_COBBLESTONE_WALL);
        }
        for (int[] corner : new int[][]{{-6,-6},{6,-6},{-6,6},{6,6}}) {
            for (int dy = 0; dy <= 2; dy++) {
                put(world, cx + corner[0], floorY + dy, cz + corner[1], Material.POLISHED_BLACKSTONE_BRICKS);
            }
            put(world, cx + corner[0], floorY + 3, cz + corner[1], Material.SOUL_LANTERN);
        }

        // Entrance arch with two stone pillars and a lintel above the walking gap.
        for (int side : new int[]{-1, 1}) {
            put(world, cx + side * 2, floorY, cz + 6, Material.COBBLED_DEEPSLATE);
            put(world, cx + side * 2, floorY + 1, cz + 6, Material.POLISHED_BLACKSTONE_BRICKS);
            put(world, cx + side * 2, floorY + 2, cz + 6, Material.POLISHED_BLACKSTONE_BRICKS);
            put(world, cx + side * 2, floorY + 3, cz + 6, Material.CHISELED_POLISHED_BLACKSTONE);
        }
        for (int x = -1; x <= 1; x++) {
            put(world, cx + x, floorY + 3, cz + 6, Material.POLISHED_BLACKSTONE_BRICKS);
        }

        // Walk-in above-ground mausoleum with a dark gabled roof at the back of the plot.
        buildMausoleum(world, cx, floorY, cz - 3);

        // Five raised grave markers are arranged around the altar and curved path.
        for (int[] grave : new int[][]{{-3,0},{3,0},{-3,3},{0,3},{3,3}}) {
            graveMarker(world, cx + grave[0], floorY, cz + grave[1], random.nextBoolean());
        }

        // The entrance path bends around the centre grave instead of cutting through it.
        for (int z = 5; z >= 4; z--) {
            put(world, cx, floorY, cz + z, z % 2 == 0 ? Material.GRAVEL : Material.COARSE_DIRT);
        }
        for (int z = 4; z >= 1; z--) {
            put(world, cx + 1, floorY, cz + z, z % 2 == 0 ? Material.COARSE_DIRT : Material.GRAVEL);
        }
        put(world, cx, floorY, cz + 1, Material.GRAVEL);
        for (int offset = -2; offset <= 2; offset++) {
            put(world, cx + offset, floorY, cz, Math.abs(offset) % 2 == 0
                    ? Material.COARSE_DIRT : Material.GRAVEL);
        }
        put(world, cx, floorY, cz, Material.COBBLED_DEEPSLATE);
        put(world, cx, floorY + 1, cz, Material.CRYING_OBSIDIAN);
        put(world, cx, floorY + 2, cz, Material.CHISELED_POLISHED_BLACKSTONE);
        put(world, cx, floorY + 3, cz, Material.SOUL_LANTERN);

        // Crimson candles and wither roses border the path without filling the walking lane.
        for (int x : new int[]{-2, 2}) {
            put(world, cx + x, floorY, cz + 1, Material.MOSSY_COBBLESTONE);
            put(world, cx + x, floorY, cz + 2, Material.MOSSY_COBBLESTONE);
            putLitCandle(world, cx + x, floorY + 1, cz + 1);
            putLitCandle(world, cx + x, floorY + 1, cz + 2);
        }
        buildDeadTree(world, cx - 5, floorY, cz - 2, true);
        buildDeadTree(world, cx + 5, floorY, cz - 2, false);
        put(world, cx - 4, floorY, cz + 2, Material.WITHER_ROSE);
        put(world, cx + 4, floorY, cz + 2, Material.WITHER_ROSE);
        put(world, cx - 4, floorY + 2, cz - 5, Material.COBWEB);
        put(world, cx + 4, floorY + 2, cz - 5, Material.COBWEB);
        if (random.nextBoolean()) put(world, cx - 4, floorY, cz + 4, Material.BROWN_MUSHROOM);
        if (random.nextBoolean()) put(world, cx + 4, floorY, cz + 4, Material.RED_MUSHROOM);
    }

    private void buildMausoleum(World world, int cx, int floorY, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                put(world, cx + dx, floorY, cz + dz,
                        edge ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_TILES);
                for (int dy = 1; dy <= 3; dy++) {
                    if (dz == 2 && dx == 0 && dy <= 2) continue;
                    if (!edge) {
                        put(world, cx + dx, floorY + dy, cz + dz, Material.AIR);
                        continue;
                    }
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    boolean window = dy == 2 && dx == 0 && dz == -2;
                    Material wall = corner ? Material.POLISHED_BLACKSTONE_BRICKS
                            : window ? Material.RED_STAINED_GLASS_PANE : Material.DEEPSLATE_BRICKS;
                    put(world, cx + dx, floorY + dy, cz + dz, wall);
                }
            }
        }

        // Massive stone roof with a raised ridge and a clear entrance lintel.
        for (int z = -2; z <= 2; z++) {
            placeStairs(world, cx - 2, floorY + 4, cz + z, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST);
            placeStairs(world, cx - 1, floorY + 5, cz + z, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST);
            put(world, cx, floorY + 6, cz + z, Material.POLISHED_BLACKSTONE_BRICKS);
            placeStairs(world, cx + 1, floorY + 5, cz + z, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST);
            placeStairs(world, cx + 2, floorY + 4, cz + z, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST);
        }
        for (int side : new int[]{-1, 1}) {
            put(world, cx + side, floorY + 3, cz + 2, Material.CHISELED_POLISHED_BLACKSTONE);
            put(world, cx + side, floorY + 1, cz + 2, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        put(world, cx, floorY + 3, cz + 2, Material.CHISELED_POLISHED_BLACKSTONE);
        put(world, cx, floorY + 1, cz - 1, Material.CRYING_OBSIDIAN);
        put(world, cx, floorY + 2, cz - 1, Material.SOUL_LANTERN);
        putLitCandle(world, cx - 1, floorY + 1, cz - 1);
        putLitCandle(world, cx + 1, floorY + 1, cz - 1);
    }

    private void buildDeadTree(World world, int x, int floorY, int z, boolean leanWest) {
        Material log = Material.STRIPPED_DARK_OAK_LOG;
        for (int dy = 0; dy <= 3; dy++) put(world, x, floorY + dy, z, log);
        int side = leanWest ? -1 : 1;
        put(world, x + side, floorY + 2, z, log);
        put(world, x + side * 2, floorY + 3, z, log);
        put(world, x - side, floorY + 3, z - 1, log);
        put(world, x + side, floorY + 4, z - 1, Material.COBWEB);
        put(world, x - side, floorY + 2, z - 2, Material.COBWEB);
        put(world, x + side * 2, floorY + 3, z + 1, Material.COBWEB);
        put(world, x, floorY + 4, z, Material.SOUL_LANTERN);
    }

    private void placeStairs(World world, int x, int y, int z, Material material, BlockFace facing) {
        BlockData data = material.createBlockData();
        if (data instanceof Stairs stairs) stairs.setFacing(facing);
        world.getBlockAt(x, y, z).setBlockData(data, false);
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

    private void putLitCandle(World world, int x, int y, int z) {
        BlockData data = Material.RED_CANDLE.createBlockData();
        if (data instanceof Candle candle) candle.setLit(true);
        world.getBlockAt(x, y, z).setBlockData(data, false);
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
