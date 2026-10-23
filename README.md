# HalloweenCore

HalloweenCore spojuje plnou implementaci sezónních funkcí SpookySeason, nezávislou implementaci HorrorNights a vlastní systém denních questů, fragmentů a obchodu pro Paper/Purpur.

## SpookySeason

Integrovaný modul obsahuje Trick-or-Treat u dveří i vesničanů, halloweenské sladkosti a negativní setkání, strašidelné noci, duchy, jumpscare efekty, roje netopýrů, dýňový déšť, krvavý déšť vykreslený částicemi, Headless Horseman bosse, halloweenské chování mobů, nájezdy nemrtvých s cíli a vlnami, sezonní odměny, žebříček nasbíraných sladkostí, menu a individuální nastavení zvuků/opt-outu. Zachované jsou také integrace pro PlaceholderAPI a volitelné WorldGuard, GriefPrevention a CustomJukebox.

## HorrorNights

Nezávislý modul má náhodné spouštění hororových nocí, nebezpečnější a označené moby, ambientní zvuky, částice a náhodné efekty. **Hororové eventy nemění počasí ani oblohu** a krvavý déšť používá pouze částice a zvuky.

Výchozí sezóna je **23. října až 2. listopadu včetně** v časovém pásmu `Europe/Prague`.

## Denní a týdenní questy a fragmenty

Příkaz `/quest` zobrazí obě skupiny; `/quest daily` zobrazí denní kontrakty a `/quest weekly` týdenní výzvy. Každý den je aktivních **15 různých kontraktů najednou**: 6 lehkých, 4 střední, 3 těžké a 2 extrémní. Každé pondělí se obmění také **3 týdenní výzvy**: jedna těžká a dvě extrémní. Zadání se vybírají z poolu **82 skutečně sledovaných úkolů** zaměřených na souboje, těžbu, farmaření, crafting, rybaření, sbírání kořisti a chov zvířat. Postup se ukládá do `plugins/HalloweenCore/players.yml`; běžné přihlášení ani reload postup nemažou.

Denní odměny jsou odstupňované podle obtížnosti: lehké **1–2**, střední **2–4**, těžké **5–8** a extrémní **10–16 fragmentů**. Dokončení všech 15 denních kontraktů přidá **15 fragmentů navíc**. Týdenní výzvy násobí odměnu příslušné obtížnosti koeficientem **3×** a dokončení všech tří přidá **50 fragmentů**. Jednotlivé odměny i bonusy lze upravit v `config.yml`.

### Jobs Reborn

Je-li nainstalovaný plugin Jobs Reborn, propojení automaticky převádí každých **25 získaných Jobs XP na 1 Halloween fragment**. Limit je **8 fragmentů denně z Jobs** na hráče; Jobs tedy přidává menší průběžný bonus, ale nenahrazuje hlavní postup přes questy. Bez Jobs Reborn zbytek HalloweenCore normálně funguje. Integrace používá veřejnou událost `JobsExpGainEvent` z [Jobs Reborn API](https://github.com/Zrips/Jobs/wiki/API).

## Oranžové zbraně

`scripts/generate_orange_weapon_items.py` generuje definice ItemsAdder pro oranžové varianty trojzubce, palcátu, netheritového meče, sekery a kopí. Používá se obarvení vestavěné do moderního systému `graphics.color`, takže nejsou potřeba nové PNG textury. Je potřeba ItemsAdder 4.0.13+ a Minecraft 1.21.4+ na serveru i klientovi; viz [dokumentace ItemsAdder – Colored Items](https://wiki.itemsadder.com/adding-content/colored-items/). Nákupy těchto zbraní použijí vlastní ItemsAdder item, pokud je ItemsAdder aktivní; jinak zůstává vanilla náhrada. Po změně obsahu vytvoř resource pack příkazem `/iazip` a ověř, že klient resource pack přijal.

## Záměrně odstraněný obsah

Tajné místnosti, klíče a jejich textura zůstávají odstraněné. Nefunkční generátor hrobů/tajné podzemní struktury se neobnovuje. Žádný event násilně nemění počasí nebo oblohu. Zachovaných 13 licencovaných sladkostí Stev's Halloween má dál svou MIT licenci a SHA-256 manifest.

## Příkazy

- `/quest` — denní úkol
- `/halloween shop`, `/halloween fragments`, `/halloween reload`
- `/halloween quest start|progress|complete|status <id>` — integrační questy
- `/spooky on|off|status|reload|rewards|optout|top|menu`
- `/spookyboss`, `/spookyraid`, `/spookyvolume`
- `/pumpkinrain start|stop`, `/bloodrain start|stop`
- `/horrornights start|stop|status|reload [svět]`

## Build a nasazení

CI ověřuje textury, konfiguraci, questy/obchod a oranžové zbraně, sestavuje JAR přes Maven a balí ItemsAdder obsah. Úspěšný build nenahrazuje živý test na skutečném serveru.

1. Vezmi `HalloweenCore.jar` z úspěšného GitHub Actions buildu a nahraď starý JAR v `plugins/`.
2. Nahraj obsah `itemsadder/contents/warriorland_halloween/` do `plugins/ItemsAdder/contents/warriorland_halloween/`.
3. Restartuj server, zkontroluj konzoli a spusť `/iazip`; hráči musí přijmout resource pack.
