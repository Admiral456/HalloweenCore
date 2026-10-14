package cz.halloween.core;

/** Pure coordinate rules used by the structure planner and verified by unit tests. */
public final class HalloweenStructureGeometry {
    private HalloweenStructureGeometry() {
    }

    public record Offset(int x, int z) {
    }

    public static Offset rotate(int rotation, int x, int z) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new Offset(-z, x);
            case 2 -> new Offset(-x, -z);
            case 3 -> new Offset(z, -x);
            default -> new Offset(x, z);
        };
    }

    public static boolean insideFootprint(int x, int z, int radius, boolean circular) {
        if (radius < 0) return false;
        return circular ? (long) x * x + (long) z * z <= (long) radius * radius
                : Math.abs(x) <= radius && Math.abs(z) <= radius;
    }

    public static boolean footprintFitsChunk(int chunkX, int chunkZ,
                                             int centerX, int centerZ, int radius) {
        if (radius < 0) return false;
        return ((centerX - radius) >> 4) == chunkX
                && ((centerX + radius) >> 4) == chunkX
                && ((centerZ - radius) >> 4) == chunkZ
                && ((centerZ + radius) >> 4) == chunkZ;
    }
}
