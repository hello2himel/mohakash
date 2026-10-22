/**
 * Archive list helpers: filtering, sorting, pagination and row mapping.
 * Pure functions only — the DOM layer lives in `src/lib/dom.ts`.
 */

import { formatDateShort, yearOf } from './format';
import type { ArchiveIndexEntry } from './types';

export interface ArchiveFilters {
  /** Free-text query matched against the entry title (case-insensitive). */
  query?: string;
  /** Calendar year, or `null`/`undefined` for every year. */
  year?: number | null;
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
 * The title match is case-insensitive and matches anywhere in the title.
 */
export function filterEntries(
  entries: readonly ArchiveIndexEntry[],
  filters: ArchiveFilters = {},
): ArchiveIndexEntry[] {
  const query = (filters.query ?? '').trim().toLowerCase();
  const year = filters.year ?? null;

  return entries.filter((entry) => {
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

/** The label above the archive grid. */
export function countLabel(total: number): string {
  return `Newest first · ${total} ${total === 1 ? 'entry' : 'entries'}`;
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
    apodUrl: entry.apod_url,
    copyright: entry.copyright ?? null,
    href: entryPath(entry.date),
  }));
}

/** The entry immediately older / newer than `date` inside an ordered list. */
export function neighbours(
  ordered: readonly ArchiveIndexEntry[],
  date: string,
): { previous: ArchiveIndexEntry | null; next: ArchiveIndexEntry | null } {
  const index = ordered.findIndex((entry) => entry.date === date);
  if (index === -1) return { previous: null, next: null };
  const older = ordered[index + 1] ?? null;
  const newer = index > 0 ? ordered[index - 1] : null;
  return { previous: older, next: newer };
}
