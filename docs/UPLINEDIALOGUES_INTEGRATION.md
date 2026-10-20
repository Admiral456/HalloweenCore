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
8. Textový katalog patnácti hotových dialogů je v docs/DIALOGUE_LIBRARY_CZ.md. UplineDialogues používá vlastní lokální formát stromů; tento dokument je obsahová specifikace k přepsání do editoru, nikoli neověřený automatický importní soubor.

## Připravené questy

Quest IDs jsou definovaná v src/main/resources/config.yml. Obtížnost a popis se zobrazují v quest statusu a required-items obsahuje požadavky na odevzdání. Pro všech 15 questů jsou odměny ponechané nenastavené. Nepřidávej do dialogu command action, která rozdává předměty nebo klíče, dokud se nedomluví jejich získávání.

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
- /locate halloweenroom [max-vzdálenost] nebo /halloween locate room hledá nejbližší skutečně zaznamenanou místnost.
- /locateall halloweenroom vypisuje všechny zaznamenané místnosti aktuálního světa pouze správci.
- Automatické generování se spouští na nově vytvořeném Overworld chunku; již existující chunky se automaticky neprohledávají. Výchozí šance 2,5 % je šancí na pokus; pokud žádný kandidát neprojde kontrolou bezpečného kamenného obalu, místnost se nevygeneruje. Ruční příkaz slouží jako ověřitelný test.
