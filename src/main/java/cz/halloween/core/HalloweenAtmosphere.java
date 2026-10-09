package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HalloweenAtmosphere {
    private final HalloweenCore plugin;
    private final Map<UUID, BukkitTask> playbackTasks = new HashMap<>();

    public HalloweenAtmosphere(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refreshPlayback();
    }

    /**
     * Start this player's ambience loop immediately and repeat it on a timer
     * tied to the audio length. Per-player timers prevent a join-time play from
     * overlapping the server-wide timer.
     */
    public void onJoin(Player player) {
        if (isAtmosphereEnabled()) {
            startLoop(player);
        }

        if (plugin.isEventEnabled()
                && plugin.getConfig().getBoolean("atmosphere.send-title-on-join", true)) {
            String title = plugin.color(plugin.getConfig().getString("atmosphere.title", "&6&lHALLOWEEN 2026"));
            String subtitle = plugin.color(plugin.getConfig().getString("atmosphere.subtitle", "&7Něco se probudilo..."));
            player.sendTitle(title, subtitle, 10, 50, 20);
        }
    }

    /**
     * Restart loops after config reload / event re-enable so the new interval
     * and sound IDs are applied without waiting for the next old timer tick.
     */
    public void refreshPlayback() {
        stopPlayback();
        if (!isAtmosphereEnabled()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            startLoop(player);
        }
    }

    public void stop(Player player) {
        if (player == null) return;
        BukkitTask task = playbackTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        stopConfiguredSounds(player);
    }

    public void stopPlayback() {
        for (BukkitTask task : playbackTasks.values()) {
            task.cancel();
        }
        playbackTasks.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            stopConfiguredSounds(player);
        }
    }

    private void startLoop(Player player) {
        if (player == null || !player.isOnline() || !isAtmosphereEnabled()) return;

        BukkitTask oldTask = playbackTasks.remove(player.getUniqueId());
        if (oldTask != null) oldTask.cancel();

        stopConfiguredSounds(player);
        play(player);

        // Milliseconds allow a 2:07 soundtrack to repeat at its actual length.
        // Preserve the older seconds option as a backwards-compatible fallback.
        long fallbackMillis = Math.max(10L, plugin.getConfig().getLong("atmosphere.loop-seconds", 76L)) * 1000L;
        long loopMillis = Math.max(10_000L, plugin.getConfig().getLong("atmosphere.loop-milliseconds", fallbackMillis));
        long periodTicks = Math.max(1L, Math.round(loopMillis / 50.0D));
        UUID playerId = player.getUniqueId();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline() || !isAtmosphereEnabled()) {
                BukkitTask current = playbackTasks.remove(playerId);
                if (current != null) current.cancel();
                if (online != null) stopConfiguredSounds(online);
                return;
            }

            // Stop the previous stream before restarting at the configured
            // boundary, preventing overlap if the server tick is delayed.
            stopConfiguredSounds(online);
            play(online);
        }, periodTicks, periodTicks);
        playbackTasks.put(playerId, task);
    }

    private boolean isAtmosphereEnabled() {
        return plugin.isEventEnabled()
                && plugin.getConfig().getBoolean("atmosphere.enabled", true);
    }

    private void stopConfiguredSounds(Player player) {
        String custom = plugin.getConfig().getString("atmosphere.sound", "");
        String fallback = plugin.getConfig().getString("atmosphere.fallback-sound", "");
        stopSound(player, custom);
        stopSound(player, fallback);
    }

    private void stopSound(Player player, String sound) {
        if (sound == null || sound.isBlank()) return;
        try {
            player.stopSound(sound);
        } catch (Exception ignored) {
            // Sound may no longer be registered after a pack/config reload.
        }
    }

    private void play(Player player) {
        String custom = plugin.getConfig().getString("atmosphere.sound", "");
        String fallback = plugin.getConfig().getString("atmosphere.fallback-sound", "");
        float volume = (float) Math.max(0.0D, Math.min(1.0D,
                plugin.getConfig().getDouble("atmosphere.volume", 1.0D)));
        float pitch = (float) Math.max(0.1D, plugin.getConfig().getDouble("atmosphere.pitch", 1.0D));

        // Stop anything already playing in the Music slider category before starting
        // the Halloween theme. The pack also maps vanilla music events to a silent OGG,
        // so normal biome/menu tracks don't start again while this pack is installed.
        try {
            player.stopSound(SoundCategory.MUSIC);
        } catch (Exception ignored) {
            // Older server API: fall back to stopping the configured sound IDs below.
        }

        if (custom != null && !custom.isBlank() && hasItemsAdder()) {
            try {
                player.playSound(player, custom, SoundCategory.MUSIC, volume, pitch);
                return;
            } catch (Exception ignored) {
                // Invalid/missing custom sound: continue to the configured fallback.
            }
        }

        if (fallback != null && !fallback.isBlank()) {
            try {
                player.playSound(player, fallback, SoundCategory.MUSIC, volume, pitch);
            } catch (Exception ignored) {
                // No valid sound has been configured.
            }
        }
    }

    private boolean hasItemsAdder() {
        var itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        return itemsAdder != null && itemsAdder.isEnabled();
    }
}
