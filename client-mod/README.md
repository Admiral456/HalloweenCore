# HalloweenCore Client Companion — Minecraft Java 1.21.10

This is a lightweight, optional Fabric client mod for the Iris Halloween shader. It does not add gameplay rules, entities, commands, or server-side dependencies.

## Required client setup

- Minecraft Java Edition 1.21.10
- Fabric Loader 0.17.3 or compatible
- Fabric API for 1.21.10
- Iris + Sodium versions that support Minecraft 1.21.10
- HalloweenCore-Client-1.21.10.jar in the client's mods/ folder
- WarriorLand-Halloween-Iris-Shader.zip in .minecraft/shaderpacks/, enabled in Video Settings

The server must run the matching HalloweenCore build. The client mod and server communicate through the namespaced play plugin-message payload halloweencore:visual_state.

## Protocol

Payload: exactly one byte.

- 1 — the server's actual blood-moon-invasion event is active.
- 0 — the event has ended, was cancelled/reloaded, or is inactive.

The mod temporarily overrides World.getTimeOfDay() only for the client world renderer, exposing a reserved synthetic day marker to Iris standard uniforms worldDay and worldTime. It never changes the server world clock, the user's visible server time command, mob AI or gameplay time. On disconnect the client marker is cleared.

## Known limitations

- Clients without this mod cannot receive the server's custom event state in a conventional Iris shader, so they get the normal Halloween mood but not event-timed crimson sky.
- The shader pack uses standard sky/cloud programs and is not a full path-tracing shader. The result must still be tested on the exact Iris/Sodium/GPU combination.
- A compiled JAR does not prove the real render looks correct. Test with /halloween event start blood-moon-invasion and /halloween event stop on a staging server; verify the transition and restoration.
