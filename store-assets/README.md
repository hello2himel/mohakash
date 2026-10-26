# Mohakash — Play Store / F-Droid artwork

Final listing artwork lives in the Fastlane metadata tree, which both Google Play
and F-Droid consume:

- `fastlane/metadata/android/en-US/images/featureGraphic.png` — **1024 × 500**
- `fastlane/metadata/android/en-US/images/phoneScreenshots/01…06.png` — **1080 × 1920** (9:16)
- `fastlane/metadata/android/en-US/images/icon.png` — existing 512 × 512 launcher icon

## Campaign

Campaign line: **“New wallpaper, every day.”**

| # | Install driver | Headline | Support | Screen |
|---|----------------|----------|---------|--------|
| 01 | Fresh wallpaper daily | **New wallpaper, every day.** | Fresh astronomy, ready for your home screen. | *M42: The Great Nebula in Orion* |
| 02 | Set-and-forget live wallpaper | **Set it once. Updated daily.** | Automatic live wallpaper. HD. Wi-Fi only. | Three-phone wallpaper showcase |
| 03 | Discovery | **Random wallpapers, at your fingertips.** | One tap. A surprise from the whole archive. | Random picker over *Comet NEAT* |
| 04 | Keep offline | **Keep the ones you love.** | Save favourites. Read them offline. | Archive and Saved, 2024 filter |
| 05 | Full-quality downloads | **Full resolution. Save or set it.** | The original image, one tap away. | *Asperitas Clouds* entry |
| 06 | Trust | **No ads. No account.** | Open source. No tracking. Just astronomy. | First-run screen |

Feature graphic copy: **“The sky, on your home screen.”**
Product line: “A new astronomy wallpaper every day.”
Colophon: “OPEN SOURCE · NO ADS”

All claims are verifiable in the app: no account, no ads, open source, APOD
metadata and images, automatic wallpaper updates, fill/fit scaling, HD option,
Wi-Fi-only downloads, searchable archive and saved/offline entries. The live
wallpaper is a still picture redrawn when the day changes—not an animated video.

**Image rights:** APOD photographs remain under their publishers' terms; each
campaign frame shows the individual photographer credit inside the real app UI.
The artwork contains no NASA logo and implies no NASA endorsement. Confirm the
specific photographer/APOD promotional-use terms before publishing the listing,
or swap the hero frame for a public-domain APOD image and re-render.

## Design system

- Background: Deep Space `#0B0D12` / brand `#0B1020`, with a subtle generated
  starfield and low-opacity orbit lines.
- Surfaces: `#151922`, borders `#323849`; accent Glow `#8FB0FF`.
- Headline: Newsreader Medium (serif), Starlight `#F4F6FF`, 96 px on the 1080 canvas.
- Support: Inter Regular 34 px, `#D9DFED`, with a soft dark text shadow and a stronger top scrim so it survives thumbnail scale.
- Backgrounds are quieter than the first cut: 260 stars max, no orbit/registration marks, reduced crest wash. The phone is 620 px wide so the UI reads at small sizes.
- Campaign plates deliberately omit a separate wordmark/folio/kicker so the
  real app screenshot is the interface; the app's own in-screen wordmark stays.
- Plate 02 is a three-phone showcase: the comet, M42 and galaxy screens sit
  behind each other like a wallpaper lineup, with the M42 feed as the hero.
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
