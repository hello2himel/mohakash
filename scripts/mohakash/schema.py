"""The normalized, versioned APOD record schema.

This schema is the contract between the data pipeline, the website and the
Android app.  Both clients consume *these* records, never the raw NASA
payload, so a change in NASA's response shape can never leak into a client.

Schema changes must bump :data:`SCHEMA_VERSION` (major version = breaking
field removal/rename, minor version = additive change).
"""

from __future__ import annotations

import datetime as _dt
import re
from dataclasses import dataclass, field
from typing import Any

SCHEMA_VERSION = "1.0.0"

#: The source that produced every record in this archive.
SOURCE_NAME = "NASA APOD"

#: Media types used by the normalized schema.
MEDIA_IMAGE = "image"
MEDIA_VIDEO = "video"
MEDIA_OTHER = "other"
VALID_MEDIA_TYPES = (MEDIA_IMAGE, MEDIA_VIDEO, MEDIA_OTHER)

#: Values of :attr:`ApodRecord.image_status`.
IMAGE_STATUS_OK = "ok"
IMAGE_STATUS_PLACEHOLDER = "placeholder"
IMAGE_STATUS_MISSING = "missing"

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


class SchemaError(ValueError):
    """Raised when a record does not satisfy the normalized schema."""


def _iso_now() -> str:
    return _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


@dataclass
class ApodRecord:
    """One normalized Astronomy Picture of the Day."""

    date: str
    title: str
    explanation: str
    media_type: str
    image_url: str | None
    hd_image_url: str | None
    thumbnail_url: str | None
    apod_url: str
    copyright: str | None
    alt_text: str | None
    source: str = SOURCE_NAME
    updated_at: str = field(default_factory=_iso_now)

    # --- additional, additive fields (schema 1.x) -------------------------
    credit: str | None = None
    source_id: str | None = None
    source_media_type: str | None = None
    image_status: str = IMAGE_STATUS_MISSING
    year: int = 0

    def __post_init__(self) -> None:
        if not self.year and DATE_RE.match(self.date or ""):
            self.year = int(self.date[:4])

    # -- serialisation ----------------------------------------------------
    def to_dict(self) -> dict[str, Any]:
        """Ordered dictionary used for every published JSON document."""
        return {
            "schema_version": SCHEMA_VERSION,
            "date": self.date,
            "title": self.title,
            "explanation": self.explanation,
            "media_type": self.media_type,
            "image_url": self.image_url,
            "hd_image_url": self.hd_image_url,
            "thumbnail_url": self.thumbnail_url,
            "apod_url": self.apod_url,
            "copyright": self.copyright,
            "credit": self.credit,
            "alt_text": self.alt_text,
            "source": self.source,
            "source_id": self.source_id,
            "source_media_type": self.source_media_type,
            "image_status": self.image_status,
            "updated_at": self.updated_at,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ApodRecord":
        """Rebuild a record from a stored dictionary."""
        return cls(
            date=data["date"],
            title=data.get("title") or "",
            explanation=data.get("explanation") or "",
            media_type=data.get("media_type") or MEDIA_OTHER,
            image_url=data.get("image_url"),
            hd_image_url=data.get("hd_image_url"),
            thumbnail_url=data.get("thumbnail_url"),
            apod_url=data.get("apod_url") or "",
            copyright=data.get("copyright"),
            credit=data.get("credit"),
            alt_text=data.get("alt_text"),
            source=data.get("source") or SOURCE_NAME,
            source_id=data.get("source_id"),
            source_media_type=data.get("source_media_type"),
            image_status=data.get("image_status") or IMAGE_STATUS_MISSING,
            updated_at=data.get("updated_at") or _iso_now(),
        )

    # -- validation -------------------------------------------------------
    def validate(self) -> None:
        """Raise :class:`SchemaError` when the record is not publishable."""
        if not DATE_RE.match(self.date or ""):
            raise SchemaError(f"invalid date {self.date!r} (expected YYYY-MM-DD)")
        if not self.title:
            raise SchemaError(f"{self.date}: title must not be empty")
        if self.media_type not in VALID_MEDIA_TYPES:
            raise SchemaError(f"{self.date}: invalid media_type {self.media_type!r}")
        if self.image_status not in (IMAGE_STATUS_OK, IMAGE_STATUS_PLACEHOLDER, IMAGE_STATUS_MISSING):
            raise SchemaError(f"{self.date}: invalid image_status {self.image_status!r}")
        if not self.apod_url:
            raise SchemaError(f"{self.date}: apod_url must not be empty")
        if self.image_status == IMAGE_STATUS_OK and not self.image_url:
            raise SchemaError(f"{self.date}: image_status is 'ok' but image_url is missing")
        if self.image_status != IMAGE_STATUS_OK and self.image_url:
            raise SchemaError(
                f"{self.date}: image_status is {self.image_status!r} but image_url is set"
            )


def is_picture(record: ApodRecord) -> bool:
    """``True`` when the entry is a still picture that can be shown as one.

    A video entry is not, even when it carries a poster frame, and an entry
    whose image could not be resolved is not either.
    """
    return record.media_type == MEDIA_IMAGE and record.image_status == IMAGE_STATUS_OK


def index_entry(record: ApodRecord, detail_path: str) -> dict[str, Any]:
    """Compact representation used in ``archive-index.json``.

    The index intentionally contains no explanations and no duplicate image
    links, so that clients can list thousands of entries without downloading
    megabytes of text.

    ``detail_path`` is relative to the API base URL, which every client knows:
    the website joins it with its same-origin prefix, the Android app with its
    configured base URL.
    """
    return {
        "date": record.date,
        "title": record.title,
        "media_type": record.media_type,
        "thumbnail_url": record.thumbnail_url,
        "apod_url": record.apod_url,
        "copyright": record.copyright,
        "detail_path": detail_path,
    }
