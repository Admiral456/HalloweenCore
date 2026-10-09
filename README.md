# HalloweenCore

HalloweenCore je herní core pro Halloween update WarriorLandu na Purpur/Paper 1.21.10.

Cíl není přidat jen dekorace nebo pár příkazů. Plugin postupně propojuje běžné hraní s Halloween progresí a serverovým finále.

## Aktuální herní smyčka

- hráč získává Halloween fragmenty za PvE, těžbu, farmení a rybaření
- každý hráč má vlastní úroveň prokletí
- vyšší prokletí zvyšuje základní zisk fragmentů
- celý server má společný fragmentový progress a milníky
- náhodně se spouští dočasné Halloween události
- události dávají různé násobiče podle aktivity
- serverové milníky zkracují interval mezi náhodnými událostmi a postupně je zesilují
- speciální Halloween mobové se mohou přirozeně objevit a dávají bonusové fragmenty
- každý typ elitního moba má vlastní schopnost: vysátí síly, odloženou značku hrobu, krvavou síť, popelavou explozi nebo náhodnou kletbu
- výbuch Dýňového přízraku nepoškozuje stavby
- speciální mobové mohou dropnout limitované „Prokleté cukroví“
- při návratu na server funguje denní streak a comeback bonus
- každý den má hráč vlastní Halloween lov
- limitované odměny pro Halloween 2026 lze získat pouze jednou za hráče
- obchod kombinuje vysokou cenu a požadovanou úroveň prokletí; nejvzácnější památeční token stojí 40 000 fragmentů
- nejvyšší úroveň prokletí vyžaduje 25 000 celoživotně získaných fragmentů
- účastnická odměna bosse vyžaduje alespoň 5 minut účasti, největší příspěvek se určuje podle uděleného poškození
- během eventu běží atmosférická smyčka zvuku
- při připojení se zobrazí Halloween title/subtitle
- custom odměny a relikvie mají připravené ItemsAdder ID, skutečné PNG textury a bezpečný vanilla fallback
- Maska nočního lovce dává při nošení +5 % k zisku fragmentů
- Prokletý talisman dává při držení +10 % k zisku fragmentů
- po dosažení globálního cíle se natrvalo odemkne serverové finále; po pěti minutách se připraví automatické vyvolání, až bude model připravený a hráč dorazí k aréně
- před spawnem proběhne třicetisekundové varování se souřadnicemi středu arény
- finále se spustí přirozeně příchodem hráče do oblasti; administrátorský příkaz zůstává jako testovací možnost
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
- /halloween setvampirearena (admin)
- /halloween give <hráč> <počet>
- /halloween on|off

## Boss bar

### Model readiness

Král upírů je navázaný na samostatný model gate. Konfigurace drží `provider`, `id`, minimální rozměry a hlavně `ready: false`; dokud nebude skutečný 3D model s křídly o minimálně 10 blocích výšky a 8 blocích šířky připravený a otestovaný, encounter se nespustí.


Král upírů má připravený vlastní boss bar: HP bar, jméno bosse, automatické zobrazování hráčům v nastaveném radiusu a automatické skrytí po opuštění oblasti nebo smrti bosse. Spawn a finální mechaniky bosse zůstávají oddělené od této vrstvy.

## Vampire encounter assets

V základním repozitáři je připravený dormantní MythicMobs definition `mythicmobs/mobs/vampire-king.yml`. Vlastní 3D model je řízen odděleně přes ModelEngine; dokud není `model.ready: true` a ModelEngine nainstalovaný, finální encounter se nespustí. MythicMobs boss bar je záměrně vypnutý, protože HP/účast/fáze řídí HalloweenCore vlastním boss barem.

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

## Směr dalšího vývoje

- Haunted Village
- hlubší systém relikvií
- resource-pack Halloween obloha
- plnohodnotný Halloween soundtrack přes ItemsAdder resource pack
- tajné úkoly a easter eggy
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
