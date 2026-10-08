package cz.halloween.core;

import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.List;

public final class HalloweenChallengeManager {
    private record Challenge(String id, String name, String source, int target) {}

    private final HalloweenCore plugin;
    private final List<Challenge> challenges = List.of(
            new Challenge("hunt", "Noční lov", "mob-kill", 25),
            new Challenge("miner", "Hřbitovní těžba", "mining", 50),
            new Challenge("harvest", "Prokletá sklizeň", "farming", 35),
            new Challenge("angler", "Rybaření duší", "fishing", 5),
            new Challenge("rare-hunt", "Lov prokletých", "special-mob", 2)
    );

    public HalloweenChallengeManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void recordAction(Player player, String source) {
        if (!plugin.isEventEnabled() || player == null || source == null) return;

        ensureToday(player);
        if (plugin.getDataManager().isChallengeClaimed(player.getUniqueId())) return;

        Challenge challenge = getChallenge(player);
        if (challenge == null || !challenge.source().equalsIgnoreCase(source)) return;

        int progress = plugin.getDataManager().incrementChallengeProgress(player.getUniqueId(), 1);
        if (progress >= challenge.target()) {
            claim(player, challenge);
        }
    }

    public void show(Player player) {
        ensureToday(player);
        Challenge challenge = getChallenge(player);
        if (challenge == null) return;

        int progress = plugin.getDataManager().getChallengeProgress(player.getUniqueId());
        boolean claimed = plugin.getDataManager().isChallengeClaimed(player.getUniqueId());

        player.sendMessage(plugin.color("&6&lDENNÍ HALLOWEEN LOV"));
        player.sendMessage(plugin.color("&7Úkol: &f" + challenge.name()));
        player.sendMessage(plugin.color("&7Postup: &e" + Math.min(progress, challenge.target()) + " &7/ &e" + challenge.target()));
        if (claimed) {
            player.sendMessage(plugin.color("&aDnešní úkol je splněný."));
        } else {
            player.sendMessage(plugin.color("&8Zdroj: &7" + challenge.source()));
        }
    }

    private void ensureToday(Player player) {
        String day = LocalDate.now().toString();
        String stored = plugin.getDataManager().getChallengeDay(player.getUniqueId());
        if (day.equals(stored)) return;

        int index = Math.floorMod(LocalDate.now().getDayOfYear() + player.getUniqueId().hashCode(), challenges.size());
        Challenge challenge = challenges.get(index);
        plugin.getDataManager().setChallenge(player.getUniqueId(), day, challenge.id());
    }

    private Challenge getChallenge(Player player) {
        String id = plugin.getDataManager().getChallengeType(player.getUniqueId());
        return challenges.stream().filter(c -> c.id().equalsIgnoreCase(id)).findFirst().orElse(challenges.get(0));
    }

    private void claim(Player player, Challenge challenge) {
        plugin.getDataManager().markChallengeClaimed(player.getUniqueId());
        long reward = Math.max(1L, plugin.getConfig().getLong("daily-challenge.reward-fragments", 60L));
        plugin.getService().addFragments(player.getUniqueId(), reward, "challenge");

        player.sendTitle(
                plugin.color("&6&lDENNÍ LOV SPLNĚN"),
                plugin.color("&e+" + reward + " fragmentů"),
                10, 50, 20
        );
        player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 0.8f, 1.2f);
    }
}
