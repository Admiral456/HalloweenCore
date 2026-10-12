# HalloweenCore Iris Shader Pack

Standalone OptiFine-style shader pack intended for Minecraft Java 1.21.10 + Iris + Sodium. Its `shaders/shaders.properties` and program files are placed according to the Iris shader-pack format. It does not override Minecraft's `assets/minecraft/shaders/core` files because Iris does not support resource-pack core-shader overrides.

## What it changes

- Charcoal-blue night sky, a muted earth-toned autumn horizon, and desaturated daytime light.
- Neutral ash-silver moon phases, a restrained cool halo, and greyed autumn clouds; no permanent oversized orange/pink moon.
- A separate deep-crimson sky and moon tint only while the actual Blood Moon event is active and the client companion is installed.
- Low, Medium, and High quality profiles.

## Real Blood Moon signaling

The Paper plugin cannot directly set arbitrary shader uniforms. HalloweenCore sends halloweencore:visual_state (one byte: 1 active, 0 inactive) to the optional client companion in client-mod/. That client mod makes a client-only world-time marker visible to Iris shader uniforms only during the real blood-moon-invasion event. Entry blends the actual time-of-day into night, then fades the sky into crimson; exit fades crimson first and then blends the visual clock back to the live world time. The server clock is never modified.

Without the client companion the shader pack still provides the normal Halloween atmosphere, but it will not enter Blood Moon mode just because it happens to be nighttime. This avoids false red moons when no event is running.

## Install

1. Use Minecraft Java 1.21.10 with Fabric Loader, Fabric API, Iris, and Sodium.
2. Put halloweencore-client-1.21.10.jar in the client's mods folder.
3. Put WarriorLand-Halloween-Iris-Shader.zip in .minecraft/shaderpacks/.
4. In Video Settings > Shader Packs, activate WarriorLand-Halloween-Iris-Shader.
5. Install the server plugin and generated ItemsAdder content separately; the client companion is needed only for event-aware shader state.

CI validates shader-pack structure and compiles the Java components. It cannot replace a real GPU/client shader compilation and appearance check; those still need to be performed in Minecraft before production deployment.
