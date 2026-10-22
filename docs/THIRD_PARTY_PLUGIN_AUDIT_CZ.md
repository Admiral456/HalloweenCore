# Audit převzetí funkcí a licencí

Datum auditu: 22. 10. 2026

## SpookySeason 1.7.0

- Projekt deklaruje licenci MIT. Adaptované zdrojové soubory jsou pod `src/main/java/cz/halloween/core/spooky/`.
- Zachováno je upozornění na původní projekt a text licence; viz `THIRD_PARTY_NOTICES.md` a `licenses/spookyseason-MIT.txt`.
- Integrované moduly zahrnují trick-or-treat, strašidelné noci a mlhu, zvuky, krvavý a dýňový déšť, hejno netopýrů, jumpscare, sezónního bosse, vlnové nájezdy, statistiky, odměny, menu, osobní preference, jazykové soubory a volitelné integrační vrstvy.
- Aktualizátor původního projektu a bStats telemetry se nepřebírají.
- Hlavní lifecycle je sloučen do HalloweenCore; nebyl přidán druhý JavaPlugin entry point.

## HorrorNights 1.0.0

- Vydavatelská stránka uvádí All Rights Reserved.
- Nebyl převzat JAR bytecode, zdrojový kód, původní texty ani assety.
- Nový modul `HorrorNightsManager` nezávisle implementuje nastavitelnou pravděpodobnost, výběr světů, strašidelné noci, posílené moby, občasnou neviditelnost, zvuky, částice, efekty a ruční administrátorské ovládání.

## Zachovaný obsah HalloweenCore

- Dialogové questy, evidence fragmentů, odměny a speciální klíče.
- Tajné podzemní místnosti a jejich uzamčené truhly.
- Třináct sladkostí, původní textury a stávající manifest SHA-256.

## Nevyřízené ověření

- CI sestavení ještě neověřuje chování na živém Purpur/Paper serveru.
- Je třeba otestovat generování místností v chráněných oblastech, odemykání truhel, všechny sezónní příkazy, nájezdy, efekty a ukončování strašidelných nocí.
