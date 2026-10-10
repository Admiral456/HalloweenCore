#!/usr/bin/env python3
"""Build Halloween audio for the ItemsAdder pack from a CC0 source track.

The ambient track is fetched from OpenGameArt, converted with ffmpeg, and its
actual encoded duration is written to the plugin default config. The event cue
is synthesized locally. Requires internet access, Python standard library, and ffmpeg.
"""
from __future__ import annotations

import array
import json
import math
import random
import re
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOUNDS_DIR = ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "sounds"
SAMPLE_RATE = 22050
TWO_PI = 2.0 * math.pi


def smoothstep(x: float) -> float:
    x = min(1.0, max(0.0, x))
    return x * x * (3.0 - 2.0 * x)


def quantize_frequency(frequency: float, duration: float) -> float:
    """Snap oscillators to integer cycles per loop to avoid boundary clicks."""
    return max(1.0 / duration, round(frequency * duration) / duration)


def s(freq: float, t: float, duration: float, phase: float = 0.0) -> float:
    return math.sin(TWO_PI * quantize_frequency(freq, duration) * t + phase)


def write_ogg(name: str, samples: array.array) -> None:
    SOUNDS_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="halloween-audio-") as tmp:
        wav_path = Path(tmp) / f"{name}.wav"
        ogg_path = SOUNDS_DIR / f"{name}.ogg"
        with wave.open(str(wav_path), "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(SAMPLE_RATE)
            wav.writeframes(samples.tobytes())
        subprocess.run([
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(wav_path), "-ac", "1", "-c:a", "libvorbis", "-q:a", "4",
            str(ogg_path),
        ], check=True)
    print(f"Generated {ogg_path.relative_to(ROOT)} ({ogg_path.stat().st_size:,} bytes)")


def normalize(samples: list[float]) -> array.array:
    peak = max(1e-9, max(abs(v) for v in samples))
    scale = 0.82 / peak
    pcm = array.array("h", (int(max(-1.0, min(1.0, math.tanh(v * scale))) * 32767) for v in samples))
    if pcm.itemsize != 2:
        raise RuntimeError("Expected 16-bit PCM samples")
    return pcm


def probe_audio_duration(path: Path) -> float:
    """Return the duration of the first decoded audio stream in seconds."""
    result = subprocess.run(
        [
            "ffprobe", "-v", "error", "-select_streams", "a:0",
            "-show_entries", "stream=duration", "-of", "json", str(path),
        ],
        check=True, capture_output=True, text=True,
    )
    streams = json.loads(result.stdout).get("streams", [])
    if not streams or not streams[0].get("duration"):
        raise RuntimeError(f"Could not determine audio duration for {path.name}")
    return float(streams[0]["duration"])


def update_track_duration(track_key: str, duration_ms: int, update_legacy: bool = False) -> None:
    config_path = ROOT / "src" / "main" / "resources" / "config.yml"
    config_text = config_path.read_text(encoding="utf-8")
    if update_legacy:
        config_text, count = re.subn(
            r"(?m)^  loop-milliseconds:\\s*\\d+\\s*$",
            f"  loop-milliseconds: {duration_ms}",
            config_text,
        )
        if count != 1:
            raise RuntimeError("Expected exactly one atmosphere.loop-milliseconds setting")
    pattern = rf"(?m)^    {re.escape(track_key)}:\\s*\\d+\\s*$"
    config_text, count = re.subn(pattern, f"    {track_key}: {duration_ms}", config_text)
    if count != 1:
        raise RuntimeError(f"Expected exactly one atmosphere.track-loop-milliseconds.{track_key} setting")
    config_path.write_text(config_text, encoding="utf-8")


def download_cc0_ogg_track(track_key: str, source_url: str) -> int:
    """Download a CC0 OGG asset and normalize it for Minecraft/ItemsAdder."""
    SOUNDS_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="halloween-audio-") as tmp:
        source_path = Path(tmp) / f"{track_key}-source.ogg"
        output_path = SOUNDS_DIR / f"{track_key}.ogg"
        request = urllib.request.Request(
            source_url,
            headers={"User-Agent": "HalloweenCore-build/1.0 (CC0 audio asset)"},
        )
        for attempt in range(3):
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    data = response.read()
                if len(data) < 20_000:
                    raise RuntimeError(f"OpenGameArt download looks incomplete ({len(data)} bytes)")
                if data[:4] != b"OggS":
                    raise RuntimeError("OpenGameArt response is not an OGG container")
                source_path.write_bytes(data)
                break
            except (OSError, urllib.error.URLError, TimeoutError, RuntimeError) as exc:
                if attempt == 2:
                    raise RuntimeError(f"Could not retrieve CC0 audio '{track_key}' from {source_url}: {exc}") from exc
                time.sleep(2 ** attempt)
        subprocess.run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-i", str(source_path), "-vn", "-map_metadata", "-1",
                "-ac", "1", "-ar", str(SAMPLE_RATE),
                "-c:a", "libvorbis", "-q:a", "4", str(output_path),
            ],
            check=True,
        )
        if not output_path.is_file() or output_path.read_bytes()[:4] != b"OggS":
            raise RuntimeError(f"ffmpeg did not create a valid OGG for {track_key}")
        duration_ms = round(probe_audio_duration(output_path) * 1000)
        update_track_duration(track_key, duration_ms)
    print(f"Encoded CC0 {track_key}: {duration_ms} ms, {output_path.stat().st_size:,} bytes")
    return duration_ms


def generate_playlist_assets() -> None:
    # All three tracks are CC0 and may be redistributed inside this resource pack.
    sources = {
        "horror_atmosphere": "https://opengameart.org/sites/default/files/Juhani%20Junkala%20-%20Post%20Apocalyptic%20Wastelands%20%5BLoop%20Ready%5D.ogg",
        "creepy_ambient": "https://opengameart.org/sites/default/files/creepyloop-v2_0.ogg",
        "dark_cavern_ambient": "https://opengameart.org/sites/default/files/dark_cavern_ambient_002.ogg",
    }
    for track_key, source_url in sources.items():
        download_cc0_ogg_track(track_key, source_url)


def generate_haunted_theme() -> None:
    """Fetch the CC0 Spooky Fester track and encode the client-ready OGG."""
    source_url = "https://opengameart.org/sites/default/files/spooky_2.mp3"
    SOUNDS_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="halloween-audio-") as tmp:
        source_path = Path(tmp) / "spooky-fester.mp3"
        ogg_path = SOUNDS_DIR / "haunted_theme.ogg"
        request = urllib.request.Request(
            source_url,
            headers={"User-Agent": "HalloweenCore-build/1.0 (CC0 audio asset)"},
        )
        for attempt in range(3):
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    data = response.read()
                if len(data) < 500_000:
                    raise RuntimeError(
                        f"OpenGameArt audio download looks incomplete ({len(data)} bytes)"
                    )
                if not (
                    data.startswith(b"ID3")
                    or (
                        len(data) > 1
                        and data[0] == 0xFF
                        and (data[1] & 0xE0) == 0xE0
                    )
                ):
                    raise RuntimeError("OpenGameArt response is not a recognizable MP3")
                source_path.write_bytes(data)
                break
            except (OSError, urllib.error.URLError, TimeoutError, RuntimeError) as exc:
                if attempt == 2:
                    raise RuntimeError(
                        f"Could not retrieve Spooky Fester from {source_url}: {exc}"
                    ) from exc
                time.sleep(2 ** attempt)

        subprocess.run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-i", str(source_path), "-vn", "-map_metadata", "-1",
                "-ac", "1", "-ar", str(SAMPLE_RATE),
                "-c:a", "libvorbis", "-q:a", "4", str(ogg_path),
            ],
            check=True,
        )

        if not ogg_path.is_file() or ogg_path.read_bytes()[:4] != b"OggS":
            raise RuntimeError("ffmpeg did not create a valid OGG container")

        duration_ms = round(probe_audio_duration(ogg_path) * 1000)
        config_path = ROOT / "src" / "main" / "resources" / "config.yml"
        config_text = config_path.read_text(encoding="utf-8")
        config_text, replacements = re.subn(
            r"(?m)^  loop-milliseconds:\s*\d+\s*$",
            f"  loop-milliseconds: {duration_ms}",
            config_text,
        )
        if replacements != 1:
            raise RuntimeError(
                "Expected exactly one 'atmosphere.loop-milliseconds' setting in config.yml"
            )
        config_path.write_text(config_text, encoding="utf-8")

    print(
        f"Encoded CC0 Spooky Fester as {ogg_path.relative_to(ROOT)} "
        f"({ogg_path.stat().st_size:,} bytes, {duration_ms} ms)"
    )


def generate_silent_music_asset() -> None:
    """Create the silent OGG used to replace vanilla music events in sounds.json."""
    target = (
        ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "resourcepack"
        / "assets" / "warriorland_halloween" / "sounds" / "halloween_silence.ogg"
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-f", "lavfi", "-i", "anullsrc=r=22050:cl=mono",
            "-t", "1.0", "-vn", "-ac", "1", "-ar", str(SAMPLE_RATE),
            "-c:a", "libvorbis", "-q:a", "0", str(target),
        ],
        check=True,
    )
    if not target.is_file() or target.read_bytes()[:4] != b"OggS":
        raise RuntimeError("ffmpeg did not create a valid silent OGG for music replacement")
    print(f"Generated silent music replacement: {target.relative_to(ROOT)}")


def generate_event_sting() -> None:
    duration = 5.0
    count = int(SAMPLE_RATE * duration)
    rng = random.Random(991026)
    partials = []
    for i in range(24):
        f = rng.uniform(130.0, 1250.0)
        phase = rng.uniform(0.0, TWO_PI)
        amp = rng.uniform(0.001, 0.006) / (1.0 + i * 0.04)
        partials.append((f, amp, phase))
    output: list[float] = []
    for n in range(count):
        t = n / SAMPLE_RATE
        attack = smoothstep(t / 0.42)
        end_fade = smoothstep((duration - t) / 0.32)
        decay = math.exp(-t / 1.75)
        impact = (s(43.65, t, duration, 0.2) + 0.32 * s(65.406, t, duration, 0.4)
                  + 0.10 * s(87.307, t, duration, 0.1)) * math.exp(-t / 1.35) * smoothstep(t / 0.035)
        wash = sum(amp * s(f, t, duration, phase) for f, amp, phase in partials) * attack * decay
        bells = 0.0
        for start, f, dur, gain in ((0.25, 587.33, 2.8, 0.085), (0.68, 554.37, 3.0, 0.065),
                                    (1.25, 415.30, 3.1, 0.060), (1.9, 311.13, 2.6, 0.052)):
            age = t - start
            if 0 <= age < dur:
                env = smoothstep(age / 0.05) * math.exp(-age / (dur / 2.0))
                bells += gain * env * (s(f, t, duration, 0.7) + 0.20 * s(f * 2.73, t, duration, 1.2))
        output.append(math.tanh((impact * 0.55 + wash * 2.2 + bells) * end_fade))
    write_ogg("event_sting", normalize(output))


def generate_custom_event_cue(
    name: str,
    duration: float,
    seed: int,
    bass_hz: float,
    notes: tuple[tuple[float, float, float, float], ...],
    brightness: float,
) -> None:
    """Create a distinct, self-contained cue so every event has its own audio identity."""
    count = int(SAMPLE_RATE * duration)
    rng = random.Random(seed)
    partials = []
    for i in range(26):
        frequency = rng.uniform(bass_hz * 1.6, bass_hz * 22.0)
        amplitude = rng.uniform(0.0012, 0.0065) / (1.0 + i * 0.055)
        partials.append((frequency, amplitude, rng.uniform(0.0, TWO_PI)))
    output: list[float] = []
    for n in range(count):
        t = n / SAMPLE_RATE
        attack = smoothstep(t / 0.10)
        tail = smoothstep((duration - t) / 0.36)
        decay = math.exp(-t / (duration * 0.40))
        low = (
            0.72 * s(bass_hz, t, duration, 0.2)
            + 0.31 * s(bass_hz * 1.48, t, duration, 1.1)
            + 0.18 * s(bass_hz * 2.03, t, duration, 0.3)
        ) * math.exp(-t / (duration * 0.33))
        texture = sum(amplitude * s(freq, t, duration, phase)
                      for freq, amplitude, phase in partials) * decay
        transient = (
            0.38 * s(bass_hz * 0.51, t, duration, 0.2)
            + 0.18 * s(bass_hz * 0.77, t, duration, 0.6)
        ) * math.exp(-t / 0.72) * smoothstep(t / 0.022)
        melody = 0.0
        for start, frequency, note_duration, gain in notes:
            age = t - start
            if 0.0 <= age < note_duration:
                envelope = smoothstep(age / 0.035) * math.exp(-age / (note_duration / 2.45))
                fundamental = s(frequency, t, duration, 0.4)
                overtone = s(frequency * 2.01, t, duration, 1.2)
                shimmer = s(frequency * 3.97, t, duration, 0.1)
                melody += gain * envelope * (fundamental + 0.28 * overtone + brightness * 0.11 * shimmer)
        grain = (rng.random() * 2.0 - 1.0) * 0.014 * math.exp(-t / 0.22)
        output.append(math.tanh((low * 0.72 + texture * 2.0 + transient * 0.75 + melody + grain) * tail * attack))
    write_ogg(name, normalize(output))


def generate_event_cues() -> None:
    # Dissonant soul chimes with a hollow low-end pulse.
    generate_custom_event_cue(
        "soulstorm_sting", 5.2, 20261001, 42.0,
        ((0.15, 587.33, 3.0, 0.10), (0.50, 554.37, 3.0, 0.09),
         (1.05, 740.00, 2.5, 0.07), (1.65, 415.30, 2.8, 0.075)),
        0.42,
    )
    # Witching hour: tight, detuned notes and an unsettling rising accent.
    generate_custom_event_cue(
        "witching_sting", 4.8, 20261002, 51.9,
        ((0.08, 311.13, 3.2, 0.11), (0.42, 369.99, 2.9, 0.09),
         (0.88, 466.16, 3.1, 0.10), (1.45, 622.25, 2.6, 0.08)),
        0.56,
    )
    # Harvest cue has a warmer, bell-like cadence over a dark drone.
    generate_custom_event_cue(
        "harvest_sting", 4.2, 20261003, 65.4,
        ((0.12, 220.00, 2.7, 0.085), (0.48, 329.63, 2.7, 0.085),
         (0.88, 440.00, 2.4, 0.075), (1.35, 523.25, 2.2, 0.065)),
        0.20,
    )
    # Blood Moon has the heaviest drum-like hit and a long, ominous tail.
    generate_custom_event_cue(
        "blood_moon_rise", 7.0, 20261004, 34.65,
        ((0.18, 196.00, 4.0, 0.09), (0.72, 293.66, 3.7, 0.10),
         (1.40, 440.00, 3.5, 0.095), (2.20, 587.33, 3.1, 0.07)),
        0.35,
    )
    # Pumpkin apocalypse uses an unstable, bright, cracked-bell chord.
    generate_custom_event_cue(
        "pumpkin_apocalypse", 5.4, 20261005, 55.0,
        ((0.10, 164.81, 3.0, 0.11), (0.40, 246.94, 2.8, 0.10),
         (0.83, 369.99, 2.7, 0.09), (1.30, 493.88, 2.6, 0.085)),
        0.63,
    )
    # Graveyard rising is cold and sparse with falling, distant bell notes.
    generate_custom_event_cue(
        "graveyard_rising", 6.0, 20261006, 38.9,
        ((0.18, 392.00, 3.7, 0.075), (0.74, 293.66, 3.6, 0.085),
         (1.35, 220.00, 3.4, 0.09), (2.00, 164.81, 3.0, 0.08)),
        0.30,
    )


def main() -> None:
    if shutil.which("ffmpeg") is None:
        raise SystemExit("ffmpeg is required to encode Halloween OGG assets")
    generate_haunted_theme()
    generate_playlist_assets()
    generate_event_sting()
    generate_event_cues()
    generate_silent_music_asset()


if __name__ == "__main__":
    main()
