package cz.halloween.core;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Creates very sparse, vanilla-only Halloween accents in newly generated Overworld chunks.
 * Existing chunks and structures are never retroactively scanned or modified.
 */
public final class HalloweenWorldFeatureManager implements Listener {
    private static final Set<Material> SAFE_GROUND = Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.PODZOL,
            Material.MYCELIUM,
            Material.MOSS_BLOCK
    );
    private static final Set<String> PUMPKIN_BIOMES = Set.of(
            "PLAINS", "SUNFLOWER_PLAINS", "FOREST", "FLOWER_FOREST",
            "BIRCH_FOREST", "OLD_GROWTH_BIRCH_FOREST", "DARK_FOREST",
            "TAIGA", "OLD_GROWTH_PINE_TAIGA", "OLD_GROWTH_SPRUCE_TAIGA",
            "MEADOW", "CHERRY_GROVE", "SWAMP", "MANGROVE_SWAMP",
            "WINDSWEPT_FOREST", "WINDSWEPT_HILLS", "WINDSWEPT_GRAVELLY_HILLS"
    );
    private static final List<BlockFace> HORIZONTAL_FACES = List.of(
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    );
    private static final List<BlockFace> ALL_FACES = List.of(
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
    );

    private final HalloweenCore plugin;
    private final NamespacedKey processedChunkKey;
    private final Set<String> warnedErrors = new HashSet<>();

    public HalloweenWorldFeatureManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.processedChunkKey = new NamespacedKey(plugin, "vanilla_world_features_v1");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("world-features.enabled", true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled()
                || !plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("world-features.generation.enabled", true)
                || !event.isNewChunk()) return;

        Chunk chunk = event.getChunk();
        if (chunk.getWorld().getEnvironment() != World.Environment.NORMAL
                || !plugin.isEligibleGameplayWorld(chunk.getWorld())
                || chunk.getPersistentDataContainer().has(processedChunkKey, PersistentDataType.BYTE)) return;

        try {
            generateForChunk(chunk);
        } catch (RuntimeException ex) {
            String type = ex.getClass().getName();
            if (warnedErrors.add(type)) {
                plugin.getLogger().warning("Vanilla world feature generation failed in "
                        + chunk.getWorld().getName() + " chunk "
                        + chunk.getX() + "," + chunk.getZ() + " (" + type
                        + "). Further errors of this type will be suppressed.");
            }
        } finally {
            chunk.getPersistentDataContainer().set(processedChunkKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private void generateForChunk(Chunk chunk) {
        double pumpkinChance = clamp(plugin.getConfig().getDouble(
                "world-features.generation.pumpkin-patch-chance-per-chunk", 0.06D), 0.0D, 1.0D);
        double webChance = clamp(plugin.getConfig().getDouble(
                "world-features.generation.cave-web-patch-chance-per-chunk", 0.04D), 0.0D, 1.0D);
        double altarChance = clamp(plugin.getConfig().getDouble(
                "world-features.generation.ancient-altar-chance-per-chunk", 0.02D), 0.0D, 1.0D);
        long seed = chunk.getWorld().getSeed()
                ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L)
                ^ 0x48414C4C46454154L;
        Random random = new Random(seed);

        if (pumpkinChance > 0.0D && random.nextDouble() < pumpkinChance) {
            createPumpkinPatch(chunk, random);
        }
        if (webChance > 0.0D && random.nextDouble() < webChance) {
            createCaveWebPatch(chunk, random);
        }
        if (altarChance > 0.0D && random.nextDouble() < altarChance) {
            createAncientAltar(chunk, random);
        }
    }

    private boolean createPumpkinPatch(Chunk chunk, Random random) {
        int attempts = boundedInt(
                "world-features.generation.attempts-per-feature", 24, 1, 64);
        int maxPumpkins = boundedInt(
                "world-features.generation.max-pumpkins-per-patch", 4, 2, 6);
        World world = chunk.getWorld();

        for (int attempt = 0; attempt < attempts; attempt++) {
            // A 5x5 local ground check stays inside this chunk even at the edges.
            int centerLocalX = random.nextInt(3, 13);
            int centerLocalZ = random.nextInt(3, 13);
            int centerX = (chunk.getX() << 4) + centerLocalX;
            int centerZ = (chunk.getZ() << 4) + centerLocalZ;
            Block centerGround = world.getHighestBlockAt(centerX, centerZ);
            if (!isSafeOpenGround(centerGround) || !PUMPKIN_BIOMES.contains(centerGround.getBiome().name())) continue;
            if (!isOpenNaturalArea(world, centerX, centerZ)) continue;

            List<int[]> offsets = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    offsets.add(new int[]{dx, dz});
                }
            }
            java.util.Collections.shuffle(offsets, random);

            int placed = 0;
            List<Block> placedBlocks = new ArrayList<>();
            boolean placedLantern = false;
            for (int[] offset : offsets) {
                if (placed >= maxPumpkins) break;
                int localX = centerLocalX + offset[0];
                int localZ = centerLocalZ + offset[1];
                // Offsets are limited to 1..14, avoiding all neighbor-chunk reads.
                Block ground = world.getHighestBlockAt(
                        (chunk.getX() << 4) + localX,
                        (chunk.getZ() << 4) + localZ);
                if (!isSafeOpenGround(ground)) continue;
                if (Math.abs(ground.getY() - centerGround.getY()) > 1) continue;
                if (!PUMPKIN_BIOMES.contains(ground.getBiome().name())) continue;

                Block target = ground.getRelative(0, 1, 0);
                if (!target.getType().isAir() || hasTileStateInNeighborhood(target)) continue;

                Material pumpkin = Material.PUMPKIN;
                if (!placedLantern && random.nextInt(8) == 0) {
                    pumpkin = Material.JACK_O_LANTERN;
                    placedLantern = true;
                }
                target.setType(pumpkin, false);
                placedBlocks.add(target);
                placed++;
            }
            if (placed >= 2) {
                plugin.getLogger().fine("Generated vanilla pumpkin patch at "
                        + world.getName() + " chunk " + chunk.getX() + "," + chunk.getZ());
                return true;
            }
            // If fewer than two safe sites were found, undo only blocks this
            // attempt placed; never remove naturally generated pumpkins.
            for (Block placedBlock : placedBlocks) {
                placedBlock.setType(Material.AIR, false);
            }
        }
        return false;
    }

    /**
     * Builds a small ruined altar entirely in clear space above natural ground.
     * It never replaces terrain blocks and never reads neighboring chunks.
     */
    private boolean createAncientAltar(Chunk chunk, Random random) {
        int attempts = boundedInt(
                "world-features.generation.altar-attempts-per-feature", 16, 1, 32);
        World world = chunk.getWorld();
        List<int[]> offsets = List.of(
                new int[]{0, 0},
                new int[]{-1, 0},
                new int[]{1, 0},
                new int[]{0, -1},
                new int[]{0, 1}
        );
        List<Material> stones = List.of(
                Material.MOSSY_COBBLESTONE,
                Material.MOSSY_COBBLESTONE,
                Material.CRACKED_STONE_BRICKS,
                Material.MOSSY_COBBLESTONE,
                Material.MOSSY_COBBLESTONE
        );

        for (int attempt = 0; attempt < attempts; attempt++) {
            int centerLocalX = random.nextInt(3, 13);
            int centerLocalZ = random.nextInt(3, 13);
            int centerX = (chunk.getX() << 4) + centerLocalX;
            int centerZ = (chunk.getZ() << 4) + centerLocalZ;
            Block centerGround = world.getHighestBlockAt(centerX, centerZ);
            if (!isSafeOpenGround(centerGround)
                    || !PUMPKIN_BIOMES.contains(centerGround.getBiome().name())
                    || !isOpenNaturalArea(world, centerX, centerZ)) continue;

            List<Block> targets = new ArrayList<>();
            boolean valid = true;
            for (int[] offset : offsets) {
                int x = centerX + offset[0];
                int z = centerZ + offset[1];
                Block ground = world.getHighestBlockAt(x, z);
                if (!isSafeOpenGround(ground)
                        || Math.abs(ground.getY() - centerGround.getY()) > 1
                        || !PUMPKIN_BIOMES.contains(ground.getBiome().name())) {
                    valid = false;
                    break;
                }
                Block target = ground.getRelative(0, 1, 0);
                if (!target.getType().isAir() || hasTileStateInNeighborhood(target)) {
                    valid = false;
                    break;
                }
                targets.add(target);
            }
            if (!valid) continue;

            Block lanternTarget = targets.get(0).getRelative(0, 1, 0);
            if (!lanternTarget.getType().isAir()
                    || hasTileStateInNeighborhood(lanternTarget)) continue;

            List<Block> placedBlocks = new ArrayList<>();
            try {
                for (int i = 0; i < targets.size(); i++) {
                    Block target = targets.get(i);
                    target.setType(stones.get(i), false);
                    placedBlocks.add(target);
                }
                lanternTarget.setType(Material.SOUL_LANTERN, false);
                placedBlocks.add(lanternTarget);
            } catch (RuntimeException ex) {
                // Roll back only the air blocks placed by this failed attempt.
                for (Block placed : placedBlocks) {
                    placed.setType(Material.AIR, false);
                }
                throw ex;
            }

            plugin.getLogger().fine("Generated vanilla ancient altar at "
                    + world.getName() + " chunk " + chunk.getX() + "," + chunk.getZ());
            return true;
        }
        return false;
    }

    private boolean createCaveWebPatch(Chunk chunk, Random random) {
        int attempts = boundedInt(
                "world-features.generation.attempts-per-feature", 24, 1, 64);
        int maxWebs = boundedInt(
                "world-features.generation.max-cave-webs-per-patch", 2, 1, 3);
        World world = chunk.getWorld();
        int configuredMinY = plugin.getConfig().getInt("world-features.generation.cave-webs-min-y", -48);
        int configuredMaxY = plugin.getConfig().getInt("world-features.generation.cave-webs-max-y", 24);
        int minY = Math.max(world.getMinHeight() + 2, configuredMinY);
        int maxY = Math.min(world.getMaxHeight() - 3, configuredMaxY);
        if (minY > maxY) return false;

        int placed = 0;
        for (int attempt = 0; attempt < attempts && placed < maxWebs; attempt++) {
            int localX = random.nextInt(1, 15);
            int localZ = random.nextInt(1, 15);
            int y = random.nextInt(minY, maxY + 1);
            Block target = chunk.getBlock(localX, y, localZ);
            if (!target.getType().isAir() || hasTileStateInNeighborhood(target)) continue;

            int stoneAnchors = 0;
            for (BlockFace face : ALL_FACES) {
                if (isNaturalAnchor(target.getRelative(face).getType())) stoneAnchors++;
            }
            // Require a genuine cave pocket rather than open air above ground.
            if (stoneAnchors < 3) continue;

            target.setType(Material.COBWEB, false);
            placed++;
        }

        if (placed > 0) {
            plugin.getLogger().fine("Generated vanilla cave webs at "
                    + world.getName() + " chunk " + chunk.getX() + "," + chunk.getZ());
            return true;
        }
        return false;
    }

    private boolean isOpenNaturalArea(World world, int centerX, int centerZ) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block ground = world.getHighestBlockAt(centerX + dx, centerZ + dz);
                if (!isSafeOpenGround(ground)) return false;
                Block headspace = ground.getRelative(0, 1, 0);
                // Passable grass and flowers may surround a patch; actual pumpkin
                // placement still requires air and checks tile entities separately.
                if (!headspace.getType().isAir() && !headspace.isPassable()) return false;
            }
        }
        return true;
    }

    private boolean isSafeOpenGround(Block ground) {
        return ground != null
                && SAFE_GROUND.contains(ground.getType())
                && ground.getY() > ground.getWorld().getMinHeight()
                && ground.getY() < ground.getWorld().getMaxHeight() - 3;
    }

    private boolean hasTileStateInNeighborhood(Block target) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (target.getRelative(dx, dy, dz).getState() instanceof TileState) return true;
                }
            }
        }
        return false;
    }

    private boolean isNaturalAnchor(Material material) {
        return material == Material.STONE
                || material == Material.DEEPSLATE
                || material == Material.TUFF
                || material == Material.ANDESITE
                || material == Material.DIORITE
                || material == Material.GRANITE
                || material == Material.CALCITE
                || material == Material.DRIPSTONE_BLOCK
                || material == Material.DIRT
                || material == Material.CLAY;
    }

    private int boundedInt(String path, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, plugin.getConfig().getInt(path, fallback)));
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
