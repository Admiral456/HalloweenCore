#!/usr/bin/env python3
"""Assemble the complete server handover ZIP from CI-verified build artifacts."""
from __future__ import annotations

import hashlib
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
TARGET = ROOT / "target"
BUNDLE = TARGET / "HalloweenCore-Server-ReadyToDrop"

FILES = {
    "plugin/HalloweenCore.jar": TARGET / "HalloweenCore.jar",
    "itemsadder/WarriorLand-Halloween-ItemsAdder.zip": TARGET / "WarriorLand-Halloween-ItemsAdder.zip",
    "shader/WarriorLand-Halloween-Iris-Shader.zip": TARGET / "WarriorLand-Halloween-Iris-Shader.zip",
    "client/HalloweenCore-Client-1.21.10.jar": TARGET / "HalloweenCore-Client-1.21.10.jar",
    "mobs/HalloweenCore-Special-Mobs-ModelEngine.zip": TARGET / "HalloweenCore-Special-Mobs-ModelEngine.zip",
    "portable/HalloweenCore-Portable-Relics.zip": TARGET / "HalloweenCore-Portable-Relics.zip",
}

INSTALL = """HALLOWEENCORE — SERVER READY TO DROP
=====================================

Target versions
- Paper/Purpur 1.21.10
- Java 21
- ItemsAdder 4.0.18+ for Batwing Elytra equipment texture and its custom resource-pack model
- ModelEngine R4.1.1+ and MythicMobs compatible with your 1.21.10 server for the separately supplied animated mob pack
- WorldGuard is optional; Goblin King fortress building fails closed if installed protection checks cannot be verified.

INSTALL ORDER
1. Back up the complete server and its world/configuration before updating anything.
2. Stop the server. Install plugin/HalloweenCore.jar into plugins/. Start once if you need to create the configuration, then stop again.
3. Review plugins/HalloweenCore/config.yml. Merge your existing config with the shipped defaults: do not blindly replace your existing config or data.yml.
4. Extract itemsadder/WarriorLand-Halloween-ItemsAdder.zip to plugins/ItemsAdder/contents/ while preserving all existing content namespaces. This ZIP contains the warriorland_halloween namespace and generated source/config/assets.
5. Start the server with ItemsAdder enabled. Run /iazip and verify the generated pack is hosted and clients accept it. Shader pack is a separate client-side Iris profile; a Bukkit plugin cannot force every client to enable it.
6. Install shader/WarriorLand-Halloween-Iris-Shader.zip on compatible clients through Iris. Choose a supported quality profile. The normal resource pack works separately, subject to client download/acceptance.
7. For custom mob animations, install mob pack/HalloweenCore-Special-Mobs-ModelEngine.zip according to its README. ModelEngine/MythicMobs support and exact server-compatible builds are required; the mob model pack is not a replacement for the plugin JAR.
8. The portable/ folder is a separate independently namespaced relic ZIP for another compatible server; do not install both the portable plugin and main HalloweenCore on the same server because they share the relic engine behaviors. On this server, the main plugin already provides the relic abilities.
9. Keep the client companion JAR optional. Install it only on compatible client profiles; it does not replace server-side HalloweenCore.

ADMIN TEST COMMANDS
- /halloween debug — integration/config diagnostics
- /halloween mob spawn <mob-id> [player] — spawn a regular special mob (admin)
- /halloween nick spawn <player> /halloween nick test <player> — test Headless Nick (admin)
- /halloween goblin status
- /halloween goblin arena <world> <x> <y> <z> then /halloween goblin confirm — dedicated fortress preflight/build
- /halloween goblin test — test encounter path; depending on configuration, arena state may be required
- /halloween structures status — natural village/graveyard diagnostics
- /halloween structures spawn haunted-village <player> — queue a safe manual Haunted Village attempt in the target player's loaded chunk
- /halloween structures spawn cursed-graveyard <player> — queue a safe manual Cursed Graveyard attempt
- /halloween reload — reload server configuration and request an ItemsAdder pack rebuild where ItemsAdder is enabled
Exact command availability should be checked with /halloween help on the installed build. All build/arena/spawn testing must be performed in a backup test world first.

ARENA SAFETY
- Vampire King and Goblin King are separate encounters and their arena state is not interchangeable.
- The Goblin King arena builder requires a preflight, clear area, loaded chunks, height validity and explicit confirmation. It should not be run in a production world without a backup.
- Do not use /halloween buildvampirearena in a production world until its own preflight has been inspected and the workflow tested.

RESOURCE-PACK / SHADER LIMITS
- Resource pack changes require /iazip plus a working pack hosting/acceptance path. A server-side reload cannot force an already connected client to switch an Iris shader pack or fully hot-reload all client assets.
- Client model rendering, elytra folded/gliding appearance, mob animations and shader appearance are not asserted as verified by server CI alone.
- Paper plugin logic and custom textures are separate from ModelEngine/MythicMobs runtime support. Consult each included pack README before turning on features.

UNINSTALL
1. Stop the server and back up config/data.
2. Remove plugins/HalloweenCore.jar; preserve the HalloweenCore config/data folder until you decide whether you need player progress.
3. Remove only plugins/ItemsAdder/contents/warriorland_halloween/ if it has no locally customized assets; do not delete other ItemsAdder namespaces.
4. Remove the separately installed ModelEngine/MythicMobs entries from this pack only if you manually copied them into shared folders.
5. Regenerate/refresh the merged resource pack and tell clients to download the refreshed assets. Restore your server backup if any custom changes were overwritten.

This is an assembled release, not a claim that an in-game visual test has been executed. See TEST-REPORT.txt for CI/artifact checks and remaining NOT TESTED items.
"""

TEST_REPORT = """HALLOWEENCORE BUILD / RELEASE TEST REPORT
========================================

Report generated during the server-ready ZIP step of GitHub Actions.

CI GATES REQUIRED BEFORE THIS REPORT CAN BE PACKAGED
- Generator execution and PNG/JSON/model validators: PASS if this packaging step ran.
- Main plugin Maven package: PASS if this packaging step ran.
- Portable Relics Maven package and ZIP inventory check: PASS if this packaging step ran.
- ItemsAdder content ZIP and Iris shader ZIP inventory check: PASS if this packaging step ran.
- Special mob model ZIP inventory check: PASS if this packaging step ran.

LIVE / CLIENT TESTS: NOT TESTED
- No live Minecraft server/client session is part of GitHub Actions.
- Do not treat model geometry validation as proof that an item or mob appears as expected in-game.
- Not verified here: worn/folded/gliding Batwing Elytra visual; cowl front/back fit; actual ItemsAdder pack delivery; ModelEngine animations in the installed renderer; combat feel/damage values; WorldGuard integration against a real region; and arena creation inside a real-world backup.
- Before public release, execute the clean test checklist in the portable and mob pack READMEs, capture screenshots/logs, and append actual PASS/FAIL/BLOCKED results.

Historical CI status is available from the build workflow:
https://github.com/Admiral456/HalloweenCore/actions
"""

def main() -> None:
    if BUNDLE.exists():
        shutil.rmtree(BUNDLE)
    BUNDLE.mkdir(parents=True)
    copied = []
    missing = []
    for relative, source in FILES.items():
        if not source.is_file():
            missing.append(str(source.relative_to(ROOT)))
            continue
        destination = BUNDLE / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        copied.append((relative, destination))
    if missing:
        raise FileNotFoundError("Missing build outputs: " + ", ".join(missing))

    config = ROOT / "src" / "main" / "resources" / "config.yml"
    plugin_yml = ROOT / "src" / "main" / "resources" / "plugin.yml"
    shutil.copy2(config, BUNDLE / "config-defaults.yml")
    shutil.copy2(plugin_yml, BUNDLE / "plugin.yml")
    (BUNDLE / "INSTALL.txt").write_text(INSTALL, encoding="utf-8")
    (BUNDLE / "TEST-REPORT.txt").write_text(TEST_REPORT, encoding="utf-8")

    lines = ["HalloweenCore Server Ready To Drop", "", "Included files and SHA-256:"]
    for path in sorted(BUNDLE.rglob("*")):
        if path.is_file() and path.name not in {"MANIFEST.txt", "CHECKSUMS.sha256"}:
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            lines.append(f"{digest}  {path.relative_to(BUNDLE).as_posix()}")
    (BUNDLE / "CHECKSUMS.sha256").write_text("\n".join(lines[2:]) + "\n", encoding="utf-8")
    manifest = ["HalloweenCore Server Ready To Drop", "", "Included file inventory:"]
    manifest.extend(sorted(p.relative_to(BUNDLE).as_posix() for p in BUNDLE.rglob("*") if p.is_file()))
    (BUNDLE / "MANIFEST.txt").write_text("\n".join(manifest) + "\n", encoding="utf-8")
    print(f"Assembled server-ready package with {len(manifest)-3} files in {BUNDLE.relative_to(ROOT)}")
    for rel, _ in copied:
        print(f"  {rel}")

if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
