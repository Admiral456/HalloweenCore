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
            "\uE1F0\n\n\n§6§lHALLOWEENCORE\n§0ILUSTROVANÝ PRŮVODCE\n§8━━━━━━━━━━━━\n§0WarriorLand • 2026\n\n§7Otevři další stránku.",

            "§6§lOBSAH\n§8━━━━━━━━━━━━\n§0 1. Jak začít .......... 3\n§0 2. Fragmenty .......... 4\n§5 3. Duševní bouře ...... 5\n§d 4. Čarodějnická hodina  6\n§a 5. Prokletá sklizeň ... 7\n§4 6. Krvavý měsíc ....... 8\n§6 7. Dýňová apokalypsa .. 9\n§3 8. Hřbitov vstává .... 10\n§0 9. Relikvie a příkazy  12",

            "§6§l01 • JAK ZAČÍT\n§8━━━━━━━━━━━━\n§0Halloween svět skrývá eventy, speciální moby a vzácné odměny.\n\n§2◆ §0Bojuj s monstry.\n§2◆ §0Těž rudy, farmař a rybař.\n§2◆ §0Sbírej Halloween fragmenty.\n§2◆ §0Plň úkoly a prozkoumávej hřbitovy.\n\n§eTip: §0Na těžké eventy se připrav s výbavou a jídlem.",

            "§6§l02 • FRAGMENTY\n§8━━━━━━━━━━━━\n§0Fragmenty jsou měna HalloweenCore. Získáš je hraním a můžeš je utrácet za odměny.\n\n§d◆ §0Lov a PvE\n§6◆ §0Těžba a farmaření\n§b◆ §0Rybaření a úkoly\n\n§5PROKLETÍ 0–5\n§0Vyšší úroveň může zvýšit výdělek a odemknout další odměny.\n§e/halloween curse",

            "\uE1F1\n\n§5§l03 • DUŠEVNÍ BOUŘE\n§8━━━━━━━━━━━━\n§0Každých 25 sekund přichází další vlna.\n\n§5◆ §0Spawn u každého způsobilého hráče.\n§5◆ §0Místo se hledá do 25 bloků.\n§5◆ §0Prokletí zombie, hrobníci a krvaví pavouci.\n\n§dTIP\n§0Drž se v pohybu a nenech se obklíčit.",

            "\uE1F2\n\n§5§l04 • ČARODĚJNICKÁ HODINA\n§8━━━━━━━━━━━━\n§0Temnota dává prostor lovcům a čarodějnicím.\n\n§d◆ §0Vlna u každého způsobilého hráče.\n§d◆ §0Spawn do 25 bloků.\n§d◆ §0Hexové čarodějky, pavouci a zombie.\n§d◆ §0Může se objevit efekt temnoty.\n\n§5TIP\n§0Sleduj zvuky a měj připravené léčení.",

            "\uE1F3\n\n§a§l05 • PROKLETÁ SKLIZEŇ\n§8━━━━━━━━━━━━\n§0Pole už není bezpečné. Nepřátelé se probouzejí na skutečné zorané půdě.\n\n§a◆ §0Pole = blok FARMLAND po použití motyky.\n§a◆ §0Krvaví pavouci a dýňoví přízraci.\n§a◆ §0Cílí na nejbližšího hráče v dosahu.\n\n§ePOZOR\n§0Svět se prochází postupně v načtených chunkech, ne celý najednou.",

            "\uE1F4\n\n§4§l06 • KRVAVÝ MĚSÍC\n§8━━━━━━━━━━━━\n§0Noc se zbarví do ruda a začíná invaze.\n\n§c◆ §0Vlny u každého způsobilého hráče.\n§c◆ §0Spawn do 25 bloků.\n§c◆ §0Nepřátelská monstra udělují 3× poškození.\n§c◆ §0Může se objevit kapitán invaze.\n\n§4TIP\n§0Červený disk je efekt z částic; textura vanilla měsíce se dynamicky nepřebarvuje.",

            "\uE1F5\n\n§6§l07 • DÝŇOVÁ APOKALYPSA\n§8━━━━━━━━━━━━\n§0Dýňoví přízraci přicházejí z plamenů a dýně se objevují v krajině.\n\n§6◆ §0Vlna u každého způsobilého hráče.\n§6◆ §0Spawn do 25 bloků.\n§6◆ §0Dýně a jack-o'-lanterny v přírodě.\n\n§eTIP\n§0Dekorace přibývají postupně v načtených přírodních chunkech a nepřepisují obsazené bloky.",

            "\uE1F6\n\n§3§l08 • HŘBITOV VSTÁVÁ\n§8━━━━━━━━━━━━\n§0Tady je důležité místo: mrtví povstanou jen na zaznamenaných hřbitovech.\n\n§3◆ §0Spawn pouze na evidovaných hřbitovech.\n§3◆ §0Žádné běžné vlny na volném poli.\n§3◆ §0Moby útočí na nejbližšího hráče v dosahu.\n\n§eTIP\n§0Prozkoumávej hřbitovy a dávej pozor na hrobníky.",

            "§c§l09 • SPECIÁLNÍ MOBOVÉ\n§8━━━━━━━━━━━━\n§0Každý typ má vlastní vlastnosti a může používat speciální útoky.\n\n§5◆ §0Prokletý zombie — vysává sílu.\n§8◆ §0Hrobník — označuje místo útoku.\n§c◆ §0Krvavý pavouk — jed a zpomalení.\n§6◆ §0Dýňový přízrak — výbuch popela.\n§d◆ §0Hexová čarodějka — kletby a lektvary.\n\n§7Některé modely potřebují správně nahraný resource pack.",

            "§5§l10 • RELIKVIE\n§8━━━━━━━━━━━━\n§0Vzácné předměty poskytují bonusy a schopnosti.\n\n§6◆ §0Maska nočního lovce — bonus k fragmentům.\n§5◆ §0Prokletý talisman — extra srdce při držení.\n§d◆ §0Batwing Elytra — výpad při plachtění.\n§8◆ §0Nocturne Sovereign Cowl — odhalení monster.\n\n§e/halloween rewards\n§0Požadavky a ceny najdeš v obchodě.",

            "§e§l11 • SVĚT A TAJEMSTVÍ\n§8━━━━━━━━━━━━\n§0V přírodě můžeš najít dýně, pavučiny, houby, svíčky, lucerny a hroby.\n\n§6◆ §0Hřbitovy a strašidelné vesnice.\n§5◆ §0Krvavý oltář.\n§d◆ §0Doupě hexové čarodějky.\n§8◆ §0Zapomenutý hrob.\n\n§e/halloween secrets\n§0Zobrazí nápovědy, nikoli přesné souřadnice.",

            "§b§l12 • PŘÍKAZY HRÁČE\n§8━━━━━━━━━━━━\n§e/halloween §0— statistiky\n§e/halloween progress §0— cíl serveru\n§e/halloween curse §0— tvé prokletí\n§e/halloween rewards §0— obchod\n§e/halloween challenge §0— denní výzva\n§e/halloween quests §0— příběh\n§e/halloween secrets §0— tajná místa\n§e/halloween guide §0— získat tuto knihu znovu.",

            "§4§l13 • CO SI PAMATOVAT\n§8━━━━━━━━━━━━\n§0Eventy mají limit počtu mobů a bezpečných spawn míst. Ne každá vlna proto musí vytvořit maximální počet nepřátel.\n\n§0Pole, dýně a hřbitovy se zpracovávají podle načtených chunků. Celá nekonečná mapa se neprochází naráz.\n\n§eVlastní obrázky\n§0Ilustrace této knihy vyžadují přijatý ItemsAdder resource pack. Bez něj se soukromé znaky nemusí zobrazit správně.\n\n§6Přežij noc — a nechoď sám na hřbitov."
        );
    }
}
