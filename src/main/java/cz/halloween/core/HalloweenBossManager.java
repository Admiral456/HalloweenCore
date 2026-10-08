package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class HalloweenBossManager {
    private final HalloweenCore plugin;
    private BossBar vampireBossBar;
    private LivingEntity trackedVampireBoss;
    private BukkitTask vampireBossBarTask;

    public HalloweenBossManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public VampireSpec getVampireSpec() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("bosses.vampire");
        if (section == null) {
            return new VampireSpec("vampire-king", "KRÁL UPÍRŮ", 10, 8, true, false, true, false);
        }

        return new VampireSpec(
                section.getString("id", "vampire-king"),
                section.getString("display-name", "&4&lKRÁL UPÍRŮ"),
                Math.max(10, section.getInt("min-height-blocks", 10)),
                Math.max(8, section.getInt("min-width-with-wings-blocks", 8)),
                section.getBoolean("wings-required", true),
                section.getBoolean("arena-required", true),
                section.getBoolean("final-boss", true),
                section.getBoolean("enabled", false)
        );
    }

    public boolean isVampireReady() {
        VampireSpec spec = getVampireSpec();
        int requiredProgress = plugin.getConfig().getInt("bosses.vampire.spawn-requirements.global-progress-percent", 100);
        long goal = plugin.getService().getGlobalGoal();
        double progress = plugin.getService().getGlobalProgressPercent();
        boolean progressReady = goal <= 0L || progress >= Math.max(0, Math.min(100, requiredProgress));
        String arenaWorld = plugin.getConfig().getString("bosses.vampire.arena.world", "");
        boolean arenaReady = !arenaWorld.isBlank()
                && plugin.getServer().getWorlds().stream().anyMatch(world -> world.getName().equalsIgnoreCase(arenaWorld))
                && plugin.getConfig().getBoolean("bosses.vampire.arena.configured", false);
        return spec.enabled()
                && plugin.getDataManager().isFinaleUnlocked()
                && progressReady
                && spec.finalBoss()
                && spec.arenaRequired()
                && arenaReady;
    }

    public boolean isVampireSpecificationValid() {
        VampireSpec spec = getVampireSpec();
        return spec.minHeightBlocks() >= 10
                && spec.minWidthWithWingsBlocks() >= 8
                && spec.wingsRequired();
    }

    public void validateConfiguration() {
        VampireSpec spec = getVampireSpec();
        int requiredProgress = plugin.getConfig().getInt("bosses.vampire.spawn-requirements.global-progress-percent", 100);
        if (requiredProgress < 0 || requiredProgress > 100) {
            plugin.getLogger().warning("Vampire boss global-progress-percent must be between 0 and 100; using 100.");
            requiredProgress = 100;
        }
        if (!isVampireSpecificationValid()) {
            plugin.getLogger().severe("Vampire boss specification is invalid. Required minimum: 10 blocks high, 8 blocks wide with wings.");
            return;
        }

        String arenaWorld = plugin.getConfig().getString("bosses.vampire.arena.world", "");
        boolean arenaConfigured = plugin.getConfig().getBoolean("bosses.vampire.arena.configured", false);
        plugin.getLogger().info("Vampire boss specification locked: "
                + spec.minHeightBlocks() + " blocks high, "
                + spec.minWidthWithWingsBlocks() + " blocks wide with wings."
                + " Arena=" + (arenaConfigured && !arenaWorld.isBlank() ? arenaWorld : "not configured") + ".");
    }

    public void trackVampireBoss(LivingEntity boss) {
        stopVampireBossBar();
        if (boss == null || boss.isDead() || !boss.isValid()) return;
        if (!plugin.getConfig().getBoolean("bosses.vampire.boss-bar.enabled", true)) return;

        trackedVampireBoss = boss;
        vampireBossBar = Bukkit.createBossBar(
                formatBossBarTitle(boss),
                readBarColor(),
                readBarStyle(),
                org.bukkit.boss.BarFlag.CREATE_FOG
        );
        vampireBossBar.setProgress(healthProgress(boss));
        vampireBossBar.setVisible(true);

        vampireBossBarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateVampireBossBar, 1L, 10L);
        updateVampireBossBar();
    }

    public void stopVampireBossBar() {
        if (vampireBossBarTask != null) {
            vampireBossBarTask.cancel();
            vampireBossBarTask = null;
        }
        if (vampireBossBar != null) {
            vampireBossBar.removeAll();
            vampireBossBar.setVisible(false);
        }
        vampireBossBar = null;
        trackedVampireBoss = null;
    }

    public boolean isVampireBossBarActive() {
        return vampireBossBar != null && trackedVampireBoss != null;
    }

    private void updateVampireBossBar() {
        if (vampireBossBar == null || trackedVampireBoss == null) return;
        if (trackedVampireBoss.isDead() || !trackedVampireBoss.isValid()) {
            stopVampireBossBar();
            return;
        }

        vampireBossBar.setTitle(formatBossBarTitle(trackedVampireBoss));
        vampireBossBar.setProgress(healthProgress(trackedVampireBoss));

        double radius = Math.max(16.0D,
                plugin.getConfig().getDouble("bosses.vampire.boss-bar.radius-blocks", 96.0D));
        double radiusSquared = radius * radius;
        Set<Player> nearby = new HashSet<>();

        Location bossLocation = trackedVampireBoss.getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)) continue;
            if (!player.getWorld().getUID().equals(bossLocation.getWorld().getUID())) continue;
            if (player.getLocation().distanceSquared(bossLocation) <= radiusSquared) {
                nearby.add(player);
                if (!vampireBossBar.getPlayers().contains(player)) {
                    vampireBossBar.addPlayer(player);
                }
            }
        }

        for (Player viewer : new HashSet<>(vampireBossBar.getPlayers())) {
            if (!nearby.contains(viewer)) {
                vampireBossBar.removePlayer(viewer);
            }
        }
    }

    private String formatBossBarTitle(LivingEntity boss) {
        VampireSpec spec = getVampireSpec();
        double health = Math.max(0.0D, boss.getHealth());
        double max = Math.max(1.0D, boss.getMaxHealth());
        return plugin.color(spec.displayName() + " &8• &c" + formatNumber(health) + " &7/ &c" + formatNumber(max) + " HP");
    }

    private double healthProgress(LivingEntity boss) {
        double max = Math.max(1.0D, boss.getMaxHealth());
        return Math.max(0.0D, Math.min(1.0D, boss.getHealth() / max));
    }

    private BarColor readBarColor() {
        try {
            return BarColor.valueOf(plugin.getConfig()
                    .getString("bosses.vampire.boss-bar.color", "RED")
                    .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarColor.RED;
        }
    }

    private BarStyle readBarStyle() {
        try {
            return BarStyle.valueOf(plugin.getConfig()
                    .getString("bosses.vampire.boss-bar.style", "SEGMENTED_20")
                    .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarStyle.SEGMENTED_20;
        }
    }

    private String formatNumber(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f", value);
    }

    public record VampireSpec(
            String id,
            String displayName,
            int minHeightBlocks,
            int minWidthWithWingsBlocks,
            boolean wingsRequired,
            boolean arenaRequired,
            boolean finalBoss,
            boolean enabled
    ) {
    }
}
