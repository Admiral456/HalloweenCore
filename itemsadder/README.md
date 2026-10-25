# ItemsAdder obsah pro HalloweenCore

Namespace `warriorland_halloween` obsahuje 13 zachovaných sladkostí a konfiguraci oranžových variant zbraní. Candy textury a jejich MIT licenční poznámky zůstávají zachované.

Oranžové zbraně (trojzubec, palcát, netheritový meč, netheritová sekera) používají vestavěný `graphics.color: "#ff7900"` na vanilkových texturách; nové PNG nejsou potřeba. Definice jsou v `configs/orange_weapons.yml` a generují se příkazem:

```bash
python3 scripts/generate_orange_weapon_items.py
```

Obarvení vyžaduje ItemsAdder 4.0.13+ a Minecraft 1.21.4+ na serveru i klientovi. Na cílové verzi Minecraft 1.21.10 jsou k dispozici čtyři varianty; kopí není zahrnuto, protože přibylo až v Java Edition 1.21.11+. Obsah zkopíruj do `plugins/ItemsAdder/contents/warriorland_halloween/`, restartuj server a spusť `/iazip`. Hráči musí přijmout resource pack.

Tajné místnosti, klíče a jejich textura nejsou součástí obsahu.

## Vlastní ikona fragmentu

Soubor `resourcepack/assets/warriorland_halloween/textures/item/halloween_fragment.png` obsahuje vlastní 64×64 pixel-art ikonu měny. Definice `halloween_fragment` je v `configs/items.yml`. Slouží jako ikona zůstatku v GUI obchodu; ekonomika je virtuální, uložená v `plugins/HalloweenCore/players.yml`.
