# Audit implementace HalloweenCore

Tento dokument shrnuje ověřitelné součásti repozitáře v rámci portu SpookySeason a samostatné implementace hororových nocí. Statické CI samo o sobě není živým testem Paper/Purpur.

## Zachované systémy HalloweenCore

- Questy, ukládání stavu v players.yml, fragmenty a jednorázové odměny.
- Klíče se serverově evidovanou raritou.
- Generování tajných podzemních místností v nových chunkech a zabezpečené truhly.
- Třináct textur sladkostí a samostatná textura klíče.
- Ukázkový farmářský dialog a BetonQuest export již nejsou součástí buildu.

## Sezónní subsystém SpookySeason 1.7.0

Adaptace zdrojového kódu s licencí MIT: trick-or-treat, sezónní strašidelné noci, krevní a dýňový déšť, netopýří hejna, jumpscare, boss, vlnové nájezdy, sezónní odměny, statistiky, osobní preference, menu, lokalizace, migrace konfigurace, ukládání dat a volitelné integrace. Original updater i bStats telemetry nejsou zapnuty. Licence a atribuce jsou v `THIRD_PARTY_NOTICES.md`.

## HorrorNights

Vydavatelská stránka uvádí All Rights Reserved. Jeho JAR, bytecode, kód, původní texty a assety nejsou kopírovány. Funkce nocí, nastavení světů, posílení mobů, neviditelnost, částice, zvuky a hráčské efekty jsou napsány nezávisle na základě veřejného seznamu vlastností.

## Ověření, které ještě vyžaduje běh serveru

- CI musí potvrdit sestavení po portování zdrojového kódu do aktuální Paper API.
- Na testovacím Paper/Purpur serveru je potřeba projít všechny příkazy, trick-or-treat, boss event, déšť, nájezdy, ochranu regionů a ukončování strašidelných nocí.
- Tajné místnosti je třeba ověřit v běžném herním světě; prázdný seznam `secret-rooms.worlds` generování bezpečně vypíná a svět Spawn zůstává vyloučený.
