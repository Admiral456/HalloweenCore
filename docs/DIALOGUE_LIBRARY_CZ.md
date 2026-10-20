# Náhodné příběhové úkoly od vesničanů — UplineDialogues

## Jak má systém fungovat

Všichni vybraní vesničané mohou používat jeden společný dialogový strom. Když se hráč zeptá na práci, HalloweenCore mu náhodně vybere úkol z `quests.definitions`. Výběr zůstane stejný, dokud hráč úkol nepřijme nebo neodmítne. Opakovatelné úkoly se mohou po splnění nabídnout znovu; jednorázové příběhové milníky se po dokončení vyřadí.

Hráč může:
- zeptat se na podrobnosti a příběhové pozadí;
- úkol přijmout;
- úkol odmítnout bez postihu;
- rozhovor zavřít a vrátit se později;
- po přijetí se vrátit, znovu si přečíst zadání a zkusit úkol odevzdat;
- po úspěšném splnění získat fragmenty a navíc samostatnou náhodnou šanci na klíč od tajné místnosti.

Quest nabídky se vybírají z konfigurace, ne z pevně zadrátovaného seznamu v dialogu. Každá nová platná definice pod `quests.definitions` je automaticky součástí náhodného poolu, pokud nemá `villager-offer-weight: 0`. Úkoly jsou opakovatelné ve výchozím nastavení, takže seznam nikdy nedojde. Do definice jednorázového milníku přidej `repeatable: false`. Dokud má hráč aktivní úkol od vesničana, jiný mu tato nabídka nepřidělí. Odmítnuté zadání se přeskočí, dokud hráč nevyčerpá jiné dostupné nabídky; potom se cyklus odmítnutých obnoví.

## Vytvoření dialogového stromu

V UplineDialogues otevři `/updialog panel`, vytvoř dialog například s názvem `Village Work` a přiřaď ho k vesničanům, kteří mají nabízet náhodnou práci. Následující uzly a akce vytvoř v editoru. Příkazy v uzlech musí být spouštěné jako **hráč**.

PlaceholderAPI musí být nainstalované a jeho placeholdery musí UplineDialogues vyhodnocovat i v textech a podmínkách uzlů.

### Uzel START — běžný rozhovor

**NPC:**
„Zdravím tě, poutníku. V poslední době se kolem vesnice objevují podivné zprávy a každý tu má plné ruce práce. Co tě přivádí?“

Možnosti:
- **„Máš pro mě nějakou práci?“** → akce příkazem `/halloween quest offer`; potom přesměruj podle `%halloween_villager_quest_status%`:
  - `offered` → uzel **OFFER**;
  - `active` → uzel **ACTIVE**;
  - `none` → uzel **NO_WORK**;
  - `completed` se po novém zavolání `offer` normálně změní na novou nabídku nebo `none`, pokud už není co nabídnout.
- **„Co se tu v poslední době děje?“** → uzel **VILLAGE_STORY**.
- **„Teď ne, díky.“** → uzel **GOODBYE**.

### Uzel VILLAGE_STORY — příběhové povídání

**NPC:**
„Lidé mluví o starých mapách, dutých ranách pod zemí a cestách, které na některých záznamech vůbec nejsou. Zatím jsou to jen zprávy. Nechci tě strašit, ale pokud se něco dozvíš, promluv si s místními.“

Možnosti:
- **„Zeptat se na práci“** → stejné jako první možnost START: spusť `/halloween quest offer` a přejdi do větve podle stavu nabídky.
- **„Děkuji za zprávy.“** → GOODBYE.

### Uzel OFFER — nová náhodná nabídka

Použij v textu dialogu placeholdery, aby se do stejného stromu propsal právě vybraný úkol.

**NPC:**
„Zrovna potřebuji s něčím pomoct. %halloween_villager_quest_story% Mohl by ses toho ujmout?“

Pod tím zobraz:
- **Úkol:** `%halloween_villager_quest_title%`
- **Zadání:** `%halloween_villager_quest_description%`
- **Obtížnost:** `%halloween_villager_quest_difficulty%`
- **Odměna:** `%halloween_villager_quest_reward%` fragmentů a samostatná šance na klíč tajné místnosti.

Možnosti:
- **„Zeptat se na podrobnosti“** → uzel **OFFER_DETAILS**.
- **„Dobře, přijímám.“** → spusť `/halloween quest accept-offer`; pokud se stav změní na `active`, přejdi do **ACCEPTED**.
- **„Ne, odmítám.“** → spusť `/halloween quest decline-offer` a přejdi do **DECLINED**.
- **„Rozmyslím si to později.“** → GOODBYE bez příkazu. Nabídka zůstane uložená a při návratu se nezmění.

### Uzel OFFER_DETAILS — podrobnosti

**NPC:**
„Jde o úkol **%halloween_villager_quest_title%**. %halloween_villager_quest_story% Zadání zní: %halloween_villager_quest_description% Za úspěšné splnění dostaneš **%halloween_villager_quest_reward% fragmentů**. Navíc může padnout klíč k jedné z uzamčených tajných truhel.“

Možnosti:
- **„Přijímám úkol.“** → `/halloween quest accept-offer` → ACCEPTED, pokud je stav `active`.
- **„Ne, děkuji.“** → `/halloween quest decline-offer` → DECLINED.
- **„Zpět k nabídce.“** → OFFER.

### Uzel ACCEPTED — přijetí úkolu

**NPC:**
„Děkuju, že ses do toho pustil. Zapamatoval jsem si naši domluvu. Až budeš mít vše připravené, vrať se za mnou a můžeme to uzavřít.“

Možnosti:
- **„Připomenout úkol“** → ACTIVE_DETAILS.
- **„Vrátím se později.“** → GOODBYE.

### Uzel ACTIVE — pokračování aktivního úkolu

**NPC:**
„Jak pokračuje úkol **%halloween_villager_quest_title%**? Nechci tě popohánět, ale na tu pomoc stále čekám.“

Možnosti:
- **„Připomenout zadání“** → ACTIVE_DETAILS.
- **„Mám vše, co potřebuješ.“** → spusť `/halloween quest turnin-offer`; poté zkontroluj `%halloween_villager_quest_status%`:
  - `completed` → QUEST_COMPLETE;
  - `active` → NOT_READY.
- **„Ještě potřebuji čas.“** → GOODBYE, úkol zůstává aktivní.

### Uzel ACTIVE_DETAILS — připomenutí zadání

**NPC:**
„Tvůj úkol je **%halloween_villager_quest_title%**. %halloween_villager_quest_description% Při odevzdání ověřím všechny požadavky najednou. Pokud něco chybí, nic z požadovaných předmětů se ti neodebere.“

Možnosti:
- **„Zkusit odevzdat“** → `/halloween quest turnin-offer` → podle stavu `completed` přejdi na QUEST_COMPLETE, jinak na NOT_READY.
- **„Zpět“** → ACTIVE.

### Uzel NOT_READY — něco chybí

**NPC:**
„Zatím to není kompletní. Potřebuji všechno, co bylo v zadání, ne jen část. Nic jsem si od tebe nevzal; zkus to znovu, až budeš připravený.“

Možnosti:
- **„Připomenout zadání“** → ACTIVE_DETAILS.
- **„Vrátím se později.“** → GOODBYE.

### Uzel QUEST_COMPLETE — úspěšné dokončení

**NPC:**
„Výborně, tohle je hotové. Díky, že ses na to nevykašlal. Odměna za úkol už byla vyplacena; snad se ti bude hodit. A jestli jsi měl štěstí, mohl jsi najít také klíč k tajné místnosti.“

Možnosti:
- **„Co dalšího se tu děje?“** → VILLAGE_STORY.
- **„Máš ještě nějakou práci?“** → START; hráč si může vyžádat další dostupný náhodný úkol.
- **„Na shledanou.“** → GOODBYE.

### Uzel DECLINED — odmítnutí nabídky

**NPC:**
„To je v pořádku. Nebudu tě nutit. Pokud si to později rozmyslíš, můžeme se pobavit znovu.“

Možnosti:
- **„Zeptat se na jinou práci“** → START.
- **„Na shledanou.“** → GOODBYE.

### Uzel NO_WORK — momentálně žádná nová nabídka

**NPC:**
„Teď pro tebe nemám žádný nový úkol. Možná už jsi všechny dostupné práce vyřešil, nebo je potřeba přidat další zadání. Zkus to později.“

Možnost:
- **„Dobře, na shledanou.“** → GOODBYE.

### Uzel GOODBYE — ukončení rozhovoru

**NPC:**
„Měj se a dávej na sebe pozor. V poslední době není dobré podceňovat cestu domů.“

---

## Placeholdery

HalloweenCore poskytuje přes PlaceholderAPI:

- `%halloween_villager_quest_status%` — `none`, `offered`, `active` nebo `completed`.
- `%halloween_villager_quest_has_quest%` — `true` nebo `false`.
- `%halloween_villager_quest_id%` — ID aktuální náhodné nabídky/úkolu.
- `%halloween_villager_quest_title%` — název právě vybraného úkolu.
- `%halloween_villager_quest_description%` — stručné zadání z konfigurace.
- `%halloween_villager_quest_story%` — příběhový text z konfigurace.
- `%halloween_villager_quest_difficulty%` — obtížnost úkolu.
- `%halloween_villager_quest_reward%` — počet fragmentů za splnění.

Příkazy pro akce dialogu:
- `/halloween quest offer` — vybere náhodný úkol do rozhovoru, úspěšně připravená nabídka nevypisuje další chatovou hlášku.
- `/halloween quest accept-offer` — přijme právě zobrazenou nabídku.
- `/halloween quest decline-offer` — odmítne a zapomene dosud nepřijatou nabídku.
- `/halloween quest turnin-offer` — odevzdá právě aktivní náhodný úkol od vesničana a po úspěchu přepne stav na `completed`.

## Přidávání vlastních úkolů

V `src/main/resources/config.yml` přidej novou položku do `quests.definitions`. Vyplň unikátní ID, `title`, `difficulty`, `description`, `objective-type`, `target` a `required-items`. Volitelně přidej:
- `dialogue-story: "Krátké příběhové pozadí, které NPC řekne při nabídce"`;
- `villager-offer-weight: 1.0` — vyšší číslo znamená častější nabídku, `0` nabídku vypne. Pokud pole chybí, váha je 1;
- `repeatable: false` — úkol se po dokončení už znovu nenabídne. Pokud pole chybí, úkol je opakovatelný;
- `reward-fragments: 10` — vlastní počet fragmentů. Pokud pole chybí, použije se výchozí odměna podle obtížnosti;
- `reward-items:` — volitelný seznam dalších předmětů s `id` a `amount`.

Výchozí fragmentová odměna je 3 za lehký, 5 za střední, 8 za těžký a 12 za velmi těžký úkol. Po úspěšném odevzdání se navíc provede nezávislý hod na klíč. Výchozí šance jsou 40 % běžný, 10 % vzácný a 5 % legendární klíč; v ostatních 45 % případech žádný klíč nepadne. Všechny hodnoty lze změnit v `config.yml`.

## Limity

Tento dokument je úplný scénář a konfigurace poskytuje dynamická data. Samotné uzly se zatím neimportují automaticky do interního úložiště UplineDialogues; strom musí být uložen v jeho editoru a přiřazen k NPC. Pro dynamický text a větvení je nutné ověřit, že konkrétní nainstalovaná verze UplineDialogues vyhodnocuje PlaceholderAPI v uzlech a podmínkách.
