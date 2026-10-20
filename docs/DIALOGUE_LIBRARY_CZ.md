# Katalog českých dialogů pro UplineDialogues

Tento soubor je připravená obsahová knihovna pro editor UplineDialogues. Není to vlastní náhradní dialogový engine HalloweenCore ani neověřený importní soubor: uzly a odpovědi se založí v UplineDialogues pomocí jeho editoru. UplineDialogues 3.0 uvádí editor přes /updialog panel, strom rozhovorů, šablony, podmínky, inventářové akce a příkazy spouštěné jako hráč, OP nebo konzole: https://www.spigotmc.org/resources/uplinedialogues.112285/

## Společný vzor pro každý quest

V UplineDialogues vytvoř pro každého zadavatele rozhovor se čtyřmi větvemi: **příběhová informace**, **Přijímám úkol**, **Odevzdat předměty** a **Odejít**.

- Akce volby „Přijímám úkol“: příkaz spuštěný jako hráč /halloween quest start ID.
- Akce volby „Odevzdat předměty“: příkaz spuštěný jako hráč /halloween quest turnin ID. HalloweenCore sám zkontroluje celý seznam požadovaných předmětů, odebere je teprve po kontrole všech požadavků a dokončí quest jednorázově. Pro běžný turn-in není potřeba spouštět příkaz jako OP.
- Podmínky viditelnosti: nabídku přijmutí lze skrýt, když %halloween_quest_ID_started% je true; nabídku odevzdání zobraz jen při aktivním a nedokončeném questu. U již dokončeného questu zobraz krátké poděkování.
- U hidden_room_report a sealed_archive musí hráč nejdříve skutečně vstoupit do vygenerované místnosti. HalloweenCore zapíše postup při vstupu do prostoru místnosti; teprve potom může dialog úkol dokončit.
- Odměny nejsou přednastavené. Pokud později domluvíme fragmenty nebo konkrétní předměty, nastaví se v src/main/resources/config.yml; do té doby se nic navíc nevydává.

## 1. Vesnický farmář Hugo — Čerstvá sklizeň

**ID:** fresh_harvest · **Obtížnost:** lehká · **Požadavek:** 16 pšenic

**Začátek:** „Letošní sklizeň není špatná, ale zásoby nám mizí rychleji, než je stíhám doplňovat. Pomůžeš mi?“
- **„Co přesně potřebuješ?“** → „Stačí mi šestnáct pšenic. Nemusí být z mého pole, ale ať jsou zralé a čisté.“
- **„Přijímám úkol.“** → akce /halloween quest start fresh_harvest; odpověď „Výborně. Až budeš mít pšenici, vrať se za mnou.“
- **„Mám pšenici.“** → akce /halloween quest turnin fresh_harvest; větev poděkování po dokončení: „Přesně tohle jsem potřeboval. Zásoby jsou zachráněné.“
- **„Teď nemůžu.“** → „Rozumím. Pole na tebe počká.“

## 2. Kameník Břetislav — Kámen na opravy

**ID:** stone_for_repairs · **Obtížnost:** lehká · **Požadavek:** 24 kamenných kostek

**Začátek:** „Cesta na sever se začíná rozpadat. Když ji neopravíme, povozy se brzy nedostanou do vesnice.“
- **„Co mám přinést?“** → „Čtyřiadvacet kamenných kostek. Hladké bloky nech klidně doma, použiju obyčejný materiál.“
- **„Beru zakázku.“** → /halloween quest start stone_for_repairs; „Skvěle, já připravím nářadí.“
- **„Materiál je tady.“** → /halloween quest turnin stone_for_repairs; po dokončení: „Dobrá práce. S touhle zásobou cestu udržíme sjízdnou.“
- **„Proč je cesta poškozená?“** → „V noci se ozývají rány z kopců. Nikdo zatím nezjistil proč.“

## 3. Kartografka Ela — Papírová stopa

**ID:** paper_trail · **Obtížnost:** lehká · **Požadavek:** 12 papírů

**Začátek:** „Staré mapy se rozpadly a nové cesty už do nich nemám kam zakreslit.“
- **„Kolik papíru potřebuješ?“** → „Dvanáct listů. Nejdřív doplním obyčejné mapy, potom se pustím do těch starších.“
- **„Přijímám úkol.“** → /halloween quest start paper_trail; „Děkuju. Na mapách závisí víc lidí, než se zdá.“
- **„Přinesl jsem zásobu.“** → /halloween quest turnin paper_trail; po dokončení: „Teď konečně překreslím cestu kolem starého lomu.“
- **„Co je za lomem?“** → „To bych ráda věděla. V původní mapě je ta část úmyslně začerněná.“

## 4. Strážný Tonda — Jídlo pro hlídku

**ID:** food_for_watch · **Obtížnost:** lehká · **Požadavek:** 8 chlebů

**Začátek:** „Hlídka se protáhla a kuchyně už zavřela. Noc bude dlouhá.“
- **„Můžu pomoct?“** → „Přines osm chlebů. Nechci, aby někomu při noční směně došla síla.“
- **„Přijímám.“** → /halloween quest start food_for_watch; „Dám pozor na cestu, zatímco budeš pryč.“
- **„Jídlo je připravené.“** → /halloween quest turnin food_for_watch; po dokončení: „Hlídka ti děkuje. Teď zvládneme až do svítání.“
- **„Viděl jsi něco podezřelého?“** → „Jen světla hluboko pod zemí. Mohla to být odražená láva… nebo taky ne.“

## 5. Knihovnice Mara — Základy knihovny

**ID:** village_library · **Obtížnost:** střední · **Požadavek:** 4 knihy

**Začátek:** „Nová knihovna má prázdné police. Knihy se ztrácejí a některé svazky se vracejí bez posledních stran.“
- **„Jaké knihy sháníš?“** → „Čtyři běžné knihy pro katalog. Vzácné rukopisy zatím nechci riskovat.“
- **„Přijímám úkol.“** → /halloween quest start village_library; „Zanesu tvé jméno do seznamu pomocníků.“
- **„Mám knihy.“** → /halloween quest turnin village_library; po dokončení: „Výborně. Alespoň začneme znovu vést záznamy.“
- **„Kdo je odnáší?“** → „Nikdo to neviděl. Ráno jsou jen otevřená okna a prach na podlaze.“

## 6. Mědikovec Oldřich — Měděné vzorky

**ID:** copper_samples · **Obtížnost:** střední · **Požadavek:** 16 měděných ingotů

**Začátek:** „Potřebuji ověřit, zda se měď z východního naleziště chová stejně jako ta ze starých štol.“
- **„Kolik mědi?“** → „Šestnáct ingotů. Roztav je normálně, nechci surovou rudu.“
- **„Přijímám úkol.“** → /halloween quest start copper_samples; „Budu připravovat zkoušky na kovadlině.“
- **„Vzorky jsem přinesl.“** → /halloween quest turnin copper_samples; po dokončení: „Tohle mi stačí. Když zjistím něco nového, řeknu to vesnici.“
- **„Proč to zkoumáš?“** → „Některé kovové předměty ze štol po čase zčernají. Zatím nevíme proč.“

## 7. Kovář Radovan — Železná žádanka

**ID:** iron_requisition · **Obtížnost:** střední · **Požadavek:** 12 železných ingotů

**Začátek:** „Nářadí se poslední dobou opotřebovává rychleji. Než začnu vyrábět další sadu, musím doplnit železo.“
- **„Kolik ingotů?“** → „Dvanáct. Nepotřebuji hotové nástroje, jen samotné ingoty.“
- **„Beru zakázku.“** → /halloween quest start iron_requisition; „Díky. Kovárna bude připravená, až se vrátíš.“
- **„Železo je tady.“** → /halloween quest turnin iron_requisition; po dokončení: „Teď můžu doplnit náhradní krumpáče pro horníky.“
- **„Proč se nářadí láme?“** → „Lámou se i nové kusy. Jako by na ně v hlubinách působilo něco víc než kámen.“

## 8. Geoložka Nela — Fialové krystaly

**ID:** amethyst_study · **Obtížnost:** střední · **Požadavek:** 6 ametystových úlomků

**Začátek:** „V jeskyni za vodopádem jsem našla krystaly, které slabě světélkují i bez světla.“
- **„Co mám přinést?“** → „Šest ametystových úlomků. Prosím, nerozbíjej kvůli nim celou geodu.“
- **„Přijímám výpravu.“** → /halloween quest start amethyst_study; „Zaznamenám si, kde přesně jsi je našel.“
- **„Mám vzorky.“** → /halloween quest turnin amethyst_study; po dokončení: „Jejich barva se mění podle hloubky. To je důležitá stopa.“
- **„Je jeskyně nebezpečná?“** → „Je klidná, ale zvuk se tam vrací, i když už nikdo nemluví.“

## 9. Alchymistka Žofie — Sada pro alchymistku

**ID:** alchemist_components · **Obtížnost:** těžká · **Požadavek:** 8 pavoučích očí, 8 skleněných lahví a 4 plamenné prášky

**Začátek:** „Dělám ochrannou směs, ale bez správných surovin je to jen barevná voda.“
- **„Co všechno potřebuješ?“** → „Osm pavoučích očí, osm skleněných lahví a čtyři plamenné prášky. Některé složky se shánějí hůř, já počkám.“
- **„Pustím se do toho.“** → /halloween quest start alchemist_components; „S ničím nespěchej. Lahve musí být nepoškozené.“
- **„Suroviny jsem přinesl.“** → /halloween quest turnin alchemist_components; po dokončení: „Tohle už je kompletní sada. Výsledek zapíšu do laboratorního deníku.“
- **„Proč ochranná směs?“** → „Někdo se vrátil z hluboké štoly s popáleninami, přestože se nedotkl lávy.“

## 10. Archivář Viktor — Ztracené záznamy

**ID:** archivist_records · **Obtížnost:** těžká · **Požadavek:** 8 knih a 12 papírů

**Začátek:** „Staré zápisy se musejí přepsat. Originály jsou křehké a některé části deníku chybí.“
- **„Co mám připravit?“** → „Osm knih a dvanáct papírů. Přepíšu do nich poznámky o cestách pod vesnicí.“
- **„Přijímám úkol.“** → /halloween quest start archivist_records; „Nespěchej, ale dej pozor na vlhkost. Každá stránka se počítá.“
- **„Záznamy jsou připravené.“** → /halloween quest turnin archivist_records; po dokončení: „Tahle kopie zůstane v knihovně. Originál schovám zvlášť.“
- **„Co je v deníku?“** → „Popisy místností, které na starých mapách vůbec nejsou zakreslené.“

## 11. Mistr kovář — Zakázka kováře

**ID:** blacksmith_order · **Obtížnost:** těžká · **Požadavek:** 24 železných a 12 zlatých ingotů

**Začátek:** „Tahle zakázka není pro začátečníky. Potřebuji kov na opravu výstroje pro dlouhou výpravu.“
- **„Jaká je objednávka?“** → „Dvacet čtyři železných a dvanáct zlatých ingotů. Bez zlatých součástí se zámky ve výstroji zaseknou.“
- **„Přijímám zakázku.“** → /halloween quest start blacksmith_order; „Dobře. Výprava nevyrazí, dokud nebude všechno připravené.“
- **„Všechno jsem přinesl.“** → /halloween quest turnin blacksmith_order; po dokončení: „Materiál sedí. Teď můžeme zajistit vybavení pro celý tým.“
- **„Kam výprava míří?“** → „K místům, která byla v mapách vyškrtnuta. Více ti zatím říct nemohu.“

## 12. Průvodce Emil — Výprava do hlubin

**ID:** deep_expedition · **Obtížnost:** velmi těžká · **Požadavek:** 3 enderové perly a 8 plamenných prášků

**Začátek:** „Prozkoumat hluboké průchody bez přípravy je nejrychlejší způsob, jak se ztratit.“
- **„Co potřebuješ?“** → „Tři enderové perly a osm plamenných prášků. Budeme připravovat navigační značky a nouzové světlo.“
- **„Přijímám výpravu.“** → /halloween quest start deep_expedition; „Vrať se, až budeš mít všechno. Tady se improvizace nevyplácí.“
- **„Výbava je hotová.“** → /halloween quest turnin deep_expedition; po dokončení: „Dobrá. Teď konečně můžeme připravit plán trasy.“
- **„Proč jsou potřeba perly?“** → „Průchody jsou klikaté a obyčejné značky v některých místech nestačí.“

## 13. Badatelka Irena — Zpráva ze skryté místnosti

**ID:** hidden_room_report · **Obtížnost:** těžká · **Požadavek:** najít skutečně vygenerovanou tajnou místnost

**Začátek:** „V některých částech podzemí jsou dutiny, které na mapách chybí. Pokud nějakou najdeš, potřebuji přesné souřadnice.“
- **„Přijímám úkol.“** → /halloween quest start hidden_room_report; „Nechci domněnky. Vstup dovnitř, prohlédni si ji a potom se vrať.“
- **„Našel jsem místnost.“** → /halloween quest turnin hidden_room_report; HalloweenCore tento turn-in povolí teprve poté, co hráč vstoupí do prostoru evidované místnosti.
- **„Jak ji najdu?“** → „Místnosti se objevují jen v nově generovaných podzemních částech světa. Souřadnice ti ukáže /locate halloweenroom.“
- **„Vrátím se později.“** → „Budu tady. Prosím, nezkoušej místo rozkopat výbušninami.“

## 14. Strážce archivu — Zapečetěný archiv

**ID:** sealed_archive · **Obtížnost:** velmi těžká · **Požadavek:** vstoupit do tajné místnosti, 8 knih, 16 papírů a 6 ametystových úlomků

**Začátek:** „Starý archiv nelze popsat jen zvenčí. Nejdřív musíš najít jednu z ukrytých místností a potom připravit materiál pro záznam.“
- **„Přijímám úkol.“** → /halloween quest start sealed_archive; „Nejdřív prohlídka. Bez ní mi materiál k ničemu nebude.“
- **„Všechno mám připravené.“** → /halloween quest turnin sealed_archive; turn-in vyžaduje dokončený průzkumný cíl a všechny uvedené předměty.
- **„Proč tolik materiálu?“** → „Záznam bude mít několik kopií, aby se už nikdy neztratil jako staré mapy.“
- **„Zatím ne.“** → „Rozumím. Tuhle práci je lepší neuspěchat.“

## 15. Noční badatel Pavel — Výzkum po setmění

**ID:** night_research · **Obtížnost:** velmi těžká · **Požadavek:** 8 pavoučích očí, 16 železných ingotů a 4 enderové perly

**Začátek:** „Výsledky měření se mění po setmění. Chci ověřit, jestli je to náhoda, nebo něco reaguje na noc.“
- **„Jaké vzorky mám přinést?“** → „Osm pavoučích očí, šestnáct železných ingotů a čtyři enderové perly. Vím, že to není jednoduché.“
- **„Přijímám úkol.“** → /halloween quest start night_research; „Vrať se, až bude kompletní sada. Bez kompletních vzorků by měření nemělo cenu.“
- **„Vzorky jsou tady.“** → /halloween quest turnin night_research; po dokončení: „Díky. Teď můžu porovnat noční a denní výsledky.“
- **„Co když je to nebezpečné?“** → „Pak to musíme zjistit dřív, než se někdo další vydá do hlubin bez přípravy.“

## 16. Lesní sběratelka Běla — Vzorky z lesa

**ID:** mushroom_samples · **Obtížnost:** lehká · **Požadavek:** 12 červených a 12 hnědých hub

**Začátek:** „Les se mění podle půdy a světla. Potřebuji porovnat houby z obou stran potoka.“
- **„Jaké vzorky?“** → „Dvanáct červených a dvanáct hnědých hub. Sbírej je opatrně, ať zůstane část podhoubí.“
- **„Přijímám úkol.“** → `/halloween quest start mushroom_samples`; „Díky. Na okraji lesa najdeš obvykle oba druhy.“
- **„Vzorky mám.“** → `/halloween quest turnin mushroom_samples`; po dokončení: „Tohle mi pro porovnání stačí. Rozdíl zapíšu do zápisníku.“
- **„Proč je porovnáváš?“** → „Některé houby rostou i u kamene, kde by podle starých poznámek nemělo být nic živého.“

## 17. Hostinská Róza — Lůžka pro hostinec

**ID:** wool_for_bedding · **Obtížnost:** lehká · **Požadavek:** 16 bílých vln

**Začátek:** „Po cestě sem míří stále víc poutníků. Chci jim připravit čisté postele, než přijde další déšť.“
- **„Co potřebuješ?“** → „Šestnáct bílých vln. Barvenou nechci, aby byly všechny pokoje stejné.“
- **„Rád pomůžu.“** → `/halloween quest start wool_for_bedding`; „Děkuju. Až je přineseš, můžu dokončit poslední dvě místnosti.“
- **„Tady je vlna.“** → `/halloween quest turnin wool_for_bedding`; po dokončení: „Teď můžeme ubytovat další cestující.“
- **„Kdo tu nocoval naposledy?“** → „Horníci. Ráno tu nechali mapu se značkou tam, kde podle ní vůbec žádná chodba být nemá.“

## 18. Kuchař Matěj — Úlovek z řeky

**ID:** river_catch · **Obtížnost:** střední · **Požadavek:** 8 tresek a 4 lososy

**Začátek:** „Dnes čekám návštěvu z druhého břehu. Potřebuji ryby, ale nechci vybrat řeku do poslední.“
- **„Kolik ryb?“** → „Osm tresek a čtyři lososy. Nic dalšího, ať ve vodě zůstane dost ryb.“
- **„Přijímám úkol.“** → `/halloween quest start river_catch`; „Zkus klidnější úsek u ohybu řeky.“
- **„Ryby jsou připravené.“** → `/halloween quest turnin river_catch`; po dokončení: „To bude stačit pro dnešní návštěvu. Zbytek necháme řece.“
- **„Přišla už zpráva od hostů?“** → „Jen krátká poznámka: dorazí po setmění a budou se ptát na starou cestu pod kopcem.“

## 19. Hrnčíř Jáchym — Hlína pro hrnčíře

**ID:** clay_for_potter · **Obtížnost:** lehká · **Požadavek:** 24 hliněných kuliček

**Začátek:** „Rozbily se nám nádoby na vodu a nové se musí vypálit dřív, než vyschnou zásoby.“
- **„Kolik hlíny?“** → „Dvacet čtyři hliněných kuliček. Připravuji malé džbány, ne velké nádoby.“
- **„Přijímám úkol.“** → `/halloween quest start clay_for_potter`; „Hlínu najdeš u řeky a v některých jeskyních.“
- **„Hlína je tady.“** → `/halloween quest turnin clay_for_potter`; po dokončení: „Dobře. Ještě dnes připravím první várku.“
- **„Proč se nádoby rozbily?“** → „Na některých se objevily tenké praskliny, jako by je zevnitř stlačil kámen.“

## 20. Správce cesty Bohdan — Světlo na cestu

**ID:** lantern_supplies · **Obtížnost:** střední · **Požadavek:** 16 pochodní

**Začátek:** „Cesta k severnímu svahu musí být průchozí i po setmění. Včera tam někdo málem spadl do staré šachty.“
- **„Co mám přinést?“** → „Šestnáct pochodní. Umístím je na bezpečná místa, ne přímo na okraj šachty.“
- **„Přijímám úkol.“** → `/halloween quest start lantern_supplies`; „Až je přineseš, projdu cestu ještě před večerem.“
- **„Pochodně mám.“** → `/halloween quest turnin lantern_supplies`; po dokončení: „Díky. Aspoň uvidíme, kudy cesta vede.“
- **„Je šachta hluboká?“** → „Nevíme. Kámen pod okrajem je hladký a zvuk padajícího kamene trvá nezvykle dlouho.“

## 21. Léčitelka Anežka — Med pro léčitelku

**ID:** honey_for_healer · **Obtížnost:** střední · **Požadavek:** 4 lahve medu

**Začátek:** „Na ošetřovně dochází med. Potřebuji ho do běžných směsí a na zásoby pro zimu.“
- **„Kolik lahví?“** → „Čtyři lahve medu. Prosím, nepřines prázdné lahve místo plných.“
- **„Přijímám úkol.“** → `/halloween quest start honey_for_healer`; „Děkuju, budu mezitím připravovat čisté nádoby.“
- **„Med jsem přinesl.“** → `/halloween quest turnin honey_for_healer`; po dokončení: „To nám na nějakou dobu vystačí.“
- **„Je někdo nemocný?“** → „Ne víc než obvykle. Jen pár lidí si stěžovalo na zvláštní kovovou chuť vody z hlubší studny.“

## 22. Truhlář Vít — Dřevo pro truhláře

**ID:** forest_materials · **Obtížnost:** střední · **Požadavek:** 16 dubových klád

**Začátek:** „Opravujeme lavice a dveře ve společenské síni. Staré dřevo už nedrží hřebíky.“
- **„Jaké dřevo potřebuješ?“** → „Šestnáct dubových klád. Potřebuji rovné kusy na rám a nohy stolů.“
- **„Přijímám úkol.“** → `/halloween quest start forest_materials`; „Dávej pozor na označené stromy u vsi, ty nekácej.“
- **„Dřevo je připravené.“** → `/halloween quest turnin forest_materials`; po dokončení: „Výborně. Sál bude znovu použitelný.“
- **„Co se stalo s původním nábytkem?“** → „Jedna lavice se propadla do podlahy. Pod ní byla dutina, kterou na půdorysu nikdo neměl.“

## 23. Sedlář Kamil — Kožené řemeny

**ID:** leather_repair · **Obtížnost:** těžká · **Požadavek:** 12 kusů kůže

**Začátek:** „Řemeny na nákladních brašnách se trhají příliš rychle. Potřebuji materiál na nové, pevnější kusy.“
- **„Kolik kůže?“** → „Dvanáct kusů. Vezmu jen nepoškozenou kůži, kterou lze sešít.“
- **„Přijímám úkol.“** → `/halloween quest start leather_repair`; „Nechám ti místo na pracovním stole.“
- **„Kůže je tady.“** → `/halloween quest turnin leather_repair`; po dokončení: „Díky. Zkontroluji také brašny, které se vrátily z dolů.“
- **„Co se jim stalo?“** → „Na několika kusech jsou oděrky z vnitřní strany, jako by se o ně opíraly ostré kameny.“

## 24. Kartografka Ela — Směr do podzemí

**ID:** map_and_compass · **Obtížnost:** těžká · **Požadavek:** 1 kompas a 8 papírů

**Začátek:** „Chci připravit přesnější mapu okolí kopce. Bez kompasu a dostatku papíru se dál nedostanu.“
- **„Co potřebuješ?“** → „Jeden kompas a osm papírů. Nejde mi o poklad, ale o zakreslení cest, které na starých mapách chybí.“
- **„Přijímám úkol.“** → `/halloween quest start map_and_compass`; „Jakmile bude všechno připravené, vyznačím měřicí body.“
- **„Vybavení mám.“** → `/halloween quest turnin map_and_compass`; po dokončení: „Dobře. Teď budu moct porovnat směr kompasu s původními záznamy.“
- **„Proč jsou mapy nepřesné?“** → „Některé staré čáry končí uprostřed kamene. Nechci si domýšlet, co je za nimi.“

## 25. Horník Zdeněk — Hornické vzorky

**ID:** miner_samples · **Obtížnost:** těžká · **Požadavek:** 12 uhlí a 8 surového železa

**Začátek:** „Připravuji přehled surovin z nových štol. Potřebuji vzorky z různých míst, ne celý náklad z jedné žíly.“
- **„Jaké vzorky?“** → „Dvanáct uhlí a osm kusů surového železa. Vzorky rozdělím podle místa nálezu.“
- **„Přijímám úkol.“** → `/halloween quest start miner_samples`; „Vezmi jen běžné suroviny. Značky na stěnách nech tak, jak jsou.“
- **„Vzorky jsem přinesl.“** → `/halloween quest turnin miner_samples`; po dokončení: „Díky. Některé nové štoly mají jinou skladbu kamene než staré.“
- **„Našel jsi něco neobvyklého?“** → „Zatím jen místa, kde se chodba náhle zužuje. Nechci je rozšiřovat, dokud je nezměříme.“

## Doporučená struktura každého rozhovoru v editoru

1. **Úvodní uzel:** první replika NPC a volby pro vysvětlení, přijetí úkolu, odevzdání a odchod.
2. **Informační uzel:** větev „Co přesně potřebuješ?“; tady vysvětli požadavky a navazující příběh.
3. **Přijetí:** příkaz /halloween quest start ID spuštěný jako hráč, potom přejdi na uzel s poděkováním.
4. **Odevzdání:** příkaz /halloween quest turnin ID spuštěný jako hráč. Po návratu do dialogu podmiň uzel s úspěchem placeholderem %halloween_quest_ID_completed% == true; při nesplněných požadavcích zobraz větev připomínající potřebné předměty.
5. **Dokončená větev:** pokud je _completed true, skryj volbu přijmutí a zobraz poděkování. Quest není možné přijmout znovu.

Příklad pro první quest: /halloween quest start fresh_harvest a /halloween quest turnin fresh_harvest. Pro průzkumný quest se použijí ID hidden_room_report a sealed_archive.

## Hranice této integrace

HalloweenCore už zajišťuje stav úkolu, kontrolu a odebrání požadovaných předmětů, evidenci dokončení a jednorázovou výplatu pouze tehdy, když je nastavena. Samotná grafická mapa rozhovorů se vytváří v editoru UplineDialogues na serveru; bez jeho vlastního vytvořeného/exportovaného souboru se tento textový katalog sám nepřidá do jeho lokálního úložiště.
