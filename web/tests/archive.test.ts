import { describe, expect, it } from 'vitest';

import {
  availableYears,
  countLabel,
  entryPath,
  filterEntries,
  indexToListRows,
  neighbours,
  paginate,
  selectEntries,
  sortNewestFirst,
  visibleSlice,
} from '../src/lib/archive';
import type { ArchiveIndexEntry } from '../src/lib/types';

function entry(date: string, title: string, extra: Partial<ArchiveIndexEntry> = {}): ArchiveIndexEntry {
  return {
    date,
    title,
    media_type: 'image',
    thumbnail_url: `https://example.test/${date}.jpg`,
    image_url: `https://example.test/${date}.jpg`,
    apod_url: `https://example.test/${date}`,
    copyright: 'Someone',
    detail_path: 'https://example.test/api/v1/archive/2026.json',
    ...extra,
  };
}

const entries: ArchiveIndexEntry[] = [
  entry('2026-10-22', 'The First Green Star'),
  entry('2026-10-21', 'Ghosts Revealed in Cassiopeia'),
  entry('2026-10-18', 'Sonified: Eagle Nebula Pillars'),
  entry('2025-05-04', 'A Comet in Spring'),
  entry('1995-06-16', 'Neutron Star Earth'),
];

describe('filterEntries', () => {
  it('matches titles case-insensitively', () => {
    const result = filterEntries(entries, { query: 'green' });
    expect(result).toHaveLength(1);
    expect(result[0]?.title).toBe('The First Green Star');
  });

  it('ignores surrounding whitespace in the query', () => {
    expect(filterEntries(entries, { query: '  COMET  ' })).toHaveLength(1);
  });

  it('matches anywhere in the title', () => {
    expect(filterEntries(entries, { query: 'nebula' })).toHaveLength(1);
  });

  it('returns nothing for an unknown term', () => {
    expect(filterEntries(entries, { query: 'nothing here' })).toHaveLength(0);
  });

  it('filters by year', () => {
    const result = filterEntries(entries, { year: 2025 });
    expect(result).toHaveLength(1);
    expect(result[0]?.date).toBe('2025-05-04');
  });

  it('combines the title and the year filter', () => {
    expect(filterEntries(entries, { query: 'eagle', year: 2026 })).toHaveLength(1);
    expect(filterEntries(entries, { query: 'eagle', year: 2025 })).toHaveLength(0);
  });

  it('keeps every entry when no filter is set', () => {
    expect(filterEntries(entries, {})).toHaveLength(entries.length);
  });
});

describe('sortNewestFirst', () => {
  it('orders entries by date, newest first', () => {
    const sorted = sortNewestFirst(entries);
    expect(sorted.map((item) => item.date)).toEqual([
      '2026-10-22',
      '2026-10-21',
      '2026-10-18',
      '2025-05-04',
      '1995-06-16',
    ]);
  });

  it('does not modify the input array', () => {
    const copy = [...entries];
    sortNewestFirst(entries);
    expect(entries).toEqual(copy);
  });
});

describe('selectEntries', () => {
  it('filters and sorts in one step', () => {
    const result = selectEntries(entries, { year: 2026 });
    expect(result.map((item) => item.date)).toEqual(['2026-10-22', '2026-10-21', '2026-10-18']);
  });
});

describe('pagination', () => {
  const items = Array.from({ length: 30 }, (_, index) => index + 1);

  it('slices one page out of a list', () => {
    const page = paginate(items, 0, 12);
    expect(page.items).toHaveLength(12);
    expect(page.items[0]).toBe(1);
    expect(page.total).toBe(30);
    expect(page.hasMore).toBe(true);
  });

  it('slices the second page out of a list', () => {
    const page = paginate(items, 1, 12);
    expect(page.items[0]).toBe(13);
    expect(page.hasMore).toBe(true);
  });

  it('reports the end of the list', () => {
    const page = paginate(items, 2, 12);
    expect(page.items).toHaveLength(6);
    expect(page.hasMore).toBe(false);
  });

  it("grows the visible list for the \"Load more\" button", () => {
    expect(visibleSlice(items, 24)).toHaveLength(24);
    expect(visibleSlice(items, 30)).toHaveLength(30);
    expect(visibleSlice(items, 0)).toHaveLength(0);
  });
});

describe('countLabel', () => {
  it('reads "Newest first · N entries"', () => {
    expect(countLabel(94)).toBe('Newest first · 94 entries');
    expect(countLabel(1)).toBe('Newest first · 1 entry');
    expect(countLabel(0)).toBe('Newest first · 0 entries');
  });
});

describe('indexToListRows', () => {
  it('maps index entries to renderable rows', () => {
    const rows = indexToListRows(entries);
    expect(rows).toHaveLength(entries.length);
    expect(rows[0]).toEqual({
      date: '2026-10-22',
      dateLabel: '22 Oct 2026',
      title: 'The First Green Star',
      year: 2026,
      mediaType: 'image',
      thumbnailUrl: 'https://example.test/2026-10-22.jpg',
      imageUrl: 'https://example.test/2026-10-22.jpg',
      apodUrl: 'https://example.test/2026-10-22',
      copyright: 'Someone',
      href: '/archive/2026-10-22',
    });
  });
});

describe('availableYears', () => {
  it('lists the years newest first', () => {
    expect(availableYears(entries)).toEqual([2026, 2025, 1995]);
  });
});

describe('neighbours', () => {
  const ordered = sortNewestFirst(entries);

  it('finds the previous (older) and next (newer) entry', () => {
    const { previous, next } = neighbours(ordered, '2026-10-21');
    expect(previous?.date).toBe('2026-10-18');
    expect(next?.date).toBe('2026-10-22');
  });

  it('has no next entry for the newest record', () => {
    const { previous, next } = neighbours(ordered, '2026-10-22');
    expect(previous?.date).toBe('2026-10-21');
    expect(next).toBeNull();
  });

  it('has no previous entry for the oldest record', () => {
    const { previous, next } = neighbours(ordered, '1995-06-16');
    expect(previous).toBeNull();
    expect(next?.date).toBe('2025-05-04');
  });
});

describe('entryPath', () => {
  it('builds the entry route', () => {
    expect(entryPath('2026-10-22')).toBe('/archive/2026-10-22');
  });
});
