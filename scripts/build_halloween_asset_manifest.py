#!/usr/bin/env python3
"""Build a deterministic SHA-256 inventory for resource-pack and ModelEngine assets."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "itemsadder/contents/warriorland_halloween"
MANIFEST_PATH = CONTENT / "ASSET_MANIFEST.json"
ASSET_ROOTS = (
    CONTENT / "resourcepack/assets",
    CONTENT / "textures",
    CONTENT / "sounds",
    ROOT / "mythicmobs/models",
)
ASSET_SUFFIXES = {".png", ".bbmodel", ".json", ".ogg", ".fsh", ".mcmeta"}


def build_manifest(root: Path, minecraft_version: str) -> dict:
    content = root / "itemsadder/contents/warriorland_halloween"
    output_path = content / "ASSET_MANIFEST.json"
    source_manifest_path = root / "third-party-assets/stevs-halloween/asset-source-manifest.json"
    source_manifest = json.loads(source_manifest_path.read_text(encoding="utf-8"))
    imported = {
        entry["target_path"]: {
            "license": entry["license"],
            "source": source_manifest["source"]["project"],
            "author": source_manifest["source"]["author"],
            "source_sha256": entry["sha256"],
        }
        for entry in source_manifest.get("assets", [])
    }

    roots = (
        content / "resourcepack/assets",
        content / "textures",
        content / "sounds",
        root / "mythicmobs/models",
    )
    files = []
    for scan_root in roots:
        if not scan_root.exists():
            continue
        for path in sorted(scan_root.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in ASSET_SUFFIXES:
                continue
            relative = path.relative_to(root).as_posix()
            if path == output_path:
                continue
            info = {
                "path": relative,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                "size_bytes": path.stat().st_size,
                "format": path.suffix.lower().lstrip("."),
            }
            if relative in imported:
                info.update({
                    "provenance": "third-party import",
                    "license": imported[relative]["license"],
                    "author": imported[relative]["author"],
                    "source_project": imported[relative]["source"],
                    "source_asset_sha256": imported[relative]["source_sha256"],
                })
            elif "/mythicmobs/models/" in "/" + relative:
                info.update({
                    "provenance": "existing ModelEngine/Blockbench asset",
                    "license": "see asset-specific author/permission record",
                })
            elif "/textures/armor/" in "/" + relative:
                info.update({
                    "provenance": "ItemsAdder source equipment texture",
                    "license": "project asset; see repository asset notes",
                })
            else:
                info.update({
                    "provenance": "HalloweenCore resource-pack asset or generated output",
                    "license": "project asset or separately attributed source; see ASSETS.md",
                })
            files.append(info)

    paths = [entry["path"] for entry in files]
    if len(paths) != len(set(paths)):
        raise ValueError("The same asset path was scanned more than once")
    files.sort(key=lambda item: item["path"])
    return {
        "schema_version": 1,
        "minecraft_version": minecraft_version,
        "namespace": "warriorland_halloween",
        "hash_algorithm": "SHA-256",
        "manifest_path": output_path.relative_to(root).as_posix(),
        "manifest_hash_note": "The manifest file is excluded from its own hash inventory to avoid a recursive checksum.",
        "asset_count": len(files),
        "assets": files,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-version", required=True, help="Target Minecraft version, e.g. 1.21.10")
    parser.add_argument("--check", action="store_true", help="Verify that the committed/generated manifest is up to date")
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", args.minecraft_version):
        parser.error("minecraft-version must look like 1.21.10")
    expected = json.dumps(build_manifest(ROOT, args.minecraft_version), ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    if args.check:
        if not MANIFEST_PATH.is_file():
            raise SystemExit(f"ERROR: missing generated asset manifest: {MANIFEST_PATH.relative_to(ROOT)}")
        actual = MANIFEST_PATH.read_text(encoding="utf-8")
        if actual != expected:
            raise SystemExit("ERROR: ASSET_MANIFEST.json is stale or non-reproducible; regenerate it and review the diff")
        print(f"PASS deterministic asset manifest: {json.loads(actual)['asset_count']} assets, Minecraft {args.minecraft_version}")
    else:
        MANIFEST_PATH.write_text(expected, encoding="utf-8", newline="\n")
        print(f"Wrote {MANIFEST_PATH.relative_to(ROOT)} with {json.loads(expected)['asset_count']} assets for Minecraft {args.minecraft_version}")


if __name__ == "__main__":
    main()
