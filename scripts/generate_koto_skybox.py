#!/usr/bin/env python3
"""Convert Koto's six-face red-sky cubemap into an OptiFine Java sky atlas.

The source archive is third-party content, licensed MIT on the project pages.
Only its six image faces are used. Original 512x512 faces are assembled without
resizing into the 3x2 atlas expected by Java custom-sky loaders.
"""
from __future__ import annotations

import argparse
import hashlib
import io
from pathlib import Path
import sys
import zipfile

from PIL import Image, UnidentifiedImageError

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "itemsadder/contents/warriorland_halloween"
OUTPUT = CONTENT / "resourcepack/assets/minecraft/optifine/sky/world0/sky1.png"
SOURCE_DIR = "assets/minecraft/textures/environment/overworld_cubemap"
FACE_HASHES = {
    0: "467c3cae4bd103c41cdca9f757fddc3c239e9f280981043eedc1bd2f8b3ea1b2",
    1: "2f29da03f7dcd73221474652e2f31a4d5cb235057b14c2105437b7d2307817b4",
    2: "b5f555661a3a0a812bd3ede48fc9d33d7cd2594e6e3eda339a539ae0ee765d31",
    3: "969f31a07f7c3f360b29a32768a1332f4919c1c86ed340114e92d9dc42bdb6e9",
    4: "c0a470bdd2819fb443f4c9f64822394220116bb0e0d1ffb782abb9fc64c27806",
    5: "ebe2fe434f0ac6a79857e556a5c69a120194f1a48bb04c2526a1eb03988b3cc6",
}
# Source cubemap mapping: 0=south (+Z), 1=east (+X), 2=north (-Z),
# 3=west (-X), 4=up (+Y), 5=down (-Y).
# OptiFine's 3x2 sky atlas layout is:
# [down, up, north] / [west, south, east].
# The previous order incorrectly put west/up/east above north/down/south,
# splitting the six directions across the wrong faces and causing the jagged
# black shards reported in-game.
ATLAS_LAYOUT = ((5, 0, 0), (4, 1, 0), (2, 2, 0),
                (3, 0, 1), (0, 1, 1), (1, 2, 1))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-zip", required=True, type=Path,
                        help="red sky by koto (1.0).zip, e.g. downloaded from Modrinth")
    args = parser.parse_args()
    source_zip = args.source_zip
    if not source_zip.is_file():
        print(f"ERROR: source ZIP not found: {source_zip}", file=sys.stderr)
        return 1

    faces: dict[int, Image.Image] = {}
    try:
        with zipfile.ZipFile(source_zip) as archive:
            names = set(archive.namelist())
            for face_id, expected_hash in FACE_HASHES.items():
                entry = f"{SOURCE_DIR}/cubemap_{face_id}.png"
                if entry not in names:
                    raise ValueError(f"missing source face: {entry}")
                raw = archive.read(entry)
                actual_hash = hashlib.sha256(raw).hexdigest()
                if actual_hash != expected_hash:
                    raise ValueError(
                        f"unexpected cubemap_{face_id}.png checksum; "
                        "source archive is not the reviewed Koto texture"
                    )
                # The supplied source ZIP labels these files .png although their
                # actual signatures are BMP. Pillow detects the true format.
                with Image.open(io.BytesIO(raw)) as image:
                    image.load()
                    if image.size != (512, 512):
                        raise ValueError(
                            f"cubemap_{face_id}.png must be 512x512, got {image.size}"
                        )
                    faces[face_id] = image.convert("RGB")
    except (OSError, zipfile.BadZipFile, KeyError, ValueError, UnidentifiedImageError) as exc:
        print(f"ERROR: cannot convert Koto sky source: {exc}", file=sys.stderr)
        return 1

    atlas = Image.new("RGB", (1536, 1024))
    for face_id, column, row in ATLAS_LAYOUT:
        atlas.paste(faces[face_id], (column * 512, row * 512))
        # Guard against accidental loss or resizing of any face during assembly.
        tile = atlas.crop((column * 512, row * 512, (column + 1) * 512, (row + 1) * 512))
        if tile.tobytes() != faces[face_id].tobytes():
            raise ValueError(f"cubemap_{face_id} was not copied losslessly into the expected atlas cell")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUTPUT, format="PNG", optimize=True, compress_level=9)

    with Image.open(OUTPUT) as check:
        check.load()
        if check.size != (1536, 1024) or check.format != "PNG":
            print("ERROR: generated Java sky atlas failed output validation", file=sys.stderr)
            return 1
    output_sha = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    print(
        f"PASS Koto Java sky atlas: {OUTPUT.relative_to(ROOT)} "
        f"(1536x1024, {OUTPUT.stat().st_size} bytes, sha256={output_sha})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
