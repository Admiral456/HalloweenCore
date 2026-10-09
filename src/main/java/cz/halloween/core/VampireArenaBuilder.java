package cz.halloween.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Builds a large gothic raid arena (97-block diameter) after a separate,
 * read-only preflight and explicit admin confirmation. Never clears overhead
 * obstructions; the selected site must be empty and chunks must already be loaded.
 */
public final class VampireArenaBuilder {
    public static final int RADIUS = 48;
    private static final int CLEARANCE_HEIGHT = 20;

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
                            "Některé chunky obří arény nejsou načtené. Zvyš dohled a stůj uprostřed lokace.",
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
                int groundY = world.getHighestBlockYAt(x, z);
                if (groundY < floorY - 6) {
                    obstructionCount++;
                    if (first.isEmpty()) {
                        first = x + " " + groundY + " " + z + " (terrain is over 6 blocks below arena floor)";
                    }
                }
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
            return new Inspection(false, "Nad podlahou obří arény musí být volných 20 bloků a terén nesmí být o více než 6 bloků níž.",
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

        // 97-block circular floor with seven concentric, contrasting rings.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > RADIUS * RADIUS) continue;
                double radius = Math.sqrt(d2);
                int x = cx + dx;
                int z = cz + dz;
                int groundY = world.getHighestBlockYAt(x, z);
                // Fill shallow low spots before laying the single flat arena floor.
                // Inspection already rejects holes deeper than six blocks.
                for (int y = groundY + 1; y < floorY; y++) {
                    Material foundation = ((y + dx + dz) & 1) == 0
                            ? Material.POLISHED_BLACKSTONE : Material.DEEPSLATE_BRICKS;
                    changed += set(world.getBlockAt(x, y, z), foundation);
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
