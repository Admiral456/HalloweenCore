package cz.halloween.mod;

import java.util.List;
import java.util.Locale;

/**
 * Pure Java decision logic for the world allow-list.
 * Kept independent from Minecraft classes so the fail-closed rules can be unit tested
 * without launching the game runtime.
 */
public final class HalloweenWorldIdPolicy {
    private HalloweenWorldIdPolicy() {}

    public static boolean isReservedWorldIdentifier(String value) {
        if (value == null || value.isBlank()) return true;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("spawn")) return true;
        int colon = normalized.indexOf(':');
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;
        return path.equals("spawn") || path.startsWith("spawn/");
    }

    public static boolean isConfiguredWorldAllowed(String dimensionId, List<? extends String> allowedIds) {
        if (isReservedWorldIdentifier(dimensionId) || allowedIds == null || allowedIds.isEmpty()) return false;
        String candidate = dimensionId.trim().toLowerCase(Locale.ROOT);
        return allowedIds.stream()
                .filter(value -> value != null)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> !isReservedWorldIdentifier(value) && value.equals(candidate));
    }
}
