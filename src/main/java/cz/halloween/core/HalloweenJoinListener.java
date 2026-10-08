package cz.halloween.core;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class HalloweenJoinListener implements Listener {
    private final HalloweenCore plugin;

    public HalloweenJoinListener(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isEventEnabled()) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long last = plugin.getDataManager().getLastJoin(player.getUniqueId());

        if (last == 0L) {
            awardDaily(player, 1, false);
            return;
        }

        long elapsed = now - last;
        long claimDelay = plugin.getConfig().getLong("return-rewards.claim-delay-hours", 20L) * 3_600_000L;
        if (elapsed < claimDelay) return;

        long streakWindow = plugin.getConfig().getLong("return-rewards.streak-window-hours", 48L) * 3_600_000L;
        boolean comeback = elapsed >= plugin.getConfig().getLong("return-rewards.comeback-after-hours", 72L) * 3_600_000L;
        int previousStreak = plugin.getDataManager().getStreak(player.getUniqueId());
        int streak = elapsed <= streakWindow ? Math.min(previousStreak + 1, 7) : 1;

        awardDaily(player, streak, comeback);
    }

    private void awardDaily(Player player, int streak, boolean comeback) {
        int base = Math.max(1, plugin.getConfig().getInt("return-rewards.daily-base", 10));
        int step = Math.max(0, plugin.getConfig().getInt("return-rewards.daily-streak-step", 5));
        int cap = Math.max(base, plugin.getConfig().getInt("return-rewards.daily-cap", 50));
        long reward = Math.min(cap, base + (long) (streak - 1) * step);

        long comebackBonus = comeback
                ? Math.max(0L, plugin.getConfig().getLong("return-rewards.comeback-bonus", 15L))
                : 0L;

        long total = reward + comebackBonus;
        plugin.getService().addFragments(player.getUniqueId(), total, "daily");

        plugin.getDataManager().recordJoin(player.getUniqueId(), System.currentTimeMillis(), streak);

        if (comeback) {
            player.sendMessage(plugin.color("&6HALLOWEEN &8» &fTma tě po delší době zase našla. &e+" + total + " &ffragmentů."));
        } else {
            player.sendMessage(plugin.color("&6HALLOWEEN &8» &fDenní odměna za návrat: &e+" + reward
                    + " &7(streak &e" + streak + "&7/7)."));
        }
    }
}
