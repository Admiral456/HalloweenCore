# HalloweenCore — nová verze

Tato pracovní verze je omezená na dohodnutý rozsah: dialogové questy s UplineDialogues, evidenci postupu a jednorázového dokončení úkolů, fragmenty, tajné podzemní místnosti se zamčenou truhlou a speciální klíč. Zachovává také třináct sladkostí a jejich původní textury převzaté ze Stev's Halloween pod licencí MIT.

## Odstraněno

- Všechny původní Halloween eventy a jejich automatické plánování.
- Halloween mobové, jejich modely a textury, Headless Nick a staré encountery.
- Staré bossy včetně Krále upírů a arénové mechaniky.
- Generování hřbitova a Halloween vesnice.
- Staré pasivní odměny za zabíjení, těžbu, farmaření a rybaření.
- Starý obchod, prokletí, relikvie, stará výbava, denní výzvy, sezónní přepínače, hudba, shader a dekorace světa.
- Konfigurace, příkazy a skripty sloužící výhradně odstraněným systémům.

Žádný ze starých mobů, bossů ani původních eventů se v této verzi znovu nezapíná.

## Dialogy s vesničany

UplineDialogues zajišťuje editor NPC rozhovorů, volby odpovědí, větvení a akce příkazů. HalloweenCore používá jeho akce jako rozhraní do questové evidence; nevytváří vlastní dialogové GUI.

Příklady příkazů pro dialog (ID jsou jen ukázky, nejsou to předem definované questy):
- Spustit jako OP: /halloween quest start archivist_intro
- Posunout postup jako OP: /halloween quest progress archivist_intro 1
- Dokončit jako OP: /halloween quest complete archivist_intro
- Zobrazit stav: /halloween quest status archivist_intro

Při změně stavu úkolu v UplineDialogues použij spuštění příkazu jako OP. Příkaz se stále provede pro hráče, který vede dialog, ale běžný hráč nemůže ručně dokončit quest v chatu. Volitelné PlaceholderAPI zástupné hodnoty pro větvení:
- %halloween_fragments%
- %halloween_quest_progress_archivist_intro%
- %halloween_quest_started_archivist_intro%
- %halloween_quest_completed_archivist_intro%

Odměny nejsou definitivně domluvené, proto nejsou přednastavené žádné questy ani odměny. HalloweenCore umí při prvním dokončení konkrétního úkolu jednorázově přidat nakonfigurované fragmenty a vykonat příkazy, ale sekce quest-rewards je ve výchozím nastavení prázdná. Nejsou přednastaveny peníze, vybavení ani jiné odměny.

Získávání klíčů není automaticky přiřazeno k žádnému úkolu. Když bude jeho zdroj domluvený, může odpovídající NPC dialog klíč vydat přes ItemsAdder položku warriorland_halloween:secret_key (UplineDialogues akce Give item) nebo příkaz /halloween key give spuštěný jako OP. Obojí HalloweenCore rozpozná. Správce může klíč vydat také z konzole.

## Tajné podzemní místnosti

- Generují se jen v nově vytvořených chunkech; staré oblasti se zpětně neprokopávají.
- Výchozí šance je 1 % na nový chunk. Místnost vznikne jen uvnitř souvislého přírodního kamene, pokud prostor neobsahuje rudu, vodu, vzduch, stavbu ani jiné bloky.
- Místnost má velikost 5×4×5, skrytý krátký vstup a jednu evidovanou truhlu.
- Truhlu nelze otevřít ani rozbít bez speciálního klíče; chrání ji také kontrola výbuchu. Použití klíče ji trvale odemkne a spotřebuje jeden klíč.
- Truhly se automaticky neplní žádným lootem. Obsah a získávání klíčů zůstanou prázdné, dokud je společně nedomluvíme.

Nastavení je v config.yml v sekci secret-rooms. Název hlavního herního světa není předpokládán: doplň jeho skutečný název do secret-rooms.worlds. Dokud je seznam prázdný, nic se negeneruje. Svět Spawn zůstává vyloučený i tehdy, když ho někdo omylem přidá do seznamu.

## Zachovaný ItemsAdder obsah

Zůstalo pouze třináct licencovaných sladkostí s nezměněnými texturami a nová vlastní 32×32 pixel-art textura klíče. Licenční poznámky a zdrojové SHA-256 záznamy ke sladkostem zůstávají zachované. Staré moby, bossové, eventy, shop, vybavení, relikvie, náhrobky, dekorace, zvuky, obloha a shader nejsou součástí nového balíčku.

## Příkazy

- /halloween help
- /halloween quest start <id>
- /halloween quest progress <id> [množství]
- /halloween quest complete <id>
- /halloween quest status [id]
- /halloween fragments
- /halloween key give [hráč] [počet] (admin)
- /halloween rooms status
- /halloween reload (admin)

## Sestavení a nasazení

Po sestavení stáhni artefakt HalloweenCore JAR a ItemsAdder ZIP z GitHub Actions. JAR vlož do plugins, samostatně nainstaluj UplineDialogues a volitelně ItemsAdder + PlaceholderAPI. ItemsAdder obsah zkopíruj do plugins/ItemsAdder/contents/warriorland_halloween/, spusť /iazip a ověř doručení resource packu klientům. Po změně JARu server restartuj.

Tato větev musí projít CI a testem na staging serveru; samotné sestavení není důkazem živého testu.
