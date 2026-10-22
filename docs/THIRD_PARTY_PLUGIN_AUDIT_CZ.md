# Audit třetích stran

- Aktivní Java plugin obsahuje vlastní implementaci denních questů, ukládání fragmentů a inventářového obchodu. Kód ani gameplay funkce z projektů SpookySeason nebo HorrorNights nejsou součástí výsledného JARu; staré eventové zdrojové soubory byly z této větve odstraněny.
- Zachovaných 13 ikon sladkostí pochází z assetové sady Stev's Halloween. Jejich licence, atribuce a SHA-256 manifest zůstávají v repozitáři. Textury jsou validovány v CI a jejich obrazová data nebyla pozměněna.
- Aktivní runtime závislostí je Paper API (poskytuje ji server). ItemsAdder je volitelný: při jeho dostupnosti plugin používá vlastní candy itemy; jinak použije náhrady Minecraftu.
- Resource pack/model pro obarvení skutečných vanilla ikon zbraní není součástí této změny. Oranžovo-zlaté názvy a enchant glow se nastavují v pluginu.
