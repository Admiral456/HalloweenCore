# Audit aktuální implementace HalloweenCore

- Obnovená větev znovu obsahuje integrovaný zdrojový kód SpookySeason včetně Trick-or-Treat, strašidelných nocí, krvavého a dýňového deště, duchů, jumpscare efektů, roje netopýrů, Headless Horseman bosse, halloweenských mobů, nájezdů, sezonních odměn, žebříčku sladkostí a menu.
- Obnovená větev obsahuje také nezávislou implementaci HorrorNights s náhodnými hororovými nocemi, upravenými moby, částicemi, zvuky a efekty.
- Hororové eventy nevyvolávají bouřku ani nepřepisují počasí hráče. Krvavý déšť je částicový efekt.
- Zůstaly zachované denní questy, ukládání fragmentů do `plugins/HalloweenCore/players.yml` a halloweenský obchod.
- Sezóna je nastavena na 23. října až 2. listopadu včetně podle `Europe/Prague`.
- Obchod obsahuje 13 zachovaných candy itemů. Oranžové varianty trojzubce, palcátu, netheritového meče a sekery jsou definované přes ItemsAdder modern graphics tint; generátor je `scripts/generate_orange_weapon_items.py`. Kopí je vynecháno, protože vanilla předmět přibyl až po cílové verzi 1.21.10.
- Tajné místnosti, klíče a jejich textura jsou stále odstraněné. Nefunkční generátor podzemních hrobů/struktur se nevrací.
- 13 licencovaných candy textur zachovává svoje zdroje, licenci a hash manifest.
- CI ověřuje zapojení eventů, konfiguraci, textury, obchod a oranžové itemy a sestavuje plugin. CI build není živý test na Hostify/Paper serveru; po nasazení je potřeba ověřit konzoli, příkazy, eventy a doručení resource packu.

- Fragmentová ekonomika používá jediný virtuální zůstatek pro questy, Jobs Reborn a obchod; obchod zobrazuje vlastní 64×64 pixel-art ikonu fragmentu přes ItemsAdder.

- Aktuální produkční cíl sestavení je Paper/Purpur 1.21.10 (Paper API 1.21.10-R0.1-SNAPSHOT, Java 21); CI tuto verzi kompiluje a validuje.

- Dialogový quest manažer, BetonQuest NPC balíček, dialogové návody a Citizens soft-dependency jsou odstraněné. Standardní 180členný pool denních/týdenních úkolů a fragmentový zůstatek zůstávají zachované.
