"""Normalization tests against saved NASA payloads."""

from __future__ import annotations

import datetime as _dt

import pytest

from mohakash.normalize import (
    derive_image_urls,
    html_to_text,
    is_image_url,
    is_page_url,
    is_placeholder_image_url,
    normalize_explanation,
    normalize_media_type,
    normalize_record,
)
from mohakash.schema import (
    IMAGE_STATUS_MISSING,
    IMAGE_STATUS_OK,
    IMAGE_STATUS_PLACEHOLDER,
    MEDIA_IMAGE,
    MEDIA_OTHER,
    MEDIA_VIDEO,
)


def test_standard_image_record(fixture_image):
    record = normalize_record(fixture_image)

    assert record.date == "2026-10-22"
    assert record.title == "The First Green Star, WD J1330+6435"
    assert record.media_type == MEDIA_IMAGE
    assert record.image_status == IMAGE_STATUS_OK
    assert record.image_url is not None
    assert record.image_url.startswith("https://")
    assert record.hd_image_url is not None
    assert record.thumbnail_url is not None
    assert record.apod_url.startswith("https://science.nasa.gov/")
    record.validate()


def test_nasa_url_field_is_a_page_not_an_image(fixture_image):
    """NASA's ``url`` points at the article page, ``hdurl`` at the picture."""
    assert is_page_url(fixture_image["url"])
    assert not is_image_url(fixture_image["url"])
    assert is_image_url(fixture_image["hdurl"])

    record = normalize_record(fixture_image)
    assert record.apod_url == fixture_image["permalink"]
    assert record.image_url != fixture_image["url"]


def test_video_record_uses_the_poster_frame(fixture_video):
    record = normalize_record(fixture_video)

    assert record.media_type == MEDIA_VIDEO
    assert record.source_media_type == "video"
    assert record.image_status == IMAGE_STATUS_OK
    # Videos still carry a usable poster image, which the wallpaper can use.
    assert record.image_url is not None
    record.validate()


def test_old_record_without_a_real_image_is_marked_placeholder(fixture_old_placeholder):
    record = normalize_record(fixture_old_placeholder)

    assert record.date == "1995-06-16"
    assert record.image_status == IMAGE_STATUS_PLACEHOLDER
    assert record.image_url is None
    assert record.hd_image_url is None
    assert record.thumbnail_url is None
    record.validate()


def test_missing_credit_is_null_not_invented(fixture_old_placeholder):
    record = normalize_record(fixture_old_placeholder)

    assert fixture_old_placeholder.get("credit") == ""
    assert record.credit is None
    assert record.copyright is None


def test_credit_is_plain_text(fixture_image):
    record = normalize_record(fixture_image)

    assert record.credit == "Mark A. Garlick/University of Warwick"
    assert "<" not in (record.credit or "")


def test_explanation_is_plain_text_without_the_redundant_label(fixture_image):
    record = normalize_record(fixture_image)

    assert not record.explanation.startswith("Explanation:")
    assert "<strong>" not in record.explanation
    assert "<a " not in record.explanation
    assert "green star" in record.explanation.lower()


def test_alt_text_is_kept_when_the_source_supplies_one(fixture_image):
    record = normalize_record(fixture_image)
    assert record.alt_text
    assert record.alt_text.startswith("A star shines")


def test_generic_alt_text_is_dropped():
    raw = {
        "date": "1995-07-04",
        "title": "Example",
        "alt": "See Explanation. Clicking on the picture will download the highest resolution version available.",
        "hdurl": "https://assets.science.nasa.gov/dynamicimage/assets/x.jpg",
        "permalink": "https://science.nasa.gov/example/",
    }
    assert normalize_record(raw).alt_text is None


def test_record_without_an_image_url(fixture_image):
    raw = dict(fixture_image)
    raw["hdurl"] = None
    record = normalize_record(raw)

    assert record.image_status == IMAGE_STATUS_MISSING
    assert record.image_url is None
    record.validate()


def test_record_with_a_page_url_instead_of_an_image(fixture_image):
    """A page URL must never be published as an image."""
    raw = dict(fixture_image)
    raw["hdurl"] = "https://science.nasa.gov/image-article/apod-2026-october-22/"
    record = normalize_record(raw)

    assert record.image_status == IMAGE_STATUS_MISSING
    assert record.image_url is None
    record.validate()


def test_record_with_an_insecure_image_url(fixture_image):
    raw = dict(fixture_image)
    raw["hdurl"] = "http://example.com/image.jpg"
    assert normalize_record(raw).image_status == IMAGE_STATUS_MISSING


def test_record_without_a_date_is_rejected():
    with pytest.raises(ValueError):
        normalize_record({"title": "No date"})


def test_media_type_mapping():
    assert normalize_media_type("image") == MEDIA_IMAGE
    assert normalize_media_type("video") == MEDIA_VIDEO
    assert normalize_media_type("iframe") == MEDIA_OTHER
    assert normalize_media_type(None) == MEDIA_OTHER
    assert normalize_media_type("") == MEDIA_OTHER


def test_html_to_text_handles_entities_and_tags():
    assert html_to_text("<p>Tom &amp; Jerry</p>") == "Tom & Jerry"
    assert html_to_text("<script>alert(1)</script>Hello") == "Hello"
    assert html_to_text("a<br>b") == "a\nb"
    assert html_to_text("<strong>Explanation:</strong> Text") == "Explanation: Text"
    assert html_to_text(None) == ""


def test_normalize_explanation_removes_only_the_first_label():
    text = "<p>Explanation: First part.</p><p>Second part.</p>"
    assert normalize_explanation(text).startswith("First part.")


def test_derive_image_urls_resizes_dynamic_images():
    source = "https://assets.science.nasa.gov/dynamicimage/assets/x.jpg?w=1280&h=1280&fit=clip&crop=faces"
    image, hd, thumb = derive_image_urls(source)

    assert image == "https://assets.science.nasa.gov/dynamicimage/assets/x.jpg?w=1600"
    assert hd == "https://assets.science.nasa.gov/dynamicimage/assets/x.jpg?w=2400"
    assert thumb == "https://assets.science.nasa.gov/dynamicimage/assets/x.jpg?w=480"


def test_derive_image_urls_keeps_static_assets_untouched():
    """Static assets are reachable through the resizing CDN as well."""
    source = "https://assets.science.nasa.gov/content/dam/science/cds/apod/apod/1999/june/x_big.jpg"
    image, hd, thumb = derive_image_urls(source)

    assert image == "https://assets.science.nasa.gov/dynamicimage/assets/science/cds/apod/apod/1999/june/x_big.jpg?w=1600"
    assert hd == "https://assets.science.nasa.gov/dynamicimage/assets/science/cds/apod/apod/1999/june/x_big.jpg?w=2400"
    assert thumb == "https://assets.science.nasa.gov/dynamicimage/assets/science/cds/apod/apod/1999/june/x_big.jpg?w=480"


def test_derive_image_urls_ignores_unknown_hosts():
    source = "https://example.com/photo.jpg"
    image, hd, thumb = derive_image_urls(source)
    assert image == source
    assert thumb is None


def test_derive_image_urls_rejects_placeholder_and_pages():
    placeholder = "https://assets.science.nasa.gov/dynamicimage/assets/science/astro/programs/cosmic-origins/images/misc/news-thumbnail.png?w=594&h=516"
    assert derive_image_urls(placeholder) == (None, None, None)
    assert derive_image_urls("https://science.nasa.gov/image-article/x/") == (None, None, None)
    assert derive_image_urls("not a url") == (None, None, None)


def test_placeholder_detection():
    assert is_placeholder_image_url(
        "https://assets.science.nasa.gov/dynamicimage/assets/science/astro/programs/cosmic-origins/images/misc/news-thumbnail.png?w=594"
    )
    assert not is_placeholder_image_url("https://assets.science.nasa.gov/dynamicimage/assets/x.jpg")


def test_old_era_record_with_a_real_image(fixture_old_image):
    record = normalize_record(fixture_old_image)

    assert record.date == "2000-06-15"
    assert record.image_status == IMAGE_STATUS_OK
    assert record.image_url is not None
    record.validate()


def test_updated_at_is_set_to_the_supplied_moment():
    moment = _dt.datetime(2026, 10, 22, 7, 17, 0, tzinfo=_dt.timezone.utc)
    record = normalize_record({"date": "2026-10-22", "title": "x"}, now=moment)
    assert record.updated_at == "2026-10-22T07:17:00Z"


def test_renormalize_rebuilds_the_urls_of_a_stored_record():
    from mohakash.normalize import renormalize_record

    record = normalize_record(
        {
            "date": "2020-12-01",
            "title": "x",
            "hdurl": "https://assets.science.nasa.gov/content/dam/science/cds/apod/apod/2020/december/x.jpg?w=1412&h=867&fit=clip",
            "permalink": "https://science.nasa.gov/x/",
        }
    )
    # The stored record was produced before the static assets were routed
    # through the resizing CDN.
    record.image_url = record.hd_image_url
    record.thumbnail_url = None

    updated = renormalize_record(record)

    assert updated.image_url == "https://assets.science.nasa.gov/dynamicimage/assets/science/cds/apod/apod/2020/december/x.jpg?w=1600"
    assert updated.hd_image_url.endswith("x.jpg?w=2400")
    assert updated.thumbnail_url.endswith("x.jpg?w=480")


def test_renormalize_leaves_records_without_an_image_alone():
    from mohakash.normalize import renormalize_record

    record = normalize_record(
        {
            "date": "1995-06-16",
            "title": "x",
            "hdurl": "https://assets.science.nasa.gov/dynamicimage/assets/science/astro/programs/cosmic-origins/images/misc/news-thumbnail.png?w=594",
            "permalink": "https://science.nasa.gov/x/",
        }
    )
    updated = renormalize_record(record)
    assert updated.image_status == IMAGE_STATUS_PLACEHOLDER
    assert updated.image_url is None
