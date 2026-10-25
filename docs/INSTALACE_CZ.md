# HalloweenCore — instalační návod (CZ)

## 1. Co potřebuješ

- **Minecraft / server:** Paper nebo Purpur **1.21.10**.
- **Java:** 21 nebo novější.
- **Nutné:** samotný soubor `HalloweenCore.jar`.
- **Doporučené pro vlastní vzhled:** ItemsAdder **4.0.13+**. Bez něj poběží questy, fragmenty, obchod i herní eventy, ale 13 vlastních sladkostí, ikonka fragmentů a oranžové zbraně se nemusí zobrazit jako vlastní předměty.
- **Volitelné integrace:** Jobs Reborn (fragmenty za Jobs XP), PlaceholderAPI, MythicMobs, WorldGuard, GriefPrevention a CustomJukebox. Dialogové NPC/BetonQuest modul v této verzi není součástí.

Tento build je sestavený konkrétně pro Paper/Purpur 1.21.10. Nepoužívej ho na jiných řadách serveru bez samostatného ověření.

## 2. Co stáhnout z GitHubu

V repozitáři otevři **Actions**, vyber poslední úspěšný běh workflow **Build HalloweenCore** a stáhni tyto dva artefakty:

1. **`HalloweenCore-JAR`** — uvnitř je `HalloweenCore.jar`.
2. **`WarriorLand-Halloween-ItemsAdder`** — ZIP s obsahem ItemsAdder. Ten je potřeba pro sladkosti, vlastní ikonu fragmentů a oranžové zbraně.

Používej artefakty ze stejného úspěšného běhu, ne starší JAR a ZIP z různých běhů.

## 3. Kam soubory dát

Před změnami udělej zálohu světa a těchto složek: `plugins/HalloweenCore/`, `plugins/ItemsAdder/`.

1. **Vypni server** čistě přes konzoli/panel. Nenasazuj změny za běhu.
2. Nahraj `HalloweenCore.jar` do složky `plugins/`. Výsledná cesta má být:
   `plugins/HalloweenCore.jar`
   Pokud tam už starý `HalloweenCore.jar` je, nahraď jej novým.
3. Rozbal ZIP `WarriorLand-Halloween-ItemsAdder`. Složku `warriorland_halloween` z archivu zkopíruj do:
   `plugins/ItemsAdder/contents/warriorland_halloween/`
   Výsledné cesty musí mimo jiné obsahovat:
   - `plugins/ItemsAdder/contents/warriorland_halloween/configs/items.yml`
   - `plugins/ItemsAdder/contents/warriorland_halloween/configs/orange_weapons.yml`
   - `plugins/ItemsAdder/contents/warriorland_halloween/resourcepack/`
   Nekopíruj obsah do další vnořené složky `warriorland_halloween/warriorland_halloween/`.
4. **Zapni server** a zkontroluj konzoli. Při prvním startu vznikne `plugins/HalloweenCore/config.yml`; postup questů a virtuální fragmenty se ukládají do `plugins/HalloweenCore/players.yml`.
5. Pokud používáš ItemsAdder, spusť `/iazip` a ověř, že resource pack jde hráčům stáhnout a že jej přijali. Případně zkontroluj nastavení distribuce resource packu ve svém hostingu.
6. Udělej úplný restart serveru po změnách pluginu. Nepoužívej `/reload`, PlugMan ani PlugManX pro opětovné načtení JARu.

**Důležité při aktualizaci:** nesmaž `plugins/HalloweenCore/players.yml` — jsou v něm uložené fragmenty a postup hráčů. Pokud chceš zachovat vlastní nastavení, zazálohuj a pečlivě slouč starý `config.yml` namísto smazání celé datové složky.

## 4. Kontrola po instalaci

Jako hráč vyzkoušej:

- `/quest`, `/quest daily`, `/quest weekly` — denní kontrakty a týdenní výzvy.
- `/halloween fragments` — zůstatek virtuálních fragmentů.
- `/halloween shop` — obchod.
- `/spooky status`, `/spooky menu` — SpookySeason.

Jako administrátor / operátor vyzkoušej:

- `/horrornights status` — stav hororových nocí; `/horrornights start` a `stop` pro ruční test.
- `/pumpkinrain start` a `/pumpkinrain stop` — dýňový déšť.
- `/bloodrain start` a `/bloodrain stop` — krvavý déšť částicemi.
- `/spookyboss info` nebo `/spookyboss spawn` — kontrola / vyvolání Headless Horseman.
- `/spookyraid status` — stav nájezdu.
- `/halloween reload` — načte konfiguraci HalloweenCore, SpookySeason a HorrorNights.

Sezóna je nastavena na **23. října až 2. listopadu včetně** v časovém pásmu `Europe/Prague`. Automatické Halloween/HorrorNights události respektují sezónní nastavení a konfiguraci povolených světů.

## 5. Questy, fragmenty a shop

Každý den se vybere 15 kontraktů z poolu 180 úkolů: 4 lehké, 4 střední, 3 těžké, 2 velmi těžké a 2 extrémní. Každé pondělí se obnoví 3 týdenní výzvy. Odměny rostou s obtížností: 1–2, 3–5, 6–9, 10–15 a 16–24 fragmentů. Za dokončení všech denních kontraktů dostane hráč 15 fragmentů navíc; za všechny týdenní výzvy dalších 50. Jobs Reborn může přidat 1 fragment za každých 25 Jobs XP, nejvýše 8 za den.

Obchod obsahuje přes 100 běžných i prémiových položek. **Enchanted Golden Apple stojí 1 000 fragmentů**; drahé předměty zahrnují Totem of Undying, Nether Star, Netherite Upgrade Template, Mace, Beacon, Elytra a oranžové halloweenské zbraně. Ceny a odměny upravíš v `plugins/HalloweenCore/config.yml`.

## 6. Oranžové zbraně a resource pack

Definice pro čtyři oranžové varianty — trojzubec, palcát, netheritový meč a sekeru — jsou v `configs/orange_weapons.yml`. Kopí není dostupné ve vanilla Minecraftu 1.21.10, na který je tato sestava cílená. Generátor je `scripts/generate_orange_weapon_items.py`. Nové PNG nejsou potřeba; barvu nastavuje moderní systém ItemsAdder `graphics.color`. Pro správné zobrazení musí mít server ItemsAdder 4.0.13+ a hráči musí mít kompatibilní Minecraft verzi a přijmout resource pack. Pokud zbraně vypadají jako běžné vanilla předměty, nejdřív zkontroluj ItemsAdder, cestu obsahu a stav resource packu.

## 7. Co plugin obsahuje

- Plný integrovaný SpookySeason: Trick-or-Treat u dveří a vesničanů, sladkosti a negativní setkání, strašidelné noci, duchy, jumpscare efekty, roje netopýrů, dýňový a částicový krvavý déšť, sezónní moby, Headless Horseman, nájezdy nemrtvých, odměny, žebříček sladkostí, menu, nastavení hlasitosti a opt-out.
- Nezávislý HorrorNights: náhodné hororové noci, silnější/označené moby, ambientní zvuky, částice a efekty. Eventy nepřepisují počasí ani oblohu.
- 130 různých questů, 15 denních kontraktů a 3 týdenní výzvy.
- Sdílená virtuální měna fragmentů pro questy, volitelně Jobs Reborn a nákupy.
- Shop s více než 100 položkami, 13 licencovanými sladkostmi, vlastní ikonou fragmentu a čtyřmi oranžovými variantami zbraní přes ItemsAdder.

Z této sestavy jsou záměrně odstraněné dialogové questy/NPC balíček, tajné místnosti, klíče a nefunkční generátor hrobů či podzemních struktur. Headless Horseman je naopak součástí zdrojového SpookySeason, proto zůstává zachován.
