from __future__ import annotations
import json, sys, tempfile, unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from build_halloween_asset_manifest import build_manifest
from package_deterministic_zip import create_zip
from validate_content_registry import assert_same_paths, entries, parse_versions, validate

class AssetPipelineIntegrationTests(unittest.TestCase):
    def test_registry_matches_itemsadder_and_all_asset_directories(self):
        validate(ROOT)
    def test_manifest_is_stable_and_records_sha256_provenance(self):
        first = build_manifest(ROOT, "1.21.10")
        second = build_manifest(ROOT, "1.21.10")
        self.assertEqual(json.dumps(first, ensure_ascii=False, sort_keys=True), json.dumps(second, ensure_ascii=False, sort_keys=True))
        self.assertEqual(first["hash_algorithm"], "SHA-256")
        paths = [asset["path"] for asset in first["assets"]]
        self.assertEqual(paths, sorted(paths))
        self.assertTrue(all(len(asset["sha256"]) == 64 for asset in first["assets"]))
        imported = [asset for asset in first["assets"] if asset.get("provenance") == "third-party import"]
        self.assertEqual(len(imported), 13)
        self.assertTrue(all(asset["license"] == "MIT" for asset in imported))
    def test_manifest_rejects_unverified_minecraft_version(self):
        with self.assertRaisesRegex(ValueError, "not verified"):
            build_manifest(ROOT, "1.21.1")
    def test_zip_is_byte_for_byte_reproducible(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "source"
            root.mkdir()
            (root / "b.txt").write_text("second", encoding="utf-8")
            (root / "a.txt").write_text("first", encoding="utf-8")
            a, b = Path(directory) / "a.zip", Path(directory) / "b.zip"
            digest_a = create_zip(root, a, "1.21.10", ["."], "BUILD_METADATA.json")
            digest_b = create_zip(root, b, "1.21.10", ["."], "BUILD_METADATA.json")
            self.assertEqual(a.read_bytes(), b.read_bytes())
            self.assertEqual(digest_a, digest_b)
    def test_path_validator_detects_unused_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(SystemExit):
                assert_same_paths("test", {root / "registered.png"}, {root / "registered.png", root / "unused.png"})
    def test_protected_artist_assets_are_not_generated(self):
        text = (ROOT / "src/main/resources/content-registry.yml").read_text(encoding="utf-8")
        protected = entries(text, "protected-assets")
        for asset_id in ("void-reaper", "frost-stalker", "nightmare"):
            self.assertEqual(protected[asset_id]["status"], "externally-authored-protected")
            self.assertEqual(protected[asset_id]["generation"], "prohibited")
    def test_only_1_21_10_is_a_verified_build_profile(self):
        text = (ROOT / "src/main/resources/content-registry.yml").read_text(encoding="utf-8")
        self.assertEqual(parse_versions(text), ("1.21.10", ["1.21.10"]))

if __name__ == "__main__":
    unittest.main()
