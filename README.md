# HalloweenCore — questy, tajné místnosti a sezónní Halloween

HalloweenCore zachovává dialogové questy, fragmenty, tajné podzemní místnosti se zamčenou truhlou, rarity klíčů a třináct existujících sladkostí. Navíc integruje sezónní funkce zdrojového projektu SpookySeason 1.7.0 pod licencí MIT. Funkce inspirované HorrorNights jsou napsané nezávisle; jeho kód, bytecode ani assety se nekopírují.

## Questy a dialogová integrace

HalloweenCore ukládá stav úkolů, fragmenty a jednorázové odměny. Dialogy a jejich cíle nejsou natvrdo definované v tomto repozitáři; zvolený dialogový plugin může používat příkazy `/halloween quest start|progress|complete|status` a PlaceholderAPI hodnoty `%halloween_fragments%`, `%halloween_quest_progress_<id>%`, `%halloween_quest_started_<id>%` a `%halloween_quest_completed_<id>%`. Ukázkový farmářský dialog a jeho testovací BetonQuest balíček jsou odstraněny.

## Odstraněno

- Všechny původní Halloween eventy a jejich automatické plánování.
- Halloween mobové, jejich modely a textury, Headless Nick a staré encountery.
- Staré bossy včetně Krále upírů a arénové mechaniky.
- Generování hřbitova a Halloween vesnice.
- Staré pasivní odměny za zabíjení, těžbu, farmaření a rybaření.
- Starý obchod, prokletí, relikvie, stará výbava, denní výzvy, sezónní přepínače, hudba, shader a dekorace světa.
- Konfigurace, příkazy a skripty sloužící výhradně odstraněným systémům.

Původní systémy HalloweenCore byly odstraněny. Sezónní moduly SpookySeason jsou samostatně integrovaná sada s vlastní konfigurací a příkazy, nikoli obnovení starého kódu HalloweenCore.

## Odměny za questy

HalloweenCore odmění quest pouze při prvním úspěšném volání /halloween quest complete <id>. Pro konkrétní ID lze nastavit pevný počet fragmentů nebo obtížnost a rozsah v quest-rewards.quests.<id>. Šance na klíče se konfigurují samostatně. Žádné konkrétní ID úkolu není předdefinováno; odměny nastavuj až pro skutečně vytvořené questy.

Klíče mají tři serverem ověřované rarity: common, rare a legendary. Všechny používají tutéž existující ItemsAdder texturu warriorland_halloween:secret_key; rarita je uložená v PersistentDataContainer, neodvozuje se od názvu předmětu.

## Tajné podzemní místnosti

- Generují se jen v nově vytvořených chunkech; staré oblasti se zpětně neprokopávají.
- Výchozí šance je 1 % na nový chunk. Místnost vznikne jen uvnitř souvislého přírodního kamene, pokud prostor neobsahuje rudu, vodu, vzduch, stavbu ani jiné bloky.
- Místnost má velikost 5×4×5, skrytý krátký vstup a jednu evidovanou truhlu.
- Rarity common / rare / legendary mají výchozí váhy 70 / 25 / 5.
- Truhlu nelze otevřít ani rozbít bez klíče stejné rarity; chrání ji také kontrola výbuchu a automatického přenosu přes hopper.
- Při prvním odemčení se připíšou fragmenty podle rarity a truhla se naplní nastavenými vanilla předměty. Loot se znovu nehází při opětovném otevření.

Nastavení je v plugins/HalloweenCore/config.yml v sekci secret-rooms. Přidej skutečný název herního světa do secret-rooms.worlds. Dokud je seznam prázdný, nic se negeneruje. Svět Spawn zůstává vyloučený.

## Zachovaný ItemsAdder obsah

Zůstalo třináct licencovaných sladkostí s nezměněnými texturami a vlastní 32×32 pixel-art textura klíče. Licenční poznámky a zdrojové SHA-256 záznamy ke sladkostem zůstávají zachované. Staré moby, bossové, eventy, obchod, vybavení, relikvie, náhrobky, dekorace, zvuky, obloha a shader nejsou součástí nového balíčku.

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

GitHub Actions sestaví plugin JAR a vytvoří obsahový ZIP balíček pro ItemsAdder. Z úspěšného běhu stáhni artefakty:
- HalloweenCore-JAR
- WarriorLand-Halloween-ItemsAdder

JAR vlož do plugins/ a server restartuj. ItemsAdder obsah zkopíruj do plugins/ItemsAdder/contents/warriorland_halloween/, spusť /iazip a ověř doručení resource packu. Questy napoj na dialogový plugin podle jeho dokumentace.

CI ověřuje konfigurace, loot tabulky, Maven sestavení i zabalení artefaktů. Po sestavení je stále nutný živý test na serveru — zejména ověření NPC ID, konverzace a reward flow.


## Nově integrované sezónní funkce

Ze SpookySeason jsou adaptované sezónní noci a zvuky, trick-or-treat při interakci s dveřmi a vesničany, krevní a dýňový déšť, hejna netopýrů, jumpscare efekty, sezónní boss, vlnové nájezdy, hráčské preference, menu, statistiky a odměny. Podporované doplňkové integrace zahrnují PlaceholderAPI, WorldGuard, GriefPrevention a volitelně CustomJukebox. Sezónní aktivita se řídí hodnotami `active` a `activeWindow` v config.yml; výchozí kalendářní okno je 27.–31. října.

## Strašidelné noci

Samostatný modul nabízí náhodnou šanci na strašidelnou noc, upravené moby, příležitostně neviditelné nepřátele se stopou částic, zvukové a temné efekty, filtr světů a ruční administrátorské řízení přes `/halloween horror start|stop|status [svět]`. Pravděpodobnost, typy mobů, síla a efekty se nastavují v sekci `horrorNights`. Tento modul je nově napsaný nezávisle podle veřejného popisu funkcí HorrorNights; licence původního HorrorNights je All Rights Reserved.

## Licenční poznámky

- Adaptovaný kód SpookySeason: MIT, původní autor BoondockSulfur; úplné znění v `licenses/spookyseason-MIT.txt`.
- HorrorNights: All Rights Reserved; jeho zdrojový kód, bytecode ani assety nebyly převzaty.
- Stávající licence a hash manifest 13 textur sladkostí ze Stev's Halloween zůstávají zachovány.
- Podrobnosti auditu: `docs/THIRD_PARTY_PLUGIN_AUDIT_CZ.md`.
