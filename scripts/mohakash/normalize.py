"""Turn raw NASA payloads into normalized :class:`~mohakash.schema.ApodRecord`.

All provider specific knowledge lives in :mod:`mohakash.nasa_adapter`; this
module only knows how to turn *some* provider payload into the project's
schema.  That keeps the adapter replaceable if NASA moves the API again.
"""

from __future__ import annotations

import datetime as _dt
import html as _html
import re
from typing import Any
from urllib.parse import urlsplit, urlunsplit

from .schema import (
    IMAGE_STATUS_MISSING,
    IMAGE_STATUS_OK,
    IMAGE_STATUS_PLACEHOLDER,
    MEDIA_IMAGE,
    MEDIA_OTHER,
    MEDIA_VIDEO,
    ApodRecord,
)

#: Extensions accepted as "this URL points at an image file".
IMAGE_EXTENSIONS = (".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif", ".bmp")

#: NASA's generic placeholder thumbnail, used by some pre-2000 entries when the
#: original scan was not migrated.  It is a real image but it is *not* the
#: picture of the day, so it must never be shown as one.
PLACEHOLDER_MARKERS = ("news-thumbnail", "/misc/news-thumbnail.png")

#: Boilerplate alt text that NASA attaches to entries without a real
#: description.  It is not an alt text for the image, so it is dropped.
GENERIC_ALT_TEXTS = {
    "see explanation. clicking on the picture will download the highest resolution version available.",
    "see explanation.",
}

#: NASA prefixes every explanation with a redundant "Explanation:" label.
_EXPLANATION_PREFIX_RE = re.compile(r"^\s*explanation\s*:\s*", re.IGNORECASE)

#: The trailing teaser NASA adds to most explanations.
_TRAILER_RE = re.compile(r"\s*Tomorrow'?s picture\s*:.*$", re.IGNORECASE | re.DOTALL)

_SCRIPT_RE = re.compile(r"<(script|style)\b[^>]*>.*?</\1>", re.IGNORECASE | re.DOTALL)
_BLOCK_END_RE = re.compile(r"</\s*(p|div|li|tr|h[1-6]|blockquote|section)\s*>", re.IGNORECASE)
_BR_RE = re.compile(r"<\s*br\s*/?\s*>", re.IGNORECASE)
_TAG_RE = re.compile(r"<[^>]+>")
_WS_RE = re.compile(r"[ \t\r\f\v]+")
_MULTI_NEWLINE_RE = re.compile(r"\n{3,}")


# --------------------------------------------------------------------------
# text helpers
# --------------------------------------------------------------------------
def html_to_text(value: str | None) -> str:
    """Convert an HTML fragment into readable plain text.

    The result is safe to render as text in any client: no tags, no entities,
    no scripts.
    """
    if not value:
        return ""
    text = _SCRIPT_RE.sub(" ", value)
    text = _BR_RE.sub("\n", text)
    text = _BLOCK_END_RE.sub("\n", text)
    text = _TAG_RE.sub("", text)
    text = _html.unescape(text)
    text = text.replace("\u00a0", " ")
    text = _WS_RE.sub(" ", text)
    text = "\n".join(line.strip() for line in text.split("\n"))
    text = _MULTI_NEWLINE_RE.sub("\n\n", text)
    return text.strip()


def clean_optional_text(value: str | None) -> str | None:
    """Normalise a free text field, returning ``None`` when it is empty."""
    if value is None:
        return None
    text = html_to_text(value)
    text = _WS_RE.sub(" ", text).strip()
    return text or None


def normalize_explanation(value: str | None) -> str:
    """Plain text explanation without the redundant "Explanation:" label."""
    text = html_to_text(value)
    text = _EXPLANATION_PREFIX_RE.sub("", text, count=1)
    return text.strip()


# --------------------------------------------------------------------------
# URL helpers
# --------------------------------------------------------------------------
def is_https_url(value: str | None) -> bool:
    if not value or not isinstance(value, str):
        return False
    parts = urlsplit(value.strip())
    return parts.scheme == "https" and bool(parts.netloc)


def is_page_url(value: str | None) -> bool:
    """True when the URL points at a web page rather than at a file."""
    if not is_https_url(value):
        return False
    path = urlsplit(value.strip()).path
    if not path or path.endswith("/"):
        return True
    return path.lower().endswith((".html", ".htm", ".php", ".aspx"))


def is_image_url(value: str | None) -> bool:
    """True when the URL is a direct HTTPS link to an image file."""
    if not is_https_url(value):
        return False
    parts = urlsplit(value.strip())
    path = parts.path.lower()
    return path.endswith(IMAGE_EXTENSIONS)


def is_placeholder_image_url(value: str | None) -> bool:
    if not is_https_url(value):
        return False
    path = urlsplit(value.strip()).path.lower()
    return any(marker in path for marker in PLACEHOLDER_MARKERS)


def _strip_resize_params(url: str) -> str:
    """Remove the CDN resize query string from a dynamic image URL."""
    parts = urlsplit(url)
    return urlunsplit((parts.scheme, parts.netloc, parts.path, "", ""))


def _with_width(url: str, width: int) -> str:
    parts = urlsplit(url)
    query = f"w={width}"
    return urlunsplit((parts.scheme, parts.netloc, parts.path, query, ""))


#: NASA's image CDN serves two families of assets:
#:
#: * ``/dynamicimage/assets/...`` - already a resizable asset
#: * ``/content/dam/...``           - a static file
#:
#: Both are reachable through the ``/dynamicimage/assets/`` route, which
#: re-encodes and resizes on the fly.  Converting the static family therefore
#: makes real thumbnails available for the whole archive.
_DYNAMIC_PATH_MARKER = "/dynamicimage/assets/"
_STATIC_PATH_MARKER = "/content/dam/"


def _to_dynamic_asset(url: str) -> str:
    """Return the resizable CDN URL for a NASA image asset."""
    if _DYNAMIC_PATH_MARKER in url:
        return _strip_resize_params(url)
    if _STATIC_PATH_MARKER in url:
        return url.replace(_STATIC_PATH_MARKER, _DYNAMIC_PATH_MARKER, 1)
    return _strip_resize_params(url)


def _supports_resize(url: str) -> bool:
    return _DYNAMIC_PATH_MARKER in url or _STATIC_PATH_MARKER in url


def derive_image_urls(source_url: str | None) -> tuple[str | None, str | None, str | None]:
    """Return ``(image_url, hd_image_url, thumbnail_url)`` for a source image.

    * ``image_url``      display size, 1600px wide
    * ``hd_image_url``  large size, 2400px wide
    * ``thumbnail_url`` small preview, 480px wide

    All three point at NASA's own asset CDN.  Images that the CDN cannot
    resize keep their original URL and get no separate thumbnail; clients then
    fall back to the full image.
    """
    if not source_url or not is_https_url(source_url):
        return None, None, None
    if not is_image_url(source_url):
        return None, None, None
    if is_placeholder_image_url(source_url):
        return None, None, None

    if _supports_resize(source_url):
        base = _to_dynamic_asset(source_url)
        return _with_width(base, 1600), _with_width(base, 2400), _with_width(base, 480)
    return source_url, source_url, None


# --------------------------------------------------------------------------
# record normalisation
# --------------------------------------------------------------------------
def normalize_media_type(value: Any) -> str:
    """Map a source media type onto the normalized vocabulary."""
    if not isinstance(value, str):
        return MEDIA_OTHER
    lowered = value.strip().lower()
    if lowered == MEDIA_IMAGE:
        return MEDIA_IMAGE
    if lowered == MEDIA_VIDEO:
        return MEDIA_VIDEO
    return MEDIA_OTHER


def _normalize_date(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    candidate = value.strip()[:10]
    try:
        return _dt.date.fromisoformat(candidate).isoformat()
    except ValueError:
        return None


def renormalize_record(record: "ApodRecord") -> "ApodRecord":
    """Re-derive the image URLs of an already stored record.

    Used when the derivation rules change: the stored record keeps the
    information that is needed to rebuild the URLs, so no network request is
    required.
    """
    source = record.hd_image_url or record.image_url
    if not source:
        return record
    image_url, hd_image_url, thumbnail_url = derive_image_urls(source)
    if not image_url:
        return record
    record.image_url = image_url
    record.hd_image_url = hd_image_url
    record.thumbnail_url = thumbnail_url
    record.image_status = IMAGE_STATUS_OK
    return record


def normalize_record(raw: dict[str, Any], *, now: _dt.datetime | None = None) -> ApodRecord:
    """Convert one raw NASA payload into a normalized record.

    Raises ``ValueError`` when the payload does not even contain a date and a
    title, i.e. when the record cannot be published at all.
    """
    date = _normalize_date(raw.get("date"))
    if not date:
        raise ValueError("payload without a usable date")

    title = clean_optional_text(raw.get("title")) or "Untitled"

    # NASA's "url" points at the APOD *article page*, not at an image.  The
    # article URL is the canonical page for an entry; the image lives in
    # "hdurl".
    article_url = raw.get("permalink") or raw.get("url")
    apod_url = article_url.strip() if isinstance(article_url, str) and is_https_url(article_url) else ""

    raw_image = raw.get("hdurl")
    raw_image = raw_image.strip() if isinstance(raw_image, str) else None
    if raw_image and (not is_https_url(raw_image) or not is_image_url(raw_image)):
        raw_image = None

    if raw_image and is_placeholder_image_url(raw_image):
        image_url = hd_image_url = thumbnail_url = None
        image_status = IMAGE_STATUS_PLACEHOLDER
    else:
        image_url, hd_image_url, thumbnail_url = derive_image_urls(raw_image)
        image_status = IMAGE_STATUS_OK if image_url else IMAGE_STATUS_MISSING

    alt_text = clean_optional_text(raw.get("alt"))
    if alt_text and alt_text.strip().lower() in GENERIC_ALT_TEXTS:
        alt_text = None

    source_id = raw.get("post_id")
    timestamp = (now or _dt.datetime.now(_dt.timezone.utc)).replace(microsecond=0)
    updated_at = timestamp.isoformat().replace("+00:00", "Z")

    return ApodRecord(
        date=date,
        title=title,
        explanation=normalize_explanation(raw.get("explanation")),
        media_type=normalize_media_type(raw.get("media_type")),
        image_url=image_url,
        hd_image_url=hd_image_url,
        thumbnail_url=thumbnail_url,
        apod_url=apod_url,
        copyright=clean_optional_text(raw.get("copyright")),
        credit=clean_optional_text(raw.get("credit")),
        alt_text=alt_text,
        source_id=str(source_id) if isinstance(source_id, (int, str)) and source_id != "" else None,
        source_media_type=raw.get("media_type") if isinstance(raw.get("media_type"), str) else None,
        image_status=image_status,
        updated_at=updated_at,
    )
