#!/usr/bin/env python3
"""Create byte-reproducible ZIP artifacts for one explicitly selected Minecraft version."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path, PurePosixPath


def create_zip(root: Path, output: Path, minecraft_version: str,
               includes: list[str] | None = None,
               metadata_path: str | None = None) -> str:
    root = root.resolve()
    output = output.resolve()
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", minecraft_version):
        raise ValueError("minecraft_version must look like 1.21.10")
    prefixes = includes or ["."]
    files: dict[str, Path] = {}
    for include in prefixes:
        relative = PurePosixPath(include)
        if relative.is_absolute() or ".." in relative.parts:
            raise ValueError(f"Unsafe include path: {include}")
        path = (root / Path(*relative.parts)).resolve()
        if root != path and root not in path.parents:
            raise ValueError(f"Include path escapes source root: {include}")
        if not path.exists():
            raise FileNotFoundError(f"Package input does not exist: {include}")
        candidates = [path] if path.is_file() else sorted(p for p in path.rglob("*") if p.is_file())
        for candidate in candidates:
            if candidate.is_symlink():
                raise ValueError(f"Symbolic links are not permitted in deterministic packages: {candidate}")
            rel = candidate.relative_to(root).as_posix()
            if rel == "BUILD_METADATA.json":
                continue
            if rel in files and files[rel] != candidate:
                raise ValueError(f"Duplicate archive path: {rel}")
            files[rel] = candidate
    if not files:
        raise ValueError("No source files found to package")

    metadata = {
        "schema_version": 1,
        "minecraft_version": minecraft_version,
        "archive_format": "deterministic-zip",
        "timestamp_policy": "fixed 2020-01-01T00:00:00Z",
        "entries_sorted": True,
        "compression": "DEFLATE level 9",
    }
    metadata_bytes = (json.dumps(metadata, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")
    if metadata_path:
        archive_metadata_path = PurePosixPath(metadata_path)
        if archive_metadata_path.is_absolute() or ".." in archive_metadata_path.parts:
            raise ValueError(f"Unsafe metadata path: {metadata_path}")
    else:
        archive_metadata_path = PurePosixPath("BUILD_METADATA.json")

    output.parent.mkdir(parents=True, exist_ok=True)
    if archive_metadata_path.as_posix() in files:
        raise ValueError("Build metadata path collides with a source file")
    payloads = {name: path.read_bytes() for name, path in files.items()}
    payloads[archive_metadata_path.as_posix()] = metadata_bytes

    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9, strict_timestamps=True) as archive:
        for archive_name, payload in sorted(payloads.items()):
            info = zipfile.ZipInfo(archive_name, date_time=(2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.flag_bits |= 0x800
            archive.writestr(info, payload, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    return hashlib.sha256(output.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True, help="Root directory for archive-relative paths")
    parser.add_argument("--output", required=True, help="Destination ZIP")
    parser.add_argument("--minecraft-version", required=True, help="Target Minecraft version, e.g. 1.21.10")
    parser.add_argument("--include", action="append", help="Relative file/directory to include; repeatable")
    parser.add_argument("--metadata-path", help="Path of deterministic BUILD_METADATA.json inside the archive")
    args = parser.parse_args()
    digest = create_zip(Path(args.root), Path(args.output), args.minecraft_version, args.include, args.metadata_path)
    print(f"PASS reproducible ZIP: {args.output} (Minecraft {args.minecraft_version}, SHA-256 {digest})")


if __name__ == "__main__":
    main()
