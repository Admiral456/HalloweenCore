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


def main() -> None:
    if shutil.which("ffmpeg") is None:
        raise SystemExit("ffmpeg is required to encode Halloween OGG assets")
    generate_haunted_theme()
    generate_event_sting()
    generate_silent_music_asset()


if __name__ == "__main__":
    main()
