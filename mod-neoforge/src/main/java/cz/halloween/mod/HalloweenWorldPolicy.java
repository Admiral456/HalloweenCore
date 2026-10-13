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
        return HalloweenWorldIdPolicy.isReservedWorldIdentifier(value);
    }

    public static boolean isConfiguredWorldAllowed(String dimensionId, List<? extends String> allowedIds) {
        return HalloweenWorldIdPolicy.isConfiguredWorldAllowed(dimensionId, allowedIds);
    }

    public static boolean isAllowed(Level level) {
        if (level == null || level.isClientSide()) return false;
        ResourceLocation dimension = level.dimension().location();
        if (isReservedWorldIdentifier(dimension.toString()) || isReservedWorldIdentifier(dimension.getPath())) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel
                && isReservedWorldIdentifier(serverLevel.getServer().getWorldData().getLevelName())) {
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
