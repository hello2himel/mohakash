"""Project configuration.

Everything that depends on the GitHub owner, the repository name or a custom
domain is read from ``config/site.json`` so that a fork only has to change that
single file.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG_PATH = REPO_ROOT / "config" / "site.json"


@dataclass(frozen=True)
class PagesConfig:
    """Where the public JSON API is published."""

    owner: str
    repo: str
    pages_type: str  # "project" or "user"
    custom_domain: str | None = None

    @property
    def base_url(self) -> str:
        """Absolute base URL of the published Pages site, without a trailing slash."""
        if self.custom_domain:
            return f"https://{self.custom_domain}".rstrip("/")
        if self.pages_type == "user":
            return f"https://{self.owner}.github.io"
        return f"https://{self.owner}.github.io/{self.repo}"

    @property
    def path_prefix(self) -> str:
        """URL path prefix of the published site ("" for user sites)."""
        if self.custom_domain:
            return ""
        if self.pages_type == "user":
            return ""
        return f"/{self.repo}"


@dataclass(frozen=True)
class SiteConfig:
    """Parsed ``config/site.json``."""

    project_name: str
    project_tagline: str
    pages: PagesConfig
    website_base_url: str
    api_version: str
    same_origin_prefix: str
    latest_max_age_seconds: int
    archive_max_age_seconds: int
    feed_entry_count: int
    android_package: str
    android_api_base_url_fallbacks: tuple[str, ...]
    raw: dict[str, Any] = field(default_factory=dict)

    @property
    def api_base_url(self) -> str:
        """Absolute base URL of the published JSON API (no trailing slash)."""
        return f"{self.pages.base_url}/api/{self.api_version}"

    @property
    def feed_url(self) -> str:
        return f"{self.pages.base_url}/feed.xml"


def load_config(path: str | Path | None = None) -> SiteConfig:
    """Load and validate the project configuration."""
    config_path = Path(path) if path else DEFAULT_CONFIG_PATH
    if not config_path.is_absolute():
        config_path = (REPO_ROOT / config_path).resolve()
    with config_path.open("r", encoding="utf-8") as handle:
        raw = json.load(handle)

    github = raw.get("github") or {}
    website = raw.get("website") or {}
    api = raw.get("api") or {}
    android = raw.get("android") or {}

    owner = str(github.get("owner") or "").strip()
    repo = str(github.get("repo") or "").strip()
    if not owner or not repo:
        raise ValueError(
            "config/site.json must define github.owner and github.repo "
            f"(found owner={owner!r}, repo={repo!r})"
        )

    pages_type = str(github.get("pages_type") or "project").strip().lower()
    if pages_type not in ("project", "user"):
        raise ValueError(f"github.pages_type must be 'project' or 'user', got {pages_type!r}")

    custom_domain = github.get("custom_domain")
    if custom_domain:
        custom_domain = str(custom_domain).strip().lstrip("https://").strip("/")

    return SiteConfig(
        project_name=str(raw.get("project_name") or "Mohakash"),
        project_tagline=str(raw.get("project_tagline") or ""),
        pages=PagesConfig(
            owner=owner,
            repo=repo,
            pages_type=pages_type,
            custom_domain=custom_domain,
        ),
        website_base_url=str(website.get("base_url") or "").rstrip("/"),
        api_version=str(api.get("version") or "v1"),
        same_origin_prefix=str(api.get("same_origin_prefix") or "/api/apod/v1"),
        latest_max_age_seconds=int(api.get("latest_max_age_seconds") or 300),
        archive_max_age_seconds=int(api.get("archive_max_age_seconds") or 86400),
        feed_entry_count=int(api.get("feed_entry_count") or 30),
        android_package=str(android.get("package") or "app.mohakash.apod"),
        android_api_base_url_fallbacks=tuple(
            str(url).rstrip("/") for url in (android.get("api_base_url_fallbacks") or [])
        ),
        raw=raw,
    )
