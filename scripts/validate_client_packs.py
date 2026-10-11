#!/usr/bin/env python3
from pathlib import Path
import binascii
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "itemsadder" / "contents" / "warriorland_halloween"
PACK = ROOT / "client-packs" / "HalloweenCore-Iris-Crimson-Sky"

def fail(message: str) -> None:
    print("ERROR:", message, file=sys.stderr)
    raise SystemExit(1)

required = {
    "pack.png": None,
    "README.txt": ("OPTIONAL client-side shader pack", "Iris", ".minecraft/shaderpacks/"),
    "LICENSE.txt": ("HalloweenCore", "No warranty"),
    "shaders/shaders.properties": ("screen.HALLOWEEN", "SKY_CRIMSON_INTENSITY", "BLOOD_MOON_INTENSITY"),
    "shaders/gbuffers_skybasic.fsh": ("#version 120", "uniform int worldTime;", "SKY_CRIMSON_INTENSITY", "gl_FragData[0]"),
    "shaders/gbuffers_skytextured.fsh": ("#version 120", "uniform int worldTime;", "BLOOD_MOON_INTENSITY", "gl_FragData[0]"),
}
for relative, markers in required.items():
    path = PACK / relative
    if not path.is_file():
        fail(f"Missing optional shader-pack file: {path.relative_to(ROOT)}")
    if markers is not None:
        content = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in content:
                fail(f"{relative} is missing required marker: {marker}")
        if relative.endswith((".fsh", ".vsh")) and content.count("{") != content.count("}"):
            fail(f"{relative} has unbalanced GLSL braces")

icon = (PACK / "pack.png").read_bytes()
if icon[:8] != b"\x89PNG\r\n\x1a\n" or icon[12:16] != b"IHDR":
    fail("Optional shader-pack icon is not a valid PNG")
width, height = struct.unpack(">II", icon[16:24])
if (width, height) != (128, 128):
    fail(f"Shader-pack icon must be 128x128, got {width}x{height}")
offset = 8
saw_iend = False
while offset + 12 <= len(icon):
    length = struct.unpack(">I", icon[offset:offset + 4])[0]
    kind = icon[offset + 4:offset + 8]
    end = offset + 8 + length
    if end + 4 > len(icon):
        fail("Shader-pack icon contains a truncated PNG chunk")
    chunk = icon[offset + 8:end]
    crc = struct.unpack(">I", icon[end:end + 4])[0]
    if (binascii.crc32(kind + chunk) & 0xffffffff) != crc:
        fail("Shader-pack icon contains a corrupt PNG chunk")
    offset = end + 4
    if kind == b"IEND":
        saw_iend = True
        break
if not saw_iend or offset != len(icon):
    fail("Shader-pack icon is missing IEND or contains trailing data")

mandatory_shader = CONTENT / "resourcepack" / "assets" / "minecraft" / "shaders" / "core" / "sky.fsh"
if mandatory_shader.exists():
    fail("The old sky.fsh is still inside the mandatory ItemsAdder content; remove it to avoid a version-sensitive client override")
print("PASS optional Iris shader pack: shader programs, menu options, documentation and 128x128 pack icon validated")
