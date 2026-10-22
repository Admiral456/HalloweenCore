# Audit implementace HalloweenCore

- `/quest` přiděluje denní úkol: zombie/kostlivci, železná ruda nebo dýně. Po splnění připíše výchozí 1 fragment.
- `/halloween shop` otevře dvoustránkový inventářový obchod za fragmenty.
- Nabízí 13 sladkostí, suroviny, netherit, totem, jablka, trojzubec, Mace, netheritovou výbavu a Elytru. Kopí pouze pokud jej API podporuje.
- Sezóna je 23. 10.–2. 11. včetně a migruje starý rozsah.
- Fog/darkness je vypnutý. Java kód, konfigurace, textura i ItemsAdder definice klíčů a generování místností jsou odstraněny; 13 sladkostí zůstává beze změny.
- Bez resource packu/modelu nejde přebarvit vanilla ikonu zbraně, jen nastavit oranžové jméno a glow.
- V repozitáři nebyl nalezen generátor hrobu ani shader oblohy; externí pack/plugin mimo repo nelze ovládat. Živý server nebyl dostupný.
