/**
 * Site-wide constants.
 *
 * The owner specific values (API base URL, website URL, repository) are
 * generated from `config/site.json` at the repository root, so they only have
 * to be changed in one place. Run `npm run generate:site` to refresh them;
 * the `predev`, `prebuild` and `pretest` scripts do it automatically.
 */

import {
  FEED_URL,
  ISSUES_URL,
  PUBLIC_API_BASE,
  REPOSITORY_URL,
  SAME_ORIGIN_API_BASE,
  WEBSITE_URL,
} from './site.generated';

export const SITE = {
  name: 'Mohakash',
  tagline: 'Daily astronomy picture, archive and live wallpaper.',
  description:
    'Mohakash is a free, open-source way to read the Astronomy Picture of the Day, browse its archive and put each new picture on your Android home screen.',
  /** Same-origin path the website uses for every request. */
  sameOriginApiBase: SAME_ORIGIN_API_BASE,
  /** Published API base URL (GitHub Pages). Documentation only — never fetched from. */
  publicApiBase: PUBLIC_API_BASE,
  websiteUrl: WEBSITE_URL,
  githubRepo: REPOSITORY_URL,
  /** Latest GitHub release page and the direct APK asset it carries. */
  androidReleaseUrl: `${REPOSITORY_URL}/releases/latest`,
  androidApkUrl: `${REPOSITORY_URL}/releases/latest/download/mohakash.apk`,
  /** Owner's donation page, tagged with this site as the source. */
  donateUrl: 'https://hello2himel.netlify.app/donate',
  issuesUrl: ISSUES_URL,
  feedUrl: FEED_URL,
  rightsUrl: '/rights',
  privacyUrl: '/privacy',
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
