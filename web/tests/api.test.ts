import { describe, expect, it } from 'vitest';

import { API_PREFIX, ApiError, buildApiPath, buildYearPath } from '../src/lib/api';

describe('buildApiPath', () => {
  it('builds same-origin API paths', () => {
    expect(buildApiPath('latest.json')).toBe('/api/apod/v1/latest.json');
    expect(buildApiPath('status.json')).toBe('/api/apod/v1/status.json');
    expect(buildApiPath('archive-index.json')).toBe('/api/apod/v1/archive-index.json');
    expect(buildApiPath('archive/2026.json')).toBe('/api/apod/v1/archive/2026.json');
  });

  it('tolerates a leading slash', () => {
    expect(buildApiPath('/latest.json')).toBe('/api/apod/v1/latest.json');
  });

  it('returns the bare prefix for an empty path', () => {
    expect(buildApiPath('')).toBe('/api/apod/v1/');
    expect(buildApiPath('/')).toBe('/api/apod/v1/');
  });

  it('never produces the published GitHub Pages URL', () => {
    const paths = [buildApiPath('latest.json'), buildApiPath('archive/2026.json'), buildApiPath('')];
    for (const path of paths) {
      expect(path.startsWith('/')).toBe(true);
      expect(path).not.toContain('github.io');
      expect(path).not.toContain('http');
      expect(path.startsWith(API_PREFIX)).toBe(true);
    }
  });

  it('builds the yearly document path', () => {
    expect(buildYearPath(2026)).toBe('/api/apod/v1/archive/2026.json');
    expect(buildYearPath(1995)).toBe('/api/apod/v1/archive/1995.json');
  });
});

describe('ApiError', () => {
  it('carries the failure kind', () => {
    const error = new ApiError('http', 'boom', 404);
    expect(error).toBeInstanceOf(Error);
    expect(error.kind).toBe('http');
    expect(error.status).toBe(404);
  });
});
