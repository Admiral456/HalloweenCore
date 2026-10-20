# HalloweenCore — NPC dialogy a tajné podzemní místnosti

HalloweenCore je zúžený update pro Paper/Purpur 1.21.10 a Java 21. Aktivní obsah této verze tvoří questy napojené na UplineDialogues, speciální klíč a skryté podzemní místnosti.

## Co funguje v kódu

- UplineDialogues je samostatný plugin pro tvorbu a editaci rozhovorů. HalloweenCore poskytuje bezpečné questové příkazy, persistentní postup a placeholders.
- Výchozí konfigurace obsahuje 25 questů se třemi úrovněmi obtížnosti. Požadované předměty jsou definované v konfiguraci; příkaz /halloween quest turnin je ověří všechny předem a odebere je až po úspěšné kontrole.
- Quest lze přijmout jen jednou, průběh je uložen podle hráče a dokončený quest nelze opakovat. Žádné fragmentové ani předmětové odměny nejsou bez dohody přednastavené.
- Nové chunky Overworldu mají 2,5% šanci na pokus o generování malé skryté místnosti. Generátor zkouší více bezpečných umístění, vyžaduje přírodní kamenný obal i nalezenou přírodní jeskyni do 14 bloků a otevře k ní úzký přístupový tunel. Tunel vyřezává pouze přírodní kámen, nikoli rudy, tekutiny, kontejnery nebo hráčské stavby.
- Každá místnost má speciálním PDC označenou zamčenou truhlu. Klíč se při prvním odemčení spotřebuje; potom truhla zůstane odemčená. Truhla je chráněná před rozbitím a výbuchy.
- Vstup do zaznamenané místnosti automaticky posune quest hidden_room_report a průzkumnou část sealed_archive.

## Dialogy

UplineDialogues nainstaluj zvlášť a otevři jeho editor příkazem /updialog panel. Připravil jsem českou knihovnu 25 rozhovorů a návod na navázání questů: docs/DIALOGUE_LIBRARY_CZ.md. Knihovna obsahuje texty, větve a příkazy pro všech 25 zadavatelů. V UplineDialogues nastav akce jako hráč: `halloween quest start <id>` a `halloween quest turnin <id>`.

Důležitě: uložené dialogové stromy jsou spravované vlastním editorem UplineDialogues na serveru. Samotný Markdown katalog se neimportuje automaticky do dat UplineDialogues; uzly je třeba vložit/vytvořit v jeho editoru. HalloweenCore ale už poskytuje skutečné příkazy pro přijetí a odevzdání questů.

## Příkazy

- /halloween quest status [id] — stav, postup a obtížnost.
- /halloween quest start <id> — přijmout jeden z 25 questů.
- /halloween quest turnin <id> — ověřit a odevzdat požadované předměty.
- /halloween quest claim <id> — jednorázově vyzvednout později nakonfigurovanou odměnu.
- /halloween rooms status — počítadla generování, šance, hloubka a poslední místnost.
- /halloween rooms generate [radius] — ruční bezpečný test (správce, Overworld).
- /locate halloweenroom [max-vzdálenost] — najde nejbližší zaznamenanou místnost v aktuálním světě.
- /locateall halloweenroom — vypíše všechny zaznamenané místnosti aktuálního světa (správce).
- /halloween locate room [max-vzdálenost] — alternativa pro vyhledání.

PlaceholderAPI je volitelný. Rozhovory mohou používat %halloween_quest_<id>_started%, %halloween_quest_<id>_progress% a %halloween_quest_<id>_completed%.

## Tajné místnosti a kontrola generování

Generování automaticky zkouší jen nově vytvořené chunky Overworldu; staré, už existující chunky se automaticky znovu neprohledávají. Výchozí rozsah hloubky je Y=-36 až Y=-8, maximálně 48 pokusů na nový chunk. Terén může místo odmítnout, proto je součástí /halloween rooms status i /halloween rooms generate pro testování na existujícím světě.

Souřadnice každé úspěšně vygenerované místnosti se zapisují do plugins/HalloweenCore/secret-rooms.yml. Při aktualizaci se převedou i záznamy starších verzí, pokud je tento soubor obsahuje. Příkaz /locate vyhledává nejbližší z těchto skutečných záznamů, ne náhodné souřadnice.

Truhly jsou záměrně prázdné, dokud nebude dohodnut jejich obsah a způsob získávání klíčů. Ruční vydání klíče přes /halloween key give je administrátorská testovací pomůcka.

## Zachovaný obsah a odstraněné systémy

ItemsAdder obsah tvoří originální 32×32 pixelartová textura klíče a přesně 13 původních 16×16 textur sladkostí ze Stev's Halloween, které zůstávají beze změny s MIT licencí a hashi. Balíček neobsahuje staré Halloween moby, bosse, ModelEngine/MythicMobs, staré eventy, hřbitov, vesnici, shader, hudbu, obchod, staré zbroje ani starou devítikapitolovou kampaň.

Již postavené staré stavby v hráčských světech se automaticky nemažou; bez jasných souřadnic by tím mohly být zničeny stavby hráčů.

## Build a instalace

1. Sestav JAR přes Java 21: mvn -B clean package.
2. Zkopíruj target/HalloweenCore.jar do plugins a restartuj server.
3. Zkopíruj namespace warriorland_halloween z ItemsAdder ZIPu do plugins/ItemsAdder/contents/.
4. Spusť /iazip a zkontroluj /iainfo; obyčejné ikonky klíče a sladkostí nevyžadují OptiFine.
5. Nainstaluj UplineDialogues zvlášť, otevři /updialog panel a vytvoř dialogové stromy podle docs/DIALOGUE_LIBRARY_CZ.md.

GitHub Actions kontroluje rozsah zdrojů a assetů, kompiluje JAR a balí ItemsAdder obsah. To ověřuje sestavení, nikoli živé chování na produkčním serveru: před nasazením ještě otestuj nové chunk-generování, ruční test, klíč, truhlu a dialogové akce na staging serveru.
