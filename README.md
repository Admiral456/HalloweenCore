# HalloweenCore

HalloweenCore spojuje plnou implementaci sezónních funkcí SpookySeason, nezávislou implementaci HorrorNights a vlastní systém denních questů, fragmentů a obchodu pro Paper/Purpur.

## SpookySeason

Integrovaný modul obsahuje Trick-or-Treat u dveří i vesničanů, halloweenské sladkosti a negativní setkání, strašidelné noci, duchy, jumpscare efekty, roje netopýrů, dýňový déšť, krvavý déšť vykreslený částicemi, Headless Horseman bosse, halloweenské chování mobů, nájezdy nemrtvých s cíli a vlnami, sezonní odměny, žebříček nasbíraných sladkostí, menu a individuální nastavení zvuků/opt-outu. Zachované jsou také integrace pro PlaceholderAPI a volitelné WorldGuard, GriefPrevention a CustomJukebox.

## HorrorNights

Nezávislý modul má náhodné spouštění hororových nocí, nebezpečnější a označené moby, ambientní zvuky, částice a náhodné efekty. **Hororové eventy nemění počasí ani oblohu** a krvavý déšť používá pouze částice a zvuky.

Výchozí sezóna je **23. října až 2. listopadu včetně** v časovém pásmu `Europe/Prague`.

## Denní questy a obchod

Příkaz `/quest` přiděluje jeden úkol na den: zabíjení zombie/kostlivců, těžbu železné rudy nebo lámání dýní. Postup a dokončení se ukládají do `plugins/HalloweenCore/players.yml`; výchozí odměna je **1 fragment**.

Příkaz `/halloween shop` otevírá černo-oranžový inventářový obchod. Obsahuje 13 zachovaných sladkostí, suroviny, netherit, totem, zlatá jablka a výbavu včetně trojzubce, palcátu, netheritového meče/sekery a kopí (pokud jej daná verze serveru podporuje). Platí se fragmenty.

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
