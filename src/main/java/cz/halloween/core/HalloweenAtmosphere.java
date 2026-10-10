package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class HalloweenAtmosphere {
    private final HalloweenCore plugin;
    private final Map<UUID, BukkitTask> playbackTasks = new HashMap<>();
    private final Map<UUID, String> activeTracks = new HashMap<>();
    private final Map<UUID, String> activeProfiles = new HashMap<>();
    private final Map<UUID, Long> nextTrackAt = new HashMap<>();
    private final Map<UUID, String> previousNightTracks = new HashMap<>();
    private final Set<String> reportedAudioIssues = new HashSet<>();
    private BukkitTask vanillaMusicMuteTask;

    public HalloweenAtmosphere(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refreshPlayback();
    }

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

    public void refreshPlayback() {
        stopPlayback();
        if (!isAtmosphereEnabled()) return;

        startVanillaMusicMute();
        for (Player player : Bukkit.getOnlinePlayers()) {
            startLoop(player);
        }
    }

    private void startVanillaMusicMute() {
        if (vanillaMusicMuteTask != null) {
            vanillaMusicMuteTask.cancel();
            vanillaMusicMuteTask = null;
        }
        // Vanilla background music is client-scheduled. Keep stopping MUSIC while the
        // Halloween event is enabled; the custom soundtrack uses AMBIENT instead.
        vanillaMusicMuteTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!isAtmosphereEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    player.stopSound(SoundCategory.MUSIC);
                } catch (Exception ignored) {
                    // A disconnect may race with this tick; continue with other players.
                }
            }
        }, 1L, 20L);
    }

    public void stop(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        BukkitTask task = playbackTasks.remove(id);
        if (task != null) task.cancel();
        stopConfiguredSounds(player);
        clearPlayerState(id);
    }

    public void stopPlayback() {
        if (vanillaMusicMuteTask != null) {
            vanillaMusicMuteTask.cancel();
            vanillaMusicMuteTask = null;
        }
        for (BukkitTask task : playbackTasks.values()) {
            task.cancel();
        }
        playbackTasks.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            stopConfiguredSounds(player);
        }
        activeTracks.clear();
        activeProfiles.clear();
        nextTrackAt.clear();
        previousNightTracks.clear();
    }

    private void startLoop(Player player) {
        if (player == null || !player.isOnline() || !isAtmosphereEnabled()) return;

        UUID playerId = player.getUniqueId();
        BukkitTask oldTask = playbackTasks.remove(playerId);
        if (oldTask != null) oldTask.cancel();
        stopConfiguredSounds(player);
        clearPlayerState(playerId);

        if (isPlaylistEnabled()) {
            updatePlaylistPlayback(player, true);
            long periodTicks = Math.max(20L,
                    plugin.getConfig().getLong("atmosphere.playlist.check-interval-ticks", 40L));
            BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online == null || !online.isOnline() || !isAtmosphereEnabled()) {
                    BukkitTask current = playbackTasks.remove(playerId);
                    if (current != null) current.cancel();
                    if (online != null) stopConfiguredSounds(online);
                    clearPlayerState(playerId);
                    return;
                }
                updatePlaylistPlayback(online, false);
            }, periodTicks, periodTicks);
            playbackTasks.put(playerId, task);
            return;
        }

        // Backwards-compatible single-track loop for servers that disable the playlist.
        play(player);
        long fallbackMillis = Math.max(10L,
                plugin.getConfig().getLong("atmosphere.loop-seconds", 76L)) * 1000L;
        long loopMillis = Math.max(10_000L,
                plugin.getConfig().getLong("atmosphere.loop-milliseconds", fallbackMillis));
        long periodTicks = Math.max(1L, Math.round(loopMillis / 50.0D));
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline() || !isAtmosphereEnabled()) {
                BukkitTask current = playbackTasks.remove(playerId);
                if (current != null) current.cancel();
                if (online != null) stopConfiguredSounds(online);
                return;
            }
            stopConfiguredSounds(online);
            play(online);
        }, periodTicks, periodTicks);
        playbackTasks.put(playerId, task);
    }

    private void updatePlaylistPlayback(Player player, boolean force) {
        UUID playerId = player.getUniqueId();
        String desiredProfile = desiredProfile(player);
        String currentTrack = activeTracks.get(playerId);
        String currentProfile = activeProfiles.get(playerId);

        if (desiredProfile == null) {
            if (currentTrack != null) stopSound(player, currentTrack);
            activeTracks.remove(playerId);
            activeProfiles.remove(playerId);
            nextTrackAt.remove(playerId);
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && desiredProfile.equals(currentProfile)
                && currentTrack != null && now < nextTrackAt.getOrDefault(playerId, 0L)) {
            return;
        }

        String nextTrack = "cave".equals(desiredProfile)
                ? plugin.getConfig().getString("atmosphere.playlist.cave-sound",
                    "warriorland_halloween:dark_cavern_ambient")
                : chooseNightTrack(playerId);

        if (nextTrack == null || nextTrack.isBlank()) {
            if (currentTrack != null) stopSound(player, currentTrack);
            activeTracks.remove(playerId);
            activeProfiles.remove(playerId);
            nextTrackAt.remove(playerId);
            return;
        }

        if (currentTrack != null) stopSound(player, currentTrack);

        if (!hasItemsAdder()) {
            warnAudioOnce("itemsadder",
                    "Halloween playlist is enabled but ItemsAdder is not enabled; custom audio cannot be registered.");
            activeTracks.remove(playerId);
            activeProfiles.remove(playerId);
            nextTrackAt.remove(playerId);
            return;
        }

        float volume = (float) Math.max(0.0D, Math.min(3.0D,
                plugin.getConfig().getDouble("atmosphere.volume", 3.0D)));
        float pitch = (float) Math.max(0.1D,
                plugin.getConfig().getDouble("atmosphere.pitch", 1.0D));
        try {
            player.playSound(player, nextTrack, SoundCategory.AMBIENT, volume, pitch);
            activeTracks.put(playerId, nextTrack);
            activeProfiles.put(playerId, desiredProfile);
            String trackKey = soundKey(nextTrack);
            long duration = Math.max(10_000L,
                    plugin.getConfig().getLong("atmosphere.track-loop-milliseconds." + trackKey,
                            plugin.getConfig().getLong("atmosphere.loop-milliseconds", 76_000L)));
            nextTrackAt.put(playerId, now + duration);
        } catch (Exception ex) {
            warnAudioOnce("playlist:" + nextTrack,
                    "Could not play Halloween track '" + nextTrack + "' (" + ex.getClass().getSimpleName()
                            + "). Rebuild ItemsAdder with /iazip and ensure clients accepted the latest pack.");
            activeTracks.remove(playerId);
            activeProfiles.remove(playerId);
            nextTrackAt.remove(playerId);
        }
    }

    private String desiredProfile(Player player) {
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return null;

        if (isUndergroundCave(player)) return "cave";

        long time = Math.floorMod(world.getTime(), 24_000L);
        long nightStart = Math.floorMod(
                plugin.getConfig().getLong("atmosphere.playlist.night-start-tick", 13_000L), 24_000L);
        long nightEnd = Math.floorMod(
                plugin.getConfig().getLong("atmosphere.playlist.night-end-tick", 23_000L), 24_000L);
        boolean isNight = nightStart <= nightEnd
                ? time >= nightStart && time < nightEnd
                : time >= nightStart || time < nightEnd;
        return isNight ? "night" : null;
    }

    /**
     * Detect underground spaces conservatively so tree canopies and ordinary rooms
     * do not trigger the cave-only theme. This is an approximation, not a cave biome.
     */
    private boolean isUndergroundCave(Player player) {
        if (!plugin.getConfig().getBoolean("atmosphere.playlist.cave-enabled", true)) return false;
        var location = player.getLocation();
        int surfaceY = player.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
        int depth = Math.max(4,
                plugin.getConfig().getInt("atmosphere.playlist.cave-min-depth-below-surface", 6));
        return location.getBlockY() <= surfaceY - depth
                && location.getBlock().getLightFromSky() <= 0;
    }

    private String chooseNightTrack(UUID playerId) {
        List<String> tracks = new ArrayList<>(
                plugin.getConfig().getStringList("atmosphere.playlist.night-sounds"));
        tracks.removeIf(track -> track == null || track.isBlank());
        if (tracks.isEmpty()) {
            String legacy = plugin.getConfig().getString("atmosphere.sound", "");
            if (legacy == null || legacy.isBlank()) return "";
            tracks.add(legacy);
        }
        String previous = previousNightTracks.get(playerId);
        if (tracks.size() > 1 && previous != null) {
            tracks.removeIf(previous::equals);
        }
        String selected = tracks.get(ThreadLocalRandom.current().nextInt(tracks.size()));
        previousNightTracks.put(playerId, selected);
        return selected;
    }

    private boolean isPlaylistEnabled() {
        return plugin.getConfig().getBoolean("atmosphere.playlist.enabled", true);
    }

    private boolean isAtmosphereEnabled() {
        return plugin.isEventEnabled()
                && plugin.getConfig().getBoolean("atmosphere.enabled", true);
    }

    private void stopConfiguredSounds(Player player) {
        try {
            player.stopSound(SoundCategory.MUSIC);
        } catch (Exception ignored) {
            // Continue stopping custom IDs below.
        }
        Set<String> sounds = new HashSet<>();
        addSound(sounds, plugin.getConfig().getString("atmosphere.sound", ""));
        addSound(sounds, plugin.getConfig().getString("atmosphere.fallback-sound", ""));
        addSound(sounds, plugin.getConfig().getString("atmosphere.playlist.cave-sound", ""));
        for (String sound : plugin.getConfig().getStringList("atmosphere.playlist.night-sounds")) {
            addSound(sounds, sound);
        }
        String active = activeTracks.get(player.getUniqueId());
        addSound(sounds, active);
        for (String sound : sounds) stopSound(player, sound);
    }

    private void addSound(Set<String> sounds, String sound) {
        if (sound != null && !sound.isBlank()) sounds.add(sound);
    }

    private String soundKey(String sound) {
        int separator = sound.lastIndexOf(':');
        return separator >= 0 ? sound.substring(separator + 1) : sound;
    }

    private void clearPlayerState(UUID playerId) {
        activeTracks.remove(playerId);
        activeProfiles.remove(playerId);
        nextTrackAt.remove(playerId);
        previousNightTracks.remove(playerId);
    }

    private void stopSound(Player player, String sound) {
        if (sound == null || sound.isBlank()) return;
        try {
            player.stopSound(sound);
        } catch (Exception ignored) {
            // A sound may no longer be registered after a pack/config reload.
        }
    }

    private void play(Player player) {
        String custom = plugin.getConfig().getString("atmosphere.sound", "");
        String fallback = plugin.getConfig().getString("atmosphere.fallback-sound", "");
        float volume = (float) Math.max(0.0D, Math.min(3.0D,
                plugin.getConfig().getDouble("atmosphere.volume", 3.0D)));
        float pitch = (float) Math.max(0.1D, plugin.getConfig().getDouble("atmosphere.pitch", 1.0D));

        try {
            player.stopSound(SoundCategory.MUSIC);
        } catch (Exception ignored) {
            // Continue with configured custom sounds.
        }

        if (custom != null && !custom.isBlank()) {
            if (!hasItemsAdder()) {
                warnAudioOnce("itemsadder",
                        "Halloween ambience is configured but ItemsAdder is not enabled; the custom soundtrack cannot be registered.");
            } else {
                try {
                    player.playSound(player, custom, SoundCategory.AMBIENT, volume, pitch);
                    return;
                } catch (Exception ex) {
                    warnAudioOnce("custom:" + custom,
                            "Could not play Halloween sound '" + custom + "' (" + ex.getClass().getSimpleName()
                                    + "). Verify that the rebuilt ItemsAdder resource pack was imported with /iazip and accepted by clients.");
                }
            }
        }

        if (fallback != null && !fallback.isBlank()) {
            try {
                player.playSound(player, fallback, SoundCategory.AMBIENT, volume, pitch);
            } catch (Exception ex) {
                warnAudioOnce("fallback:" + fallback,
                        "Configured Halloween fallback sound '" + fallback + "' is unavailable (" + ex.getClass().getSimpleName() + ").");
            }
        }
    }

    private void warnAudioOnce(String key, String message) {
        if (reportedAudioIssues.add(key)) plugin.getLogger().warning(message);
    }

    private boolean hasItemsAdder() {
        var itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        return itemsAdder != null && itemsAdder.isEnabled();
    }
}
