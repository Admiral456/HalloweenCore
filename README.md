# HalloweenCore — dialogy NPC a tajné podzemní místnosti

HalloweenCore je plugin pro Paper/Purpur 1.21.10 a Java 21. Aktuální rozsah tvoří questy pro dialogy přes UplineDialogues, tři vzácnosti klíče a malé uzamčené podzemní místnosti.

## Aktuální obsah

- **29 úkolů:** 25 základních úkolů a čtyři další náročné zakázky. Úkoly se vybírají náhodně z `quests.definitions`; přidaný platný úkol se zapojí do nabídky automaticky, pokud nemá `villager-offer-weight: 0`.
- **Odměny:** výchozí odměna je 4 / 7 / 12 / 20 fragmentů podle obtížnosti. Klíče mají odstupňované šance podle obtížnosti a čtyři nejtěžší zakázky mají vlastní šance na legendární klíč.
- **Ukládání postupu:** stav, nabídka, odmítnuté nabídky, fragmenty a dokončení se ukládají do `plugins/HalloweenCore/quest-data.yml`. Úkoly jsou opakovatelné ve výchozím nastavení; `hidden_room_report` a `sealed_archive` jsou jednorázové.
- **Odevzdávání:** všechny požadavky se kontrolují před odebráním předmětů. Chybějící předmět nebo neplatné zadání nemá spotřebovat požadované předměty.
- **Tajné místnosti:** na nově vytvořený povolený chunk Overworldu připadá 2,5% šance na pokus. Místnost se umisťuje pod zem do Y=-36 až Y=-8 a generátor ji vytvoří jen v bezpečném přírodním kameni s nalezeným spojením do přírodní jeskyně. Staré chunky se automaticky znovu neprohledávají.
- **Tři klíče:** běžný, vzácný a legendární. Zatím sdílejí jednu texturu, ale mají odlišný název, barvu, PDC vzácnost a otevírají jen truhlu odpovídající vzácnosti.
- **Ochrana truhel:** zamčené truhly vyžadují správný klíč a po odemčení zůstávají chráněné před rozbitím a řadou automatických či destruktivních událostí.

## Důležité hranice

- UplineDialogues je samostatný plugin. Markdown strom v `docs/DIALOGUE_LIBRARY_CZ.md` se **neimportuje automaticky**. Musí se vytvořit a uložit v `/updialog panel`, poté přiřadit k vybraným NPC.
- HalloweenCore **nevytváří vesničany a automaticky jim nenasazuje pumpkin hlavy**. V této větvi není implementována automatická úprava vzhledu villagerů.
- Tajné místnosti mají zamčenou truhlu, ale **loot se generuje při prvním odemčení** podle rarity a obsahuje pouze vanilla suroviny, jídlo, fragmenty, očarované knihy/nástroje a velmi vzácné bonusy. Nejsou potřeba žádné nové textury.
- GitHub Actions ověřuje statické kontrakty, kompilaci a balíčkování. Nenahrazuje test na skutečném Paper/Purpur serveru; do UplineDialogues ani do živého serveru nemám přístup.

## Dialogy a příkazy

Pro náhodný dialogový strom použij příkazy jako hráč:

- `/halloween quest offer` — vybere a uloží náhodnou nabídku.
- `/halloween quest accept-offer` — přijme zobrazený úkol.
- `/halloween quest decline-offer` — odmítne nabídku bez postihu.
- `/halloween quest turnin-offer` — ověří a odevzdá právě aktivní úkol od vesničana.

Další příkazy:

- `/halloween quest status [id]` — stav, průběh a obtížnost.
- `/halloween quest start <id>` a `/halloween quest turnin <id>` — ruční práce s konkrétním ID úkolu.
- `/halloween quest validate` — správce; kontrola neplatných definic.
- `/halloween quest claim <id>` — případná nevyplacená konfigurovaná odměna.
- `/halloween fragments` — zůstatek fragmentů.
- `/halloween key give <počet>` nebo `/halloween key give <rare|legendary> <počet> [hráč]` — administrátorská pomůcka.
- `/halloween rooms status`, `/halloween rooms generate [radius]`, `/halloween rooms verify`, `/halloween rooms list` — stav, ruční test a kontrola místností.
- `/locate halloweenroom [max-vzdálenost]`, `/locateall halloweenroom` — vyhledání zaznamenaných místností.

PlaceholderAPI je volitelné pro backend úkolů; dynamické dialogové texty a podmínky v UplineDialogues jej potřebují. Dostupné dynamické placeholdery jsou uvedené v `docs/DIALOGUE_LIBRARY_CZ.md`.

## Tajné místnosti a odměny

Místnost má půdorys přibližně 7×5×7 bloků, dekorace pouze z vanilla bloků a jednu zamčenou truhlu. Klíč se spotřebuje při prvním odemčení. Loot truhly je záměrně ponechán prázdný do potvrzení obsahu a neměl by být považován za hotovou odměnovou tabulku.

## ItemsAdder

Balíček obsahuje jednu 32×32 texturu klíče (sdílenou všemi třemi vzácnostmi) a přesně 13 původních 16×16 textur sladkostí z Stev's Halloween, s MIT licencí a hash manifestem. Ikony běžných ItemsAdder předmětů nevyžadují OptiFine.

1. Sestav JAR přes Java 21: `mvn -B clean package`.
2. Zkopíruj `target/HalloweenCore.jar` do `plugins/` a restartuj server.
3. Zkopíruj obsah `itemsadder/contents/warriorland_halloween` do `plugins/ItemsAdder/contents/`.
4. Spusť `/iazip` a ověř balíček příkazem `/iainfo`.
5. Nainstaluj UplineDialogues a PlaceholderAPI zvlášť, vytvoř strom z českého scénáře a přiřaď ho konkrétním NPC.
