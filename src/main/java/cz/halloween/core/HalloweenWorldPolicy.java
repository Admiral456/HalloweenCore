package cz.halloween.core;

import java.util.Collection;
import java.util.Locale;

/**
 * Pure world-name policy shared by gameplay and all automatic terrain changes.
 * Kept independent of Bukkit so the safety rules can be unit-tested directly.
 */
public final class HalloweenWorldPolicy {
    private static final String PROTECTED_WORLD = "spawn";

    private HalloweenWorldPolicy() {
    }

    public static boolean isGameplayWorldEligible(String worldName,
                                                   Collection<String> configuredNames,
                                                   String mode) {
        if (isBlank(worldName) || isProtectedWorld(worldName)) return false;
        boolean listed = containsIgnoreCase(configuredNames, worldName);
        return "WHITELIST".equalsIgnoreCase(mode == null ? "" : mode.trim()) ? listed : !listed;
    }

    /**
     * Automatic generation uses an allow-list and fails closed if it is empty.
     * Manual admin structure commands do not call this function.
     */
    public static boolean isAutomaticWorldEligible(String worldName, Collection<String> allowedNames) {
        if (isBlank(worldName) || isProtectedWorld(worldName)) return false;
        return allowedNames != null && !allowedNames.isEmpty() && containsIgnoreCase(allowedNames, worldName);
    }

    private static boolean isProtectedWorld(String worldName) {
        return PROTECTED_WORLD.equals(worldName.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean containsIgnoreCase(Collection<String> names, String worldName) {
        if (names == null || names.isEmpty()) return false;
        String expected = worldName.trim();
        return names.stream().filter(name -> !isBlank(name))
                .anyMatch(name -> name.trim().equalsIgnoreCase(expected));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
