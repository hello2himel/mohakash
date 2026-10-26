/**
 * Archive list helpers: filtering, sorting, pagination and row mapping.
 * Pure functions only — the DOM layer lives in `src/lib/dom.ts`.
 */

import { formatDateShort, yearOf } from './format';
import { WEBSITE_URL } from './site.generated';
import type { ArchiveIndex, ArchiveIndexEntry } from './types';

export interface ArchiveFilters {
  /** Free-text query matched against the entry title (case-insensitive). */
  query?: string;
  /** Calendar year, or `null`/`undefined` for every year. */
  year?: number | null;
  /** Drop video entries. Videos are hidden by default. */
  hideVideos?: boolean;
}

/** A row of the archive list, ready to be rendered. */
export interface ListRow {
  date: string;
  /** Formatted publication date, e.g. `22 Oct 2026`. */
  dateLabel: string;
  title: string;
  year: number;
  mediaType: ArchiveIndexEntry['media_type'];
  thumbnailUrl: string | null;
  imageUrl: string | null;
  apodUrl: string;
  copyright: string | null;
  /** Same-origin path of the entry page. */
  href: string;
}

/** Path of the entry page for a calendar date. */
export function entryPath(date: string): string {
  return `/archive/${date}`;
}

/**
 * Absolute, shareable URL of one entry on the public website.
 *
 * Today's share button copies this URL (the canonical domain plus
 * `/archive/YYYY-MM-DD`), not the current `/` address, so the recipient lands
 * on the same picture with its explanation and credit.
 */
export function entryUrl(date: string): string {
  return new URL(entryPath(date), WEBSITE_URL).toString();
}

/** Sort entries newest first (ISO dates sort lexicographically). */
export function sortNewestFirst<T extends { date: string }>(entries: readonly T[]): T[] {
  return [...entries].sort((a, b) => {
    if (a.date === b.date) return 0;
    return a.date < b.date ? 1 : -1;
  });
}

/** Sort entries oldest first. */
export function sortOldestFirst<T extends { date: string }>(entries: readonly T[]): T[] {
  return [...entries].sort((a, b) => (a.date === b.date ? 0 : a.date < b.date ? -1 : 1));
}

/**
 * Filter entries by title text and/or year.
 *
 * The title match is case-insensitive and matches anywhere in the title.
 * Video entries are dropped when `hideVideos` is set, which is the default
 * for the whole site.
 */
export function filterEntries(
  entries: readonly ArchiveIndexEntry[],
  filters: ArchiveFilters = {},
): ArchiveIndexEntry[] {
  const query = (filters.query ?? '').trim().toLowerCase();
  const year = filters.year ?? null;
  const hideVideos = filters.hideVideos ?? true;

  return entries.filter((entry) => {
    if (hideVideos && entry.media_type === 'video') return false;
    if (year !== null && yearOf(entry.date) !== year) return false;
    if (!query) return true;
    return (entry.title ?? '').toLowerCase().includes(query);
  });
}

/** Apply the filters and keep the newest-first order. */
export function selectEntries(
  entries: readonly ArchiveIndexEntry[],
  filters: ArchiveFilters = {},
): ArchiveIndexEntry[] {
  return sortNewestFirst(filterEntries(entries, filters));
}

export interface Page<T> {
  items: T[];
  /** Number of items in the full (filtered) list. */
  total: number;
  /** Zero-based page index. */
  page: number;
  pageSize: number;
  hasMore: boolean;
}

/** Slice one page out of a list ("Load more" pagination). */
export function paginate<T>(items: readonly T[], page: number, pageSize: number): Page<T> {
  const size = Math.max(1, Math.floor(pageSize));
  const index = Math.max(0, Math.floor(page));
  const start = index * size;
  const slice = items.slice(start, start + size);
  return {
    items: slice,
    total: items.length,
    page: index,
    pageSize: size,
    hasMore: start + slice.length < items.length,
  };
}

/** Slice the first `count` items — used to grow the list when "Load more" is pressed. */
export function visibleSlice<T>(items: readonly T[], count: number): T[] {
  return items.slice(0, Math.max(0, count));
}

/** The label beside the archive filters. */
export function countLabel(total: number): string {
  return `${total} ${total === 1 ? 'entry' : 'entries'} · newest first`;
}

/**
 * Grid geometry for a fixed-height (no-scroll) archive page.
 *
 * Columns follow the container width with a minimum thumbnail of about
 * 200px: `columns = floor((width + gap) / (200 + gap))`.
 *
 * Rows are derived from the remaining height. A card needs a 3:2 thumbnail
 * (the tallest ratio the cards are designed around) plus the date/title block;
 * at the reference 1440×900 that yields exactly 6 columns × 3 rows = 18 cards.
 * Both values are clamped to at least 1.
 */
export function gridGeometry(
  width: number,
  height: number,
  minThumb = 200,
  gap = 16,
): { columns: number; rows: number } {
  if (!(width > 0) || !(height > 0)) return { columns: 1, rows: 1 };
  const columns = Math.max(1, Math.floor((width + gap) / (minThumb + gap)));
  const columnWidth = (width - gap * (columns - 1)) / columns;
  const cardHeight = Math.max(120, columnWidth * (2 / 3) + 46);
  const rows = Math.max(1, Math.floor((height + gap) / (cardHeight + gap)));
  return { columns, rows };
}

/** Total number of pages for `total` items at `pageSize` per page. */
export function pageCount(total: number, pageSize: number): number {
  const size = Math.max(1, Math.floor(pageSize));
  const count = Math.max(0, Math.floor(total));
  return Math.max(1, Math.ceil(count / size));
}

/** Distinct years present in the index, newest first. */
export function availableYears(entries: readonly ArchiveIndexEntry[]): number[] {
  const years = new Set<number>();
  for (const entry of entries) {
    const year = yearOf(entry.date);
    if (year) years.add(year);
  }
  return [...years].sort((a, b) => b - a);
}

/** Map index entries to renderable list rows. */
export function indexToListRows(entries: readonly ArchiveIndexEntry[]): ListRow[] {
  return entries.map((entry) => ({
    date: entry.date,
    dateLabel: formatDateShort(entry.date),
    title: entry.title,
    year: yearOf(entry.date),
    mediaType: entry.media_type,
    thumbnailUrl: entry.thumbnail_url ?? null,
    imageUrl: entry.image_url ?? null,
    apodUrl: entry.apod_url ?? '',
    copyright: entry.copyright ?? null,
    href: entryPath(entry.date),
  }));
}

/**
 * The entry immediately older / newer than `date` inside an ordered list.
 *
 * When `hideVideos` is set, video entries are skipped, so the links always
 * point at an entry the visitor can actually see.
 */
export function neighbours(
  ordered: readonly ArchiveIndexEntry[],
  date: string,
  hideVideos = true,
): { previous: ArchiveIndexEntry | null; next: ArchiveIndexEntry | null } {
  const sorted = sortNewestFirst(ordered);
  const visible = hideVideos ? sorted.filter((entry) => entry.media_type !== 'video') : sorted;
  const older = visible.find((entry) => entry.date < date) ?? null;
  const newer = [...visible].reverse().find((entry) => entry.date > date) ?? null;
  return { previous: older, next: newer };
}

/**
 * A cache-friendly copy of the index.
 *
 * The published index grows with every entry; the local copy only keeps the
 * fields the site renders, so the offline fallback still fits into the
 * storage quota of a browser.
 */
export function compactArchiveIndex(index: ArchiveIndex): ArchiveIndex {
  return {
    ...index,
    entries: (index.entries ?? []).map((entry) => ({
      date: entry.date,
      title: entry.title,
      media_type: entry.media_type,
      thumbnail_url: entry.thumbnail_url ?? null,
      image_url: entry.image_url ?? null,
    })),
  };
}
