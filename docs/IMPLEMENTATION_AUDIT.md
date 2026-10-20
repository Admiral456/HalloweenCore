# Audit implementace nové verze HalloweenCore

Stav tohoto dokumentu odpovídá verzi sloučené do main přes PR #58. CI ověřilo sestavení a statické validátory; test na živém Paper/Purpur serveru ani skutečné mapy UplineDialogues tím ověřeny nejsou.

## Co je v kódu připravené

| Oblast | Stav | Poznámka |
|---|---|---|
| Odstranění starých Halloween systémů | Provedeno v pracovní větvi | Staré třídy, konfigurace, mob modely, bossové, eventy, hudba, shader, shop, gear, dekorace a generování hřbitova/vesnice byly odstraněny z této verze. |
| UplineDialogues napojení | Backend příkazy připravené | HalloweenCore umí přijmout ID questu, uložit postup a jednorázově dokončit quest. Vlastní dialogové mapy se vytvářejí přímo v editoru UplineDialogues, ne v YAML HalloweenCore. |
| Podmínky pro větvení | Připravené při použití PlaceholderAPI | Zástupné hodnoty poskytují počet fragmentů, postup questu a stav přijetí/dokončení. Vlastní větve a podmínky ještě musí být vytvořeny v UplineDialogues. |
| Evidence postupu a jednorázové dokončení | Implementováno | Stav hráče se ukládá do players.yml. Opakované dokončení stejného ID znovu nespustí odměnu. |
| Odměny za questy | Implementováno a konfigurováno | Rozsahy 3–5 / 6–9 / 10–15 / 16–25 fragmentů podle obtížnosti; šance na klíč common/rare/legendary se řídí nastavením obtížnosti nebo override konkrétního ID. Neznámé ID má výchozí lehkou obtížnost. |
| Speciální klíč | Implementovány tři rarity bez dalších textur | Common, rare a legendary sdílí existující ItemsAdder položku/texturu; rarita je v PDC. Klíč bez tier značky z UplineDialogues se považuje za common. |
| Tajné místnosti | Implementované, ale bezpečně vypnuté do konfigurace světa | Nové místnosti se generují pouze v nových Overworld chunkech ve výslovně povoleném světě. Výchozí seznam worlds je prázdný; svět Spawn se vždy odmítá. |
| Zamčená truhla | Implementována | Každá místnost má raritu a přijímá pouze stejný tier klíče; chrání ji blokování rozbití, explozí a hopper přenosu, dokud je zamčená. |
| Obsah truhly | Implementováno | Při odemčení se jednorázově hodí fragmentová odměna a do truhly se vloží vanilla materiály, jídlo, očarované knihy/nástroje a vzácné bonusy podle rarity. Bez nových předmětů nebo textur. |
| Převzaté sladkosti a licence | Zachováno | Třináct 16×16 textur sladkostí ze Stev's Halloween zůstává beze změny, s MIT licencí a SHA-256 manifestem. Validátor hash porovnává. |
| Textura klíče | Přidána | Vlastní pixel-art PNG 32×32 je registrována v ItemsAdder. |

## Co ještě chybí před dokončením

1. V UplineDialogues editoru vytvořit vlastní vesničany/dialogové mapy, texty, volby, podmínky a skutečná ID questů. Tato část není vložená do HalloweenCore YAML, protože UplineDialogues drží rozhovory ve vlastním editoru.
2. Domluvit skutečná ID questů a jejich obtížnost v UplineDialogues. Odměna neznámého ID je lehká; střední/těžké úkoly je nutné přiřadit v quest-rewards.quests.<id>.difficulty.
3. V UplineDialogues vybrat zdroj klíčů. Přímé vydání ItemsAdder položky dává common klíč; rare/legendary může dostat questová odměna nebo admin příkaz.
4. Nastavit přesný název běžného herního světa v secret-rooms.worlds. Prázdný seznam je záměrný fail-closed stav; žádný svět není odhadován a Spawn zůstává vyloučen.
5. Projít testy na stagingu: UplineDialogues příkazy a placeholder podmínky, doručení stávající klíčové textury, generování místností všech tří rarit, spotřeba klíče správné rarity, jednorázové fragmenty a loot, rozbití, hopper a exploze.

## CI versus reálný server

GitHub Actions kontroluje obsah/licenční hashe, sestavení JARu a ZIP ItemsAdder. Úspěšný build nepotvrzuje, že ItemsAdder texturu vykreslí klientovi nebo že generování a ochrana truhel fungují na živém Purpur/Paper serveru. Dokud neproběhne staging test, nepovažuj tuto větev za produkčně ověřenou.
