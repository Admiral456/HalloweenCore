package cz.halloween.relics;

import cz.halloween.core.HalloweenRelicAbilityEngine;
import org.bukkit.plugin.java.JavaPlugin;

public final class HalloweenPortableRelics extends JavaPlugin {
    private HalloweenRelicAbilityEngine engine;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        engine = new HalloweenRelicAbilityEngine(this);
        engine.start();
        var itemsAdder = getServer().getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null) {
            getLogger().warning("ItemsAdder was not detected; install ItemsAdder 4.0.18+ and apply the resource pack for custom relic IDs and visuals.");
        } else if (compareVersions(itemsAdder.getDescription().getVersion(), "4.0.18") < 0) {
            getLogger().warning("Batwing Elytra requires ItemsAdder 4.0.18+; detected " + itemsAdder.getDescription().getVersion() + ".");
        }
    }

    @Override
    public void onDisable() {
        if (engine != null) engine.stop();
    }

    private int compareVersions(String actual, String minimum) {
        int[] a = versionParts(actual), b = versionParts(minimum);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? a[i] : 0, bv = i < b.length ? b[i] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private int[] versionParts(String value) {
        String[] parts = (value == null ? "0" : value).split("\\.");
        int[] result = new int[Math.min(3, parts.length)];
        for (int i = 0; i < result.length; i++) {
            String digits = parts[i].replaceAll("[^0-9].*$", "");
            try { result[i] = Integer.parseInt(digits.isEmpty() ? "0" : digits); }
            catch (NumberFormatException ignored) { result[i] = 0; }
        }
        return result;
    }
}
