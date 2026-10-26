# Attribution

This folder is a design artifact. Nothing here is shipped. The photographs are stand-ins for APOD
imagery so the dock can be judged over real pictures; the fonts and icons are copied from the app so
the mockup matches it.

## Photographs (mockup only, not shipped)

| File | Work | Author | License | Source |
|---|---|---|---|---|
| `assets/img/return.jpg` | *Return to the Veil Nebula* (POTW 2113a) | ESA/Hubble & NASA, Z. Levay | **CC BY 4.0** — https://creativecommons.org/licenses/by/4.0/ | https://commons.wikimedia.org/wiki/File:Return_to_the_Veil_Nebula.jpg |
| `assets/img/lift.jpg` | *Lifting the Veil* (POTW 2508a) | ESA/Hubble & NASA, R. Sankrit | **CC BY 4.0** — https://creativecommons.org/licenses/by/4.0/ | https://commons.wikimedia.org/wiki/File:Lifting_the_veil_(potw2508a).jpg |
| `assets/img/pillars.jpg` | *Pillars of Creation* (2014 HST WFC3/UVIS) | NASA, ESA, and the Hubble Heritage Team (STScI/AURA) | Public domain | https://commons.wikimedia.org/wiki/File:Pillars_of_creation_2014_HST_WFC3-UVIS_full-res_denoised.jpg |
| `assets/img/carina.jpg` | *Ultraviolet image of the Cygnus Loop Nebula* (crop) | NASA/JPL-Caltech | Public domain | https://commons.wikimedia.org/wiki/File:Ultraviolet_image_of_the_Cygnus_Loop_Nebula_crop.jpg |
| `assets/img/fireworks.jpg` | *NASA Unveils Celestial Fireworks* (Hubble 25th anniversary) | NASA, ESA, and the Hubble Heritage Team | Public domain | https://commons.wikimedia.org/wiki/File:NASA_Unveils_Celestial_Fireworks_as_Official_Hubble_25th_Anniversary_Image.jpg |

**Changes made for this lab:** the files were downscaled for the repository (return and lift 1280 px wide,
pillars/carina/fireworks 300 px wide). In the mockup they are additionally cropped by CSS
(`object-fit: cover`), slightly dimmed and desaturated. The two CC BY images are credited in the
mockup footer and on this page; the attribution requirement is satisfied for the lab copy. If this
mockup is published or reused outside the lab, keep the attribution and the license link.

The mockup's captions describe fictional entries ("The Veil of Cygnus", "A River of Dust in Orion",
etc.) for layout purposes; they do not represent the actual APOD metadata.

## Fonts

| File | Family | License | Upstream |
|---|---|---|---|
| `assets/fonts/inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf` | Inter | SIL Open Font License 1.1 | https://github.com/rsms/inter |
| `assets/fonts/newsreader_medium.ttf` | Newsreader | SIL Open Font License 1.1 | https://github.com/productiontype/Newsreader |

Copied verbatim from `android/app/src/main/res/font/` so the mockup's type matches the app. Only the
mockup uses these copies; the app keeps its own.

## Icons

The SVG paths inside `assets/scene.js` are the production Remix Icon paths, transcribed from the
app's vector drawables (`ic_today_24`, `ic_archive_24`, `ic_rocket_24`, `ic_settings_24`,
`ic_search_24`, `ic_heart_24`, `ic_download_24`, `ic_share_24`).

- Remix Icon 4.9.1 — Copyright (c) 2017–2026 Remix Design
- **Remix Icon License v1.0** — the full text is in
  [`../../licenses/remix-icon-license.txt`](../../licenses/remix-icon-license.txt)
- https://remixicon.com

## Tooling

Screenshots were rendered with the Chromium build that Playwright keeps in
`~/.cache/ms-playwright/chromium-1248` (Google Chrome for Testing 156) through the small CDP helper in
`tools/`. No npm package is installed and nothing from the browser is shipped. `tools/shot.mjs` and
`tools/rects.mjs` are development conveniences only.
