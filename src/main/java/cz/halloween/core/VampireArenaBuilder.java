package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Builds a large gothic raid arena (97-block diameter) after a separate,
 * read-only preflight and explicit admin confirmation. Never clears overhead
 * obstructions; the selected site must be empty and chunks must already be loaded.
 */
public final class VampireArenaBuilder {
    public static final int RADIUS = 48;
    private static final int CLEARANCE_HEIGHT = 20;
    private static final int MAX_TERRAIN_CUT = 8;
    private static final int MAX_TERRAIN_FILL = 12;

    public record Inspection(boolean clear, String problem, int obstructions, String firstObstruction) {}
    private record Point(int x, int z) {}

    public Inspection inspect(Location center) {
        return inspect(center, null);
    }

    public Inspection inspect(Location center, Player actor) {
        if (center == null || center.getWorld() == null) {
            return new Inspection(false, "Střed arény nemá načtený svět.", 0, "");
        }
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        int floorY = centerY - 1;
        if (floorY < world.getMinHeight() || centerY + CLEARANCE_HEIGHT >= world.getMaxHeight()) {
            return new Inspection(false, "Střed je příliš blízko hranici výšky světa.", 0, "");
        }

        int minChunkX = Math.floorDiv(centerX - RADIUS, 16);
        int maxChunkX = Math.floorDiv(centerX + RADIUS, 16);
        int minChunkZ = Math.floorDiv(centerZ - RADIUS, 16);
        int maxChunkZ = Math.floorDiv(centerZ + RADIUS, 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return new Inspection(false,
                            "Část 97blokové arény není načtená. Přijď blíže a zvyš dohled.",
                            0, "");
                }
            }
        }

        WorldGuardAccess worldGuard;
        try {
            worldGuard = createWorldGuardAccess(actor);
        } catch (ReflectiveOperationException | LinkageError error) {
            return new Inspection(false,
                    "WorldGuard je aktivní, ale ochranu nelze ověřit; stavba byla z bezpečnostních důvodů odmítnuta.",
                    1, error.getClass().getSimpleName());
        }

        int obstructionCount = 0;
        String first = "";
        int r2Limit = RADIUS * RADIUS;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > r2Limit) continue;
                int x = centerX + dx;
                int z = centerZ + dz;
                int groundY = world.getHighestBlockYAt(x, z);

                if (groundY < floorY - MAX_TERRAIN_FILL || groundY > floorY + MAX_TERRAIN_CUT) {
                    obstructionCount++;
                    if (first.isEmpty()) {
                        first = x + " " + groundY + " " + z + " (překročen bezpečný limit řezu/zasypání terénu)";
                    }
                }

                // Low terrain may receive foundation only through air. Hills may be
                // cut only when every affected block is natural and has no tile state.
                if (groundY < floorY) {
                    for (int y = groundY + 1; y < floorY; y++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (!block.getType().isAir() || block.getState() instanceof TileState) {
                            obstructionCount++;
                            if (first.isEmpty()) {
                                first = x + " " + y + " " + z + " (blok uvnitř budoucího základu)";
                            }
                            break;
                        }
                    }
                } else {
                    for (int y = floorY; y <= groundY; y++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (!isSafeNaturalCutBlock(block)) {
                            obstructionCount++;
                            if (first.isEmpty()) {
                                first = x + " " + y + " " + z + " (" + block.getType().name() + " není bezpečný přírodní terén)";
                            }
                            break;
                        }
                    }
                }

                for (int y = centerY; y < centerY + CLEARANCE_HEIGHT; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) continue;
                    if (y <= groundY && isSafeNaturalCutBlock(block)) continue;
                    obstructionCount++;
                    if (first.isEmpty()) {
                        first = x + " " + y + " " + z + " (" + block.getType().name() + " v prostoru arény)";
                    }
                }

                if (worldGuard != null) {
                    String protectionProblem = worldGuardBuildProblem(
                            new Location(world, x + 0.5D, floorY, z + 0.5D), worldGuard);
                    if (protectionProblem != null) {
                        obstructionCount++;
                        if (first.isEmpty()) first = x + " " + floorY + " " + z + " (" + protectionProblem + ")";
                    }
                }
            }
        }
        if (obstructionCount > 0) {
            return new Inspection(false,
                    "Předkontrola odmítla nebezpečnou oblast. Povoleno: řez přírodního terénu do "
                            + MAX_TERRAIN_CUT + " bloků, zásyp do " + MAX_TERRAIN_FILL
                            + " bloků a volný prostor 20 bloků. Stavby a chráněné oblasti se nepřepisují.",
                    obstructionCount, first);
        }
        return new Inspection(true, "", 0, "");
    }

    private record WorldGuardAccess(Method adaptLocation, Method testState, Object query,
                                    Object localPlayer, Object buildFlags) { }

    private WorldGuardAccess createWorldGuardAccess(Player actor) throws ReflectiveOperationException {
        Plugin worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) return null;

        Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
        Object instance = worldGuardClass.getMethod("getInstance").invoke(null);
        Object platform = worldGuardClass.getMethod("getPlatform").invoke(instance);
        Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
        Object query = container.getClass().getMethod("createQuery").invoke(container);

        Class<?> adapterClass = Class.forName("com.sk89q.worldguard.bukkit.BukkitAdapter");
        Method adaptLocation = adapterClass.getMethod("adapt", Location.class);
        Object localPlayer = null;
        if (actor != null) {
            Class<?> pluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object pluginInstance = pluginClass.getMethod("inst").invoke(null);
            localPlayer = pluginClass.getMethod("wrapPlayer", Player.class).invoke(pluginInstance, actor);
        }

        Class<?> associableClass = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
        Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
        Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
        Field buildField = flagsClass.getField("BUILD");
        Object flags = Array.newInstance(stateFlagClass, 1);
        Array.set(flags, 0, buildField.get(null));
        Class<?> locationClass = Class.forName("com.sk89q.worldedit.util.Location");
        Class<?> regionQueryClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
        Method testState = regionQueryClass.getMethod("testState", locationClass, associableClass, flags.getClass());
        return new WorldGuardAccess(adaptLocation, testState, query, localPlayer, flags);
    }

    private String worldGuardBuildProblem(Location location, WorldGuardAccess access) {
        try {
            Object worldEditLocation = access.adaptLocation().invoke(null, location);
            boolean permitted = (Boolean) access.testState().invoke(
                    access.query(), worldEditLocation, access.localPlayer(), access.buildFlags());
            return permitted ? null : "WorldGuard nepovoluje stavbu";
        } catch (ReflectiveOperationException | LinkageError error) {
            return "kontrola WorldGuardu selhala; stavba byla odmítnuta";
        }
    }

    private boolean isSafeNaturalCutBlock(Block block) {
        if (block.getState() instanceof TileState) return false;
        Material type = block.getType();
        return type.isAir() || isGroundCover(type) || switch (type) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM, MOSS_BLOCK, MUD,
                    STONE, GRANITE, DIORITE, ANDESITE, DEEPSLATE, COBBLED_DEEPSLATE,
                    TUFF, CALCITE, DRIPSTONE_BLOCK, GRAVEL, SAND, RED_SAND, CLAY,
                    SANDSTONE, RED_SANDSTONE, MOSSY_COBBLESTONE, COBBLESTONE,
                    SNOW_BLOCK, PACKED_ICE -> true;
            default -> false;
        };
    }

    private boolean isGroundCover(Material material) {
        return switch (material) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, DANDELION,
                    POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET, RED_TULIP, ORANGE_TULIP,
                    WHITE_TULIP, PINK_TULIP, OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY,
                    WITHER_ROSE, SNOW, VINE, GLOW_LICHEN, MOSS_CARPET -> true;
            default -> false;
        };
    }

    public int build(Location center) {
        return build(center, null);
    }

    public int build(Location center, Player actor) {
        if (center == null || center.getWorld() == null) {
            throw new IllegalArgumentException("Arena center and world must be available");
        }
        Inspection inspection = inspect(center, actor);
        if (!inspection.clear()) {
            throw new IllegalStateException(inspection.problem() + " První překážka: " + inspection.firstObstruction());
        }
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int centerY = center.getBlockY();
        int floorY = centerY - 1;
        int changed = 0;

        // 97-block circular floor with seven concentric, contrasting rings.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > RADIUS * RADIUS) continue;
                double radius = Math.sqrt(d2);
                int x = cx + dx;
                int z = cz + dz;
                int groundY = world.getHighestBlockYAt(x, z);
                if (groundY > floorY) {
                    for (int y = floorY; y <= groundY; y++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (!isSafeNaturalCutBlock(block)) {
                            throw new IllegalStateException("Terén se po předkontrole změnil na "
                                    + x + " " + y + " " + z + "; další bloky se nepoložily.");
                        }
                        if (!block.getType().isAir()) changed += set(block, Material.AIR);
                    }
                } else {
                    for (int y = groundY + 1; y < floorY; y++) {
                        Material foundation = ((y + dx + dz) & 1) == 0
                                ? Material.POLISHED_BLACKSTONE : Material.DEEPSLATE_BRICKS;
                        changed += set(world.getBlockAt(x, y, z), foundation);
                    }
                }
                changed += set(world.getBlockAt(x, floorY, z), floorMaterial(dx, dz, radius));
            }
        }

        // Two-layer battlement with four five-block entrance gates.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                double radius = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(radius - RADIUS) > 0.72D) continue;
                boolean gate = (Math.abs(dx) <= 2 && Math.abs(dz) >= RADIUS - 1.4D)
                        || (Math.abs(dz) <= 2 && Math.abs(dx) >= RADIUS - 1.4D);
                if (gate) continue;
                int x = cx + dx;
                int z = cz + dz;
                changed += set(world.getBlockAt(x, centerY, z), Material.POLISHED_BLACKSTONE_BRICK_WALL);
                changed += set(world.getBlockAt(x, centerY + 1, z), Material.POLISHED_BLACKSTONE_BRICK_WALL);
                if (((dx + dz) & 1) == 0) {
                    changed += set(world.getBlockAt(x, centerY + 2, z), Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }

        // Eight 5x5 vampire-keep towers with a high obsidian crown and soul lantern.
        Point[] towers = {
                new Point(0, -39), new Point(28, -28), new Point(39, 0), new Point(28, 28),
                new Point(0, 39), new Point(-28, 28), new Point(-39, 0), new Point(-28, -28)
        };
        for (Point tower : towers) {
            int tx = cx + tower.x();
            int tz = cz + tower.z();
            changed += set(world.getBlockAt(tx, floorY, tz), Material.CRYING_OBSIDIAN);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy <= 12; dy++) {
                        boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        Material pillar;
                        if (dy == 3 || dy == 8 || dy == 11) {
                            pillar = Material.GILDED_BLACKSTONE;
                        } else if (dy >= 10 && !edge) {
                            pillar = Material.CRYING_OBSIDIAN;
                        } else {
                            pillar = edge ? Material.POLISHED_BLACKSTONE_BRICKS : Material.BLACKSTONE;
                        }
                        changed += set(world.getBlockAt(tx + dx, centerY + dy, tz + dz), pillar);
                    }
                }
            }
            // Gothic crown and beacon-like lantern at the tower top.
            changed += set(world.getBlockAt(tx, centerY + 13, tz), Material.NETHER_BRICK_FENCE);
            changed += set(world.getBlockAt(tx, centerY + 14, tz), Material.SOUL_LANTERN);
            changed += set(world.getBlockAt(tx + 1, centerY + 12, tz), Material.RED_NETHER_BRICKS);
            changed += set(world.getBlockAt(tx - 1, centerY + 12, tz), Material.RED_NETHER_BRICKS);
            changed += set(world.getBlockAt(tx, centerY + 12, tz + 1), Material.RED_NETHER_BRICKS);
            changed += set(world.getBlockAt(tx, centerY + 12, tz - 1), Material.RED_NETHER_BRICKS);
        }

        // Four monumental gatehouses just inside the entrances.
        Point[] gates = {new Point(0, -43), new Point(43, 0), new Point(0, 43), new Point(-43, 0)};
        for (Point gate : gates) {
            boolean alongZ = gate.x() == 0;
            for (int side : new int[]{-1, 1}) {
                int gx = cx + gate.x() + (alongZ ? side * 3 : 0);
                int gz = cz + gate.z() + (alongZ ? 0 : side * 3);
                for (int dy = 0; dy <= 7; dy++) {
                    changed += set(world.getBlockAt(gx, centerY + dy, gz),
                            dy == 3 || dy == 6 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
                changed += set(world.getBlockAt(gx, centerY + 8, gz), Material.CRYING_OBSIDIAN);
                changed += set(world.getBlockAt(gx, centerY + 9, gz), Material.SOUL_LANTERN);
            }
            for (int along = -3; along <= 3; along++) {
                int x = cx + gate.x() + (alongZ ? along : 0);
                int z = cz + gate.z() + (alongZ ? 0 : along);
                changed += set(world.getBlockAt(x, centerY + 7, z), Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }

        // Eight inner rune obelisks make the center feel like a summoning ritual.
        Point[] obelisks = {
                new Point(0, -26), new Point(18, -18), new Point(26, 0), new Point(18, 18),
                new Point(0, 26), new Point(-18, 18), new Point(-26, 0), new Point(-18, -18)
        };
        for (Point point : obelisks) {
            int ox = cx + point.x();
            int oz = cz + point.z();
            changed += set(world.getBlockAt(ox, floorY, oz), Material.CRYING_OBSIDIAN);
            for (int dy = 0; dy <= 5; dy++) {
                Material body = (dy == 2 || dy == 4)
                        ? Material.GILDED_BLACKSTONE
                        : Material.RED_NETHER_BRICKS;
                changed += set(world.getBlockAt(ox, centerY + dy, oz), body);
            }
            changed += set(world.getBlockAt(ox, centerY + 6, oz), Material.SOUL_LANTERN);
            changed += set(world.getBlockAt(ox + 1, centerY, oz), Material.POLISHED_BLACKSTONE_BRICKS);
            changed += set(world.getBlockAt(ox - 1, centerY, oz), Material.POLISHED_BLACKSTONE_BRICKS);
            changed += set(world.getBlockAt(ox, centerY, oz + 1), Material.POLISHED_BLACKSTONE_BRICKS);
            changed += set(world.getBlockAt(ox, centerY, oz - 1), Material.POLISHED_BLACKSTONE_BRICKS);
        }

        // Four cardinal rune pylons inside the clear 13-block boss spawn circle.
        int[][] sigils = {{0, -12}, {12, 0}, {0, 12}, {-12, 0}};
        for (int[] sigil : sigils) {
            int x = cx + sigil[0];
            int z = cz + sigil[1];
            changed += set(world.getBlockAt(x, floorY, z), Material.CRYING_OBSIDIAN);
            changed += set(world.getBlockAt(x, centerY, z), Material.POLISHED_BLACKSTONE_BRICKS);
            changed += set(world.getBlockAt(x, centerY + 1, z), Material.POLISHED_BLACKSTONE_BRICKS);
            changed += set(world.getBlockAt(x, centerY + 2, z), Material.GILDED_BLACKSTONE);
        }
        return changed;
    }

    private Material floorMaterial(int dx, int dz, double radius) {
        if (isRing(radius, 7.0D) || isRing(radius, 15.0D) || isRing(radius, 23.0D)
                || isRing(radius, 31.0D) || isRing(radius, 39.0D) || isRing(radius, 46.0D)) {
            return Material.CRYING_OBSIDIAN;
        }
        boolean runeRay = radius >= 7.0D && radius <= 36.0D
                && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz));
        if (runeRay) return Material.RED_NETHER_BRICKS;
        if (radius <= 7.0D) return Material.POLISHED_BLACKSTONE_BRICKS;
        if (radius <= 16.0D) return Material.DEEPSLATE_TILES;
        if (radius <= 31.0D) return Material.POLISHED_BLACKSTONE_BRICKS;
        if (radius <= 40.0D) return Material.DEEPSLATE_BRICKS;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private boolean isRing(double radius, double targetRadius) {
        return Math.abs(radius - targetRadius) <= 0.48D;
    }

    private int set(Block block, Material material) {
        if (block.getType() == material) return 0;
        block.setType(material, false);
        return 1;
    }
}
