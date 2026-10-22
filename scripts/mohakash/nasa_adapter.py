"""Adapter for NASA's current APOD endpoint.

This is the **only** module in the project that knows how NASA's payload
looks.  Everything else consumes :mod:`mohakash.schema`.

Endpoint
--------
``https://science.nasa.gov/wp-json/wp/v2/apod-basic``

Verified behaviour (checked against the live service):

* No API key is required and no key is used.
* The collection endpoint answers with a JSON **array**.
* Supported query parameters are ``page`` (default 1) and ``per_page``
  (default 25).  The service clamps ``per_page`` to 25, so a full archive of
  ~11.4k records needs ~458 pages.
* ``date_from`` / ``date_to`` are advertised by the endpoint's own OPTIONS
  response but currently answer ``400 rest_invalid_param``; the adapter
  therefore probes them once and transparently falls back to pagination.
* ``date`` / ``start_date`` / ``end_date`` are *not* supported: they are
  silently ignored by the service and always return the newest page.
* A single record can be fetched by its WordPress id, which for this post
  type is the date written as ``YYMMDD`` (e.g. ``261016`` -> 2026-10-16).
* ``?date=...`` style queries must never be used, see above.
"""

from __future__ import annotations

import datetime as _dt
import json
import logging
import random
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Iterable, Sequence

LOGGER = logging.getLogger("mohakash.nasa")

DEFAULT_BASE_URL = "https://science.nasa.gov/wp-json/wp/v2/apod-basic"
DEFAULT_USER_AGENT = "Mohakash/1.0 (+https://github.com/mohakash/mohakash) APOD sync"

#: Fields the pipeline actually needs.  Requesting them explicitly shrinks the
#: response by roughly 70% (the full payload embeds a complete HTML document).
WANTED_FIELDS = (
    "date",
    "post_id",
    "title",
    "permalink",
    "media_type",
    "explanation",
    "credit",
    "copyright",
    "alt",
    "url",
    "hdurl",
)

#: The service ignores per_page values above this number.
MAX_PER_PAGE = 25


class SourceError(RuntimeError):
    """The upstream service failed in a way that is worth retrying."""


class SourceNotFound(SourceError):
    """The requested record does not exist upstream."""


@dataclass
class FetchStats:
    """Counters describing what a run actually did."""

    requests: int = 0
    records: int = 0
    retries: int = 0
    bytes_downloaded: int = 0

    def as_dict(self) -> dict[str, int]:
        return {
            "requests": self.requests,
            "records": self.records,
            "retries": self.retries,
            "bytes_downloaded": self.bytes_downloaded,
        }


class NasaAdapter:
    """Small, dependency free client for NASA's WordPress APOD endpoint."""

    def __init__(
        self,
        base_url: str = DEFAULT_BASE_URL,
        *,
        user_agent: str = DEFAULT_USER_AGENT,
        timeout: float = 30.0,
        max_retries: int = 4,
        backoff_base: float = 1.5,
        sleep: Any = time.sleep,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.user_agent = user_agent
        self.timeout = timeout
        self.max_retries = max_retries
        self.backoff_base = backoff_base
        self._sleep = sleep
        self.stats = FetchStats()
        #: ``True`` once the service answered ``400`` for ``date_from``/``date_to``.
        self._date_range_supported: bool | None = None

    # -- low level --------------------------------------------------------
    def _request(self, url: str, *, allow_404: bool = False) -> Any:
        """Perform a GET request with bounded retries and exponential backoff."""
        request = urllib.request.Request(
            url,
            headers={
                "User-Agent": self.user_agent,
                "Accept": "application/json",
            },
        )
        last_error: Exception | None = None
        for attempt in range(self.max_retries + 1):
            try:
                self.stats.requests += 1
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    payload = response.read()
                    self.stats.bytes_downloaded += len(payload)
                    return json.loads(payload.decode("utf-8"))
            except urllib.error.HTTPError as exc:  # noqa: PERF203 - explicit retry logic
                if exc.code == 404 and allow_404:
                    raise SourceNotFound(f"not found: {url}") from exc
                if exc.code in (400, 401, 403, 404):
                    # Client errors are not transient: do not retry.
                    body = ""
                    try:
                        body = exc.read().decode("utf-8", "replace")[:300]
                    except Exception:  # pragma: no cover - best effort only
                        pass
                    raise SourceError(f"HTTP {exc.code} for {url}: {body}") from exc
                last_error = exc
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
                last_error = exc

            if attempt >= self.max_retries:
                break
            self.stats.retries += 1
            delay = self.backoff_base**attempt + random.uniform(0, 0.4)
            LOGGER.warning("retry %s/%s after error: %s", attempt + 1, self.max_retries, last_error)
            self._sleep(delay)
        raise SourceError(f"request failed after {self.max_retries + 1} attempts: {url}") from last_error

    def _collection_url(self, params: dict[str, Any]) -> str:
        query = urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
        return f"{self.base_url}?{query}" if query else self.base_url

    # -- public API -------------------------------------------------------
    def fetch_page(self, page: int = 1, per_page: int = MAX_PER_PAGE) -> list[dict[str, Any]]:
        """Fetch one page of the collection endpoint (newest first)."""
        url = self._collection_url(
            {
                "page": page,
                "per_page": min(per_page, MAX_PER_PAGE),
                "_fields": ",".join(WANTED_FIELDS),
            }
        )
        payload = self._request(url)
        if not isinstance(payload, list):
            raise SourceError(f"expected a JSON array from {url}, got {type(payload).__name__}")
        self.stats.records += len(payload)
        return [item for item in payload if isinstance(item, dict)]

    def fetch_total_pages(self, per_page: int = MAX_PER_PAGE) -> int | None:
        """Total number of pages reported by the service (``X-WP-TotalPages``)."""
        url = self._collection_url({"per_page": min(per_page, MAX_PER_PAGE), "_fields": "date"})
        request = urllib.request.Request(url, headers={"User-Agent": self.user_agent})
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                total = response.headers.get("X-WP-Total")
                pages = response.headers.get("X-WP-TotalPages")
        except Exception:  # pragma: no cover - headers are a convenience only
            return None
        if pages and pages.isdigit():
            return int(pages)
        if total and total.isdigit():
            per = min(per_page, MAX_PER_PAGE)
            return -(-int(total) // per)
        return None

    def fetch_date(self, date: _dt.date) -> dict[str, Any] | None:
        """Fetch exactly one record by date.

        Returns ``None`` when the service has no record for that date.
        """
        post_id = date.strftime("%y%m%d")
        url = f"{self.base_url}/{post_id}?_fields={','.join(WANTED_FIELDS)}"
        try:
            payload = self._request(url, allow_404=True)
        except SourceNotFound:
            return None
        except SourceError as exc:
            if "HTTP 404" in str(exc):
                return None
            raise
        if isinstance(payload, list):
            payload = payload[0] if payload else None
        if not isinstance(payload, dict):
            return None
        self.stats.records += 1
        return payload

    def supports_date_range(self) -> bool:
        """Probe whether ``date_from``/``date_to`` currently work upstream."""
        if self._date_range_supported is not None:
            return self._date_range_supported
        url = self._collection_url(
            {
                "date_from": "2020-01-01",
                "date_to": "2020-01-02",
                "per_page": 1,
                "_fields": "date",
            }
        )
        try:
            self._request(url)
        except SourceError as exc:
            if "HTTP 400" in str(exc):
                LOGGER.info("source does not accept date_from/date_to, using pagination")
                self._date_range_supported = False
                return False
            LOGGER.info("date range probe failed: %s", exc)
            self._date_range_supported = False
            return False
        self._date_range_supported = True
        return True

    def fetch_range(self, start: _dt.date, end: _dt.date) -> list[dict[str, Any]]:
        """Fetch every record between ``start`` and ``end`` (inclusive).

        Uses the upstream date-range parameters when they are available and
        otherwise pages through the collection and filters client side.  The
        fallback is required because NASA's ``date_from``/``date_to`` currently
        answer ``400``.
        """
        if start > end:
            return []
        if self.supports_date_range():
            return self._fetch_range_native(start, end)
        return self._fetch_range_by_paging(start, end)

    def _fetch_range_native(self, start: _dt.date, end: _dt.date) -> list[dict[str, Any]]:
        collected: list[dict[str, Any]] = []
        page = 1
        while True:
            url = self._collection_url(
                {
                    "date_from": start.isoformat(),
                    "date_to": end.isoformat(),
                    "page": page,
                    "per_page": MAX_PER_PAGE,
                    "_fields": ",".join(WANTED_FIELDS),
                }
            )
            payload = self._request(url)
            if not isinstance(payload, list) or not payload:
                break
            self.stats.records += len(payload)
            collected.extend(item for item in payload if isinstance(item, dict))
            if len(payload) < MAX_PER_PAGE:
                break
            page += 1
        return [item for item in collected if _in_range(item, start, end)]

    def _fetch_range_by_paging(self, start: _dt.date, end: _dt.date) -> list[dict[str, Any]]:
        collected: list[dict[str, Any]] = []
        page = 1
        while True:
            batch = self.fetch_page(page)
            if not batch:
                break
            reached_end = False
            for item in batch:
                item_date = _parse_date(item.get("date"))
                if item_date is None:
                    continue
                if item_date < start:
                    reached_end = True
                    break
                if item_date <= end:
                    collected.append(item)
            if reached_end:
                break
            page += 1
        return collected

    def fetch_latest(self, count: int = 1) -> list[dict[str, Any]]:
        """Fetch the newest ``count`` records."""
        return self.fetch_page(1, per_page=min(count, MAX_PER_PAGE))

    def find_page_for_date(self, target: _dt.date) -> int:
        """Find the first page that can contain ``target``.

        The collection is ordered newest first, so the page number grows with
        age.  A binary search over the page index finds the right page in a
        handful of requests instead of walking the whole archive.
        """
        total_pages = self.fetch_total_pages() or 1
        low, high = 1, max(1, total_pages)
        best = high
        while low <= high:
            middle = (low + high) // 2
            batch = self.fetch_page(middle)
            if not batch:
                high = middle - 1
                continue
            dates = [d for d in (_parse_date(item.get("date")) for item in batch) if d]
            if not dates:
                high = middle - 1
                continue
            oldest = min(dates)
            if oldest <= target:
                best = middle
                high = middle - 1
            else:
                low = middle + 1
        return max(1, best)


    def iter_all_pages(self, max_pages: int | None = None) -> Iterable[tuple[int, list[dict[str, Any]]]]:
        """Yield ``(page_number, records)`` for the whole collection."""
        page = 1
        while True:
            batch = self.fetch_page(page)
            if not batch:
                return
            yield page, batch
            if max_pages is not None and page >= max_pages:
                return
            page += 1


def _parse_date(value: Any) -> _dt.date | None:
    if not isinstance(value, str):
        return None
    try:
        return _dt.date.fromisoformat(value.strip()[:10])
    except ValueError:
        return None


def _in_range(item: dict[str, Any], start: _dt.date, end: _dt.date) -> bool:
    item_date = _parse_date(item.get("date"))
    return item_date is not None and start <= item_date <= end


def records_by_date(records: Sequence[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    """Index raw records by publication date, keeping the first occurrence."""
    indexed: dict[str, dict[str, Any]] = {}
    for record in records:
        key = record.get("date")
        if isinstance(key, str):
            indexed.setdefault(key, record)
    return indexed
