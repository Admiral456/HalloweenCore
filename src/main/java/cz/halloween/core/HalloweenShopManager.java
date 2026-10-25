package cz.halloween.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

public final class HalloweenShopManager implements Listener {
    private static final String TITLE = ChatColor.DARK_PURPLE + "☾ " + ChatColor.GOLD + "Halloweenský obchod";
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final HalloweenCore plugin;
    private List<Offer> catalogue;

    private record Offer(Material material, int amount, int price, String priceKey,
                        String name, String description, String iaId, boolean gear, boolean available) {}

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Offer> offers = new HashMap<>();
        private int page;
        @Override public Inventory getInventory() { return inventory; }
    }

    public HalloweenShopManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.catalogue = buildCatalogue();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void refresh() {
        catalogue = buildCatalogue();
    }

    public void open(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Obchod může otevřít jen hráč.");
            return;
        }
        if (!plugin.isSeasonActive()) {
            player.sendMessage(ChatColor.RED + "Halloween sezóna právě není aktivní.");
            return;
        }
        if (!plugin.getConfig().getBoolean("shop.enabled", true)) {
            player.sendMessage(ChatColor.RED + "Obchod je vypnutý.");
            return;
        }
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 54, TITLE);
        render(holder, player);
        player.openInventory(holder.inventory);
    }

    private List<Offer> buildCatalogue() {
        List<Offer> offers = new ArrayList<>();

        // 13 existing Halloween candies — their textures stay owned by the existing ItemsAdder pack.
        offers.add(candy("stev_apple_core", "Jablečná jadérka", 3));
        offers.add(candy("stev_candy_apple", "Karamelové jablko", 5));
        offers.add(candy("stev_candy_bag", "Pytlík sladkostí", 6));
        offers.add(candy("stev_caramel", "Karamel", 3));
        offers.add(candy("stev_caramel_apple", "Jablko v karamelu", 5));
        offers.add(candy("stev_chocolate_bar", "Čokoládová tyčinka", 4));
        offers.add(candy("stev_frosted_sugar_cookie", "Zdobená sušenka", 4));
        offers.add(candy("stev_gumdrop", "Želé bonbon", 3));
        offers.add(candy("stev_hard_candy", "Tvrdý bonbon", 3));
        offers.add(candy("stev_lollipop", "Lízátko", 4));
        offers.add(candy("stev_peanut_butter_cup", "Arašídový košíček", 4));
        offers.add(candy("stev_sugar_cookie", "Cukrová sušenka", 3));
        offers.add(candy("stev_toasted_pumpkin_seeds", "Pražená dýňová semínka", 3));

        // Everyday survival resources.
        offers.add(vanilla("COAL", 32, "coal", 10, "Uhlí (32)", "Palivo do pecí.", false));
        offers.add(vanilla("COPPER_INGOT", 16, "copper", 12, "Měděné ingoty (16)", "Základní surovina.", false));
        offers.add(vanilla("RAW_IRON", 16, "raw-iron", 15, "Surové železo (16)", "K vytavení.", false));
        offers.add(vanilla("IRON_INGOT", 16, "iron", 18, "Železné ingoty (16)", "Balení základního kovu.", false));
        offers.add(vanilla("RAW_GOLD", 8, "raw-gold", 14, "Surové zlato (8)", "K vytavení.", false));
        offers.add(vanilla("GOLD_INGOT", 8, "gold", 20, "Zlaté ingoty (8)", "Balení vzácnějšího kovu.", false));
        offers.add(vanilla("REDSTONE", 32, "redstone", 15, "Redstone (32)", "Na obvody a mechanismy.", false));
        offers.add(vanilla("LAPIS_LAZULI", 32, "lapis", 15, "Lapis lazuli (32)", "Na očarování.", false));
        offers.add(vanilla("QUARTZ", 16, "quartz", 18, "Netherový křemen (16)", "Z Netheru.", false));
        offers.add(vanilla("EMERALD", 4, "emerald", 30, "Smaragdy (4)", "Užitečné při obchodování.", false));
        offers.add(vanilla("DIAMOND", 1, "diamond", 45, "Diamant", "Jednotlivý diamant.", false));
        offers.add(vanilla("NETHERITE_SCRAP", 1, "netherite-scrap", 100, "Netheritový šrot", "Vzácný materiál z Netheru.", false));
        offers.add(vanilla("NETHERITE_INGOT", 1, "netherite-ingot", 350, "Netheritový ingot", "Prémiový materiál.", false));
        offers.add(vanilla("AMETHYST_SHARD", 16, "amethyst-shard", 30, "Ametystové úlomky (16)", "Dekorace a kalibrované senzory.", false));
        offers.add(vanilla("PRISMARINE_SHARD", 16, "prismarine-shard", 25, "Prismarinové úlomky (16)", "Kořist z oceánských památek.", false));
        offers.add(vanilla("PRISMARINE_CRYSTALS", 16, "prismarine-crystals", 40, "Prismarinové krystaly (16)", "Vzácnější podmořský materiál.", false));
        offers.add(vanilla("SLIME_BALL", 16, "slime-ball", 30, "Slizové kuličky (16)", "Pro lepivé redstone mechanismy.", false));
        offers.add(vanilla("MAGMA_CREAM", 8, "magma-cream", 50, "Magma krém (8)", "Surovina pro lektvary.", false));
        offers.add(vanilla("PHANTOM_MEMBRANE", 4, "phantom-membrane", 110, "Fantómové membrány (4)", "Na opravu Elytry.", false));
        offers.add(vanilla("GHAST_TEAR", 2, "ghast-tear", 130, "Ghastovy slzy (2)", "Vzácná alchymistická surovina.", false));
        offers.add(vanilla("SHULKER_SHELL", 1, "shulker-shell", 125, "Shulkerova schránka", "Základ pro shulker box.", false));
        offers.add(vanilla("TURTLE_SCUTE", 2, "turtle-scute", 150, "Želví štítky (2)", "Materiál na želví helmu.", false));
        offers.add(vanilla("NAUTILUS_SHELL", 1, "nautilus-shell", 90, "Nautilova mušle", "Materiál pro srdce moře.", false));
        offers.add(vanilla("HEART_OF_THE_SEA", 1, "heart-of-the-sea", 250, "Srdce moře", "Vzácný poklad z oceánu.", false));
        offers.add(vanilla("CRYING_OBSIDIAN", 8, "crying-obsidian", 45, "Plačící obsidián (8)", "Temný dekorační blok.", false));
        offers.add(vanilla("BLAZE_POWDER", 16, "blaze-powder", 45, "Blaze prášek (16)", "Na lektvary a enderová oka.", false));
        offers.add(vanilla("ENDER_EYE", 8, "ender-eye", 120, "Enderova oka (8)", "Pomůcka na hledání pevnosti.", false));
        offers.add(vanilla("DRAGON_BREATH", 2, "dragon-breath", 100, "Dračí dech (2)", "Vzácná alchymistická ingredience.", false));
        offers.add(vanilla("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 1, "netherite-template", 1100,
                "Netheritová vylepšovací šablona", "Prémiový předmět pro postup na netherit.", true));

        // Building and redstone supplies.
        offers.add(vanilla("COBBLESTONE", 64, "cobblestone", 10, "Kámen (64)", "Základní stavební balík.", false));
        offers.add(vanilla("STONE", 64, "stone", 12, "Opracovaný kámen (64)", "Stavební balík.", false));
        offers.add(vanilla("DEEPSLATE", 64, "deepslate", 16, "Břidlice (64)", "Temná stavební varianta.", false));
        offers.add(vanilla("OAK_LOG", 32, "oak-logs", 15, "Dubové kmeny (32)", "Stavební dřevo.", false));
        offers.add(vanilla("GLASS", 32, "glass", 15, "Sklo (32)", "Na okna a dekorace.", false));
        offers.add(vanilla("OBSIDIAN", 8, "obsidian", 35, "Obsidián (8)", "Odolný blok.", false));
        offers.add(vanilla("QUARTZ_BLOCK", 16, "quartz-block", 30, "Křemenné bloky (16)", "Prémiový stavební materiál.", false));
        offers.add(vanilla("GLOWSTONE", 16, "glowstone", 25, "Glowstone (16)", "Svítící bloky.", false));
        offers.add(vanilla("SLIME_BLOCK", 8, "slime-block", 40, "Slime bloky (8)", "Pro redstone mechanismy.", false));
        offers.add(vanilla("TNT", 4, "tnt", 45, "TNT (4)", "Výbušniny — používej opatrně.", false));
        offers.add(vanilla("DARK_OAK_LOG", 32, "dark-oak-logs", 20, "Tmavé dubové kmeny (32)", "Stavební dřevo.", false));
        offers.add(vanilla("PRISMARINE_BRICKS", 32, "prismarine-bricks", 45, "Prismarinové cihly (32)", "Podmořský stavební materiál.", false));
        offers.add(vanilla("SEA_LANTERN", 16, "sea-lantern", 60, "Mořské lucerny (16)", "Silný zdroj světla.", false));
        offers.add(vanilla("TERRACOTTA", 32, "terracotta", 25, "Terakota (32)", "Dekorace a stavby.", false));
        offers.add(vanilla("ORANGE_CONCRETE", 32, "orange-concrete", 30, "Oranžový beton (32)", "Halloweenová stavební paleta.", false));
        offers.add(vanilla("LANTERN", 8, "lantern", 25, "Lucerny (8)", "Teplé osvětlení.", false));
        offers.add(vanilla("CAMPFIRE", 8, "campfire", 20, "Tábornické ohně (8)", "Atmosférická dekorace.", false));
         offers.add(vanilla("CHAIN", 16, "chain", 35, "Řetězy (16)", "Na lampy a dekorace.", false));
        offers.add(vanilla("DIRT", 64, "dirt", 8, "Hlína (64)", "Základní stavební materiál.", false));
        offers.add(vanilla("SAND", 64, "sand", 10, "Písek (64)", "Na sklo a stavby.", false));
        offers.add(vanilla("GRAVEL", 64, "gravel", 10, "Štěrk (64)", "Stavební a výrobní materiál.", false));
        offers.add(vanilla("NETHERRACK", 64, "netherrack", 12, "Netherrack (64)", "Stavební blok z Netheru.", false));
        offers.add(vanilla("SOUL_SAND", 32, "soul-sand", 20, "Písek duší (32)", "Temný materiál z Netheru.", false));
        offers.add(vanilla("SOUL_SOIL", 32, "soul-soil", 20, "Půda duší (32)", "Temný materiál z údolí duší.", false));
        offers.add(vanilla("BASALT", 32, "basalt", 18, "Čedič (32)", "Dekorační kámen z Netheru.", false));
        offers.add(vanilla("BLACKSTONE", 32, "blackstone", 18, "Černokámen (32)", "Temný stavební blok.", false));
        offers.add(vanilla("END_STONE", 32, "end-stone", 35, "Endový kámen (32)", "Vzácnější blok z Endu.", false));
        offers.add(vanilla("PURPUR_BLOCK", 16, "purpur-block", 45, "Purpurové bloky (16)", "Stavební materiál z Endu.", false));
        offers.add(vanilla("BRICKS", 32, "bricks", 20, "Cihly (32)", "Klasický stavební blok.", false));
        offers.add(vanilla("BOOKSHELF", 8, "bookshelf", 45, "Knihovny (8)", "Pro očarovací místnost.", false));
        offers.add(vanilla("ENCHANTING_TABLE", 1, "enchanting-table", 100, "Očarovací stůl", "Základ pro očarování výbavy.", false));
        offers.add(vanilla("BREWING_STAND", 1, "brewing-stand", 50, "Varný stojan", "Pro přípravu lektvarů.", false));
        offers.add(vanilla("HOPPER", 8, "hopper", 55, "Trychtýře (8)", "Pro automatické třídění předmětů.", false));
        offers.add(vanilla("DISPENSER", 8, "dispenser", 40, "Dávkovače (8)", "Pro redstone mechanismy.", false));
        offers.add(vanilla("DROPPER", 8, "dropper", 35, "Vhazovače (8)", "Pro redstone mechanismy.", false));
        offers.add(vanilla("PISTON", 8, "piston", 45, "Písty (8)", "Pro automatizaci.", false));
        offers.add(vanilla("STICKY_PISTON", 4, "sticky-piston", 75, "Lepivé písty (4)", "Pro pokročilé redstone stroje.", false));
        offers.add(vanilla("OBSERVER", 8, "observer", 55, "Pozorovatelé (8)", "Detekce změn bloků.", false));
        offers.add(vanilla("COMPARATOR", 8, "comparator", 35, "Komparátory (8)", "Pro redstone obvody.", false));
        offers.add(vanilla("REPEATER", 16, "repeater", 25, "Opakovače (16)", "Pro redstone obvody.", false));
        offers.add(vanilla("CHEST", 16, "chest", 15, "Truhly (16)", "Základní úložiště.", false));
        offers.add(vanilla("BARREL", 16, "barrel", 18, "Sudy (16)", "Úložiště do dílen a skladů.", false));
        offers.add(vanilla("MINECART", 1, "minecart", 65, "Vozík", "Pro důlní železnice.", false));
        offers.add(vanilla("RAIL", 32, "rail", 20, "Koleje (32)", "Základní železniční trať.", false));
        offers.add(vanilla("POWERED_RAIL", 16, "powered-rail", 55, "Poháněné koleje (16)", "Pro rychlou dopravu.", false));
        offers.add(vanilla("ARMOR_STAND", 4, "armor-stand", 25, "Stojany na zbroj (4)", "Na vystavení výbavy.", false));
        offers.add(vanilla("LEATHER", 16, "leather", 12, "Kůže (16)", "Na brnění a knihy.", false));
        offers.add(vanilla("LEATHER_CHESTPLATE", 1, "leather-chestplate", 35, "Kožená tunika", "Lehká základní zbroj.", true));
        offers.add(vanilla("TURTLE_HELMET", 1, "turtle-helmet", 160, "Želví krunýř", "Vzácná helma s vodní výhodou.", true));
        offers.add(vanilla("GOLDEN_HELMET", 1, "golden-helmet", 55, "Zlatá helma", "Základní zlatá zbroj.", true));
        offers.add(vanilla("GOLDEN_SWORD", 1, "golden-sword", 25, "Zlatý meč", "Lehká zbraň.", true));
        offers.add(vanilla("IRON_SWORD", 1, "iron-sword", 30, "Železný meč", "Spolehlivá zbraň.", true));
        offers.add(vanilla("IRON_AXE", 1, "iron-axe", 35, "Železná sekera", "Nástroj i zbraň.", true));
        offers.add(vanilla("BONE_BLOCK", 32, "bone-block", 35, "Kostěné bloky (32)", "Dekorační blok.", false));
        offers.add(vanilla("MOSS_BLOCK", 32, "moss-block", 30, "Mechové bloky (32)", "Přírodní dekorace.", false));
        offers.add(vanilla("SPONGE", 4, "sponge", 100, "Houby (4)", "Užitečné pro odvodnění.", false));
        offers.add(vanilla("SEA_PICKLE", 16, "sea-pickle", 30, "Mořské okurky (16)", "Podvodní dekorace.", false));
        offers.add(vanilla("FLOWER_POT", 16, "flower-pot", 20, "Květináče (16)", "Dekorace staveb.", false));
        offers.add(vanilla("PUMPKIN", 16, "pumpkin", 12, "Dýně (16)", "Halloweenová dekorace.", false));
        offers.add(vanilla("CARVED_PUMPKIN", 8, "carved-pumpkin", 20, "Vyřezávané dýně (8)", "Halloweenská dekorace.", false));
        offers.add(vanilla("JACK_O_LANTERN", 8, "jack-o-lantern", 30, "Dýňové lucerny (8)", "Dekorace a světlo.", false));
        offers.add(vanilla("HONEY_BLOCK", 8, "honey-block", 45, "Medové bloky (8)", "Lepivý stavební materiál.", false));
        offers.add(vanilla("TARGET", 8, "target", 35, "Terče (8)", "Pro střelbu a redstone.", false));
        offers.add(vanilla("REDSTONE_LAMP", 16, "redstone-lamp", 30, "Redstone lampy (16)", "Ovládatelné osvětlení.", false));
        offers.add(vanilla("REDSTONE_TORCH", 32, "redstone-torch", 20, "Redstone pochodně (32)", "Pro redstone obvody.", false));

        // Food and healing.
        offers.add(vanilla("BREAD", 16, "bread", 10, "Chléb (16)", "Základní jídlo na cesty.", false));
        offers.add(vanilla("COOKED_BEEF", 16, "cooked-beef", 18, "Steaky (16)", "Výživná zásoba.", false));
        offers.add(vanilla("BAKED_POTATO", 16, "baked-potato", 12, "Pečené brambory (16)", "Jednoduchá zásoba jídla.", false));
        offers.add(vanilla("GOLDEN_CARROT", 8, "golden-carrot", 25, "Zlaté mrkve (8)", "Kvalitní jídlo.", false));
        offers.add(vanilla("PUMPKIN_PIE", 8, "pumpkin-pie", 15, "Dýňové koláče (8)", "Halloweenová svačina.", false));
        offers.add(vanilla("HONEY_BOTTLE", 4, "honey-bottle", 18, "Medové lahve (4)", "Sladká zásoba.", false));
        offers.add(vanilla("GOLDEN_APPLE", 1, "golden-apple", 100, "Zlaté jablko", "Vzácná nouzová léčba.", false));
        offers.add(vanilla("ENCHANTED_GOLDEN_APPLE", 1, "enchanted-golden-apple", 1000,
                "Zakleté zlaté jablko", "Extrémně vzácná endgame odměna.", true));

        // Utility and exploration supplies.
        offers.add(vanilla("ENDER_PEARL", 4, "ender-pearl", 25, "Enderovy perly (4)", "Rychlý přesun.", false));
        offers.add(vanilla("BLAZE_ROD", 4, "blaze-rod", 35, "Blaze tyče (4)", "Důležitá surovina z Netheru.", false));
        offers.add(vanilla("ARROW", 32, "arrow", 12, "Šípy (32)", "Munice pro luky.", false));
        offers.add(vanilla("FIREWORK_ROCKET", 32, "firework-rocket", 25, "Ohňostrojné rakety (32)", "Pro létání s Elytrou.", false));
        offers.add(vanilla("EXPERIENCE_BOTTLE", 16, "experience-bottle", 40, "Lahvičky zkušeností (16)", "Zkušenosti na očarování.", false));
        offers.add(vanilla("ENDER_CHEST", 1, "ender-chest", 60, "Enderova truhla", "Osobní úložiště.", false));
        offers.add(vanilla("SHULKER_BOX", 1, "shulker-box", 200, "Shulker box", "Přenosné úložiště.", false));
        offers.add(vanilla("ANVIL", 1, "anvil", 45, "Kovadlina", "Na opravy a přejmenování.", false));
        offers.add(vanilla("NAME_TAG", 1, "name-tag", 90, "Jmenovka", "Pojmenuj svého moba.", false));
        offers.add(vanilla("SADDLE", 1, "saddle", 65, "Sedlo", "Pro jízdu na zvířatech.", false));
        offers.add(vanilla("WATER_BUCKET", 1, "water-bucket", 20, "Kbelík vody", "Pomůcka na cestování a stavbu.", false));
        offers.add(vanilla("LEAD", 4, "lead", 15, "Vodítka (4)", "Na přesun zvířat.", false));
        offers.add(vanilla("COMPASS", 1, "compass", 45, "Kompas", "Pomůcka na orientaci.", false));
        offers.add(vanilla("CLOCK", 1, "clock", 45, "Hodiny", "Sleduj den a noc.", false));
        offers.add(vanilla("RECOVERY_COMPASS", 1, "recovery-compass", 180, "Kompas obnovy", "Pomůže najít místo poslední smrti.", false));
        offers.add(vanilla("SPYGLASS", 1, "spyglass", 60, "Dalekohled", "Na průzkum dálky.", false));
        offers.add(vanilla("FISHING_ROD", 1, "fishing-rod", 50, "Rybářský prut", "Na lov ryb a pokladů.", true));
        offers.add(vanilla("SHEARS", 1, "shears", 35, "Nůžky", "Na vlnu a listí.", true));
        offers.add(vanilla("FLINT_AND_STEEL", 1, "flint-and-steel", 40, "Křesadlo", "Na oheň a portály.", true));
        offers.add(vanilla("DRAGON_HEAD", 1, "dragon-head", 450, "Dračí hlava", "Vzácná trofej z Endu.", true));
        offers.add(vanilla("END_CRYSTAL", 4, "end-crystal", 225, "Endové krystaly (4)", "Drahá a nebezpečná položka.", false));
        offers.add(vanilla("CONDUIT", 1, "conduit", 650, "Conduit", "Prémiový podmořský užitek.", true));
        offers.add(vanilla("TOTEM_OF_UNDYING", 1, "totem", 750, "Totem nesmrtelnosti", "Velmi drahá druhá šance.", true));
        offers.add(vanilla("BEACON", 1, "beacon", 1600, "Maják", "Endgame předmět za dlouhodobé šetření.", true));
        offers.add(vanilla("NETHER_STAR", 1, "nether-star", 900, "Nether star", "Vzácná trofej z Withera.", true));

        // Tools and combat gear.
        offers.add(vanilla("IRON_PICKAXE", 1, "iron-pickaxe", 35, "Železný krumpáč", "Spolehlivý začátek.", true));
        offers.add(vanilla("DIAMOND_SWORD", 1, "diamond-sword", 120, "Diamantový meč", "Silná zbraň.", true));
        offers.add(vanilla("DIAMOND_PICKAXE", 1, "diamond-pickaxe", 150, "Diamantový krumpáč", "Na cennou těžbu.", true));
        offers.add(vanilla("DIAMOND_AXE", 1, "diamond-axe", 120, "Diamantová sekera", "Nástroj i zbraň.", true));
        offers.add(vanilla("DIAMOND_SHOVEL", 1, "diamond-shovel", 85, "Diamantová lopata", "Rychlá práce se zeminou.", true));
        offers.add(vanilla("DIAMOND_HOE", 1, "diamond-hoe", 95, "Diamantová motyka", "Odolný zemědělský nástroj.", true));
        offers.add(vanilla("NETHERITE_SWORD", 1, "netherite-sword", 400, "Netheritový meč", "Prémiová zbraň.", true,
                "warriorland_halloween:orange_netherite_sword"));
        offers.add(vanilla("NETHERITE_AXE", 1, "netherite-axe", 360, "Netheritová sekera", "Prémiový nástroj i zbraň.", true,
                "warriorland_halloween:orange_netherite_axe"));
        offers.add(vanilla("NETHERITE_PICKAXE", 1, "netherite-pickaxe", 480, "Netheritový krumpáč", "Prémiový nástroj.", true));
        offers.add(vanilla("NETHERITE_SHOVEL", 1, "netherite-shovel", 280, "Netheritová lopata", "Prémiový nástroj.", true));
        offers.add(vanilla("NETHERITE_HOE", 1, "netherite-hoe", 300, "Netheritová motyka", "Prémiový nástroj.", true));
        offers.add(vanilla("BOW", 1, "bow", 80, "Luk", "Základní zbraň na dálku.", true));
        offers.add(vanilla("CROSSBOW", 1, "crossbow", 100, "Kuše", "Silná zbraň na dálku.", true));
        offers.add(vanilla("SHIELD", 1, "shield", 70, "Štít", "Obrana proti zásahům.", true));

        offers.add(orange("TRIDENT", "trident", 850, "Halloweenový trojzubec",
                "Oranžová zbraň z HalloweenCore.", "warriorland_halloween:orange_trident"));
        Material mace = Material.matchMaterial("MACE");
        offers.add(new Offer(mace == null ? Material.BARRIER : mace, 1, price("mace", 1400), "mace",
                "Halloweenový palcát", "Vzácná oranžová endgame zbraň.",
                "warriorland_halloween:orange_mace", true, mace != null));

        // Armor from iron through netherite.
        offers.add(vanilla("IRON_HELMET", 1, "iron-helmet", 35, "Železná helma", "Základní ochrana.", true));
        offers.add(vanilla("IRON_CHESTPLATE", 1, "iron-chestplate", 60, "Železný hrudník", "Základní ochrana.", true));
        offers.add(vanilla("IRON_LEGGINGS", 1, "iron-leggings", 50, "Železné kalhoty", "Základní ochrana.", true));
        offers.add(vanilla("IRON_BOOTS", 1, "iron-boots", 30, "Železné boty", "Základní ochrana.", true));
        offers.add(vanilla("DIAMOND_HELMET", 1, "diamond-helmet", 150, "Diamantová helma", "Silná ochrana.", true));
        offers.add(vanilla("DIAMOND_CHESTPLATE", 1, "diamond-chestplate", 250, "Diamantový hrudník", "Silná ochrana.", true));
        offers.add(vanilla("DIAMOND_LEGGINGS", 1, "diamond-leggings", 200, "Diamantové kalhoty", "Silná ochrana.", true));
        offers.add(vanilla("DIAMOND_BOOTS", 1, "diamond-boots", 120, "Diamantové boty", "Silná ochrana.", true));
        offers.add(vanilla("NETHERITE_HELMET", 1, "netherite-helmet", 300, "Netheritová helma", "Prémiová ochrana.", true));
        offers.add(vanilla("NETHERITE_CHESTPLATE", 1, "netherite-chestplate", 600, "Netheritový hrudník", "Velmi drahá prémiová ochrana.", true));
        offers.add(vanilla("NETHERITE_LEGGINGS", 1, "netherite-leggings", 480, "Netheritové kalhoty", "Prémiová ochrana.", true));
        offers.add(vanilla("NETHERITE_BOOTS", 1, "netherite-boots", 300, "Netheritové boty", "Prémiová ochrana.", true));
        offers.add(vanilla("ELYTRA", 1, "elytra", 2000, "Halloweenová Elytra", "Jedna z nejdražších položek celého obchodu.", true));
        offers.add(vanilla("GOLDEN_HORSE_ARMOR", 1, "golden-horse-armor", 100, "Zlatá koňská zbroj", "Výbava pro věrného oře.", true));
        offers.add(vanilla("DIAMOND_HORSE_ARMOR", 1, "diamond-horse-armor", 180, "Diamantová koňská zbroj", "Prémiová ochrana koně.", true));

        // Additional vanilla survival materials, Nether/End drops and building blocks.
        // All are ordinary Minecraft materials; if a material is unavailable on an older API,
        // it is displayed as unavailable rather than breaking plugin startup.
        offers.add(vanilla("AMETHYST_SHARD", 8, "amethyst-shard", 30, "Ametystové úlomky (8)", "Na dalekohledy a dekorace.", false));
        offers.add(vanilla("PRISMARINE_SHARD", 8, "prismarine-shard", 25, "Prismarinové úlomky (8)", "Kořist z oceánských monumentů.", false));
        offers.add(vanilla("PRISMARINE_CRYSTALS", 8, "prismarine-crystals", 40, "Prismarinové krystaly (8)", "Na výrobu mořských lamp.", false));
        offers.add(vanilla("SLIME_BALL", 8, "slime-ball", 30, "Slimeball (8)", "Užitečné na redstone mechanismy.", false));
        offers.add(vanilla("MAGMA_CREAM", 4, "magma-cream", 50, "Magma krém (4)", "Surovina z Netheru.", false));
        offers.add(vanilla("PHANTOM_MEMBRANE", 2, "phantom-membrane", 110, "Fantómová membrána (2)", "Vzácná noční kořist.", false));
        offers.add(vanilla("GHAST_TEAR", 1, "ghast-tear", 130, "Ghastí slza", "Vzácná alchymistická surovina.", false));
        offers.add(vanilla("SHULKER_SHELL", 1, "shulker-shell", 125, "Shulkerova schránka", "Základ přenosného úložiště.", false));
        offers.add(vanilla("TURTLE_SCUTE", 2, "turtle-scute", 150, "Želví štítky (2)", "Vzácná surovina z želv.", false));
        offers.add(vanilla("NAUTILUS_SHELL", 1, "nautilus-shell", 90, "Nautilova ulita", "Mořská relikvie.", false));
        offers.add(vanilla("HEART_OF_THE_SEA", 1, "heart-of-the-sea", 250, "Srdce moře", "Vzácný poklad z map.", true));
        offers.add(vanilla("CRYING_OBSIDIAN", 8, "crying-obsidian", 45, "Plačící obsidián (8)", "Vzácný blok z bastionů a portálů.", false));
        offers.add(vanilla("BLAZE_POWDER", 8, "blaze-powder", 45, "Blaze prášek (8)", "Na lektvary a End.", false));
        offers.add(vanilla("ENDER_EYE", 4, "ender-eye", 120, "Oči Enderu (4)", "Pomohou najít pevnost.", false));
        offers.add(vanilla("DRAGON_BREATH", 1, "dragon-breath", 100, "Dračí dech", "Vzácná surovina z Endu.", false));
        offers.add(vanilla("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 1, "netherite-template", 1100,
                "Netheritová kovací šablona", "Velmi vzácná prémiová šablona.", true));

        offers.add(vanilla("DARK_OAK_LOG", 32, "dark-oak-logs", 20, "Tmavé dubové kmeny (32)", "Tmavší stavební dřevo.", false));
        offers.add(vanilla("PRISMARINE_BRICKS", 32, "prismarine-bricks", 45, "Prismarinové cihly (32)", "Oceánské stavební bloky.", false));
        offers.add(vanilla("SEA_LANTERN", 8, "sea-lantern", 60, "Mořské lampy (8)", "Výrazné světelné bloky.", false));
        offers.add(vanilla("TERRACOTTA", 32, "terracotta", 25, "Terakota (32)", "Dekorační stavební materiál.", false));
        offers.add(vanilla("ORANGE_CONCRETE", 32, "orange-concrete", 30, "Oranžový beton (32)", "Halloweenská stavební paleta.", false));
        offers.add(vanilla("LANTERN", 8, "lantern", 25, "Lucerny (8)", "Dekorativní osvětlení.", false));
        offers.add(vanilla("CAMPFIRE", 4, "campfire", 20, "Tábornické ohně (4)", "Světlo a dekorace.", false));
        offers.add(vanilla("CHAIN", 16, "chain", 35, "Řetězy (16)", "Dekorace a industriální stavby.", false));

        offers.add(vanilla("COMPASS", 1, "compass", 45, "Kompas", "Ukazuje směr ke spawnu.", false));
        offers.add(vanilla("CLOCK", 1, "clock", 45, "Hodiny", "Pomohou odhadnout denní dobu.", false));
        offers.add(vanilla("RECOVERY_COMPASS", 1, "recovery-compass", 180, "Kompas návratu", "Vzácný navigační předmět.", true));
        offers.add(vanilla("SPYGLASS", 1, "spyglass", 60, "Dalekohled", "Prozkoumej okolí.", false));
        offers.add(vanilla("FISHING_ROD", 1, "fishing-rod", 50, "Rybářský prut", "Na rybaření.", false));
        offers.add(vanilla("SHEARS", 1, "shears", 35, "Nůžky", "Na vlnu, listí a další bloky.", false));
        offers.add(vanilla("FLINT_AND_STEEL", 1, "flint-and-steel", 40, "Křesadlo", "Zapálení ohně a portálů.", false));
        offers.add(vanilla("DRAGON_HEAD", 1, "dragon-head", 450, "Dračí hlava", "Vzácná dekorativní trofej.", true));
        offers.add(vanilla("END_CRYSTAL", 1, "end-crystal", 225, "Endový krystal", "Vzácný a nebezpečný předmět.", false));
        offers.add(vanilla("CONDUIT", 1, "conduit", 650, "Mořské srdce — conduit", "Prémiová pomůcka pro stavby pod vodou.", true));

        offers.add(vanilla("DIAMOND_SHOVEL", 1, "diamond-shovel", 85, "Diamantová lopata", "Rychlý nástroj.", true));
        offers.add(vanilla("DIAMOND_HOE", 1, "diamond-hoe", 95, "Diamantová motyka", "Prémiový zemědělský nástroj.", true));
        offers.add(vanilla("GOLDEN_HORSE_ARMOR", 1, "golden-horse-armor", 100, "Zlatá koňská zbroj", "Ochrana pro koně.", true));
        offers.add(vanilla("DIAMOND_HORSE_ARMOR", 1, "diamond-horse-armor", 180, "Diamantová koňská zbroj", "Prémiová ochrana pro koně.", true));

        // Additional vanilla survival stock: farming, food, storage, building and workstation blocks.
        offers.add(vanilla("RAW_COPPER", 16, "raw-copper", 10, "Surová měď (16)", "K vytavení měděných ingotů.", false));
        offers.add(vanilla("IRON_BLOCK", 1, "iron-block", 22, "Blok železa", "Kompaktní zásoba železa.", false));
        offers.add(vanilla("GOLD_BLOCK", 1, "gold-block", 30, "Blok zlata", "Kompaktní zásoba zlata.", false));
        offers.add(vanilla("DIAMOND_BLOCK", 1, "diamond-block", 405, "Blok diamantů", "Drahá zásoba devíti diamantů.", true));
        offers.add(vanilla("EMERALD_BLOCK", 1, "emerald-block", 80, "Blok smaragdů", "Pro bohaté obchodníky.", false));
        offers.add(vanilla("NETHERITE_BLOCK", 1, "netherite-block", 3150, "Blok netheritu", "Extrémně drahý endgame materiál.", true));
        offers.add(vanilla("WHEAT", 16, "wheat", 10, "Pšenice (16)", "Pro pečení a chov zvířat.", false));
        offers.add(vanilla("WHEAT_SEEDS", 32, "wheat-seeds", 5, "Semínka pšenice (32)", "Základní zemědělská zásoba.", false));
        offers.add(vanilla("BEETROOT_SEEDS", 16, "beetroot-seeds", 8, "Semínka červené řepy (16)", "Pro zakládání polí.", false));
        offers.add(vanilla("PUMPKIN_SEEDS", 16, "pumpkin-seeds", 10, "Dýňová semínka (16)", "Pro halloweenskou farmu.", false));
        offers.add(vanilla("MELON_SEEDS", 16, "melon-seeds", 10, "Melounová semínka (16)", "Pro pěstování melounů.", false));
        offers.add(vanilla("BONE_MEAL", 32, "bone-meal", 15, "Kostní moučka (32)", "Urychluje růst plodin a rostlin.", false));
        offers.add(vanilla("SUGAR_CANE", 32, "sugar-cane", 12, "Cukrová třtina (32)", "Pro papír a cukr.", false));
        offers.add(vanilla("CACTUS", 16, "cactus", 15, "Kaktusy (16)", "Použití v dekoracích a farmách.", false));
        offers.add(vanilla("BAMBOO", 32, "bamboo", 12, "Bambus (32)", "Pro stavění i palivo.", false));
        offers.add(vanilla("NETHER_WART", 16, "nether-wart", 25, "Netherové bradavice (16)", "Základ pro vaření lektvarů.", false));
        offers.add(vanilla("CARROT", 16, "carrot", 12, "Mrkev (16)", "Jídlo i chov zvířat.", false));
        offers.add(vanilla("POTATO", 16, "potato", 12, "Brambory (16)", "Zásoba k jídlu i sázení.", false));
        offers.add(vanilla("BEETROOT", 16, "beetroot", 12, "Červená řepa (16)", "Surovina na jídlo.", false));
        offers.add(vanilla("RED_MUSHROOM", 16, "red-mushroom", 15, "Červené houby (16)", "Pro farmy a recepty.", false));
        offers.add(vanilla("BROWN_MUSHROOM", 16, "brown-mushroom", 15, "Hnědé houby (16)", "Pro farmy a recepty.", false));
        offers.add(vanilla("OAK_SAPLING", 16, "oak-sapling", 10, "Dubové sazenice (16)", "Pro obnovu lesa.", false));
        offers.add(vanilla("SPRUCE_SAPLING", 16, "spruce-sapling", 10, "Smrkové sazenice (16)", "Pro obnovu lesa.", false));
        offers.add(vanilla("BIRCH_SAPLING", 16, "birch-sapling", 10, "Březové sazenice (16)", "Pro obnovu lesa.", false));
        offers.add(vanilla("CHERRY_SAPLING", 16, "cherry-sapling", 15, "Třešňové sazenice (16)", "Vzácnější dekorativní dřevo.", false));
        offers.add(vanilla("DARK_OAK_SAPLING", 16, "dark-oak-sapling", 12, "Sazenice tmavého dubu (16)", "Pro tmavé lesní stavby.", false));
        offers.add(vanilla("MANGROVE_PROPAGULE", 16, "mangrove-propagule", 15, "Mangrovové propagule (16)", "Pro pěstování mangrovů.", false));
        offers.add(vanilla("COOKED_CHICKEN", 16, "cooked-chicken", 20, "Pečené kuře (16)", "Připravené jídlo na cestu.", false));
        offers.add(vanilla("COOKED_PORKCHOP", 16, "cooked-porkchop", 22, "Pečené vepřové (16)", "Výživná zásoba jídla.", false));
        offers.add(vanilla("COOKIE", 16, "cookie", 8, "Sušenky (16)", "Levná sladká svačina.", false));
        offers.add(vanilla("CAKE", 1, "cake", 30, "Dort", "Slavnostní jídlo.", false));
        offers.add(vanilla("MILK_BUCKET", 1, "milk-bucket", 25, "Kbelík mléka", "Na výrobu jídla a odstranění efektů.", false));
        offers.add(vanilla("EGG", 16, "egg", 10, "Vejce (16)", "Na pečení a chov kuřat.", false));
        offers.add(vanilla("APPLE", 16, "apple", 12, "Jablka (16)", "Základní jídlo a chov koní.", false));
        offers.add(vanilla("DRIED_KELP", 32, "dried-kelp", 8, "Sušená chaluhа (32)", "Lehká zásoba jídla.", false));
        offers.add(vanilla("SWEET_BERRIES", 16, "sweet-berries", 10, "Sladké bobule (16)", "Jídlo a rozmnožování lišek.", false));
        offers.add(vanilla("PAPER", 16, "paper", 20, "Papír (16)", "Na mapy, rakety a knihy.", false));
        offers.add(vanilla("BOOK", 8, "book", 25, "Knihy (8)", "Na knihovny a očarování.", false));
        offers.add(vanilla("MAP", 1, "map", 35, "Prázdná mapa", "Na vlastní průzkumné mapy.", false));
        offers.add(vanilla("BUNDLE", 1, "bundle", 60, "Vak", "Praktické přenosné úložiště.", false));
        offers.add(vanilla("OAK_PLANKS", 64, "oak-planks", 12, "Dubová prkna (64)", "Základní stavební materiál.", false));
        offers.add(vanilla("SPRUCE_LOG", 32, "spruce-logs", 15, "Smrkové kmeny (32)", "Stavební dřevo.", false));
        offers.add(vanilla("BIRCH_LOG", 32, "birch-logs", 15, "Březové kmeny (32)", "Světlé stavební dřevo.", false));
        offers.add(vanilla("CHERRY_LOG", 32, "cherry-logs", 20, "Třešňové kmeny (32)", "Růžové dekorativní dřevo.", false));
        offers.add(vanilla("MANGROVE_LOG", 32, "mangrove-logs", 20, "Mangrovové kmeny (32)", "Tmavší exotické dřevo.", false));
        offers.add(vanilla("STONE_BRICKS", 32, "stone-bricks", 20, "Kamenné cihly (32)", "Pro pevnosti a hrady.", false));
        offers.add(vanilla("MOSSY_COBBLESTONE", 32, "mossy-cobblestone", 25, "Mechem porostlá dlažba (32)", "Starobylý stavební vzhled.", false));
        offers.add(vanilla("CALCITE", 32, "calcite", 35, "Kalcit (32)", "Světlý dekorativní kámen.", false));
        offers.add(vanilla("TUFF", 32, "tuff", 25, "Tuf (32)", "Stavební kámen z hlubin.", false));
        offers.add(vanilla("MUD_BRICKS", 32, "mud-bricks", 25, "Hliněné cihly (32)", "Rustikální stavební blok.", false));
        offers.add(vanilla("GLASS_PANE", 32, "glass-pane", 12, "Skleněné tabulky (32)", "Na okna a prosklené stavby.", false));
        offers.add(vanilla("COBBLESTONE_WALL", 32, "cobblestone-wall", 15, "Kamenné zídky (32)", "Pro oplocení a stavby.", false));
        offers.add(vanilla("STONE_BRICK_STAIRS", 32, "stone-brick-stairs", 25, "Kamenná schodiště (32)", "Pro detailní stavby.", false));
        offers.add(vanilla("HAY_BLOCK", 16, "hay-block", 25, "Balíky sena (16)", "Dekorace a zásoba pro koně.", false));
        offers.add(vanilla("SCAFFOLDING", 16, "scaffolding", 20, "Lešení (16)", "Pomáhá při stavění ve výškách.", false));
        offers.add(vanilla("REDSTONE_BLOCK", 1, "redstone-block", 30, "Blok redstonu", "Kompaktní zdroj redstonu.", false));
        offers.add(vanilla("CRAFTER", 1, "crafter", 100, "Automatický výrobník", "Pokročilý redstone blok.", true));
        offers.add(vanilla("COMPOSTER", 4, "composter", 25, "Kompostéry (4)", "Pro farmy a výrobu kostní moučky.", false));
        offers.add(vanilla("BEEHIVE", 1, "beehive", 45, "Včelí úl", "Pro vlastní produkci medu.", false));
        offers.add(vanilla("SMOKER", 1, "smoker", 35, "Udírna", "Rychlejší příprava jídla.", false));
        offers.add(vanilla("BLAST_FURNACE", 1, "blast-furnace", 60, "Vysoká pec", "Rychlé tavení rud a kovů.", false));
        offers.add(vanilla("SMITHING_TABLE", 1, "smithing-table", 50, "Kovářský stůl", "Pro vylepšování vybavení.", false));
        offers.add(vanilla("CARTOGRAPHY_TABLE", 1, "cartography-table", 40, "Kartografický stůl", "Pro úpravu map.", false));
        offers.add(vanilla("FLETCHING_TABLE", 1, "fletching-table", 25, "Lučnický stůl", "Pracovní stanice vesničanů.", false));
        offers.add(vanilla("LECTERN", 1, "lectern", 40, "Kněžský pult", "Pro knihy a pracovní stanici vesničanů.", false));
        offers.add(vanilla("STONECUTTER", 1, "stonecutter", 35, "Kameník", "Pro přesné opracování kamene.", false));
        offers.add(vanilla("GRINDSTONE", 1, "grindstone", 30, "Brusný kámen", "Pro práci s výbavou.", false));
        offers.add(vanilla("LOOM", 1, "loom", 25, "Tkalcovský stav", "Pro vzory na praporech.", false));
        offers.add(vanilla("JUKEBOX", 1, "jukebox", 100, "Jukebox", "Pro přehrávání hudebních disků.", false));
        offers.add(vanilla("NOTE_BLOCK", 8, "note-block", 25, "Notebloky (8)", "Pro hudební redstone mechanismy.", false));

        return deduplicateCatalogue(offers);
    }

    /**
     * Collapse repeated vanilla offers created while extending the catalogue.
     * Custom ItemsAdder entries remain distinct by namespaced ID (all candy and
     * orange weapon IDs are unique). For a repeated vanilla material at the same
     * configured price, keep the largest bundle so duplicate entries cannot offer
     * a smaller bundle for the exact same price.
     */
    private List<Offer> deduplicateCatalogue(List<Offer> offers) {
        Map<String, Offer> unique = new LinkedHashMap<>();
        for (Offer offer : offers) {
            String identity = offer.iaId() != null && !offer.iaId().isBlank()
                    ? "itemsadder:" + offer.iaId()
                    : "vanilla:" + offer.material().name();
            Offer previous = unique.get(identity);
            if (previous == null || offer.amount() > previous.amount()) {
                unique.put(identity, offer);
            }
        }
        return List.copyOf(unique.values());
    }

    private Offer candy(String id, String name, int fallbackPrice) {
        return new Offer(Material.COOKIE, 1, price("candy", fallbackPrice), "candy", name,
                "Sladkost z existující kolekce 13 textur.", "warriorland_halloween:" + id, false, true);
    }

    private Offer orange(String materialName, String priceKey, int fallbackPrice, String name,
                         String description, String iaId) {
        Material material = Material.matchMaterial(materialName);
        return new Offer(material == null ? Material.BARRIER : material, 1, price(priceKey, fallbackPrice),
                priceKey, name, description, iaId, true, material != null);
    }

    private Offer vanilla(String materialName, int amount, String priceKey, int fallbackPrice,
                          String name, String description, boolean gear) {
        return vanilla(materialName, amount, priceKey, fallbackPrice, name, description, gear, null);
    }

    private Offer vanilla(String materialName, int amount, String priceKey, int fallbackPrice,
                          String name, String description, boolean gear, String iaId) {
        Material material = Material.matchMaterial(materialName);
        return new Offer(material == null ? Material.BARRIER : material, amount, price(priceKey, fallbackPrice),
                priceKey, name, description, iaId, gear, material != null);
    }

    private int price(String key, int fallback) {
        return Math.max(0, plugin.getConfig().getInt("shop.prices." + key, fallback));
    }

    private void render(Holder holder, Player player) {
        holder.inventory.clear();
        holder.offers.clear();
        for (int i = 0; i < 54; i++) holder.inventory.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE, " "));
        for (int i = 0; i < 9; i++) holder.inventory.setItem(i, pane(Material.ORANGE_STAINED_GLASS_PANE, " "));
        for (int i = 45; i < 54; i++) holder.inventory.setItem(i, pane(Material.ORANGE_STAINED_GLASS_PANE, " "));

        int pages = Math.max(1, (catalogue.size() + SLOTS.length - 1) / SLOTS.length);
        holder.inventory.setItem(4, named(Material.JACK_O_LANTERN, ChatColor.GOLD + "☾ Halloweenský obchod",
                List.of(ChatColor.GRAY + "Platíš Halloween fragmenty.",
                        ChatColor.DARK_GRAY + "Nákup surovin, výbavy i vzácných trofejí."), false));
        holder.inventory.setItem(46, fragmentBalanceItem(player));
        holder.inventory.setItem(47, named(Material.PAPER, ChatColor.YELLOW + "Stránka " + (holder.page + 1) + "/" + pages,
                List.of(ChatColor.GRAY + "V obchodě je " + catalogue.size() + " položek."), false));
        holder.inventory.setItem(49, named(Material.BARRIER, ChatColor.RED + "Zavřít obchod", List.of(), false));
        holder.inventory.setItem(48, named(Material.PUMPKIN, ChatColor.GOLD + "Suroviny a poklady",
                List.of(ChatColor.GRAY + "Každá stránka nabízí další vybavení."), false));
        holder.inventory.setItem(51, named(Material.SOUL_LANTERN, ChatColor.DARK_PURPLE + "Výbava lovce",
                List.of(ChatColor.GRAY + "Oranžové zbraně vyžadují ItemsAdder.",
                        ChatColor.GRAY + "Vanilkové vybavení funguje i bez něj."), false));

        if (holder.page > 0) {
            holder.inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Předchozí stránka", List.of(), false));
        }
        if ((holder.page + 1) * SLOTS.length < catalogue.size()) {
            holder.inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Další stránka", List.of(), false));
        }

        for (int i = 0; i < SLOTS.length; i++) {
            int index = holder.page * SLOTS.length + i;
            if (index >= catalogue.size()) break;
            int slot = SLOTS[i];
            Offer offer = catalogue.get(index);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + offer.description());
            if (offer.available()) {
                lore.add(ChatColor.DARK_GRAY + "Cena: " + ChatColor.GOLD + offer.price() + " fragmentů");
                lore.add(ChatColor.YELLOW + "Klikni pro nákup");
                if (offer.iaId() != null) lore.add(ChatColor.DARK_GRAY + "Vzhled: ItemsAdder");
            } else {
                lore.add(ChatColor.RED + "Není dostupné v této verzi serveru");
            }
            ItemStack display = offer.available() ? buildItem(offer)
                    : named(Material.BARRIER, ChatColor.RED + offer.name(), lore, false);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((offer.gear() ? ChatColor.GOLD.toString() + ChatColor.BOLD : ChatColor.GOLD)
                        + offer.name());
                meta.setLore(lore);
                if (offer.gear()) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                display.setItemMeta(meta);
            }
            if (offer.gear() && !display.containsEnchantment(Enchantment.UNBREAKING)) {
                display.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            }
            holder.inventory.setItem(slot, display);
            if (offer.available()) holder.offers.put(slot, offer);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 45 && holder.page > 0) {
            holder.page--;
            render(holder, player);
            return;
        }
        if (slot == 53 && (holder.page + 1) * SLOTS.length < catalogue.size()) {
            holder.page++;
            render(holder, player);
            return;
        }
        Offer offer = holder.offers.get(slot);
        if (offer != null) purchase(player, offer, holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private ItemStack fragmentBalanceItem(Player player) {
        Offer icon = new Offer(Material.AMETHYST_SHARD, 1, 0, "fragment",
                "Halloween Fragment", "Vlastní symbol měny HalloweenCore.",
                "warriorland_halloween:halloween_fragment", false, true);
        ItemStack item = buildItem(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Fragmenty: "
                    + plugin.getFragmentManager().getFragments(player.getUniqueId()));
            meta.setLore(List.of(
                    ChatColor.GRAY + "Získáváš je z questů, Jobs a eventů.",
                    ChatColor.DARK_GRAY + "Zůstatek je virtuální a uložený v players.yml."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildItem(Offer offer) {
        if (offer.iaId() != null && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Method getInstance = customStack.getMethod("getInstance", String.class);
                Object custom = getInstance.invoke(null, offer.iaId());
                if (custom != null) {
                    Object raw = customStack.getMethod("getItemStack").invoke(custom);
                    if (raw instanceof ItemStack stack) return stack.clone();
                }
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().fine("ItemsAdder item není dostupný: " + offer.iaId());
            }
        }
        ItemStack item = new ItemStack(offer.material(), offer.amount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + offer.name());
            meta.setLore(List.of(ChatColor.GRAY + offer.description(), ChatColor.DARK_GRAY + "Halloweenský obchod"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void purchase(Player player, Offer offer, Holder holder) {
        long balance = plugin.getFragmentManager().getFragments(player.getUniqueId());
        if (balance < offer.price()) {
            player.sendMessage(ChatColor.RED + "Nemáš dost fragmentů. Cena " + offer.price()
                    + ", máš " + balance + ".");
            return;
        }

        ItemStack item = buildItem(offer);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((offer.gear() ? ChatColor.GOLD.toString() + ChatColor.BOLD : ChatColor.GOLD)
                    + offer.name());
            meta.setLore(List.of(ChatColor.GRAY + offer.description(),
                    ChatColor.DARK_GRAY + "Halloweenský obchod"));
            if (offer.gear()) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        if (offer.gear() && !item.containsEnchantment(Enchantment.UNBREAKING)) {
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        }
        if (!canFit(player.getInventory(), item)) {
            player.sendMessage(ChatColor.RED + "Udělej si místo v inventáři.");
            return;
        }

        Map<Integer, ItemStack> extra = player.getInventory().addItem(item);
        for (ItemStack leftover : extra.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        plugin.getPlayerData().set("players." + player.getUniqueId() + ".fragments", balance - offer.price());
        plugin.savePlayerData();
        player.sendMessage(ChatColor.GREEN + "Koupeno: " + ChatColor.GOLD + offer.name()
                + ChatColor.GRAY + " za " + ChatColor.YELLOW + offer.price() + " fragmentů.");
        render(holder, player);
    }

    private boolean canFit(PlayerInventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = item.getMaxStackSize();
        for (ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
                remaining -= maxStack;
            } else if (existing.isSimilar(item)) {
                remaining -= Math.max(0, Math.min(maxStack, existing.getMaxStackSize()) - existing.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private ItemStack pane(Material material, String name) {
        return named(material, name, List.of(), false);
    }

    private ItemStack named(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            if (glow) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        if (glow) item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        return item;
    }
}
