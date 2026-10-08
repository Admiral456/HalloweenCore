package cz.halloween.core;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HalloweenDataManager {
    private final HalloweenCore plugin;
    private final File file;
    private final Map<UUID, Long> fragments = new HashMap<>();
    private long serverFragments;

    public HalloweenDataManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        fragments.clear();
        serverFragments = 0L;
        if (!file.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        serverFragments = Math.max(0L, data.getLong("server.total-fragments", 0L));

        if (data.isConfigurationSection("players")) {
            for (String key : data.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long amount = Math.max(0L, data.getLong("players." + key + ".fragments", 0L));
                    if (amount > 0L) fragments.put(uuid, amount);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid player UUID in data.yml: " + key);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("server.total-fragments", serverFragments);
        for (Map.Entry<UUID, Long> entry : fragments.entrySet()) {
            data.set("players." + entry.getKey() + ".fragments", entry.getValue());
        }

        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder.");
            }
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public long getFragments(UUID uuid) {
        return fragments.getOrDefault(uuid, 0L);
    }

    public long addFragments(UUID uuid, long amount) {
        if (amount <= 0L) return getFragments(uuid);

        long current = getFragments(uuid);
        long updated;
        try {
            updated = Math.addExact(current, amount);
            serverFragments = Math.addExact(serverFragments, amount);
        } catch (ArithmeticException ex) {
            updated = Long.MAX_VALUE;
            serverFragments = Long.MAX_VALUE;
        }

        fragments.put(uuid, updated);
        return updated;
    }

    public long getServerFragments() {
        return serverFragments;
    }

    public Map<UUID, Long> getAllFragments() {
        return Map.copyOf(fragments);
    }
}
