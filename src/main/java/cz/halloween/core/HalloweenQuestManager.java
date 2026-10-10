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
    /*
     * Story progression deliberately counts only tagged Halloween mobs.
     * The first chapter additionally requires actual in-game night, so the
     * "after dark" clue is a real condition rather than flavor text.
     */
    private final List<Quest> quests = List.of(
            new Quest("fog-patrol", "Co se probouzí po setmění",
                    "Počkej na noc a poraz 4 Prokleté zombie. Běžní zombie se nepočítají.",
                    "special-night:cursed-zombie", 4, 350),
            new Quest("gravekeepers-debt", "Dluh hrobníkovi",
                    "Hrobníci střeží stopy po prvním útoku. Najdi a poraz 5 Hrobníků.",
                    "special:gravekeeper", 5, 500),
            new Quest("blood-silk", "Pavučina svědků",
                    "Krvaví pavouci obývají místo, kde zmizeli průzkumníci. Poraz 6 Krvavých pavouků.",
                    "special:blood-spider", 6, 650),
            new Quest("witching-hour", "Výpověď z popela",
                    "Hexové čarodějky znají jméno toho, kdo otevřel trhlinu. Poraz 4 Hexové čarodějky.",
                    "special:hex-witch", 4, 750),
            new Quest("pumpkin-wraith", "Dýňová pečeť",
                    "Přízraky hlídají rozbitou pečeť. Znič 5 Dýňových přízraků.",
                    "special:pumpkin-wraith", 5, 900),
            new Quest("void-reaper", "Za hranou světa",
                    "Trhlina zesílila. Vyhledej a poraz 4 Žence prázdnoty.",
                    "special:void-reaper", 4, 1100),
            new Quest("frost-stalker", "Ledová stopa",
                    "Ledoví stopaři odnášejí poslední části pečeti. Poraz 5 Ledových stopařů.",
                    "special:frost-stalker", 5, 1250),
            new Quest("nightmare", "Lovec ve snech",
                    "Noční můry už znají tvé jméno. Přežij jejich lov a poraz 3 Noční můry.",
                    "special:nightmare", 3, 1600),
            new Quest("last-nightmare", "Poslední noc",
                    "Trhlina je otevřená naplno. Za noci poraz poslední Noční můru a uzavři příběh.",
                    "special-night:nightmare", 1, 2500)
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
        var dead = event.getEntity();
        boolean hostile = dead instanceof Monster || dead instanceof EnderDragon || dead instanceof Wither
                || dead instanceof Ghast || dead instanceof Phantom || dead instanceof Shulker
                || dead instanceof Slime;
        if (!hostile) return;

        Quest active = ensureActiveQuest(killer);
        if (active == null) return;
        // Ordinary vanilla hostiles never advance the story. A matching tagged
        // Halloween mob is required for every combat chapter.
        if (specialMob == null || specialMob.isBlank()) return;

        String mobId = normalizeMobId(specialMob);
        String objective = active.objective().toLowerCase(Locale.ROOT);
        boolean requiresNight = objective.startsWith("special-night:");
        String requiredMob = objective.startsWith("special-night:")
                ? objective.substring("special-night:".length())
                : objective.startsWith("special:") ? objective.substring("special:".length()) : "";
        if (requiredMob.isBlank() || !requiredMob.equals(mobId)) return;

        if (requiresNight) {
            long worldTime = dead.getWorld().getTime() % 24000L;
            // Minecraft night: 13000 through 23000 ticks.
            if (worldTime < 13000L || worldTime > 23000L) {
                if (killer.getUniqueId().equals(killer.getUniqueId())) {
                    killer.sendMessage(plugin.color("&8HALLOWEEN &7» &cTento cíl se počítá pouze v noci."));
                }
                return;
            }
        }
        addProgress(killer, active, 1);
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
        player.sendMessage(plugin.color("&8Počítají se pouze označení Halloween mobové."));
        if (active.objective().startsWith("special-night:")) {
            player.sendMessage(plugin.color("&8Podmínka: skutečná noc ve hře (čas 13 000–23 000)."));
        }
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
