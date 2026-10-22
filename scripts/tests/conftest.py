"""Shared test fixtures for the Mohakash data pipeline."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = SCRIPTS_DIR.parent
FIXTURES_DIR = Path(__file__).resolve().parent / "fixtures"

if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from mohakash.config import load_config  # noqa: E402


def load_fixture(name: str):
    """Load a saved NASA payload."""
    with (FIXTURES_DIR / name).open("r", encoding="utf-8") as handle:
        return json.load(handle)


@pytest.fixture()
def fixture_image() -> dict:
    return load_fixture("raw_image_2026-10-22.json")


@pytest.fixture()
def fixture_video() -> dict:
    return load_fixture("raw_video_2026-10-18.json")


@pytest.fixture()
def fixture_old_placeholder() -> dict:
    return load_fixture("raw_old_placeholder_1995-06-16.json")


@pytest.fixture()
def fixture_old_image() -> dict:
    return load_fixture("raw_image_2000-06-15.json")


@pytest.fixture()
def fixture_page() -> list:
    return load_fixture("raw_page_1.json")


@pytest.fixture()
def site_config():
    return load_config(REPO_ROOT / "config" / "site.json")
