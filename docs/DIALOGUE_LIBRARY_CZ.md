# Kompletní české dialogové stromy pro UplineDialogues

Tento soubor obsahuje celé texty jednotlivých větví a přesné volby hráče, nikoli jen náměty na rozhovor. Dialogy jsou určené k ručnímu sestavení v editoru UplineDialogues přes `/updialog panel`. HalloweenCore automaticky nevytváří lokální datové stromy UplineDialogues.

## Jak má v editoru vypadat každý rozhovor

Každý NPC má samostatný dialogový strom se čtyřmi stavy. Z textů níže vytvoř uzly a připoj uvedené volby.

### A. Úkol ještě nebyl přijat

Zobraz úvodní repliku konkrétního NPC a tyto čtyři možnosti:
- **Zeptat se na podrobnosti** → přejdi na uzel „Podrobnosti“ daného NPC.
- **Přijmout úkol** → spusť jako hráč příkaz `/halloween quest start ID`; pokud se quest úspěšně přijme, zobraz větu „Přijetí“.
- **Odmítnout** → spusť pouze text „Odmítnutí“. Nespouštěj žádný questový příkaz a nic hráči neodebírej.
- **Teď odejít** → zobraz větu „Odchod“ a ukonči rozhovor.

V uzlu „Podrobnosti“ přehraj přesnou repliku z příslušné sekce a nabídni:
- **Dobře, přijímám** → stejný příkaz `/halloween quest start ID`, spuštěný jako hráč, a potom větu „Přijetí“.
- **Ne, odmítám** → věta „Odmítnutí“, bez příkazu a bez změny stavu.
- **Zpět** → vrať se na úvodní uzel.

### B. Úkol je aktivní, ale nedokončený

Podmínka: `%halloween_quest_ID_started% == true` a `%halloween_quest_ID_completed% == false`. Zobraz repliku „Úkol je aktivní“ a možnosti:
- **Připomenout podrobnosti** → uzel „Podrobnosti“.
- **Pokusit se odevzdat** → spusť jako hráč `/halloween quest turnin ID`. Potom vyhodnoť stav dokončení: pokud je `%halloween_quest_ID_completed% == true`, zobraz repliku „Dokončení“; pokud je stále false, zobraz repliku „Ještě to nestačí“. Tato druhá větev hráči nic neodebere, pokud nemá splněné všechny podmínky.
- **Vrátím se později** → zobraz repliku „Odchod“ a ukonči rozhovor. Úkol zůstane aktivní.

### C. Úkol je dokončený

Podmínka: `%halloween_quest_ID_completed% == true`. Nezobrazuj znovu volbu přijmout ani odevzdat úkol. Zobraz repliku „Dokončení“ a nabídni:
- **Připomenout, o čem jsme mluvili** → uzel „Podrobnosti“ bez spuštění příkazu.
- **Rozloučit se** → replika „Odchod“ a konec rozhovoru.

PlaceholderAPI musí být nainstalovaný, pokud UplineDialogues používá placeholdery k podmínění viditelnosti. Příkazy `start` a `turnin` musí běžet jako hráč, ne jako konzole. U průzkumných úkolů se dokončení povolí až po skutečném vstupu do evidované tajné místnosti. Odměny ani výplaty zde nejsou domluvené a tyto dialogy žádnou neslibují.

---

## 1. Farmář Hugo — Čerstvá sklizeň
**ID:** `fresh_harvest` · **Požadavek:** 16 pšenic

**Úvod:** „Zdravím tě. Sklizeň sice dopadla slušně, ale zásoby mizí rychleji, než je stačím doplňovat. Mohl bys mi s jednou věcí pomoct?“

**Podrobnosti:** „Potřebuji šestnáct pšenic. Nemusí být z mého pole. Chci jen doplnit zásoby, než přijde další skupina cestujících. Máš čas se do toho pustit?“

**Přijetí:** „Děkuju, že ses nabídl. Až budeš mít všech šestnáct pšenic, vrať se za mnou.“

**Odmítnutí:** „To je v pořádku. Nechci tě do ničeho nutit. Kdybys změnil názor, najdeš mě u pole.“

**Úkol je aktivní:** „Pšenici ještě stále potřebuji. Vrať se, až budeš mít celou požadovanou zásobu.“

**Ještě to nestačí:** „Ještě nemám všech šestnáct pšenic. Zkontroluj si zásobu a vrať se, až bude kompletní.“

**Dokončení:** „To je přesně ono. Díky, teď můžu doplnit vesnické zásoby.“

**Odchod:** „Dobře, měj se. Budu u pole, kdybys mě potřeboval.“

## 2. Kameník Břetislav — Kámen na opravy
**ID:** `stone_for_repairs` · **Požadavek:** 24 dlažebních kostek

**Úvod:** „Zastav se na chvíli. Cesta na sever se rozpadá a povozy po ní sotva projedou. Potřebuji doplnit stavební materiál.“

**Podrobnosti:** „Přines mi čtyřiadvacet dlažebních kostek. Opravíme jimi nejhorší úsek cesty. Hladký kámen ani jiné bloky mi nepomohou.“

**Přijetí:** „Dobrá. Až budeš mít materiál, přijď za mnou. Nechám tu volné místo, abych ho mohl hned použít.“

**Odmítnutí:** „Rozumím. Každý má vlastní práci. Snad se nám cestu podaří opravit dřív, než přijde další déšť.“

**Úkol je aktivní:** „Oprava cesty stále čeká. Potřebuji přesně ty dlažební kostky, o kterých jsme mluvili.“

**Ještě to nestačí:** „Zatím nemám všech čtyřiadvacet dlažebních kostek. Nic se ti neodebere, dokud nebude celá požadovaná zásoba.“

**Dokončení:** „Výborně, tohle nám stačí na nejhorší místo. Cesta bude zase bezpečnější.“

**Odchod:** „Opatruj se. A až půjdeš na sever, dávej na poškozenou cestu pozor.“

## 3. Kartografka Ela — Papírová stopa
**ID:** `paper_trail` · **Požadavek:** 12 papírů

**Úvod:** „Promiň, že tě zdržuji. Staré mapy se rozpadají a nové cesty už nemám kam zakreslit. Mohl bys mi pomoci doplnit materiál?“

**Podrobnosti:** „Potřebuji dvanáct listů papíru. Zatím chci obnovit běžné mapy, ale narazila jsem i na starý záznam s částí území, která je začerněná.“

**Přijetí:** „Děkuji. Jakmile budeš mít papír, přines ho sem. Zatím připravím mapy, které je nutné přepsat jako první.“

**Odmítnutí:** „Samozřejmě, nemusíš. Jen mě mrzí, že mapy dál chátrají. Kdyby sis to rozmyslel, budu tady.“

**Úkol je aktivní:** „Na papír stále čekám. Bez něj nemůžu mapy přepsat ani zkontrolovat starý záznam.“

**Ještě to nestačí:** „Ještě mi chybí část z dvanácti listů. Až bude požadovaný počet pohromadě, můžeš mi ho odevzdat.“

**Dokončení:** „Děkuji. Teď konečně překreslím cestu kolem starého lomu. Tu začerněnou část zatím nechám beze změny.“

**Odchod:** „Na shledanou. A kdybys někde našel starou mapu, nerozebírej ji — přines ji raději ukázat.“

## 4. Strážný Tonda — Jídlo pro hlídku
**ID:** `food_for_watch` · **Požadavek:** 8 chlebů

**Úvod:** „Stůj — tedy, vlastně nemusíš. Hlídka se protáhla a kuchyně už zavřela. Potřebovali bychom něco k jídlu, než skončí noční směna.“

**Podrobnosti:** „Osm chlebů by stačilo. Nejde o žádnou slavnost, jen nechci, aby hlídka zůstala celou noc bez jídla.“

**Přijetí:** „To nám pomůže. Až je budeš mít, vrať se. Do té doby zůstanu na stanovišti.“

**Odmítnutí:** „Chápu. Zkusíme to nějak vydržet. Hlavně si dávej pozor, pokud půjdeš kolem severní cesty.“

**Úkol je aktivní:** „Hlídka pořád potřebuje osm chlebů. Jakmile budeš připravený, můžeš se pokusit je odevzdat.“

**Ještě to nestačí:** „Tohle ještě není celá zásoba. Potřebuji osm chlebů, aby vyšlo na všechny ve službě.“

**Dokončení:** „Výborně. Teď už máme co rozdělit mezi hlídky. Díky za pomoc.“

**Odchod:** „Pokračuj. Kdyby ses v noci vracel, dej vědět, ať tě nemusíme považovat za podezřelého.“

## 5. Knihovnice Mara — Základy knihovny
**ID:** `village_library` · **Požadavek:** 4 knihy

**Úvod:** „Vítej v knihovně. Police jsou skoro prázdné a některé svazky se vracejí bez posledních stran. Potřebovala bych znovu sestavit základní katalog.“

**Podrobnosti:** „Stačí čtyři obyčejné knihy. Než začneme pátrat po ztracených rukopisech, musím dát dohromady běžné záznamy a ověřit, co přesně chybí.“

**Přijetí:** „Děkuji. Zatím připravím seznam chybějících svazků. Až budeš mít knihy, přines je ke stolu u okna.“

**Odmítnutí:** „To nevadí. Knihovnu budeme muset dát dohromady postupně. Jen prosím neodnášej žádné svazky bez zápisu.“

**Úkol je aktivní:** „Stále čekám na čtyři knihy. Potřebuji je pro nový katalog, ne jako náhradu za vzácné rukopisy.“

**Ještě to nestačí:** „Ještě nemám čtyři knihy. Když je budeš mít všechny, dokončíme zápis najednou.“

**Dokončení:** „Výborně. Základní katalog můžu obnovit. Až budu vědět, které svazky dál chybí, začnu pátrat po zbytku.“

**Odchod:** „Děkuji za návštěvu. V knihovně mluv potichu a starší svazky nech na jejich místě.“

## 6. Mědikovec Oldřich — Měděné vzorky
**ID:** `copper_samples` · **Požadavek:** 16 měděných ingotů

**Úvod:** „Potřebuji ověřit něco ohledně mědi z východního naleziště. Některé staré kusy se chovají jinak než běžný kov.“

**Podrobnosti:** „Přines šestnáct měděných ingotů, prosím. Chci porovnat běžně vytavenou měď s několika starými vzorky. Nechci surovou rudu.“

**Přijetí:** „Dobře. Připravím měřicí nástroje. Až budeš mít ingoty, vrať se do dílny.“

**Odmítnutí:** „Rozumím. Zkoumání může počkat. Jen doufám, že se nám podaří zjistit, proč některé staré předměty tmavnou.“

**Úkol je aktivní:** „Na měděné ingoty stále čekám. Bez stejného počtu vzorků nemá porovnání smysl.“

**Ještě to nestačí:** „Zatím nemám všech šestnáct ingotů. Neodevzdávej nic po částech — nejdřív je potřeba splnit celý požadavek.“

**Dokončení:** „Děkuji. Teď mohu porovnat vzorky podle stejného postupu. Až budu mít výsledek, dám vědět.“

**Odchod:** „Měj se. Pokud najdeš starý měděný předmět, raději ho nejdřív ukaž někomu, kdo ho umí prohlédnout.“

## 7. Kovář Radovan — Železná žádanka
**ID:** `iron_requisition` · **Požadavek:** 12 železných ingotů

**Úvod:** „Zdravím. Nářadí se poslední dobou opotřebovává rychleji než dřív. Než začnu vyrábět nové kusy, musím doplnit železo.“

**Podrobnosti:** „Potřebuji dvanáct železných ingotů. Hotové nástroje mi nepomohou, protože musím materiál zpracovat na náhradní součásti.“

**Přijetí:** „Díky. Až je budeš mít, přines je sem. Kovárnu mezitím připravím.“

**Odmítnutí:** „Dobrá, nebudu tě přemlouvat. Když se ale nářadí začne znovu lámat, budeme muset práci v dolech omezit.“

**Úkol je aktivní:** „Stále potřebuji dvanáct železných ingotů. Bez nich nemůžu vyrobit náhradní součásti.“

**Ještě to nestačí:** „Ještě nemám všech dvanáct ingotů. Vrať se s kompletní zásobou; zatím ti nic neodeberu.“

**Dokončení:** „To stačí. Doplním zásobu součástí pro horníky. Snad jim nové nářadí vydrží déle.“

**Odchod:** „Na shledanou. Pokud se ti v dole začne zdát, že se kámen chová podivně, raději se vrať na povrch.“

## 8. Geoložka Nela — Fialové krystaly
**ID:** `amethyst_study` · **Požadavek:** 6 ametystových úlomků

**Úvod:** „Našla jsem v jeskyni několik krystalů, které vypadají jinak podle toho, kolik světla na ně dopadá. Ráda bych ověřila, jestli jde o běžný jev.“

**Podrobnosti:** „Potřebuji šest ametystových úlomků. Prosím, nerozbíjej kvůli nim celou geodu. Stačí malé vzorky, abych mohla porovnat barvu a lesk.“

**Přijetí:** „Děkuji. Poznamenám si okolnosti nálezu. Až budeš mít šest úlomků, vrať se za mnou.“

**Odmítnutí:** „Rozumím. Geologický průzkum není pro každého. Já zatím doplním poznámky z minulého měření.“

**Úkol je aktivní:** „Na šest úlomků stále čekám. Potřebuji je porovnat za stejných podmínek, proto mi prosím nenos jiné suroviny.“

**Ještě to nestačí:** „Zatím jich nemám všech šest. Až bude celá sada hotová, můžeme měření dokončit.“

**Dokončení:** „Výborně. Tohle bude pro základní porovnání stačit. Zapíšu výsledek a nebudu kvůli němu rozbíjet další geody.“

**Odchod:** „Opatruj se v jeskyních. Když budeš něco zkoumat, vždy si označ cestu zpět.“

## 9. Alchymistka Žofie — Sada pro alchymistku
**ID:** `alchemist_components` · **Požadavek:** 8 pavoučích očí, 8 skleněných lahví a 4 plamenné prášky

**Úvod:** „Potřebuji doplnit suroviny do laboratorních zásob. Bez nich teď nemohu pokračovat v testování ochranné směsi.“

**Podrobnosti:** „Připrav osm pavoučích očí, osm skleněných lahví a čtyři plamenné prášky. Musí to být všechny tři složky v uvedeném množství. Jinak bude směs neúplná.“

**Přijetí:** „Dobře, ale nespěchej. S některými surovinami je potřeba zacházet opatrně. Vrať se, až budeš mít celou sadu.“

**Odmítnutí:** „To chápu. Sběr těchto surovin může být nepříjemný. Zatím budu kontrolovat své poznámky a nic míchat nebudu.“

**Úkol je aktivní:** „Potřebuji celou sadu: osm pavoučích očí, osm skleněných lahví a čtyři plamenné prášky.“

**Ještě to nestačí:** „Některá složka stále chybí. Příkaz zkontroluje vše najednou a žádnou surovinu neodebere, dokud nejsou splněné všechny požadavky.“

**Dokončení:** „Sada je kompletní. Teď mohu bezpečně připravit další pokus a zapsat přesné složení.“

**Odchod:** „Dávej na sebe pozor. A do laboratoře bez dovolení nechoď — některé pokusy ještě nejsou bezpečně uzavřené.“

## 10. Archivář Viktor — Ztracené záznamy
**ID:** `archivist_records` · **Požadavek:** 8 knih a 12 papírů

**Úvod:** „Staré záznamy jsou křehké a některé jejich části chybí. Nechci originály přepisovat narychlo, proto připravuji nové kopie.“

**Podrobnosti:** „Přines osm knih a dvanáct papírů. Do kopií zapíšu staré poznámky o cestách pod vesnicí. Některé popisy se neshodují s mapami, které máme v archivu.“

**Přijetí:** „Děkuji. Než se vrátíš, připravím seznam záznamů. Papír drž v suchu, ať se nám materiál nezničí.“

**Odmítnutí:** „Rozumím. Archiv nikam neuteče. Jen nechci, aby původní poznámky zmizely dřív, než je stihneme bezpečně opsat.“

**Úkol je aktivní:** „Stále čekám na osm knih a dvanáct papírů. Teprve potom budu moci začít přepisovat záznamy.“

**Ještě to nestačí:** „Chybí mi část požadovaných knih nebo papírů. Potřebuji obojí v uvedeném množství.“

**Dokončení:** „Výborně. Kopie začnu ukládat odděleně od originálů. Staré záznamy už nebudeme muset tolik vystavovat opotřebení.“

**Odchod:** „Na shledanou. Pokud narazíš na starý zápis, nemanipuluj s ním zbytečně a dej mi vědět.“

## 11. Mistr kovář — Zakázka kováře
**ID:** `blacksmith_order` · **Požadavek:** 24 železných a 12 zlatých ingotů

**Úvod:** „Mám před sebou větší zakázku na opravu výstroje. Než začnu, potřebuji vědět, jestli se podaří zajistit všechen materiál.“

**Podrobnosti:** „Potřebuji dvacet čtyři železných a dvanáct zlatých ingotů. Oba kovy mají své využití, takže nestačí přinést jen jeden druh.“

**Přijetí:** „Dobrá. Připravím pracovní stůl a nástroje. Přines obě suroviny v celém požadovaném množství, ať můžu práci dokončit najednou.“

**Odmítnutí:** „To je tvoje rozhodnutí. Zakázku odložím a zatím nebudu nic vyrábět.“

**Úkol je aktivní:** „Na zakázku stále potřebuji dvacet čtyři železných a dvanáct zlatých ingotů. Oba požadavky musí být splněné.“

**Ještě to nestačí:** „Jedna z požadovaných surovin nebo její část chybí. Přines kompletní zásobu obou kovů.“

**Dokončení:** „Materiál je kompletní. Můžu se pustit do přípravy součástí a zapsat skutečnou spotřebu kovu.“

**Odchod:** „Měj se. Pokud se rozhodneš vrátit, najdeš mě u kovadliny.“

## 12. Průvodce Emil — Výprava do hlubin
**ID:** `deep_expedition` · **Požadavek:** 3 enderové perly a 8 plamenných prášků

**Úvod:** „Plánovat cestu do hlubokých průchodů bez přípravy je nejrychlejší způsob, jak se ztratit. Nejdřív potřebuji dokončit vybavení.“

**Podrobnosti:** „Přines tři enderové perly a osm plamenných prášků. Připravuji navigační značky a nouzové světlo. Bez těchto materiálů trasu zatím nechci plánovat.“

**Přijetí:** „Dobře. Nespěchej a připrav všechno přesně. Až bude výbava kompletní, vrať se.“

**Odmítnutí:** „Rozumím. Výpravu raději odložím, než abych někoho poslal do hlubin nepřipraveného.“

**Úkol je aktivní:** „Příprava stále čeká na tři enderové perly a osm plamenných prášků.“

**Ještě to nestačí:** „Výbava ještě není kompletní. Potřebuji oba druhy surovin v uvedeném množství.“

**Dokončení:** „To stačí k přípravě vybavení. Než vyrazíme, ještě jednou zkontroluji trasu a nouzové značky.“

**Odchod:** „Opatruj se. Do neznámých chodeb nechoď bez připravené cesty zpět.“

## 13. Badatelka Irena — Zpráva ze skryté místnosti
**ID:** `hidden_room_report` · **Požadavek:** skutečně vstoupit do evidované tajné místnosti

**Úvod:** „Hledám záznamy o dutinách pod zemí, které nejsou na běžných mapách. Nechci jen odhad podle stěny — potřebuji ověřený průzkum.“

**Podrobnosti:** „Pokud najdeš tajnou místnost, vstup dovnitř a pořádně si ji prohlédni. HalloweenCore zaznamená průzkum až tehdy, když dojdeš do prostoru evidované místnosti. Samotné nalezení souřadnic nestačí.“

**Přijetí:** „Děkuji. Nechci, abys kvůli tomu rozkopával okolní chodby. Hledej bezpečně a pokud místnost najdeš, vstup do ní.“

**Odmítnutí:** „To je pochopitelné. Neznámé podzemní prostory mohou být nebezpečné. Nebudu tě do průzkumu nutit.“

**Úkol je aktivní:** „Stále potřebuji zprávu z opravdové tajné místnosti. Můžeš zkusit příkaz `/locate halloweenroom`; po nalezení musíš vstoupit dovnitř.“

**Ještě to nestačí:** „Zatím nemám zaznamenaný vstup do evidované tajné místnosti. Pouhé stání nad ní ani znalost souřadnic nestačí.“

**Dokončení:** „Takže místnost opravdu existuje a někdo do ní vstoupil. Tohle je ověřená informace, kterou jsem potřebovala.“

**Odchod:** „Dávej na sebe pozor. Pokud se vrátíš do podzemí, označ si cestu zpět a nenič konstrukci místnosti.“

## 14. Strážce archivu — Zapečetěný archiv
**ID:** `sealed_archive` · **Požadavek:** vstoupit do tajné místnosti, 8 knih, 16 papírů a 6 ametystových úlomků

**Úvod:** „Starý archiv nelze popsat jen zvenčí. Nejdřív je třeba skutečně vstoupit do jedné z ukrytých místností a potom připravit záznam.“

**Podrobnosti:** „Nejdřív musíš prozkoumat skutečně vygenerovanou tajnou místnost. Potom přines osm knih, šestnáct papírů a šest ametystových úlomků. Potřebuji ověřený průzkum i kompletní materiál.“

**Přijetí:** „Dobrá. Nejdřív ověř místnost, teprve potom sháněj zbytek. Nechci zapisovat domněnky jako fakta.“

**Odmítnutí:** „Rozumím. Tuto práci není dobré uspěchat. Archiv zůstane uzavřený, dokud nebudou údaje ověřené.“

**Úkol je aktivní:** „Potřebuji ověřený průzkum tajné místnosti a celou sadu osmi knih, šestnácti papírů a šesti ametystových úlomků.“

**Ještě to nestačí:** „Není splněný průzkum, chybí některá surovina nebo obojí. Vše musí být hotové před odevzdáním.“

**Dokončení:** „Záznam můžeme bezpečně sestavit. Teď budeme mít kopii, kterou půjde porovnat s dalšími nálezy.“

**Odchod:** „Na shledanou. Do archivních prostor nezasahuj, dokud nebudeme mít hotový záznam.“

## 15. Noční badatel Pavel — Výzkum po setmění
**ID:** `night_research` · **Požadavek:** 8 pavoučích očí, 16 železných ingotů a 4 enderové perly

**Úvod:** „Moje měření se po setmění mění. Chci ověřit, jestli jde jen o chybu přístrojů, nebo na výsledky působí něco v okolí.“

**Podrobnosti:** „Přines osm pavoučích očí, šestnáct železných ingotů a čtyři enderové perly. Potřebuji úplnou sadu materiálu, jinak by porovnání vzorků nebylo spolehlivé.“

**Přijetí:** „Děkuji. Zatím připravím zápisník a nástroje. Vrať se s kompletní sadou, ať můžeme měřit za stejných podmínek.“

**Odmítnutí:** „Chápu. Nechci nikoho vystavovat zbytečnému riziku. S měřením počkám, dokud nebudou lepší podmínky.“

**Úkol je aktivní:** „Stále čekám na celou sadu: osm pavoučích očí, šestnáct železných ingotů a čtyři enderové perly.“

**Ještě to nestačí:** „Některé vzorky chybí. Potřebuji celý seznam, abych mohl měření porovnat.“

**Dokončení:** „To je kompletní. Teď konečně můžu připravit kontrolní měření a porovnat noční výsledky.“

**Odchod:** „Měj se. Jestli budeš venku po setmění, drž se osvětlené cesty.“

## 16. Lesní sběratelka Běla — Vzorky z lesa
**ID:** `mushroom_samples` · **Požadavek:** 12 červených a 12 hnědých hub

**Úvod:** „Porovnávám houby z různých částí lesa. Některé rostou na místech, kde by podle starých poznámek nemělo být dost vláhy.“

**Podrobnosti:** „Potřebuji dvanáct červených a dvanáct hnědých hub. Sbírej je opatrně a nerozebírej kvůli nim celý kus lesa. Potřebuji oba druhy pro srovnání.“

**Přijetí:** „Děkuji. Zatím připravím zápisník. Až budeš mít oba druhy v požadovaném množství, vrať se.“

**Odmítnutí:** „To nevadí. Příroda počká. Já zatím sepíšu poznámky z míst, která jsem už prohlédla.“

**Úkol je aktivní:** „Stále čekám na dvanáct červených a dvanáct hnědých hub. Potřebuji oba druhy.“

**Ještě to nestačí:** „Ještě nemám obě sady v požadovaném počtu. Bez nich by porovnání nebylo úplné.“

**Dokončení:** „Výborně. Teď mohu porovnat oba vzorky a zapsat, v čem se liší.“

**Odchod:** „Na shledanou. V lese nech část rostlin na místě, aby mohly dál růst.“

## 17. Hostinská Róza — Lůžka pro hostinec
**ID:** `wool_for_bedding` · **Požadavek:** 16 bílých vln

**Úvod:** „Do hostince míří víc cestujících než obvykle. Chci připravit čistá lůžka, než přijde další déšť.“

**Podrobnosti:** „Přines šestnáct bílých vln. Potřebuji stejnou barvu pro všechny pokoje. Barvenou vlnu použít nechci.“

**Přijetí:** „Děkuji, to mi pomůže dokončit přípravy. Až budeš mít všech šestnáct kusů, vrať se za mnou.“

**Odmítnutí:** „Samozřejmě. Pokoje připravím postupně, jak to půjde. Nemusíš se do toho pouštět, pokud nechceš.“

**Úkol je aktivní:** „Stále potřebuji šestnáct bílých vln. Teprve potom dokončím přípravu dalších lůžek.“

**Ještě to nestačí:** „Ještě mi chybí část vlny. Potřebuji šestnáct bílých kusů, ne jinou barvu.“

**Dokončení:** „Skvělé, teď můžu dokončit lůžka. Hosté budou mít kde přespat.“

**Odchod:** „Na shledanou. Když se budeš potřebovat schovat před deštěm, můžeš se zastavit v hostinci.“

## 18. Kuchař Matěj — Úlovek z řeky
**ID:** `river_catch` · **Požadavek:** 8 tresek a 4 lososi

**Úvod:** „Čekám hosty z druhého břehu a rád bych jim připravil rybí jídlo. Nechci ale řeku vybrat do poslední ryby.“

**Podrobnosti:** „Stačí osm tresek a čtyři lososi. Potřebuji oba druhy, abych mohl připravit dvě různé porce a zbytek nechat v řece.“

**Přijetí:** „Dobře, děkuji. Až budeš mít oba druhy, přines je do kuchyně.“

**Odmítnutí:** „To je v pořádku. Menu změním podle toho, co bude k dispozici. Nemá smysl nikoho nutit do rybaření.“

**Úkol je aktivní:** „Pořád čekám na osm tresek a čtyři lososy. Potřebuji obě množství.“

**Ještě to nestačí:** „Ještě chybí některé ryby. Až budeš mít všech osm tresek a čtyři lososy, můžeme úkol dokončit.“

**Dokončení:** „Výborně, to je přesně to, co potřebuji. Zbytek úlovku necháme řece.“

**Odchod:** „Měj se. Pokud půjdeš rybařit, vybírej klidnější místo, kde se dá bezpečně dostat k vodě.“

## 19. Hrnčíř Jáchym — Hlína pro hrnčíře
**ID:** `clay_for_potter` · **Požadavek:** 24 hliněných kuliček

**Úvod:** „Několik nádob se nám rozbilo a zásoby vody se musí bezpečně uložit. Potřebuji připravit hlínu na další várku.“

**Podrobnosti:** „Přines čtyřiadvacet hliněných kuliček. Budu z nich vyrábět menší nádoby. Ostatní materiál teď nepotřebuji.“

**Přijetí:** „Díky. Připravím pec a pracovní stůl. Až budeš mít celou zásobu hlíny, vrať se.“

**Odmítnutí:** „Rozumím. Výrobu odložím, dokud nebudeme mít dost materiálu. Nemusíš kvůli tomu měnit svoje plány.“

**Úkol je aktivní:** „Stále čekám na čtyřiadvacet hliněných kuliček. Pak budu moci připravit další várku.“

**Ještě to nestačí:** „Ještě nemám celých čtyřiadvacet kuliček hlíny. Potřebuji kompletní množství.“

**Dokončení:** „Výborně. Teď mohu připravit hlínu a vypálit nové nádoby.“

**Odchod:** „Na shledanou. U řeky si dávej pozor na měkký břeh, někde se propadá.“

## 20. Správce cesty Bohdan — Světlo na cestu
**ID:** `lantern_supplies` · **Požadavek:** 16 pochodní

**Úvod:** „Cestu k severnímu svahu je potřeba lépe osvětlit. Včera někdo málem spadl do staré šachty.“

**Podrobnosti:** „Přines šestnáct pochodní. Umístím je na bezpečné úseky cesty. Nebudu je dávat přímo na okraj šachty, dokud neověříme, jak je hluboká.“

**Přijetí:** „Děkuji. Až budeš mít pochodně, přines je sem. Potom znovu projdu cestu a vyberu bezpečná místa.“

**Odmítnutí:** „Chápu. Do té doby cestu označím, jak nejlépe umím. Ve tmě raději nechoď blízko staré šachty.“

**Úkol je aktivní:** „Stále potřebuji šestnáct pochodní. Bez nich nemohu dokončit osvětlení cesty.“

**Ještě to nestačí:** „Ještě nemám všech šestnáct pochodní. Až bude zásoba kompletní, můžeme pokračovat.“

**Dokončení:** „Výborně. Teď můžu bezpečně naplánovat osvětlení cesty.“

**Odchod:** „Na shledanou. Pokud půjdeš kolem severního svahu, drž se vyznačené cesty.“

## 21. Léčitelka Anežka — Med pro léčitelku
**ID:** `honey_for_healer` · **Požadavek:** 4 lahve medu

**Úvod:** „Doplňuji zásoby na ošetřovně. Některé běžné směsi se bez medu připravují obtížně a před zimou chci mít vše připravené.“

**Podrobnosti:** „Potřebuji čtyři plné lahve medu. Prázdné lahve mi nepomohou. Zkontroluj, že přinášíš opravdu med v lahvích.“

**Přijetí:** „Děkuji. Připravím čisté nádoby a místo na zásoby. Až budeš mít všechny čtyři lahve, vrať se.“

**Odmítnutí:** „Rozumím. Zásoby budu muset rozdělit úsporněji. Nemusíš se do toho pouštět, pokud nechceš.“

**Úkol je aktivní:** „Stále potřebuji čtyři plné lahve medu. Prázdné lahve ani jiné suroviny tento požadavek nenahradí.“

**Ještě to nestačí:** „Ještě mi chybí část medu. Potřebuji čtyři plné lahve, potom mohu zásoby doplnit.“

**Dokončení:** „Děkuji. Teď máme zásoby pro běžné směsi připravené.“

**Odchod:** „Opatruj se. Pokud někdo potřebuje ošetřit, pošli ho za mnou do ošetřovny.“

## 22. Truhlář Vít — Dřevo pro truhláře
**ID:** `forest_materials` · **Požadavek:** 16 dubových klád

**Úvod:** „Opravujeme dveře a lavice ve společenské síni. Staré dřevo už nedrží dobře a potřebuji materiál na nové části.“

**Podrobnosti:** „Přines šestnáct dubových klád. Potřebuji rovné kusy na rámy a nohy stolů. Označené stromy u vesnice nekácej.“

**Přijetí:** „Dobře. Připravím nářadí a pracovní prostor. Až budeš mít všech šestnáct klád, vrať se.“

**Odmítnutí:** „To je v pořádku. Opravy můžu rozdělit na menší části. Jen prosím nenič označené stromy u vsi.“

**Úkol je aktivní:** „Stále potřebuji šestnáct dubových klád. Bez nich nemůžu připravit nové rámy a součásti nábytku.“

**Ještě to nestačí:** „Ještě nemám celých šestnáct dubových klád. Přines kompletní množství a můžeme pokračovat.“

**Dokončení:** „Výborně. Teď můžu připravit materiál a pustit se do oprav.“

**Odchod:** „Na shledanou. V lese si dávej pozor na označené stromy — ty jsou ponechané záměrně.“

## 23. Sedlář Kamil — Kožené řemeny
**ID:** `leather_repair` · **Požadavek:** 12 kusů kůže

**Úvod:** „Řemeny na nákladních brašnách se trhají příliš rychle. Potřebuji připravit nové, než se další výprava vydá na cestu.“

**Podrobnosti:** „Přines dvanáct kusů kůže. Zpracovávám z nich řemeny a malé opravy brašen. Jiné materiály mi tentokrát nepomohou.“

**Přijetí:** „Děkuji. Připravím pracovní stůl. Až budeš mít celou zásobu kůže, vrať se za mnou.“

**Odmítnutí:** „Rozumím. Opravy odložím, ale výpravy budou muset zatím používat staré brašny.“

**Úkol je aktivní:** „Stále čekám na dvanáct kusů kůže. Potřebuji kompletní zásobu pro výrobu řemenů.“

**Ještě to nestačí:** „Ještě nemám všech dvanáct kusů kůže. Přines je v kompletním množství a dokončíme to.“

**Dokončení:** „Výborně. Teď můžu připravit nové řemeny a zkontrolovat brašny, které se vrátily z dolů.“

**Odchod:** „Měj se. Pokud se nějaká brašna začne trhat, raději ji před cestou zkontroluj.“

## 24. Kartografka Ela — Směr do podzemí
**ID:** `map_and_compass` · **Požadavek:** 1 kompas a 8 papírů

**Úvod:** „Pracuji na přesnější mapě okolí kopce. Staré čáry na mapách občas končí uprostřed kamene a já nechci jen hádat, co znamenají.“

**Podrobnosti:** „Potřebuji jeden kompas a osm papírů. Kompas použiji k ověření směru a papír na nové záznamy. Nejde mi o hledání pokladu, ale o přesnější mapu.“

**Přijetí:** „Dobře, děkuji. Připravím měřicí body. Jakmile budeš mít kompas a papír, přines je sem.“

**Odmítnutí:** „Samozřejmě. Mapování chvíli počká. Raději budu mít přesné záznamy později, než chybné hned.“

**Úkol je aktivní:** „Stále potřebuji jeden kompas a osm papírů. Potřebuji obě věci, abych mohla pokračovat.“

**Ještě to nestačí:** „Chybí kompas, papíry nebo jejich požadovaný počet. Vrať se, až bude celá sada kompletní.“

**Dokončení:** „Děkuji. Teď můžu začít měřit a porovnat směr s původními záznamy.“

**Odchod:** „Na shledanou. Pokud najdeš starou mapu, uschovej ji a nepřekresluj její značky.“

## 25. Horník Zdeněk — Hornické vzorky
**ID:** `miner_samples` · **Požadavek:** 12 uhlí a 8 surového železa

**Úvod:** „Připravuji přehled surovin z nových štol. Potřebuji vzorky z různých míst, abychom neporovnávali jen jednu žílu.“

**Podrobnosti:** „Přines dvanáct uhlí a osm kusů surového železa. Zaznamenám oba druhy zvlášť. Značky na stěnách nech tak, jak jsou, a nepokoušej se chodby rozšiřovat.“

**Přijetí:** „Díky. Zatím připravím štítky pro vzorky. Až budeš mít uhlí i surové železo v požadovaném počtu, vrať se.“

**Odmítnutí:** „Rozumím. Průzkum nebudeme uspěchávat. Bez řádných vzorků raději zatím nic neuzavřu.“

**Úkol je aktivní:** „Pořád čekám na dvanáct uhlí a osm kusů surového železa. Potřebuji oba druhy v uvedeném množství.“

**Ještě to nestačí:** „Zatím chybí část uhlí nebo surového železa. Přines kompletní sadu a můžeme záznam dokončit.“

**Dokončení:** „Výborně. Teď mohu oba druhy vzorků odděleně zaznamenat a porovnat je s poznámkami ze starších štol.“

**Odchod:** „Na shledanou. Pokud narazíš na neznámou chodbu, nejdřív si poznamenej polohu a nepouštěj se do ní bez přípravy.“

---

## Důležité poznámky k nasazení

- Texty a větve jsou kompletní podklady; pořád je potřeba je sestavit v editoru UplineDialogues a přiřadit k příslušným NPC.
- ID v příkazech musí přesně odpovídat ID v `config.yml`.
- Volba „Odmítnout“ nikdy nespouští `start` ani `turnin`. Neznamená zrušení už aktivního úkolu, protože žádný příkaz na zrušení zde není nabízen.
- Úspěšné odevzdání se ověřuje přes placeholder dokončeného questu. Pokud odevzdání neprojde, hráč dostane větev „Ještě to nestačí“ a požadované předměty se neodeberou, pokud není splněný celý seznam.
- Tyto dialogy neslibují odměny. Konkrétní odměny se mají přidat až po samostatné dohodě.
