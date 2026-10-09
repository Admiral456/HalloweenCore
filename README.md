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

- `scripts/generate_halloween_audio.py` stáhne skladbu `Spooky Fester` od Eldritch Grim z OpenGameArt (licence CC0), převede ji na mono OGG/Vorbis a vygeneruje originální 5sekundový event cue. Vyžaduje přístup k internetu, Python standard library a `ffmpeg`; nastaví také přesnou délku ambientní smyčky podle výsledného OGG souboru.
- Interval přehrávání se při sestavení nastavuje přes `atmosphere.loop-milliseconds` podle skutečné délky `Spooky Fester`. Hudba se přehrává jako zdroj navázaný na hráče v kategorii AMBIENT, aby se nezastavila spolu s vanilla hudbou.
- Zdroj hudby: [Spooky Fester — Eldritch Grim](https://opengameart.org/content/spooky-fester), CC0. OpenGameArt uvádí, že uvedení autora není povinné; zdroj zde přesto evidujeme.
- ItemsAdder zvuky jsou registrované v `itemsadder/contents/warriorland_halloween/configs/sounds.yml`. Plugin používá `warriorland_halloween:haunted_theme` pro atmosféru a `warriorland_halloween:event_sting` na začátku náhodného eventu.
- Ambientní hudba se spouští hned po připojení a opakuje se samostatně každému hráči podle délky skladby; při odchodu nebo vypnutí eventu se jeho přehrávací úloha zruší, aby nevznikala překrývající se hudba.
- CI vytvoří ke stažení artefakt `WarriorLand-Halloween-ItemsAdder.zip` včetně zvuků.
- Shader oblohy je v `itemsadder/contents/warriorland_halloween/resourcepack/assets/minecraft/shaders/core/sky.fsh`; zachovává základní cyklus dne/noci a přidává oranžovo-karmínový filtr.
- Po nasazení obsahu na server spusť `/iazip` a zajisti, že hráči obdrží nový resource pack. ItemsAdder sloučí obsahy do jednoho packu; `warriorland_halloween` je namespace, ne druhý pack v seznamu Minecraftu. V `/iainfo` ověř hlavně hosting URL/status. Pro ItemsAdder 4.0.17+ je obvykle nejjednodušší `resource-pack.hosting.simple_self_host.enabled: true` a `server_address: auto`; dostupnost doručení na Hostify je nutné ověřit na skutečném serveru. V language souboru ItemsAdder lze nastavit `resourcepack-popup-message` na značkovaný text `&6WarriorLand Halloween 2026`. Vizuální vzhled shaderu je potřeba potvrdit v klientu; shaderový mod jej může přepsat.
- Hlavní hudba má hlasitost zvýšenou na multiplikátor `3.0` (3× výchozí hlasitost). Starší serverové konfigurace se při aktualizaci jednou převedou přes `atmosphere.volume-tripled-v2-migrated`. Resource pack v `resourcepack/assets/minecraft/sounds.json` přepisuje přesně 31 existujících hudebních událostí Minecraftu 1.21.10 tichým OGG; server navíc každou sekundu zastavuje kategorii MUSIC. Halloween soundtrack běží v kategorii AMBIENT, aby ho muter neukončil.
- Custom shop obsahuje čtyři kusy zbroje Krvavého strážce. Každý používá vlastní PNG ikonu a společné 64×32 armor layer_1/layer_2 textury, netheritový základ, +1 armor na kus a vyšší výdrž; běžné enchantování zůstává povolené. Doplňuje je vlastní meč, krumpáč, sekera, lopatka a motyka s transparentními pixel-art ikonami, vyšší výdrží a posílenými atributy; všechny používají netheritové materiály a enchanty nejsou blokované.
- Pokud se pack hráčům vůbec neukáže, spusť `/iainfo` a ověř, že ItemsAdder hlásí dosažitelnou URL resource packu. `/iazip` pouze sestaví ZIP; doručování vyžaduje funkční hosting v `plugins/ItemsAdder/config.yml`. Na ItemsAdder 4.0.17+ lze použít `simple_self_host`; u starších verzí je třeba podporovaný self-host s otevřeným portem nebo externí hosting. Nezaměňuj tento serverový pack s ručně přidávaným packem v seznamu Minecraftu.

## Eventy, test bosse a dekorace světa

- `/halloween on` aktivuje atmosféru a naplánuje první náhodný event přibližně za 30 sekund; další eventy se spouštějí v běžném intervalovém nastavení.
- `/halloween event` zobrazuje stav; admin může spustit `/halloween event start random` nebo určit `soulstorm`, `witching-hour`, `cursed-harvest` či `blood-moon-invasion`. `/halloween event stop` event ukončí.
- `/halloween boss test` vyvolá testovacího Krále upírů bez globálního progressu, odemčení finále a model-ready gate. Vyžaduje uložený střed arény a funkční MythicMobs mob `vampire-king`; testovací zabití nedává odměny ani neoznačí finále za splněné.
- Při zapnutém eventu se při načítání chunků na přirozeném terénu postupně objeví dýně, jack-o-lanterny a ojedinělé pavučiny. Jednotlivé chunky se označí, aby se dekorace při restartu neopakovaly. Dekorace nepřepisují existující bloky, ale na přírodně vypadající trávě u staveb je vhodné zkontrolovat výsledek.

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
