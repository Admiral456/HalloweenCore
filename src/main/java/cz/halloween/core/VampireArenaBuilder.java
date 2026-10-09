package cz.halloween.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class VampireArenaBuilder {
    public static final int RADIUS = 22;
    private static final int CLEARANCE_HEIGHT = 9;

    public record Inspection(boolean clear, String problem, int obstructions, String firstObstruction) {}
    private record Point(int x, int z) {}

    public Inspection inspect(Location center) {
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

        int minChunkX = (centerX - RADIUS) >> 4;
        int maxChunkX = (centerX + RADIUS) >> 4;
        int minChunkZ = (centerZ - RADIUS) >> 4;
        int maxChunkZ = (centerZ + RADIUS) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return new Inspection(false,
                            "Některé chunky arény nejsou načtené. Přijď blíž, zvyš dohled nebo oblast načti.",
                            0, "");
                }
            }
        }

        int obstructionCount = 0;
        String first = "";
        int r2Limit = RADIUS * RADIUS;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > r2Limit) continue;
                int x = centerX + dx;
                int z = centerZ + dz;
                for (int y = centerY; y < centerY + CLEARANCE_HEIGHT; y++) {
                    Material material = world.getBlockAt(x, y, z).getType();
                    if (!material.isAir()) {
                        obstructionCount++;
                        if (first.isEmpty()) {
                            first = x + " " + y + " " + z + " (" + material.name() + ")";
                        }
                    }
                }
            }
        }
        if (obstructionCount > 0) {
            return new Inspection(false, "Nad budoucí podlahou nejsou volné všechny prostory.",
                    obstructionCount, first);
        }
        return new Inspection(true, "", 0, "");
    }

    public int build(Location center) {
        if (center == null || center.getWorld() == null) {
            throw new IllegalArgumentException("Arena center and world must be available");
        }
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int centerY = center.getBlockY();
        int floorY = centerY - 1;
        int changed = 0;

        // The floor's top layer replaces only the surface layer after the explicit command confirmation.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > RADIUS * RADIUS) continue;
                double r = Math.sqrt(d2);
                Material floor = floorMaterial(dx, dz, r);
                changed += set(world.getBlockAt(cx + dx, floorY, cz + dz), floor);
            }
        }

        // Perimeter battlement with clear, cardinal entrances.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(r - RADIUS) > 0.65D) continue;
                boolean gate = (Math.abs(dx) <= 1 && Math.abs(dz) >= RADIUS - 1)
                        || (Math.abs(dz) <= 1 && Math.abs(dx) >= RADIUS - 1);
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

        // Eight gothic towers around the playable floor, each capped with a soul lantern.
        Point[] towers = {
                new Point(0, -18), new Point(13, -13), new Point(18, 0), new Point(13, 13),
                new Point(0, 18), new Point(-13, 13), new Point(-18, 0), new Point(-13, -13)
        };
        for (Point point : towers) {
            int tx = cx + point.x();
            int tz = cz + point.z();
            changed += set(world.getBlockAt(tx, floorY, tz), Material.CRYING_OBSIDIAN);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 6; dy++) {
                        Material pillar = dy == 2 || dy == 5
                                ? Material.GILDED_BLACKSTONE
                                : ((Math.abs(dx) == 1 || Math.abs(dz) == 1)
                                    ? Material.POLISHED_BLACKSTONE_BRICKS : Material.BLACKSTONE);
                        changed += set(world.getBlockAt(tx + dx, centerY + dy, tz + dz), pillar);
                    }
                }
            }
            changed += set(world.getBlockAt(tx, centerY + 7, tz), Material.NETHER_BRICK_FENCE);
            changed += set(world.getBlockAt(tx, centerY + 8, tz), Material.SOUL_LANTERN);
        }

        // Four small cardinal rune pillars frame the entrance without obstructing the boss spawn.
        int[][] sigils = {{0, -9}, {9, 0}, {0, 9}, {-9, 0}};
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
        if (isRing(radius, 4.0D) || isRing(radius, 8.0D) || isRing(radius, 14.0D) || isRing(radius, 20.0D)) {
            return Material.CRYING_OBSIDIAN;
        }
        boolean runeRay = radius >= 4.0D && radius <= 17.0D
                && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz));
        if (runeRay) return Material.RED_NETHER_BRICKS;
        if (radius <= 4.0D) return Material.POLISHED_BLACKSTONE_BRICKS;
        if (radius <= 9.0D) return Material.DEEPSLATE_TILES;
        if (radius <= 17.0D) return Material.POLISHED_BLACKSTONE_BRICKS;
        if (radius <= 20.0D) return Material.DEEPSLATE_BRICKS;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private boolean isRing(double radius, double targetRadius) {
        return Math.abs(radius - targetRadius) <= 0.45D;
    }

    private int set(Block block, Material material) {
        if (block.getType() == material) return 0;
        block.setType(material, false);
        return 1;
    }
}
