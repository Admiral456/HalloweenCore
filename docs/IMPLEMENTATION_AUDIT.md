# Audit aktuální implementace HalloweenCore

- Obnovená větev znovu obsahuje integrovaný zdrojový kód SpookySeason včetně Trick-or-Treat, strašidelných nocí, krvavého a dýňového deště, duchů, jumpscare efektů, roje netopýrů, Headless Horseman bosse, halloweenských mobů, nájezdů, sezonních odměn, žebříčku sladkostí a menu.
- Obnovená větev obsahuje také nezávislou implementaci HorrorNights s náhodnými hororovými nocemi, upravenými moby, částicemi, zvuky a efekty.
- Hororové eventy nevyvolávají bouřku ani nepřepisují počasí hráče. Krvavý déšť je částicový efekt.
- Zůstaly zachované denní questy, ukládání fragmentů do `plugins/HalloweenCore/players.yml` a halloweenský obchod.
- Sezóna je nastavena na 23. října až 2. listopadu včetně podle `Europe/Prague`.
- Obchod obsahuje 13 zachovaných candy itemů. Oranžové varianty trojzubce, palcátu, netheritového meče, sekery a kopí jsou definované přes ItemsAdder modern graphics tint; generátor je `scripts/generate_orange_weapon_items.py`.
- Tajné místnosti, klíče a jejich textura jsou stále odstraněné. Nefunkční generátor podzemních hrobů/struktur se nevrací.
- 13 licencovaných candy textur zachovává svoje zdroje, licenci a hash manifest.
- CI ověřuje zapojení eventů, konfiguraci, textury, obchod a oranžové itemy a sestavuje plugin. CI build není živý test na Hostify/Paper serveru; po nasazení je potřeba ověřit konzoli, příkazy, eventy a doručení resource packu.
