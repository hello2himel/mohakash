"""Adapter tests.  These never touch the network."""

from __future__ import annotations

import io
import json
import urllib.error
from datetime import date

import pytest

from mohakash import nasa_adapter
from mohakash.nasa_adapter import NasaAdapter, SourceError, SourceNotFound


class FakeResponse(io.BytesIO):
    def __init__(self, payload, headers=None, status=200):
        super().__init__(json.dumps(payload).encode("utf-8"))
        self.headers = headers or {}
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()
        return False


class FakeServer:
    """Serves canned responses and records every request."""

    def __init__(self):
        self.calls: list[str] = []
        self.handler = None

    def __call__(self, request, timeout=None):
        url = request.full_url if hasattr(request, "full_url") else str(request)
        self.calls.append(url)
        if self.handler is None:
            raise AssertionError(f"unexpected request: {url}")
        return self.handler(url)


@pytest.fixture()
def server(monkeypatch):
    fake = FakeServer()
    monkeypatch.setattr(nasa_adapter.urllib.request, "urlopen", fake)
    return fake


def make_adapter(server, **kwargs):
    slept: list[float] = []
    adapter = NasaAdapter(sleep=slept.append, **kwargs)
    adapter.slept = slept  # type: ignore[attr-defined]
    return adapter


def test_fetch_page_returns_records(server):
    payload = [{"date": "2026-10-22", "title": "A"}, {"date": "2026-10-21", "title": "B"}]
    server.handler = lambda url: FakeResponse(payload)

    adapter = make_adapter(server)
    records = adapter.fetch_page(1)

    assert len(records) == 2
    assert "_fields" in server.calls[0]
    assert "per_page=25" in server.calls[0]


def test_fetch_page_clamps_per_page(server):
    server.handler = lambda url: FakeResponse([])
    adapter = make_adapter(server)
    adapter.fetch_page(1, per_page=500)
    assert "per_page=25" in server.calls[0]


def test_fetch_page_rejects_a_non_array_payload(server):
    server.handler = lambda url: FakeResponse({"code": "boom"})
    adapter = make_adapter(server)
    with pytest.raises(SourceError):
        adapter.fetch_page(1)


def test_fetch_date_returns_none_for_a_missing_date(server):
    def handler(url):
        raise urllib.error.HTTPError(url, 404, "Not Found", None, None)

    server.handler = handler
    adapter = make_adapter(server)
    assert adapter.fetch_date(date(1995, 6, 17)) is None


def test_fetch_date_uses_the_yymmdd_post_id(server):
    server.handler = lambda url: FakeResponse({"date": "1995-06-16", "title": "x"})
    adapter = make_adapter(server)
    adapter.fetch_date(date(1995, 6, 16))
    assert "/950616" in server.calls[0]


def test_fetch_date_returns_the_record(server):
    server.handler = lambda url: FakeResponse({"date": "2026-10-22", "title": "x"})
    adapter = make_adapter(server)
    assert adapter.fetch_date(date(2026, 10, 22))["title"] == "x"


def test_retries_are_bounded_and_use_exponential_backoff(server):
    attempts = {"n": 0}

    def handler(url):
        attempts["n"] += 1
        raise urllib.error.URLError("boom")

    server.handler = handler
    adapter = make_adapter(server, max_retries=3, backoff_base=2.0)
    with pytest.raises(SourceError):
        adapter.fetch_page(1)

    assert attempts["n"] == 4  # 1 initial attempt + 3 retries
    assert len(adapter.slept) == 3
    # exponential backoff: each delay is larger than the previous one
    assert adapter.slept[0] < adapter.slept[1] < adapter.slept[2]


def test_client_errors_are_not_retried(server):
    attempts = {"n": 0}

    def handler(url):
        attempts["n"] += 1
        raise urllib.error.HTTPError(url, 400, "Bad Request", None, None)

    server.handler = handler
    adapter = make_adapter(server, max_retries=3)
    with pytest.raises(SourceError):
        adapter.fetch_page(1)
    assert attempts["n"] == 1


def test_server_errors_are_retried(server):
    attempts = {"n": 0}

    def handler(url):
        attempts["n"] += 1
        if attempts["n"] < 3:
            raise urllib.error.HTTPError(url, 503, "Service Unavailable", None, None)
        return FakeResponse([{"date": "2026-10-22"}])

    server.handler = handler
    adapter = make_adapter(server, max_retries=4)
    assert adapter.fetch_page(1) == [{"date": "2026-10-22"}]
    assert attempts["n"] == 3


def test_invalid_json_raises_a_source_error(server):
    def handler(url):
        response = FakeResponse({})
        response.read = lambda: b"{not json"  # type: ignore[assignment]
        return response

    server.handler = handler
    adapter = make_adapter(server, max_retries=0)
    with pytest.raises(SourceError):
        adapter.fetch_page(1)


def test_date_range_falls_back_to_pagination_when_unsupported(server):
    """The upstream ``date_from``/``date_to`` parameters currently answer 400."""
    pages = {
        1: [{"date": "2026-10-22"}, {"date": "2026-10-21"}],
        2: [{"date": "2026-10-20"}, {"date": "2020-01-01"}],
        3: [{"date": "2019-12-31"}],
    }

    def handler(url):
        if "date_from=" in url:
            raise urllib.error.HTTPError(url, 400, "Bad Request", None, None)
        page = int(url.split("page=")[1].split("&")[0])
        return FakeResponse(pages.get(page, []))

    server.handler = handler
    adapter = make_adapter(server)

    records = adapter.fetch_range(date(2020, 1, 1), date(2026, 10, 22))
    dates = sorted(record["date"] for record in records)
    assert dates == ["2020-01-01", "2026-10-20", "2026-10-21", "2026-10-22"]


def test_supports_date_range_is_false_for_a_400(server):
    def handler(url):
        raise urllib.error.HTTPError(url, 400, "Bad Request", None, None)

    server.handler = handler
    adapter = make_adapter(server)
    assert adapter.supports_date_range() is False


def test_fetch_range_uses_the_native_parameters_when_supported(server):
    def handler(url):
        if "date_from=" in url:
            return FakeResponse([{"date": "2020-01-02"}, {"date": "2020-01-01"}])
        raise AssertionError("pagination should not be used")

    server.handler = handler
    adapter = make_adapter(server)
    records = adapter.fetch_range(date(2020, 1, 1), date(2020, 1, 2))
    assert len(records) == 2


def test_fetch_total_pages_uses_the_headers(server):
    def handler(url):
        return FakeResponse([{"date": "2026-10-22"}], headers={"X-WP-TotalPages": "458"})

    server.handler = handler
    adapter = make_adapter(server)
    assert adapter.fetch_total_pages() == 458


def test_source_not_found_is_a_source_error():
    assert issubclass(SourceNotFound, SourceError)
