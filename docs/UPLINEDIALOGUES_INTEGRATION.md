# HalloweenCore + UplineDialogues — aktuální integrace

HalloweenCore zajišťuje náhodnou nabídku úkolu, uložení hráčova postupu, kontrolu předmětů, fragmentové odměny a hod na klíč. UplineDialogues zajišťuje editor a samotné uzly rozhovoru. Plugin automaticky nevytváří villagery, nepřiřazuje jim dialogy a nenasazuje jim pumpkin hlavy.

## Co musí být nainstalováno

- HalloweenCore JAR
- UplineDialogues
- PlaceholderAPI (potřebná pro dynamické placeholdery a podmíněné větve)
- ItemsAdder (volitelný pro vlastní vzhled klíčů; bez něj má klíč vanilla fallback)

## Zapojení sdíleného náhodného stromu

1. Spusť `/updialog panel` a vytvoř strom například `Village Work`.
2. Přiřaď jej každému NPC, které má nabízet náhodné úkoly. Přiřazení se ukládá v UplineDialogues na serveru, ne v tomto repozitáři.
3. V uzlu `START` při volbě „Máš pro mě nějakou práci?“ spusť jako **hráč** `/halloween quest offer`, poté přejdi do `OFFER_ROUTER`.
4. V `OFFER_ROUTER` zobrazuj podmíněně jednu z možností:
   - `%halloween_villager_quest_status% == offered` → `OFFER`
   - `%halloween_villager_quest_status% == active` → `ACTIVE`
   - `%halloween_villager_quest_status% == none` → `NO_WORK`
5. V `OFFER` ukaž `%halloween_villager_quest_title%`, `%halloween_villager_quest_description%`, `%halloween_villager_quest_difficulty%`, `%halloween_villager_quest_reward%` a příběh z `%halloween_villager_quest_story%`.
6. Volba přijetí spustí jako hráč `/halloween quest accept-offer`; odmítnutí spustí `/halloween quest decline-offer`.
7. U aktivního úkolu nabídka odevzdání spustí jako hráč `/halloween quest turnin-offer`. Hráč musí být vykonavatelem příkazu, aby se ověřil jeho inventář.
8. V `TURNIN_ROUTER` podle `%halloween_villager_quest_turnin_status%` větvi úspěch na `completed`, chybějící požadavky na `missing` a chybu zadání na `error`.
9. Ulož strom. Zkontroluj, že každé NPC, které má zadávat úkoly, je skutečně přiřazené ke stejnému sdílenému stromu.

Kompletní české texty a názvy všech uzlů jsou v `docs/DIALOGUE_LIBRARY_CZ.md`. Je to obsahový scénář, nikoli importní soubor pro UplineDialogues.

## Testy na serveru

- Na testovacím účtu požádej o úkol a ověř, že se náhodná nabídka zachová, dokud ji nepřijmeš nebo neodmítneš.
- Nabídku odmítni a otevři dialog znovu. Jiný dostupný úkol by měl být přednostně nabídnut.
- Přijmi úkol, zavři dialog, vrať se a ověř, že zůstává aktivní.
- Zkus odevzdání bez všech předmětů. Dialog má ukázat větev `missing` a inventář se nesmí změnit.
- Dodej všechny požadavky a odevzdej úkol. Zkontroluj fragmenty, stav dokončení a případný bonusový klíč.
- Ověř i dvě jednorázové úlohy tajných místností: jejich průzkumný cíl se splní až po skutečném vstupu do zaznamenané místnosti.
- Na admin účtu spusť `/halloween quest validate`, `/halloween rooms generate 3` a `/halloween rooms verify`.

## Přímé úkoly podle ID

Pro speciální skriptovanou větev lze stále použít `/halloween quest start <id>` a `/halloween quest turnin <id>`. Nepoužívej tyto dva příkazy pro nový náhodný strom místo `offer`, `accept-offer` a `turnin-offer`, jinak se neaktualizuje dialogový stav náhodné nabídky.

## Šance na klíč a odměny

Základní šance podle obtížnosti:

| Obtížnost | Běžný | Vzácný | Legendární |
|---|---:|---:|---:|
| Lehký | 10 % | 1 % | 0,1 % |
| Střední | 15 % | 3,5 % | 0,5 % |
| Těžký | 20 % | 6 % | 2 % |
| Velmi těžký | 25 % | 10 % | 8 % |

Speciální legendární šance: 20 bloků vysušeného ghasta — 18 %; 40 železných ingotů — 10 %; 5 diamantů — 14 %; trojzubec — 20 %. Jednotlivé pravděpodobnosti jsou vzájemně se vylučující výsledek jednoho hodu; u výchozí velmi těžké úlohy bez vlastní úpravy je 57 % šance, že nepadne žádný klíč. Fragmenty se vyplácejí nezávisle: 4 / 7 / 12 / 20 podle obtížnosti.

## Limity

- Stromy v editoru nejsou verzovány v tomto repozitáři a nejsou automaticky importovány.
- Pumpkin hlavy ani automatická úprava vzhledu vesničanů nejsou v současném HalloweenCore implementované.
- Truhly tajných místností jsou zamčené a chráněné, ale zatím nemají dohodnutou loot tabulku.
- Úspěšné CI ověřuje kompilaci, statické kontroly a balení, nikoli živé chování serveru či správnost uloženého stromu v UplineDialogues.
