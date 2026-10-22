#!/usr/bin/env python3
"""Verify a deployed Mohakash API.

Fetches the published endpoints and checks that they are consistent with each
other.  Use it after the GitHub Pages site has been published::

    python3 scripts/verify_api.py --base-url https://<owner>.github.io/<repo>/api/v1
    python3 scripts/verify_api.py --base-url https://<owner>.github.io/api/v1 --user-site

Exit code 0 means everything checked out.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path
from xml.etree import ElementTree

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mohakash.config import REPO_ROOT, load_config  # noqa: E402

TIMEOUT = 30


def fetch(url: str) -> tuple[int, bytes, str]:
    request = urllib.request.Request(url, headers={"User-Agent": "Mohakash-verify/1.0", "Accept": "*/*"})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            return response.status, response.read(), response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read() or b"", ""
    except Exception as exc:  # pragma: no cover - network problems
        return 0, str(exc).encode("utf-8"), ""


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Verify a deployed Mohakash API")
    parser.add_argument("--base-url", help="base URL of the published API (without a trailing slash)")
    parser.add_argument("--config", default=str(REPO_ROOT / "config" / "site.json"))
    parser.add_argument("--feed-url", help="URL of the RSS feed (defaults to the feed next to the API)")
    args = parser.parse_args(argv)

    config = load_config(args.config)
    base = (args.base_url or config.api_base_url).rstrip("/")
    feed_url = args.feed_url or f"{base.rsplit('/api/', 1)[0]}/feed.xml"

    problems: list[str] = []
    checks: list[str] = []

    def check_json(path: str, label: str):
        url = f"{base}/{path}"
        status, body, content_type = fetch(url)
        if status != 200:
            problems.append(f"{label}: HTTP {status} for {url}")
            return None
        if "json" not in content_type.lower():
            checks.append(f"{label}: served as {content_type or 'unknown content type'}")
        try:
            return json.loads(body.decode("utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            problems.append(f"{label}: invalid JSON ({exc})")
            return None

    latest = check_json("latest.json", "latest.json")
    index = check_json("archive-index.json", "archive-index.json")
    status = check_json("status.json", "status.json")

    if latest:
        checks.append(f"latest.json: {latest.get('date')} — {latest.get('title')}")
        for field in ("date", "title", "explanation", "media_type", "apod_url", "updated_at"):
            if field not in latest:
                problems.append(f"latest.json: the field {field!r} is missing")
        if latest.get("media_type") == "image" and not latest.get("image_url"):
            problems.append("latest.json: an image entry without an image_url")

    if index:
        entries = index.get("entries") or []
        checks.append(f"archive-index.json: {index.get('count')} entries")
        dates = [entry.get("date") for entry in entries]
        if dates != sorted(dates, reverse=True):
            problems.append("archive-index.json: the entries are not sorted newest first")
        if index.get("count") != len(entries):
            problems.append("archive-index.json: the count does not match the number of entries")
        if entries:
            year = entries[0]["date"][:4]
            year_doc = check_json(f"archive/{year}.json", f"archive/{year}.json")
            if year_doc:
                checks.append(f"archive/{year}.json: {year_doc.get('count')} records")
                if year_doc.get("count") != len(year_doc.get("records") or []):
                    problems.append(f"archive/{year}.json: the count does not match the number of records")

    if status:
        checks.append(
            "status.json: {records} records, latest {latest}, last sync {sync}".format(
                records=status.get("record_count"),
                latest=status.get("latest_available_date"),
                sync=status.get("last_successful_sync"),
            )
        )
        if status.get("record_count") and index and status.get("record_count") != index.get("count"):
            problems.append("status.json and archive-index.json disagree about the record count")

    code, body, _ = fetch(feed_url)
    if code != 200:
        problems.append(f"feed.xml: HTTP {code} for {feed_url}")
    else:
        try:
            tree = ElementTree.fromstring(body.decode("utf-8"))
            items = tree.find("channel").findall("item")
            checks.append(f"feed.xml: {len(items)} items")
        except Exception as exc:
            problems.append(f"feed.xml: not a valid RSS document ({exc})")

    for line in checks:
        print(f"  ok  {line}")
    for line in problems:
        print(f"  !!  {line}", file=sys.stderr)

    if problems:
        print(f"\n{len(problems)} problem(s) found", file=sys.stderr)
        return 1
    print("\nall checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
