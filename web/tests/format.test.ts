import { describe, expect, it } from 'vitest';

import {
  formatDate,
  formatDateShort,
  formatMonthYear,
  formatRelative,
  formatSyncedAgo,
  isIsoDate,
  yearOf,
} from '../src/lib/format';

describe('formatDate', () => {
  it('formats an ISO date as a long readable date', () => {
    expect(formatDate('2026-10-22')).toBe('22 October 2026');
    expect(formatDate('1995-06-16')).toBe('16 June 1995');
  });

  it('formats a short date', () => {
    expect(formatDateShort('2026-10-22')).toBe('22 Oct 2026');
  });

  it('formats a month and year', () => {
    expect(formatMonthYear('2026-10-22')).toBe('October 2026');
  });

  it('returns an empty string for unusable input', () => {
    expect(formatDate('')).toBe('');
    expect(formatDate('not a date')).toBe('');
    expect(formatDate('2026-13-45')).toBe('');
  });

  it('reads the calendar year', () => {
    expect(yearOf('2026-10-22')).toBe(2026);
    expect(yearOf('nope')).toBe(0);
  });

  it('recognises zero padded calendar dates', () => {
    expect(isIsoDate('2026-10-22')).toBe(true);
    expect(isIsoDate('2026-10-32')).toBe(false);
    expect(isIsoDate('2026-1-2')).toBe(false);
    expect(isIsoDate('yesterday')).toBe(false);
  });
});

describe('formatRelative', () => {
  const now = '2026-10-22T12:00:00Z';

  it('reports "just now" for very recent timestamps', () => {
    expect(formatRelative('2026-10-22T11:59:40Z', now)).toBe('just now');
  });

  it('reports minutes, hours, days, weeks and months', () => {
    expect(formatRelative('2026-10-22T11:30:00Z', now)).toBe('30 minutes ago');
    expect(formatRelative('2026-10-22T09:00:00Z', now)).toBe('3 hours ago');
    expect(formatRelative('2026-10-21T12:00:00Z', now)).toBe('1 day ago');
    expect(formatRelative('2026-10-15T12:00:00Z', now)).toBe('1 week ago');
    expect(formatRelative('2026-08-22T12:00:00Z', now)).toBe('2 months ago');
  });

  it('reports years', () => {
    expect(formatRelative('2024-10-22T12:00:00Z', now)).toBe('1 year ago');
    expect(formatRelative('2024-09-22T12:00:00Z', now)).toBe('2 years ago');
  });

  it('returns an empty string when a timestamp cannot be parsed', () => {
    expect(formatRelative('whenever', now)).toBe('');
  });
});

describe('formatSyncedAgo', () => {
  it('prefixes the relative time with "Synced"', () => {
    expect(formatSyncedAgo('2026-10-22T09:00:00Z', '2026-10-22T12:00:00Z')).toBe('Synced 3 hours ago');
  });

  it('stays empty without a timestamp', () => {
    expect(formatSyncedAgo('', '2026-10-22T12:00:00Z')).toBe('');
  });
});
