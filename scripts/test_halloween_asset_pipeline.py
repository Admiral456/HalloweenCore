#!/usr/bin/env python3
"""Unit/integration checks for the HalloweenCore content and asset-pack pipeline."""
from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from build_halloween_asset_manifest import build_manifest  # noqa: E402
from package_deterministic_zip import create_zip  # noqa: E402
from validate_halloween_content_registry import load_registry, validate  # noqa: E402
from validate_content_registry import validate as validate_full_asset_inventory  # noqa: E402


class ContentRegistryTests(unittest.TestCase):
    def test_registered_items_have_unique_ids_and_texture_keys(self) -> None:
        items = load_registry()
        self.assertGreaterEqual(len(items), 29)
        self.assertEqual(len({v["itemsadder-id"] for v in items.values()}), len(items))
        self.assertEqual(len({v["texture"] for v in items.values()}), len(items))

    def test_registry_and_imported_asset_hashes_match_the_real_pack(self) -> None:
        self.assertGreaterEqual(validate(), 29)

    def test_models_decorations_armor_and_audio_have_no_unregistered_files(self) -> None:
        # Full-path check also fails when an asset is orphaned from the central registry.
        validate_full_asset_inventory(ROOT)


class AssetManifestTests(unittest.TestCase):
    def test_manifest_is_sorted_unique_and_sha256_only(self) -> None:
        manifest = build_manifest(ROOT, "1.21.10")
        paths = [asset["path"] for asset in manifest["assets"]]
        self.assertEqual(paths, sorted(paths))
        self.assertEqual(len(paths), len(set(paths)))
        self.assertEqual(manifest["hash_algorithm"], "SHA-256")
        self.assertEqual(manifest["minecraft_version"], "1.21.10")
        self.assertGreater(manifest["asset_count"], 0)
        for asset in manifest["assets"]:
            self.assertRegex(asset["sha256"], r"^[0-9a-f]{64}$")
            self.assertGreater(asset["size_bytes"], 0)
        imported = [a for a in manifest["assets"] if a.get("source_project") == "Stev's Halloween"]
        self.assertEqual(len(imported), 13)
        self.assertTrue(all(a["license"] == "MIT" for a in imported))

    def test_unverified_donor_version_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "not verified"):
            build_manifest(ROOT, "1.21.1")

    def test_deterministic_zip_bytes_are_identical(self) -> None:
        with tempfile.TemporaryDirectory(prefix="halloweencore-pack-test-") as temp:
            temp_path = Path(temp)
            source_root = ROOT / "itemsadder/contents"
            first = temp_path / "first.zip"
            second = temp_path / "second.zip"
            first_hash = create_zip(
                source_root, first, "1.21.10",
                ["warriorland_halloween"],
                "warriorland_halloween/BUILD_METADATA.json",
            )
            second_hash = create_zip(
                source_root, second, "1.21.10",
                ["warriorland_halloween"],
                "warriorland_halloween/BUILD_METADATA.json",
            )
            self.assertEqual(first_hash, second_hash)
            self.assertEqual(hashlib.sha256(first.read_bytes()).hexdigest(), hashlib.sha256(second.read_bytes()).hexdigest())
            with zipfile.ZipFile(first) as archive:
                self.assertIsNone(archive.testzip())
                names = archive.namelist()
                self.assertEqual(names, sorted(names))
                self.assertIn("warriorland_halloween/BUILD_METADATA.json", names)
                self.assertIn("warriorland_halloween/THIRD_PARTY_LICENSES/stevs-halloween-MIT.txt", names)
                self.assertIn("warriorland_halloween/THIRD_PARTY_LICENSES/README.txt", names)
                self.assertIn("warriorland_halloween/THIRD_PARTY_LICENSES/stevs-halloween-source-manifest.json", names)
                self.assertIn(b"MIT License", archive.read("warriorland_halloween/THIRD_PARTY_LICENSES/stevs-halloween-MIT.txt"))
                shipped_source_manifest = json.loads(archive.read("warriorland_halloween/THIRD_PARTY_LICENSES/stevs-halloween-source-manifest.json"))
                self.assertEqual(len(shipped_source_manifest["assets"]), 13)
                metadata = json.loads(archive.read("warriorland_halloween/BUILD_METADATA.json"))
                self.assertEqual(metadata["minecraft_version"], "1.21.10")
                self.assertTrue(all(info.date_time == (2020, 1, 1, 0, 0, 0) for info in archive.infolist()))


if __name__ == "__main__":
    unittest.main(verbosity=2)
