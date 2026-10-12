# HalloweenCore Iris Shader Pack

Standalone OptiFine-style shader pack intended for Minecraft Java 1.21.10 + Iris + Sodium. It does not override Minecraft's assets/minecraft/shaders/core files because Iris documents that resource packs which override core shaders are unsupported.

## What it changes

- Dark violet night sky with a muted burnt-orange horizon and an autumnal daytime palette.
- Warm-tinted moon, a restrained directional halo, and dusk-toned clouds.
- A separate crimson Blood Moon sky, moon tint, and cloud atmosphere.
- Low, Medium, and High quality profiles.

## Real Blood Moon signaling

The Paper plugin cannot directly set arbitrary shader uniforms. HalloweenCore sends halloweencore:visual_state (one byte: 1 active, 0 inactive) to the optional client companion in client-mod/. That client mod makes a client-only world-time marker visible to Iris shader uniforms while the real blood-moon-invasion event is active, with a two-second transition in and out. It does not alter the server world clock.

Without the client companion the shader pack still provides the normal Halloween atmosphere, but it will not enter Blood Moon mode just because it happens to be nighttime. This avoids false red moons when no event is running.

## Install

1. Use Minecraft Java 1.21.10 with Fabric Loader, Fabric API, Iris, and Sodium.
2. Put halloweencore-client-1.21.10.jar in the client's mods folder.
3. Put WarriorLand-Halloween-Iris-Shader.zip in .minecraft/shaderpacks/.
4. In Video Settings > Shader Packs, activate WarriorLand-Halloween-Iris-Shader.
5. Install the server plugin and generated ItemsAdder content separately; the client companion is needed only for event-aware shader state.

CI validates shader-pack structure and compiles the Java components. It cannot replace a real GPU/client shader compilation and appearance check; those still need to be performed in Minecraft before production deployment.
