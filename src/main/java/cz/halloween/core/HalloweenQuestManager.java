package cz.halloween.core;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;
import java.util.UUID;

public final class HalloweenQuestManager implements Listener {
    private record Quest(String id, String title, String description, String objective,
                         int target, long reward) {}

    private final List<Quest> quests = List.of(
            new Quest("fog-patrol", "Znamení v mlze",
                    "Zabij 10 nepřátelských tvorů a zjisti, co se děje po setmění.",
                    "mob-kill", 10, 150),
            new Quest("gravekeepers-debt", "Dluh hrobníkovi",
                    "Vytěž 20 bloků z nastaveného seznamu těžebních odměn.",
                    "mining", 20, 250),
            new Quest("blood-silk", "Krvavé hedvábí",
                    "Ulovi 10 ryb v temných vodách a připrav se na další stopu.",
                    "fishing", 10, 300),
            new Quest("cursed-harvest", "Sklizeň za úplňku",
                    "Sklid 20 zralých plodin a doplň zásoby před nocí.",
                    "farming", 20, 200),
            new Quest("witching-hour", "Čarodějnická hodina",
                    "Objev jedno skutečně nastavené tajné místo.",
                    "secret-discovery", 1, 400),
            new Quest("pumpkin-wraith", "Dýňové prokletí",
                    "Poraz 25 nepřátelských tvorů a zastav šíření kletby.",
                    "mob-kill", 25, 450),
            new Quest("void-reaper", "Ženec z prázdnoty",
                    "Odemkni jednu zamčenou podzemní Halloween schránku.",
                    "cache-open", 1, 600),
            new Quest("frost-stalker", "Ledová stopa",
                    "Vytěž dalších 40 bloků z nastaveného seznamu těžebních odměn.",
                    "mining", 40, 650),
            new Quest("nightmare", "Poslední noční můra",
                    "Poraz 50 nepřátelských tvorů a dokonči letošní příběh.",
                    "mob-kill", 50, 900)
    );

    private final HalloweenCore plugin;

    public HalloweenQuestManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void recordAction(Player player, String source) {
        if (!isEligible(player) || source == null) return;
        Quest quest = ensureActiveQuest(player);
        if (quest == null || !quest.objective().equalsIgnoreCase(source)) return;
        addProgress(player, quest, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHostileMobDeath(EntityDeathEvent event) {
        if (!plugin.isEventEnabled()) return;
        Player killer = event.getEntity().getKiller();
        if (!isEligible(killer)) return;

        var dead = event.getEntity();
        boolean hostile = dead instanceof Monster || dead instanceof EnderDragon || dead instanceof Wither
                || dead instanceof Ghast || dead instanceof Phantom || dead instanceof Shulker
                || dead instanceof Slime;
        if (!hostile) return;
        recordAction(killer, "mob-kill");
    }

    public void show(Player player) {
        if (player == null) return;
        Quest active = ensureActiveQuest(player);
        int completedCount = plugin.getDataManager().getCompletedStoryQuests(player.getUniqueId()).size();

        player.sendMessage(plugin.color("&8&m--------------------------------"));
        player.sendMessage(plugin.color("&6&lPŘÍBĚHOVÉ HALLOWEEN QUESTY"));
        player.sendMessage(plugin.color("&7Dokončeno: &e" + completedCount + "&7/&e" + quests.size()));
        if (active == null) {
            player.sendMessage(plugin.color("&a&lVŠECHNY KAPITOLY DOKONČENY"));
            player.sendMessage(plugin.color("&7Pro letošní příběh už nemáš další úkol. Díky za záchranu světa."));
            player.sendMessage(plugin.color("&8&m--------------------------------"));
            return;
        }

        int progress = plugin.getDataManager().getStoryQuestProgress(player.getUniqueId());
        player.sendMessage(plugin.color("&eKapitola " + (completedCount + 1) + "&8/&e" + quests.size()
                + " &8» &f" + active.title()));
        player.sendMessage(plugin.color("&7" + active.description()));
        player.sendMessage(plugin.color("&7Postup: &e" + Math.min(progress, active.target())
                + "&7/&e" + active.target()));
        player.sendMessage(plugin.color("&7Odměna: &6" + active.reward() + " fragmentů"));
        player.sendMessage(plugin.color("&8Postup se ukládá a přežije restart serveru."));
        player.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    public int getQuestCount() {
        return quests.size();
    }

    private Quest ensureActiveQuest(Player player) {
        UUID id = player.getUniqueId();
        String activeId = plugin.getDataManager().getStoryQuest(id);
        if (!activeId.isBlank()) {
            Quest active = findQuest(activeId);
            if (active != null && !plugin.getDataManager().hasCompletedStoryQuest(id, active.id())) {
                return active;
            }
        }

        for (Quest quest : quests) {
            if (!plugin.getDataManager().hasCompletedStoryQuest(id, quest.id())) {
                plugin.getDataManager().setStoryQuest(id, quest.id());
                return quest;
            }
        }

        plugin.getDataManager().setStoryQuest(id, "");
        return null;
    }

    private Quest findQuest(String id) {
        return quests.stream().filter(quest -> quest.id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    private void addProgress(Player player, Quest quest, int amount) {
        UUID playerId = player.getUniqueId();
        int oldProgress = plugin.getDataManager().getStoryQuestProgress(playerId);
        int updated = Math.min(quest.target(), oldProgress + Math.max(0, amount));
        plugin.getDataManager().setStoryQuestProgress(playerId, updated);
        if (updated < quest.target()) return;

        if (!plugin.getDataManager().completeStoryQuest(playerId, quest.id())) return;
        plugin.getDataManager().setStoryQuest(playerId, "");
        plugin.getService().addFragments(playerId, quest.reward(), "story-quest");
        plugin.getDataManager().save();

        player.sendTitle(plugin.color("&6&lKAPITOLA DOKONČENA"),
                plugin.color("&f" + quest.title() + " &8• &e+" + quest.reward() + " fragmentů"),
                10, 60, 20);
        player.sendMessage(plugin.color("&6&lHALLOWEEN &8» &aSplnil jsi quest &f" + quest.title()
                + "&a a získáváš &e" + quest.reward() + " fragmentů&a."));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1.0, 0),
                24, 0.45, 0.6, 0.45, 0.02);

        Quest next = ensureActiveQuest(player);
        if (next != null) {
            player.sendMessage(plugin.color("&7Další úkol odemčen: &e" + next.title()
                    + "&7. Použij &f/halloween quests &7pro podrobnosti."));
        } else {
            player.sendMessage(plugin.color("&6&lPŘÍBĚH DOKONČEN &8» &eDěkujeme, že jsi zachránil svět."));
        }
    }

    private boolean isEligible(Player player) {
        return player != null && player.isOnline() && plugin.isEventEnabled()
                && plugin.isEligibleGameplayPlayer(player)
                && plugin.isEligibleGameplayWorld(player.getWorld())
                && plugin.getConfig().getBoolean("story-quests.enabled", true);
    }
}
