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

1. Server dosáhne globálního cíle a trvale odemkne finále.
2. Administrátor jednou nastaví střed arény příkazem `/halloween setvampirearena`; přesný středový blok na podlaze je současně spawn point.
3. MythicMobs musí obsahovat `vampire-king` a ModelEngine musí mít otestovaný model.
4. Jakmile je finále, aréna i model připravený a na serveru je hráč, HalloweenCore spustí přirozené vyvolání: znamení, varování a pětiminutové odpočítávání.
5. Po odpočítávání se Král upírů automaticky spawnne ve středu arény; není třeba příkaz `/halloween boss start`.
6. Příkaz `/halloween boss start` zůstává pouze pro admin testy. `/halloween boss status` zobrazuje stav.
7. HalloweenCore označí entitu PDC klíčem a převezme správu HP baru, účastníků, omezení arény a odměn.
8. Po smrti proběhne jednorázová výplata; při timeoutu se boss odstraní bez victory odměny a automatické vyvolání se odloží.
9. Účastnická odměna vyžaduje nejméně 120 sekund v dosahu a způsobení alespoň 2,5 % maximálního zdraví bosse.

## Boss bar contract

- červený bar, defaultně `SEGMENTED_20`
- zobrazení do 96 bloků
- automatický update HP
- automatické odebrání po smrti, stopu, reloadu nebo vypnutí eventu
- na stejné obrazovce nepoužívat druhý MythicMobs boss bar; dormantní MythicMobs definice ho má vypnutý

## Fáze souboje a originální útoky

- **Fáze I — Falešná kořist:** runa označí místo, kde hráč právě stál; po krátkém varování místo vybuchne.
- **Fáze II (70 % HP) — Krvavý puls:** velká runa označí oblast a o chvíli později vyšle odhazující výboj.
- **Fáze III (40 % HP) — Zrcadlový výpad:** objeví se tři téměř totožné značky, ale skutečný zásah se náhodně objeví jen na jedné.
- **Fáze IV (15 % HP) — Zatmění:** několik kruhů se snaží hráče zmást; vnější kruh značí skutečný dosah výbuchu.
- Útoky nejsou instantní: hráči mají čas reagovat a opustit nebezpečnou oblast. Pokud encounter skončí, opožděné zásahy se zruší.

Prahové hodnoty a síla schopností jsou záměrně v `config.yml`, aby šly ladit bez změny základního modelu.

## MythicMobs / ModelEngine

MythicMobs dodává základní mob definition `mythicmobs/mobs/vampire-king.yml`. ModelEngine hook není aktivovaný, dokud není hotový vlastní model; připravená specifikace je v `docs/VAMPIRE_MODEL_ENGINE.md`.

Oficiální MythicMobs API podporuje získání MythicMob přes MobManager a spawn pomocí Bukkit adaptéru, což je důvod, proč HalloweenCore používá API bridge místo spouštění shellového příkazu. citeturn967112search0turn610413search0


## Encounter rewards

- Účastnická odměna vyžaduje nejméně 120 sekund v dosahu a poškození alespoň 2,5 % maximálního zdraví bosse.
- Kvalifikační podmínku musí splnit i vrah bosse, jinak nedostane bonus za závěrečný úder.
- Top-contributor bonus vychází ze skutečného poškození, včetně zásahů projektily; pouhé stání poblíž nestačí.
- Victory handling is guarded against duplicate execution, and the defeated flag is persisted before the encounter is cleaned up.
