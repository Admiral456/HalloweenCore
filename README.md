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

### Odměny za questy a klíče

HalloweenCore při prvním dokončení úkolu připíše fragmenty podle nastavené obtížnosti a provede jeden nezávislý hod na klíč. Výchozí fragmentové rozsahy jsou:
- Lehký: 3–5 fragmentů; šance na klíč: common 10 %, rare 1 %, legendary 0,1 %.
- Střední: 6–9 fragmentů; common 15 %, rare 3,5 %, legendary 0,5 %.
- Těžký: 10–15 fragmentů; common 20 %, rare 6 %, legendary 2 %.
- Velmi těžký: 16–25 fragmentů; common 25 %, rare 10 %, legendary 8 %.

ID úkolů a jejich příběh zůstávají v UplineDialogues; není vymyšlen žádný přednastavený quest ani cíl. Neznámé ID dostane výchozí lehkou odměnu. Pro konkrétní ID lze v `config.yml` nastavit `quest-rewards.quests.<id>.difficulty`, přepsat fragmenty nebo jednotlivé šance na klíč. Stav dokončení a použitá odměna se uloží, takže opakované dokončení stejného ID nevyplatí odměnu znovu. Připravené jsou pouze fragmenty a klíče; nepřidávají se žádné vlastní předměty, schopnosti, peníze ani další textury.

Klíče mají tři serverem ověřované rarity: common, rare a legendary. Všechny používají tutéž existující ItemsAdder texturu `warriorland_halloween:secret_key`; rarita je uložená v PersistentDataContainer a nepoznává se podle názvu. Přímá akce UplineDialogues Give item vydá obyčejný klíč. Vyšší rarity může vydat questová odměna nebo správce příkazem `/halloween key give rare [hráč] [počet]` či `/halloween key give legendary [hráč] [počet]`.

## Tajné podzemní místnosti

- Generují se jen v nově vytvořených chunkech; staré oblasti se zpětně neprokopávají.
- Výchozí šance je 1 % na nový chunk. Místnost vznikne jen uvnitř souvislého přírodního kamene, pokud prostor neobsahuje rudu, vodu, vzduch, stavbu ani jiné bloky.
- Místnost má velikost 5×4×5, skrytý krátký vstup a jednu evidovanou truhlu.
- Každá místnost má jednu ze tří rarit: common / rare / legendary. Poměr při generování je výchozí 70 / 25 / 5 a jde změnit v konfiguraci.
- Truhlu nelze otevřít ani rozbít bez klíče přesně odpovídající rarity; chrání ji také kontrola výbuchu a automatického přenosu přes hopper. Použití klíče ji trvale odemkne a spotřebuje jeden klíč.
- Při prvním odemčení dostane hráč jednorázově 8–16 / 20–35 / 40–70 fragmentů podle rarity a truhla se naplní nastavenými vanilla surovinami, jídlem, očarovanými knihami/nástroji a malou šancí na vzácnější bonus. Loot se znovu nehází při opětovném otevření.

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
- /halloween key give [common|rare|legendary] [hráč] [počet] (admin)
- /halloween rooms status
- /halloween reload (admin)

## Sestavení a nasazení

Po sestavení stáhni artefakt HalloweenCore JAR a ItemsAdder ZIP z GitHub Actions. JAR vlož do plugins, samostatně nainstaluj UplineDialogues a volitelně ItemsAdder + PlaceholderAPI. ItemsAdder obsah zkopíruj do plugins/ItemsAdder/contents/warriorland_halloween/, spusť /iazip a ověř doručení resource packu klientům. Po změně JARu server restartuj.

CI run [39424417548](https://github.com/Admiral456/HalloweenCore/actions/runs/39424417548) prošel validací konfigurace a loot tabulek, Maven sestavením i zabalením JARu a ItemsAdder ZIPu. Živé testování na staging serveru a vlastní dialogové mapy v UplineDialogues jsou stále samostatné kroky.
