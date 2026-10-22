import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import {
  cacheKey,
  clearCache,
  fetchWithCache,
  readCache,
  setStorage,
  writeCache,
  type StorageLike,
} from '../src/lib/cache';

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

let store: ReturnType<typeof memoryStore>;

beforeEach(() => {
  store = memoryStore();
  setStorage(store);
});

afterEach(() => {
  setStorage(null);
});

describe('cache', () => {
  it('stores and reads a payload', () => {
    writeCache('latest', { title: 'A star' }, new Date('2026-10-22T12:00:00Z'));
    const cached = readCache<{ title: string }>('latest');
    expect(cached?.data.title).toBe('A star');
    expect(cached?.savedAt).toBe('2026-10-22T12:00:00.000Z');
  });

  it('uses a namespaced key', () => {
    expect(cacheKey('latest')).toBe('mohakash:v1:latest');
  });

  it('returns null for an unknown payload', () => {
    expect(readCache('missing')).toBeNull();
  });

  it('removes a payload', () => {
    writeCache('latest', { title: 'A star' });
    clearCache('latest');
    expect(readCache('latest')).toBeNull();
  });

  it('survives broken JSON', () => {
    store.setItem(cacheKey('latest'), '{not json');
    expect(readCache('latest')).toBeNull();
  });
});

describe('fetchWithCache', () => {
  it('returns the fresh payload and caches it', async () => {
    const result = await fetchWithCache('latest', async () => ({ title: 'Fresh' }));
    expect(result.fromCache).toBe(false);
    expect(result.savedAt).toBeNull();
    expect(result.data.title).toBe('Fresh');
    expect(readCache<{ title: string }>('latest')?.data.title).toBe('Fresh');
  });

  it('returns the cached payload when the request fails', async () => {
    writeCache('latest', { title: 'Saved' }, new Date('2026-10-21T08:00:00Z'));

    const result = await fetchWithCache<{ title: string }>('latest', async () => {
      throw new Error('network down');
    });

    expect(result.fromCache).toBe(true);
    expect(result.data.title).toBe('Saved');
    expect(result.savedAt).toBe('2026-10-21T08:00:00.000Z');
    expect(result.error).toBeInstanceOf(Error);
  });

  it('re-throws when there is nothing cached', async () => {
    await expect(
      fetchWithCache<{ title: string }>('latest', async () => {
        throw new Error('network down');
      }),
    ).rejects.toThrow('network down');
  });

  it('caches the archive index as well', async () => {
    const first = await fetchWithCache<{ entries: number[] }>('archive-index', async () => ({
      entries: [1, 2, 3],
    }));
    expect(first.fromCache).toBe(false);

    const second = await fetchWithCache<{ entries: number[] }>('archive-index', async () => {
      throw new Error('offline');
    });
    expect(second.fromCache).toBe(true);
    expect(second.data.entries).toEqual([1, 2, 3]);
  });
});
