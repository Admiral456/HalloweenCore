# HalloweenCore

HalloweenCore je herní core pro Halloween update WarriorLandu na Purpur/Paper 1.21.10.

Cíl není přidat jen dekorace nebo pár příkazů. Plugin postupně propojuje běžné hraní s Halloween progresí a serverovým finále.

## Aktuální herní smyčka

- hráč získává Halloween fragmenty za PvE, těžbu, farmení a rybaření
- těžba, sklizeň a rybaření mají nastavitelné minutové limity, aby je nešlo snadno zneužít automatizovanými farmami
- každý hráč má vlastní úroveň prokletí
- vyšší prokletí zvyšuje základní zisk fragmentů
- celý server má společný fragmentový progress a milníky
- náhodně se spouští dočasné Halloween události včetně Krvavého měsíce — invaze s vlnami speciálních mobů a silným kapitánem
- události dávají různé násobiče podle aktivity
- serverové milníky zkracují interval mezi náhodnými událostmi a postupně je zesilují
- speciální Halloween mobové se mohou přirozeně objevit a dávají bonusové fragmenty
- osm typů custom mobů (pět s dodanými ModelEngine modely a texturami; Ženec prázdnoty, Ledový stopař a Noční můra zatím používají vanilla vzhled, dokud jejich autor nedodá vlastní assety): Prokletý zombie, Ženec prázdnoty, Hrobník, Ledový stopař, Krvavý pavouk, Dýňový přízrak, Hexová čarodějka a Noční můra
- každý elitní mob má vlastní efekt nebo útok: Zombie vysává sílu, Hrobník označuje zem pod hráčem, Krvavý pavouk ho zachytí do krvavé sítě, Dýňový přízrak odpálí oslepující popel, Hexová čarodějka sesílá náhodnou kletbu, Ženec prázdnoty vyvolá temnotu, Ledový stopař zpomalí cíle a Noční můra způsobí děsivé vidiny
- útoky mají vlastní cooldowny a varování; značce Hrobníka lze uhnout a výbuch Dýňového přízraku neničí stavby
- speciální mobové mohou dropnout limitované „Prokleté cukroví“
- při návratu na server funguje denní streak a comeback bonus
- každý den má hráč vlastní Halloween lov
- limitované odměny pro Halloween 2026 lze získat pouze jednou za hráče
- odměny jsou navíc gated podle úrovně prokletí, takže nejlepší věci vyžadují aktivní hraní
- v noci se náhodně střídají dvě CC0 strašidelné ambientní skladby; v podzemních jeskyních má vlastní CC0 smyčku přednost před nočním playlistem; ve dne na povrchu Halloween hudba nehraje
- při připojení se zobrazí Halloween title/subtitle
- custom odměny a relikvie mají připravené ItemsAdder ID, skutečné PNG textury a bezpečný vanilla fallback
- Maska nočního lovce dává při nošení +5 % k zisku fragmentů; stojí 4 000 fragmentů a vyžaduje prokletí 3
- Prokletý talisman stojí 1 000 000 fragmentů a vyžaduje prokletí 5; při držení v hlavní nebo vedlejší ruce přidá +2 maximálního zdraví (jedno srdce navíc) a zachovává +10 % k zisku fragmentů
- limitovaný token Halloween 2026 stojí 30 000 fragmentů a vyžaduje prokletí 5
- po dosažení globálního cíle se natrvalo odemkne serverové finále
- hráči, kteří se vrátí až po odemčení finále, dostanou při připojení upozornění

## Příkazy

- /halloween
- /halloween stats
- /halloween progress
- /halloween curse
- /halloween event
- /halloween event start <random|soulstorm|witching-hour|cursed-harvest|blood-moon-invasion|pumpkin-apocalypse|graveyard-rising> (admin, testovací okamžité spuštění)
- /halloween event stop (admin)
- /halloween boss test (admin, testovací boss bez progressu, odměn a dokončení finále)
- /halloween shader <on|off|reload> (admin, změna shaderu a automatické předání /iazip)
- /halloween quests — příběhová kampaň o devíti kapitolách, uložený postup a automatické odměny
- /halloween challenge
- /halloween rewards
- /halloween claim <id>
- /halloween top
- /halloween reload
- /halloween debug
- /halloween spawn <hřbitov|vesnice|aréna|vampirearena|goblinarena> (admin, ihned umístí strukturu před hráčem v režimu FORCE; žádné nastavování středu ani confirm)
- /halloween spawn <cursed-zombie|void-reaper|gravekeeper|frost-stalker|blood-spider|pumpkin-wraith|hex-witch|nightmare> (admin, testovací spawn jednoho mobu; použije ModelEngine/MythicMobs, pokud jsou dostupné, jinak vanilla fallback)
- /halloween status (diagnostika aktivního eventu, povolených automatických světů a stavby)
- Batwing Elytra: nasazená na hrudi zachovává standardní elytra glide/rocket chování; při plachtění stiskni sneak pro Bat Dash (20s cooldown).
- Nocturne Sovereign Cowl: nasaď na hlavu pro noční vidění; stiskni sneak pro zpomalení a odhalení okolních monster (30s cooldown).
- /halloween setsecret <id> (admin, nastaví polohu tajného místa)
- /halloween secrets (zobrazí postup a nápovědy bez souřadnic)
- /halloween bosseffects <1|2|3|4> (admin, vizuální náhled útoků bez bosse a bez poškození)
- /halloween give <hráč> <počet|all> (admin; all vydá všechny nakonfigurované odměny, gear a přenosné relikvie pro testování)
- /halloween on|off

## Boss bar

### Model readiness

Král upírů je navázaný na samostatný model gate. Konfigurace drží `provider`, `id`, minimální rozměry a hlavně `ready: false`; dokud nebude skutečný 3D model s křídly o minimálně 10 blocích výšky a 8 blocích šířky připravený a otestovaný, encounter se nespustí.


Král upírů má připravený vlastní boss bar: HP bar, jméno bosse, automatické zobrazování hráčům v nastaveném radiusu a automatické skrytí po opuštění oblasti nebo smrti bosse. Spawn a finální mechaniky bosse zůstávají oddělené od této vrstvy.

## Vampire encounter assets

V základním repozitáři je připravený dormantní MythicMobs definition `mythicmobs/mobs/vampire-king.yml`. Vlastní 3D model je řízen přes ModelEngine; dokud není `model.ready: true`, MythicMobs a ModelEngine dostupné a aréna nastavená, finální encounter se nespustí.

Jakmile je finále odemčené a vše připravené, HalloweenCore přirozeně spustí znamení a pětiminutové varování. Potom bosse automaticky vyvolá přesně ve středu arény; příkaz `/halloween boss start` zůstává pouze pro admin testy. Útoky mají výrazné telegraphy a hráč je může přečíst a uhnout jim: Falešná kořist, Krvavý puls, Zrcadlový výpad se třemi klamnými runami a Zatmění s matoucími kruhy.

Odměny jsou nastavené jako výzva: účast vyžaduje nejméně 120 sekund v dosahu i způsobení alespoň 2,5 % maximálního zdraví bosse. Top bonus získá pouze kvalifikovaný hráč s nejvyšším poškozením, nikoliv hráč, který jen stál v aréně.

## PlaceholderAPI

Pokud je na serveru nainstalovaný PlaceholderAPI, HalloweenCore registruje vlastní expansion bez dalšího JARu.

Příklady:
- `%halloween_fragments%` — aktuální fragmenty hráče
- `%halloween_lifetime_fragments%` — celoživotně získané fragmenty
- `%halloween_curse_level%` / `%halloween_curse_name%` — prokletí
- `%halloween_multiplier%` — aktuální násobič zisku
- `%halloween_streak%` — návratový streak
- `%halloween_server_fragments%` / `%halloween_global_goal%` / `%halloween_global_percent%` — serverový progress
- `%halloween_event%` / `%halloween_event_remaining%` — aktivní event
- `%halloween_finale_unlocked%` — stav finále
- `%halloween_village_discovered%` — zda hráč objevil Haunted Village

## Připravené integrace

Projekt je navržený tak, aby se dal dál napojovat na pluginy, které už WarriorLand používá:

- MythicMobs
- ItemsAdder (custom item ID + resource-pack content)
- BattlePass
- PyroFishingPro
- PyroFarming
- ExcellentCrates
- DiscordSRV
- PlaceholderAPI
- FancyNPCs / hologramy
- WorldGuard / FAWE

## Halloween hudba a obloha

- Noční playlist náhodně střídá Horror Atmosphere a Creepy Ambient Loop; v jeskyních hraje pouze Dark Cavern Ambient (jeskynní režim má přednost, a to i ve dne). Spooky Fester zůstává zachovaný jen jako starší záložní soundtrack při vypnutí playlistu.
- Skladby jsou z OpenGameArt označené CC0 a jsou automaticky stažené a překódované při CI buildu; licence a zdroje jsou zdokumentované v `MUSIC_LICENSE.md`.
- Playlist kontroluje změnu prostředí přibližně každé 2 sekundy; vypočítané délky každé smyčky odpovídají vytvořenému OGG souboru.
- `scripts/generate_halloween_audio.py` stáhne skladbu `Spooky Fester` od Eldritch Grim z OpenGameArt (licence CC0), převede ji na mono OGG/Vorbis a vygeneruje šest originálních, odlišných eventových znělek pro všech 6 eventů. Vyžaduje přístup k internetu, Python standard library a `ffmpeg`; při buildu se přesná délka ambientní smyčky zapíše do konfigurace.
- Interval přehrávání se při sestavení nastavuje přes `atmosphere.loop-milliseconds` podle skutečné délky `Spooky Fester`. Hudba se přehrává jako zdroj navázaný na hráče v kategorii AMBIENT, aby se nezastavila spolu s vanilla hudbou.
- Zdroj hudby: [Spooky Fester — Eldritch Grim](https://opengameart.org/content/spooky-fester), CC0. OpenGameArt uvádí, že uvedení autora není povinné; zdroj zde přesto evidujeme.
- ItemsAdder zvuky jsou registrované v `itemsadder/contents/warriorland_halloween/configs/sounds.yml`. Ambient používá `warriorland_halloween:haunted_theme`, šest eventů má samostatné ID a `event_sting` zůstává jako obecná znělka.
- Ambientní hudba se spouští hned po připojení a opakuje se samostatně každému hráči podle délky skladby; při odchodu nebo vypnutí eventu se jeho přehrávací úloha zruší, aby nevznikala překrývající se hudba.
- CI vytvoří ke stažení artefakt `WarriorLand-Halloween-ItemsAdder.zip` včetně zvuků.
- Shader oblohy je v `itemsadder/contents/warriorland_halloween/resourcepack/assets/minecraft/shaders/core/sky.fsh`; zachovává základní cyklus dne/noci a přidává oranžovo-karmínový filtr.
- Po nasazení obsahu na server spusť `/iazip` a zajisti, že hráči obdrží nový resource pack. ItemsAdder sloučí obsahy do jednoho packu; `warriorland_halloween` je namespace, ne druhý pack v seznamu Minecraftu. V `/iainfo` ověř hlavně hosting URL/status. Pro ItemsAdder 4.0.17+ je obvykle nejjednodušší `resource-pack.hosting.simple_self_host.enabled: true` a `server_address: auto`; dostupnost doručení na Hostify je nutné ověřit na skutečném serveru. V language souboru ItemsAdder lze nastavit `resourcepack-popup-message` na značkovaný text `&6WarriorLand Halloween 2026`. Vizuální vzhled shaderu je potřeba potvrdit v klientu; shaderový mod jej může přepsat.
- Hlavní hudba má hlasitost zvýšenou na multiplikátor `3.0` (3× výchozí hlasitost). Starší serverové konfigurace se při aktualizaci jednou převedou přes `atmosphere.volume-tripled-v2-migrated`. Resource pack v `resourcepack/assets/minecraft/sounds.json` přepisuje přesně 31 existujících hudebních událostí Minecraftu 1.21.10 tichým OGG; server navíc každou sekundu zastavuje kategorii MUSIC. Halloween soundtrack běží v kategorii AMBIENT, aby ho muter neukončil.
- Custom shop obsahuje čtyři kusy zbroje Krvavého strážce. Každý používá vlastní PNG ikonu a společné 64×32 armor layer_1/layer_2 textury, netheritový základ, brnění 6/12/9/6 podle kusu a vyšší výdrž; běžné enchantování zůstává povolené. Doplňuje je vlastní meč, krumpáč, sekera, lopatka a motyka s transparentními pixel-art ikonami, vyšší výdrží a posílenými atributy; všechny používají netheritové materiály a enchanty nejsou blokované.
- Pokud se pack hráčům vůbec neukáže, spusť `/iainfo` a ověř, že ItemsAdder hlásí dosažitelnou URL resource packu. `/iazip` pouze sestaví ZIP; doručování vyžaduje funkční hosting v `plugins/ItemsAdder/config.yml`. Na ItemsAdder 4.0.17+ lze použít `simple_self_host`; u starších verzí je třeba podporovaný self-host s otevřeným portem nebo externí hosting. Nezaměňuj tento serverový pack s ručně přidávaným packem v seznamu Minecraftu.

## Eventy, speciální mobové a dekorace světa

Náhodné eventy se spouštějí přibližně 15–24 minut od sebe a trvají 6 minut. Po zapnutí Halloween systému přijde první event po krátké prodlevě.

| Event | Co se během něj děje |
|---|---|
| **Duševní bouře** | Vlny prokletých zombie, hrobníků a krvavých pavouků; duševní částice; 2× odměna za lov a 1,5× za rybaření. |
| **Čarodějnická hodina** | Hexové čarodějky a pavouci útočí ze stínů; může se objevit efekt Darkness; zvýšené odměny za těžbu, lov a rybaření. |
| **Prokletá sklizeň** | Temná magie urychlí několik okolních plodin a ze záhonů vyrazí speciální mobové; 3× odměna za sklizeň. |
| **Krvavý měsíc – invaze** | Vlny nepřátel až u 3 hráčů každých 25 sekund, nejvýše 48 eventových mobů na svět, unikátní varovné částice, Kapitán invaze v závěrečné části a **3× poškození od nepřátelských monster**. |
| **Dýňová apokalypsa** | Husté vlny dýňových přízraků, plameny, popel a lávové částice; 2,5× odměna za lov. |
| **Hřbitov vstává** | Hrobníci, prokleté zombie a pavouci se objevují ve vlnách z duševní mlhy; 2,25× odměna za lov. |

Každý event má vlastní zvukovou znělku v resource packu. Příkazy `/halloween event`, `/halloween event start <id>` a `/halloween event stop` umožňují správcům ověřit jednotlivé eventy. Dostupné ID jsou `soulstorm`, `witching-hour`, `cursed-harvest`, `blood-moon-invasion`, `pumpkin-apocalypse` a `graveyard-rising`.

Přirozené moby se mohou změnit v osm typů speciálních nepřátel. Pět typů má v repozitáři již dodané ModelEngine modely a textury; Ženec prázdnoty, Ledový stopař a Noční můra zatím používají plně hratelný vanilla vzhled, dokud jejich modely a textury nedodá jejich autor. CI tyto assety nevytváří ani nepřebarvuje. Existující ModelEngine + MythicMobs soubory se balí do artefaktu `HalloweenCore-Special-Mobs-ModelEngine.zip`. Pokud některý požadovaný plugin nebo definice nejsou načtené, HalloweenCore použije vanilla fallback, aby lov a eventy zůstaly hratelné.

Při načítání nových i starších chunků HalloweenCore postupně přidává dýně, jack-o-lanterny, pavučiny, červené svíčky, hřiby, náhrobky, duševní lucerny a suché keře na bezpečná místa s přirozeným terénem. Nezastavuje existující bloky ani bloky s inventářem/entitami; starší chunky dostanou nové atmosférické vrstvy, aniž by se opakovala celá původní dávka dekorací.

- `/halloween boss test` vyvolá testovacího Krále upírů bez globálního progressu, odemčení finále a model-ready gate. Vyžaduje uložený střed arény a funkční MythicMobs mob `vampire-king`; testovací zabití nedává odměny ani neoznačí finále za splněné.

## Návod: aréna, obchod a resource pack

- Spusť příkaz `/halloween spawn vampirearena` pro automatické umístění upíří arény před sebou. Střed arény se uloží po dokončení stavby; není potřeba používat `setvampirearena` ani `confirm`.
- `/halloween spawn goblinarena` vytvoří samostatnou arénu Goblin Kinga a nezasahuje do identity upíří arény.
- Ruční stavby používají FORCE uvnitř jasně omezeného footprintu a mohou smazat přírodní bloky nebo stavby v tomto prostoru. Před použitím na produkčním světě udělej zálohu.
- `/halloween setsecret <id>` ukládá tajné místo a `/halloween secrets` zobrazuje hráčům nápovědy.
- `/halloween shader on|off` upraví oranžový nádech přímo v shaderu uloženém v ItemsAdder a spustí `/iazip`. `/halloween shader reload` jen znovu vygeneruje pack.
- `/halloween rewards` otevře rozšířený 54slotový shop s prostorem až pro 28 položek; `/halloween claim <id>` vyzvedne odměnu podle přesného ID z konfigurace.
- Prokletý talisman stojí 1 000 000 fragmentů. Při držení v hlavní nebo vedlejší ruce přidá +2 k maximálnímu zdraví (jedno srdce navíc) a zachovává 10% bonus k fragmentům.
- Po nahrání obsahu do `plugins/ItemsAdder/contents/warriorland_halloween` restartuj server, spusť `/iazip` a ověř `/iainfo`. Pokud není URL resource packu dosažitelná nebo se žádná výzva neobjeví, je nutné opravit hosting resource packu v ItemsAdder, ne plugin HalloweenCore.

## Směr dalšího vývoje

- Haunted Village
- hlubší systém relikvií
- resource-pack Halloween obloha
- ověřit Halloween soundtrack a oranžovo-karmínovou oblohu v reálném klientu Minecraftu 1.21.10
- rozšiřování tajných úkolů a easter eggů (základ skrytých lokací už je připraven)
- větší eventové invaze
- serverové finále a hlavní boss
- upíří boss má už nyní pevnou minimální specifikaci: 10 bloků výšky a 8 bloků šířky včetně křídel

Boss je záměrně až pozdější fáze vývoje, aby se napojil na hotový progres, milníky a eventový systém. Rozměrové minimum je už zamčené v konfiguraci i v dokumentu `VAMPIRE_BOSS.md`: minimálně 10 bloků výšky a 8 bloků šířky včetně křídel. Mechaniky a finální vzhled zatím nejsou předčasně uzamčené.

## Generování struktur a pravidla světů

- `/halloween spawn hřbitov`, `/halloween spawn vesnice`, `/halloween spawn aréna`, `/halloween spawn vampirearena` a `/halloween spawn goblinarena` jsou ruční admin příkazy. Umístí stavbu před hráče bez výběru středu a bez `confirm`.
- Přímé ruční stavby používají FORCE uvnitř konečného, ohraničeného footprintu a výšky. Mohou odstranit stromy a bloky uvnitř tohoto prostoru; změny se dávkují po tickách a při chybě se plugin pokusí o rollback. Udělej zálohu, než je použiješ na produkčním světě.
- Pro testování mobů použij `/halloween spawn <mob-id>`; výčet je v seznamu příkazů. ModelEngine/MythicMobs vizuály jsou volitelné; vanilla fallback zachovává základní hraní.
- Výchozí nastavení používá `automatic-world-generation.allowed-worlds: ["*"]`: Halloween prvky, speciální přirozené moby a eventy se mohou objevovat ve všech světech kromě světa pojmenovaného přesně `Spawn`. Chceš-li obsah omezit, nahraď `*` seznamem konkrétních názvů světů.
- Generování běží i při načítání již prozkoumaných chunků. Dekorace je pouze přidávací: umísťuje prvky jen do vzduchu nad bezpečným přírodním terénem a nepřepisuje existující bloky. Náhodné malé hřbitovy a vesnice se pokoušejí vznikat v rozestupech; vyžadují vhodný, rovný přírodní povrch, takže ne každý kandidátní chunk strukturu dostane.
- Každý chunk dostane omezenou směs dýní, pavučin, červených svíček, hřibů, malých náhrobků, duševních luceren a suchých keřů podle terénu. Starší chunky z dřívější verze dostanou nové atmosférické vrstvy bez opakovaného přidávání celé původní dávky dýní a pavučin.
- `/halloween status` vypíše, které světy jsou povolené pro automatické generování. Po změně konfigurace použij `/halloween reload`.

## Přenosné relikvie a jejich assety

Serverová logika Batwing Elytra a Nocturne Sovereign Cowl funguje přes stabilní PDC identity. Pokud ItemsAdder obsahuje ID `warriorland_halloween:batwing_elytra` a `warriorland_halloween:nocturne_sovereign_cowl`, použije se jejich skutečný vlastní item; jinak plugin dá funkční vanilla fallback (elytra / carved pumpkin). **Vizuál vlastních křídel a kapuce zůstává neověřený a vyžaduje dodání modelů/textur a jejich kontrolu v ModelEngine/ItemsAdder resource packu.**

## ItemsAdder assety

Vlastní PNG jsou v `itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/textures/item/`.

V repozitáři je připravený namespace `warriorland_halloween`:

- `itemsadder/contents/warriorland_halloween/configs/items.yml`
- `itemsadder/contents/warriorland_halloween/resourcepack/assets/warriorland_halloween/textures/item/*.png` — vlastní textury
- `itemsadder/contents/warriorland_halloween/ASSETS.md` — seznam assetů

Po nasazení obsahu do ItemsAdder je potřeba znovu vygenerovat resource pack přes `/iazip`.


### Krvavý strážce — statistiky gearu

Všechny kusy mají vlastní původní černo-karmínové PNG ikony; zbroj navíc používá stávající custom layer_1/layer_2 atlasy.

| Kus | Statistika | Netherite baseline | Nastavení HalloweenCore |
|---|---|---|---|
| Helma | Brnění / výdrž | 3 / 407 | 6 / 900 |
| Kyrys | Brnění / výdrž | 8 / 592 | 12 / 1 300 |
| Nohavice | Brnění / výdrž | 6 / 555 | 9 / 1 150 |
| Boty | Brnění / výdrž | 3 / 481 | 6 / 950 |
| Celá sada | Celkem brnění | 20 bodů | 33 bodů |
| Meč | Útokový modifier / výdrž | vanilla Netherite / 2 031 | 15.0 / 5 000 |
| Krumpáč | Modifier útoku / těžba / výdrž | vanilla Netherite / rychlost 9 / 2 031 | 10.0 / 14 / 5 000 |
| Sekera | Modifier útoku / těžba / výdrž | vanilla Netherite / rychlost 9 / 2 031 | 14.0 / 14 / 5 000 |
| Lopatka | Modifier útoku / těžba / výdrž | vanilla Netherite / rychlost 9 / 2 031 | 10.0 / 14 / 4 500 |
| Motyka | Modifier útoku / těžba / výdrž | vanilla Netherite / rychlost 9 / 2 031 | 7.0 / 14 / 4 500 |

Krvavý strážce má nastavené brnění 6/12/9/6 podle kusu; celá sada tedy dává 33 bodů brnění místo 20 u běžného Netheritu. Meč má attack-damage modifier 15.0 (nad požadovanými 12); přesný výsledný údaj v tooltipu/hit závisí na aplikaci komponent ItemsAdderem, základním atributu hráče a attack cooldownu. Zbroj, meč a nástroje používají moderní item component JSON s enchantability 25; krumpáč, sekera, lopatka a motyka mají navíc tool komponent rychlosti 14 pro správné blokové tagy. Talisman stojí 1 000 000 fragmentů a při držení v ruce nebo offhandu přidává +2 max HP (jedno srdce navíc).

## Boss placeholders

- `%halloween_vampire_boss_active%` — běží encounter
- `%halloween_vampire_boss_phase%` — aktuální fáze 1–4
- `%halloween_vampire_boss_hp_percent%` — zbývající HP v procentech
- `%halloween_vampire_boss_participants%` — počet účastníků encounteru
- `%halloween_vampire_boss_defeated%` — zda už byl finální boss poražen

### Hudba a licence

Halloween soundtrack je **Spooky Fester** od Eldritch Grim z OpenGameArt (CC0); CI jej stáhne a převede do OGG při sestavení packu. Aktuální ID je `warriorland_halloween:haunted_theme`. Hlavní ambient má nakonfigurovaný gain 3.0 (Bukkit volume multiplier), běží v kategorii AMBIENT a server každou sekundu zastavuje vanilla kategorii MUSIC. Resource pack má přepsané 31 běžných událostí vanilla hudby na tichý OGG soubor. Pokud se přesto hraje klasická hudba, nejprve ověř, že hráč skutečně obdržel nový pack přes ItemsAdder — samotný serverový kód nedokáže přepsat zvuk z jiného klientského/resource packu.

## Build

Java 21 + Maven.

```
mvn clean package
```

Výstup:

`target/HalloweenCore.jar`


## Speciální mobové: modely a resource pack

Dodané Blockbench modely a atlasy pro pět vizuálně dokončených speciálních mobů jsou v `mythicmobs/models/halloween_*.bbmodel` a `mythicmobs/models/halloween_*.png`. Modely a textury pro Žence prázdnoty, Ledového stopaře a Noční můru doplní jejich autor; dokud nejsou obě části dodané, zůstávají tito mobové na vanilla vzhledu. Kontroluje je `python3 scripts/validate_special_mob_models.py`; CI pouze ověřuje a balí přítomné soubory do artefaktu `HalloweenCore-Special-Mobs-ModelEngine.zip` a assety negeneruje ani nepřebarvuje.

Po instalaci na server rozbal tento balíček do složky `plugins/`, naimportuj blueprinty přes ModelEngine, spusť `/meg reload models` a `/mm reload`. Do stávajícího seznamu `merge_other_plugins_resourcepacks_folders` v konfiguraci ItemsAdderu přidej cestu `ModelEngine/resource pack`, aby se modelové textury dostaly do stejného klientského packu. Poté spusť `/iazip`, ověř `/iainfo`, přijmi pack na klientovi a znovu se připoj.

## Diagnostika hudby a klientského packu

Resource pack nahrazuje 31 standardních událostí **hudby na pozadí** Minecraftu tichým OGG a plugin navíc jednou za sekundu zastavuje kategorii `MUSIC`. To není možné ověřit pouze kompilací: klient musí stáhnout a přijmout nejnovější pack. Hudební disky/jukebox a běžné zvukové efekty se tímto seznamem nepotlačují.

- Ověř `/halloween debug` a `/iainfo`.
- Ve hře zkus `/playsound minecraft:music.game master @s` — se správným packem by se tato hudební událost měla ozvat potichu/nebýt slyšet.
- Ověř `/playsound warriorland_halloween:haunted_theme ambient @s` a `/playsound warriorland_halloween:blood_moon_rise ambient @s`.
- Po změně zvuků proveď `/iazip`, nech klienta přijmout změněný resource pack a připoj se znovu.

CI dokládá strukturu PNG, OGG, zvukových registrací, modelů a JAR build; skutečné stažení packu, rendering ModelEngine a zastavení vanilla hudby je nutné potvrdit na živém Hostify serveru.

## Skutečný model Krále upírů

Repozitář obsahuje editovatelný Blockbench blueprint `mythicmobs/models/vampire_king.bbmodel` a texturu `mythicmobs/models/vampire_king.png`. Model má animace `idle`, `walk`, `attack` a `fly`; ostré útoky používají stejné animované telegraphy jako testovací příkazy a zásah se vyhodnocuje až na konci animace. Před produkční aktivací je nutné model načíst do ModelEngine, distribuovat resource pack a ověřit klientský render. Strukturní kontrola: `python3 scripts/validate_vampire_model.py`.


## Generování upíří arény

Přímá stavba arén se spouští příkazem `/halloween spawn vampirearena` nebo `/halloween spawn goblinarena`. Obě struktury se umisťují před hráče bez nastavování středu a bez potvrzovacího kroku. Režim FORCE může přepsat bloky pouze uvnitř footprintu stavby; přesto před použitím na produkčním světě vytvoř zálohu.


## Krvavý měsíc — invaze

Mezi náhodnými událostmi je nově i invaze. Během ní se v okolí aktivních hráčů objevují vlny speciálních mobů, jejich počet v jedné vlně lze nastavit přes `random-events.invasion-mobs-per-surge` (1–4) a celkový limit hlídá `random-events.max-event-mobs`. V závěrečné části se pokusí objevit silnější Kapitán krvavé invaze. Eventové moby jsou označené a po skončení invaze se uklidí; invaze sama o sobě neodemkne ani nespustí finálního bosse.


## Tajné objevy a easter eggy

Tři definice skrytých míst jsou v `secret-discoveries.locations`: `blood-altar`, `witch-den` a `forgotten-grave`. Administrátor se postaví na přesné místo a nastaví ho příkazem `/halloween setsecret <id>`. Hráč příkazem `/halloween secrets` uvidí počet objevených míst a textové nápovědy k dosud nalezeným tajemstvím — nikdy ne jejich souřadnice. Po vstupu do blízkosti místa se tajemství uloží do `plugins/HalloweenCore/data.yml` a hráč obdrží jednorázovou fragmentovou odměnu. Nálezy zůstávají uložené po restartu serveru.


## Synchronizace animací bosse

Encounter při každém speciálním útoku vyvolá jednorázovou animaci `attack` na aktivním modelu ModelEngine ještě před dokončením telegraphu a vyhodnocením zásahu. Při vstupu do čtvrté fáze navíc spustí smyčkovou animaci `fly`, která může běžet souběžně s útoky. Volání používá reflexi, takže plugin nemá tvrdou závislost na ModelEngine při startu; pokud API neodpovídá očekávanému rozhraní, zaznamená pouze jedno varování. Produkční test musí potvrdit skutečný klientský render.


## Čtyři samostatné animace útoků

Model nyní obsahuje vlastní jednorázové animace false_sigil, blood_pulse, mirror_strike a nightfall. Encounter vybírá odpovídající animaci podle fáze bosse a spustí ji před telegraphem; pokud nainstalovaná verze ModelEngine některou animaci neumí přehrát, použije základní attack animaci jako bezpečný fallback. Validátor kontroluje přítomnost všech čtyř fázových animací.


## Bezpečný náhled modelu

Administrátor může použít `/halloween modelpreview <idle|walk|attack|fly|false_sigil|blood_pulse|mirror_strike|nightfall>`. Příkaz vytvoří čtyři bloky před hráčem dočasný, nezranitelný model přes MythicMobs a ModelEngine, vypne jeho AI a odstraní ho po 10 sekundách. Náhled nespustí encounter, nepočítá účast a neuděluje odměny. Pořád je nutné ověřit render v klientovi s přijatým resource packem.
