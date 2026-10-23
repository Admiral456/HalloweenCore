package cz.halloween.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public final class DailyQuestManager implements Listener, CommandExecutor, TabCompleter {
    private enum Activity { KILL, HOSTILE_KILL, BLOCK, CRAFT, CONSUME, FISH, PICKUP, BREED }

    private record Task(String id, String title, String description, int target,
                        String difficulty, Activity activity, String targetKey) {}
    private record ContractSlot(String suffix, String difficulty, String label, boolean weekly) {}
    private record ActivityCount(Activity activity, String target, int amount) {}

    // Fifteen daily contracts across five tiers: 4 light, 4 medium, 3 hard,
    // 2 very-hard and 2 extreme. The pool offers 130 different task definitions.
    private static final List<ContractSlot> SLOTS = List.of(
            new ContractSlot("daily-quest", "light", "LEHKÝ", false),
            new ContractSlot("daily-quests.medium", "medium", "STŘEDNÍ", false),
            new ContractSlot("daily-quests.hard", "hard", "TĚŽKÝ", false),
            new ContractSlot("daily-quests.very-hard", "very-hard", "VELMI TĚŽKÝ", false),
            new ContractSlot("daily-quests.extreme", "extreme", "EXTRÉMNÍ", false),
            new ContractSlot("daily-quests.light-02", "light", "LEHKÝ", false),
            new ContractSlot("daily-quests.light-03", "light", "LEHKÝ", false),
            new ContractSlot("daily-quests.light-04", "light", "LEHKÝ", false),
            new ContractSlot("daily-quests.medium-02", "medium", "STŘEDNÍ", false),
            new ContractSlot("daily-quests.medium-03", "medium", "STŘEDNÍ", false),
            new ContractSlot("daily-quests.medium-04", "medium", "STŘEDNÍ", false),
            new ContractSlot("daily-quests.hard-02", "hard", "TĚŽKÝ", false),
            new ContractSlot("daily-quests.hard-03", "hard", "TĚŽKÝ", false),
            new ContractSlot("daily-quests.very-hard-02", "very-hard", "VELMI TĚŽKÝ", false),
            new ContractSlot("daily-quests.extreme-02", "extreme", "EXTRÉMNÍ", false),
            new ContractSlot("weekly-quests.contract-1", "hard", "TÝDENNÍ · TĚŽKÝ", true),
            new ContractSlot("weekly-quests.contract-2", "very-hard", "TÝDENNÍ · VELMI TĚŽKÝ", true),
            new ContractSlot("weekly-quests.contract-3", "extreme", "TÝDENNÍ · EXTRÉMNÍ", true)
    );

    private static final List<Task> TASKS = List.of(
            // Lehký kontrakt — pro běžný denní postup
            new Task("zombies", "Lov na nemrtvé", "Zabij 5 zombie.", 5, "light", Activity.KILL, "ZOMBIE"),
            new Task("skeletons", "Kostlivci po setmění", "Zabij 5 kostlivců.", 5, "light", Activity.KILL, "SKELETON"),
            new Task("iron", "Železná zásoba", "Vytěž 8 železných rud.", 8, "light", Activity.BLOCK, "IRON_ORE_GROUP"),
            new Task("pumpkins", "Dýňová sklizeň", "Rozbij 8 dýní.", 8, "light", Activity.BLOCK, "PUMPKINS"),
            new Task("coal", "Zásoby uhlí", "Vytěž 12 uhelných rud.", 12, "light", Activity.BLOCK, "COAL_ORE_GROUP"),
            new Task("logs", "Dřevo na zimu", "Pokácej 24 bloků kmenů nebo dřeva.", 24, "light", Activity.BLOCK, "LOGS"),
            new Task("crops", "Úroda za úplňku", "Sklid 16 bloků plodin.", 16, "light", Activity.BLOCK, "CROPS"),
            new Task("cows", "Hovězí zásoby", "Zabij 4 krávy.", 4, "light", Activity.KILL, "COW"),
            new Task("fishing", "Noční rybář", "Chyť 4 ryby.", 4, "light", Activity.FISH, "ANY"),
            new Task("bread", "Pekařova odměna", "Vyrob 6 chlebů.", 6, "light", Activity.CRAFT, "BREAD"),
            new Task("food", "Připrav se na noc", "Sněz 8 kusů jídla.", 8, "light", Activity.CONSUME, "FOOD"),
            new Task("spiders", "Pavoučí kout", "Zabij 5 pavouků.", 5, "light", Activity.KILL, "SPIDER"),
            new Task("hostiles", "Očista okolí", "Zabij 10 nepřátelských mobů.", 10, "light", Activity.HOSTILE_KILL, "HOSTILE_ANY"),
            new Task("stone", "Kamení na obranu", "Vytěž 48 bloků kamene nebo břidlice.", 48, "light", Activity.BLOCK, "STONE_GROUP"),
            new Task("chickens", "Slepice v noci", "Poraz 5 kuřat.", 5, "light", Activity.KILL, "CHICKEN"),
            new Task("sheep", "Vlna na zimu", "Poraz 6 ovcí.", 6, "light", Activity.KILL, "SHEEP"),
            new Task("bone-pickup", "Kosti pro alchymistu", "Sebírej 12 kostí.", 12, "light", Activity.PICKUP, "BONE"),
            new Task("glass-craft", "Skleněné lahvičky", "Vyrob 16 skleněných bloků.", 16, "light", Activity.CRAFT, "GLASS"),
            new Task("salmon-fishing", "Lososí úlovek", "Ulov 5 lososů.", 5, "light", Activity.FISH, "SALMON"),

            // Další lehké úkoly pro pestřejší denní rotaci
            new Task("strays-light-extra", "Stíny zasněžených plání", "Poraz 4 stray.", 4, "light", Activity.KILL, "STRAY"),
            new Task("cod-fishing-light-extra", "Tresčí večeře", "Ulov 5 tresek.", 5, "light", Activity.FISH, "COD"),
            new Task("breed-sheep-light-extra", "Ovčí stádo", "Rozmnož 4 ovce.", 4, "light", Activity.BREED, "SHEEP"),
            new Task("torch-craft-light-extra", "Pochodně proti tmě", "Vyrob 24 pochodní.", 24, "light", Activity.CRAFT, "TORCH"),
            new Task("potatoes-light-extra", "Bramborová úroda", "Sklid 12 bloků brambor.", 12, "light", Activity.BLOCK, "POTATOES"),
            new Task("rotten-flesh-light-extra", "Zásoby alchymisty", "Sebírej 16 kusů shnilého masa.", 16, "light", Activity.PICKUP, "ROTTEN_FLESH"),
            new Task("rabbit-food-light-extra", "Zajíc na cestu", "Sněz 4 kusy králičího masa.", 4, "light", Activity.CONSUME, "RABBIT"),
            new Task("sugar-cane-light-extra", "Cukrová třtina", "Nasbírej 20 bloků cukrové třtiny.", 20, "light", Activity.BLOCK, "SUGAR_CANE"),

            // Střední kontrakt — cílenější boj a suroviny
            new Task("creepers", "Výbuch pod kontrolou", "Zabij 6 creeperů.", 6, "medium", Activity.KILL, "CREEPER"),
            new Task("endermen", "Pohled do prázdna", "Zabij 4 endermany.", 4, "medium", Activity.KILL, "ENDERMAN"),
            new Task("gold-ore", "Zlatá žíla", "Vytěž 8 zlatých rud.", 8, "medium", Activity.BLOCK, "GOLD_ORE_GROUP"),
            new Task("redstone-ore", "Rudý signál", "Vytěž 10 rud redstonu.", 10, "medium", Activity.BLOCK, "REDSTONE_ORE_GROUP"),
            new Task("diamond-ore", "Diamantový sen", "Vytěž 3 diamantové rudy.", 3, "medium", Activity.BLOCK, "DIAMOND_ORE_GROUP"),
            new Task("quartz-ore", "Netherový sběr", "Vytěž 20 netherových křemenných rud.", 20, "medium", Activity.BLOCK, "NETHER_QUARTZ_ORE"),
            new Task("blazes", "Oheň z Netheru", "Zabij 5 blaze.", 5, "medium", Activity.KILL, "BLAZE"),
            new Task("witches", "Čarodějnický problém", "Zabij 3 čarodějnice.", 3, "medium", Activity.KILL, "WITCH"),
            new Task("big-harvest", "Velká sklizeň", "Sklid 40 bloků plodin.", 40, "medium", Activity.BLOCK, "CROPS"),
            new Task("fishing-pro", "Hluboké vody", "Chyť 12 ryb.", 12, "medium", Activity.FISH, "ANY"),
            new Task("torches", "Světlo proti stínům", "Vyrob 32 pochodní.", 32, "medium", Activity.CRAFT, "TORCH"),
            new Task("pearls", "Kroky endermana", "Sebírej 6 enderových perel.", 6, "medium", Activity.PICKUP, "ENDER_PEARL"),
            new Task("long-logs", "Zásoba stavebního dřeva", "Pokácej 48 kmenů nebo bloků dřeva.", 48, "medium", Activity.BLOCK, "LOGS"),
            new Task("breed-cows", "Chovatel za úplňku", "Rozmnož 6 krav.", 6, "medium", Activity.BREED, "COW"),
            new Task("lapis-ore", "Modrý prášek", "Vytěž 8 lapisových rud.", 8, "medium", Activity.BLOCK, "LAPIS_ORE_GROUP"),
            new Task("hostiles-20", "Čistka v okolí", "Zabij 20 nepřátelských mobů.", 20, "medium", Activity.HOSTILE_KILL, "HOSTILE_ANY"),
            new Task("drowned-medium", "Hlídka pod hladinou", "Zabij 10 utopenců.", 10, "medium", Activity.KILL, "DROWNED"),
            new Task("phantoms-medium", "Křídla temnoty", "Zabij 6 fantomů.", 6, "medium", Activity.KILL, "PHANTOM"),
            new Task("slime-balls", "Slizká alchymie", "Sebírej 16 slimeballů.", 16, "medium", Activity.PICKUP, "SLIME_BALL"),
            new Task("end-eye-craft", "Oko do Endu", "Vyrob 4 enderová oka.", 4, "medium", Activity.CRAFT, "ENDER_EYE"),
            new Task("cooked-cod", "Jídlo z oceánu", "Sněz 12 pečených tresek.", 12, "medium", Activity.CONSUME, "COOKED_COD"),

            // Další středně obtížné kontrakty
            new Task("slimes-medium-extra", "Sliz z bažin", "Poraz 10 slizů.", 10, "medium", Activity.KILL, "SLIME"),
            new Task("gold-ingots-medium-extra", "Zlaté zásoby", "Získej 12 zlatých ingotů jako předměty.", 12, "medium", Activity.PICKUP, "GOLD_INGOT"),
            new Task("shield-craft-medium-extra", "Štít proti stínům", "Vyrob štít.", 1, "medium", Activity.CRAFT, "SHIELD"),
            new Task("nether-wart-medium-extra", "Sklizeň z Netheru", "Nasbírej 20 bloků netherové bradavice.", 20, "medium", Activity.BLOCK, "NETHER_WART"),
            new Task("pufferfish-medium-extra", "Jedovatý úlovek", "Ulov 3 čtverzubce.", 3, "medium", Activity.FISH, "PUFFERFISH"),
            new Task("breed-goats-medium-extra", "Kozy pod měsícem", "Rozmnož 4 kozy.", 4, "medium", Activity.BREED, "GOAT"),
            new Task("arrows-medium-extra", "Zbrojířova zásoba", "Vyrob 32 šípů.", 32, "medium", Activity.CRAFT, "ARROW"),
            new Task("iron-ingots-medium-extra", "Těžký náklad", "Získej 32 železných ingotů jako předměty.", 32, "medium", Activity.PICKUP, "IRON_INGOT"),

            // Těžký kontrakt — Nether, End a nebezpeční nepřátelé
            new Task("ancient-debris", "Starověká relikvie", "Vytěž 2 bloky ancient debris.", 2, "hard", Activity.BLOCK, "ANCIENT_DEBRIS"),
            new Task("wither-skeletons", "Černé kosti", "Zabij 12 wither skeletonů.", 12, "hard", Activity.KILL, "WITHER_SKELETON"),
            new Task("blazes-hard", "Plameny pekla", "Zabij 15 blaze.", 15, "hard", Activity.KILL, "BLAZE"),
            new Task("endermen-hard", "Lovec Endu", "Zabij 12 endermanů.", 12, "hard", Activity.KILL, "ENDERMAN"),
            new Task("shulkers", "Živé schránky", "Zabij 8 shulkerů.", 8, "hard", Activity.KILL, "SHULKER"),
            new Task("elder-guardian", "Vládce hlubin", "Zabij elder guardiana.", 1, "hard", Activity.KILL, "ELDER_GUARDIAN"),
            new Task("magma-cubes", "Magma pod nohama", "Zabij 12 magma cubů.", 12, "hard", Activity.KILL, "MAGMA_CUBE"),
            new Task("pillagers", "Nájezdní hlídka", "Zabij 15 pillagerů.", 15, "hard", Activity.KILL, "PILLAGER"),
            new Task("obsidian", "Černá pevnost", "Vytěž 24 obsidiánu.", 24, "hard", Activity.BLOCK, "OBSIDIAN"),
            new Task("diamonds-hard", "Diamantová horečka", "Vytěž 12 diamantových rud.", 12, "hard", Activity.BLOCK, "DIAMOND_ORE_GROUP"),
            new Task("hostiles-40", "Lovec nestvůr", "Zabij 40 nepřátelských mobů.", 40, "hard", Activity.HOSTILE_KILL, "HOSTILE_ANY"),
            new Task("golden-apple-craft", "Zlatá medicína", "Vyrob zlaté jablko.", 1, "hard", Activity.CRAFT, "GOLDEN_APPLE"),
            new Task("totem-pickup", "Druhá šance", "Získej totem nesmrtelnosti jako kořist.", 1, "hard", Activity.PICKUP, "TOTEM_OF_UNDYING"),
            new Task("breed-animals", "Rozšíření stáda", "Rozmnož 20 zvířat.", 20, "hard", Activity.BREED, "ANY"),
            new Task("netherite-scrap", "Kov hlubokých hlubin", "Získej 3 netheritové šroty jako předměty.", 3, "hard", Activity.PICKUP, "NETHERITE_SCRAP"),
            new Task("cooked-food", "Výprava připravená na vše", "Sněz 20 kusů tepelně upraveného jídla.", 20, "hard", Activity.CONSUME, "COOKED_FOOD"),
            new Task("guardians-hard", "Strážci mořské pevnosti", "Zabij 15 guardianů.", 15, "hard", Activity.KILL, "GUARDIAN"),
            new Task("ghasts-hard", "Lov na ghasty", "Zabij 8 ghastů.", 8, "hard", Activity.KILL, "GHAST"),
            new Task("diamond-pickup", "Diamantová kořist", "Sebírej 8 diamantů.", 8, "hard", Activity.PICKUP, "DIAMOND"),
            new Task("ender-eyes-pickup", "Kompas do Endu", "Sebírej 12 enderových očí.", 12, "hard", Activity.PICKUP, "ENDER_EYE"),
            new Task("goats-hard", "Horské stádo", "Rozmnož 8 koz.", 8, "hard", Activity.BREED, "GOAT"),

            // Další těžké úkoly s rizikem Netheru a nájezdů
            new Task("hoglins-hard-extra", "Lov na hogliny", "Poraz 20 hoglinů.", 20, "hard", Activity.KILL, "HOGLIN"),
            new Task("zombified-piglins-hard-extra", "Zlato a nemrtví", "Poraz 25 zombifikovaných piglinů.", 25, "hard", Activity.KILL, "ZOMBIFIED_PIGLIN"),
            new Task("netherite-ingot-hard-extra", "Netheritový zisk", "Získej netheritový ingot jako předmět.", 1, "hard", Activity.PICKUP, "NETHERITE_INGOT"),
            new Task("golden-apples-hard-extra", "Zlatá léčba", "Sněz 2 zlatá jablka.", 2, "hard", Activity.CONSUME, "GOLDEN_APPLE"),
            new Task("end-crystals-hard-extra", "Nebezpečný experiment", "Vyrob 4 endové krystaly.", 4, "hard", Activity.CRAFT, "END_CRYSTAL"),
            new Task("ancient-debris-hard-extra", "Hluboká netheritová žíla", "Vytěž 6 ancient debris.", 6, "hard", Activity.BLOCK, "ANCIENT_DEBRIS"),
            new Task("ravagers-hard-extra", "Zastav ničitele", "Poraz 3 ravagery.", 3, "hard", Activity.KILL, "RAVAGER"),
            new Task("totems-hard-extra", "Dvě další šance", "Získej 2 totemy nesmrtelnosti.", 2, "hard", Activity.PICKUP, "TOTEM_OF_UNDYING"),

            // Velmi těžké kontrakty — drahé suroviny, velké počty a nebezpeční nepřátelé
            new Task("wither-skeletons-very-hard", "Černá legie", "Poraz 24 wither skeletonů.", 24, "very-hard", Activity.KILL, "WITHER_SKELETON"),
            new Task("ancient-debris-very-hard", "Pod lávovým mořem", "Vytěž 8 ancient debris.", 8, "very-hard", Activity.BLOCK, "ANCIENT_DEBRIS"),
            new Task("guardian-army-very-hard", "Očista oceánského chrámu", "Poraz 30 guardianů.", 30, "very-hard", Activity.KILL, "GUARDIAN"),
            new Task("ravagers-very-hard", "Zastav velký nájezd", "Poraz 5 ravagerů.", 5, "very-hard", Activity.KILL, "RAVAGER"),
            new Task("netherite-scrap-very-hard", "Šest kusů temného kovu", "Získej 6 netheritových šrotů.", 6, "very-hard", Activity.PICKUP, "NETHERITE_SCRAP"),
            new Task("shulkers-very-hard", "Lovci prázdnoty", "Poraz 16 shulkerů.", 16, "very-hard", Activity.KILL, "SHULKER"),
            new Task("ender-eyes-very-hard", "Cesta do pevnosti", "Získej 20 enderových očí.", 20, "very-hard", Activity.PICKUP, "ENDER_EYE"),
            new Task("obsidian-very-hard", "Černá hradba", "Vytěž 48 obsidiánu.", 48, "very-hard", Activity.BLOCK, "OBSIDIAN"),
            new Task("ender-pearls-very-hard", "Pytel prázdnoty", "Získej 48 enderových perel.", 48, "very-hard", Activity.PICKUP, "ENDER_PEARL"),
            new Task("ghasts-very-hard", "Křik v Netheru", "Poraz 12 ghastů.", 12, "very-hard", Activity.KILL, "GHAST"),
            new Task("end-crystals-very-hard", "Osudové krystaly", "Vyrob 8 endových krystalů.", 8, "very-hard", Activity.CRAFT, "END_CRYSTAL"),
            new Task("golden-apples-very-hard", "Zásoby na obléhání", "Sněz 6 zlatých jablek.", 6, "very-hard", Activity.CONSUME, "GOLDEN_APPLE"),
            new Task("magma-cubes-very-hard", "Vlna magmatu", "Poraz 30 magma cubů.", 30, "very-hard", Activity.KILL, "MAGMA_CUBE"),
            new Task("phantoms-very-hard", "Křídla bez spánku", "Poraz 20 fantomů.", 20, "very-hard", Activity.KILL, "PHANTOM"),
            new Task("breed-animals-very-hard", "Rozsáhlá farma", "Rozmnož 50 zvířat.", 50, "very-hard", Activity.BREED, "ANY"),
            new Task("netherite-ingots-very-hard", "Netheritová výprava", "Získej 3 netheritové ingoty.", 3, "very-hard", Activity.PICKUP, "NETHERITE_INGOT"),

            // Extrémní kontrakt — endgame cíle s nejlepšími odměnami
            new Task("warden", "Ztiš hlubokou tmu", "Poraz Wardena.", 1, "extreme", Activity.KILL, "WARDEN"),
            new Task("wither", "Konec Withera", "Poraz Withera.", 1, "extreme", Activity.KILL, "WITHER"),
            new Task("ender-dragon", "Pád draka", "Poraz Ender Dragona.", 1, "extreme", Activity.KILL, "ENDER_DRAGON"),
            new Task("elder-guardians-extreme", "Zkáza oceánské pevnosti", "Poraz 3 elder guardiany.", 3, "extreme", Activity.KILL, "ELDER_GUARDIAN"),
            new Task("ravagers", "Zastav nájezd", "Poraz 5 ravagerů.", 5, "extreme", Activity.KILL, "RAVAGER"),
            new Task("wither-skeletons-extreme", "Armáda černých kostí", "Poraz 25 wither skeletonů.", 25, "extreme", Activity.KILL, "WITHER_SKELETON"),
            new Task("ancient-debris-extreme", "Poklad pod lávou", "Vytěž 12 bloků ancient debris.", 12, "extreme", Activity.BLOCK, "ANCIENT_DEBRIS"),
            new Task("diamonds-extreme", "Diamantový magnát", "Vytěž 32 diamantových rud.", 32, "extreme", Activity.BLOCK, "DIAMOND_ORE_GROUP"),
            new Task("ghasts", "Lov na ghasty", "Poraz 15 ghastů.", 15, "extreme", Activity.KILL, "GHAST"),
            new Task("shulkers-extreme", "Invaze do Endu", "Poraz 20 shulkerů.", 20, "extreme", Activity.KILL, "SHULKER"),
            new Task("hostiles-extreme", "Jedna noc, šedesát nestvůr", "Poraz 60 nepřátelských mobů.", 60, "extreme", Activity.HOSTILE_KILL, "HOSTILE_ANY"),
            new Task("beacon", "Maják naděje", "Vyrob maják.", 1, "extreme", Activity.CRAFT, "BEACON"),
            new Task("nether-star", "Hvězda podsvětí", "Získej nether star jako předmět.", 1, "extreme", Activity.PICKUP, "NETHER_STAR"),
            new Task("obsidian-extreme", "Těžba pro pevnost", "Vytěž 64 bloků obsidiánu.", 64, "extreme", Activity.BLOCK, "OBSIDIAN"),
            new Task("blazes-extreme", "Pekelná sklizeň", "Poraz 32 blaze.", 32, "extreme", Activity.KILL, "BLAZE"),
            new Task("piglin-brutes", "Brutální směna", "Poraz 20 piglin brute.", 20, "extreme", Activity.KILL, "PIGLIN_BRUTE"),
            new Task("wither-double", "Dvojitá apokalypsa", "Poraz 2 Withery.", 2, "extreme", Activity.KILL, "WITHER"),
            new Task("ender-dragons-extreme", "Lovec draků", "Poraz 2 Ender Dragony.", 2, "extreme", Activity.KILL, "ENDER_DRAGON"),
            new Task("nether-stars-extreme", "Tři hvězdy podsvětí", "Sebírej 3 Nether Star.", 3, "extreme", Activity.PICKUP, "NETHER_STAR"),
            new Task("shulkers-army", "Prázdnota plná schránek", "Poraz 32 shulkerů.", 32, "extreme", Activity.KILL, "SHULKER"),
            new Task("wardens-extreme", "Králové hlubiny", "Poraz 3 Wardeny.", 3, "extreme", Activity.KILL, "WARDEN"),
            new Task("withers-extreme-extra", "Tři pády Withera", "Poraz 3 Withery.", 3, "extreme", Activity.KILL, "WITHER"),
            new Task("dragons-extreme-extra", "Trojitý lov draků", "Poraz 3 Ender Dragony.", 3, "extreme", Activity.KILL, "ENDER_DRAGON"),
            new Task("wardens-extreme-extra", "Pět králů hlubiny", "Poraz 5 Wardenů.", 5, "extreme", Activity.KILL, "WARDEN"),
            new Task("elder-guardians-extreme-extra", "Zkáza oceánského chrámu", "Poraz 6 elder guardianů.", 6, "extreme", Activity.KILL, "ELDER_GUARDIAN"),
            new Task("ancient-debris-extreme-extra", "Netheritový poklad", "Vytěž 64 ancient debris.", 64, "extreme", Activity.BLOCK, "ANCIENT_DEBRIS"),
            new Task("shulkers-extreme-extra", "Padesát schránek", "Poraz 50 shulkerů.", 50, "extreme", Activity.KILL, "SHULKER"),
            new Task("nether-stars-extreme-extra", "Pět hvězd podsvětí", "Získej 5 nether star.", 5, "extreme", Activity.PICKUP, "NETHER_STAR"),
            new Task("beacons-extreme-extra", "Majáky na konci světa", "Vyrob 3 majáky.", 3, "extreme", Activity.CRAFT, "BEACON")
    );

    private static final Set<String> HOSTILE_MOBS = Set.of(
            "BLAZE", "BOGGED", "BREEZE", "CAVE_SPIDER", "CREEPER", "DROWNED", "ELDER_GUARDIAN",
            "ENDER_DRAGON", "ENDERMAN", "ENDERMITE", "EVOKER", "GHAST", "GUARDIAN", "HOGLIN",
            "HUSK", "MAGMA_CUBE", "PHANTOM", "PIGLIN", "PIGLIN_BRUTE", "PILLAGER", "RAVAGER",
            "SHULKER", "SILVERFISH", "SKELETON", "SLIME", "SPIDER", "STRAY", "VEX", "VINDICATOR",
            "WARDEN", "WITCH", "WITHER", "WITHER_SKELETON", "ZOGLIN", "ZOMBIE", "ZOMBIE_VILLAGER"
    );

    private static final Set<String> CROPS = Set.of(
            "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA",
            "MELON", "PUMPKIN", "SWEET_BERRY_BUSH", "TORCHFLOWER_CROP", "PITCHER_CROP"
    );
    private static final Set<String> FOOD = Set.of(
            "APPLE", "BAKED_POTATO", "BEEF", "BEETROOT", "BEETROOT_SOUP", "BREAD", "CARROT",
            "CHICKEN", "CHORUS_FRUIT", "COD", "COOKED_BEEF", "COOKED_CHICKEN", "COOKED_COD",
            "COOKED_MUTTON", "COOKED_PORKCHOP", "COOKED_RABBIT", "COOKED_SALMON", "COOKIE",
            "DRIED_KELP", "ENCHANTED_GOLDEN_APPLE", "GLOW_BERRIES", "GOLDEN_APPLE", "GOLDEN_CARROT",
            "HONEY_BOTTLE", "MELON_SLICE", "MUSHROOM_STEW", "MUTTON", "PORKCHOP", "POTATO",
            "PUMPKIN_PIE", "RABBIT", "RABBIT_STEW", "ROTTEN_FLESH", "SALMON", "SUSPICIOUS_STEW",
            "SWEET_BERRIES", "TROPICAL_FISH"
    );
    private static final Set<String> COOKED_FOOD = Set.of(
            "BAKED_POTATO", "BEETROOT_SOUP", "COOKED_BEEF", "COOKED_CHICKEN", "COOKED_COD",
            "COOKED_MUTTON", "COOKED_PORKCHOP", "COOKED_RABBIT", "COOKED_SALMON", "MUSHROOM_STEW",
            "PUMPKIN_PIE", "RABBIT_STEW"
    );

    private final HalloweenCore plugin;
    private final Random random = new Random();

    public DailyQuestManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Příkaz /quest může použít jen hráč.");
            return true;
        }
        if (!plugin.isSeasonActive()) {
            player.sendMessage(ChatColor.RED + "Halloween sezóna právě není aktivní.");
            return true;
        }
        if (!plugin.getConfig().getBoolean("daily-quest.enabled", true)) {
            player.sendMessage(ChatColor.RED + "Denní kontrakty jsou vypnuté.");
            return true;
        }
        ensureAssignments(player, true);
        String view = args.length == 0 ? "all" : args[0].toLowerCase(Locale.ROOT);
        if (view.equals("daily")) show(player, true, false);
        else if (view.equals("weekly")) show(player, false, true);
        else show(player);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isSeasonActive() || !plugin.getConfig().getBoolean("daily-quest.enabled", true)) return;
        Player player = event.getPlayer();
        if (ensureAssignments(player, true)) show(player);
    }

    private boolean ensureAssignments(Player player, boolean notify) {
        String today = today();
        String week = weekKey();
        boolean changed = false;
        Set<String> usedTasks = new HashSet<>();

        // Preserve every assignment for the current day/week before choosing missing ones;
        // this prevents a reload or a player's first login from giving duplicate contracts.
        for (ContractSlot slot : SLOTS) {
            String key = slot.weekly() ? week : today;
            String path = path(player.getUniqueId(), slot.suffix());
            if (key.equals(plugin.getPlayerData().getString(path + ".date", ""))) {
                String id = plugin.getPlayerData().getString(path + ".id", "");
                if (!id.isBlank()) usedTasks.add(id);
            }
        }

        for (ContractSlot slot : SLOTS) {
            String key = slot.weekly() ? week : today;
            String path = path(player.getUniqueId(), slot.suffix());
            if (key.equals(plugin.getPlayerData().getString(path + ".date", ""))) {
                // Migrate a contract saved by an older release without resetting its progress.
                if (!plugin.getPlayerData().contains(path + ".difficulty")) {
                    plugin.getPlayerData().set(path + ".difficulty", slot.difficulty());
                    int legacyReward = Math.max(0, plugin.getConfig().getInt("daily-quest.reward-fragments", 1));
                    plugin.getPlayerData().set(path + ".reward", legacyReward);
                    changed = true;
                }
                continue;
            }
            assign(player, path, key, slot, usedTasks);
            changed = true;
        }
        if (changed) {
            plugin.savePlayerData();
            if (notify) {
                player.sendMessage(ChatColor.DARK_PURPLE + "☾ " + ChatColor.GOLD
                        + "Připraveno 15 denních kontraktů a 3 týdenní výzvy.");
            }
        }
        return changed;
    }

    private void assign(Player player, String path, String periodKey, ContractSlot slot, Set<String> usedTasks) {
        String previousId = plugin.getPlayerData().getString(path + ".id", "");
        List<Task> choices = TASKS.stream()
                .filter(task -> task.difficulty().equals(slot.difficulty()))
                .filter(task -> !task.id().equals(previousId))
                .filter(task -> !usedTasks.contains(task.id()))
                .toList();
        if (choices.isEmpty()) {
            choices = TASKS.stream()
                    .filter(task -> task.difficulty().equals(slot.difficulty()))
                    .filter(task -> !usedTasks.contains(task.id()))
                    .toList();
        }
        if (choices.isEmpty()) {
            choices = TASKS.stream().filter(task -> !usedTasks.contains(task.id())).toList();
        }
        if (choices.isEmpty()) choices = TASKS;
        Task task = choices.get(random.nextInt(choices.size()));
        usedTasks.add(task.id());
        int reward = rollReward(task, slot.weekly());
        plugin.getPlayerData().set(path + ".date", periodKey);
        plugin.getPlayerData().set(path + ".id", task.id());
        plugin.getPlayerData().set(path + ".title", task.title());
        plugin.getPlayerData().set(path + ".description", task.description());
        plugin.getPlayerData().set(path + ".target", task.target());
        plugin.getPlayerData().set(path + ".progress", 0);
        plugin.getPlayerData().set(path + ".completed", false);
        plugin.getPlayerData().set(path + ".difficulty", task.difficulty());
        plugin.getPlayerData().set(path + ".reward", reward);
    }

    private int rollReward(Task task, boolean weekly) {
        String override = "quest-rewards.quests." + task.id();
        String tier = "quest-rewards.difficulties." + task.difficulty();
        int fallbackMin = switch (task.difficulty()) {
            case "light" -> 1;
            case "medium" -> 3;
            case "hard" -> 6;
            case "very-hard" -> 10;
            case "extreme" -> 16;
            default -> 1;
        };
        int fallbackMax = switch (task.difficulty()) {
            case "light" -> 2;
            case "medium" -> 5;
            case "hard" -> 9;
            case "very-hard" -> 15;
            case "extreme" -> 24;
            default -> fallbackMin + 1;
        };
        int min = plugin.getConfig().getInt(override + ".min-fragments",
                plugin.getConfig().getInt(tier + ".min-fragments", fallbackMin));
        int max = plugin.getConfig().getInt(override + ".max-fragments",
                plugin.getConfig().getInt(tier + ".max-fragments", fallbackMax));
        min = Math.max(0, min);
        max = Math.max(min, max);
        int reward = min + random.nextInt(max - min + 1);
        if (weekly) {
            double multiplier = Math.max(1.0, plugin.getConfig().getDouble("quest-rewards.weekly-multiplier", 3.0));
            reward = Math.max(1, (int) Math.round(reward * multiplier));
        }
        return reward;
    }

    private void show(Player player) {
        show(player, true, true);
    }

    private void show(Player player, boolean includeDaily, boolean includeWeekly) {
        player.sendMessage(ChatColor.DARK_PURPLE + "╔════════ H A L L O W E E N ════════╗");
        if (includeDaily) {
            player.sendMessage(ChatColor.GOLD + "☾ DENNÍ KONTRAKTY (15) " + ChatColor.DARK_GRAY + "(/quest daily)");
            for (ContractSlot slot : SLOTS) {
                if (!slot.weekly()) showContract(player, slot);
            }
        }
        if (includeWeekly) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ TÝDENNÍ VÝZVY (3) " + ChatColor.DARK_GRAY + "(/quest weekly)");
            for (ContractSlot slot : SLOTS) {
                if (slot.weekly()) showContract(player, slot);
            }
        }
        player.sendMessage(ChatColor.DARK_PURPLE + "╚══════════════════════════════════╝");
    }

    private void showContract(Player player, ContractSlot slot) {
        String path = path(player.getUniqueId(), slot.suffix());
        String title = plugin.getPlayerData().getString(path + ".title", "Halloween úkol");
        int progress = Math.max(0, plugin.getPlayerData().getInt(path + ".progress", 0));
        int target = Math.max(1, plugin.getPlayerData().getInt(path + ".target", 1));
        int reward = Math.max(0, plugin.getPlayerData().getInt(path + ".reward",
                plugin.getConfig().getInt("daily-quest.reward-fragments", 1)));
        boolean completed = plugin.getPlayerData().getBoolean(path + ".completed", false);
        ChatColor color = switch (slot.difficulty()) {
            case "light" -> ChatColor.GREEN;
            case "medium" -> ChatColor.YELLOW;
            case "hard" -> ChatColor.GOLD;
            case "very-hard" -> ChatColor.RED;
            default -> ChatColor.DARK_RED;
        };
        player.sendMessage(color + "[" + slot.label() + "] " + ChatColor.YELLOW + title
                + (completed ? ChatColor.GREEN + " ✓" : ""));
        player.sendMessage(ChatColor.GRAY + plugin.getPlayerData().getString(path + ".description", ""));
        player.sendMessage(ChatColor.DARK_GRAY + "  Postup: " + ChatColor.GOLD
                + Math.min(progress, target) + ChatColor.GRAY + "/" + ChatColor.GOLD + target
                + ChatColor.DARK_GRAY + " | Odměna: " + ChatColor.LIGHT_PURPLE
                + reward + " fragmentů");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKilled(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntityType() == EntityType.PLAYER) return;
        String type = event.getEntityType().name();
        List<ActivityCount> counts = new ArrayList<>();
        counts.add(new ActivityCount(Activity.KILL, type, 1));
        if (HOSTILE_MOBS.contains(type)) counts.add(new ActivityCount(Activity.HOSTILE_KILL, type, 1));
        track(killer, counts);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        String type = event.getBlock().getType().name();
        if (CROPS.contains(type) && event.getBlock().getBlockData() instanceof Ageable ageable
                && ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        track(event.getPlayer(), List.of(new ActivityCount(Activity.BLOCK, type, 1)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) return;
        track(player, List.of(new ActivityCount(Activity.CRAFT, result.getType().name(),
                Math.max(1, result.getAmount()))));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        track(event.getPlayer(), List.of(new ActivityCount(Activity.CONSUME, event.getItem().getType().name(), 1)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Entity caught = event.getCaught();
        if (!(caught instanceof Item item)) return;
        track(event.getPlayer(), List.of(new ActivityCount(Activity.FISH, item.getItemStack().getType().name(), 1)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        track(player, List.of(new ActivityCount(Activity.PICKUP, event.getItem().getItemStack().getType().name(),
                Math.max(1, event.getItem().getItemStack().getAmount()))));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        track(player, List.of(new ActivityCount(Activity.BREED, event.getEntity().getType().name(), 1)));
    }

    private void track(Player player, List<ActivityCount> actions) {
        if (!plugin.isSeasonActive() || !plugin.getConfig().getBoolean("daily-quest.enabled", true)) return;
        // On the first action after midnight, issue new contracts but never count that same action.
        if (ensureAssignments(player, true)) {
            show(player);
            return;
        }

        boolean changed = false;
        for (ContractSlot slot : SLOTS) {
            String path = path(player.getUniqueId(), slot.suffix());
            if (plugin.getPlayerData().getBoolean(path + ".completed", false)) continue;
            String id = plugin.getPlayerData().getString(path + ".id", "");
            Task task = taskById(id);
            if (task == null || !matches(task, actions)) continue;

            int target = Math.max(1, plugin.getPlayerData().getInt(path + ".target", task.target()));
            int progress = Math.max(0, plugin.getPlayerData().getInt(path + ".progress", 0));
            int increase = 0;
            for (ActivityCount action : actions) {
                if (matches(task, action)) increase = Math.max(increase, action.amount());
            }
            if (increase <= 0) continue;
            progress = (int) Math.min((long) target, (long) progress + increase);
            plugin.getPlayerData().set(path + ".progress", progress);
            changed = true;

            if (progress >= target) {
                plugin.getPlayerData().set(path + ".completed", true);
                long reward = Math.max(0L, plugin.getPlayerData().getLong(path + ".reward",
                        plugin.getConfig().getLong("daily-quest.reward-fragments", 1)));
                addFragments(player, reward);
                player.sendMessage(ChatColor.GREEN + "Splnil jsi kontrakt " + ChatColor.GOLD
                        + task.title() + ChatColor.GREEN + "! +" + reward + " fragmentů.");
            } else {
                int milestone = Math.max(1, target / 4);
                if (progress == 1 || progress % milestone == 0) {
                    player.sendMessage(ChatColor.GRAY + "Kontrakt " + ChatColor.YELLOW + task.title()
                            + ChatColor.GRAY + ": " + ChatColor.GOLD + progress + "/" + target);
                }
            }
        }
        if (changed && allDailyContractsComplete(player)) {
            String bonusPath = "players." + player.getUniqueId() + ".daily-quests.bonus-date";
            String today = today();
            if (!today.equals(plugin.getPlayerData().getString(bonusPath, ""))) {
                long bonus = Math.max(0L, plugin.getConfig().getLong("quest-rewards.daily-completion-bonus", 15L));
                addFragments(player, bonus);
                plugin.getPlayerData().set(bonusPath, today);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Všech 15 denních kontraktů splněno! Bonus: +" + bonus + " fragmentů.");
            }
        }
        if (changed && allWeeklyContractsComplete(player)) {
            String bonusPath = "players." + player.getUniqueId() + ".weekly-quests.bonus-week";
            String week = weekKey();
            if (!week.equals(plugin.getPlayerData().getString(bonusPath, ""))) {
                long bonus = Math.max(0L, plugin.getConfig().getLong("quest-rewards.weekly-completion-bonus", 50L));
                addFragments(player, bonus);
                plugin.getPlayerData().set(bonusPath, week);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Všechny 3 týdenní výzvy splněny! Bonus: +" + bonus + " fragmentů.");
            }
        }
        if (changed) plugin.savePlayerData();
    }

    private boolean allDailyContractsComplete(Player player) {
        String today = today();
        for (ContractSlot slot : SLOTS) {
            if (slot.weekly()) continue;
            String path = path(player.getUniqueId(), slot.suffix());
            if (!today.equals(plugin.getPlayerData().getString(path + ".date", ""))
                    || !plugin.getPlayerData().getBoolean(path + ".completed", false)) return false;
        }
        return true;
    }

    private boolean allWeeklyContractsComplete(Player player) {
        String week = weekKey();
        for (ContractSlot slot : SLOTS) {
            if (!slot.weekly()) continue;
            String path = path(player.getUniqueId(), slot.suffix());
            if (!week.equals(plugin.getPlayerData().getString(path + ".date", ""))
                    || !plugin.getPlayerData().getBoolean(path + ".completed", false)) return false;
        }
        return true;
    }

    private boolean matches(Task task, List<ActivityCount> actions) {
        for (ActivityCount action : actions) if (matches(task, action)) return true;
        return false;
    }

    private boolean matches(Task task, ActivityCount action) {
        if (task.activity() != action.activity()) return false;
        String expected = task.targetKey();
        String actual = action.target();
        if ("ANY".equals(expected)) return true;
        if ("HOSTILE_ANY".equals(expected)) return action.activity() == Activity.HOSTILE_KILL;
        if (expected.equals(actual)) return true;
        if (expected.endsWith("_GROUP") || expected.equals("LOGS")) {
            return switch (expected) {
                case "IRON_ORE_GROUP" -> actual.equals("IRON_ORE") || actual.equals("DEEPSLATE_IRON_ORE");
                case "COAL_ORE_GROUP" -> actual.equals("COAL_ORE") || actual.equals("DEEPSLATE_COAL_ORE");
                case "GOLD_ORE_GROUP" -> actual.equals("GOLD_ORE") || actual.equals("DEEPSLATE_GOLD_ORE");
                case "REDSTONE_ORE_GROUP" -> actual.equals("REDSTONE_ORE") || actual.equals("DEEPSLATE_REDSTONE_ORE");
                case "DIAMOND_ORE_GROUP" -> actual.equals("DIAMOND_ORE") || actual.equals("DEEPSLATE_DIAMOND_ORE");
                case "LAPIS_ORE_GROUP" -> actual.equals("LAPIS_ORE") || actual.equals("DEEPSLATE_LAPIS_ORE");
                case "LOGS" -> actual.endsWith("_LOG") || actual.endsWith("_WOOD")
                        || actual.endsWith("_STEM") || actual.equals("BAMBOO_BLOCK");
                case "STONE_GROUP" -> actual.equals("STONE") || actual.equals("DEEPSLATE")
                        || actual.equals("COBBLED_DEEPSLATE") || actual.equals("COBBLESTONE")
                        || actual.equals("TUFF") || actual.equals("ANDESITE") || actual.equals("DIORITE")
                        || actual.equals("GRANITE");
                default -> false;
            };
        }
        if ("PUMPKINS".equals(expected)) return actual.equals("PUMPKIN") || actual.equals("CARVED_PUMPKIN");
        if ("CROPS".equals(expected)) return CROPS.contains(actual);
        if ("FOOD".equals(expected)) return FOOD.contains(actual);
        if ("COOKED_FOOD".equals(expected)) return COOKED_FOOD.contains(actual);
        return false;
    }

    private Task taskById(String id) {
        for (Task task : TASKS) if (task.id().equals(id)) return task;
        return null;
    }

    private void addFragments(Player player, long reward) {
        String balancePath = "players." + player.getUniqueId() + ".fragments";
        long balance = Math.max(0L, plugin.getPlayerData().getLong(balancePath, 0));
        plugin.getPlayerData().set(balancePath, balance > Long.MAX_VALUE - reward ? Long.MAX_VALUE : balance + reward);
    }

    private String path(UUID uuid, String suffix) {
        return "players." + uuid + "." + suffix;
    }

    private String today() {
        return LocalDate.now(ZoneId.of("Europe/Prague")).toString();
    }

    private String weekKey() {
        LocalDate now = LocalDate.now(ZoneId.of("Europe/Prague"));
        return now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
