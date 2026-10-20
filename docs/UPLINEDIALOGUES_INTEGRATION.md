# UplineDialogues a HalloweenCore — funkční propojení

UplineDialogues je samostatný plugin pro editor dialogů. HalloweenCore jej nepřebaluje ani nenahrazuje: dodává persistentní questy, ověřování předmětů, jednorázové dokončení, průzkumný cíl tajných místností a placeholders.

Oficiální popis UplineDialogues 3.0 uvádí lokální editor přes /updialog panel, větvení rozhovorů, podmínky, inventářové akce a příkazy spouštěné jako hráč, OP nebo konzole. Viz https://www.spigotmc.org/resources/uplinedialogues.112285/.

## Instalace a tvorba dialogů

1. Nainstaluj UplineDialogues samostatně a restartuj server.
2. Spusť /updialog panel a otevři adresu, kterou příkaz vypíše.
3. Pro každého zadavatele vytvoř v editoru rozhovor s uzly: úvod, vysvětlení úkolu, přijetí, odevzdání, úspěch, již dokončeno a odchod.
4. Do větve „Přijmout úkol“ přidej akci jako hráč: /halloween quest start <id>.
5. Do větve „Odevzdat“ přidej akci jako hráč: /halloween quest turnin <id>. HalloweenCore ověří a odebere požadované předměty sám, takže není nutné skládat samostatné podmínky a příkazy pro každý předmět.
6. Pro viditelnost možností použij PlaceholderAPI podmínky. Příklad: nabídku přijetí skryj, pokud %halloween_quest_fresh_harvest_started% je true; po dokončení ukaž větev s poděkováním, pokud %halloween_quest_fresh_harvest_completed% je true.
7. Pro questy hidden_room_report a sealed_archive se nejprve musí hráč fyzicky dostat do prostoru zaznamenané tajné místnosti. Potom se průzkumný postup uloží a dialog mu dovolí quest odevzdat.
8. Textový katalog 25 hotových dialogů je v docs/DIALOGUE_LIBRARY_CZ.md. UplineDialogues používá vlastní lokální formát stromů; tento dokument je obsahová specifikace k přepsání do editoru, nikoli neověřený automatický importní soubor.

## Ověření propojení panelu s herními questy

HalloweenCore zpracovává akce z panelu přes skutečné Bukkit příkazy. Příkaz `/halloween` má oprávnění `halloweencore.use` s výchozí hodnotou `true`; přijetí a odevzdání vlastního questu nevyžadují OP. Pro odevzdání zvol akci jako **hráč**, aby HalloweenCore četl inventář osoby, která mluví s NPC.

1. Spusť `/updialog panel` na serveru a otevři adresu vypsanou příkazem. UplineDialogues 3.0 podle popisu autora ukládá stromy lokálně u své konfigurace a změny mají být živé po uložení.
2. V panelu vytvoř nebo otevři rozhovor a přiřaď ho konkrétní entitě/NPC. Vytvoř větve: úvod, vysvětlení, přijetí, odevzdání, neúspěšné odevzdání a dokončeno. Ulož změny.
3. Do akce volby přijetí přidej **Run command → as player** s `halloween quest start fresh_harvest`. Použij úvodní lomítko pouze tehdy, pokud ho pole editoru vyžaduje. Potom připoj repliku potvrzující přijetí.
4. Do volby odevzdání přidej **Run command → as player** s `halloween quest turnin fresh_harvest` (stejné pravidlo k lomítku). Nedávej tuto akci jako OP ani konzoli — inventář musí patřit mluvícímu hráči.
5. Pro skrývání voleb použij v panelu podmínku PlaceholderAPI: `%halloween_quest_fresh_harvest_started%` je `true`, když je úkol aktivní nebo dokončený; `%halloween_quest_fresh_harvest_completed%` je `true` po dokončení. To vyžaduje PlaceholderAPI. Bez něj se backend stále brání druhému přijetí a opakovanému dokončení.
6. **Test s ne-OP hráčem:** bez 16 pšenic klikni na odevzdání — má to odmítnout odevzdání a nic nesmí odebrat. Potom přines 16 pšenic a zopakuj — quest se dokončí. Znovu zkus přijetí i odevzdání; dokončení se nesmí započítat podruhé.
7. Pokud se dialog v herním světě vůbec neotevře, ověř uložení rozhovoru, přiřazení ke správné entitě/NPC a chybové zprávy UplineDialogues v konzoli. To je odlišné od samotné questové akce.

**Hranice ověření:** příkazy, oprávnění a backend jsou ověřitelné v repozitáři. Nemám přístup k tvému běžícímu serveru ani k lokálním souborům UplineDialogues, takže nemůžu potvrdit již uložený NPC strom na serveru. Markdown katalog se do panelu sám neimportuje.

## Bonusový klíč z villager questů

Po úspěšném odevzdání questu příkazem `/halloween quest turnin <id>` proběhne jeden náhodný hod pro bonusový klíč: **40 % běžný, 10 % vzácný, 5 % legendární**. Jde o absolutní a vzájemně se vylučující šance; v 45 % případů nepadne žádný klíč. Quest se dokončí i tehdy, když klíč nepadne. Ruční administrátorské dokončení questu klíč nelosuje — bonus je jen za skutečné odevzdání hráčem.

Šance jsou v `quests.key-drop-chances.common`, `quests.key-drop-chances.rare` a `quests.key-drop-chances.legendary` jako čísla od 0 do 1. Běžná odměna se nastavuje nezávisle přes `reward-fragments` nebo `reward-items` u jednotlivých questů; není nahrazena klíčem.

## Připravené questy

Quest IDs jsou definovaná v src/main/resources/config.yml. Obtížnost a popis se zobrazují v quest statusu a required-items obsahuje požadavky na odevzdání. Konkrétní běžné odměny nejsou nastavené, protože jejich položky a množství zatím nebyly určeny.

## Quest příkazy

- /halloween quest start <id> — přijme nakonfigurovaný quest hráče; nejde jej znovu přijmout po dokončení.
- /halloween quest turnin <id> — zkontroluje všechny required-items, a až když má hráč vše, odebere předměty a dokončí quest. Pokud mu něco chybí nebo není splněný průzkumný cíl, nic neodebere.
- /halloween quest status [id] — ukáže stav, obtížnost, popis a postup.
- /halloween quest claim <id> — jednorázově vyzvedne odměnu nastavenou až dodatečně, pokud nějaká existuje.
- /halloween fragments — ukáže uložený zůstatek fragmentů.

IDs mají malá písmena, čísla, pomlčky nebo podtržítka, maximálně 48 znaků.

## PlaceholderAPI

PlaceholderAPI je volitelné. Jsou dostupné:
- %halloween_fragments%
- %halloween_quest_<id>_started%
- %halloween_quest_<id>_progress%
- %halloween_quest_<id>_completed%

## Tajné místnosti a locate

- /halloween rooms status zobrazuje počítadla, konfiguraci a poslední zaznamenanou místnost.
- /halloween rooms generate [radius] je administrátorský test v Overworldu; zkouší bezpečné umístění v okruhu několika chunků.
- /halloween rooms verify zkontroluje zaznamenané místnosti v již načtených chunkech a vypíše chybějící či nechráněné truhly; nenačtené chunky neotevírá.
- /locate halloweenroom [max-vzdálenost] nebo /halloween locate room hledá nejbližší skutečně zaznamenanou místnost.
- /locateall halloweenroom vypisuje všechny zaznamenané místnosti aktuálního světa pouze správci.
- Automatické generování se spouští na nově vytvořeném Overworld chunku; již existující chunky se automaticky neprohledávají. Výchozí šance 2,5 % je šancí na pokus, nikoli zárukou místnosti v každém novém chunku; kandidát musí projít kontrolou kamenného obalu i bezpečné cesty do přírodní jeskyně. Použij /halloween rooms generate jako test a /halloween rooms verify ke kontrole záznamů.
- Truhly zůstávají chráněné i po odemčení proti rozbití, ohni, výbuchům TNT/creeperů, přesunu písty a automatickému vkládání/vytahování předmětů hopperem či hopper-minecartem. Po odemčení je lze ručně otevřít.
