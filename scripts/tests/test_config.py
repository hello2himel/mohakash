"""Configuration and Netlify proxy tests."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

try:  # Python 3.11+
    import tomllib
except ModuleNotFoundError:  # pragma: no cover
    tomllib = None  # type: ignore[assignment]

from mohakash.config import SiteConfig, load_config

REPO_ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = REPO_ROOT / "config" / "site.json"


def test_config_file_exists():
    assert CONFIG_PATH.is_file()


def test_project_is_named_mohakash(site_config: SiteConfig):
    assert site_config.project_name == "Mohakash"


def test_pages_base_url_for_a_project_site(site_config: SiteConfig):
    assert site_config.pages.base_url == "https://hello2himel.github.io/mohakash"
    assert site_config.pages.path_prefix == "/mohakash"
    assert site_config.api_base_url == "https://hello2himel.github.io/mohakash/api/v1"


def test_pages_base_url_for_a_user_site(tmp_path):
    path = tmp_path / "site.json"
    path.write_text(
        json.dumps(
            {
                "project_name": "Mohakash",
                "github": {"owner": "someone", "repo": "mohakash", "pages_type": "user"},
                "website": {"base_url": "https://example.netlify.app"},
                "api": {"version": "v1"},
                "android": {"package": "app.mohakash.apod"},
            }
        ),
        encoding="utf-8",
    )
    config = load_config(path)
    assert config.pages.base_url == "https://someone.github.io"
    assert config.pages.path_prefix == ""
    assert config.api_base_url == "https://someone.github.io/api/v1"


def test_custom_domain_wins(tmp_path):
    path = tmp_path / "site.json"
    path.write_text(
        json.dumps(
            {
                "project_name": "Mohakash",
                "github": {"owner": "someone", "repo": "mohakash", "custom_domain": "apod.example.org"},
                "website": {"base_url": "https://apod.example.org"},
                "api": {"version": "v1"},
                "android": {"package": "app.mohakash.apod"},
            }
        ),
        encoding="utf-8",
    )
    config = load_config(path)
    assert config.pages.base_url == "https://apod.example.org"


def test_missing_owner_is_rejected(tmp_path):
    path = tmp_path / "site.json"
    path.write_text(json.dumps({"github": {"repo": "x"}}), encoding="utf-8")
    with pytest.raises(ValueError):
        load_config(path)


def test_invalid_pages_type_is_rejected(tmp_path):
    path = tmp_path / "site.json"
    path.write_text(
        json.dumps({"github": {"owner": "a", "repo": "b", "pages_type": "nope"}}),
        encoding="utf-8",
    )
    with pytest.raises(ValueError):
        load_config(path)


# --------------------------------------------------------------------------
# Netlify
# --------------------------------------------------------------------------
NETLIFY_TOML = REPO_ROOT / "netlify.toml"


@pytest.mark.skipif(tomllib is None, reason="tomllib is not available")
def test_netlify_build_configuration():
    if not NETLIFY_TOML.exists():
        pytest.skip("netlify.toml has not been created yet")
    with NETLIFY_TOML.open("rb") as handle:
        data = tomllib.load(handle)

    build = data.get("build", {})
    assert build.get("base") == "web"
    assert build.get("publish") == "dist"
    assert "npm run build" in str(build.get("command"))


@pytest.mark.skipif(tomllib is None, reason="tomllib is not available")
def test_netlify_proxies_the_api_to_github_pages(site_config: SiteConfig):
    if not NETLIFY_TOML.exists():
        pytest.skip("netlify.toml has not been created yet")
    with NETLIFY_TOML.open("rb") as handle:
        data = tomllib.load(handle)

    redirects = data.get("redirects", [])
    api = next((r for r in redirects if r.get("from") == "/api/apod/v1/*"), None)
    assert api is not None, "the API proxy rule is missing"
    assert api.get("status") == 200
    assert api.get("force") is True
    assert api.get("to") == f"{site_config.pages.base_url}/api/v1/:splat"

    feed = next((r for r in redirects if r.get("from") == "/feed.xml"), None)
    assert feed is not None, "the RSS proxy rule is missing"
    assert feed.get("to") == f"{site_config.pages.base_url}/feed.xml"


@pytest.mark.skipif(tomllib is None, reason="tomllib is not available")
def test_netlify_config_matches_the_project_configuration(site_config: SiteConfig):
    """``netlify.toml`` is generated from ``config/site.json``."""
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from render_netlify_config import TEMPLATE_PATH, render

    if not TEMPLATE_PATH.exists():
        pytest.skip("netlify.toml.template has not been created yet")
    template = TEMPLATE_PATH.read_text(encoding="utf-8")
    expected = render(template, site_config.pages.base_url)
    current = NETLIFY_TOML.read_text(encoding="utf-8")
    assert current == expected, "netlify.toml is stale, run scripts/render_netlify_config.py"


@pytest.mark.skipif(tomllib is None, reason="tomllib is not available")
def test_netlify_cache_headers_keep_the_latest_document_fresh(site_config: SiteConfig):
    if not NETLIFY_TOML.exists():
        pytest.skip("netlify.toml has not been created yet")
    with NETLIFY_TOML.open("rb") as handle:
        data = tomllib.load(handle)

    headers = data.get("headers", [])
    rules = {rule.get("for"): rule for rule in headers}
    assert "/api/apod/v1/latest.json" in rules
    assert "/api/apod/v1/status.json" in rules
    assert "/api/apod/v1/archive/*" in rules

    def max_age(rule) -> int:
        value = rule["values"]["Cache-Control"]
        return int(value.split("max-age=")[1].split(",")[0])

    assert max_age(rules["/api/apod/v1/latest.json"]) <= 600
    assert max_age(rules["/api/apod/v1/status.json"]) <= 600
    assert max_age(rules["/api/apod/v1/archive/*"]) >= 3600
