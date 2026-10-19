package cz.halloween.core;

import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.List;

public final class HalloweenChallengeManager {
    private record Challenge(String id, String name, String source, int target) {}

    private final HalloweenCore plugin;
    private final List<Challenge> challenges = List.of(
            new Challenge("hunt", "Noční lov", "mob-kill", 25),
            new Challenge("miner", "Těžba", "mining", 50),
            new Challenge("harvest", "Prokletá sklizeň", "farming", 35),
            new Challenge("angler", "Rybaření", "fishing", 5)
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
        String storedDay = plugin.getDataManager().getChallengeDay(player.getUniqueId());
        String storedType = plugin.getDataManager().getChallengeType(player.getUniqueId());
        boolean knownChallenge = challenges.stream()
                .anyMatch(challenge -> challenge.id().equalsIgnoreCase(storedType));
        if (day.equals(storedDay) && knownChallenge) return;

        // Reassign invalid/retired challenge IDs as well as a new day. This avoids
        // silently treating old "rare-hunt" progress as an ordinary hunt challenge.
        int index = Math.floorMod(LocalDate.now().getDayOfYear() + player.getUniqueId().hashCode(), challenges.size());
        Challenge challenge = challenges.get(index);
        plugin.getDataManager().setChallenge(player.getUniqueId(), day, challenge.id());
    }

    private Challenge getChallenge(Player player) {
        String id = plugin.getDataManager().getChallengeType(player.getUniqueId());
        return challenges.stream().filter(c -> c.id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    private void claim(Player player, Challenge challenge) {
        long reward = Math.max(1L, plugin.getConfig().getLong("daily-challenge.reward-fragments", 60L));
        // Mark first: addFragments can persist immediately when the global goal is
        // reached. That save must already contain the claimed flag, or a crash at
        // the goal transition could let the same daily reward be claimed again.
        plugin.getDataManager().markChallengeClaimed(player.getUniqueId());
        plugin.getService().addFragments(player.getUniqueId(), reward, "challenge");
        // Persist the reward and claimed flag together when no intermediate save was needed.
        plugin.getDataManager().save();

        player.sendTitle(
                plugin.color("&6&lDENNÍ LOV SPLNĚN"),
                plugin.color("&e+" + reward + " fragmentů"),
                10, 50, 20
        );
        player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 0.8f, 1.2f);
    }
}
