package cz.halloween.core;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Optional, reflection-only WorldGuard protection check. HalloweenCore does not
 * require WorldGuard to start. If WorldGuard is enabled but its API cannot be
 * queried, automatic edits fail closed; intentional administrator FORCE builds
 * do not use this helper.
 */
public final class HalloweenRegionProtection {
    private final HalloweenCore plugin;
    private boolean warnedApiUnavailable;

    public HalloweenRegionProtection(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public boolean isProtected(Location location) {
        if (location == null || location.getWorld() == null) return true;
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) return false;

        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object singleton = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = singleton.getClass().getMethod("getPlatform").invoke(singleton);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adaptedLocation = adapterClass.getMethod("adapt", Location.class).invoke(null, location);

            Class<?> regionQueryClass = Class.forName(
                    "com.sk89q.worldguard.protection.regions.RegionQuery");
            Class<?> worldEditLocationClass = Class.forName(
                    "com.sk89q.worldedit.util.Location");
            Method applicableMethod = regionQueryClass.getMethod(
                    "getApplicableRegions", worldEditLocationClass);
            Object applicable = applicableMethod.invoke(query, adaptedLocation);

            Class<?> applicableSetClass = Class.forName(
                    "com.sk89q.worldguard.protection.regions.ApplicableRegionSet");
            Method regionsMethod = applicableSetClass.getMethod("getRegions");
            Object rawRegions = regionsMethod.invoke(applicable);
            if (!(rawRegions instanceof Iterable<?> regions)) {
                throw new IllegalStateException("WorldGuard applicable regions is not iterable");
            }

            Object buildFlag = null;
            try {
                Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
                buildFlag = flagsClass.getField("BUILD").get(null);
            } catch (ReflectiveOperationException ignored) {
                // Named regions remain blocked conservatively even if the optional build flag API differs.
            }

            for (Object region : regions) {
                Class<?> protectedRegionClass = Class.forName(
                        "com.sk89q.worldguard.protection.regions.ProtectedRegion");
                String id = String.valueOf(protectedRegionClass.getMethod("getId").invoke(region));
                if (!"__global__".equalsIgnoreCase(id)) return true;

                if (buildFlag != null) {
                    Class<?> flagClass = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
                    Method getFlag = protectedRegionClass.getMethod("getFlag", flagClass);
                    Object state = getFlag.invoke(region, buildFlag);
                    if (state != null && "DENY".equalsIgnoreCase(state.toString())) return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!warnedApiUnavailable) {
                warnedApiUnavailable = true;
                plugin.getLogger().warning("WorldGuard is present but its region API could not be safely checked; "
                        + "automatic Halloween terrain changes are blocked. Manual admin FORCE commands remain available. Cause: "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            return true;
        }
    }
}
