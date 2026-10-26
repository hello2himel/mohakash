# Mohakash — phone screenshot campaign

**Campaign line: “New wallpaper, every day.”**

Six 1080 × 1920 (9:16) RGB screenshots built from real 1080 × 2400 app
captures, uniformly scaled to 54.63% inside an understated device frame.

| # | File | Install driver | Headline | Support | Screen |
|---|------|----------------|----------|---------|--------|
| 01 | `01-new-wallpaper-every-day.png` | Fresh wallpaper daily | New wallpaper,<br>every day. | Fresh astronomy, ready for your home screen. | M42 feed |
| 02 | `02-set-it-once.png` | Automatic live wallpaper | Set it once.<br>Updated daily. | Automatic live wallpaper. HD images. | Three-phone feed showcase |
| 03 | `03-random-wallpapers.png` | Discovery | Random wallpapers,<br>at your fingertips. | One tap. A surprise from the whole archive. | Random picker, Comet NEAT |
| 04 | `04-save-favourites.png` | Keep offline | Keep the ones<br>you love. | Save favourites. Read them offline. | Archive + Saved, 2024 |
| 05 | `05-no-ads-no-account.png` | Trust | No ads.<br>No account. | Open source. No tracking. Just astronomy. | First-run screen |
| 06 | `06-full-resolution-explanation.png` | Full quality + story | Full resolution.<br>With explanation. | The original image, plus the full APOD story. | Asperitas Clouds entry |

## Why these topics

The first screen sells the daily payoff; the next five answer the questions that
make someone install a wallpaper app: *will it update itself, can I find good
pictures, can I keep them offline, is the image full quality, and is it clean?*
No feature is shown that the release does not ship. Language support stays a
product fact but is not used as an install hook.

## Readability and composition

- Headline: Newsreader 96 px, Starlight `#F4F6FF`, soft dark shadow.
- Support: Inter 34 px, `#D9DFED`, stronger top scrim and text shadow so it
  survives the Play thumbnail at ~272 px wide.
- Single-phone plates use a 620 px-wide frame; screen is 1080 × 2400 scaled
  uniformly—no stretching. Plate 02 fans three real feed screens (Earth, 1 January 2017 aurora, Hamburger Galaxy)
  in a shallow V, with the 1 January 2017 feed as the centre card.
- Background: 260 stars maximum, no orbit lines or registration ticks; the
  defocused nebula wash is kept below 0.4 opacity. This removes the visual
  clutter of the first campaign while keeping the Deep Space atmosphere.
- Headline and support are centred on the full 1080 canvas.
- Critical text sits between y=150 and y=430, above the phone; the phone bottom
  is at y≈1872. No copy touches the outer 40 px side margins.
- Each plate carries one headline, one support line and one accent full stop.

## Compliance

- 1080 × 1920, 9:16, 8-bit RGB, no alpha; 0.9–1.6 MB each.
- Six screenshots (Play allows 2–8); feature graphic is 1024 × 500 RGB.
- Real app UI inside the frame; no fake ratings, awards, NASA marks or drawn UI.
- The live wallpaper is a still image redrawn daily—the copy does not call it an
  animation. "HD images" refers to the real optional download setting.
- APOD photographs are credited inside the app; confirm promotional-use terms
  before publishing, or swap the hero frame for a public-domain APOD image.

## Rebuild

```bash
cd store-assets/phone
PLAYWRIGHT_DIR=/home/himel/Documents/moydan/node_modules node src/render.mjs
python3 src/postprocess.py
```

Sources: `src/assets/screenshots/`, `src/plates.mjs`, `src/styles.css`,
`src/compose.mjs`, and the local Inter/Newsreader font files. Review sheets:
`src/out/contact-sheet.png` and `src/out/thumbnail-check.png`.
