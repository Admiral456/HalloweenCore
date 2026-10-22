# HalloweenCore

Denní Halloween questy, fragmenty a obchod pro Paper/Purpur. Staré Halloween moby a eventy i jejich zdrojové soubory jsou odstraněné. Tajné místnosti a klíče jsou odstraněné.

## Denní úkol

Příkaz `/quest` přidělí jeden úkol na den: zabíjení zombie/kostlivců, těžbu železné rudy nebo lámání dýní. Postup se ukládá do `plugins/HalloweenCore/players.yml`; po splnění dostaneš výchozí **1 fragment**. Další úkol přijde další den podle `Europe/Prague`. Denní úkoly jsou aktivní v Halloween sezóně.

## Obchod

`/halloween shop` otevře černo-oranžový inventářový obchod. Nabízí 13 sladkostí, suroviny, netherit, totem, zlatá jablka, trojzubec, Mace, netheritovou výbavu a Elytru; kopí jen pokud ho API serveru podporuje. Platí se fragmenty, ceny jsou v `shop.prices`.

Zbraně mají oranžovo-zlatý název a enchant glow. **Vanilla ikonu nelze bez resource packu/modelu přebarvit pouze Java kódem.** Sladkosti používají ItemsAdder API, je-li dostupné; jinak se použije vanilla náhrada.

## Sezóna a obsah

Výchozí sezóna je **23. října až 2. listopadu včetně**, podle časového pásma `Europe/Prague`. Staré nastavení období 27.–31. října se při startu migruje. Při startu se také odstraní staré konfigurační sekce mobů a eventů z již existující instalace.

Zůstává 13 licencovaných sladkostí Stev's Halloween a jejich MIT licence/hash manifest. Textura klíče i její ItemsAdder definice jsou odstraněny. V repozitáři není generátor hrobu ani shader oblohy; změny oblohy z externího pluginu/resource packu mimo tento repozitář musí být odstraněny tam.

Příkazy: `/quest`, `/halloween shop`, `/halloween fragments`, `/halloween quest start|progress|complete|status <id>`, `/halloween reload`.

GitHub Actions ověřuje textury a konfiguraci, sestavuje JAR přes Maven a balí ItemsAdder ZIP. Build nenahrazuje živý test. JAR vlož do `plugins/` a restartuj; obsah ItemsAdder nahraj do `plugins/ItemsAdder/contents/warriorland_halloween/` a spusť `/iazip`.
