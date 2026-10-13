package cz.halloween.mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

public final class HalloweenWorldPolicy {
    private HalloweenWorldPolicy() {}

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
        return allowedIds.stream().filter(value -> value != null)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> !isReservedWorldIdentifier(value)
                        && value.equals(dimensionId.trim().toLowerCase(Locale.ROOT)));
    }

    public static boolean isAllowed(Level level) {
        if (level == null || level.isClientSide()) return false;
        ResourceLocation dimension = level.dimension().location();
        if (isReservedWorldIdentifier(dimension.toString()) || isReservedWorldIdentifier(dimension.getPath())) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel
                && isReservedWorldIdentifier(serverLevel.getLevelData().getLevelName())) {
            return false;
        }
        return isConfiguredWorldAllowed(dimension.toString(), HalloweenConfig.ALLOWED_WORLD_IDS.get());
    }

    public static boolean isAllowedGeneration(WorldGenLevel level) {
        if (level == null) return false;
        // Identify the actual world-save level whenever generation runs inside a region.
        // Fail closed if this NeoForge implementation cannot provide that identity.
        if (level instanceof ServerLevel serverLevel) return isAllowed(serverLevel);
        if (level instanceof WorldGenRegion region) {
            ServerLevel serverLevel = region.getLevel();
            return serverLevel != null && isAllowed(serverLevel);
        }
        return false;
    }

    public static boolean isAllowedDimension(ResourceLocation dimension) {
        if (dimension == null || isReservedWorldIdentifier(dimension.toString())
                || isReservedWorldIdentifier(dimension.getPath())) return false;
        return isConfiguredWorldAllowed(dimension.toString(), HalloweenConfig.ALLOWED_WORLD_IDS.get());
    }
}
