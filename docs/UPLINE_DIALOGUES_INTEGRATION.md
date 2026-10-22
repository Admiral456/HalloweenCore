# Volitelná integrace UplineDialogues a HalloweenCore

Tento návod je pro server, který chce vytvářet NPC dialogy v editoru UplineDialogues. HalloweenCore samo neposkytuje vlastní dialogový editor: spravuje stav úkolů, postup, fragmenty a jednorázové vyplacení odměny.

V repozitáři je jako alternativa připravený ukázkový balíček **BetonQuest** v `betonquest/QuestPackages/warriorland_halloween_dialogues/`. Nepoužívej zároveň dvě dialogové implementace pro stejný úkol.

## Vytvoření úkolu v UplineDialogues

1. V editoru UplineDialogues vytvoř dialog a přiřaď jej k NPC.
2. Přidej volbu pro přijetí úkolu. Akce musí příkaz spustit jako aktivního hráče, který má potřebné oprávnění.
3. Při přijetí úkolu zavolej `/halloween quest start <id>`.
4. Při změně postupu volej `/halloween quest progress <id> [množství]`.
5. Po splnění všech podmínek volej `/halloween quest complete <id>`.
6. Pro zobrazení stavu lze použít `/halloween quest status <id>`; seznam úkolů ukáže `/halloween quest list` přes podřízené `/halloween quest list` nebo `/halloween quest status` bez ID.

ID úkolu smí obsahovat malá písmena, číslice, pomlčku a podtržítko. Příkazy pro zahájení, posun a dokončení jsou chráněné oprávněním `halloweencore.quest.dialogue` (standardně OP). Dokončení konkrétního ID vyplatí odměnu jen jednou.

## Odměny a PlaceholderAPI

Obtížnost a výchozí počet fragmentů nastavuje `quest-rewards` v `plugins/HalloweenCore/config.yml`:

- `light`: 3–5 fragmentů
- `medium`: 6–9 fragmentů
- `hard`: 10–15 fragmentů
- `very-hard`: 16–25 fragmentů

Nové ID používá `quest-rewards.default-difficulty` (výchozí `light`), pokud mu administrátor výslovně nenastaví vlastní rozsah nebo pevný počet fragmentů. Tento návod nepředepisuje příběh, konkrétní úkol ani zvláštní odměnu.

Pokud je aktivní PlaceholderAPI, jsou k dispozici:

- `%halloween_fragments%`
- `%halloween_quest_progress_<id>%`
- `%halloween_quest_started_<id>%`
- `%halloween_quest_completed_<id>%`

## Co není součástí současného rozsahu

Tajné místnosti, klíče, jejich textura, zvláštní odměny založené na `key-chances` a nefunkční generátor podzemních hrobů jsou záměrně odstraněné. Neodkazuj na ně z NPC dialogů. Odměny se řídí pouze aktuální konfigurací HalloweenCore.

## Ověření

- Ověř, že přijetí úkolu uloží stav konkrétního hráče.
- Ověř, že posun postupu odpovídá skutečným podmínkám úkolu.
- Ověř, že dokončení připíše nakonfigurované fragmenty pouze jednou.
- Ověř běh příkazů s hráčským kontextem a oprávněním `halloweencore.quest.dialogue`.
- Ověř Placeholders, pokud je na serveru PlaceholderAPI.
