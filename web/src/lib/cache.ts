/**
 * Small localStorage cache.
 *
 * The site keeps the last successful `latest.json` and `archive-index.json`
 * payloads so a temporarily unavailable API still leaves the visitor with
 * something useful (the "stale / offline with cache" state).
 */

export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export interface CacheEnvelope<T> {
  /** ISO timestamp of the moment the payload was stored. */
  savedAt: string;
  data: T;
}

const CACHE_PREFIX = 'mohakash:v1:';

function memoryStorage(): StorageLike {
  const map = new Map<string, string>();
  return {
    getItem: (key) => map.get(key) ?? null,
    setItem: (key, value) => {
      map.set(key, value);
    },
    removeItem: (key) => {
      map.delete(key);
    },
  };
}

let override: StorageLike | null = null;
let fallback: StorageLike | null = null;

/** The storage used by the cache. `localStorage` when available, memory otherwise. */
export function getStorage(): StorageLike {
  if (override) return override;
  try {
    if (typeof localStorage !== 'undefined' && localStorage) {
      // Touch the API to find out whether storage is blocked (private mode).
      const probe = `${CACHE_PREFIX}probe`;
      localStorage.setItem(probe, '1');
      localStorage.removeItem(probe);
      return localStorage;
    }
  } catch {
    /* fall through to the in-memory store */
  }
  if (!fallback) fallback = memoryStorage();
  return fallback;
}

/** Replace the storage implementation (used by the tests). Pass `null` to restore the default. */
export function setStorage(storage: StorageLike | null): void {
  override = storage;
}

/** Cache key for a document name. */
export function cacheKey(name: string): string {
  return `${CACHE_PREFIX}${name}`;
}

/** Read a cached payload, or `null` when nothing is stored. */
export function readCache<T>(name: string): CacheEnvelope<T> | null {
  try {
    const raw = getStorage().getItem(cacheKey(name));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CacheEnvelope<T>;
    if (!parsed || typeof parsed !== 'object' || !('data' in parsed)) return null;
    return parsed;
  } catch {
    return null;
  }
}

/** Store a payload together with the time it was stored. */
export function writeCache<T>(name: string, data: T, now: Date = new Date()): CacheEnvelope<T> {
  const envelope: CacheEnvelope<T> = { savedAt: now.toISOString(), data };
  try {
    getStorage().setItem(cacheKey(name), JSON.stringify(envelope));
  } catch {
    /* storage full or blocked — the site still works without the cache */
  }
  return envelope;
}

/** Remove a cached payload. */
export function clearCache(name: string): void {
  try {
    getStorage().removeItem(cacheKey(name));
  } catch {
    /* ignore */
  }
}

export interface CacheResult<T> {
  data: T;
  /** `true` when the payload came from the local cache instead of the network. */
  fromCache: boolean;
  /** ISO timestamp of the cached copy (`null` for a fresh network response). */
  savedAt: string | null;
  /** The error that caused the fallback, if any. */
  error: unknown;
}

/**
 * Fetch a document and fall back to the cached copy when the request fails.
 *
 * The error is re-thrown when nothing is cached, so callers can render the
 * "error, no cache" state.
 */
export async function fetchWithCache<T>(
  name: string,
  fetcher: () => Promise<T>,
  now: Date = new Date(),
): Promise<CacheResult<T>> {
  try {
    const data = await fetcher();
    writeCache(name, data, now);
    return { data, fromCache: false, savedAt: null, error: null };
  } catch (error) {
    const cached = readCache<T>(name);
    if (cached) {
      return { data: cached.data, fromCache: true, savedAt: cached.savedAt, error };
    }
    throw error;
  }
}
