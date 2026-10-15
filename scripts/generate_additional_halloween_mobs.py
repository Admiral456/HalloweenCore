#!/usr/bin/env python3
"""Fail-closed guard for artist-protected Halloween mob asset IDs.

The former prototype wrote generated/recolored files to the official ModelEngine
paths for Void Reaper, Frost Stalker and Nightmare. The content registry explicitly
marks those IDs as externally authored and prohibits generation/recoloring. Do not
restore output-writing behavior here unless the asset policy and IDs are changed
through a reviewed project decision. Create future project-owned variants under
new, unprotected IDs and register their files explicitly.
"""
from __future__ import annotations

import sys


def main() -> int:
    print(
        "Generation blocked: halloween_void_reaper, halloween_frost_stalker, and "
        "halloween_nightmare are protected asset IDs. No files were written. "
        "Use separately approved project-owned IDs or obtain explicit asset permission.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
