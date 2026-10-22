"""Publisher tests: public documents, index generation and the RSS feed."""

from __future__ import annotations

import json
import xml.etree.ElementTree as ET

import pytest

from mohakash.archive import ArchiveStore
from mohakash.publish import assemble_api_site, build_public_api, build_feed
from mohakash.schema import IMAGE_STATUS_OK, SCHEMA_VERSION, ApodRecord

from test_archive import make_record


@pytest.fixture()
def populated(tmp_path, site_config):
    store = ArchiveStore(tmp_path)
    store.upsert(
        [
            make_record("2026-10-22", title="Newest"),
            make_record("2026-10-21", title="Second"),
            make_record("2025-03-04", title="Older"),
        ]
    )
    return tmp_path, store, site_config


def test_build_writes_every_public_document(populated):
    root, store, config = populated
    output = root / "out"

    result = build_public_api(
        data_root=store.root,
        output_root=output,
        config=config,
        state={"last_successful_sync": "2026-10-22T07:17:00Z", "record_count": 3, "source_status": "ok"},
    )

    assert result.changed is True
    assert (output / "api" / "v1" / "latest.json").exists()
    assert (output / "api" / "v1" / "archive-index.json").exists()
    assert (output / "api" / "v1" / "status.json").exists()
    assert (output / "api" / "v1" / "archive" / "2026.json").exists()
    assert (output / "api" / "v1" / "archive" / "2025.json").exists()
    assert (output / "feed.xml").exists()


def test_latest_document_is_flat(populated):
    root, store, config = populated
    output = root / "out"
    build_public_api(data_root=store.root, output_root=output, config=config, state={})

    latest = json.loads((output / "api" / "v1" / "latest.json").read_text(encoding="utf-8"))

    assert latest["date"] == "2026-10-22"
    assert latest["title"] == "Newest"
    assert latest["schema_version"] == SCHEMA_VERSION
    assert latest["latest_available_date"] == "2026-10-22"
    assert "last_successful_sync" in latest


def test_index_is_newest_first_and_has_no_explanations(populated):
    root, store, config = populated
    output = root / "out"
    build_public_api(data_root=store.root, output_root=output, config=config, state={})

    index = json.loads((output / "api" / "v1" / "archive-index.json").read_text(encoding="utf-8"))

    assert index["count"] == 3
    dates = [entry["date"] for entry in index["entries"]]
    assert dates == sorted(dates, reverse=True)
    for entry in index["entries"]:
        assert "explanation" not in entry
        # The index must not duplicate the image links; the thumbnail is enough.
        assert "image_url" not in entry
        assert entry["detail_path"] == f"archive/{entry['date'][:4]}.json"
        assert entry["apod_url"].startswith("https://")


def test_status_document(populated):
    root, store, config = populated
    output = root / "out"
    build_public_api(
        data_root=store.root,
        output_root=output,
        config=config,
        state={"last_successful_sync": "2026-10-22T07:17:00Z", "source_status": "ok"},
    )

    status = json.loads((output / "api" / "v1" / "status.json").read_text(encoding="utf-8"))

    assert status["schema_version"] == SCHEMA_VERSION
    assert status["last_successful_sync"] == "2026-10-22T07:17:00Z"
    assert status["latest_available_date"] == "2026-10-22"
    assert status["archive_start_date"] == "2025-03-04"
    assert status["record_count"] == 3
    assert status["source_status"] == "ok"
    assert status["years"] == [2025, 2026]


def test_status_never_leaks_secrets(populated):
    root, store, config = populated
    output = root / "out"
    build_public_api(
        data_root=store.root,
        output_root=output,
        config=config,
        state={"api_key": "SECRET", "token": "SECRET"},
    )

    text = (output / "api" / "v1" / "status.json").read_text(encoding="utf-8")
    assert "SECRET" not in text


def test_yearly_documents_contain_full_records(populated):
    root, store, config = populated
    output = root / "out"
    build_public_api(data_root=store.root, output_root=output, config=config, state={})

    year = json.loads((output / "api" / "v1" / "archive" / "2026.json").read_text(encoding="utf-8"))
    assert year["year"] == 2026
    assert year["count"] == 2
    assert year["records"][0]["explanation"]
    assert year["records"][0]["image_url"]


def test_build_is_idempotent(populated):
    root, store, config = populated
    output = root / "out"
    first = build_public_api(data_root=store.root, output_root=output, config=config, state={})
    second = build_public_api(data_root=store.root, output_root=output, config=config, state={})

    assert first.changed is True
    assert second.changed is False
    assert second.unchanged


def test_build_refuses_to_publish_an_empty_archive(tmp_path, site_config):
    store = ArchiveStore(tmp_path)
    with pytest.raises(ValueError):
        build_public_api(data_root=store.root, output_root=tmp_path / "out", config=site_config, state={})


def test_the_published_timestamp_comes_from_the_data(populated):
    """An unchanged archive must produce byte identical files."""
    root, store, config = populated
    output = root / "out"
    build_public_api(data_root=store.root, output_root=output, config=config, state={})

    latest = json.loads((output / "api" / "v1" / "latest.json").read_text(encoding="utf-8"))
    index = json.loads((output / "api" / "v1" / "archive-index.json").read_text(encoding="utf-8"))
    year = json.loads((output / "api" / "v1" / "archive" / "2026.json").read_text(encoding="utf-8"))

    assert latest["generated_at"] == "2026-10-22T07:17:00Z"
    assert index["generated_at"] == "2026-10-22T07:17:00Z"
    assert year["generated_at"] == "2026-10-22T07:17:00Z"


def test_feed_is_valid_rss(populated):
    root, store, config = populated
    output = root / "out"
    build_public_api(data_root=store.root, output_root=output, config=config, state={})

    feed = (output / "feed.xml").read_text(encoding="utf-8")
    tree = ET.fromstring(feed)
    assert tree.tag == "rss"
    channel = tree.find("channel")
    assert channel is not None
    items = channel.findall("item")
    assert len(items) == 3
    assert items[0].findtext("title") == "Newest"
    assert items[0].findtext("link").startswith("https://science.nasa.gov/")
    assert items[0].findtext("guid")
    assert items[0].findtext("pubDate")
    description = "".join(items[0].find("description").itertext())
    assert "Credit" in description
    assert "A Creator" in description


def test_feed_contains_at_most_thirty_entries(tmp_path, site_config):
    store = ArchiveStore(tmp_path)
    store.upsert([make_record(f"2026-01-{day:02d}") for day in range(1, 29)])
    output = tmp_path / "out"
    build_public_api(data_root=store.root, output_root=output, config=site_config, state={})

    tree = ET.fromstring((output / "feed.xml").read_text(encoding="utf-8"))
    assert len(tree.find("channel").findall("item")) == 28


def test_published_files_are_readable(populated):
    """The published data has to be readable by the web server."""
    import stat

    root, store, config = populated
    output = root / "out"
    build_public_api(data_root=store.root, output_root=output, config=config, state={})

    for path in [
        output / "api" / "v1" / "latest.json",
        output / "api" / "v1" / "archive-index.json",
        output / "api" / "v1" / "status.json",
        output / "api" / "v1" / "archive" / "2026.json",
        output / "feed.xml",
    ]:
        mode = stat.S_IMODE(path.stat().st_mode)
        assert mode & 0o044, f"{path} is not readable by others (mode {oct(mode)})"


def test_assemble_api_site_only_contains_public_data(populated, tmp_path):
    root, store, config = populated
    output = root / "out"
    build_public_api(data_root=store.root, output_root=output, config=config, state={})
    site = tmp_path / "api-site"

    assemble_api_site(data_root=output, site_root=site)

    assert (site / ".nojekyll").exists()
    assert (site / "api" / "v1" / "latest.json").exists()
    assert (site / "api" / "v1" / "archive" / "2026.json").exists()
    assert (site / "feed.xml").exists()
    # nothing else
    names = sorted(path.name for path in site.iterdir())
    assert names == [".nojekyll", "api", "feed.xml"]


def test_build_feed_escapes_markup(tmp_path, site_config):
    record = ApodRecord.from_dict(
        {
            "date": "2026-10-22",
            "title": "Tags & <b>markup</b>",
            "explanation": "A & B",
            "media_type": "image",
            "apod_url": "https://science.nasa.gov/x/?a=1&b=2",
            "image_status": IMAGE_STATUS_OK,
        }
    )
    feed = build_feed([record], config=site_config)
    tree = ET.fromstring(feed)
    item = tree.find("channel").find("item")
    assert item.findtext("title") == "Tags & <b>markup</b>"
    assert item.findtext("link") == "https://science.nasa.gov/x/?a=1&b=2"
