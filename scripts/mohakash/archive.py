"""The on-disk archive of normalized records.

Layout::

    data/records/1995.json
    data/records/1996.json
    ...

Each file holds one calendar year::

    {
      "schema_version": "1.0.0",
      "year": 1995,
      "records": [ { ...record... } ]
    }

Records are stored newest first inside every file so that a diff of the
archive is stable and readable.
"""

from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path
from typing import Any, Iterable, Iterator

from .schema import SCHEMA_VERSION, ApodRecord

#: Fields that are merged rather than overwritten when a record is refreshed.
_PRESERVED_TEXT_FIELDS = ("title", "explanation", "copyright", "credit", "alt_text")


def _fingerprint(record: ApodRecord) -> str:
    payload = record.to_dict()
    payload.pop("updated_at", None)
    return json.dumps(payload, sort_keys=True, ensure_ascii=False)


def merge_record(existing: ApodRecord | None, incoming: ApodRecord) -> tuple[ApodRecord, bool]:
    """Merge a freshly fetched record into the stored one.

    Returns ``(record, changed)``.  ``changed`` is ``False`` when the new
    payload carries no new information, which keeps scheduled runs idempotent
    and avoids empty commits.

    Rules:

    * A freshly supplied non-empty value always wins.
    * An empty value never destroys information that is already stored.
    * A record that temporarily lost its image keeps the previous, working
      image instead of dropping the picture.
    """
    if existing is None:
        return incoming, True

    merged = ApodRecord.from_dict(incoming.to_dict())
    for name in _PRESERVED_TEXT_FIELDS:
        if not getattr(merged, name) and getattr(existing, name):
            setattr(merged, name, getattr(existing, name))

    if merged.image_status != "ok" and existing.image_status == "ok":
        merged.image_url = existing.image_url
        merged.hd_image_url = existing.hd_image_url
        merged.thumbnail_url = existing.thumbnail_url
        merged.image_status = existing.image_status

    if _fingerprint(merged) == _fingerprint(existing):
        return existing, False
    return merged, True


class ArchiveStore:
    """Reads and writes the yearly archive files.

    ``root`` is the **data root** (the directory that contains ``records/``),
    not the ``records`` directory itself.
    """

    def __init__(self, root: str | Path) -> None:
        self.root = Path(root)

    # -- reading ----------------------------------------------------------
    @property
    def records_dir(self) -> Path:
        return self.root / "records"

    def year_path(self, year: int) -> Path:
        return self.records_dir / f"{year}.json"

    def available_years(self) -> list[int]:
        if not self.records_dir.is_dir():
            return []
        years = []
        for path in self.records_dir.glob("*.json"):
            try:
                years.append(int(path.stem))
            except ValueError:
                continue
        return sorted(years)

    def load_year(self, year: int) -> dict[str, ApodRecord]:
        """Return every stored record of ``year`` keyed by date."""
        path = self.year_path(year)
        if not path.exists():
            return {}
        with path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
        records = payload.get("records", payload if isinstance(payload, list) else [])
        result: dict[str, ApodRecord] = {}
        for item in records:
            try:
                record = ApodRecord.from_dict(item)
            except KeyError:
                continue
            result[record.date] = record
        return result

    def load_all(self) -> dict[str, ApodRecord]:
        """Return every stored record keyed by date."""
        all_records: dict[str, ApodRecord] = {}
        for year in self.available_years():
            all_records.update(self.load_year(year))
        return all_records

    def iter_records(self) -> Iterator[ApodRecord]:
        for year in self.available_years():
            yield from self.load_year(year).values()

    # -- writing ----------------------------------------------------------
    def _serialize_year(self, year: int, records: dict[str, ApodRecord]) -> str:
        ordered = sorted(records.values(), key=lambda record: record.date, reverse=True)
        payload = {
            "schema_version": SCHEMA_VERSION,
            "year": year,
            "count": len(ordered),
            "records": [record.to_dict() for record in ordered],
        }
        return json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=False) + "\n"

    def save_year(self, year: int, records: dict[str, ApodRecord]) -> bool:
        """Write a yearly file. Returns ``True`` when the file changed."""
        path = self.year_path(year)
        path.parent.mkdir(parents=True, exist_ok=True)
        content = self._serialize_year(year, records)
        if path.exists() and path.read_text(encoding="utf-8") == content:
            return False
        _atomic_write(path, content)
        return True

    def upsert(self, incoming: Iterable[ApodRecord]) -> tuple[set[int], set[str]]:
        """Merge ``incoming`` records into the archive.

        Returns ``(changed_years, changed_dates)``.  Only years whose content
        actually changed are written back to disk.
        """
        by_year: dict[int, dict[str, ApodRecord]] = {}
        for record in incoming:
            by_year.setdefault(record.year, {})[record.date] = record

        changed_years: set[int] = set()
        changed_dates: set[str] = set()
        for year, records in by_year.items():
            stored = self.load_year(year)
            year_changed = False
            for date, record in records.items():
                merged, changed = merge_record(stored.get(date), record)
                stored[date] = merged
                if changed:
                    changed_dates.add(date)
                    year_changed = True
            if year_changed and self.save_year(year, stored):
                changed_years.add(year)
        return changed_years, changed_dates

    def replace_year(self, year: int, records: dict[str, ApodRecord]) -> bool:
        """Replace a whole year (used by the backfill)."""
        return self.save_year(year, records)


def _atomic_write(path: Path, content: str) -> None:
    """Write a file atomically so an interrupted run cannot corrupt the archive."""
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


def load_sync_state(path: str | Path) -> dict[str, Any]:
    """Load the committed sync state, tolerating a missing or broken file."""
    state_path = Path(path)
    if not state_path.exists():
        return {}
    try:
        with state_path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
    except (json.JSONDecodeError, OSError):
        return {}
    return data if isinstance(data, dict) else {}


def save_sync_state(path: str | Path, state: dict[str, Any]) -> bool:
    """Persist the sync state; returns ``True`` when the file changed."""
    state_path = Path(path)
    state_path.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(state, indent=2, ensure_ascii=False, sort_keys=True) + "\n"
    if state_path.exists() and state_path.read_text(encoding="utf-8") == content:
        return False
    _atomic_write(state_path, content)
    return True
