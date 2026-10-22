/**
 * Local-only usage accounting for the occasional support prompt.
 *
 * The site is static and promises no tracking, so nothing here is sent
 * anywhere. The counters live in the browser's local storage and only decide
 * whether a polite donation note may be shown.
 */

export interface UsageState {
  firstSeen: string;
  lastSeen: string;
  /** Distinct local dates (YYYY-MM-DD), capped to keep storage small. */
  days: string[];
  pageViews: number;
  entryViews: number;
  sessions: number;
  /** When the support note was last shown. */
  promptedAt: string | null;
  /** When a donation link was last clicked. */
  donatedAt: string | null;
}

export const USAGE_KEY = 'mohakash:v1:usage';
export const SESSION_KEY = 'mohakash:v1:session';
export const PROMPT_COOLDOWN_DAYS = 30;
export const DONATION_GRACE_DAYS = 180;
export const PROMPT_DELAY_MS = 8_000;

export const THRESHOLDS = {
  entryViews: 20,
  sessions: 6,
  pageViews: 40,
  daysForEntries: 2,
  daysForSessions: 3,
  daysForPages: 2,
} as const;

const DAY_MS = 86_400_000;
const MAX_DAYS = 60;

export function emptyUsage(): UsageState {
  return {
    firstSeen: '',
    lastSeen: '',
    days: [],
    pageViews: 0,
    entryViews: 0,
    sessions: 0,
    promptedAt: null,
    donatedAt: null,
  };
}

/** `/archive/YYYY-MM-DD` is an entry read; `/archive` is just the index. */
export function isEntryPath(path: string): boolean {
  return /^\/archive\/\d{4}-\d{2}-\d{2}(?:\/|$)/.test(path);
}

/** Record one page view and return the updated state. */
export function registerUsage(
  state: UsageState,
  path: string,
  now: Date,
  newSession: boolean,
): UsageState {
  const timestamp = now.toISOString();
  const day = timestamp.slice(0, 10);
  const days = state.days.includes(day) ? state.days : [...state.days, day].slice(-MAX_DAYS);

  return {
    firstSeen: state.firstSeen || timestamp,
    lastSeen: timestamp,
    days,
    pageViews: state.pageViews + 1,
    entryViews: state.entryViews + (isEntryPath(path) ? 1 : 0),
    sessions: state.sessions + (newSession ? 1 : 0),
    promptedAt: state.promptedAt,
    donatedAt: state.donatedAt,
  };
}

function withinDays(iso: string | null, now: Date, days: number): boolean {
  if (!iso) return false;
  const timestamp = Date.parse(iso);
  if (!Number.isFinite(timestamp)) return false;
  return now.getTime() - timestamp < days * DAY_MS;
}

/**
 * Decide whether this reader has used the site enough to be asked once.
 *
 * Deliberately conservative: a single long first session is not enough; the
 * reader must have returned on at least two different days, and must not have
 * seen the note or used a donation link recently.
 */
export function shouldAsk(state: UsageState, now: Date): boolean {
  if (withinDays(state.donatedAt, now, DONATION_GRACE_DAYS)) return false;
  if (withinDays(state.promptedAt, now, PROMPT_COOLDOWN_DAYS)) return false;

  const days = state.days.length;
  return (
    (state.entryViews >= THRESHOLDS.entryViews && days >= THRESHOLDS.daysForEntries) ||
    (state.sessions >= THRESHOLDS.sessions && days >= THRESHOLDS.daysForSessions) ||
    (state.pageViews >= THRESHOLDS.pageViews && days >= THRESHOLDS.daysForPages)
  );
}

export function markPrompted(state: UsageState, now: Date): UsageState {
  return { ...state, promptedAt: now.toISOString() };
}

export function markDonated(state: UsageState, now: Date): UsageState {
  return { ...state, donatedAt: now.toISOString() };
}

function count(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.floor(value) : 0;
}

function isoOrNull(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null;
}

/** Parse stored usage defensively; anything malformed starts from zero. */
export function parseUsage(raw: string | null): UsageState {
  const base = emptyUsage();
  if (!raw) return base;
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    if (!value || typeof value !== 'object') return base;
    const days = Array.isArray(value.days)
      ? value.days.filter((day): day is string => typeof day === 'string')
      : [];
    return {
      firstSeen: typeof value.firstSeen === 'string' ? value.firstSeen : '',
      lastSeen: typeof value.lastSeen === 'string' ? value.lastSeen : '',
      days,
      pageViews: count(value.pageViews),
      entryViews: count(value.entryViews),
      sessions: count(value.sessions),
      promptedAt: isoOrNull(value.promptedAt),
      donatedAt: isoOrNull(value.donatedAt),
    };
  } catch {
    return base;
  }
}
