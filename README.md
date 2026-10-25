# HalloweenCore

HalloweenCore spojuje plnou implementaci sezónních funkcí SpookySeason, nezávislou implementaci HorrorNights a vlastní systém denních questů, fragmentů a obchodu pro Paper/Purpur.

## Požadavky a instalace

Tato vydávaná sestava je cílená na **Paper/Purpur 1.21.10** a sestavuje se proti **Paper API 1.21.10-R0.1-SNAPSHOT** s Java 21. Tento JAR je určen pro server Minecraft/Paper/Purpur 1.21.10; na jiné verzi jej používej až po samostatném ověření.

- **Povinné:** Paper nebo Purpur **1.21.10**, Java **21+**.
- **Doporučeno:** ItemsAdder 4.0.13+ pro vlastní ikonu fragmentu, 13 sladkostí a oranžové zbraně; hráči musí obdržet a přijmout resource pack.
- **Volitelné integrace:** Jobs Reborn (odměny z Jobs), PlaceholderAPI (fragmentový placeholder), MythicMobs (vlastní HorrorNights moby), WorldGuard/GriefPrevention (kontrola chráněných oblastí) a CustomJukebox (vlastní ambientní hudba).
- **Po instalaci:** plně restartuj server, zkontroluj konzoli, potom spusť `/iazip`. Obsah ItemsAdder ZIP rozbal do `plugins/ItemsAdder/contents/`, aby výsledná cesta byla `plugins/ItemsAdder/contents/warriorland_halloween/`. Nepoužívej PlugMan/PlugManX k hot-reloadu tohoto pluginu.

Podrobnější postup, příkazy a oprávnění jsou v [instalační příručce](docs/INSTALACE_CZ.md). Oranžované zbraně používají moderní barevný systém ItemsAdder, který vyžaduje ItemsAdder 4.0.13+ a Minecraft 1.21.4+ na serveru i klientovi; kopí (Spear) není ve vanilla Minecraftu 1.21.10 dostupné a z této verze je proto vynechané; ostatní oranžové zbraně fungují na 1.21.10. 

## SpookySeason

Integrovaný modul obsahuje Trick-or-Treat u dveří i vesničanů, halloweenské sladkosti a negativní setkání, strašidelné noci, duchy, jumpscare efekty, roje netopýrů, dýňový déšť, krvavý déšť vykreslený částicemi, Headless Horseman bosse, halloweenské chování mobů, nájezdy nemrtvých s cíli a vlnami, sezonní odměny, žebříček nasbíraných sladkostí, menu a individuální nastavení zvuků/opt-outu. Zachované jsou také integrace pro PlaceholderAPI a volitelné WorldGuard, GriefPrevention a CustomJukebox.

## HorrorNights

Nezávislý modul má náhodné spouštění hororových nocí, nebezpečnější a označené moby, ambientní zvuky, částice a náhodné efekty. **Hororové eventy nemění počasí ani oblohu** a krvavý déšť používá pouze částice a zvuky.

Výchozí sezóna je **23. října až 2. listopadu včetně** v časovém pásmu `Europe/Prague`.

## Denní a týdenní questy a fragmenty

Příkaz `/quest` zobrazí obě skupiny; `/quest daily` zobrazí denní kontrakty a `/quest weekly` týdenní výzvy. Každý den je aktivních **15 různých kontraktů najednou**: 4 lehké, 4 střední, 3 těžké, 2 velmi těžké a 2 extrémní. Každé pondělí se obmění také **3 týdenní výzvy**: jedna těžká, jedna velmi těžká a jedna extrémní. Zadání se vybírají z poolu **180 unikátních úkolů** zaměřených na souboje, těžbu, farmaření, crafting, rybaření, sbírání kořisti a chov zvířat. Postup se ukládá do `plugins/HalloweenCore/players.yml`; běžné přihlášení ani reload postup nemažou.

Denní odměny jsou odstupňované podle pěti obtížností: lehké **1–2**, střední **3–5**, těžké **6–9**, velmi těžké **10–15** a extrémní **16–24 fragmentů**. Dokončení všech 15 denních kontraktů přidá **15 fragmentů navíc**. Týdenní výzvy násobí odměnu příslušné obtížnosti koeficientem **3×** a dokončení všech tří přidá **50 fragmentů**. Jednotlivé odměny i bonusy lze upravit v `config.yml`.

### Jobs Reborn

Je-li nainstalovaný plugin Jobs Reborn, propojení automaticky převádí každých **25 získaných Jobs XP na 1 Halloween fragment**. Limit je **8 fragmentů denně z Jobs** na hráče; Jobs tedy přidává menší průběžný bonus, ale nenahrazuje hlavní postup přes questy. Bez Jobs Reborn zbytek HalloweenCore normálně funguje. Integrace používá veřejnou událost `JobsExpGainEvent` z [Jobs Reborn API](https://github.com/Zrips/Jobs/wiki/API).

## Vlastní textura fragmentu

Fragmenty mají vlastní 64×64 pixel-art ikonu `halloween_fragment.png`, která se zobrazuje u zůstatku v GUI obchodu, pokud je aktivní ItemsAdder a klient přijal resource pack. Položka `warriorland_halloween:halloween_fragment` slouží jako grafická ikona — skutečná měna je bezpečně vedena jako číselný zůstatek `players.<UUID>.fragments` v `plugins/HalloweenCore/players.yml`. Fragmentový předmět proto není směnitelný token a nelze jej rozmnožovat jako náhradu zůstatku.

## Obchod a ceny

Všechny ceny jsou v halloweenských fragmentech. U surovin je uvedené množství za jednu koupi; vybavení a vzácné předměty se prodávají po jednom. Obchod má široký katalog běžných surovin, stavebních bloků, jídla, utility předmětů, zbroje a endgame výbavy.

| Předmět | Cena |
| --- | ---: |
| Sladkost z kolekce | 3 fragmenty |
| Železné ingoty (16) | 18 fragmentů |
| Diamant | 45 fragmentů |
| Netheritový ingot | 350 fragmentů |
| Zlaté jablko | 100 fragmentů |
| **Enchanted Golden Apple** | **1 000 fragmentů** |
| Totem of Undying | 750 fragmentů |
| Nether Star | 900 fragmentů |
| Netherite Upgrade Template | 1 100 fragmentů |
| Mace – oranžová varianta | 1 400 fragmentů |
| Beacon | 1 600 fragmentů |
| Elytra | 2 000 fragmentů |

Ceny jsou nastavitelné v `shop.prices` v `config.yml`. Enchanted Golden Apple zůstává záměrně prémiový nákup; kontrola CI hlídá, aby nestál méně než 1 000 fragmentů.

## Oranžové zbraně

`scripts/generate_orange_weapon_items.py` generuje definice ItemsAdder pro oranžové varianty trojzubce, palcátu, netheritového meče a sekery. Používá se obarvení vestavěné do moderního systému `graphics.color`, takže nejsou potřeba nové PNG textury. Je potřeba ItemsAdder 4.0.13+ a Minecraft 1.21.4+ na serveru i klientovi; na Minecraftu 1.21.10 není zahrnuto kopí, které přibylo až v 1.21.11. Viz [dokumentace ItemsAdder – Colored Items](https://wiki.itemsadder.com/adding-content/colored-items/). Nákupy těchto zbraní použijí vlastní ItemsAdder item, pokud je ItemsAdder aktivní; jinak zůstává vanilla náhrada. Po změně obsahu vytvoř resource pack příkazem `/iazip` a ověř, že klient resource pack přijal.

## Záměrně odstraněný obsah

Dialogové questy, BetonQuest NPC balíček a Citizens integrace jsou odstraněné; běžné herní questy zůstávají dostupné přes `/quest`. Tajné místnosti, klíče a jejich textura zůstávají odstraněné. Nefunkční generátor hrobů/tajné podzemní struktury se neobnovuje. Žádný event násilně nemění počasí nebo oblohu. Zachovaných 13 licencovaných sladkostí Stev's Halloween má dál svou MIT licenci a SHA-256 manifest.

## Příkazy

- `/quest` — denní a týdenní kontrakty
- `/halloween shop`, `/halloween fragments`, `/halloween reload`
- `/spooky on|off|status|reload|rewards|optout|top|menu`
- `/spookyboss`, `/spookyraid`, `/spookyvolume`
- `/pumpkinrain start|stop`, `/bloodrain start|stop`
- `/horrornights start|stop|status|reload [svět]`

## Build a nasazení

CI ověřuje textury, konfiguraci, questy/obchod a oranžové zbraně, sestavuje JAR přes Maven a balí ItemsAdder obsah. Úspěšný build nenahrazuje živý test na skutečném serveru.

1. Vezmi `HalloweenCore.jar` z úspěšného GitHub Actions buildu a nahraď starý JAR v `plugins/`.
2. Nahraj obsah `itemsadder/contents/warriorland_halloween/` do `plugins/ItemsAdder/contents/warriorland_halloween/`.
3. Restartuj server, zkontroluj konzoli a spusť `/iazip`; hráči musí přijmout resource pack.
