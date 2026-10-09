#!/usr/bin/env python3
"""Generate original, sample-free Halloween audio for the ItemsAdder pack.

Requires only Python's standard library and ffmpeg. Files are generated in CI
and locally so no third-party recording must be downloaded or redistributed.
"""
from __future__ import annotations

import array
import math
import random
import shutil
import subprocess
import tempfile
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


def generate_haunted_theme() -> None:
    duration = 64.0
    count = int(SAMPLE_RATE * duration)
    rng = random.Random(4562026)
    # Four slowly shifting minor-ish chords: E minor, C, A minor, B tension.
    chords = [
        [41.203, 61.735, 82.407, 123.471],
        [32.703, 48.999, 65.406, 98.000],
        [27.500, 41.203, 55.000, 82.407],
        [30.868, 46.249, 61.735, 92.499],
    ]
    melody = [659.25, 587.33, 493.88, 392.00, 440.00, 587.33, 349.23, 493.88]
    # Deterministic oscillator bank makes an airy, filtered-noise-like wind bed.
    wind_partials = []
    for i in range(26):
        f = rng.uniform(95.0, 760.0)
        amp = rng.uniform(0.003, 0.010) / (1.0 + i * 0.045)
        phase = rng.uniform(0.0, TWO_PI)
        wind_partials.append((f, amp, phase))

    output: list[float] = []
    for n in range(count):
        t = n / SAMPLE_RATE
        local = t % 16.0
        section = int(t // 16.0)
        blend = smoothstep((local - 13.5) / 2.5)
        chord_a = chords[section]
        chord_b = chords[(section + 1) % len(chords)]
        pad = 0.0
        for j, f in enumerate(chord_a):
            phase = section * 0.41 + j * 1.17
            pad += (1.0 - blend) * [0.16, 0.075, 0.060, 0.025][j] * (
                s(f, t, duration, phase) + 0.18 * s(f * 2.0, t, duration, phase * 0.7)
            )
        next_section = (section + 1) % len(chords)
        for j, f in enumerate(chord_b):
            phase = next_section * 0.41 + j * 1.17
            pad += blend * [0.16, 0.075, 0.060, 0.025][j] * (
                s(f, t, duration, phase) + 0.18 * s(f * 2.0, t, duration, phase * 0.7)
            )

        drone = (
            0.075 * s(41.203, t, duration, 0.4)
            + 0.035 * s(20.602, t, duration, 1.1)
            + 0.024 * s(61.735, t, duration, 2.2)
        ) * (0.82 + 0.18 * math.sin(TWO_PI * t / 32.0 + 0.6))

        wind = sum(amp * s(f, t, duration, phase) for f, amp, phase in wind_partials)
        wind *= 0.78 + 0.22 * math.sin(TWO_PI * t / 16.0 + 0.7)

        # Sparse bell motif each sixteen-second section; notes end before the boundary.
        bells = 0.0
        for when, note_idx, note_duration in ((1.0, 0, 2.5), (4.8, 2, 2.4), (8.7, 1, 2.7), (12.2, 3, 2.4)):
            age = local - when
            if 0.0 <= age < note_duration:
                env = smoothstep(age / 0.12) * math.exp(-age / (note_duration / 2.4))
                f = melody[(note_idx + section) % len(melody)]
                bells += 0.055 * env * (
                    s(f, t, duration, section * 0.33)
                    + 0.24 * s(f * 2.76, t, duration, 0.7)
                    + 0.08 * s(f * 4.1, t, duration, 1.1)
                )
        whisper = (
            0.012 * s(784.0, t, duration, 1.3) * math.sin(TWO_PI * t / 32.0 + 0.4)
            + 0.008 * s(932.33, t, duration, 0.8) * math.sin(TWO_PI * t / 16.0 + 2.0)
        )
        output.append(math.tanh((pad + drone + wind * 0.38 + bells + whisper) * 1.65))
    write_ogg("haunted_theme", normalize(output))


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


if __name__ == "__main__":
    main()
