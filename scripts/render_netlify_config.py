#!/usr/bin/env python3
"""Render ``netlify.toml`` from ``netlify.toml.template``.

``config/site.json`` is the single place where the GitHub owner, the
repository name and the custom domain live.  This script keeps the Netlify
configuration in sync with it, so moving the project to another account only
requires editing one JSON file.

Usage::

    python3 scripts/render_netlify_config.py          # write netlify.toml
    python3 scripts/render_netlify_config.py --check  # fail if it is stale
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mohakash.config import REPO_ROOT, load_config  # noqa: E402

TEMPLATE_PATH = REPO_ROOT / "netlify.toml.template"
OUTPUT_PATH = REPO_ROOT / "netlify.toml"


def render(template: str, pages_base_url: str) -> str:
    return template.replace("{{PAGES_BASE_URL}}", pages_base_url.rstrip("/"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Render netlify.toml from the project configuration")
    parser.add_argument("--config", default=str(REPO_ROOT / "config" / "site.json"))
    parser.add_argument("--template", default=str(TEMPLATE_PATH))
    parser.add_argument("--output", default=str(OUTPUT_PATH))
    parser.add_argument("--check", action="store_true", help="only report whether the output is up to date")
    args = parser.parse_args(argv)

    config = load_config(args.config)
    template = Path(args.template).read_text(encoding="utf-8")
    rendered = render(template, config.pages.base_url)
    output = Path(args.output)

    if args.check:
        current = output.read_text(encoding="utf-8") if output.exists() else ""
        if current != rendered:
            print(
                "netlify.toml is out of date. Run: python3 scripts/render_netlify_config.py",
                file=sys.stderr,
            )
            return 1
        print("netlify.toml is up to date")
        return 0

    if output.exists() and output.read_text(encoding="utf-8") == rendered:
        print("netlify.toml is already up to date")
        return 0
    output.write_text(rendered, encoding="utf-8")
    print(f"wrote {output} (API base: {config.pages.base_url})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
