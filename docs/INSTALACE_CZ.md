# HalloweenCore — instalační příručka (CZ)

## 1. Požadavky

- Server **Paper nebo Purpur 26.1.2** (tento artefakt je cílen na tuto verzi API).
- **Java 25 nebo novější**; řada Minecraft 26.1.x ji vyžaduje.
- Samotný HalloweenCore je jediný nutný plugin pro questy, fragmenty, shop a eventy.
- **ItemsAdder 4.0.13+** je potřeba pro vlastní fragmentovou texturu, sladkosti a oranžové zbraně. Obarvení používá moderní systém ItemsAdder pro Minecraft 1.21.4+; Netherite Spear existuje v Java Edition od 1.21.11+, proto je celý katalog pěti oranžových zbraní dostupný na cílovém 26.1.2.
- **Jobs Reborn 5.2.6.7+** je volitelný: bez něj questy, eventy i shop fungují, jen nepřibývají fragmenty za Jobs XP.
- Další volitelné integrace: PlaceholderAPI, BetonQuest, Citizens, MythicMobs, WorldGuard, GriefPrevention a CustomJukebox. Instaluj pouze ty, jejichž funkce chceš využít.

## 2. Instalace

1. Udělej zálohu světa a složek `plugins/HalloweenCore/` a `plugins/ItemsAdder/`.
2. Zastav server; nepoužívej PlugMan/PlugManX k hot-reloadu.
3. Nahraď starý `plugins/HalloweenCore.jar` novým souborem `HalloweenCore.jar`.
4. Rozbal `WarriorLand-Halloween-ItemsAdder-Content.zip` do `plugins/ItemsAdder/contents/`. Výsledná cesta musí být:
   `plugins/ItemsAdder/contents/warriorland_halloween/configs/items.yml`
   a vedle ní `configs/orange_weapons.yml` a složka `resourcepack/`.
5. Spusť server a zkontroluj konzoli. Při prvním startu se vytvoří/načte `plugins/HalloweenCore/config.yml` a data hráčů se uchovávají v `plugins/HalloweenCore/players.yml`.
6. Pokud používáš ItemsAdder, spusť `/iazip`, ověř distribuci resource packu a přijetí u hráčů.
7. Udělej kontrolní test: `/quest`, `/halloween fragments`, `/halloween shop`, `/spooky status` a `/horrornights status`. Před otevřením serveru hráčům zkontroluj logy, náhodnou hororovou noc, příkazy admina a vizuál resource packu.

Sezóna je nastavena na **23. října až 2. listopadu včetně** v pásmu `Europe/Prague`. Questy a zůstatky jsou per-player uloženy v `players.yml`; před upgradem tuto složku nemaž.

## 3. Příkazy

### Hráči

- `/quest` — přehled denních a týdenních kontraktů.
- `/quest daily` — denní kontrakty; `/quest weekly` — týdenní výzvy.
- `/halloween shop` — halloweenský obchod.
- `/halloween fragments` — zůstatek fragmentů.
- `/spooky status` — stav SpookySeason.
- `/spooky optout` — zapnutí/vypnutí účasti na osobních funkcích; `/spooky menu` — menu.
- `/spooky top` — žebříček sladkostí.
- `/spookyvolume <ambient|ghost|rain|reset> [0.0–1.0]` — osobní hlasitost strašidelných zvuků.

### Admin / eventy

- `/halloween reload` — znovunačtení konfigurace HalloweenCore.
- `/horrornights <start|stop|status|reload|help> [svět]` — HorrorNights; aliasy `/hn` a `/horror`.
- `/pumpkinrain <start|stop>` — dýňový déšť.
- `/bloodrain <start|stop>` — krvavý déšť tvořený částicemi, bez změny počasí světa.
- `/spooky <on|off|status|reload|rewards|spawn|optout|top|menu>` — řízení a přehled sezónního modulu.
- `/spookyboss <spawn|remove|info|set|armor|ability|loot>` — Headless Horseman.
- `/spookyraid <start|stop|status|stats|target|zone>` — nájezd nemrtvých.
- `/halloween quest list` — seznam dialogových questů; `/halloween quest status <id>` — stav questu.
- `/halloween quest start <id>`, `progress <id> [množství]`, `complete <id>` — integrační akce pro dialogové/questové pluginy.

Akce `start/progress/complete` pro dialogové questy vyžadují `halloweencore.quest.dialogue`. Admin příkazy mají výchozí oprávnění OP: `halloweencore.admin`, `horrornights.admin`, `spooky.admin`, `spooky.pumpkinrain` a `spooky.bloodrain`.

## 4. Questy, Jobs a shop

Každý den se losuje 15 kontraktů z poolu 130 úkolů a obnovují se 3 týdenní výzvy. Odměny rostou s obtížností: 1–2, 3–5, 6–9, 10–15 a 16–24 fragmentů. Denní kompletní bonus je 15 fragmentů, týdenní odměna má koeficient 3× a bonus za všechny tři týdenní výzvy je 50 fragmentů.

Jobs Reborn přidává 1 fragment za každých 25 Jobs XP, nejvýše 8 fragmentů denně. Questy, Jobs a shop používají jeden společný virtuální zůstatek. Enchanted Golden Apple stojí 750 fragmentů; ceny upravíš v `shop.prices` v `config.yml`.

## 5. Co je součástí

- plný SpookySeason: Trick-or-Treat, duchové a strašidelné noci, roje netopýrů, dýňový déšť, krvavý déšť jako částice, Headless Horseman, sezónní mobové, nájezdy, odměny, žebříček, menu a nastavení zvuků/opt-out;
- nezávislý HorrorNights: náhodné hororové noci, výraznější mobové, ambientní zvuky, částice a náhodné efekty;
- 130 definic questů, 15 denních kontraktů, 3 týdenní výzvy, persistentní zůstatek a Jobs Reborn bridge;
- shop s více než 100 běžnými i prémiovými předměty, včetně Enchanted Golden Apple, Totemu, Beaconu, Nether Star, Elytry a drahých halloweenských zbraní;
- 13 existujících licencovaných sladkostí, vlastní 64×64 pixel-art ikona fragmentu a generátor pěti oranžových zbraní.

Tajné místnosti, klíče a nefunkční generátor hrobů/podzemních struktur jsou záměrně vynechané. Eventy nenasazují bouřku ani nepřepisují oblohu. Automatický build a validace CI úspěšně ověřují kompilaci a balíčky, ale nenahrazují finální živý test na konkrétním serveru.
