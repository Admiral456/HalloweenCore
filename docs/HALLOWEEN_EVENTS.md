# HalloweenCore — průvodce eventy

Tento přehled popisuje chování aktuální implementace, ne pouze texty v oznámeních.

## Jak se spouštějí
- Po zapnutí přes `/halloween on` se první náhodný event plánuje po krátké prodlevě (výchozí 30 sekund).
- Další eventy se náhodně plánují každých 15–24 minut a trvají 6 minut.
- Přibližně každých 25 sekund event vybere jednoho způsobilého hráče a pokusí se poblíž něj vytvořit další vlnu.
- Počet eventových mobů je omezen na 48 na svět. Eventové moby se po ukončení eventu uklidí.
- Během eventu se ve výchozím nastavení zdvojnásobuje šance na výskyt speciálního Halloween moba. Vzhled ale vyžaduje nainstalované ModelEngine/MythicMobs assety a doručený resource pack.

## Šest eventů

### 1. Duševní bouře
Každá vlna se pokusí přivolat 3 nepřátele z trojice Prokletý zombie, Hrobník a Krvavý pavouk. Kolem hráče se objeví duševní částice. Zabíjení dává 2× fragmenty a rybaření 1,5×.

### 2. Čarodějnická hodina
Vlna se pokusí přivolat 3 nepřátele z Hexové čarodějky, Krvavého pavouka a Prokletého zombie. Objevují se čarodějnické/portálové částice. Při každém pulzu je přibližně 50% šance na Darkness na 180 ticků (9 s). Těžba a lov dávají 1,75× fragmenty, rybaření 1,25×.

### 3. Prokletá sklizeň
U vybraného hráče se zkusí posunout růst až 5 okolních nedozrálých plodin o jeden stupeň v okruhu 8 bloků. Navíc se přivolají až 2 Krvaví pavouci nebo Dýňoví přízraci přímo na dostupné bloky orné půdy (farmland) v okolí. Pokud poblíž není pole, vlna se objeví bezpečně poblíž hráče. Sklizeň má 3× násobič fragmentů, rybaření 1,25× a zabíjení 1,5×.

**Důležité:** aktuální kód pole ani plodiny neničí. Staré oznámení naznačovalo, že strážci pole pohltí; to bylo zavádějící. Event urychluje plodiny a přivolává nepřátele, ale farma zůstává nedotčená.

### 4. Krvavý měsíc — invaze
Každá vlna se pokusí přivolat 4 nepřátele z Prokletého zombie, Hrobníka, Krvavého pavouka, Dýňového přízraku a Hexové čarodějky. Během posledních 2 minut se jednou pokusí objevit Kapitán invaze poblíž způsobilého hráče. Před hráči se vykresluje rudý lunární disk z částic. Bukkit ale nedokáže přebarvit skutečnou texturu měsíce nebo základní nebe klienta, takže jde o herní efekt, nikoli o změnu klientského skyboxu. Hráči dostávají 3× poškození od monster. Limit je 48 eventových mobů na svět; zabíjení dává 2,25× fragmenty a těžba 1,25×.

### 5. Dýňová apokalypsa
Každá vlna se pokusí přivolat 5 nepřátel; Dýňový přízrak má ve výběru dvojnásobnou váhu oproti Prokletému zombie. Objevují se plameny, láva a popel. Zabíjení dává 2,5× fragmenty, sklizeň 1,5×.

### 6. Hřbitov vstává
Každá vlna se pokusí přivolat 4 nepřátele. Hrobník má dvojnásobnou váhu, ostatní možnosti jsou Prokletý zombie a Krvavý pavouk. Objevují se duševní částice a popel. Zabíjení dává 2,25× fragmenty a těžba 1,5×. Event může běžet i mimo vygenerovaný hřbitov — jeho vlny cílí na hráče.

## Náhodné vesnice a hřbitovy
Tohle je samostatný systém, nezávislý na aktivním eventu. Při zpracování vhodného načteného Overworld chunku je výchozí šance Haunted Village 1,8 % a po opravě šance hřbitova 13,5 %. Souhrnná šance jedné z těchto struktur je tedy až 15,3 % na vhodný chunk. Chunky u spawnu, ve vyloučených světech, blízko arény, s nevhodným terénem nebo s již existující strukturou se přeskočí. Generátor nevykopává jeskyně ani hluboké díry. Při zvýšení šance staré chunky bez struktury dostanou jednorázový nový pokus po dalším načtení; stávající stavby se nemají duplikovat.

## Testovací příkazy
- `/halloween event start <id>` spustí event okamžitě. ID: `soulstorm`, `witching-hour`, `cursed-harvest`, `blood-moon-invasion`, `pumpkin-apocalypse`, `graveyard-rising`.
- `/halloween boss test` nebo `/halloween spawn vampire-king` vyvolá testovacího Krále upírů před administrátorem bez uložené arény. Testovací kill nedává odměny ani neodemkne finále.
- `/halloween spawn all` vyvolá po jednom každého z devíti testovacích mobů včetně Bezhlavého Nicka na koni. Jednotlivě lze použít `/halloween spawn pumpkin-wraith 2`; `/halloween spawn vampire-king` otestuje bosse bez uložené arény. Testovací mobové se po pěti minutách odstraní a nedávají fragmenty ani loot.
- `/halloween giveitem <hráč> <odměna-ID> [počet]` dá item z katalogu bez odečtení fragmentů a bez spotřebování jednorázového nároku.

Bezhlavý Nick se objevuje v noci v Overworldu, má kostlivého koně, pronásleduje hráče a svým prokletým úderem je zpomaluje a způsobuje jim Wither. Jeho model má odstraněnou geometrii hlavy, ne jen skrytou texturu.

Na povrchu generátoru přibyly i skvrny prokleté půdy: soul soil, mycelium, coarse dirt a podzol. Generátor doplňuje jen přírodní povrchy a má nový marker verze, aby se starší dekorované chunky mohly jednorázově doplnit.

Úspěšné GitHub Actions sestavení ověřuje kód a assety automaticky; nenahrazuje test na živém serveru.
