import { describe, expect, it } from 'vitest';

import {
  emptyUsage,
  isEntryPath,
  markDonated,
  markPrompted,
  parseUsage,
  registerUsage,
  shouldAsk,
} from '../src/lib/donation';

const day = (iso: string) => new Date(`${iso}T12:00:00Z`);

describe('isEntryPath', () => {
  it('recognises entry detail routes only', () => {
    expect(isEntryPath('/archive/2026-10-21')).toBe(true);
    expect(isEntryPath('/archive/2026-10-21/')).toBe(true);
    expect(isEntryPath('/archive')).toBe(false);
    expect(isEntryPath('/archive/entry')).toBe(false);
    expect(isEntryPath('/')).toBe(false);
  });
});

describe('registerUsage', () => {
  it('counts page views, entry reads, sessions and distinct days', () => {
    let state = emptyUsage();
    state = registerUsage(state, '/', day('2026-10-20'), true);
    state = registerUsage(state, '/archive/2026-10-20', day('2026-10-20'), false);
    state = registerUsage(state, '/archive/2026-10-21', day('2026-10-21'), true);

    expect(state.pageViews).toBe(3);
    expect(state.entryViews).toBe(2);
    expect(state.sessions).toBe(2);
    expect(state.days).toEqual(['2026-10-20', '2026-10-21']);
    expect(state.firstSeen).toBe('2026-10-20T12:00:00.000Z');
    expect(state.lastSeen).toBe('2026-10-21T12:00:00.000Z');
  });
});

describe('shouldAsk', () => {
  const now = day('2026-10-22');

  it('does not ask a light reader', () => {
    expect(shouldAsk(emptyUsage(), now)).toBe(false);
    const state = { ...emptyUsage(), entryViews: 5, pageViews: 8, sessions: 1, days: ['2026-10-22'] };
    expect(shouldAsk(state, now)).toBe(false);
  });

  it('asks after 20 entry reads across two days', () => {
    const state = {
      ...emptyUsage(),
      entryViews: 20,
      pageViews: 20,
      sessions: 1,
      days: ['2026-10-20', '2026-10-22'],
    };
    expect(shouldAsk(state, now)).toBe(true);
  });

  it('does not ask for 20 entry reads on a single day', () => {
    const state = {
      ...emptyUsage(),
      entryViews: 20,
      pageViews: 20,
      sessions: 1,
      days: ['2026-10-22'],
    };
    expect(shouldAsk(state, now)).toBe(false);
  });

  it('asks a frequent returning session reader after 3 days', () => {
    const state = {
      ...emptyUsage(),
      sessions: 6,
      pageViews: 6,
      days: ['2026-10-20', '2026-10-21', '2026-10-22'],
    };
    expect(shouldAsk(state, now)).toBe(true);
  });

  it('asks after 40 page views across two days', () => {
    const state = {
      ...emptyUsage(),
      sessions: 2,
      pageViews: 40,
      days: ['2026-10-20', '2026-10-22'],
    };
    expect(shouldAsk(state, now)).toBe(true);
  });

  it('respects the 30-day prompt cooldown', () => {
    const state = {
      ...emptyUsage(),
      sessions: 6,
      pageViews: 6,
      days: ['2026-10-20', '2026-10-21', '2026-10-22'],
      promptedAt: '2026-10-01T12:00:00.000Z',
    };
    expect(shouldAsk(state, now)).toBe(false);
    expect(shouldAsk(markPrompted(state, day('2026-09-01')), now)).toBe(true);
  });

  it('respects the donation grace period', () => {
    const state = {
      ...emptyUsage(),
      sessions: 6,
      pageViews: 6,
      days: ['2026-10-20', '2026-10-21', '2026-10-22'],
      donatedAt: '2026-06-01T12:00:00.000Z',
    };
    expect(shouldAsk(state, now)).toBe(false);
    expect(shouldAsk(markDonated(state, day('2026-01-01')), now)).toBe(true);
  });
});

describe('parseUsage', () => {
  it('handles missing or malformed data', () => {
    expect(parseUsage(null)).toEqual(emptyUsage());
    expect(parseUsage('not json')).toEqual(emptyUsage());
    expect(parseUsage('{}')).toEqual(emptyUsage());
  });

  it('keeps valid counters and ignores bad values', () => {
    const parsed = parseUsage(
      JSON.stringify({
        firstSeen: '2026-10-01T00:00:00.000Z',
        pageViews: 12.8,
        entryViews: -1,
        sessions: 'many',
        days: ['2026-10-01', 42],
        promptedAt: null,
      }),
    );
    expect(parsed.pageViews).toBe(12);
    expect(parsed.entryViews).toBe(0);
    expect(parsed.sessions).toBe(0);
    expect(parsed.days).toEqual(['2026-10-01']);
  });
});
