package cz.halloween.core;

import java.util.List;

/**
 * Pure world-name policy used by live gameplay guards and automatic generation.
 * The reserved lobby world can never be opted in through configuration.
 */
public final class HalloweenWorldPolicy {
    private static final String RESERVED_WORLD = "Spawn";

    private HalloweenWorldPolicy() {}

    public static boolean isReservedWorld(String worldName) {
        return worldName != null && RESERVED_WORLD.equalsIgnoreCase(worldName.trim());
    }

    public static boolean isExplicitlyAllowedForAutomaticGeneration(
            String worldName, List<String> allowedWorlds, boolean gameplayWorldAllowed) {
        if (isReservedWorld(worldName) || !gameplayWorldAllowed || worldName == null
                || allowedWorlds == null || allowedWorlds.isEmpty()) return false;
        return allowedWorlds.stream().filter(name -> name != null)
                .anyMatch(name -> name.trim().equalsIgnoreCase(worldName.trim()));
    }
}
