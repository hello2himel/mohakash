/**
 * Runtime access to the public JSON API.
 *
 * Every request goes to the **same origin** (`/api/apod/v1/...`). Netlify
 * proxies that path to the published GitHub Pages API (see `netlify.toml`).
 * The GitHub Pages URL is documentation only and is never used for fetching.
 */

import type { ApodRecord, ArchiveIndex, ArchiveYear, StatusDocument } from './types';

export const API_PREFIX = '/api/apod/v1';

export type ApiErrorKind = 'network' | 'timeout' | 'http' | 'parse' | 'aborted';

/** Typed error thrown by every function in this module. */
export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number | undefined;

  constructor(kind: ApiErrorKind, message: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status;
  }
}

export interface RequestOptions {
  /** Milliseconds before the request is aborted. Defaults to 12 000. */
  timeoutMs?: number;
  /** Browser cache mode. `no-cache` forces revalidation for fresh documents. */
  cache?: RequestCache;
  signal?: AbortSignal;
}

const DEFAULT_TIMEOUT_MS = 12_000;

/**
 * Build a same-origin API path.
 *
 * `buildApiPath('latest.json')` → `/api/apod/v1/latest.json`.
 * The published GitHub Pages URL is never produced here.
 */
export function buildApiPath(path: string): string {
  const trimmed = path.trim().replace(/^\/+/, '');
  return trimmed ? `${API_PREFIX}/${trimmed}` : `${API_PREFIX}/`;
}

/** Path of the yearly archive document for a given year. */
export function buildYearPath(year: number): string {
  return buildApiPath(`archive/${year}.json`);
}

async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = buildApiPath(path);
  const controller = new AbortController();
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const timer = setTimeout(() => {
    controller.abort(new DOMException('Request timed out', 'TimeoutError'));
  }, timeoutMs);

  if (options.signal) {
    if (options.signal.aborted) controller.abort(options.signal.reason);
    else options.signal.addEventListener('abort', () => controller.abort(options.signal?.reason), { once: true });
  }

  try {
    const response = await fetch(url, {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
      cache: options.cache ?? 'default',
      credentials: 'omit',
    });

    if (!response.ok) {
      throw new ApiError('http', `The API responded with status ${response.status}.`, response.status);
    }

    try {
      return (await response.json()) as T;
    } catch {
      throw new ApiError('parse', 'The API response was not valid JSON.');
    }
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof DOMException) {
      if (error.name === 'TimeoutError') throw new ApiError('timeout', 'The request to the API timed out.');
      if (error.name === 'AbortError') throw new ApiError('aborted', 'The request was cancelled.');
    }
    throw new ApiError('network', 'The API could not be reached.');
  } finally {
    clearTimeout(timer);
  }
}

/** Newest record plus sync metadata. Never served stale. */
export function fetchLatest(options: RequestOptions = {}): Promise<ApodRecord> {
  return requestJson<ApodRecord>('latest.json', { cache: 'no-cache', ...options });
}

/**
 * Newest entry that is a still picture (`media_type: "image"`).
 *
 * Used when video entries are hidden, so that the site can still show the
 * most recent picture instead of the most recent entry.
 */
export function fetchLatestImage(options: RequestOptions = {}): Promise<ApodRecord> {
  return requestJson<ApodRecord>('latest-image.json', { cache: 'no-cache', ...options });
}

/** Compact index of every entry, newest first. */
export function fetchIndex(options: RequestOptions = {}): Promise<ArchiveIndex> {
  return requestJson<ArchiveIndex>('archive-index.json', { cache: 'no-cache', ...options });
}

/** Full records (with explanations) for one calendar year. */
export function fetchYear(year: number, options: RequestOptions = {}): Promise<ArchiveYear> {
  return requestJson<ArchiveYear>(`archive/${year}.json`, { cache: 'default', ...options });
}

/** Sync status and archive coverage. Never served stale. */
export function fetchStatus(options: RequestOptions = {}): Promise<StatusDocument> {
  return requestJson<StatusDocument>('status.json', { cache: 'no-cache', ...options });
}
