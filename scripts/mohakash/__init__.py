"""Mohakash APOD data pipeline.

Modules:
    config      -- loads ``config/site.json`` and derives owner specific URLs.
    schema      -- the normalized, versioned record schema shared by web and Android.
    nasa_adapter-- the only module that knows about NASA's WordPress APOD endpoint.
    normalize   -- turns raw NASA payloads into normalized records.
    archive     -- the on-disk archive store (``data/records/YYYY.json``).
    publish     -- builds the public API artifact and the RSS feed.
"""

from __future__ import annotations

__all__ = ["config", "schema", "nasa_adapter", "normalize", "archive", "publish"]
