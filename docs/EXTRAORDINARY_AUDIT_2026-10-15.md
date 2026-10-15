# Mimořádný audit HalloweenCore — 15. 10. 2026

## Shrnutí

**Výsledek statické kontroly a CI: PASS na commitu `fff550232572fd431e87753c1f42e2b2953e455c`.**
**Výsledek testování na živém Minecraft serveru: NEPROVEDENO v rámci tohoto auditu.**

Projekt byl zkontrolován napříč Java logikou, konfigurací, registry předmětů, ItemsAdder assety, správou odměn, individuálním opt-outem, spouštěním encounterů, správou chunk rezervací, vypínáním subsystémů, oprávněními a build/pipeline. Audit vedl k několika konkrétním opravám; nejde pouze o kontrolu dokumentace.

- GitHub Actions: [run 1983 — úspěšný build](https://github.com/Admiral456/HalloweenCore/actions/runs/38799384427)
- PR: [#56 — pracovní draft, nesloučený](https://github.com/Admiral456/HalloweenCore/pull/56)
- Ověřený cílový profil: Minecraft 1.21.10, Java 21, Maven
- Regresní sada: **23 testů, OK**
- Registry: **34 jedinečných ID**, žádné nepoužité/nezaregistrované PNG ikon
- Modely: **6 zaregistrovaných párů Blockbench/PNG** (pět speciálních mobů a boss Král upírů); registrace a formát souborů prošly validačními kontrolami
- ItemsAdder nábytek: **3 strukturálně validní modely**
- Světové blokové textury: **12**
- OGG zvukové assety: **12**
- Licencované assety: **15 cílových souborů z 14 originálních zdrojových assetů**, SHA-256 ověření prošlo
- Build vytvořil plugin JAR, ItemsAdder ZIP, ModelEngine/mob ZIP a SHA-256 manifest

## Opravy zavedené během auditu

### Vysoká priorita — persistence a zneužitelnost jednorázových odměn

1. **Denní návratová odměna:** timestamp posledního nároku a streak se zapisují před přidáním fragmentů; změna se uloží bez čekání na pětiminutový autosave. To chrání před opětovným vyzvednutím po restartu v okamžiku, kdy by přidání fragmentů samo vyvolalo ukládání kvůli globálnímu milníku.
2. **Denní výzva:** příznak vyzvednutí se nastaví před připsáním fragmentů a okamžitě se uloží.
3. **Tajné objevy:** stav objevení se nyní ukládá po připsání odměny; původní stavový příznak mohl bez okamžitého save po havárii zmizet.
4. **Hřbitovní encounter:** odměny účastníků se po rozdělení okamžitě uloží.
5. **Denní časové pásmo:** denní výzva používá `season.timezone` (výchozí Europe/Prague) místo implicitního časového pásma JVM; datum se vyhodnotí konzistentně jednou a chyba konfigurace nevypisuje varování každým voláním.

### Vysoká priorita — generování světa a životní cyklus

6. **Osiřelé rezervace chunků:** přirozené generování si nyní eviduje čekající úlohy v paměti, uvolňuje rezervace v `finally`, obnovuje stale PDC rezervace po startu a ruší čekající úlohy i značky při vypnutí pluginu. Tím se řeší riziko, že po restartu stará značka `reserved` blokuje dekorátor.
7. **Vypnutí Halloween:** při vypnutí globálního Halloween přepínače se nyní zastaví fronta dekorací světa; dříve mohl naplánovaný task zůstat aktivní a opakovaně kontrolovat vypnutý systém.
8. **Headless Nick test spawn:** administrátorský spawn nyní odmítá vypnutý event, vypnutou konfiguraci Nicka, nezpůsobilého hráče nebo zakázaný svět, namísto zdánlivého úspěchu a okamžitého odstranění encounteru.

### Střední priorita — opt-out, výbava, registry

9. **Individuální `/halloween disable`:** opted-out hráč nedostane falešnou zprávu o denní odměně, nevyčerpá si nárok a nepokračuje mu vlastní Halloween ambient/title po připojení. Přepnutí enable/disable okamžitě obnoví nebo zastaví individuální přehrávání.
10. **Štít Krvavého strážce:** doplněna původní generovaná ikona, ItemsAdder definice, stabilní interní ID, shop záznam a jednorázová migrace pro starší instalace. Cena v novém výchozím záznamu je 28 000 fragmentů, minimální prokletí 4.
11. **Talisman:** popis i fallback/migrační hodnoty sjednoceny na **+2 body zdraví = jedno srdce navíc**. Opraven starý shop migration kód, který mohl přepisovat bonus zpět na 20 bodů, a nouzový fallback správce efektů. Text již neslibuje +20 HP / celý další řádek srdcí.
12. **Administrátorský test kit:** sladkosti z obchodu se již do `/halloween give <hráč> all` nepřidávají dvakrát; mimo obchod se přidává pouze licencovaná dekorace listí s jablky.
13. **Oprávnění:** kód nyní respektuje specializované uzly `halloweencore.event.manage`, `halloweencore.reload`, `halloweencore.debug` a `halloweencore.items.give`, které byly dříve deklarovány, ale příkazy stále vyžadovaly pouze `halloweencore.admin`.
14. **Chráněné assety:** pomocný skript, který by mohl zapisovat/přebarvovat atlas tří externě chráněných mobů, je převeden na fail-closed guard. Už nemůže omylem vytvářet soubory pod chráněnými ID.
15. **Štítek stavu modelů:** README/ModelEngine dokumentace byla opravena, aby netvrdila, že všech osm nepřátel má hotové vlastní modely. V současném repozitáři je pět custom modelů mobů; Void Reaper, Frost Stalker a Nightmare zůstávají na vanilla fallbacku.

## Co přesně prošlo automatickými kontrolami

V běhu [GitHub Actions 38799384427](https://github.com/Admiral456/HalloweenCore/actions/runs/38799384427) byly úspěšné tyto kroky:

- generování audio, blokových a item textur;
- validace kanonického registru a licenční provenance;
- validace všech cest modelů, dekorací, zbroje a zvuků;
- validace assetů a modelu Krále upírů;
- validace modelů speciálních mobů a pravidel runtime;
- deterministické vytvoření a kontrola asset manifestu;
- testy registru, odměnové persistence, oprávnění, opt-outu, testovacího spawnu, ochrany assetů, modelových/texturních cest a deterministického balení;
- `mvn -B clean package`;
- zabalení a nahrání všech čtyř artefaktů.

Testovací skript hlásil **23 testů, OK**. Výsledek validačních skriptů navíc potvrzuje 34 jedinečných kanonických ID, synchronizaci ItemsAdder ID, žádné přebytečné item ikony, 6 modelových párů, 3 modely nábytku, 12 blokových textur, 12 OGG souborů a úspěšné SHA-256 kontroly licencovaných PNG.

## Co stále není možné prohlásit za ověřené

Tento audit **není** živý staging test. Následující věci zůstávají otevřené až do ověření v klientovi/serveru:

- [ ] Nainstalovat JAR a přesně odpovídající ItemsAdder ZIP z téhož CI běhu na kopii serveru Paper/Purpur 1.21.10.
- [ ] Spustit `/iazip`, ověřit `/iainfo`, URL hostingu, doručení balíčku a skutečné přijetí na čistém klientovi.
- [ ] Prohlédnout zbroj Krvavého strážce při nošení ze všech stran; PNG ikona v inventáři sama nepotvrzuje správné armor layer vykreslení.
- [ ] Ověřit štít při držení a blokování. Nová ikona je zaregistrovaná, ale vlastní 3D tvar/animace štítu zatím nebyly potvrzeny ve hře.
- [ ] Ověřit Batwing Elytra při plachtění a vlastní vzhled Nocturne Sovereign Cowl. Ikony jsou generované, ale unikátní modelový vzhled ve hře zůstává neověřený.
- [ ] Importovat pět custom mob blueprintů přes ModelEngine, ověřit jejich UV, idle/walk/attack animace, resource pack a skutečné vykreslení.
- [ ] Dodělat schválené modely/textury pro Void Reaper, Frost Stalker a Nightmare. Jejich assety jsou záměrně chráněné a automaticky se nevyrábějí.
- [ ] Vytvořit a klientsky otestovat model Goblin Kinga a Headless Nicka s koněm; jejich hotové vlastní modelové páry nebyly potvrzeny.
- [ ] Ověřit vizuální shader oblohy, Halloween playlist, tiché vanilla music overrides, hlasitost, jeskynní profil a chování při odchodu/připojení.
- [ ] Na kopii světa ručně otestovat `/halloween spawn hřbitov`, `vesnice`, `vampirearena`, `goblinarena` v režimu FORCE, kontrolu hranic, rollback po selhání a ochrany WorldGuard/claim pluginů.
- [ ] Dlouhodobě sledovat TPS, frontu chunk dekorací, zatížení při prvním průchodu existujícími chunky, restart během generování a shutdown s běžícím encounterem.
- [ ] Ověřit integrace BattlePass, PlaceholderAPI, WorldGuard a serverový resource-pack hosting na konkrétních instalovaných verzích.

**Bezpečnost nasazení:** ruční stavby používají FORCE uvnitř konečného footprintu. Před testem na reálném světě je nutná záloha. Úspěšný Maven/CI build není autorizací k nasazení do produkce.

## Stav vydání

- Auditní větev: `feature/softici-plugin-audit-and-texture-roadmap`
- Commit auditu/CI v době poslední úspěšné kontroly: `fff550232572fd431e87753c1f42e2b2953e455c`
- Draft PR #56 zůstává otevřený a **nesloučený**.
- Ověřený profil: Minecraft 1.21.10. Profily 1.21.1 a 1.21.2 jsou v registru stále označeny jako pending.
- Produkční nasazení ani živý klientský rendering nebyly touto automatizovanou kontrolou potvrzeny.
