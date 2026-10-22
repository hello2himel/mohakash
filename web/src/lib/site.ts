/**
 * Site-wide constants.
 *
 * These mirror `config/site.json` at the repository root. The website is built
 * from the `web/` folder alone (Netlify build base), so it cannot read the
 * root config at build time — keep the two in sync when the owner, repository
 * or domain changes.
 */

export const SITE = {
  name: 'Mohakash',
  tagline: 'Daily astronomy picture, archive and live wallpaper.',
  description:
    'Mohakash is a free, open-source way to read the Astronomy Picture of the Day, browse its archive and put each new picture on your Android home screen.',
  /** Same-origin path the website uses for every request. */
  sameOriginApiBase: '/api/apod/v1/',
  /** Published API base URL (GitHub Pages). Documentation only — never fetched from. */
  publicApiBase: 'https://hello2himel.github.io/mohakash/api/v1/',
  websiteUrl: 'https://mohakash.netlify.app',
  githubRepo: 'https://github.com/hello2himel/mohakash',
  issuesUrl: 'https://github.com/hello2himel/mohakash/issues',
  rightsUrl: '/rights',
} as const;

export const NAV_LINKS = [
  { href: '/', label: 'Today' },
  { href: '/archive', label: 'Archive' },
  { href: '/api', label: 'API' },
  { href: '/about', label: 'About' },
] as const;

/** Text shared by the footer and the mobile menu. */
export const NOT_AFFILIATED =
  'Image rights belong to their creators. Independent project, not affiliated with or endorsed by NASA.';
