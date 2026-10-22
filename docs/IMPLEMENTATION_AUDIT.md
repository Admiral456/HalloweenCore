# Audit aktuální implementace HalloweenCore

- Aktivní plugin obsahuje jen denní questy, fragmenty a halloweenský obchod. Starý SpookySeason/HorrorNights gameplay kód a jeho příkazy byly z větve odstraněny.
- Příkaz `/quest` přiděluje jeden denní úkol: zabíjení zombie/kostlivců, těžbu železné rudy nebo lámání dýní. Ukládá se postup i dokončení do `plugins/HalloweenCore/players.yml`; za dokončení je výchozí odměna 1 fragment.
- Úkoly a obchod jsou dostupné v období 23. října až 2. listopadu včetně podle časového pásma `Europe/Prague`. Staré nastavení 27.–31. října se při startu migruje.
- Příkaz `/halloween shop` otevírá černo-oranžové GUI. Nabízí 13 zachovaných sladkostí, suroviny, vzácné předměty a drahou koncovou výbavu; nákup odečítá fragmenty a kontroluje místo v inventáři.
- Tajné místnosti, klíče, jejich textury/ItemsAdder definice a starý BetonQuest testovací balíček jsou odstraněné.
- Ikony vanilla zbraní nelze přebarvit jen Java kódem. Obchod používá oranžovo-zlaté názvy a enchant glow; skutečná změna ikon vyžaduje resource pack/model.
- V repozitáři není generátor hrobu ani asset pro shader/oblohu. Pokud oblohu mění jiný serverový plugin, resource pack nebo klientský shader, musí se upravit samostatně.
- CI ověřuje 13 candy PNG, SHA-256 manifest, konfiguraci a ceny, sestavuje JAR přes Maven a testuje ZIP s ItemsAdder obsahem. Úspěšný build není náhradou živého testu na Paper/Purpur serveru.
