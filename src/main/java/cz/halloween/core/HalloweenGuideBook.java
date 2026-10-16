package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;
import java.util.Map;

/**
 * Illustrated HalloweenCore guide. The private-use characters are mapped to
 * bitmap glyphs by the ItemsAdder font_images resource-pack content.
 */
public final class HalloweenGuideBook {
    private HalloweenGuideBook() {}

    public static void give(HalloweenCore plugin, Player player) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) {
            player.sendMessage(plugin.color("&cPrůvodce HalloweenCore se nepodařilo vytvořit."));
            return;
        }

        meta.setTitle("Průvodce HalloweenCore");
        meta.setAuthor("WarriorLand");
        meta.setDisplayName(plugin.color("&6&l✦ Průvodce HalloweenCore"));
        meta.setLore(List.of(
                plugin.color("&7Ilustrovaná příručka eventů"),
                plugin.color("&8WarriorLand • Halloween 2026"),
                plugin.color("&ePravým kliknutím otevři")
        ));
        meta.addPage(pages().toArray(String[]::new));
        item.setItemMeta(meta);

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.sendMessage(plugin.color("&6&lHALLOWEEN &8» &7Do inventáře jsi dostal &eIlustrovaného průvodce&7."));
        player.sendMessage(plugin.color("&8Poznámka: vlastní obrázky v knize vyžadují přijatý ItemsAdder resource pack."));
    }

    private static List<String> pages() {
        return List.of(
            "\uE1F0\n\n\n§6§lHALLOWEENCORE\n§0ILUSTROVANÝ PRŮVODCE\n§8━━━━━━━━━━━━\n§0WarriorLand • 2026\n§7Otevři další stránku.",

            "§6§lOBSAH\n§8━━━━━━━━━━━━\n§0Jak začít • fragmenty\n§5Bouře • čarodějnická hodina\n§aProkletá sklizeň\n§4Krvavý měsíc\n§6Dýňová apokalypsa\n§3Hřbitov vstává\n§0Mobové • relikvie\n§0Tajemství • příkazy",

            "§6§l01 • JAK ZAČÍT\n§8━━━━━━━━━━━━\n§0Halloween svět skrývá eventy, speciální moby a vzácné odměny.\n\n§2• §0Bojuj, těž, farmař a rybař.\n§2• §0Sbírej fragmenty.\n§2• §0Plň úkoly a hledej hřbitovy.\n\n§eTIP: §0Vezmi si jídlo a dobrou výbavu.",

            "§6§l02 • FRAGMENTY\n§8━━━━━━━━━━━━\n§0Fragmenty získáš hraním. Utrácí se za odměny a relikvie.\n\n§d• §0PvE, těžba, farmaření\n§b• §0Rybaření a úkoly\n\n§5PROKLETÍ 0–5\n§0Vyšší úroveň může zvýšit výdělek a odemykat další odměny.\n§e/halloween curse",

            "\uE1F1\n\n§5§l03 • DUŠEVNÍ BOUŘE\n§8━━━━━━━━━━━━\n§0Vlna každých 25 sekund.\n§5• §0U každého způsobilého hráče\n§5• §0Spawn do 25 bloků\n§0Moby: zombie, hrobník,\n§0krvavý pavouk\n\n§dTIP: §0Drž se v pohybu.",

            "\uE1F2\n\n§5§l04 • ČARODĚJNICKÁ HODINA\n§8━━━━━━━━━━━━\n§0Temnota dává prostor lovcům.\n§d• §0Vlna u každého hráče\n§d• §0Spawn do 25 bloků\n§0Moby: čarodějka, pavouk,\n§0prokletý zombie\n\n§5TIP: §0Měj připravené léčení.",

            "\uE1F3\n\n§a§l05 • PROKLETÁ SKLIZEŇ\n§8━━━━━━━━━━━━\n§0Nepřátelé se probouzejí na polích.\n§a• §0Pole = blok FARMLAND po motyce\n§a• §0Krvaví pavouci a dýňoví přízraci\n§a• §0Cílí na nejbližšího hráče\n\n§ePOZOR\n§0Načtené chunky se procházejí postupně.",

            "\uE1F4\n\n§4§l06 • KRVAVÝ MĚSÍC\n§8━━━━━━━━━━━━\n§0Noc se zbarví do ruda.\n§c• §0Vlna u každého hráče\n§c• §0Spawn do 25 bloků\n§c• §03× poškození od nepřátelských monster\n§c• §0Kapitán invaze může zaútočit\n\n§4Červený disk je částicový efekt.",

            "\uE1F5\n\n§6§l07 • DÝŇOVÁ APOKALYPSA\n§8━━━━━━━━━━━━\n§0Přicházejí dýňoví přízraci.\n§6• §0Vlna u každého hráče\n§6• §0Spawn do 25 bloků\n§6• §0Dýně se přidávají do přírody\n\n§eTIP\n§0Dekorace nepřepisují obsazené bloky.",

            "\uE1F6\n\n§3§l08 • HŘBITOV VSTÁVÁ\n§8━━━━━━━━━━━━\n§0Mrtví povstanou jen na zaznamenaných hřbitovech.\n§3• §0Nikdy ne na obyčejném poli\n§3• §0Cílí na nejbližšího hráče v dosahu\n\n§eTIP\n§0Prozkoumávej hřbitovy opatrně.",

            "§c§l09 • SPECIÁLNÍ MOBOVÉ\n§8━━━━━━━━━━━━\n§0Každý má svůj útok:\n§5• §0Zombie — vysává sílu\n§8• §0Hrobník — značící útok\n§c• §0Pavouk — jed a zpomalení\n§6• §0Přízrak — výbuch popela\n§d• §0Čarodějka — kletby\n§7Vlastní modely potřebují pack.",

            "§5§l10 • RELIKVIE\n§8━━━━━━━━━━━━\n§0Vzácné předměty poskytují bonusy.\n§6• §0Maska — více fragmentů\n§5• §0Talisman — extra srdce při držení\n§d• §0Batwing Elytra — vzdušný výpad\n§8• §0Cowl — odhalení monster\n\n§e/halloween rewards",

            "§e§l11 • SVĚT A TAJEMSTVÍ\n§8━━━━━━━━━━━━\n§0V přírodě hledej dýně, pavučiny, houby, svíčky a hroby.\n\n§6• §0Krvavý oltář\n§5• §0Doupě hexové čarodějky\n§8• §0Zapomenutý hrob\n\n§e/halloween secrets",

            "§b§l12 • PŘÍKAZY\n§8━━━━━━\n§e/halloween §0statistiky\n§e/halloween progress §0cíl\n§e/halloween curse §0prokletí\n§e/halloween rewards §0obchod\n§e/halloween challenge §0výzva\n§e/halloween quests §0příběh\n§e/halloween guide §0kniha",

            "§4§l13 • PAMATUJ SI\n§8━━━━━━\n§0Počet mobů je omezený; některé vlny proto nemusí využít maximum.\n\n§0Pole i dýně se zpracovávají v načtených chunkech.\n\n§eObrázky vyžadují ItemsAdder pack.\n§6Přežij noc!",
            "§6§l14 • ÚSPĚCHY\n§8━━━━━━\n§0Odemčené úspěchy se ukládají.\n§e/halloween achievements\n§0Najdi Halloween vesnici i hřbitov.\n§0Získej všechny 4 kusy zbroje Krvavého strážce.\n§6Prohlédni si další stránku.",
            "§4§l15 • LOVEC BOSSŮ\n§8━━━━━━\n§0Poraz jednotlivé hlavní bosse:\n§4• §0Krále upírů\n§8• §0Strážce hrobu\n§5• §0Bezhlavého Nicka\n§c• §0Kapitána krvavé invaze\n\n§6Poraz všechny čtyři a získej další úspěch!"
        );
    }
}