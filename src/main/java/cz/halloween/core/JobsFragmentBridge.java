package cz.halloween.core;

import com.gamingmesh.jobs.api.JobsExpGainEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Optional Jobs Reborn integration.
 *
 * Converts earned Jobs XP into a small number of seasonal fragments. A per-player daily cap
 * prevents job grinding from replacing quests as the main progression source. This class is
 * loaded reflectively only when Jobs is installed, so Jobs remains an optional dependency.
 */
public final class JobsFragmentBridge implements Listener {
    private static final ZoneId ZONE = ZoneId.of("Europe/Prague");

    private static final class State {
        private final String date;
        private double remainder;
        private int earned;

        private State(String date, double remainder, int earned) {
            this.date = date;
            this.remainder = remainder;
            this.earned = earned;
        }
    }

    private final HalloweenCore plugin;
    private final Map<UUID, State> states = new HashMap<>();

    public JobsFragmentBridge(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJobsExperience(JobsExpGainEvent event) {
        if (!plugin.isSeasonActive() || !plugin.getConfig().getBoolean("jobs-fragments.enabled", true)) return;

        OfflinePlayer offline = event.getPlayer();
        if (offline == null || !offline.isOnline()) return;
        Player player = offline.getPlayer();
        if (player == null || !player.isOnline()) return;

        double gained = event.getExp();
        if (!Double.isFinite(gained) || gained <= 0.0) return;

        int dailyCap = Math.max(0, plugin.getConfig().getInt("jobs-fragments.max-per-day", 8));
        if (dailyCap == 0) return;
        double threshold = plugin.getConfig().getDouble("jobs-fragments.experience-per-fragment", 25.0);
        if (!Double.isFinite(threshold) || threshold <= 0.0) return;

        State state = stateFor(player);
        if (state.earned >= dailyCap) return;

        state.remainder += gained;
        int available = (int) Math.min(Integer.MAX_VALUE, Math.floor(state.remainder / threshold));
        int award = Math.min(available, dailyCap - state.earned);
        if (award <= 0) return;

        state.remainder -= award * threshold;
        state.earned += award;
        if (state.earned >= dailyCap) state.remainder = 0.0;

        YamlConfiguration data = plugin.getPlayerData();
        String balancePath = "players." + player.getUniqueId() + ".fragments";
        long balance = Math.max(0L, data.getLong(balancePath, 0L));
        long safelyAwarded = award > Long.MAX_VALUE - balance ? Long.MAX_VALUE - balance : award;
        data.set(balancePath, balance + safelyAwarded);
        persistState(player.getUniqueId(), state);
        plugin.savePlayerData();

        if (safelyAwarded > 0L) {
            player.sendMessage(ChatColor.GOLD + "Jobs: " + ChatColor.LIGHT_PURPLE + "+"
                    + safelyAwarded + " Halloween fragmentů " + ChatColor.GRAY
                    + "(limit " + state.earned + "/" + dailyCap + " za dnešek).");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        State state = states.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            persistState(event.getPlayer().getUniqueId(), state);
            plugin.savePlayerData();
        }
    }

    /** Flushes cached XP remainder when HalloweenCore is shutting down. */
    public void flush() {
        if (states.isEmpty()) return;
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            persistState(entry.getKey(), entry.getValue());
        }
        states.clear();
        plugin.savePlayerData();
    }

    private State stateFor(Player player) {
        UUID uuid = player.getUniqueId();
        String today = today();
        State cached = states.get(uuid);
        if (cached != null && cached.date.equals(today)) return cached;

        String base = base(uuid);
        YamlConfiguration data = plugin.getPlayerData();
        String storedDate = data.getString(base + ".date", "");
        State loaded;
        if (today.equals(storedDate)) {
            loaded = new State(today,
                    Math.max(0.0, data.getDouble(base + ".xp-remainder", 0.0)),
                    Math.max(0, data.getInt(base + ".earned", 0)));
        } else {
            loaded = new State(today, 0.0, 0);
            persistState(uuid, loaded);
            plugin.savePlayerData();
        }
        states.put(uuid, loaded);
        return loaded;
    }

    private void persistState(UUID uuid, State state) {
        String base = base(uuid);
        YamlConfiguration data = plugin.getPlayerData();
        data.set(base + ".date", state.date);
        data.set(base + ".xp-remainder", Math.max(0.0, state.remainder));
        data.set(base + ".earned", Math.max(0, state.earned));
    }

    private String base(UUID uuid) {
        return "players." + uuid + ".jobs-fragments";
    }

    private String today() {
        return LocalDate.now(ZONE).toString();
    }
}
