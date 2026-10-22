# ItemsAdder obsah pro HalloweenCore

Namespace `warriorland_halloween` obsahuje 13 zachovaných sladkostí a konfiguraci oranžových variant zbraní. Candy textury a jejich MIT licenční poznámky zůstávají zachované.

Oranžové zbraně (trojzubec, palcát, netheritový meč, netheritová sekera a netheritové kopí) používají vestavěný `graphics.color: "#ff7900"` na vanilkových texturách; nové PNG nejsou potřeba. Definice jsou v `configs/orange_weapons.yml` a generují se příkazem:

```bash
python3 scripts/generate_orange_weapon_items.py
```

Vyžaduje ItemsAdder 4.0.13+ a Minecraft 1.21.4+ na serveru i klientovi. Obsah zkopíruj do `plugins/ItemsAdder/contents/warriorland_halloween/`, restartuj server a spusť `/iazip`. Hráči musí přijmout resource pack.

Tajné místnosti, klíče a jejich textura nejsou součástí obsahu.
