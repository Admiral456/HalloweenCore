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
- každý elitní mob má vlastní útok: Zombie vysává sílu, Hrobník označuje zem pod hráčem, Krvavý pavouk ho zachytí do krvavé sítě, Dýňový přízrak odpálí oslepující popel a Hexová čarodějka sesílá náhodnou kletbu
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
- Prokletý talisman stojí 1 000 000 fragmentů a vyžaduje prokletí 5; při držení v hlavní nebo vedlejší ruce přidá +20 maximálního zdraví (jeden řádek srdcí navíc) a zachovává +10 % k zisku fragmentů
- limitovaný token Halloween 2026 stojí 30 000 fragmentů a vyžaduje prokletí 5
- po dosažení globálního cíle se natrvalo odemkne serverové finále
- hráči, kteří se vrátí až po odemčení finále, dostanou při připojení upozornění

## Příkazy

- /halloween
- /halloween stats
- /halloween progress
- /halloween curse
- /halloween event
- /halloween event start <random|soulstorm|witching-hour|cursed-harvest|blood-moon-invasion> (admin, testovací okamžité spuštění)
- /halloween event stop (admin)
- /halloween boss test (admin, testovací boss bez progressu, odměn a dokončení finále)
- /halloween shader <on|off|reload> (admin, změna shaderu a automatické předání /iazip)
- /halloween challenge
- /halloween rewards
- /halloween claim <id>
- /halloween top
- /halloween reload
- /halloween debug
- /halloween setvillage (admin)
- /halloween setsecret <id> (admin, nastaví polohu tajného místa)
- /halloween secrets (zobrazí postup a nápovědy bez souřadnic)
- /halloween setvampirearena (admin)
- /halloween buildvampirearena [confirm] (admin, bezpečně postaví kruhovou arénu do volného prostoru uloženého středu)
- /halloween bosseffects <1|2|3|4> (admin, vizuální náhled útoků bez bosse a bez poškození)
- /halloween give <hráč> <počet>
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
| **Krvavý měsíc – invaze** | Silnější vlny až 4 nepřátel každých 25 sekund, nejvýše 48 eventových mobů na svět, unikátní varovné částice, Kapitán invaze v závěrečné části a **3× poškození od nepřátelských monster**. |
| **Dýňová apokalypsa** | Husté vlny dýňových přízraků, plameny, popel a lávové částice; 2,5× odměna za lov. |
| **Hřbitov vstává** | Hrobníci, prokleté zombie a pavouci se objevují ve vlnách z duševní mlhy; 2,25× odměna za lov. |

Každý event má vlastní zvukovou znělku v resource packu. Příkazy `/halloween event`, `/halloween event start <id>` a `/halloween event stop` umožňují správcům ověřit jednotlivé eventy. Dostupné ID jsou `soulstorm`, `witching-hour`, `cursed-harvest`, `blood-moon-invasion`, `pumpkin-apocalypse` a `graveyard-rising`.

Přirozené moby mají šanci změnit se v pět typů speciálních nepřátel. Každý má vlastní 128×128 pixel-art texturu, Blockbench model a animace `idle`, `walk` a `attack`; podle typu také vlastní světelné částice. ModelEngine + MythicMobs definice jsou distribuované odděleně v artefaktu `HalloweenCore-Special-Mobs-ModelEngine.zip`. Pokud některý požadovaný plugin nebo definice nejsou načtené, HalloweenCore použije vanilla fallback, aby lov a eventy zůstaly hratelné.

Při načítání chunků HalloweenCore postupně přidává dýně, jack-o-lanterny, pavučiny a červené svíčky na bezpečná místa s přirozeným terénem. Nezastavuje existující bloky ani bloky s inventářem/entitami; staré chunky se při aktualizaci nedekorují znovu celou dávkou.

- `/halloween boss test` vyvolá testovacího Krále upírů bez globálního progressu, odemčení finále a model-ready gate. Vyžaduje uložený střed arény a funkční MythicMobs mob `vampire-king`; testovací zabití nedává odměny ani neoznačí finále za splněné.

## Návod: aréna, obchod a resource pack

- Správce stojí na bloku, který má být středem podlahy arény, a spustí `/halloween setvampirearena`.
- Vhodné volné místo ověří pomocí `/halloween buildvampirearena`. Pokud náhled potvrdí volný prostor, dokončí stavbu příkazem `/halloween buildvampirearena confirm`. Stavba mění povrch v kruhu o poloměru 48 bloků a staví až 15 bloků vysoké věže. Před potvrzením udělej zálohu světa.
- `/halloween setsecret <id>` ukládá tajné místo a `/halloween secrets` zobrazuje hráčům nápovědy.
- `/halloween shader on|off` upraví oranžový nádech přímo v shaderu uloženém v ItemsAdder a spustí `/iazip`. `/halloween shader reload` jen znovu vygeneruje pack.
- `/halloween rewards` otevře rozšířený 54slotový shop s prostorem až pro 28 položek; `/halloween claim <id>` vyzvedne odměnu podle přesného ID z konfigurace.
- Prokletý talisman stojí 1 000 000 fragmentů. Při držení v hlavní nebo vedlejší ruce přidá +20 k maximálnímu zdraví (jeden celý řádek srdcí) a zachovává 10% bonus k fragmentům.
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

Krvavý strážce má nastavené brnění 6/12/9/6 podle kusu; celá sada tedy dává 33 bodů brnění místo 20 u běžného Netheritu. Meč má attack-damage modifier 15.0 (nad požadovanými 12); přesný výsledný údaj v tooltipu/hit závisí na aplikaci komponent ItemsAdderem, základním atributu hráče a attack cooldownu. Zbroj, meč a nástroje používají moderní item component JSON s enchantability 25; krumpáč, sekera, lopatka a motyka mají navíc tool komponent rychlosti 14 pro správné blokové tagy. Talisman stojí 1 000 000 fragmentů a při držení v ruce nebo offhandu přidává +20 max HP (jeden celý řádek srdcí).

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

Zdrojové Blockbench modely a externí atlasy pro speciální moby jsou v `mythicmobs/models/halloween_*.bbmodel` a `mythicmobs/models/halloween_*.png`. Kontroluje je `python3 scripts/validate_special_mob_models.py`; CI zároveň balí definice a blueprinty do artefaktu `HalloweenCore-Special-Mobs-ModelEngine.zip`.

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

Příkaz `/halloween buildvampirearena` nejprve provede kontrolu volného prostoru a nic nemění. Pokud kontrola projde, ukáže rozsah a vyžádá si výslovné potvrzení příkazem `/halloween buildvampirearena confirm`. Potvrzená stavba vytvoří kruhovou kamennou arénu o průměru 97 bloků, šest soustředných runových kruhů, obvodové cimbuří, osm gotických věží, čtyři monumentální brány a osm vnitřních obelisků. Příkaz vyžaduje nastavený střed přes `/halloween setvampirearena`, načtené chunky, volný prostor nad podlahou a terén bez hlubokých proláklin. Neničí překážky nad budoucí podlahou; pokud tam jsou stromy nebo stavby, stavbu odmítne. Po potvrzení se v kruhu o poloměru 48 bloků upraví povrch terénu a vytvoří nová podlaha. Před spuštěním si udělej zálohu světa.


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
