package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import java.util.List;
import java.util.Map;

public final class HalloweenGuideBook {
    private HalloweenGuideBook() {}
    public static void give(HalloweenCore plugin, Player player) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) { player.sendMessage(plugin.color("&cHalloween průvodce se nepodařilo vytvořit.")); return; }
        meta.setTitle("Průvodce HalloweenCore");
        meta.setAuthor("WarriorLand");
        meta.addPage(pages().toArray(String[]::new));
        item.setItemMeta(meta);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        player.sendMessage(plugin.color("&6&lHALLOWEEN &8» &7Dostal jsi knihu &ePrůvodce HalloweenCore&7."));
    }
    private static List<String> pages() {
        return List.of(
            "§6§lHALLOWEEN 2026\n§0Průvodce serverovým eventem WarriorLand.\n\n§0Pravidla událostí, postup, fragmenty, speciální mobové, odměny a příkazy.",
            "§6§lJAK HRÁT\n§0Hraj v Survivalu nebo Adventure. Zabíjej moby, těž rudy, sklízej a rybař. Získáváš §6Halloween fragmenty§0 do obchodu.",
            "§6§lPROKLETÍ\n§0Tvoje úroveň prokletí je 0 až 5. Vyšší úroveň zvyšuje zisk fragmentů a odemyká dražší odměny. §e/halloween curse",
            "§5§lDUŠEVNÍ BOUŘE\n§0Každých 25 sekund přichází vlna prokletých zombie, hrobníků a krvavých pavouků. Moby se objevují u každého hráče do 25 bloků.",
            "§5§lČARODĚJNICKÁ HODINA\n§0U každého hráče se objevují hexové čarodějky, krvaví pavouci a prokletí zombie. Temnota a zvuky oznamují začátek lovu.",
            "§a§lPROKLETÁ SKLIZEŇ\n§0Nepřátelé se probouzejí na blocích §eFarmland§0 — zorané půdě po použití motyky. Plugin postupně prochází načtené chunky a pole.",
            "§4§lKRVAVÝ MĚSÍC\n§0Svět se přepne na noc a červené částice vytvoří krvavý měsíc. Přicházejí vlny u hráčů; nepřátelská monstra způsobují 3× poškození.",
            "§6§lDÝŇOVÁ APOKALYPSA\n§0U každého hráče se objevují dýňoví přízraci. Plugin přidává dýně a jack-o-lanterny do bezpečných míst v načtených přírodních chunkech.",
            "§8§lHŘBITOV VSTÁVÁ\n§0Moby se objevují pouze u hřbitovů zaznamenaných při generování. Útočí na nejbližšího způsobilého hráče v okolí hřbitova.",
            "§c§lSPECIÁLNÍ MOBO VÉ\n§0Prokletý zombie, Ženec prázdnoty, Hrobník, Ledový stopař, Krvavý pavouk, Dýňový přízrak, Hexová čarodějka a Noční můra mají vlastní útoky.",
            "§6§lODMĚNY\n§0Obchod: §e/halloween rewards§0. Denní lov: §e/halloween challenge§0. Příběh: §e/halloween quests§0. Některé věci potřebují vyšší prokletí.",
            "§5§lRELIKVIE\n§0Maska nočního lovce zvyšuje fragmenty. Talisman při držení přidá jedno srdce. Batwing Elytra nabízí výpad za letu; Cowl odhaluje a zpomaluje monstra.",
            "§e§lSVĚT A HUDBA\n§0V přírodě se mohou objevit dýně, pavučiny, červené svíčky, houby, náhrobky, duševní lucerny a suché keře. V noci hraje Halloween playlist.",
            "§b§lPŘÍKAZY HRÁČE\n§e/halloween §0statistiky\n§e/halloween progress §0cíl serveru\n§e/halloween event §0stav eventu\n§e/halloween secrets §0nápovědy\n§e/halloween guide §0kniha znovu.",
            "§c§lVLASTNÍ VZHLEDY\n§0Vlastní modely vyžadují přijetí ItemsAdder resource packu. Pokud předmět vypadá jako vanilla, může chybět pack nebo model; některé modely čekají na ověření.",
            "§6§lSPOLEČNÝ CÍL\n§0Všichni přispívají fragmenty do společného cíle. Dosáhněte milníků, posilujte Halloween eventy a odemkněte finále.\n\n§8§oPřežij noc — a pozor na hřbitově."
        );
    }
}
