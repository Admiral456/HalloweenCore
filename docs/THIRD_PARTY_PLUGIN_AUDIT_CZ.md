# Audit třetích stran

- Plný zdrojový modul **SpookySeason 1.7.0** od BoondockSulfur je integrován pod MIT licencí; text licence je v `THIRD_PARTY_LICENSES/SpookySeason-MIT.txt` a zdrojové soubory mají atribuční hlavičky.
- **HorrorNights** je nadále vlastní implementace inspirovaná veřejně popsanými funkcemi; jeho zdrojový kód ani assety nejsou kopírovány.
- Aktivní runtime závislostí je Paper API. PlaceholderAPI, ItemsAdder, WorldGuard, GriefPrevention a CustomJukebox jsou volitelné integrace; při jejich nedostupnosti musí plugin bezpečně zůstat funkční s odpovídajícími náhradami.
- Zachovaných 13 ikon sladkostí pochází z assetové sady Stev's Halloween. Jejich licence, atribuce a SHA-256 manifest zůstávají v repozitáři.
- Oranžové zbraně se generují jako ItemsAdder item definice pomocí moderního `graphics.color`; nevytváří se ani nekopírují nové PNG textury. Tato funkce vyžaduje ItemsAdder 4.0.13+ a Minecraft 1.21.4+ na serveru i klientovi. Pro cílovou verzi Minecraft 1.21.10 se generují trojzubec, palcát, netheritový meč a sekera; kopí není v 1.21.10 dostupné. Viz [oficiální dokumentace ItemsAdder](https://wiki.itemsadder.com/adding-content/colored-items/).
- Tajné místnosti, klíče, klíčová textura a nefunkční generátor hrobů jsou mimo aktuální rozsah.
