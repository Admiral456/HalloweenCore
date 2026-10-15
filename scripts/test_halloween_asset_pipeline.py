#!/usr/bin/env python3
"""Unit/integration checks for the HalloweenCore content and asset-pack pipeline."""
from __future__ import annotations

import hashlib
import json
import re
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
        self.assertGreaterEqual(len(items), 30)
        self.assertEqual(len({v["itemsadder-id"] for v in items.values()}), len(items))
        self.assertEqual(len({v["texture"] for v in items.values()}), len(items))

    def test_registry_and_imported_asset_hashes_match_the_real_pack(self) -> None:
        self.assertGreaterEqual(validate(), 30)

    def test_models_decorations_armor_and_audio_have_no_unregistered_files(self) -> None:
        # Full-path check also fails when an asset is orphaned from the central registry.
        validate_full_asset_inventory(ROOT)


class CrimsonWardenArmorTests(unittest.TestCase):
    def test_armor_lore_states_the_worn_slot_bonus(self) -> None:
        config = (ROOT / "itemsadder/contents/warriorland_halloween/configs/items.yml").read_text(encoding="utf-8")
        expected = {
            "crimson_warden_helmet": ("Při nasazení na hlavu: +6 brnění.", 3),
            "crimson_warden_chestplate": ("Při nasazení na hruď: +12 brnění.", 4),
            "crimson_warden_leggings": ("Při nasazení na nohy: +9 brnění.", 3),
            "crimson_warden_boots": ("Při nasazení na chodidla: +6 brnění.", 3),
        }
        for item_id, (lore_line, extra_armor) in expected.items():
            with self.subTest(item=item_id):
                match = re.search(
                    rf"(?ms)^  {re.escape(item_id)}:\s*\n(.*?)(?=^  [a-z0-9_]+:\s*$|\Z)",
                    config,
                )
                self.assertIsNotNone(match, f"Missing ItemsAdder entry {item_id}")
                block = match.group(1)
                self.assertIn(lore_line, block)
                modifier = re.search(r"(?m)^\s+armor:\s*(\d+)\s*$", block)
                self.assertIsNotNone(modifier, f"{item_id} must have an armor attribute modifier")
                self.assertEqual(int(modifier.group(1)), extra_armor)


    def test_licensed_apple_leaves_are_registered_as_a_translucent_block(self) -> None:
        config = (ROOT / "itemsadder/contents/warriorland_halloween/configs/items.yml").read_text(encoding="utf-8")
        registry = load_registry()
        self.assertIn("decor_stev_apple_leaves", registry)
        self.assertEqual(registry["decor_stev_apple_leaves"]["texture"], "stev_candy/apple_leaves_with_apples")
        self.assertEqual(registry["decor_stev_apple_leaves"]["itemsadder-texture"], "minecraft:block/stev_apple_leaves_with_apples")
        entry = re.search(
            r"(?ms)^  stev_apple_leaves:\s*\n(.*?)(?=^  [a-z0-9_]+:\s*$|\Z)",
            config,
        )
        self.assertIsNotNone(entry)
        self.assertIn("type: REAL_TRANSPARENT", entry.group(1))
        self.assertIn("drop_when_mined: true", entry.group(1))



class CrimsonWardenShieldTests(unittest.TestCase):
    def test_shield_is_registered_generated_and_available_in_shop(self) -> None:
        registry = load_registry()
        self.assertIn("gear_crimson_warden_shield", registry)
        definition = registry["gear_crimson_warden_shield"]
        self.assertEqual(definition["itemsadder-id"], "warriorland_halloween:crimson_warden_shield")
        self.assertEqual(definition["fallback-material"], "SHIELD")
        self.assertEqual(definition["texture"], "crimson_warden_shield")

        items_config = (
            ROOT / "itemsadder/contents/warriorland_halloween/configs/items.yml"
        ).read_text(encoding="utf-8")
        newline = chr(10)
        self.assertIn("  crimson_warden_shield:" + newline, items_config)
        shield_block = items_config.split("  crimson_warden_shield:" + newline, 1)[1].split(
            newline + "  crimson_warden_sword:", 1
        )[0]
        self.assertIn("material: SHIELD", shield_block)
        self.assertIn("texture: item/crimson_warden_shield", shield_block)

        shop_config = (ROOT / "src/main/resources/config.yml").read_text(encoding="utf-8")
        self.assertIn('itemsadder-id: "warriorland_halloween:crimson_warden_shield"', shop_config)

        generator = (ROOT / "scripts/generate_halloween_item_textures.py").read_text(encoding="utf-8")
        self.assertIn('"crimson_warden_shield.png": shield', generator)


class ConfigUpgradeCoverageTests(unittest.TestCase):
    def test_new_shield_migrates_to_existing_servers_after_older_migration_ran(self) -> None:
        core = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenCore.java"
        ).read_text(encoding="utf-8")
        defaults = (ROOT / "src/main/resources/config.yml").read_text(encoding="utf-8")

        # The migration has a dedicated version flag and is called both at startup
        # and after /halloween reload; it does not depend on the older gear-v2 flag.
        self.assertIn('getConfig().getBoolean("migrations.crimson-warden-shield-v1", false)', core)
        self.assertIn('getConfig().set("migrations.crimson-warden-shield-v1", true)', core)
        self.assertEqual(core.count("migrateCrimsonWardenShieldDefaults();"), 2)
        migration_start = core.index("private void migrateCrimsonWardenShieldDefaults()")
        migration_end = core.index("private void migrateCrimsonWardenArmorStats()", migration_start)
        migration = core[migration_start:migration_end]
        self.assertIn('"rewards.shop.crimson-warden-shield"', migration)
        self.assertIn('List.of("cost", "min-curse")', migration)
        self.assertIn('!getConfig().contains(targetPath + "." + key)', migration)

        lines = defaults.splitlines()
        try:
            start = lines.index("    crimson-warden-shield:") + 1
        except ValueError:
            self.fail("Packaged defaults must provide the new shield entry")
        end = next(
            (index for index in range(start, len(lines))
             if len(lines[index]) - len(lines[index].lstrip()) == 4
             and lines[index].rstrip().endswith(":")),
            len(lines),
        )
        shop_block = "\n".join(lines[start:end])
        self.assertIn('itemsadder-id: "warriorland_halloween:crimson_warden_shield"', shop_block)
        self.assertIn("cost: 28000", shop_block)
        self.assertIn("min-curse: 4", shop_block)


class PlayerOptOutRegressionTests(unittest.TestCase):
    def test_opted_out_player_is_not_marked_as_daily_reward_claimed_on_join(self) -> None:
        join = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenJoinListener.java"
        ).read_text(encoding="utf-8")
        disabled_guard = "isHalloweenDisabled(player.getUniqueId())"
        self.assertIn(disabled_guard, join)
        self.assertIn("plugin.getAtmosphere().stop(player)", join)
        self.assertLess(join.index(disabled_guard), join.index("long now = System.currentTimeMillis()"))
        self.assertLess(join.index(disabled_guard), join.index("awardDaily(player, 1, false)"))

    def test_player_opt_out_stops_ambience_and_toggle_can_restore_it(self) -> None:
        atmosphere = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenAtmosphere.java"
        ).read_text(encoding="utf-8")
        command = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenCommand.java"
        ).read_text(encoding="utf-8")
        self.assertIn("public void refreshPlayerPlayback(Player player)", atmosphere)
        self.assertIn("!isAtmosphereEnabled() || isPlayerOptedOut(player)", atmosphere)
        self.assertIn("!isPlayerOptedOut(player)", atmosphere)
        self.assertIn("plugin.getAtmosphere().refreshPlayerPlayback(player)", command)


class HeadlessNickCommandSafetyTests(unittest.TestCase):
    def test_admin_nick_spawn_refuses_encounters_that_would_be_cleaned_immediately(self) -> None:
        source = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenHeadlessNickManager.java"
        ).read_text(encoding="utf-8")
        start = source.index("public boolean spawnForAdmin(Player target)")
        end = source.index("public String statusLine()", start)
        method = source[start:end]
        for required in (
            "!plugin.isEventEnabled()",
            'getBoolean("headless-nick.enabled", true)',
            "!plugin.isEligibleGameplayPlayer(target)",
            "!plugin.isEligibleGameplayWorld(target.getWorld())",
        ):
            self.assertIn(required, method)


class RewardDurabilityRegressionTests(unittest.TestCase):
    def test_daily_return_reward_saves_balance_and_join_timestamp(self) -> None:
        join = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenJoinListener.java"
        ).read_text(encoding="utf-8")
        start = join.index("private void awardDaily(")
        end = join.index("        if (comeback) {", start)
        method = join[start:end]
        self.assertLess(method.index("addFragments("), method.index("recordJoin("))
        self.assertLess(method.index("recordJoin("), method.index("getDataManager().save()"))

    def test_secret_discovery_and_reward_are_persisted_together(self) -> None:
        source = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenSecretDiscoveryManager.java"
        ).read_text(encoding="utf-8")
        start = source.index("private void checkLocations(")
        end = source.index("public void showSecrets(", start)
        method = source[start:end]
        self.assertLess(method.index("markSecretDiscovered("), method.index("addFragments("))
        self.assertLess(method.index("addFragments("), method.index("getDataManager().save()"))

    def test_daily_challenge_claim_is_saved_immediately_and_uses_configured_timezone(self) -> None:
        source = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenChallengeManager.java"
        ).read_text(encoding="utf-8")
        start = source.index("private void claim(")
        end = source.index("        player.sendTitle(", start)
        claim = source[start:end]
        self.assertLess(claim.index("addFragments("), claim.index("markChallengeClaimed("))
        self.assertLess(claim.index("markChallengeClaimed("), claim.index("getDataManager().save()"))
        self.assertIn('getString("season.timezone", "Europe/Prague")', source)
        self.assertIn("LocalDate.now(ZoneId.of", source)

    def test_grave_encounter_payout_is_saved_after_all_participants(self) -> None:
        source = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenGraveEncounterManager.java"
        ).read_text(encoding="utf-8")
        start = source.index("private void finish(Encounter encounter, boolean success, boolean reward)")
        end = source.index("private void removeWaveMobs(", start)
        method = source[start:end]
        self.assertLess(method.index('addFragments(uuid, fragments, "grave-encounter")'), method.index("getDataManager().save()"))


class AdminItemKitRegressionTests(unittest.TestCase):
    def test_admin_item_kit_does_not_append_shop_candies_a_second_time(self) -> None:
        source = (
            ROOT / "src/main/java/cz/halloween/core/HalloweenRewardManager.java"
        ).read_text(encoding="utf-8")
        start = source.index("public List<ItemStack> createAdminRewardItems()")
        end = source.index("public ItemStack createAdminRewardItem(", start)
        method = source[start:end]
        self.assertIn('getConfigurationSection("rewards.shop")', method)
        self.assertIn('findByItemsAdderId("warriorland_halloween:stev_apple_leaves")', method)
        self.assertNotIn('startsWith("candy_stev_")', method)
        self.assertEqual(method.count("createRegisteredItem(appleLeaves.internalId(), 1)"), 1)


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
        self.assertEqual(len(imported), 15)
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
                self.assertEqual(len(shipped_source_manifest["assets"]), 15)
                self.assertEqual(len({entry["source_path"] for entry in shipped_source_manifest["assets"]}), 14)
                metadata = json.loads(archive.read("warriorland_halloween/BUILD_METADATA.json"))
                self.assertEqual(metadata["minecraft_version"], "1.21.10")
                self.assertTrue(all(info.date_time == (2020, 1, 1, 0, 0, 0) for info in archive.infolist()))


if __name__ == "__main__":
    unittest.main(verbosity=2)
