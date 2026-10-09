# Halloween sky — Minecraft Java 1.21.10

Shader path:
`resourcepack/assets/minecraft/shaders/core/sky.fsh`

This is the vanilla core-shader entry point for the sky in Minecraft Java 1.21.10. The shader keeps the version-matched fog/dynamic-transform imports and the vanilla time-of-day color as its base, then applies a stronger orange/crimson tint. It does not change the world time, sun/moon schedule, or server-side gameplay.

This is a client-side resource-pack change. The shader has been checked against the 1.21.10 vanilla shader interface and the repository validator, but an actual Minecraft client/resource-pack test is still required before calling the visual result verified. Shader-replacing client mods may override this file.