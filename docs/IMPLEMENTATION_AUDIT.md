# Audit implementace nové verze HalloweenCore

Stav tohoto dokumentu odpovídá pracovní větvi refactor/dialogues-secret-rooms-scope. Tato verze zatím není sloučena do main ani otestována na živém serveru.

## Co je v kódu připravené

| Oblast | Stav | Poznámka |
|---|---|---|
| Odstranění starých Halloween systémů | Provedeno v pracovní větvi | Staré třídy, konfigurace, mob modely, bossové, eventy, hudba, shader, shop, gear, dekorace a generování hřbitova/vesnice byly odstraněny z této verze. |
| UplineDialogues napojení | Backend příkazy připravené | HalloweenCore umí přijmout ID questu, uložit postup a jednorázově dokončit quest. Vlastní dialogové mapy se vytvářejí přímo v editoru UplineDialogues, ne v YAML HalloweenCore. |
| Podmínky pro větvení | Připravené při použití PlaceholderAPI | Zástupné hodnoty poskytují počet fragmentů, postup questu a stav přijetí/dokončení. Vlastní větve a podmínky ještě musí být vytvořeny v UplineDialogues. |
| Evidence postupu a jednorázové dokončení | Implementováno | Stav hráče se ukládá do players.yml. Opakované dokončení stejného ID znovu nespustí odměnu. |
| Fragmenty a reward hook | Implementováno bez výchozích odměn | Sekce quest-rewards je prázdná. Žádná částka fragmentů, peníze, výbava ani itemy nebyly přiřazeny. |
| Speciální klíč | Implementován | ItemsAdder ID warriorland_halloween:secret_key a vanilla fallback. Klíč se rozpoznává přes HalloweenCore PDC i přes ItemsAdder ID, takže ho lze později vydat dialogovou akcí Give item. |
| Tajné místnosti | Implementované, ale bezpečně vypnuté do konfigurace světa | Nové místnosti se generují pouze v nových Overworld chunkech ve výslovně povoleném světě. Výchozí seznam worlds je prázdný; svět Spawn se vždy odmítá. |
| Zamčená truhla | Implementována | Bez klíče ji hráč neotevře ani nerozbije; blokuje se i přenos přes hopper a destrukce výbuchem. Jeden klíč truhlu trvale odemkne. |
| Obsah truhly | Záměrně prázdný | Loot nebyl domluven, proto se žádný negeneruje. |
| Převzaté sladkosti a licence | Zachováno | Třináct 16×16 textur sladkostí ze Stev's Halloween zůstává beze změny, s MIT licencí a SHA-256 manifestem. Validátor hash porovnává. |
| Textura klíče | Přidána | Vlastní pixel-art PNG 32×32 je registrována v ItemsAdder. |

## Co ještě chybí před dokončením

1. V UplineDialogues editoru vytvořit vlastní vesničany/dialogové mapy, texty, volby, podmínky a skutečná ID questů. Tato část není vložená do HalloweenCore YAML, protože UplineDialogues drží rozhovory ve vlastním editoru.
2. Domluvit cíle jednotlivých questů a kdy přesně mají větve povolit příkaz complete. Backend zatím ukládá čítač postupu; nestanovuje žádné neodsouhlasené cíle.
3. Domluvit odměny, počet fragmentů a způsob získávání klíčů. Výchozí quest-rewards zůstává prázdné a žádný NPC zatím klíč automaticky nedává.
4. Domluvit obsah truhel. Všechny nové truhly jsou do té doby prázdné.
5. Nastavit přesný název běžného herního světa v secret-rooms.worlds. Prázdný seznam je záměrný fail-closed stav; žádný svět není odhadován a Spawn zůstává vyloučen.
6. Projít testy na stagingu: UplineDialogues příkazy a placeholder podmínky, doručení ItemsAdder resource packu a klíčová textura, generování místností, spotřeba klíče, opakované otevření, rozbití a hopper/exploze.

## CI versus reálný server

GitHub Actions kontroluje obsah/licenční hashe, sestavení JARu a ZIP ItemsAdder. Úspěšný build nepotvrzuje, že ItemsAdder texturu vykreslí klientovi nebo že generování a ochrana truhel fungují na živém Purpur/Paper serveru. Dokud neproběhne staging test, nepovažuj tuto větev za produkčně ověřenou.
