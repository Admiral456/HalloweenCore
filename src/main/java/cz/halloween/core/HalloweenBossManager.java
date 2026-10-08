package cz.halloween.core;

import org.bukkit.configuration.ConfigurationSection;

public final class HalloweenBossManager {
    private final HalloweenCore plugin;

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
        return spec.enabled()
                && plugin.getDataManager().isFinaleUnlocked()
                && progressReady
                && spec.finalBoss()
                && spec.arenaRequired();
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

        plugin.getLogger().info("Vampire boss specification locked: "
                + spec.minHeightBlocks() + " blocks high, "
                + spec.minWidthWithWingsBlocks() + " blocks wide with wings.");
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
