# HalloweenCore

HalloweenCore spojuje plnou implementaci sezónních funkcí SpookySeason, nezávislou implementaci HorrorNights a vlastní systém denních questů, fragmentů a obchodu pro Paper/Purpur.

## SpookySeason

Integrovaný modul obsahuje Trick-or-Treat u dveří i vesničanů, halloweenské sladkosti a negativní setkání, strašidelné noci, duchy, jumpscare efekty, roje netopýrů, dýňový déšť, krvavý déšť vykreslený částicemi, Headless Horseman bosse, halloweenské chování mobů, nájezdy nemrtvých s cíli a vlnami, sezonní odměny, žebříček nasbíraných sladkostí, menu a individuální nastavení zvuků/opt-outu. Zachované jsou také integrace pro PlaceholderAPI a volitelné WorldGuard, GriefPrevention a CustomJukebox.

## HorrorNights

Nezávislý modul má náhodné spouštění hororových nocí, nebezpečnější a označené moby, ambientní zvuky, částice a náhodné efekty. **Hororové eventy nemění počasí ani oblohu** a krvavý déšť používá pouze částice a zvuky.

Výchozí sezóna je **23. října až 2. listopadu včetně** v časovém pásmu `Europe/Prague`.

## Denní questy a fragmenty

Příkaz `/quest` každý den nabídne **4 současné kontrakty**: lehký, střední, těžký a extrémní. Zadání se náhodně vybírá z poolu **82 skutečně sledovaných úkolů** zaměřených na souboje, těžbu, farmaření, crafting, rybaření, sbírání kořisti a chov zvířat. Postup a dokončení se ukládají do `plugins/HalloweenCore/players.yml`; rozpracované úkoly se nepřidělují znovu při každém přihlášení.

Fragmentové odměny jsou odstupňované podle obtížnosti: lehké **3–5**, střední **8–12**, těžké **18–28** a extrémní **55–80 fragmentů**. Za dokončení všech čtyř kontraktů v jednom dni je ještě **15 fragmentů navíc**. Úkoly z dialogů mají vlastní konfigurovatelné odměny; systém podporuje i stupeň „velmi těžký“. Ceny i odměny lze upravit v `config.yml`.

## Halloweenský obchod

Příkaz `/halloween shop` otevírá stránkovaný černo-oranžový inventářový obchod s více než **100 nabídkami**. Kromě všech 13 zachovaných sladkostí zahrnuje suroviny a rudy, stavební bloky, jídlo, redstone materiály, užitečné předměty, nástroje, zbraně, brnění a vzácné trofeje. Platí se fragmenty.

Mezi prémiové nákupy patří zakleté zlaté jablko za **750 fragmentů**, netheritový ingot za **350**, totem nesmrtelnosti za **750**, palcát za **1 400**, maják za **1 600** a Elytra za **2 000 fragmentů**. Dražší položky tak vyžadují dlouhodobé šetření místo rychlého nakoupení po jednom jednoduchém questu.

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
