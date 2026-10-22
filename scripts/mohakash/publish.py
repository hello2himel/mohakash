"""Build the public API artifact and the RSS feed from the archive.

The generated documents are the only thing that is published on GitHub Pages
and the only thing the website and the Android app consume.  Every file is
written deterministically so that re-running the publisher produces byte
identical output (which keeps the scheduled workflow from creating empty
commits).
"""

from __future__ import annotations

import datetime as _dt
import json
import os
import tempfile
import xml.sax.saxutils as saxutils
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

from .archive import ArchiveStore
from .config import SiteConfig
from .schema import SCHEMA_VERSION, ApodRecord, index_entry, is_picture

RSS_ITEM_LIMIT_DEFAULT = 30


@dataclass
class BuildResult:
    """What the publisher produced."""

    written: list[str] = field(default_factory=list)
    unchanged: list[str] = field(default_factory=list)
    record_count: int = 0
    latest: ApodRecord | None = None

    @property
    def changed(self) -> bool:
        return bool(self.written)

    def as_dict(self) -> dict[str, Any]:
        return {
            "written": list(self.written),
            "unchanged": list(self.unchanged),
            "record_count": self.record_count,
            "latest_date": self.latest.date if self.latest else None,
        }


def _utc_now_iso() -> str:
    return _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def data_timestamp(records: Iterable[ApodRecord]) -> str:
    """A timestamp that only changes when the data changes.

    The published documents must be byte identical when nothing was
    refreshed, otherwise every scheduled run would produce a diff and an
    unnecessary deployment.  The newest ``updated_at`` of the archive is used
    instead of the current time.
    """
    stamps = [record.updated_at for record in records if record.updated_at]
    return max(stamps) if stamps else _utc_now_iso()


def _write_if_changed(path: Path, content: str, result: BuildResult) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    relative = str(path)
    if path.exists() and path.read_text(encoding="utf-8") == content:
        result.unchanged.append(relative)
        return
    _atomic_write(path, content)
    result.written.append(relative)


def _atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    handle = tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=str(path.parent), prefix=path.name, suffix=".tmp", delete=False
    )
    try:
        with handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        # NamedTemporaryFile creates the file with 0600; the published data has
        # to be readable by the web server.
        os.chmod(handle.name, 0o644)
        os.replace(handle.name, path)
    except BaseException:
        try:
            os.unlink(handle.name)
        except OSError:
            pass
        raise


def _dump(payload: Any) -> str:
    return json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=False) + "\n"


# --------------------------------------------------------------------------
# documents
# --------------------------------------------------------------------------
def build_latest_document(
    record: ApodRecord,
    *,
    state: dict[str, Any],
    config: SiteConfig,
    now: str | None = None,
) -> dict[str, Any]:
    """``latest.json`` -- the newest record plus synchronisation metadata.

    The record fields are flattened into the document so that clients can use
    ``latest.title`` / ``latest.date`` directly.
    """
    document = record.to_dict()
    document["generated_at"] = now or _utc_now_iso()
    document["last_successful_sync"] = state.get("last_successful_sync")
    document["latest_available_date"] = record.date
    document["record_count"] = int(state.get("record_count") or 0)
    document["source_status"] = state.get("source_status", "unknown")
    document["source"] = record.source
    document["api_base_url"] = config.api_base_url
    return document


def build_latest_image_document(
    record: ApodRecord,
    *,
    state: dict[str, Any],
    config: SiteConfig,
    latest_entry_date: str | None = None,
    now: str | None = None,
) -> dict[str, Any]:
    """``latest-image.json`` -- the newest entry that is a still picture.

    Clients that hide video entries use this document to show the most recent
    picture instead of the most recent entry.  It has the same shape as
    ``latest.json``.
    """
    document = build_latest_document(record, state=state, config=config, now=now)
    document["latest_entry_date"] = latest_entry_date
    document["selection"] = "newest picture entry (media_type=image, image_status=ok)"
    return document


def build_index_document(
    records: Iterable[ApodRecord],
    *,
    config: SiteConfig,
    now: str | None = None,
) -> dict[str, Any]:
    """``archive-index.json`` -- a compact, newest-first list of every entry."""
    ordered = sorted(records, key=lambda record: record.date, reverse=True)
    entries = [
        index_entry(record, f"archive/{record.year}.json") for record in ordered
    ]
    return {
        "schema_version": SCHEMA_VERSION,
        "generated_at": now or _utc_now_iso(),
        "count": len(entries),
        "api_base_url": config.api_base_url,
        "entries": entries,
    }


def build_year_document(
    year: int,
    records: Iterable[ApodRecord],
    *,
    now: str | None = None,
) -> dict[str, Any]:
    """``archive/YYYY.json`` -- the full records of one calendar year."""
    ordered = sorted(records, key=lambda record: record.date, reverse=True)
    return {
        "schema_version": SCHEMA_VERSION,
        "year": year,
        "generated_at": now or _utc_now_iso(),
        "count": len(ordered),
        "records": [record.to_dict() for record in ordered],
    }


def build_status_document(
    records: list[ApodRecord],
    *,
    state: dict[str, Any],
    config: SiteConfig,
    now: str | None = None,
) -> dict[str, Any]:
    """``status.json`` -- schema version, sync state and archive statistics."""
    dates = [record.date for record in records]
    years = sorted({record.year for record in records})
    pictures = [record.date for record in records if is_picture(record)]
    return {
        "schema_version": SCHEMA_VERSION,
        "generated_at": now or _utc_now_iso(),
        "last_successful_sync": state.get("last_successful_sync"),
        "latest_available_date": max(dates) if dates else None,
        "latest_image_date": max(pictures) if pictures else None,
        "archive_start_date": min(dates) if dates else None,
        "archive_end_date": max(dates) if dates else None,
        "record_count": len(records),
        "picture_count": len(pictures),
        "source_status": state.get("source_status", "unknown"),
        "source_name": "NASA APOD",
        "source_endpoint": state.get("source_endpoint"),
        "years": years,
        "api_base_url": config.api_base_url,
        "website_base_url": config.website_base_url,
    }


def build_feed(
    records: list[ApodRecord],
    *,
    config: SiteConfig,
    limit: int = RSS_ITEM_LIMIT_DEFAULT,
    now: str | None = None,
) -> str:
    """Build the RSS 2.0 feed with the newest ``limit`` entries."""
    ordered = sorted(records, key=lambda record: record.date, reverse=True)[:limit]
    generated = now or data_timestamp(records)
    site_link = config.website_base_url or config.pages.base_url
    feed_link = config.feed_url

    parts: list[str] = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">',
        "  <channel>",
        f"    <title>{saxutils.escape(config.project_name)}</title>",
        f"    <link>{saxutils.escape(site_link)}</link>",
        f"    <description>{saxutils.escape(config.project_tagline or 'Daily astronomy picture of the day.')}</description>",
        "    <language>en</language>",
        f"    <lastBuildDate>{saxutils.escape(_rfc822(_parse_iso(generated)))}</lastBuildDate>",
        f'    <atom:link href="{saxutils.escape(feed_link)}" rel="self" type="application/rss+xml" />',
        "    <copyright>Image rights belong to their creators. Not affiliated with or endorsed by NASA.</copyright>",
    ]

    for record in ordered:
        title = saxutils.escape(record.title)
        link = saxutils.escape(record.apod_url or site_link)
        pub_date = _rfc822(_dt.datetime.strptime(record.date, "%Y-%m-%d").replace(tzinfo=_dt.timezone.utc))
        description_parts = []
        if record.explanation:
            description_parts.append(saxutils.escape(_shorten(record.explanation, 600)))
        if record.copyright:
            description_parts.append(f"Credit: {saxutils.escape(record.copyright)}")
        description = "<br/>".join(description_parts)
        guid = saxutils.escape(record.apod_url or f"{feed_link}#{record.date}")
        parts.extend(
            [
                "    <item>",
                f"      <title>{title}</title>",
                f"      <link>{link}</link>",
                f"      <guid isPermaLink=\"false\">{guid}</guid>",
                f"      <pubDate>{pub_date}</pubDate>",
                f"      <description>{description}</description>",
                "    </item>",
            ]
        )

    parts.append("  </channel>")
    parts.append("</rss>")
    return "\n".join(parts) + "\n"


def _rfc822(moment: _dt.datetime) -> str:
    return moment.strftime("%a, %d %b %Y %H:%M:%S GMT")


def _shorten(text: str, limit: int) -> str:
    """Cut a text at a word boundary and mark that it was cut."""
    flattened = " ".join(text.split())
    if len(flattened) <= limit:
        return flattened
    cut = flattened[:limit]
    space = cut.rfind(" ")
    if space > limit // 2:
        cut = cut[:space]
    return cut.rstrip(" ,;:.") + "…"


def _parse_iso(value: str) -> _dt.datetime:
    """Parse an ISO-8601 timestamp, falling back to the current time."""
    try:
        parsed = _dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (TypeError, ValueError):
        return _dt.datetime.now(_dt.timezone.utc)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=_dt.timezone.utc)
    return parsed


# --------------------------------------------------------------------------
# publisher
# --------------------------------------------------------------------------
def build_public_api(
    *,
    data_root: str | Path,
    output_root: str | Path,
    config: SiteConfig,
    state: dict[str, Any] | None = None,
    now: str | None = None,
) -> BuildResult:
    """Regenerate every public document from the archive.

    ``data_root`` is the directory that contains ``records/``.  Only files
    whose content actually changed are written, so a scheduled run without new
    data produces no diff at all.
    """
    state = state or {}
    output_root = Path(output_root)
    api_root = output_root / "api" / config.api_version
    store = ArchiveStore(data_root)
    result = BuildResult()

    records = list(store.iter_records())
    records.sort(key=lambda record: record.date, reverse=True)
    result.record_count = len(records)
    if not records:
        raise ValueError("refusing to publish an empty archive")
    result.latest = records[0]

    # The timestamp is derived from the data, not from the clock, so an
    # unchanged archive always produces byte identical files.
    timestamp = now or data_timestamp(records)

    latest_doc = build_latest_document(records[0], state=state, config=config, now=timestamp)
    _write_if_changed(api_root / "latest.json", _dump(latest_doc), result)

    # The newest entry that is a still picture.  Clients that hide video
    # entries show this one instead of the newest entry.
    pictures = [record for record in records if is_picture(record)]
    if pictures:
        latest_image_doc = build_latest_image_document(
            pictures[0],
            state=state,
            config=config,
            latest_entry_date=records[0].date,
            now=timestamp,
        )
        _write_if_changed(api_root / "latest-image.json", _dump(latest_image_doc), result)

    index_doc = build_index_document(records, config=config, now=timestamp)
    _write_if_changed(api_root / "archive-index.json", _dump(index_doc), result)

    status_doc = build_status_document(records, state=state, config=config, now=timestamp)
    _write_if_changed(api_root / "status.json", _dump(status_doc), result)

    by_year: dict[int, list[ApodRecord]] = {}
    for record in records:
        by_year.setdefault(record.year, []).append(record)
    for year, year_records in sorted(by_year.items()):
        year_doc = build_year_document(year, year_records, now=timestamp)
        _write_if_changed(api_root / "archive" / f"{year}.json", _dump(year_doc), result)

    feed = build_feed(records, config=config, limit=config.feed_entry_count, now=timestamp)
    _write_if_changed(output_root / "feed.xml", feed, result)

    return result


def assemble_api_site(*, data_root: str | Path, site_root: str | Path, api_version: str = "v1") -> list[Path]:
    """Assemble the GitHub Pages artifact.

    The artifact contains **only** the public data: ``api/<version>/...`` and
    ``feed.xml``.  No source code, no tests, no workflow files.
    """
    data_root = Path(data_root)
    site_root = Path(site_root)
    site_root.mkdir(parents=True, exist_ok=True)

    written: list[Path] = []

    nojekyll = site_root / ".nojekyll"
    nojekyll.write_text("", encoding="utf-8")
    written.append(nojekyll)

    api_target = site_root / "api"
    if api_target.exists():
        _remove_tree(api_target)
    api_source = data_root / "api"
    if api_source.is_dir():
        _copy_tree(api_source, api_target)
        written.append(api_target)

    feed_source = data_root / "feed.xml"
    if feed_source.is_file():
        feed_target = site_root / "feed.xml"
        feed_target.write_text(feed_source.read_text(encoding="utf-8"), encoding="utf-8")
        written.append(feed_target)

    return written


def _copy_tree(source: Path, target: Path) -> None:
    target.mkdir(parents=True, exist_ok=True)
    for item in sorted(source.iterdir()):
        if item.is_dir():
            _copy_tree(item, target / item.name)
        else:
            (target / item.name).write_text(item.read_text(encoding="utf-8"), encoding="utf-8")


def _remove_tree(path: Path) -> None:
    if not path.exists():
        return
    for item in sorted(path.iterdir(), reverse=True):
        if item.is_dir():
            _remove_tree(item)
        else:
            item.unlink()
    path.rmdir()
