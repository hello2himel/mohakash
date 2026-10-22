#!/usr/bin/env python3
"""Mohakash APOD synchronisation CLI.

Commands
--------
``sync``            Fetch the newest entry and revalidate the last few days.
``backfill``        Build the historical archive (1995-06-16 onwards).
``build``           Regenerate the public API documents and the RSS feed.
``validate``        Check the archive and the generated documents.
``assemble-site``   Build the GitHub Pages artifact (public data only).
``status``          Print a short summary of the local archive.

Examples::

    python3 scripts/sync_apod.py sync
    python3 scripts/sync_apod.py backfill --start-year 1995 --end-year 2026
    python3 scripts/sync_apod.py build
    python3 scripts/sync_apod.py validate
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mohakash.archive import ArchiveStore, load_sync_state, save_sync_state  # noqa: E402
from mohakash.config import REPO_ROOT, load_config  # noqa: E402
from mohakash.nasa_adapter import DEFAULT_BASE_URL, NasaAdapter, SourceError  # noqa: E402
from mohakash.normalize import normalize_record  # noqa: E402
from mohakash.publish import assemble_api_site, build_public_api, data_timestamp  # noqa: E402
from mohakash.schema import SCHEMA_VERSION  # noqa: E402

LOGGER = logging.getLogger("mohakash.sync")

#: The first day APOD was published.
FIRST_APOD_DATE = _dt.date(1995, 6, 16)

#: How many past days are revalidated on every routine run.
REVALIDATE_WINDOW_DAYS = 7

#: The service clamps ``per_page`` to this value.
MAX_PER_PAGE = 25


def _utc_now() -> _dt.datetime:
    return _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0)


def _iso(moment: _dt.datetime) -> str:
    return moment.isoformat().replace("+00:00", "Z")


def _setup_logging(verbose: bool) -> None:
    logging.basicConfig(
        level=logging.DEBUG if verbose else logging.INFO,
        format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )


def _paths(args: argparse.Namespace) -> tuple[Path, Path, Path, Path]:
    """Return ``(data_root, output_root, state_path, run_state_path)``.

    ``data_root`` is the directory that holds ``records/``, ``api/`` and
    ``sync-state.json``.  The committed ``sync-state.json`` only carries facts
    that are derived from the archive, so it stays stable as long as the data
    does.  The volatile run log (``last_attempt`` and friends) is written to
    ``.run-state.json``, which is not committed.
    """
    data_root = Path(args.data_root) if args.data_root else REPO_ROOT / "data"
    output_root = Path(args.output_root) if args.output_root else data_root
    state_path = data_root / "sync-state.json"
    run_state_path = data_root / ".run-state.json"
    return data_root, output_root, state_path, run_state_path


def _adapter(args: argparse.Namespace) -> NasaAdapter:
    return NasaAdapter(
        base_url=args.source_url or DEFAULT_BASE_URL,
        timeout=args.timeout,
        max_retries=args.max_retries,
    )


# --------------------------------------------------------------------------
# sync
# --------------------------------------------------------------------------
def command_sync(args: argparse.Namespace) -> int:
    data_root, output_root, state_path, run_state_path = _paths(args)
    adapter = _adapter(args)
    store = ArchiveStore(data_root)
    state = load_sync_state(state_path)
    run_state = load_sync_state(run_state_path)
    now = _utc_now()

    run_state["last_attempt"] = _iso(now)
    run_state["source_endpoint"] = adapter.base_url

    try:
        newest = adapter.fetch_page(1)
        if not newest:
            raise SourceError("the source returned no records")
        collected = list(newest)

        # Revalidate the recent window so that corrections and delayed
        # publications are picked up even when they fall outside the first page.
        newest_date = _parse_date(newest[0].get("date"))
        if newest_date:
            for offset in range(1, REVALIDATE_WINDOW_DAYS + 1):
                day = newest_date - _dt.timedelta(days=offset)
                if day < FIRST_APOD_DATE:
                    continue
                record = adapter.fetch_date(day)
                if record is not None:
                    collected.append(record)

        normalized = []
        for raw in collected:
            try:
                normalized.append(normalize_record(raw, now=now))
            except ValueError as exc:
                LOGGER.warning("skipping malformed record: %s", exc)

        changed_years, changed_dates = store.upsert(normalized)
        LOGGER.info(
            "sync: %s records checked, %s changed, years touched: %s",
            len(normalized),
            len(changed_dates),
            sorted(changed_years) or "-",
        )
    except SourceError as exc:
        LOGGER.error("sync failed, keeping the existing archive: %s", exc)
        run_state["source_status"] = "error"
        run_state["last_error"] = str(exc)[:400]
        save_sync_state(run_state_path, run_state)
        return 2

    all_records = list(store.iter_records())
    # The published timestamp is derived from the data, so an unchanged
    # archive produces no diff at all.
    state["record_count"] = len(all_records)
    state["latest_available_date"] = max((r.date for r in all_records), default=None)
    state["source_status"] = "ok"
    state["last_error"] = None
    state["source_endpoint"] = adapter.base_url
    state["last_successful_sync"] = data_timestamp(all_records)
    # The committed state only carries facts that are derived from the
    # archive; volatile run information lives in the run state file.
    state.pop("last_attempt", None)
    save_sync_state(state_path, state)
    run_state["last_successful_run"] = _iso(now)
    run_state["source_status"] = "ok"
    run_state["last_error"] = None
    save_sync_state(run_state_path, run_state)

    result = build_public_api(
        data_root=data_root,
        output_root=output_root,
        config=load_config(args.config),
        state=state,
    )
    LOGGER.info("published %s file(s), %s unchanged", len(result.written), len(result.unchanged))
    if result.written:
        for path in result.written:
            LOGGER.info("  changed: %s", path)
    return 0


# --------------------------------------------------------------------------
# backfill
# --------------------------------------------------------------------------
def command_backfill(args: argparse.Namespace) -> int:
    data_root, output_root, state_path, run_state_path = _paths(args)
    adapter = _adapter(args)
    store = ArchiveStore(data_root)
    state = load_sync_state(state_path)
    run_state = load_sync_state(run_state_path)
    now = _utc_now()

    today = now.date()
    start = FIRST_APOD_DATE
    end = today
    if args.start_year:
        start = max(start, _dt.date(args.start_year, 1, 1))
    if args.end_year:
        end = min(end, _dt.date(args.end_year, 12, 31))
    if args.from_date:
        start = max(start, _dt.date.fromisoformat(args.from_date))
    if args.to_date:
        end = min(end, _dt.date.fromisoformat(args.to_date))
    if start > end:
        LOGGER.error("empty range: %s .. %s", start, end)
        return 1

    LOGGER.info("backfilling %s .. %s", start, end)
    run_state["last_attempt"] = _iso(now)
    run_state["source_endpoint"] = adapter.base_url

    existing = store.load_all()
    if existing:
        LOGGER.info("archive already holds %s records", len(existing))

    # Locate the first page to read.  The collection is ordered newest first,
    # so the range starts on the page that contains ``end``.  When the archive
    # already covers the range completely the walk resumes just below the
    # oldest stored date.
    page_number = args.from_page
    if not page_number:
        resume_from = end
        stored_dates = {
            parsed for parsed in (_parse_date(d) for d in existing) if parsed and start <= parsed <= end
        }
        if stored_dates:
            # Walk back from the newest stored day for as long as the archive
            # holds an unbroken run of days.  Everything below that run is
            # still missing and is where the backfill has to continue.
            run_start = max(stored_dates)
            while (run_start - _dt.timedelta(days=1)) in stored_dates and run_start > start:
                run_start -= _dt.timedelta(days=1)
            if run_start > start:
                resume_from = run_start - _dt.timedelta(days=1)
                LOGGER.info(
                    "archive already covers %s..%s, resuming below %s",
                    run_start,
                    max(stored_dates),
                    resume_from,
                )
        try:
            page_number = adapter.find_page_for_date(resume_from)
            LOGGER.info("resuming at page %s (date %s)", page_number, resume_from)
        except SourceError as exc:
            LOGGER.error("could not locate the start page: %s", exc)
            run_state["source_status"] = "error"
            run_state["last_error"] = str(exc)[:400]
            save_sync_state(run_state_path, run_state)
            return 2

    pending: dict[int, dict[str, object]] = {}
    processed = 0
    stored = 0
    reached_start = False
    pages_done = 0
    try:
        while True:
            batch = adapter.fetch_page(page_number)
            if not batch:
                break
            pages_done += 1
            batch_dates = [d for d in (_parse_date(item.get("date")) for item in batch) if d]
            if not batch_dates:
                break
            oldest = min(batch_dates)
            newest = max(batch_dates)

            for raw in batch:
                record_date = _parse_date(raw.get("date"))
                if record_date is None:
                    continue
                if record_date > end:
                    continue
                if record_date < start:
                    reached_start = True
                    continue
                processed += 1
                try:
                    record = normalize_record(raw, now=now)
                except ValueError as exc:
                    LOGGER.warning("skipping malformed record: %s", exc)
                    continue
                if not args.refresh and record.date in existing:
                    continue
                pending.setdefault(record.year, {})[record.date] = record

            # A year is complete as soon as the batch moved past its end.
            for year in list(pending):
                if oldest <= _dt.date(year, 12, 31):
                    stored += _flush_year(store, year, pending.pop(year))

            LOGGER.info(
                "backfill: page %s covers %s..%s (%s records seen, %s stored)",
                page_number,
                oldest,
                newest,
                processed,
                stored,
            )
            if reached_start or oldest < start:
                break
            page_number += 1
            if args.max_pages and pages_done >= args.max_pages:
                LOGGER.info("stopping after %s pages (--max-pages)", args.max_pages)
                break
    except SourceError as exc:
        LOGGER.error("backfill interrupted: %s", exc)
        for year, records in pending.items():
            stored += _flush_year(store, year, records)
        run_state["source_status"] = "error"
        run_state["last_error"] = str(exc)[:400]
        save_sync_state(run_state_path, run_state)
        # The archive keeps whatever was stored before the failure.
        _publish(data_root, output_root, state, args)
        return 2

    for year, records in pending.items():
        stored += _flush_year(store, year, records)

    all_records = list(store.iter_records())
    state["record_count"] = len(all_records)
    state["latest_available_date"] = max((r.date for r in all_records), default=None)
    state["source_status"] = "ok"
    state["last_error"] = None
    state["source_endpoint"] = adapter.base_url
    state["last_successful_sync"] = data_timestamp(all_records)
    # The committed state only carries facts that are derived from the
    # archive; volatile run information lives in the run state file.
    state.pop("last_attempt", None)
    save_sync_state(state_path, state)
    run_state["last_successful_run"] = _iso(now)
    run_state["source_status"] = "ok"
    run_state["last_error"] = None
    save_sync_state(run_state_path, run_state)
    LOGGER.info("backfill finished: %s records stored in total", len(all_records))

    _publish(data_root, output_root, state, args)
    return 0


def _flush_year(store: ArchiveStore, year: int, records: dict) -> int:
    """Merge a buffered year into the archive and write it back once."""
    from mohakash.archive import merge_record

    stored = store.load_year(year)
    changed = False
    for date, record in records.items():
        merged, did_change = merge_record(stored.get(date), record)
        stored[date] = merged
        changed = changed or did_change
    if changed:
        store.save_year(year, stored)
        return len(records)
    return 0


def _publish(data_root: Path, output_root: Path, state: dict, args: argparse.Namespace) -> None:
    result = build_public_api(
        data_root=data_root,
        output_root=output_root,
        config=load_config(args.config),
        state=state,
    )
    LOGGER.info("published %s file(s), %s unchanged", len(result.written), len(result.unchanged))


# --------------------------------------------------------------------------
# build / validate / assemble
# --------------------------------------------------------------------------
def command_renormalize(args: argparse.Namespace) -> int:
    """Re-derive the image URLs of every stored record (no network access)."""
    from mohakash.normalize import renormalize_record

    data_root, output_root, state_path, run_state_path = _paths(args)
    store = ArchiveStore(data_root)
    changed_years: set[int] = set()
    for year in store.available_years():
        records = store.load_year(year)
        updated = {date: renormalize_record(record) for date, record in records.items()}
        if store.save_year(year, updated):
            changed_years.add(year)
    LOGGER.info("renormalized %s year file(s)", len(changed_years))
    _publish(data_root, output_root, load_sync_state(state_path), args)
    return 0


def command_build(args: argparse.Namespace) -> int:
    data_root, output_root, state_path, run_state_path = _paths(args)
    state = load_sync_state(state_path)
    result = build_public_api(
        data_root=data_root,
        output_root=output_root,
        config=load_config(args.config),
        state=state,
    )
    print(json.dumps(result.as_dict(), indent=2))
    return 0


def command_validate(args: argparse.Namespace) -> int:
    data_root, output_root, state_path, run_state_path = _paths(args)
    errors = validate_archive(data_root)
    errors += validate_public_api(output_root, load_config(args.config))
    if errors:
        print(f"validation failed with {len(errors)} problem(s):", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print("validation ok")
    return 0


def validate_archive(data_root: Path) -> list[str]:
    """Validate the stored archive. Returns a list of problems."""
    from mohakash.schema import ApodRecord, SchemaError

    errors: list[str] = []
    store = ArchiveStore(data_root)
    years = store.available_years()
    if not years:
        return [f"no archive files found in {data_root / 'records'}"]

    seen_dates: dict[str, int] = {}
    for year in years:
        path = store.year_path(year)
        try:
            with path.open("r", encoding="utf-8") as handle:
                payload = json.load(handle)
        except (json.JSONDecodeError, OSError) as exc:
            errors.append(f"{path}: unreadable ({exc})")
            continue
        if not isinstance(payload, dict) or "records" not in payload:
            errors.append(f"{path}: not an archive document")
            continue
        if payload.get("schema_version") != SCHEMA_VERSION:
            errors.append(f"{path}: schema_version {payload.get('schema_version')!r} != {SCHEMA_VERSION!r}")
        records = payload["records"]
        previous = None
        for item in records:
            try:
                record = ApodRecord.from_dict(item)
                record.validate()
            except (SchemaError, KeyError) as exc:
                errors.append(f"{path}: {exc}")
                continue
            if record.year != year:
                errors.append(f"{path}: record {record.date} does not belong to {year}")
            if previous is not None and record.date >= previous:
                errors.append(f"{path}: records are not sorted newest first at {record.date}")
            previous = record.date
            if record.date in seen_dates:
                errors.append(f"{path}: duplicate date {record.date} (also in {seen_dates[record.date]})")
            else:
                seen_dates[record.date] = year
    return errors


def validate_public_api(output_root: Path, config) -> list[str]:
    """Validate the generated public documents."""
    errors: list[str] = []
    api_root = output_root / "api" / config.api_version
    latest_path = api_root / "latest.json"
    index_path = api_root / "archive-index.json"
    status_path = api_root / "status.json"

    for path in (latest_path, index_path, status_path):
        if not path.exists():
            errors.append(f"missing {path}")
    if errors:
        return errors

    def load(path: Path):
        try:
            with path.open("r", encoding="utf-8") as handle:
                return json.load(handle)
        except (json.JSONDecodeError, OSError) as exc:
            errors.append(f"{path}: unreadable ({exc})")
            return None

    latest = load(latest_path)
    index = load(index_path)
    status = load(status_path)
    if latest is None or index is None or status is None:
        return errors

    for name, document in (("latest.json", latest), ("archive-index.json", index), ("status.json", status)):
        if document.get("schema_version") != SCHEMA_VERSION:
            errors.append(f"{name}: schema_version {document.get('schema_version')!r} != {SCHEMA_VERSION!r}")

    if latest.get("date") != status.get("latest_available_date"):
        errors.append("latest.json and status.json disagree about the latest date")
    if index.get("count") != len(index.get("entries", [])):
        errors.append("archive-index.json: count does not match the number of entries")
    if status.get("record_count") != index.get("count"):
        errors.append("status.json and archive-index.json disagree about the record count")
    dates = [entry.get("date") for entry in index.get("entries", [])]
    if dates != sorted(dates, reverse=True):
        errors.append("archive-index.json: entries are not sorted newest first")
    if len(set(dates)) != len(dates):
        errors.append("archive-index.json: contains duplicate dates")

    feed_path = output_root / "feed.xml"
    if not feed_path.exists():
        errors.append(f"missing {feed_path}")
    else:
        content = feed_path.read_text(encoding="utf-8")
        if "<rss" not in content or "</rss>" not in content:
            errors.append("feed.xml: not a valid RSS document")
        if content.count("<item>") != min(30, index.get("count", 0)):
            errors.append("feed.xml: unexpected number of items")

    for path in sorted((api_root / "archive").glob("*.json")) if (api_root / "archive").is_dir() else []:
        document = load(path)
        if document is None:
            continue
        if document.get("year") != int(path.stem):
            errors.append(f"{path}: year field does not match the file name")
    return errors


def command_assemble_site(args: argparse.Namespace) -> int:
    data_root, output_root, _state, _run_state = _paths(args)
    site_root = Path(args.site_root) if args.site_root else REPO_ROOT / "api-site"
    written = assemble_api_site(data_root=output_root, site_root=site_root)
    LOGGER.info("assembled %s path(s) into %s", len(written), site_root)
    return 0


def command_status(args: argparse.Namespace) -> int:
    data_root, output_root, state_path, run_state_path = _paths(args)
    store = ArchiveStore(data_root)
    records = list(store.iter_records())
    state = load_sync_state(state_path)
    summary = {
        "schema_version": SCHEMA_VERSION,
        "records": len(records),
        "years": store.available_years(),
        "oldest": min((r.date for r in records), default=None),
        "newest": max((r.date for r in records), default=None),
        "state": state,
    }
    print(json.dumps(summary, indent=2))
    return 0


def _parse_date(value) -> _dt.date | None:
    if not isinstance(value, str):
        return None
    try:
        return _dt.date.fromisoformat(value.strip()[:10])
    except ValueError:
        return None


# --------------------------------------------------------------------------
# argument parsing
# --------------------------------------------------------------------------
def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Mohakash APOD data pipeline")
    parser.add_argument("--data-root", help="directory holding records/, api/ and sync-state.json")
    parser.add_argument("--output-root", help="directory the public API is written to")
    parser.add_argument("--config", help="path to config/site.json")
    parser.add_argument("--source-url", help="override the NASA APOD endpoint")
    parser.add_argument("--timeout", type=float, default=30.0, help="HTTP timeout in seconds")
    parser.add_argument("--max-retries", type=int, default=4, help="bounded retries per request")
    parser.add_argument("-v", "--verbose", action="store_true")
    sub = parser.add_subparsers(dest="command", required=True)

    sync = sub.add_parser("sync", help="fetch the newest entry and revalidate recent days")
    sync.set_defaults(func=command_sync)

    backfill = sub.add_parser("backfill", help="build the historical archive")
    backfill.add_argument("--start-year", type=int)
    backfill.add_argument("--end-year", type=int)
    backfill.add_argument("--from-date", help="YYYY-MM-DD")
    backfill.add_argument("--to-date", help="YYYY-MM-DD")
    backfill.add_argument("--refresh", action="store_true", help="re-fetch records that are already stored")
    backfill.add_argument("--max-pages", type=int, default=0)
    backfill.add_argument("--from-page", type=int, default=0, help="start at this page instead of searching for it")
    backfill.set_defaults(func=command_backfill)

    build = sub.add_parser("build", help="regenerate the public API and the RSS feed")
    build.set_defaults(func=command_build)

    renorm = sub.add_parser("renormalize", help="re-derive the image URLs of the stored records")
    renorm.set_defaults(func=command_renormalize)

    validate = sub.add_parser("validate", help="validate the archive and the generated documents")
    validate.set_defaults(func=command_validate)

    assemble = sub.add_parser("assemble-site", help="build the GitHub Pages artifact")
    assemble.add_argument("--site-root", help="directory the artifact is written to")
    assemble.set_defaults(func=command_assemble_site)

    status = sub.add_parser("status", help="print a summary of the local archive")
    status.set_defaults(func=command_status)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    _setup_logging(args.verbose)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
