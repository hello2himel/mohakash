"""End-to-end tests for the command line interface.

The network is replaced by a fake adapter, so these tests exercise the real
command implementations without calling NASA.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import sync_apod  # noqa: E402
from mohakash.nasa_adapter import SourceError  # noqa: E402
from test_archive import make_record  # noqa: E402


class FakeAdapter:
    """Stands in for :class:`NasaAdapter`."""

    def __init__(self, records=None, fail=False):
        self.base_url = "https://example.invalid/wp-json/wp/v2/apod-basic"
        self.records = records or []
        self.fail = fail
        self.fetched_pages = 0

    def fetch_page(self, page=1, per_page=25):
        if self.fail:
            raise SourceError("the source is unavailable")
        self.fetched_pages += 1
        return list(self.records)

    def fetch_date(self, date):
        if self.fail:
            raise SourceError("the source is unavailable")
        return None

    def find_page_for_date(self, target):
        return 1

    def fetch_total_pages(self, per_page=25):
        return 1


def make_args(tmp_path, monkeypatch, adapter, **overrides):
    class Args:
        data_root = str(tmp_path / "data")
        output_root = None
        config = str(Path(__file__).resolve().parents[2] / "config" / "site.json")
        source_url = None
        timeout = 1.0
        max_retries = 0
        verbose = False
        command = "sync"
        start_year = None
        end_year = None
        from_date = None
        to_date = None
        refresh = False
        max_pages = 0
        from_page = 1
        site_root = str(tmp_path / "api-site")

    args = Args()
    for key, value in overrides.items():
        setattr(args, key, value)
    monkeypatch.setattr(sync_apod, "_adapter", lambda a: adapter)
    return args


def write_record_file(tmp_path, records):
    data_root = tmp_path / "data"
    store_dir = data_root / "records"
    store_dir.mkdir(parents=True, exist_ok=True)
    by_year = {}
    for record in records:
        by_year.setdefault(record.year, []).append(record)
    for year, items in by_year.items():
        payload = {
            "schema_version": "1.0.0",
            "year": year,
            "count": len(items),
            "records": [item.to_dict() for item in items],
        }
        (store_dir / f"{year}.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return data_root


def test_sync_writes_the_public_api(tmp_path, monkeypatch):
    adapter = FakeAdapter(records=[{"date": "2026-10-22", "title": "T", "permalink": "https://science.nasa.gov/x/"}])
    args = make_args(tmp_path, monkeypatch, adapter)

    assert sync_apod.command_sync(args) == 0

    latest = json.loads((tmp_path / "data" / "api" / "v1" / "latest.json").read_text(encoding="utf-8"))
    assert latest["date"] == "2026-10-22"
    assert latest["title"] == "T"


def test_sync_failure_keeps_the_previous_archive(tmp_path, monkeypatch):
    records = [make_record("2026-10-21")]
    write_record_file(tmp_path, records)
    adapter = FakeAdapter(fail=True)
    args = make_args(tmp_path, monkeypatch, adapter)

    assert sync_apod.command_sync(args) == 2

    archive = json.loads((tmp_path / "data" / "records" / "2026.json").read_text(encoding="utf-8"))
    assert archive["count"] == 1
    state = json.loads((tmp_path / "data" / "sync-state.json").read_text(encoding="utf-8"))
    assert state["source_status"] == "error"


def test_validate_detects_a_broken_archive(tmp_path, monkeypatch):
    data_root = write_record_file(tmp_path, [make_record("2026-10-22")])
    path = data_root / "records" / "2026.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    payload["records"][0]["date"] = "not-a-date"
    path.write_text(json.dumps(payload), encoding="utf-8")

    errors = sync_apod.validate_archive(data_root)
    assert errors


def test_validate_detects_unsorted_records(tmp_path):
    data_root = write_record_file(tmp_path, [make_record("2026-10-22"), make_record("2026-10-21")])
    path = data_root / "records" / "2026.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    payload["records"] = list(reversed(payload["records"]))
    path.write_text(json.dumps(payload), encoding="utf-8")

    errors = sync_apod.validate_archive(data_root)
    assert any("not sorted" in error for error in errors)


def test_validate_detects_duplicate_dates(tmp_path):
    data_root = write_record_file(tmp_path, [make_record("2026-10-22")])
    path = data_root / "records" / "2026.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    payload["records"].append(dict(payload["records"][0]))
    path.write_text(json.dumps(payload), encoding="utf-8")

    errors = sync_apod.validate_archive(data_root)
    assert any("duplicate date" in error for error in errors)


def test_validate_detects_invalid_public_json(tmp_path, site_config):
    output = tmp_path / "out"
    (output / "api" / "v1").mkdir(parents=True)
    (output / "api" / "v1" / "latest.json").write_text('{"schema_version": "1.0.0"}', encoding="utf-8")
    (output / "api" / "v1" / "archive-index.json").write_text('{"count": 2, "entries": []}', encoding="utf-8")
    (output / "api" / "v1" / "status.json").write_text('{"schema_version": "1.0.0"}', encoding="utf-8")

    errors = sync_apod.validate_public_api(output, site_config)
    assert errors


def test_validate_reports_a_missing_feed(tmp_path, site_config):
    from mohakash.publish import build_public_api
    from mohakash.archive import ArchiveStore

    data_root = tmp_path / "data"
    store = ArchiveStore(data_root)
    store.upsert([make_record("2026-10-22")])
    output = tmp_path / "out"
    build_public_api(data_root=data_root, output_root=output, config=site_config, state={})
    (output / "feed.xml").unlink()

    errors = sync_apod.validate_public_api(output, site_config)
    assert any("feed.xml" in error for error in errors)


def test_status_reports_the_archive(tmp_path, monkeypatch):
    write_record_file(tmp_path, [make_record("2026-10-22")])
    adapter = FakeAdapter()
    args = make_args(tmp_path, monkeypatch, adapter)
    assert sync_apod.command_status(args) == 0


def test_assemble_site_command(tmp_path, monkeypatch):
    from mohakash.publish import build_public_api
    from mohakash.archive import ArchiveStore

    data_root = tmp_path / "data"
    store = ArchiveStore(data_root)
    store.upsert([make_record("2026-10-22")])
    build_public_api(data_root=data_root, output_root=data_root, config=site_config_default(), state={})

    adapter = FakeAdapter()
    args = make_args(tmp_path, monkeypatch, adapter)
    assert sync_apod.command_assemble_site(args) == 0
    assert (tmp_path / "api-site" / "api" / "v1" / "latest.json").exists()
    assert (tmp_path / "api-site" / "feed.xml").exists()
    assert (tmp_path / "api-site" / ".nojekyll").exists()


def site_config_default():
    from mohakash.config import load_config

    return load_config(Path(__file__).resolve().parents[2] / "config" / "site.json")
