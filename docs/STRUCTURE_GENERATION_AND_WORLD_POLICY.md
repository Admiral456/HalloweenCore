# Generování Halloween struktur a ochrana světů

## Ruční stavby

Používej ve hře jeden přímý příkaz:

- `/halloween spawn hřbitov`
- `/halloween spawn vesnice`
- `/halloween spawn aréna`
- `/halloween spawn vampirearena`
- `/halloween spawn goblinarena`

Příkaz automaticky vybere polohu přibližně osm bloků před hráčem a orientaci odvodí z pohledu. Nepotřebuje `setvillage`, `setvampirearena`, ručně nastavený střed ani druhé potvrzení. Staré příkazy `setvillage`, `setvampirearena` a `buildvampirearena` zůstávají kompatibilní, ale okamžitě spouštějí stavbu před hráčem.

Nepřesměrováváme samotný `/spawn`, protože na serveru může patřit EssentialsX nebo jinému pluginu. Tím neodstraňujeme existující funkci serveru.

### Oprávnění

- `hallowencore.admin`
- `hallowencore.structure.build`
- `hallowencore.structure.force`

Všechny tři uzly musí být povolené. V základním `plugin.yml` mají výchozí hodnotu `op`.

### Chování FORCE

Generátor předem sestaví deterministický plán bloků, kontroluje výšku, světovou hranici a limit bloků, načítá chunky cílové oblasti, zapisuje po dávkách po tazích serveru a před změnou ukládá `BlockState` pro rollback. Přepisuje výhradně v deklarovaném footprintu a clearance objemu. Při chybě se pokusí vrátit všechny změněné bloky; úplnou obnovu block entities nelze považovat za ověřenou, dokud neproběhne test na kopii světa. Příkaz správci předem oznámí, že bloky uvnitř objemu mohou být přepsány.

Aréna Vampire Kinga má průměr 97 bloků, Goblin arena 49 bloků a obecná aréna 37 bloků. Kvůli objemu se zapisuje po dávkách, takže dokončení příkazu může trvat několik sekund. Zpráva „Hotovo“ se posílá až po dokončení plánu a zápisu konfigurace.

## Automatické generování

Automatické generování je záměrně **fail-closed**. Než se začne upravovat terén, musí být v `config.yml` výslovně nastaven seznam skutečných herních světů:

```yaml
world-generation:
  allowed-worlds:
    - "PRESNY_NAZEV_HERNÍHO_SVETA"
  allowed-biomes: []
  structures:
    enabled: true
    one-in-new-chunks: 256
    minimum-distance-blocks: 384
    maximum-surface-variation: 2
  generated-structure-index: []
```

Nahraď `PRESNY_NAZEV_HERNÍHO_SVETA` skutečným názvem ověřeným na serveru. Tento návod záměrně neodhaduje, zda se svět jmenuje `world`, `survival` nebo jinak. Prázdný `allowed-worlds` znamená, že žádné automatické úpravy světa neproběhnou; plugin při startu vypíše upozornění. Seznam biomů může zůstat prázdný (potom se použije vestavěný výběr vhodných biomů), anebo může obsahovat přesné Bukkit názvy biomů.

Svět pojmenovaný `Spawn` je odmítnut přímo v centrální policy bez ohledu na konfiguraci, režim blacklistu ani omylem přidaný název ve whitelistu. Pravidlo platí pro eventové efekty a moby, přirozené speciální moby, automatické povrchové dekorace a automatické struktury. Staré chunky se automaticky neprocházejí a zpětně neupravují. Dekorace a větší automatické struktury se posuzují jen při načítání nového chunku.

### Automatické hřbitovy a osady

Automatické kandidáty se vybírají deterministicky podle seedu světa a souřadnic chunku. Pravděpodobnost určuje `one-in-new-chunks`; čím vyšší číslo, tím jsou struktury vzácnější. Jedna automatická šablona má půdorys 15×15 bloků, takže se vejde do jednoho nového 16×16 chunku. Jsou připravené dvě varianty: malý hřbitov s náhrobky a kryptou a kompaktní strašidelná osada se dvěma domky, studnou, cestami a dekoracemi.

Kandidát se odmítne, pokud povrch neodpovídá povolenému přírodnímu materiálu, pokud je výškový rozdíl příliš velký, pokud oblast obsahuje stromy či jiné překážky nebo block entities. Kontroluje se také minimální vzdálenost od dříve zapsaných automatických struktur. Automatický režim není FORCE a nesmí rozebírat stavby hráčů.

## Co je a není ověřeno

- **Build/CI:** ověřuje kompilaci JARu a automatické kontroly assetů; po přidání unit testů také čistou logiku světové policy.
- **NOT TESTED:** načtení na reálném Purpuru/WarriorLandu, multiplayer, skutečný přepis přes stromy a svah, plný rollback block entities, automatické generování v konkrétních biomes a kompatibilita s WorldGuard/FAWE/CoreProtect.
- **NOT TESTED:** samotné boss fighty Krále upírů a Goblin Kinga nejsou automaticky spouštěné pouhou stavbou arény. Jejich samostatné encounter systémy a modely musejí být ověřené na serveru.

Před zapnutím automatické generace udělej zálohu testovacího světa a ověř si, že skutečný cílový svět je v allow-listu. Žádná změna na produkčním WarriorLandu není součástí této větve.
