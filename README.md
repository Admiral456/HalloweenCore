# HalloweenCore — questy, fragmenty a tajné místnosti

Tato verze je omezená na dialogové questy, evidenci jejich stavu, fragmenty, tajné podzemní místnosti se zamčenou truhlou a speciální klíč. Zachovává také třináct sladkostí a jejich původní textury ze Stev's Halloween pod licencí MIT.

## Dialogy přes BetonQuest

Dialogy a kontrolu cílů zajišťuje BetonQuest 3.2+. Připravený příklad najdeš v balíčku betonquest/QuestPackages/warriorland_halloween_dialogues; GitHub Actions z něj vytváří ZIP artefakt WarriorLand-Halloween-BetonQuest.

- Dialogy se upravují v YAML souborech, nikoli přes webový editor.
- Pro přiřazení dialogu potřebuješ Citizens NPC. Přirozeně spawnutý villager nemá Citizens ID; vytvoř NPC typu villager a jeho skutečné ID vlož do package.yml.
- Ukázkový dialog je pouze test: farmář požádá o 10 pšenic. BetonQuest ověří inventář a odebere předměty; HalloweenCore potom jednorázově připíše 5 fragmentů.
- V ukázkovém questu jsou bonusové klíče vypnuté. Odměnu můžeš změnit v plugins/HalloweenCore/config.yml.
- Podrobný návod: docs/DIALOGUE_LIBRARY_CZ.md.

BetonQuest a Citizens je potřeba nainstalovat samostatně. UplineDialogues už není vyžadován.

## Odstraněno

- Všechny původní Halloween eventy a jejich automatické plánování.
- Halloween mobové, jejich modely a textury, Headless Nick a staré encountery.
- Staré bossy včetně Krále upírů a arénové mechaniky.
- Generování hřbitova a Halloween vesnice.
- Staré pasivní odměny za zabíjení, těžbu, farmaření a rybaření.
- Starý obchod, prokletí, relikvie, stará výbava, denní výzvy, sezónní přepínače, hudba, shader a dekorace světa.
- Konfigurace, příkazy a skripty sloužící výhradně odstraněným systémům.

Žádný ze starých mobů, bossů ani původních eventů se v této verzi znovu nezapíná.

## Odměny za questy

HalloweenCore odmění quest pouze při prvním úspěšném volání /halloween quest complete <id>. Pro konkrétní ID lze nastavit pevný počet fragmentů nebo obtížnost a rozsah v quest-rewards.quests.<id>. Šance na klíče se konfigurují samostatně. Ukázkové ID farmer_wheat je nastaveno na pět fragmentů bez bonusového klíče.

Klíče mají tři serverem ověřované rarity: common, rare a legendary. Všechny používají tutéž existující ItemsAdder texturu warriorland_halloween:secret_key; rarita je uložená v PersistentDataContainer, neodvozuje se od názvu předmětu.

## Tajné podzemní místnosti

- Generují se jen v nově vytvořených chunkech; staré oblasti se zpětně neprokopávají.
- Výchozí šance je 1 % na nový chunk. Místnost vznikne jen uvnitř souvislého přírodního kamene, pokud prostor neobsahuje rudu, vodu, vzduch, stavbu ani jiné bloky.
- Místnost má velikost 5×4×5, skrytý krátký vstup a jednu evidovanou truhlu.
- Rarity common / rare / legendary mají výchozí váhy 70 / 25 / 5.
- Truhlu nelze otevřít ani rozbít bez klíče stejné rarity; chrání ji také kontrola výbuchu a automatického přenosu přes hopper.
- Při prvním odemčení se připíšou fragmenty podle rarity a truhla se naplní nastavenými vanilla předměty. Loot se znovu nehází při opětovném otevření.

Nastavení je v plugins/HalloweenCore/config.yml v sekci secret-rooms. Přidej skutečný název herního světa do secret-rooms.worlds. Dokud je seznam prázdný, nic se negeneruje. Svět Spawn zůstává vyloučený.

## Zachovaný ItemsAdder obsah

Zůstalo třináct licencovaných sladkostí s nezměněnými texturami a vlastní 32×32 pixel-art textura klíče. Licenční poznámky a zdrojové SHA-256 záznamy ke sladkostem zůstávají zachované. Staré moby, bossové, eventy, obchod, vybavení, relikvie, náhrobky, dekorace, zvuky, obloha a shader nejsou součástí nového balíčku.

## Příkazy

- /halloween help
- /halloween quest start <id>
- /halloween quest progress <id> [množství]
- /halloween quest complete <id>
- /halloween quest status [id]
- /halloween fragments
- /halloween key give [common|rare|legendary] [hráč] [počet] (admin)
- /halloween rooms status
- /halloween reload (admin)

## Sestavení a nasazení

GitHub Actions sestaví plugin JAR a vytvoří dva obsahové ZIP balíčky: ItemsAdder a BetonQuest. Z úspěšného běhu stáhni artefakty:
- HalloweenCore-JAR
- WarriorLand-Halloween-ItemsAdder
- WarriorLand-Halloween-BetonQuest

JAR vlož do plugins/ a server restartuj. ItemsAdder obsah zkopíruj do plugins/ItemsAdder/contents/warriorland_halloween/, spusť /iazip a ověř doručení resource packu. BetonQuest ZIP rozbal do plugins/BetonQuest/ a postupuj podle návodu pro přiřazení Citizens NPC ID.

CI ověřuje konfigurace, loot tabulky, ukázkový BetonQuest balíček, Maven sestavení i zabalení artefaktů. Po sestavení je stále nutný živý test na serveru — zejména ověření NPC ID, konverzace a reward flow.
