# Halloween audio assets and licences

## Background ambience

- File: `itemsadder/contents/warriorland_halloween/sounds/haunted_theme.ogg`
- Source: **Spooky Fester** by Eldritch Grim, published on OpenGameArt: https://opengameart.org/content/spooky-fester
- Licence: **CC0 1.0** (public-domain dedication; attribution is not required, but the source is documented here).
- The CI generator downloads the source and converts it to mono OGG/Vorbis for the ItemsAdder pack. The exact encoded duration is written to `atmosphere.loop-milliseconds` so playback restarts without overlap.

## Original Halloween cues

The following sounds are generated locally by `scripts/generate_halloween_audio.py` using procedural tones, envelopes, detuned partials and synthesized noise; they do not contain third-party recordings or sampled game audio.

- `event_sting.ogg` — generic five-second Halloween cue.
- `soulstorm_sting.ogg` — dissonant soul chimes and hollow low pulse.
- `witching_sting.ogg` — detuned notes and rising accent.
- `harvest_sting.ogg` — warmer bell-like cadence over a dark drone.
- `blood_moon_rise.ogg` — heavier impact and ominous tail.
- `pumpkin_apocalypse.ogg` — cracked-bell chord.
- `graveyard_rising.ogg` — cold, sparse falling bells.
- `halloween_silence.ogg` — generated near-silent OGG referenced by vanilla background-music overrides.

All files are mono OGG/Vorbis. CI inspects the audio stream codec, sample rate, channel count, duration, ItemsAdder registrations and resource-pack music override table.

## Resource pack installation

Copy `warriorland_halloween` to `plugins/ItemsAdder/contents/`, run `/iazip`, and verify `/iainfo` reports a reachable resource-pack URL. Clients must accept and download the rebuilt pack for the sounds to work. The plugin plays `haunted_theme` in the AMBIENT category and periodically stops Minecraft's MUSIC category; the pack separately replaces 31 configured background music event IDs with the silent asset.

A successful CI build verifies asset structure, not delivery to a live Hostify server or client-side playback.


## Night and cave playlist (CC0)

These downloadable tracks are included in the ItemsAdder resource pack by the CI audio generator. Each OpenGameArt page marks the asset CC0; no attribution is legally required, but the source links are retained here.

- `horror_atmosphere.ogg` — **Horror Atmosphere**, by SubspaceAudio; the attached OGG is named “Juhani Junkala - Post Apocalyptic Wastelands [Loop Ready]”. CC0. Source: https://opengameart.org/content/horror-atmosphere
- `creepy_ambient.ogg` — **Creepy Ambient Loop**, by epb9000. CC0. Source: https://opengameart.org/content/creepy-ambient-loop
- `dark_cavern_ambient.ogg` — **Dark Cavern Ambient**, by Paul Wortmann; version 002 is the continuous loop. CC0. Source: https://opengameart.org/content/dark-cavern-ambient

The playlist rotates Spooky Fester, Horror Atmosphere, and Creepy Ambient during Overworld night. Dark Cavern Ambient takes priority in underground spaces detected by depth and sky-light checks. Each loop period is derived from the actual encoded audio duration during CI, so tracks do not get cut off or overlap at the transition.
