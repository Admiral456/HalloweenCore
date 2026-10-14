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

            Method applicableMethod = null;
            for (Method method : query.getClass().getMethods()) {
                if (method.getName().equals("getApplicableRegions")
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(adaptedLocation)) {
                    applicableMethod = method;
                    break;
                }
            }
            if (applicableMethod == null) throw new NoSuchMethodException("RegionQuery.getApplicableRegions");
            Object applicable = applicableMethod.invoke(query, adaptedLocation);

            Method regionsMethod = null;
            for (Method method : applicable.getClass().getMethods()) {
                if (method.getName().equals("getRegions") && method.getParameterCount() == 0) {
                    regionsMethod = method;
                    break;
                }
            }
            if (regionsMethod == null) throw new NoSuchMethodException("ApplicableRegionSet.getRegions");
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
                String id = String.valueOf(region.getClass().getMethod("getId").invoke(region));
                if (!"__global__".equalsIgnoreCase(id)) return true;

                if (buildFlag != null) {
                    for (Method method : region.getClass().getMethods()) {
                        if (!method.getName().equals("getFlag") || method.getParameterCount() != 1
                                || !method.getParameterTypes()[0].isInstance(buildFlag)) continue;
                        Object state = method.invoke(region, buildFlag);
                        if (state != null && "DENY".equalsIgnoreCase(state.toString())) return true;
                        break;
                    }
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
