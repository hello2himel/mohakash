/**
 * Date and relative-time formatting used across the site.
 * All functions are pure so they can be unit tested without a DOM.
 */

const MONTHS_LONG = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

const MONTHS_SHORT = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
];

function parseDate(value: string | number | Date): Date | null {
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  if (typeof value === 'number') {
    const fromNumber = new Date(value);
    return Number.isNaN(fromNumber.getTime()) ? null : fromNumber;
  }
  const text = value.trim();
  if (!text) return null;

  // A bare `YYYY-MM-DD` is a calendar date, not a UTC instant.
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
  if (dateOnly) {
    const year = Number(dateOnly[1]);
    const month = Number(dateOnly[2]);
    const day = Number(dateOnly[3]);
    const parsed = new Date(Date.UTC(year, month - 1, day));
    if (parsed.getUTCFullYear() !== year || parsed.getUTCMonth() !== month - 1 || parsed.getUTCDate() !== day) {
      return null;
    }
    return parsed;
  }

  const parsed = new Date(text);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

/** `2026-10-22` → `22 October 2026`. Returns an empty string for invalid input. */
export function formatDate(value: string | number | Date): string {
  const date = parseDate(value);
  if (!date) return '';
  return `${date.getUTCDate()} ${MONTHS_LONG[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
}

/** `2026-10-22` → `22 Oct 2026`. */
export function formatDateShort(value: string | number | Date): string {
  const date = parseDate(value);
  if (!date) return '';
  return `${date.getUTCDate()} ${MONTHS_SHORT[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
}

/** Calendar year of an ISO date string. `0` when the value cannot be parsed. */
export function yearOf(value: string): number {
  const date = parseDate(value);
  return date ? date.getUTCFullYear() : 0;
}

/** `true` when the value is a zero-padded `YYYY-MM-DD` calendar date. */
export function isIsoDate(value: string): boolean {
  return parseDate(value) !== null && /^\d{4}-\d{2}-\d{2}$/.test(value.trim());
}

/**
 * Relative time such as `3 hours ago`.
 *
 * `formatRelative(from, now)` → `just now`, `12 minutes ago`, `3 hours ago`,
 * `2 days ago`, `5 months ago`, `a year ago`.
 */
export function plural(value: number, unit: string): string {
  return `${value} ${unit}${value === 1 ? '' : 's'} ago`;
}

export function formatRelative(from: string | number | Date, now: string | number | Date = Date.now()): string {
  const start = parseDate(from);
  const current = parseDate(now);
  if (!start || !current) return '';

  const seconds = Math.round((current.getTime() - start.getTime()) / 1000);
  if (seconds < 45) return 'just now';

  const minutes = seconds / 60;
  if (minutes < 60) return plural(Math.floor(minutes), 'minute');

  const hours = minutes / 60;
  if (hours < 24) return plural(Math.floor(hours), 'hour');

  const days = hours / 24;
  if (days < 7) return plural(Math.floor(days), 'day');

  const weeks = days / 7;
  if (weeks < 4.35) return plural(Math.floor(weeks), 'week');

  const months = days / 30.44;
  if (months < 12) return plural(Math.floor(months), 'month');

  return plural(Math.floor(days / 365.25), 'year');
}

/** `Synced 3 hours ago` — the caption used under an entry. */
export function formatSyncedAgo(from: string | number | Date, now: string | number | Date = Date.now()): string {
  const relative = formatRelative(from, now);
  if (!relative) return '';
  return `Synced ${relative}`;
}

/** `2026-10-22` → `October 2026` (used by the entry breadcrumb). */
export function formatMonthYear(value: string | number | Date): string {
  const date = parseDate(value);
  if (!date) return '';
  return `${MONTHS_LONG[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
}
