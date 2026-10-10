package cz.halloween.core;

import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class HalloweenQuestManager implements Listener {
    private record Quest(String id, String title, String description, String objective,
                         int target, long reward) {}

    private final HalloweenCore plugin;
    private final NamespacedKey cursedMobKey;
    private final List<Quest> quests = List.of(
            new Quest("fog-patrol", "Znamení v mlze",
                    "Zabij 10 nepřátelských tvorů a zjisti, co se děje po setmění.",
                    "mob-kill", 10, 150),
            new Quest("gravekeepers-debt", "Dluh hrobníkovi",
                    "Najdi a poraz 2 hrobníky na toulkách světem.",
                    "special:gravekeeper", 2, 250),
            new Quest("blood-silk", "Krvavé hedvábí",
                    "Připrav krvavé pavouky o jejich kořist — poraz 3 kusy.",
                    "special:blood-spider", 3, 300),
            new Quest("cursed-harvest", "Sklizeň za úplňku",
                    "Sklid 20 zralých plodin a posil svou výbavu.",
                    "farming", 20, 200),
            new Quest("witching-hour", "Čarodějnická hodina",
                    "Najdi a poraz 2 hexové čarodějky.",
                    "special:hex-witch", 2, 400),
            new Quest("pumpkin-wraith", "Dýňové prokletí",
                    "Znič 2 dýňové přízraky, než jejich popel zahalí okolí.",
                    "special:pumpkin-wraith", 2, 450),
            new Quest("void-reaper", "Ženec z prázdnoty",
                    "Vypátrej a poraz 2 Žence prázdnoty.",
                    "special:void-reaper", 2, 600),
            new Quest("frost-stalker", "Ledová stopa",
                    "Přemoz 2 Ledové stopaře v chladných krajích.",
                    "special:frost-stalker", 2, 650),
            new Quest("nightmare", "Poslední noční můra",
                    "Přežij noc a poraz Noční můru.",
                    "special:nightmare", 1, 900)
    );

    public HalloweenQuestManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.cursedMobKey = new NamespacedKey(plugin, "cursed_mob");
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

        String specialMob = event.getEntity().getPersistentDataContainer()
                .get(cursedMobKey, PersistentDataType.STRING);
        if (specialMob != null && !specialMob.isBlank()) {
            recordAction(killer, "special:" + normalizeMobId(specialMob));
            recordAction(killer, "mob-kill");
            return;
        }

        var dead = event.getEntity();
        if (dead instanceof Monster || dead instanceof EnderDragon || dead instanceof Wither
                || dead instanceof Ghast || dead instanceof Phantom || dead instanceof Shulker
                || dead instanceof Slime) {
            recordAction(killer, "mob-kill");
        }
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

    private String normalizeMobId(String mobId) {
        return mobId.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
