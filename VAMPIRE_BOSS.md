# Král upírů — technický kontrakt

Tento dokument zamyká pouze rozměrové minimum. Vizuální styl, model, mechaniky, fáze souboje, dropy a aréna zůstávají otevřené pro další návrh.

## Pevné podmínky

- minimální výška celé postavy: **10 bloků**
- minimální šířka celé siluety včetně křídel: **8 bloků**
- křídla jsou povinnou součástí siluety
- boss je finální serverový boss Halloween 2026
- boss se nesmí objevit před odemčením serverového finále
- boss zůstává limitovaný na Halloween event

## Resource-pack / model plán

Budoucí model bude připraven jako vlastní custom entity pro ItemsAdder/MythicMobs. Model musí zachovat uvedené minimální rozměry i v nejmenší animační póze.

Před samotným modelem budou samostatně řešeny:
- hlavní tělo
- hlava/koruna
- levé křídlo
- pravé křídlo
- případné zbraně a efekty

Při výrobě modelu budou současně dodané jeho textury a potřebné resource-pack soubory.


## Encounter lifecycle

1. Server dosáhne globálního cíle; okamžik odemčení se trvale uloží.
2. Admin jednou nastaví střed arény přes `/halloween setvampirearena` (stoupne na podlahu uprostřed; souřadnice X/Z se zarovnají do středu bloku).
3. Po pěti minutách systém čeká na připravený model a na vhodného hráče v okolí arény.
4. Hráči dostanou třicetisekundové varování se souřadnicemi; pokud zůstanou poblíž, boss se objeví přesně v uloženém středu.
5. MythicMobs musí obsahovat `vampire-king` a skutečný ModelEngine model musí mít `bosses.vampire.model.ready: true`.
6. Admin příkazy `/halloween boss start|stop|status` zůstávají pouze pro testování a zásah obsluhy.
7. HalloweenCore převezme HP bar, účastníky, ochranu arény a odměny.
8. Po smrti proběhne jednorázová výplata; při timeoutu nebo ručním stopu se boss odstraní bez victory odměn.

## Boss bar contract

- červený bar, defaultně `SEGMENTED_20`
- zobrazení do 96 bloků
- automatický update HP
- automatické odebrání po smrti, stopu, reloadu nebo vypnutí eventu
- na stejné obrazovce nepoužívat druhý MythicMobs boss bar; dormantní MythicMobs definice ho má vypnutý

## Fáze souboje

- Fáze I: základní lov.
- Fáze II (70 % HP): Speed/Resistance, krvavý plošný výboj a slabý vampirický sustain.
- Fáze III (40 % HP): silnější efekty, shadow strike/teleport a vyšší sustain.
- Fáze IV (15 % HP): enrage, silnější odpor, plošný Nightfall a Blindness.

Prahové hodnoty a síla schopností jsou záměrně v `config.yml`, aby šly ladit bez změny základního modelu.

## MythicMobs / ModelEngine

MythicMobs dodává základní mob definition `mythicmobs/mobs/vampire-king.yml`. ModelEngine hook není aktivovaný, dokud není hotový vlastní model; připravená specifikace je v `docs/VAMPIRE_MODEL_ENGINE.md`.

Oficiální MythicMobs API podporuje získání MythicMob přes MobManager a spawn pomocí Bukkit adaptéru, což je důvod, proč HalloweenCore používá API bridge místo spouštění shellového příkazu. citeturn967112search0turn610413search0


## Encounter rewards

- Participation rewards are based on eligible time spent near the arena, not merely joining the encounter.
- The top-contributor bonus is based on accumulated damage dealt to the boss. Players must also meet the configured minimum participation time.
- Melee hits and player-fired projectiles contribute. Spectators who only remain nearby cannot receive the top-damage bonus.
- Victory handling is guarded against duplicate execution, and the defeated flag is persisted before the encounter is cleaned up.


## Obtížnost a odměny

- Encounter trvá nejvýše 20 minut.
- Účastnická odměna vyžaduje alespoň 5 minut v okolí arény během souboje.
- Bonus za největší příspěvek se určuje podle reálně uděleného poškození, ne podle samotné přítomnosti.
- Výchozí fragmentové odměny: 1 000 za způsobilou účast, 5 000 pro způsobilého účastníka, který bosse dorazí, a 2 500 za nejvyšší poškození.
- Finální boss je jednorázový pro Halloween 2026.
