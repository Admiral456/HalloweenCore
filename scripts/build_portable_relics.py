#!/usr/bin/env python3
"""Assemble a portable, independently namespaced ItemsAdder + plugin ZIP."""
from __future__ import annotations

import json
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
TARGET = ROOT / "target" / "HalloweenCore-Portable-Relics"
CONTENT = TARGET / "contents" / "halloween_relics"
IA_SOURCE = ROOT / "portable-relics" / "contents" / "halloween_relics"
ASSET_ROOT = ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "resourcepack" / "assets" / "warriorland_halloween"
MODEL_ROOT = ROOT / "itemsadder" / "contents" / "warriorland_halloween" / "models"
PLUGIN_JAR = ROOT / "portable-relics" / "target" / "HalloweenPortableRelics.jar"


def copy_required(source: pathlib.Path, destination: pathlib.Path) -> None:
    if not source.is_file():
        raise FileNotFoundError(f"required generated asset is missing: {source.relative_to(ROOT)}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def main() -> None:
    if not PLUGIN_JAR.is_file():
        raise FileNotFoundError("Build portable-relics/target/HalloweenPortableRelics.jar first")
    if TARGET.exists():
        shutil.rmtree(TARGET)
    CONTENT.mkdir(parents=True, exist_ok=True)
    shutil.copytree(IA_SOURCE / "configs", CONTENT / "configs")

    source_icons = ASSET_ROOT / "textures" / "item"
    target_icons = CONTENT / "textures" / "item"
    for name in ("batwing_elytra.png", "batwing_elytra_broken.png", "nocturne_sovereign_cowl.png"):
        copy_required(source_icons / name, target_icons / name)

    copy_required(
        ASSET_ROOT / "textures" / "entity" / "equipment" / "wings" / "batwing_elytra.png",
        CONTENT / "textures" / "entity" / "equipment" / "wings" / "batwing_elytra.png",
    )

    source_model = MODEL_ROOT / "armor" / "nocturne_sovereign_cowl_3d.json"
    if not source_model.is_file():
        raise FileNotFoundError("generated wearable Nocturne Sovereign Cowl model is missing")
    model = json.loads(source_model.read_text(encoding="utf-8"))
    for key in ("layer0", "particle"):
        if key in model.get("textures", {}):
            model["textures"][key] = model["textures"][key].replace(
                "warriorland_halloween:", "halloween_relics:"
            )
    target_model = CONTENT / "models" / "armor" / "nocturne_sovereign_cowl_3d.json"
    target_model.parent.mkdir(parents=True, exist_ok=True)
    target_model.write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")

    source_model = MODEL_ROOT / "source" / "nocturne_sovereign_cowl.bbmodel"
    copy_required(source_model, TARGET / "source" / "nocturne_sovereign_cowl.bbmodel")
    copy_required(PLUGIN_JAR, TARGET / "plugin" / "HalloweenPortableRelics.jar")
    copy_required(ROOT / "portable-relics" / "src" / "main" / "resources" / "config.yml", TARGET / "config.yml")
    copy_required(ROOT / "portable-relics" / "README.md", TARGET / "README.md")

    definition = (CONTENT / "configs" / "items.yml").read_text(encoding="utf-8")
    for identifier in ("batwing_elytra", "nocturne_sovereign_cowl"):
        if f"  {identifier}:" not in definition:
            raise ValueError(f"portable ItemsAdder definition missing {identifier}")
    if "namespace: halloween_relics" not in definition:
        raise ValueError("portable item namespace must remain halloween_relics")
    if "halloween_relics:item/nocturne_sovereign_cowl" not in target_model.read_text(encoding="utf-8"):
        raise ValueError("cowl model texture reference was not remapped to the portable namespace")

    manifest = ["HalloweenCore Portable Relics v1.0.0", "", "Included files:"]
    for path in sorted(TARGET.rglob("*")):
        if path.is_file():
            manifest.append(path.relative_to(TARGET).as_posix())
    (TARGET / "MANIFEST.txt").write_text("\n".join(manifest) + "\n", encoding="utf-8")
    print(f"Assembled portable relic package with {len(manifest) - 3} files: {TARGET.relative_to(ROOT)}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
