# HalloweenCore — herní průvodce

Tento návod je dostupný ve hře jako napsaná kniha příkazem `/halloween guide`.

## Základní postup
Hráči získávají Halloween fragmenty za PvE, těžbu rud, farmaření a rybaření. Prokletí od 0 do 5 zvyšuje výdělek a odemyká dražší předměty. Celý server společně přispívá do globálního cíle.

## Eventy
- **Duševní bouře:** každá vlna vyvolá u každého způsobilého hráče v povoleném světě prokleté zombie, hrobníky a krvavé pavouky. Spawn je omezen na 25 bloků od hráče.
- **Čarodějnická hodina:** stejně jako Duševní bouře, ale s hexovými čarodějkami, krvavými pavouky a prokletými zombie a efekty temnoty.
- **Prokletá sklizeň:** hledá bloky `FARMLAND`, zoranou půdu vytvořenou motykou. Na nalezených povrchových políčkách se postupně pokouší vyvolat nepřítele.
- **Krvavý měsíc:** přepne svět na noc, každých 25 sekund přivádí vlny nepřátel u hráčů a násobí nepřátelské poškození na hráče třemi. Červené částice simulují eventový krvavý měsíc; skutečnou vanilla texturu měsíce Bukkit API dynamicky nepřebarví.
- **Dýňová apokalypsa:** u každého způsobilého hráče se objevují dýňoví přízraci a další nepřátelé. Plugin přidává dýně a jack-o-lanterny na bezpečná volná místa v načtených přírodních chunkech.
- **Hřbitov vstává:** spawnuje jen u hřbitovů zaznamenaných při generování Halloween struktur. Moby cílí na nejbližšího způsobilého hráče v dosahu; na volném poli se tato událost nespouští.

## Pole a bezpečnost
Plugin chápe pole jako blok `FARMLAND`, který vznikne použitím motyky na vhodnou půdu. Kvůli výkonu se prohlížejí pouze již načtené chunky — server nenačítá celou nekonečnou mapu. Dýně se umisťují nad přirozený terén, ne do hráčských staveb.

## Progrese a odměny
- `/halloween` — statistiky hráče.
- `/halloween progress` — společný cíl serveru.
- `/halloween curse` — prokletí.
- `/halloween rewards` — obchod.
- `/halloween challenge` — denní výzva.
- `/halloween quests` — příběhové úkoly.
- `/halloween secrets` — nápovědy k tajným místům.
- `/halloween guide` — získat knihu znovu.

## Relikvie a vlastní vzhled
Maska nočního lovce zvyšuje příjem fragmentů; Prokletý talisman přidá při držení jedno srdce; Batwing Elytra má výpad při skrčení během plachtění; Nocturne Sovereign Cowl odhaluje a zpomaluje okolní monstra. Vlastní vzhled vyžaduje přijatý ItemsAdder resource pack.

## Administrace
Ruční spawn struktur, mobů a testování eventů jsou vyhrazené správcům. Ruční stavby používají FORCE v ohraničeném prostoru a před testováním na produkčním světě je nutná záloha.
