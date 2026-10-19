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

Sekce quest-rewards v config.yml zůstává prázdná. Až bude odměna konkrétního questu domluvená, lze nastavit fragments a/nebo commands. Příkazy se spouštějí jednou při prvním dokončení a rozpoznají zástupné hodnoty %player%, %uuid% a %quest%. Výchozí verze nepřiděluje žádné peníze, vybavení ani předměty.

## Klíč

HalloweenCore označí vlastní speciální klíč značkou PersistentDataContainer. ItemsAdder položka má ID warriorland_halloween:secret_key. Způsob získávání klíčů zatím není pevně přiřazený. Až bude dohodnutý, UplineDialogues může použít akci Give item pro ItemsAdder ID warriorland_halloween:secret_key; HalloweenCore rozpozná toto ID i bez vlastního PDC markeru. Alternativou je /halloween key give spuštěný jako OP pro právě hovořícího hráče. Žádný NPC dialog zatím klíč automaticky nepřiděluje.

## Ověření na stagingu

- Ověř otevření NPC dialogu a správné větvení voleb.
- Ověř, že přijetí, postup a dokončení ukládá stav konkrétního hráče.
- Ověř, že opakované dokončení znovu nevyplatí nakonfigurované fragmenty ani příkazy.
- Ověř vlastní texturu klíče z ItemsAdder a vanilla fallback bez ItemsAdder.
- Ověř, že nová truhla odmítne otevření bez klíče, spotřebuje jeden klíč, trvale se odemkne a zůstane prázdná do další dohody o obsahu.
