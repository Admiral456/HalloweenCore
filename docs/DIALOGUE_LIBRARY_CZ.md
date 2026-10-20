# Knihovna dialogů NPC — UplineDialogues

Tento dokument je ruční scénář pro editor UplineDialogues. **Není to importovatelný soubor** a HalloweenCore ho sám nenačte. Vytvoř dialog u vesničana v editoru, přidej uzly a přiřaď dialog konkrétnímu NPC.

## Co HalloweenCore skutečně umí

- Ukládá stav questu pro každého hráče: přijatý, čítač postupu, dokončený.
- Příkazy: `/halloween quest start <id>`, `/halloween quest progress <id> [množství]`, `/halloween quest complete <id>`, `/halloween quest status <id>`.
- Placeholders (vyžadují PlaceholderAPI): `%halloween_fragments%`, `%halloween_quest_progress_<id>%`, `%halloween_quest_started_<id>%`, `%halloween_quest_completed_<id>%`.
- Odměna se přidá při prvním úspěšném `complete`; opakované dokončení stejného ID další odměnu nepřidá.
- Questové ID smí obsahovat jen malá písmena, číslice, pomlčku a podtržítko.

**Důležité omezení:** HalloweenCore samo nekontroluje, zda hráč skutečně přinesl konkrétní předměty, navštívil místo nebo zabil moba. UplineDialogues musí před voláním `complete` vyhodnotit vhodné podmínky, případně musí být později doplněna skutečná kontrola cílů. Příkaz `progress` pouze přičte číslo; automaticky ho nevyvolává těžba, zabití ani průzkum. Nedefinuj proto dialog, který tvrdí, že plugin tyto cíle ověřuje, pokud nejsou zvlášť nakonfigurované.

## Základní strom dialogu

Použij tyto uzly jako textový a logický podklad. Názvy uzlů jsou doporučené názvy v editoru, nikoli pevná syntaxe UplineDialogues.

### 1. Začátek — „Potřebuješ něco?“

Text NPC:
> Psst… mám pro tebe práci, ale není pro každého. Chceš slyšet, co potřebuji?

Volby:
- **„Řekni mi víc.“** → uzel Podrobnosti.
- **„Teď ne.“** → uzel Konec.

### 2. Podrobnosti

Text NPC:
> Potřebuji pomoc s jedním úkolem. Než ho přijmeš, dobře si přečti zadání.

Volby:
- **„Přijímám úkol.“** → uzel Přijetí.
- **„Ještě si to rozmyslím.“** → uzel Konec.

### 3. Přijetí

Pro tento konkrétní dialog zvol unikátní ID, například `vesnican_zelezo_01`. Akci nastav tak, aby příkaz spustil jako OP nebo jiným způsobem s oprávněním `halloweencore.quest.dialogue`:

```text
/halloween quest start vesnican_zelezo_01
```

Text NPC:
> Dobrá. Úkol jsem ti svěřil. Vrať se, až budeš připravený.

### 4. Návrat / kontrola stavu

Vytvoř podmíněné větve podle placeholderů:
- `%halloween_quest_completed_vesnican_zelezo_01%` je `true` → Dokončeno.
- `%halloween_quest_started_vesnican_zelezo_01%` je `true` a dokončeno je `false` → Úkol stále probíhá.
- Jinak → Přijetí / nabídka.

Příklad textu pro probíhající úkol:
> Ještě jsi mi úkol neodevzdal. Až budeš mít vše připravené, vrať se.

### 5. Dokončení

**Použij jen tehdy, když podmínky v UplineDialogues skutečně ověřily splnění cíle.** Příkaz:

```text
/halloween quest complete vesnican_zelezo_01
```

Text NPC:
> Výborně, práci jsi splnil. Tady máš svou odměnu.

Pokud UplineDialogues nedokáže ověřit skutečný cíl, nedávej hráči volbu „Splnil jsem to“ bez dalšího ověření — jinak by mohl získat odměnu bez splnění úkolu.

### 6. Dokončeno

Podmínka:
`%halloween_quest_completed_vesnican_zelezo_01%` je `true`.

Text NPC:
> S tímhle úkolem už jsi mi pomohl. Díky — další odměnu za stejný úkol nedostaneš.

### 7. Konec

Text NPC:
> Dobře. Zastav se, až budeš chtít pomoct.

## Fragmenty

Placeholder `%halloween_fragments%` zobrazí aktuální počet fragmentů. Například:
> Máš u sebe %halloween_fragments% fragmentů. Neplýtvej jimi.

## Odměny

Výchozí hodnoty jsou v `config.yml`:
- Lehká obtížnost: 3–5 fragmentů; common klíč 10 %, rare 1 %, legendary 0,1 %.
- Střední: 6–9 fragmentů; common 15 %, rare 3,5 %, legendary 0,5 %.
- Těžká: 10–15 fragmentů; common 20 %, rare 6 %, legendary 2 %.
- Velmi těžká: 16–25 fragmentů; common 25 %, rare 10 %, legendary 8 %.

Nové ID má výchozí lehkou obtížnost. Pro změnu obtížnosti konkrétního ID nastav `quest-rewards.quests.<id>.difficulty` v konfiguraci. Čtyři hodnoty šancí jsou samostatné šance; celková šance na jakýkoliv klíč je jejich součet.

## Klíče a tajné místnosti

Všechny tři rarity používají stejnou existující texturu klíče. UplineDialogues akce „Give item“ pro `warriorland_halloween:secret_key` vytvoří obyčejný klíč; vyšší rarity se vydávají z questové odměny nebo příkazem správce.

## Kontrolní seznam před nasazením

- [ ] Uložit dialog v editoru UplineDialogues.
- [ ] Přiřadit ho správnému NPC.
- [ ] Nastavit příkazy přijetí/dokončení s oprávněním, které je povoluje.
- [ ] Zkontrolovat placeholdery a přesnou syntaxi podmínek v nainstalované verzi UplineDialogues.
- [ ] Otestovat s běžným hráčským účtem, že bez splnění cíle nejde dojít k uzlu dokončení.
- [ ] Otestovat odměnu a opakovaný návrat po dokončení.
