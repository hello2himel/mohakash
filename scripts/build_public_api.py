#!/usr/bin/env python3
"""Regenerate the public JSON API and the RSS feed from the stored archive.

This is a thin wrapper around :func:`mohakash.publish.build_public_api` so
that it can be called directly from CI::

    python3 scripts/build_public_api.py
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mohakash.archive import load_sync_state  # noqa: E402
from mohakash.config import REPO_ROOT, load_config  # noqa: E402
from mohakash.publish import build_public_api  # noqa: E402


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build the public Mohakash API")
    parser.add_argument("--data-root", default=str(REPO_ROOT / "data"))
    parser.add_argument("--output-root", default=None)
    parser.add_argument("--config", default=str(REPO_ROOT / "config" / "site.json"))
    args = parser.parse_args(argv)

    data_root = Path(args.data_root)
    output_root = Path(args.output_root) if args.output_root else data_root
    state = load_sync_state(data_root / "sync-state.json")

    result = build_public_api(
        data_root=data_root,
        output_root=output_root,
        config=load_config(args.config),
        state=state,
    )
    print(json.dumps(result.as_dict(), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
