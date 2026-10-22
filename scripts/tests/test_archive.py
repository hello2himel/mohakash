"""Archive store tests: upsert, dedupe, merge, idempotency."""

from __future__ import annotations

import json

from mohakash.archive import ArchiveStore, load_sync_state, merge_record, save_sync_state
from mohakash.schema import IMAGE_STATUS_MISSING, IMAGE_STATUS_OK, ApodRecord


def make_record(date: str, **overrides) -> ApodRecord:
    payload = {
        "date": date,
        "title": f"Title {date}",
        "explanation": f"Explanation for {date}",
        "media_type": "image",
        "image_url": "https://assets.science.nasa.gov/dynamicimage/assets/x.jpg?w=1600",
        "hd_image_url": "https://assets.science.nasa.gov/dynamicimage/assets/x.jpg?w=2400",
        "thumbnail_url": "https://assets.science.nasa.gov/dynamicimage/assets/x.jpg?w=480",
        "apod_url": f"https://science.nasa.gov/image-article/{date}/",
        "copyright": "A Creator",
        "credit": "A Creator",
        "alt_text": "Alt",
        "image_status": IMAGE_STATUS_OK,
        "updated_at": "2026-10-22T07:17:00Z",
    }
    payload.update(overrides)
    return ApodRecord.from_dict(payload)


def test_upsert_writes_a_year_file(tmp_path):
    store = ArchiveStore(tmp_path)
    changed_years, changed_dates = store.upsert([make_record("2026-10-22"), make_record("2026-10-21")])

    assert changed_years == {2026}
    assert changed_dates == {"2026-10-22", "2026-10-21"}
    assert store.year_path(2026).exists()

    with store.year_path(2026).open(encoding="utf-8") as handle:
        payload = json.load(handle)
    assert payload["year"] == 2026
    assert payload["count"] == 2
    assert payload["records"][0]["date"] == "2026-10-22"  # newest first


def test_records_are_deduplicated_by_date(tmp_path):
    store = ArchiveStore(tmp_path)
    store.upsert([make_record("2026-10-22")])
    store.upsert([make_record("2026-10-22", title="Corrected title")])

    records = store.load_year(2026)
    assert len(records) == 1
    assert records["2026-10-22"].title == "Corrected title"


def test_upsert_is_idempotent(tmp_path):
    store = ArchiveStore(tmp_path)
    store.upsert([make_record("2026-10-22")])
    changed_years, changed_dates = store.upsert([make_record("2026-10-22")])

    assert changed_years == set()
    assert changed_dates == set()


def test_historical_correction_updates_the_stored_record(tmp_path):
    store = ArchiveStore(tmp_path)
    store.upsert([make_record("2001-01-01", title="Old title")])
    changed_years, changed_dates = store.upsert([make_record("2001-01-01", title="New title")])

    assert changed_years == {2001}
    assert changed_dates == {"2001-01-01"}
    assert store.load_year(2001)["2001-01-01"].title == "New title"


def test_merge_keeps_existing_values_when_the_new_payload_is_empty():
    existing = make_record("2026-10-22", explanation="Full text", copyright="A Creator")
    incoming = make_record("2026-10-22", explanation="", copyright=None, credit=None)

    merged, changed = merge_record(existing, incoming)

    assert merged.explanation == "Full text"
    assert merged.copyright == "A Creator"
    assert changed is False


def test_merge_keeps_the_previous_image_when_a_refresh_loses_it():
    existing = make_record("2026-10-22")
    incoming = make_record(
        "2026-10-22",
        image_url=None,
        hd_image_url=None,
        thumbnail_url=None,
        image_status=IMAGE_STATUS_MISSING,
    )

    merged, changed = merge_record(existing, incoming)

    assert merged.image_status == IMAGE_STATUS_OK
    assert merged.image_url == existing.image_url
    assert changed is False


def test_merge_adopts_a_new_image():
    existing = make_record("2026-10-22")
    incoming = make_record(
        "2026-10-22",
        image_url="https://assets.science.nasa.gov/dynamicimage/assets/new.jpg?w=1600",
        hd_image_url="https://assets.science.nasa.gov/dynamicimage/assets/new.jpg?w=2400",
        thumbnail_url="https://assets.science.nasa.gov/dynamicimage/assets/new.jpg?w=480",
    )

    merged, changed = merge_record(existing, incoming)

    assert changed is True
    assert merged.image_url.endswith("new.jpg?w=1600")


def test_merge_updates_the_timestamp_only_when_something_changed():
    existing = make_record("2026-10-22")
    incoming = make_record("2026-10-22", updated_at="2026-10-23T07:17:00Z")

    merged, changed = merge_record(existing, incoming)
    assert changed is False
    assert merged.updated_at == existing.updated_at

    incoming = make_record("2026-10-22", title="Something new", updated_at="2026-10-23T07:17:00Z")
    merged, changed = merge_record(existing, incoming)
    assert changed is True
    assert merged.updated_at == "2026-10-23T07:17:00Z"


def test_interrupted_backfill_keeps_a_valid_archive(tmp_path):
    """Records written before an interruption stay readable and valid."""
    store = ArchiveStore(tmp_path)
    store.upsert([make_record(f"2026-10-{day:02d}") for day in range(1, 21)])

    # A crash right in the middle of the next batch.
    store.upsert([make_record("2026-10-21")])

    records = store.load_all()
    assert len(records) == 21
    for record in records.values():
        record.validate()
    with store.year_path(2026).open(encoding="utf-8") as handle:
        payload = json.load(handle)
    assert payload["count"] == 21


def test_save_year_does_not_rewrite_identical_content(tmp_path):
    store = ArchiveStore(tmp_path)
    records = {"2026-10-22": make_record("2026-10-22")}

    assert store.save_year(2026, records) is True
    stamp = store.year_path(2026).stat().st_mtime_ns
    assert store.save_year(2026, records) is False
    assert store.year_path(2026).stat().st_mtime_ns == stamp


def test_sync_state_round_trip(tmp_path):
    path = tmp_path / "sync-state.json"
    assert save_sync_state(path, {"last_successful_sync": "2026-10-22T07:17:00Z"}) is True
    assert save_sync_state(path, {"last_successful_sync": "2026-10-22T07:17:00Z"}) is False
    assert load_sync_state(path)["last_successful_sync"] == "2026-10-22T07:17:00Z"


def test_sync_state_tolerates_a_broken_file(tmp_path):
    path = tmp_path / "sync-state.json"
    path.write_text("{not json", encoding="utf-8")
    assert load_sync_state(path) == {}
