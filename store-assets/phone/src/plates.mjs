/**
 * Mohakash — phone store campaign (1080x1920)
 * Six plates in running order. One headline + one support line each.
 *
 * The campaign deliberately draws from the whole archive, not the last week:
 * 2003, 2013, 2014, 2020, 2024 and 2025 appear across the six plates. The app's
 * own random picker supplied the 2003, 2013, 2014, 2020 and 2025 screens; the
 * archive plate is filtered to 2024. Plate 02 is the exception — a first-run
 * wallpaper screen whose small preview is the latest APOD (M42).
 *
 * `bg` is a crop/scale of a real app screenshot used as an out-of-focus sky
 * wash. It is never presented as app UI.
 */

export const PLATES = [
  {
    id: '01',
    file: '01-new-wallpaper-every-day.png',
    lines: ['New wallpaper,', 'every day.'],
    support: 'Fresh astronomy, ready for your home screen.',
    screen: 'brand-final-feed.png',
    bg: { src: 'brand-final-feed.png', x: -260, y: -900, scale: 1.55, opacity: 0.34 },
    seed: 51901,
    note: 'The daily feed with the brightest hero image; this is the install moment.',
  },
  {
    id: '02',
    file: '02-set-it-once.png',
    lines: ['Set it once.', 'Updated daily.'],
    support: 'Automatic live wallpaper. HD images.',
    kind: 'showcase',
    phones: [
      { screen: 'feed-comet.png', x: -78, y: 612, w: 478, r: -11, z: 2 },
      { screen: 'feed-2017-01-01.png', x: 260, y: 478, w: 560, r: 0, z: 3 },
      { screen: 'feed-hamburger.png', x: 678, y: 602, w: 478, r: 11, z: 1 },
    ],
    bg: { src: 'feed-2017-01-01.png', x: -400, y: -520, scale: 1.7, opacity: 0.20 },
    seed: 51902,
    note: 'Three real feed screens fanned like a wallpaper showdown: Earth, the 1 January 2017 aurora and the Hamburger Galaxy. No M42 or entry-page reuse.',
  },
  {
    id: '03',
    file: '03-random-wallpapers.png',
    lines: ['Random wallpapers,', 'at your fingertips.'],
    support: 'One tap. A surprise from the whole archive.',
    screen: 'overlay-comet.png',
    bg: { src: 'feed-comet.png', x: -300, y: -800, scale: 1.6, opacity: 0.38 },
    seed: 51903,
    note: 'Random picker over Comet NEAT; the benefit is discovery, not a specific date.',
  },
  {
    id: '04',
    file: '04-save-favourites.png',
    lines: ['Keep the ones', 'you love.'],
    support: 'Save favourites. Read them offline.',
    screen: 'archive-2024.png',
    bg: { src: 'feed-hamburger.png', x: -760, y: -200, scale: 1.85, opacity: 0.18 },
    seed: 51904,
    note: 'Archive and Saved tabs; search three decades and keep pictures offline.',
  },
  {
    id: '05',
    file: '05-no-ads-no-account.png',
    lines: ['No ads.', 'No account.'],
    support: 'Open source. No tracking. Just astronomy.',
    screen: 'store-onboarding.png',
    bg: { src: 'brand-final-feed.png', x: -360, y: -720, scale: 1.7, opacity: 0.24 },
    seed: 51905,
    note: 'First-run screen; the copy states the no-account, no-ads, open-source promise.',
  },
  {
    id: '06',
    file: '06-full-resolution-explanation.png',
    lines: ['Full resolution.', 'With explanation.'],
    support: 'The original image, plus the full APOD story.',
    screen: 'entry-asperitas.png',
    bg: { src: 'entry-asperitas.png', x: -420, y: -600, scale: 1.5, opacity: 0.30 },
    seed: 51906,
    note: 'Full entry with the original image, credit, explanation, Save image and Set as wallpaper.',
  },
];

export const DIMENSIONS = { width: 1080, height: 1920 };
