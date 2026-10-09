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
- během eventu běží atmosférická smyčka zvuku
- při připojení se zobrazí Halloween title/subtitle
- custom odměny a relikvie mají připravené ItemsAdder ID, skutečné PNG textury a bezpečný vanilla fallback
- Maska nočního lovce dává při nošení +5 % k zisku fragmentů; stojí 4 000 fragmentů a vyžaduje prokletí 3
- Prokletý talisman dává při držení +10 % k zisku fragmentů; stojí 15 000 fragmentů a vyžaduje prokletí 5
- limitovaný token Halloween 2026 stojí 30 000 fragmentů a vyžaduje prokletí 5
- po dosažení globálního cíle se natrvalo odemkne serverové finále
- hráči, kteří se vrátí až po odemčení finále, dostanou při připojení upozornění

## Příkazy

- /halloween
- /halloween stats
- /halloween progress
- /halloween curse
- /halloween event
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

- `scripts/generate_halloween_audio.py` generuje originální 64sekundovou ambientní smyčku a 5sekundový event cue jako mono OGG/Vorbis; potřebuje Python standard library a `ffmpeg`.
- ItemsAdder zvuky jsou registrované v `itemsadder/contents/warriorland_halloween/configs/sounds.yml`. Plugin používá `warriorland_halloween:haunted_theme` pro atmosféru a `warriorland_halloween:event_sting` na začátku náhodného eventu.
- CI vytvoří ke stažení artefakt `WarriorLand-Halloween-ItemsAdder.zip` včetně zvuků.
- Shader oblohy je v `itemsadder/contents/warriorland_halloween/resourcepack/assets/minecraft/shaders/core/sky.fsh`; zachovává základní cyklus dne/noci a přidává oranžovo-karmínový filtr.
- Po nasazení obsahu na server spusť `/iazip` a zajisti, že hráči obdrží nový resource pack. Vizuální vzhled je potřeba potvrdit v klientu; shaderový mod jej může přepsat.

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


### Boss placeholders

- `%halloween_vampire_boss_active%` — běží encounter
- `%halloween_vampire_boss_phase%` — aktuální fáze 1–4
- `%halloween_vampire_boss_hp_percent%` — zbývající HP v procentech
- `%halloween_vampire_boss_participants%` — počet účastníků encounteru
- `%halloween_vampire_boss_defeated%` — zda už byl finální boss poražen

### Hudba a licence

Halloween soundtrack není AI-generovaný. Připravený sound ID je `halloween:haunted_theme`; pro resource pack počítáme s hudbou pod **CC0** s dohledatelným původem. Momentálně je v repozitáři pouze licence/source záznam, ne samotný audio soubor.

## Build

Java 21 + Maven.

```
mvn clean package
```

Výstup:

`target/HalloweenCore.jar`


## Skutečný model Krále upírů

Repozitář obsahuje editovatelný Blockbench blueprint `mythicmobs/models/vampire_king.bbmodel` a texturu `mythicmobs/models/vampire_king.png`. Model má animace `idle`, `walk`, `attack` a `fly`; ostré útoky používají stejné animované telegraphy jako testovací příkazy a zásah se vyhodnocuje až na konci animace. Před produkční aktivací je nutné model načíst do ModelEngine, distribuovat resource pack a ověřit klientský render. Strukturní kontrola: `python3 scripts/validate_vampire_model.py`.


## Generování upíří arény

Příkaz `/halloween buildvampirearena` nejprve provede kontrolu volného prostoru a nic nemění. Pokud kontrola projde, ukáže rozsah a vyžádá si výslovné potvrzení příkazem `/halloween buildvampirearena confirm`. Potvrzená stavba vytvoří kruhovou kamennou arénu o průměru 45 bloků, obvodovou zeď s průchody, osm věží se soul lanternami a krvavý runový vzor. Příkaz vyžaduje nastavený střed přes `/halloween setvampirearena`, stejné načtené světlo a volný prostor nad podlahou. Neničí překážky nad budoucí podlahou; pokud tam jsou stromy nebo stavby, stavbu odmítne. Horní vrstva terénu v kruhu se po potvrzení nahradí novou podlahou.


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
