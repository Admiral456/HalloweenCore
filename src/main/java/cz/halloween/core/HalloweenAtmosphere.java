package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class HalloweenAtmosphere {
    private final HalloweenCore plugin;

    public HalloweenAtmosphere(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("atmosphere.enabled", true)) return;

        long loopSeconds = Math.max(10L, plugin.getConfig().getLong("atmosphere.loop-seconds", 95L));
        long ticks = loopSeconds * 20L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, ticks);
    }

    public void onJoin(Player player) {
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("atmosphere.enabled", true)) return;
        play(player);

        if (plugin.getConfig().getBoolean("atmosphere.send-title-on-join", true)) {
            String title = plugin.color(plugin.getConfig().getString("atmosphere.title", "&6&lHALLOWEEN 2026"));
            String subtitle = plugin.color(plugin.getConfig().getString("atmosphere.subtitle", "&7Něco se probudilo..."));
            player.sendTitle(title, subtitle, 10, 50, 20);
        }
    }

    private void tick() {
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("atmosphere.enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            play(player);
        }
    }

    private void play(Player player) {
        String custom = plugin.getConfig().getString("atmosphere.sound", "");
        String fallback = plugin.getConfig().getString("atmosphere.fallback-sound", "minecraft:music_disc.11");
        float volume = (float) Math.max(0.0D, plugin.getConfig().getDouble("atmosphere.volume", 0.35D));
        float pitch = (float) Math.max(0.1D, plugin.getConfig().getDouble("atmosphere.pitch", 1.0D));

        if (custom != null && !custom.isBlank() && hasItemsAdder()) {
            player.playSound(player.getLocation(), custom, volume, pitch);
            return;
        }

        if (fallback != null && !fallback.isBlank()) {
            player.playSound(player.getLocation(), fallback, volume, pitch);
        }
    }

    private boolean hasItemsAdder() {
        return plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null;
    }
}
