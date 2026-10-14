package cz.halloween.core;

import java.util.Locale;

/** Pure geometry and command normalization shared by structure planning and unit tests. */
final class HalloweenStructureGeometry {
    private HalloweenStructureGeometry() {}

    static int rotationFromYaw(float yaw) {
        return Math.floorMod(Math.round(yaw / 90.0f), 4);
    }

    static int rotateX(int dx, int dz, int rotation) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> -dz;
            case 2 -> -dx;
            case 3 -> dz;
            default -> dx;
        };
    }

    static int rotateZ(int dx, int dz, int rotation) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> dx;
            case 2 -> -dz;
            case 3 -> -dx;
            default -> dz;
        };
    }

    static boolean withinCircle(int dx, int dz, int radius) {
        return (long) dx * dx + (long) dz * dz <= (long) radius * radius;
    }

    static int variant(long worldSeed, int centerX, int centerZ) {
        return Math.floorMod(Long.hashCode(worldSeed ^ (((long) centerX) << 32)
                ^ (centerZ & 0xffffffffL)), 3);
    }

    static int radius(String type) {
        return switch (type) {
            case "cemetery" -> 10;
            case "village" -> 17;
            case "arena" -> 20;
            case "goblinarena" -> 22;
            case "vampirearena" -> 48;
            default -> throw new IllegalArgumentException("neznámý typ struktury: " + type);
        };
    }

    static int clearance(String type) {
        return switch (type) {
            case "cemetery" -> 14;
            case "village" -> 20;
            case "arena" -> 12;
            case "goblinarena" -> 13;
            case "vampirearena" -> 15;
            default -> throw new IllegalArgumentException("neznámý typ struktury: " + type);
        };
    }

    static String normalizeType(String requested) {
        if (requested == null) return null;
        return switch (requested.trim().toLowerCase(Locale.ROOT)) {
            case "hřbitov", "hrbitov", "cemetery", "graveyard" -> "cemetery";
            case "vesnice", "village", "haunted-village" -> "village";
            case "aréna", "arena" -> "arena";
            case "vampirearena", "vampire-arena", "vampire" -> "vampirearena";
            case "goblinarena", "goblin-arena", "goblin" -> "goblinarena";
            default -> null;
        };
    }
}
