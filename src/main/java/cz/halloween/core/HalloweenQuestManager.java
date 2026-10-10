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
     * Every combat step needs the matching mob's cursed_mob tag. Two story
     * beats also need a genuine night-time visit/ritual at the configured rift.
     */
    private final List<Quest> quests = List.of(
            new Quest("fog-patrol", "Znamení po setmění",
                    "Za skutečné noci poraz 8 Prokletých zombie. Obyčejní zombie se nepočítají.",
                    "special-night:cursed-zombie", 8, 800),
            new Quest("graveyard-survey", "Mapa zapomenutých hřbitovů",
                    "V noci navštiv 3 různé hřbitovy nastavené po mapě. Každé místo se započítá jen jednou.",
                    "graveyard-survey", 3, 1300),
            new Quest("gravekeepers-debt", "Dluh hrobníkům",
                    "Na nastavených hřbitovech poraz 8 Hrobníků. Hrobníci mimo hřbitovy se nepočítají.",
                    "special-graveyard:gravekeeper", 8, 1600),
            new Quest("blood-silk", "Pavučina svědků",
                    "Krvaví pavouci obývají místa, kde zmizeli průzkumníci. Poraz 8 Krvavých pavouků.",
                    "special:blood-spider", 8, 1400),
            new Quest("witching-hour", "Výpověď z popela",
                    "Hexové čarodějky znají další část pravdy. Poraz 6 Hexových čarodějek.",
                    "special:hex-witch", 6, 1400),
            new Quest("pumpkin-wraith", "Rozbité pečeti",
                    "Přízraky hlídají rozbité pečeti. Poraz 8 Dýňových přízraků.",
                    "special:pumpkin-wraith", 8, 1600),
            new Quest("rift-investigation", "Sestup k trhlině",
                    "V noci se vrať k příběhové trhlině a zůstaň poblíž, dokud tě její energie nezaznamená.",
                    "rift-investigation", 1, 1800),
            new Quest("void-reaper", "Za hranou světa",
                    "Trhlina se probudila. Vyhledej a poraz 8 Ženců prázdnoty.",
                    "special:void-reaper", 8, 2000),
            new Quest("frost-stalker", "Ledová stopa",
                    "Ledoví stopaři odnášejí části pečeti. Poraz 8 Ledových stopařů.",
                    "special:frost-stalker", 8, 2200),
            new Quest("nightmare", "Lovec ve snech",
                    "Přežij lov a poraz 5 Nočních můr.",
                    "special:nightmare", 5, 2500),
            new Quest("graveyard-echo", "Ozvěny pod náhrobky",
                    "Hrobníci se vrátili. Na některém z nastavených hřbitovů poraz dalších 5 Hrobníků.",
                    "special-graveyard:gravekeeper", 5, 2600),
            new Quest("blood-silk-return", "Hnízdo krvavého hedvábí",
                    "Znič zdroj nákazy: poraz dalších 10 Krvavých pavouků.",
                    "special:blood-spider", 10, 2600),
            new Quest("witching-hour-elite", "Kruh třinácti svící",
                    "Hexové čarodějky se shromáždily. Poraz 10 Hexových čarodějek.",
                    "special:hex-witch", 10, 2800),
            new Quest("void-reaper-night", "Ženci po půlnoci",
                    "Za skutečné noci poraz 4 Žence prázdnoty, než se spojení znovu rozšíří.",
                    "special-night:void-reaper", 4, 3000),
            new Quest("rift-seal", "Cena za zborcení",
                    "V noci se vrať k trhlině. Drž Echo Shard a klikni pravým do vzduchu. Rituál spotřebuje 8 Echo Shardů a 2 Crying Obsidiany.",
                    "rift-seal", 1, 4000),
            new Quest("nightmare-last-pack", "Noc bez úniku",
                    "Za skutečné noci poraz 3 Noční můry. Pouze označené Halloween moby se počítají.",
                    "special-night:nightmare", 3, 3500),
            new Quest("cursed-return", "Poslední vlna prokletí",
                    "Trhlina vysílá posily. Za noci poraz 12 Prokletých zombie.",
                    "special-night:cursed-zombie", 12, 3500),
            new Quest("vampire-awakening", "Klíč ke Králi upírů",
                    "Dostaň se do nastavené upíří arény. Drž Nether Star a klikni pravým do vzduchu; rituál spotřebuje 8 Echo Shardů, 4 Crying Obsidiany, 4 Ghast Tears a 1 Nether Star.",
                    "vampire-awakening", 1, 5000),
            new Quest("vampire-king-defeated", "Pád Krále upírů",
                    "Finále: zapoj se do skutečného souboje v aréně a pomoz porazit Krále upírů. Počítá se jen řádný encounter, ne testovací boss.",
                    "vampire-king-defeated", 1, 10000)
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

    public boolean isActiveObjective(Player player, String objective) {
        if (!isEligible(player) || objective == null || objective.isBlank()) return false;
        Quest active = ensureActiveQuest(player);
        return active != null && active.objective().equalsIgnoreCase(objective);
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
        // Vanilla hostile mobs can never advance the story.
        if (specialMob == null || specialMob.isBlank()) return;

        String mobId = normalizeMobId(specialMob);
        String objective = active.objective().toLowerCase(Locale.ROOT);
        boolean requiresNight = objective.startsWith("special-night:");
        boolean requiresGraveyard = objective.startsWith("special-graveyard:");
        String requiredMob = objective.startsWith("special-night:")
                ? objective.substring("special-night:".length())
                : objective.startsWith("special-graveyard:")
                ? objective.substring("special-graveyard:".length())
                : objective.startsWith("special:") ? objective.substring("special:".length()) : "";
        if (requiredMob.isBlank() || !requiredMob.equals(mobId)) return;

        if (requiresNight) {
            long worldTime = dead.getWorld().getTime() % 24000L;
            if (worldTime < 13000L || worldTime > 23000L) {
                killer.sendMessage(plugin.color("&8HALLOWEEN &7» &cTento cíl se počítá pouze v noci."));
                return;
            }
        }
        if (requiresGraveyard && (plugin.getGraveyardManager() == null
                || !plugin.getGraveyardManager().isAtGraveyard(dead.getLocation()))) return;
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

        String objective = active.objective();
        if (objective.startsWith("special:") || objective.startsWith("special-night:")
                || objective.startsWith("special-graveyard:")) {
            player.sendMessage(plugin.color("&8Počítají se jen označení Halloween mobové, ne běžná monstra."));
        }
        if (objective.startsWith("special-night:") || objective.equals("rift-investigation")
                || objective.equals("rift-seal") || objective.equals("graveyard-survey")) {
            player.sendMessage(plugin.color("&8Podmínka: noc ve hře (čas 13 000–23 000)."));
        }
        if (objective.startsWith("special-graveyard:") || objective.equals("graveyard-survey")) {
            int configured = plugin.getGraveyardManager() == null ? 0 : plugin.getGraveyardManager().getConfiguredGraveyardCount();
            if (configured == 0) {
                player.sendMessage(plugin.color("&cHřbitovy nejsou nastavené. Správce musí použít /halloween setgraveyard <id> na každém hřbitově."));
            } else if (objective.equals("graveyard-survey") && configured < active.target()) {
                player.sendMessage(plugin.color("&cJe potřeba nastavit alespoň " + active.target() + " různé hřbitovy; aktuálně: " + configured + "."));
            } else player.sendMessage(plugin.color("&8Hrobníci se počítají pouze uvnitř nastavených hřbitovů."));
        }
        if ((objective.equals("rift-investigation") || objective.equals("rift-seal"))
                && (plugin.getRiftManager() == null || !plugin.getRiftManager().isConfigured())) {
            player.sendMessage(plugin.color("&cTrhlina zatím není umístěná. Správce ji musí nastavit příkazem /halloween setrift."));
        }
        if ((objective.equals("vampire-awakening") || objective.equals("vampire-king-defeated"))
                && !plugin.getConfig().getBoolean("bosses.vampire.arena.configured", false)) {
            player.sendMessage(plugin.color("&cUpíří aréna není nastavená. Správce musí použít /halloween setvampirearena."));
        }
        player.sendMessage(plugin.color("&7Odměna: &6" + active.reward() + " fragmentů"));
        player.sendMessage(plugin.color("&8Postup se ukládá a přežije restart serveru."));
        player.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    public int getQuestCount() {
        return quests.size();
    }

    /**
     * Applies a previously saved final-boss proof when a qualified participant is online.
     * The proof is persisted independently so the player can also finish the campaign later.
     */
    public void refreshStoredVampireVictory(Player player) {
        if (player != null && player.isOnline()) ensureActiveQuest(player);
    }

    private Quest ensureActiveQuest(Player player) {
        UUID id = player.getUniqueId();
        String activeId = plugin.getDataManager().getStoryQuest(id);
        if (!activeId.isBlank()) {
            Quest active = findQuest(activeId);
            if (active != null && !plugin.getDataManager().hasCompletedStoryQuest(id, active.id())) {
                if (active.id().equals("vampire-king-defeated")
                        && plugin.getDataManager().hasVampireKingDefeatProof(id)) {
                    addProgress(player, active, 1);
                    return ensureActiveQuest(player);
                }
                return active;
            }
        }

        for (Quest quest : quests) {
            if (!plugin.getDataManager().hasCompletedStoryQuest(id, quest.id())) {
                plugin.getDataManager().setStoryQuest(id, quest.id());
                if (quest.id().equals("vampire-king-defeated")
                        && plugin.getDataManager().hasVampireKingDefeatProof(id)) {
                    addProgress(player, quest, 1);
                    return ensureActiveQuest(player);
                }
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
        if (quest.id().equals("vampire-awakening")) {
            plugin.getDataManager().unlockFinale();
            org.bukkit.Bukkit.broadcastMessage(plugin.color(
                    "&4&lHALLOWEEN &8» &cRituál u arény byl dokončen. Král upírů se může probudit, jakmile jsou splněny i serverové podmínky finále."
            ));
        }
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
            player.sendMessage(plugin.color("&7Další úkol odemčen: &e" + next.title()));
            player.sendMessage(plugin.color("&7" + next.description()));
            if ((next.objective().equals("rift-investigation") || next.objective().equals("rift-seal"))
                    && (plugin.getRiftManager() == null || !plugin.getRiftManager().isConfigured())) {
                player.sendMessage(plugin.color("&cSprávce musí umístit trhlinu příkazem /halloween setrift."));
            }
            player.sendMessage(plugin.color("&7Použij &f/halloween quests &7pro podrobnosti."));
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
