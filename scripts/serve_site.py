#!/usr/bin/env python3
"""Serve the built website the way Netlify would.

This mimics the rewrite rules in ``netlify.toml``:

* ``/api/apod/v1/*``  ->  the published API (served from ``data/api/v1/``)
* ``/feed.xml``       ->  the published feed
* ``/archive/:date``  ->  the static entry shell

It exists so that the site can be checked locally, including the runtime data
fetch, without a Netlify account.

Usage::

    python3 scripts/serve_site.py --port 4321
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

REPO_ROOT = Path(__file__).resolve().parents[1]
WEB_DIST = REPO_ROOT / "web" / "dist"
DATA_ROOT = REPO_ROOT / "data"

DATE_ROUTE = re.compile(r"^/archive/(\d{4}-\d{2}-\d{2})/?$")


class Handler(BaseHTTPRequestHandler):
    server_version = "MohakashLocal/1.0"

    def log_message(self, fmt, *args):  # pragma: no cover - quieter output
        print("  %s" % (fmt % args))

    # -- helpers ----------------------------------------------------------
    def _send(self, body: bytes, content_type: str, status: int = 200, cache: str | None = None):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        if cache:
            self.send_header("Cache-Control", cache)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _send_file(self, path: Path, cache: str | None = None):
        content_type = mimetypes.guess_type(str(path))[0] or "application/octet-stream"
        self._send(path.read_bytes(), content_type, cache=cache)

    def _not_found(self):
        self._send(b"Not found", "text/plain; charset=utf-8", status=404)

    # -- routing ----------------------------------------------------------
    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = urlparse(self.path).path

        # /api/apod/v1/* -> data/api/v1/*
        if path.startswith("/api/apod/v1/"):
            relative = path[len("/api/apod/v1/") :]
            target = (DATA_ROOT / "api" / "v1" / relative).resolve()
            if not str(target).startswith(str((DATA_ROOT / "api").resolve())) or not target.is_file():
                return self._not_found()
            cache = "public, max-age=300, must-revalidate"
            if relative.startswith("archive/"):
                cache = "public, max-age=86400, stale-while-revalidate=3600"
            return self._send_file(target, cache=cache)

        # /feed.xml -> data/feed.xml
        if path == "/feed.xml":
            target = DATA_ROOT / "feed.xml"
            if target.is_file():
                return self._send_file(target, cache="public, max-age=3600")
            return self._not_found()

        # /archive/:date -> the static entry shell
        match = DATE_ROUTE.match(path)
        if match:
            shell = WEB_DIST / "archive" / "entry" / "index.html"
            if shell.is_file():
                return self._send_file(shell)
            return self._not_found()

        # static files
        relative = path.lstrip("/") or "index.html"
        target = (WEB_DIST / relative).resolve()
        if not str(target).startswith(str(WEB_DIST.resolve())):
            return self._not_found()
        if target.is_dir():
            target = target / "index.html"
        if target.is_file():
            return self._send_file(target)
        fallback = WEB_DIST / "404.html"
        if fallback.is_file():
            return self._send_file(fallback)
        return self._not_found()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Serve the built website with the Netlify rewrites")
    parser.add_argument("--port", type=int, default=4321)
    parser.add_argument("--host", default="127.0.0.1")
    args = parser.parse_args(argv)

    if not WEB_DIST.is_dir():
        print("web/dist does not exist. Run `npm run build` in web/ first.", flush=True)
        return 1

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"serving {WEB_DIST} on http://{args.host}:{args.port} (API from {DATA_ROOT / 'api'})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
