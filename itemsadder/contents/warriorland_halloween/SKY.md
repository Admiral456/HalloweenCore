# Halloween sky: optional client shader

The mandatory ItemsAdder content intentionally does not override Minecraft's internal `sky.fsh`. That old approach was version-sensitive and placed a client rendering change inside the pack every player receives.

The optional, standalone Iris shader pack lives at:

`client-packs/HalloweenCore-Iris-Crimson-Sky/`

It is packaged by CI as the `HalloweenCore-Iris-Crimson-Sky` artifact. It includes the `gbuffers_skybasic` and `gbuffers_skytextured` programs and a 128x128 pack icon. The sky pass follows the client-side world clock, adds a restrained crimson dusk/dawn grade, and the textured pass tints the vanilla moon at night. Shader intensities are configurable from the shader settings.

Target and limitation: Minecraft Java Edition 1.21.10 with Iris. Static checks verify the pack layout, shader source markers and icon, but the rendered effect still needs client testing with the target graphics drivers. ViaVersion and ViaRewind do not deliver or convert the client's shader pack.
