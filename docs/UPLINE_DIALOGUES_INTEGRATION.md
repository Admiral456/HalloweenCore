# Integrace UplineDialogues a HalloweenCore

Dialogy se vytvářejí jako mapa v editoru UplineDialogues, nikoli jako YAML konfigurace HalloweenCore. Plugin podporuje skutečná okna Minecraft dialogů od verze klienta 1.21.6; starší verze mají ostatní dialogové funkce, ale nativní okna se neotevřou. Server WarriorLandu na 1.21.10 je nad tímto minimem.

UplineDialogues 3.0.0 poskytuje editor NPC dialogů, volby, větvení a příkazy spouštěné jako hráč, OP nebo konzole. HalloweenCore proto neposkytuje vlastní dialogové GUI; eviduje questový stav, fragmenty a jednorázové dokončení.

## Napojení

1. V editoru UplineDialogues vytvoř dialog u vybraného vesničana.
2. Přidej odpovědi a příběhové větve v UplineDialogues.
3. Akci přijetí úkolu spusť jako OP: /halloween quest start <id>.
4. Akci posunu postupu spusť jako OP: /halloween quest progress <id> [množství].
5. Po splnění dialogových podmínek spusť jako OP: /halloween quest complete <id>.
6. Pokud je nainstalovaný PlaceholderAPI, v podmínkách lze použít %halloween_fragments%, %halloween_quest_progress_<id>%, %halloween_quest_started_<id>% a %halloween_quest_completed_<id>%.

ID v příkladech jsou zástupné. Skutečný příběh, názvy questů a jejich odměny se touto změnou předem nedefinují.

## Odměny

Výchozí odměny jsou aktivní a nastavují se v `config.yml`:
- `light`: 3–5 fragmentů; common klíč 10 %, rare 1 %, legendary 0,1 %.
- `medium`: 6–9 fragmentů; common 15 %, rare 3,5 %, legendary 0,5 %.
- `hard`: 10–15 fragmentů; common 20 %, rare 6 %, legendary 2 %.
- `very-hard`: 16–25 fragmentů; common 25 %, rare 10 %, legendary 8 %.

Nově vytvořené ID automaticky spadá do lehké obtížnosti, dokud mu ve `quest-rewards.quests.<id>.difficulty` nenastavíš správný tier. Lze také nastavit pevné `fragments`, vlastní rozsah `min-fragments` / `max-fragments` nebo přepsat jednotlivé hodnoty `key-chances`. Příkazy se spouštějí jednou při prvním dokončení a rozpoznají `%player%`, `%uuid%` a `%quest%`. Nedefinuje se tím příběh ani cíl žádného questu, pouze odměna.

## Klíče a loot

Tři rarity klíčů používají tutéž existující ItemsAdder položku a texturu `warriorland_halloween:secret_key`; rarity jsou rozlišeny interní PDC značkou. Přímá UplineDialogues akce Give item vydá obyčejný klíč. Vyšší rarity lze vydat z questové odměny nebo jako OP příkazem `/halloween key give rare [hráč] [počet]` či `/halloween key give legendary [hráč] [počet]`. Běžný klíč otevře pouze obyčejnou místnost, vzácný pouze vzácnou a legendární pouze legendární.

Při prvním odemčení místnosti hráč dostane 8–16 / 20–35 / 40–70 fragmentů podle rarity a do truhly se jednorázově vloží vanilla loot. Vzácnější tabulky mají šanci na očarované knihy/nástroje, diamanty, netheritový šrot a velmi nízkou šanci na totem nesmrtelnosti. Nepřidávají se žádné další custom předměty ani textury.

## Ověření na stagingu

- Ověř otevření NPC dialogu a správné větvení voleb.
- Ověř, že přijetí, postup a dokončení ukládá stav konkrétního hráče.
- Ověř, že opakované dokončení znovu nevyplatí nakonfigurované fragmenty ani příkazy.
- Ověř vlastní texturu klíče z ItemsAdder a vanilla fallback bez ItemsAdder.
- Ověř, že klíč nesprávné rarity truhlu neotevře, správný klíč se spotřebuje, odměna se připíše jen jednou a truhla se naplní vanilla lootem.
