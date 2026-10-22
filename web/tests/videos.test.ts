import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { filterEntries, neighbours } from '../src/lib/archive';
import { setStorage, type StorageLike } from '../src/lib/cache';
import { needsPictureFallback } from '../src/lib/media';
import { HIDE_VIDEOS_DEFAULT, readHideVideos, writeHideVideos } from '../src/lib/preferences';
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
    detail_path: 'archive/2026.json',
    ...extra,
  };
}

const entries: ArchiveIndexEntry[] = [
  entry('2026-10-22', 'The First Green Star'),
  entry('2026-10-21', 'Ghosts Revealed in Cassiopeia'),
  entry('2026-10-18', 'Sonified: Eagle Nebula Pillars', { media_type: 'video' }),
  entry('2026-10-17', '3D 67P'),
  entry('2026-10-16', 'Chasing the Space Station', { media_type: 'video' }),
  entry('2026-10-15', 'The Milky Way over Manhattan'),
];

function memoryStore(): StorageLike & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => {
      data.set(key, value);
    },
    removeItem: (key) => {
      data.delete(key);
    },
  };
}

describe('the hide videos preference', () => {
  beforeEach(() => {
    setStorage(memoryStore());
  });

  afterEach(() => {
    setStorage(null);
  });

  it('hides videos by default', () => {
    expect(HIDE_VIDEOS_DEFAULT).toBe(true);
    expect(readHideVideos()).toBe(true);
  });

  it('survives a reload', () => {
    writeHideVideos(false);
    expect(readHideVideos()).toBe(false);
    writeHideVideos(true);
    expect(readHideVideos()).toBe(true);
  });
});

describe('filterEntries with videos', () => {
  it('drops video entries when videos are hidden', () => {
    const result = filterEntries(entries, { hideVideos: true });
    expect(result.map((item) => item.date)).toEqual([
      '2026-10-22',
      '2026-10-21',
      '2026-10-17',
      '2026-10-15',
    ]);
  });

  it('keeps video entries when videos are shown', () => {
    const result = filterEntries(entries, { hideVideos: false });
    expect(result).toHaveLength(entries.length);
  });

  it('hides videos by default even without an explicit flag', () => {
    expect(filterEntries(entries)).toHaveLength(entries.length - 2);
  });

  it('combines the video filter with the query and the year', () => {
    const result = filterEntries(entries, { query: 'space', hideVideos: true });
    expect(result).toHaveLength(0);

    const withVideos = filterEntries(entries, { query: 'space', hideVideos: false });
    expect(withVideos.map((item) => item.date)).toEqual(['2026-10-16']);
  });
});

describe('the Today page fallback', () => {
  it('shows the newest picture when the newest entry is a video', () => {
    expect(needsPictureFallback({ media_type: 'video' }, true)).toBe(true);
  });

  it('keeps the newest entry when it is already a picture', () => {
    expect(needsPictureFallback({ media_type: 'image' }, true)).toBe(false);
    expect(needsPictureFallback({ media_type: 'other' }, true)).toBe(false);
  });

  it('shows the newest entry when videos are not hidden', () => {
    expect(needsPictureFallback({ media_type: 'video' }, false)).toBe(false);
  });
});

describe('neighbours with videos', () => {
  it('skips video entries when videos are hidden', () => {
    const { previous, next } = neighbours(entries, '2026-10-17', true);
    // "previous day" is the older entry, "next day" the newer one.
    expect(previous?.date).toBe('2026-10-15');
    expect(next?.date).toBe('2026-10-21');
  });

  it('links to the neighbouring day when videos are shown', () => {
    const { previous, next } = neighbours(entries, '2026-10-17', false);
    expect(previous?.date).toBe('2026-10-16');
    expect(next?.date).toBe('2026-10-18');
  });

  it('still finds the neighbours of a video entry itself', () => {
    const { previous, next } = neighbours(entries, '2026-10-18', true);
    expect(previous?.date).toBe('2026-10-17');
    expect(next?.date).toBe('2026-10-21');
  });
});
