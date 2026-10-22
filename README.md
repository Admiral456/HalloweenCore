# HalloweenCore

Denní Halloween questy, fragmenty a obchod pro Paper/Purpur. Tajné místnosti a klíče jsou odstraněné; interní efekt fog/darkness je vypnutý.

`/quest` přidělí jeden úkol na den: zabíjení zombie/kostlivců, těžbu železné rudy nebo lámání dýní. Postup se ukládá v `plugins/HalloweenCore/players.yml`; po splnění dostaneš výchozí **1 fragment**. Další úkol přijde další den podle `Europe/Prague`.

`/halloween shop` otevře dvoustránkový černo-oranžový inventářový obchod. Nabízí 13 sladkostí, suroviny, netherit, totem, zlatá jablka, trojzubec, Mace, netheritovou výbavu a Elytru; kopí jen pokud ho API serveru podporuje. Platí se fragmenty, ceny jsou v `shop.prices`.

Zbraně mají oranžovo-zlatý název a enchant glow. **Vanilla ikonu nelze skriptem přebarvit bez resource packu/modelu.** Sladkosti používají ItemsAdder API, je-li dostupné; jinak se použije vanilla náhrada.

Výchozí sezóna je **23. října až 2. listopadu včetně**. Kód podporuje přechod mezi měsíci a migruje staré výchozí datum 27.–31. října.
Automatické spuštění Horror Nights se řídí stejným sezónním oknem; ruční spuštění přes `/halloween horror start` zůstává dostupné pro administrátorské testování i mimo sezónu.

Zůstává 13 licencovaných sladkostí Stev's Halloween a jejich MIT licence/hash manifest. Kód a textura klíče i generování tajných místností jsou odstraněny. V repozitáři není generátor hrobu ani shader oblohy. Pokud oblohu mění externí plugin/pack mimo repo, musí se odstranit tam.

Příkazy: `/quest`, `/halloween shop`, `/halloween fragments`, `/halloween quest start|progress|complete|status <id>`, `/halloween horror start|stop|status [world]`, `/halloween reload`. Sezónní `/spooky`, `/spookyboss`, `/spookyraid`, `/pumpkinrain`, `/bloodrain` zůstávají.

GitHub Actions ověřuje konfiguraci a obsah, sestavuje JAR přes Maven a balí ItemsAdder ZIP. Build nenahrazuje živý test. JAR vlož do `plugins/` a restartuj; ItemsAdder ZIP dej do `plugins/ItemsAdder/contents/warriorland_halloween/` a spusť `/iazip`.
