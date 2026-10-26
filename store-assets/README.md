# Mohakash — Play Store / F-Droid artwork

Final listing artwork lives in the Fastlane metadata tree, which both Google Play
and F-Droid consume:

- `fastlane/metadata/android/en-US/images/featureGraphic.png` — **1024 × 500**
- `fastlane/metadata/android/en-US/images/phoneScreenshots/01…06.png` — **1080 × 1920** (9:16)
- `fastlane/metadata/android/en-US/images/icon.png` — existing 512 × 512 launcher icon

## Campaign

Campaign line: **“The sky, every day.”**

| # | Beat | Headline | Support | Screen |
|---|------|----------|---------|--------|
| 01 | Daily feed | **The sky, every day.** | A new picture of the universe, each day. | *The Tail of the Hamburger Galaxy*, 8 May 2014 |
| 02 | Live wallpaper | **Your wallpaper, alive.** | A live wallpaper that follows the day's picture. | first-run wallpaper screen, M42 preview |
| 03 | Random pick | **Let the sky surprise you.** | One tap opens a random picture from the archive. | random picker over *Comet NEAT*, 6 Mar 2003 |
| 04 | Archive | **Every picture, kept.** | Search three decades, save what you love. | Archive filtered to 2024 |
| 05 | Full entry | **More than a pretty picture.** | The title, the credit, the whole explanation. | *Asperitas Clouds Over New Zealand*, 17 Aug 2025 |
| 06 | Languages | **In your language.** | The interface, in fourteen languages. | Bengali UI on *Plato and the Lunar Alps*, 15 Jan 2026 |

Feature graphic copy: **“The sky, on your home screen.”**
Product line: “A new astronomy wallpaper every day.”
Colophon: “OPEN SOURCE · NO ADS”

All claims are verifiable in the app: no account, no ads, open source, APOD
metadata and images, 13 non-English UI locales plus English variants, live
wallpaper, archive and saved list. The live wallpaper is a still picture
redrawn when the day changes—not an animated video.

**Image rights:** APOD photographs remain under their publishers' terms; each
campaign frame shows the individual photographer credit inside the real app UI.
The artwork contains no NASA logo and implies no NASA endorsement. Confirm the
specific photographer/APOD promotional-use terms before publishing the listing,
or swap the hero frame for a public-domain APOD image and re-render.

## Design system

- Background: Deep Space `#0B0D12` / brand `#0B1020`, with a subtle generated
  starfield and low-opacity orbit lines.
- Surfaces: `#151922`, borders `#323849`; accent Glow `#8FB0FF`.
- Headline: Newsreader Medium (serif), Starlight `#F4F6FF`.
- Kicker/support/UI: Inter Regular/Medium; muted `#A3ABBD`.
- Campaign plates deliberately omit a separate wordmark/folio/kicker so the
  real app screenshot is the interface; the app's own in-screen wordmark stays.
- Device frames are intentionally understated; the app screenshot inside is a
  real 1080 × 2400 capture, uniformly scaled (never stretched).
- Feature graphic keeps critical type well inside the central 80% safe area;
  Play’s overlaid controls cannot cover the headline or brand.
- Screenshot critical text sits above the bottom 8% and below the top 6%;
  no store-specific badges, ratings, awards or NASA endorsement marks.

## Sources / rebuilding

- `store-assets/phone/src/` — HTML/CSS/JS compositions plus the real app
  screenshots, local Inter/Newsreader fonts and brand SVG. Rebuild with
  `node src/render.mjs` then `python3 src/postprocess.py` if Playwright is
  available; `src/out/` contains 2× renders and validation reports.
- `store-assets/feature/` — feature graphic composition and `feature.html`
  source; the exported 1024 × 500 PNG is the final asset.

## Store policy notes

- Screenshots show the actual app UI; the onboarding/live-wallpaper frame is a
  genuine first-run screen, not a fabricated mockup.
- No fake reviews, download counts, rankings or “Editor’s Choice” claims.
- No NASA logo or implied NASA endorsement; APOD credit is visible in the app.
- F-Droid uses the same Fastlane screenshots and feature graphic; no proprietary
  assets are embedded in the APK.
