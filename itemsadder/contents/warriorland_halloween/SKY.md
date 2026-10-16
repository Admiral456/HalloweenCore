# Halloween sky — Minecraft Java 1.21.10

The Halloween content pack now includes Koto's high-resolution red-sky artwork as a Java custom-sky atlas. The source was supplied as six 512x512 cubemap faces. The build checks their individual SHA-256 values and assembles them without resizing into:

- Atlas: `resourcepack/assets/minecraft/optifine/sky/world0/sky1.png` (1536x1024, 3x2 faces).
- Loader settings: `resourcepack/assets/minecraft/optifine/sky/world0/sky1.properties`.

The source package labels the six files as PNGs, but their binary signature is BMP; the converter detects and decodes the actual image format, then writes a valid PNG atlas. The six directions are placed in OptiFine's required order: **down / up / north** on the upper row, and **west / south / east** on the lower row. This corrects an earlier face-order mistake that produced jagged black geometry in-game. Each original 512x512 face is kept pixel-for-pixel; only its position in the atlas changes.

## Client compatibility

The Java custom-sky format at `assets/minecraft/optifine/sky/world0` is recognized by OptiFine and compatible custom-sky loaders (for example, certain Skyboxify setups). Plain vanilla clients do not read OptiFine sky properties. They keep the existing Halloween-tinted vanilla sky shader as a fallback. Shader packs or client modifications that replace Minecraft's sky renderer may override either appearance.

The skybox properties explicitly include clear, rain and thunder weather, so the Koto sky should not disappear just because it starts raining. This is a permanent custom-sky visual for compatible clients; it is not automatically toggled by server event state. A server-side plugin cannot force a client to load a custom-sky mod. The command `/halloween shader on|off` now toggles both the Koto OptiFine skybox layer and the vanilla `sky.fsh` fallback tint. When switched off, the plugin saves `sky1.properties` outside the ItemsAdder content folder so `on` can restore it. `/halloween shader status` reports both states. A live Minecraft client/resource-pack test is still required before calling the rendered result verified.

## Attribution and license

The source is **Blood Moon Horizon — red sky by koto (1.0)**, uploaded by abdouahabch7. The project page lists the asset as MIT licensed:

- https://modrinth.com/resourcepack/blood-moon-horizon/version/2CcGu1oW
- https://www.curseforge.com/minecraft/texture-packs/blood-moon-horizon/files/5489312

See `THIRD_PARTY_LICENSES/koto-sky-NOTICE.txt` for the source details, checksums, and license notice.
