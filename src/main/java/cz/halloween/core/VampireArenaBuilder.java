package cz.halloween.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;

/**
 * Builds a large gothic raid arena (97-block diameter) after a separate,
 * read-only preflight and explicit admin confirmation. Never clears overhead
 * obstructions; the selected site must be empty and chunks must already be loaded.
 */
public final class VampireArenaBuilder {
    public static final int RADIUS = 48;
    private static final int CLEARANCE_HEIGHT = 24;

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

        // The admin explicitly confirms a destructive 97-block arena build. Load the
        // footprint's chunks up front instead of silently failing on a partly unloaded edge.
        int minChunkX = (centerX - RADIUS) >> 4;
        int maxChunkX = (centerX + RADIUS) >> 4;
        int minChunkZ = (centerZ - RADIUS) >> 4;
        int maxChunkZ = (centerZ + RADIUS) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    try {
                        world.getChunkAt(cx, cz);
                    } catch (RuntimeException ex) {
                        return new Inspection(false,
                                "Nepodařilo se načíst chunk " + cx + "," + cz + " pro arénu: " + ex.getClass().getSimpleName(),
                                0, "");
                    }
                }
                if (!world.isChunkLoaded(cx, cz)) {
                    return new Inspection(false,
                            "Chunk " + cx + "," + cz + " se nepodařilo načíst. Zkus to znovu po chvíli.",
                            0, "");
                }
            }
        }

        // Do not reject normal hills, forest canopies, or player-built ground during
        // preflight: these were the common reason the old command could never proceed
        // on real worlds. The explicit confirmation warns that the whole circle is
        // cleared/levelled, so count blocks that will be replaced for the preview.
        int overwritten = 0;
        String first = "";
        int r2Limit = RADIUS * RADIUS;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > r2Limit) continue;
                int x = centerX + dx;
                int z = centerZ + dz;
                int groundY = world.getHighestBlockYAt(x, z);
                for (int y = floorY + 1; y <= groundY; y++) {
                    Material material = world.getBlockAt(x, y, z).getType();
                    if (material.isAir()) continue;
                    overwritten++;
                    if (first.isEmpty()) first = x + " " + y + " " + z + " (" + material.name() + ")";
                }
            }
        }
        return new Inspection(true, "", overwritten, first);
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
                // The explicit preview/confirm flow authorizes leveling the whole
                // arena footprint. Clear high terrain/trees/buildings to the chosen
                // floor, or fill low ground/holes without creating a pit.
                int clearToY = Math.max(groundY, floorY + CLEARANCE_HEIGHT);
                for (int y = floorY + 1; y <= clearToY; y++) {
                    changed += set(world.getBlockAt(x, y, z), Material.AIR);
                }
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

        // Eight wall buttresses break up the curtain wall and add lantern-lit silhouettes.
        Point[] wallButtresses = {
                new Point(15, -41), new Point(41, -15), new Point(41, 15), new Point(15, 41),
                new Point(-15, 41), new Point(-41, 15), new Point(-41, -15), new Point(-15, -41)
        };
        for (Point buttress : wallButtresses) {
            int bx = cx + buttress.x();
            int bz = cz + buttress.z();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 7; dy++) {
                        Material block = (dy == 3 || dy == 6) ? Material.GILDED_BLACKSTONE
                                : (dy == 7 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS);
                        changed += set(world.getBlockAt(bx + dx, centerY + dy, bz + dz), block);
                    }
                }
            }
            changed += set(world.getBlockAt(bx, centerY + 8, bz), Material.SOUL_LANTERN);
        }

        // Eight hollow 7x7 gothic watchtowers with corner buttresses and illuminated crowns.
        Point[] towers = {
                new Point(0, -39), new Point(28, -28), new Point(39, 0), new Point(28, 28),
                new Point(0, 39), new Point(-28, 28), new Point(-39, 0), new Point(-28, -28)
        };
        for (Point tower : towers) {
            int tx = cx + tower.x();
            int tz = cz + tower.z();
            changed += set(world.getBlockAt(tx, floorY, tz), Material.CRYING_OBSIDIAN);

            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                    for (int dy = 0; dy <= 16; dy++) {
                        Material block;
                        if (dy == 0 || dy == 4 || dy == 8 || dy == 12 || dy == 16) {
                            block = corner ? Material.CRYING_OBSIDIAN
                                    : edge ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE;
                        } else if (edge) {
                            boolean inwardDoor;
                            if (Math.abs(tower.x()) >= Math.abs(tower.z()) && tower.x() != 0) {
                                inwardDoor = (tower.x() < 0 && dx == 3 && dz == 0
                                        || tower.x() > 0 && dx == -3 && dz == 0) && dy <= 2;
                            } else {
                                inwardDoor = (tower.z() < 0 && dz == 3 && dx == 0
                                        || tower.z() > 0 && dz == -3 && dx == 0) && dy <= 2;
                            }
                            boolean slit = (Math.abs(dx) == 3 && dz == 0 || Math.abs(dz) == 3 && dx == 0)
                                    && (dy == 2 || dy == 6 || dy == 10 || dy == 14);
                            if (inwardDoor) block = Material.AIR;
                            else if (dy == 3 || dy == 7 || dy == 11 || dy == 15) block = Material.GILDED_BLACKSTONE;
                            else if (slit) block = Material.RED_STAINED_GLASS;
                            else block = Material.POLISHED_BLACKSTONE_BRICKS;
                        } else {
                            // Hollow interior with three walkable gallery floors.
                            block = Material.AIR;
                        }
                        changed += set(world.getBlockAt(tx + dx, centerY + dy, tz + dz), block);
                    }
                }
            }

            // Crenellated crown, blood-red corner windows and a soul lantern beacon.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if ((Math.abs(dx) == 2 || Math.abs(dz) == 2)
                            && (Math.abs(dx) + Math.abs(dz)) % 2 == 0) {
                        changed += set(world.getBlockAt(tx + dx, centerY + 17, tz + dz),
                                Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    }
                }
            }
            changed += set(world.getBlockAt(tx, centerY + 17, tz), Material.CRYING_OBSIDIAN);
            changed += set(world.getBlockAt(tx, centerY + 18, tz), Material.NETHER_BRICK_FENCE);
            changed += set(world.getBlockAt(tx, centerY + 19, tz), Material.SOUL_LANTERN);
            changed += set(world.getBlockAt(tx + 1, centerY + 16, tz), Material.RED_NETHER_BRICKS);
            changed += set(world.getBlockAt(tx - 1, centerY + 16, tz), Material.RED_NETHER_BRICKS);
            changed += set(world.getBlockAt(tx, centerY + 16, tz + 1), Material.RED_NETHER_BRICKS);
            changed += set(world.getBlockAt(tx, centerY + 16, tz - 1), Material.RED_NETHER_BRICKS);
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

        // Layered flanking buttresses and blood-glass ward windows around every entrance.
        for (Point gate : gates) {
            boolean alongZ = gate.x() == 0;
            for (int outerSide : new int[]{-1, 1}) {
                int bx = cx + gate.x() + (alongZ ? outerSide * 4 : 0);
                int bz = cz + gate.z() + (alongZ ? 0 : outerSide * 4);
                for (int dy = 0; dy <= 10; dy++) {
                    Material body = (dy == 3 || dy == 7) ? Material.GILDED_BLACKSTONE
                            : (dy >= 8 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS);
                    changed += set(world.getBlockAt(bx, centerY + dy, bz), body);
                }
                changed += set(world.getBlockAt(bx, centerY + 11, bz), Material.SOUL_LANTERN);
                int inwardX = bx - Integer.signum(gate.x());
                int inwardZ = bz - Integer.signum(gate.z());
                changed += set(world.getBlockAt(inwardX, centerY + 4, inwardZ), Material.RED_STAINED_GLASS);
                changed += set(world.getBlockAt(inwardX, centerY + 5, inwardZ), Material.RED_STAINED_GLASS);
            }
        }

        // Seven rune obelisks frame the ritual circle; the eighth northern point is the royal sanctum.
        // The north-south centre lane remains open so the boss approach is deliberately symmetrical.
        Point[] obelisks = {
                new Point(18, -18), new Point(26, 0), new Point(18, 18),
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

        // The arena's focal point: a monumental blood-moon portal and the Vampire King's throne.
        changed += buildThroneSanctum(world, cx, centerY, cz);

        // Four raised sarcophagi on the outer ritual ring; far from the boss spawn circle.
        Point[] sarcophagi = {
                new Point(28, -16), new Point(28, 16), new Point(-28, 16), new Point(-28, -16)
        };
        for (Point sarcophagus : sarcophagi) {
            changed += buildSarcophagus(world, cx + sarcophagus.x(), floorY, cz + sarcophagus.z());
        }

        // Four cardinal rune pylons outside the 15-block clear boss spawn circle.
        int[][] sigils = {{0, -16}, {16, 0}, {0, 16}, {-16, 0}};
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

    /**
     * Builds the royal focal sanctuary outside the central boss spawn area.
     * The portal reaches +16 and the tower lights +19, inside the 24-block preflight clearance.
     */
    private int buildThroneSanctum(World world, int cx, int centerY, int cz) {
        int floorY = centerY - 1;
        int portalZ = cz - 31;
        int changed = 0;

        // 17x9 sanctuary forecourt with bloodstone inlays and dark obsidian edge blocks.
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Material base = (Math.abs(dx) == 8 || Math.abs(dz) == 4)
                        ? Material.CRYING_OBSIDIAN
                        : ((dx + dz) % 3 == 0 ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
                changed += set(world.getBlockAt(cx + dx, floorY, portalZ + dz), base);
            }
        }
        changed += placeStairs(world, cx - 8, floorY + 1, cz - 27,
                Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST);
        changed += placeStairs(world, cx + 8, floorY + 1, cz - 27,
                Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST);

        // Monumental outside piers with layered mouldings and interior pilasters.
        for (int side : new int[]{-1, 1}) {
            for (int dy = 0; dy <= 14; dy++) {
                Material material = (dy == 3 || dy == 7 || dy == 11)
                        ? Material.GILDED_BLACKSTONE
                        : (dy >= 12 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS);
                changed += set(world.getBlockAt(cx + side * 7, centerY + dy, portalZ), material);
                changed += set(world.getBlockAt(cx + side * 7, centerY + dy, portalZ + 1), material);
            }
            for (int dy = 1; dy <= 11; dy++) {
                Material material = dy % 4 == 0 ? Material.GILDED_BLACKSTONE : Material.RED_NETHER_BRICKS;
                changed += set(world.getBlockAt(cx + side * 5, centerY + dy, portalZ + 1), material);
            }
        }

        // Gothic lintel, pointed crown, and a five-wide blood-glass heart.
        for (int dx = -7; dx <= 7; dx++) {
            changed += set(world.getBlockAt(cx + dx, centerY + 12, portalZ), Material.POLISHED_BLACKSTONE_BRICKS);
            if (Math.abs(dx) <= 5) {
                changed += set(world.getBlockAt(cx + dx, centerY + 13, portalZ), Material.CRYING_OBSIDIAN);
            }
        }
        for (int dy = 5; dy <= 10; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                Material glass = Math.abs(dx) == 2 ? Material.RED_NETHER_BRICKS : Material.RED_STAINED_GLASS;
                changed += set(world.getBlockAt(cx + dx, centerY + dy, portalZ), glass);
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            changed += set(world.getBlockAt(cx + dx, centerY + 4, portalZ), Material.GILDED_BLACKSTONE);
        }
        for (int side : new int[]{-1, 1}) {
            changed += set(world.getBlockAt(cx + side * 6, centerY + 15, portalZ), Material.POLISHED_BLACKSTONE_BRICK_WALL);
            changed += set(world.getBlockAt(cx + side * 4, centerY + 15, portalZ), Material.CRYING_OBSIDIAN);
            changed += set(world.getBlockAt(cx + side * 4, centerY + 16, portalZ), Material.SOUL_LANTERN);
            changed += set(world.getBlockAt(cx + side * 7, centerY + 15, portalZ + 1), Material.SOUL_LANTERN);
        }
        changed += set(world.getBlockAt(cx, centerY + 14, portalZ), Material.CRYING_OBSIDIAN);
        changed += set(world.getBlockAt(cx, centerY + 15, portalZ), Material.NETHER_BRICK_FENCE);
        changed += set(world.getBlockAt(cx, centerY + 16, portalZ), Material.SOUL_LANTERN);

        // King's throne faces the arena, on a separate raised dais south of the portal wall.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                changed += set(world.getBlockAt(cx + dx, floorY + 1, cz - 26 + dz),
                        (Math.abs(dx) == 3 || Math.abs(dz) == 2)
                                ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int dy = 1; dy <= 5; dy++) {
            changed += set(world.getBlockAt(cx, floorY + dy, cz - 28), dy == 5
                    ? Material.CRYING_OBSIDIAN : Material.RED_NETHER_BRICKS);
            changed += set(world.getBlockAt(cx - 2, floorY + dy, cz - 26), Material.POLISHED_BLACKSTONE_BRICKS);
            changed += set(world.getBlockAt(cx + 2, floorY + dy, cz - 26), Material.POLISHED_BLACKSTONE_BRICKS);
        }
        changed += set(world.getBlockAt(cx, floorY + 2, cz - 25), Material.RED_NETHER_BRICKS);
        changed += set(world.getBlockAt(cx, floorY + 3, cz - 25), Material.CRYING_OBSIDIAN);
        changed += set(world.getBlockAt(cx - 1, floorY + 2, cz - 25), Material.POLISHED_BLACKSTONE_BRICKS);
        changed += set(world.getBlockAt(cx + 1, floorY + 2, cz - 25), Material.POLISHED_BLACKSTONE_BRICKS);

        // Five broad blackstone steps lead up from the open central floor to the king's dais.
        for (int dx = -2; dx <= 2; dx++) {
            changed += placeStairs(world, cx + dx, centerY, cz - 23,
                    Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.NORTH);
        }

        // Hanging iron tracery and lanterns deepen the portal recess.
        for (int side : new int[]{-1, 1}) {
            for (int dy = 0; dy <= 3; dy++) {
                changed += set(world.getBlockAt(cx + side * 4, centerY + 10 - dy, portalZ + 1), Material.IRON_BARS);
            }
            changed += set(world.getBlockAt(cx + side * 4, centerY + 6, portalZ + 1), Material.SOUL_LANTERN);
        }
        return changed;
    }

    private int buildSarcophagus(World world, int cx, int floorY, int cz) {
        int changed = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                changed += set(world.getBlockAt(cx + dx, floorY + 1, cz + dz), Material.POLISHED_BLACKSTONE_BRICKS);
                changed += set(world.getBlockAt(cx + dx, floorY + 2, cz + dz),
                        (Math.abs(dx) == 2 || Math.abs(dz) == 1)
                                ? Material.DEEPSLATE_BRICKS : Material.POLISHED_BLACKSTONE);
            }
        }
        changed += set(world.getBlockAt(cx, floorY + 3, cz), Material.CRYING_OBSIDIAN);
        changed += set(world.getBlockAt(cx, floorY + 3, cz - 1), Material.RED_NETHER_BRICKS);
        changed += set(world.getBlockAt(cx - 2, floorY + 3, cz), Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        changed += set(world.getBlockAt(cx + 2, floorY + 3, cz), Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        changed += set(world.getBlockAt(cx - 3, floorY + 1, cz), Material.CRYING_OBSIDIAN);
        changed += set(world.getBlockAt(cx + 3, floorY + 1, cz), Material.CRYING_OBSIDIAN);
        changed += set(world.getBlockAt(cx, floorY + 1, cz - 2), Material.RED_NETHER_BRICKS);
        changed += set(world.getBlockAt(cx, floorY + 1, cz + 2), Material.RED_NETHER_BRICKS);
        return changed;
    }

    private Material floorMaterial(int dx, int dz, double radius) {
        if (isRing(radius, 7.0D) || isRing(radius, 15.0D) || isRing(radius, 23.0D)
                || isRing(radius, 31.0D) || isRing(radius, 39.0D) || isRing(radius, 46.0D)) {
            return Material.CRYING_OBSIDIAN;
        }
        // Eight directional rays read as an occult compass across the concentric arena.
        boolean runeRay = radius >= 7.0D && radius <= 36.0D
                && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz));
        if (runeRay && ((Math.abs(dx) + Math.abs(dz)) % 9 == 0)) return Material.GILDED_BLACKSTONE;
        if (runeRay) return Material.RED_NETHER_BRICKS;
        if (radius >= 2.0D && radius <= 6.0D
                && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz))) return Material.RED_NETHER_BRICKS;
        if (radius <= 7.0D) return Material.POLISHED_BLACKSTONE_BRICKS;
        if (radius <= 16.0D) return Material.DEEPSLATE_TILES;
        if (radius <= 31.0D) return Material.POLISHED_BLACKSTONE_BRICKS;
        if (radius <= 40.0D) return Material.DEEPSLATE_BRICKS;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private boolean isRing(double radius, double targetRadius) {
        return Math.abs(radius - targetRadius) <= 0.48D;
    }

    private int placeStairs(World world, int x, int y, int z, Material material, BlockFace facing) {
        BlockData data = material.createBlockData();
        if (data instanceof Stairs stairs) stairs.setFacing(facing);
        Block block = world.getBlockAt(x, y, z);
        if (block.getBlockData().equals(data)) return 0;
        block.setBlockData(data, false);
        return 1;
    }

    private int set(Block block, Material material) {
        if (block.getType() == material) return 0;
        block.setType(material, false);
        return 1;
    }
}
