/**
 * Today page — loads the newest record at runtime and fills the fixed
 * two-column shell. The cached copy is shown first; a stale copy gets the
 * banner row and the content below shrinks to fit.
 */

import { fetchLatest, fetchLatestImage, fetchYear } from '../lib/api';
import { fetchWithCache, readCache } from '../lib/cache';
import { entryPath, entryUrl } from '../lib/archive';
import { copyWithFeedback } from '../lib/clipboard';
import { formatDate, yearOf } from '../lib/format';
import { mediaPresentation, needsPictureFallback, type MediaPresentation } from '../lib/media';
import { readHideVideos } from '../lib/preferences';
import { initDrawer, setDrawerContent } from '../lib/drawer';
import type { ApodRecord, ArchiveYear } from '../lib/types';
import {
  applyBanner,
  applyPresentation,
  buildAction,
  downloadLink,
  hide,
  hideBanner,
  need,
  renderErrorState,
  renderProse,
  show,
  tomorrowTitle,
} from '../lib/dom';

const main = need<HTMLElement>('main');
const banner = need('#today-banner');
const frameLink = need<HTMLAnchorElement>('#today-frame-link');
const frame = need('#today-frame');
const content = need('#today-content');
const errorHost = need('#today-error');

const skeletonFrame = need('[data-skeleton="frame"]');
const skeletonPanel = need('[data-skeleton="panel"]');

const dateLine = need('#today-date');
const title = need('#today-title');
const actions = need('#today-actions');
const explanation = need('#today-explanation');
const navRow = need('#today-nav');
const previousButton = need<HTMLAnchorElement>('#today-previous');

/**
 * The date of the older entry, once it is known. It is resolved from the
 * yearly archive file (which is much smaller than the full index) in the
 * background, so the picture itself is never blocked on it.
 */
let previousDate: string | null = null;
let previousLookup: Promise<string | null> | null = null;
let loadedDate = '';

/* ---------------------------------------------------------------- media */

/** Hide the frame skeleton as soon as the picture settles (load or error). */
function clearFrameSkeleton(presentation: MediaPresentation): void {
  const image = frame.querySelector<HTMLImageElement>('.media-frame__img');
  const clear = () => {
    skeletonFrame.hidden = true;
  };
  if (presentation.src && image) {
    image.addEventListener('load', clear, { once: true });
    image.addEventListener('error', clear, { once: true });
    if (image.complete && image.naturalWidth > 0) clear();
  } else {
    clear();
  }
}

/* -------------------------------------------------------------- actions */

/**
 * The visible description is a short preview; the drawer still receives the
 * complete explanation. Prefer a complete sentence between 100 and 240
 * characters, then fall back to a word boundary.
 */
function shortDescription(text: string, limit = 240): string {
  const normalized = text.replace(/\s+/g, ' ').trim();
  if (normalized.length <= limit) return normalized;

  const boundaries = [...normalized.matchAll(/[.!?](?=\s|$)/g)].map(
    (match) => (match.index ?? 0) + 1,
  );
  const sentenceEnd = boundaries.find((end) => end >= 100 && end <= limit);
  if (sentenceEnd !== undefined) return normalized.slice(0, sentenceEnd).trimEnd();

  const window = normalized.slice(0, limit);
  const wordEnd = window.lastIndexOf(' ');
  const cut = wordEnd > 0 ? window.slice(0, wordEnd) : window;
  return `${cut.replace(/[,;:\s]+$/, '')}…`;
}

function renderActions(record: ApodRecord, presentation: MediaPresentation): void {
  actions.replaceChildren();

  if (presentation.kind === 'image' && (record.hd_image_url || record.image_url)) {
    actions.append(
      downloadLink({
        label: 'Save HD image',
        variant: 'primary',
        href: record.hd_image_url ?? record.image_url ?? '',
        icon: 'ri-download-line',
      }),
    );
  } else if ((presentation.kind === 'video' || presentation.kind === 'other') && record.apod_url) {
    actions.append(
      buildAction({
        label: 'Watch on original page',
        variant: 'primary',
        href: record.apod_url,
        external: true,
        icon: 'ri-play-circle-line',
      }),
    );
  }

  // The share action copies the canonical entry URL, not the current `/`
  // address, so a shared link opens this exact APOD with its explanation.
  const share = buildAction({
    label: 'Share',
    variant: 'secondary',
    icon: 'ri-share-line',
    ariaLabel: 'Copy the link to this entry',
    iconOnly: true,
  }) as HTMLButtonElement;
  share.addEventListener('click', () => {
    void copyWithFeedback(share, entryUrl(record.date));
  });
  actions.append(share);

  if (record.apod_url) {
    actions.append(
      buildAction({
        label: 'Original APOD page',
        variant: 'secondary',
        href: record.apod_url,
        external: true,
        icon: 'ri-information-line',
        iconOnly: true,
        ariaLabel: 'Open the original APOD page',
      }),
    );
  }

  actions.append(
    buildAction({
      label: 'Browse archive',
      variant: 'secondary',
      href: '/archive',
      icon: 'ri-archive-line',
      iconOnly: true,
    }),
  );
}

/* ------------------------------------------------------------- previous */

/**
 * Find the entry immediately older than [date], skipping videos when they are
 * hidden. Only the yearly archive files are read — they are far smaller than
 * the full index, and the current year usually answers on its own.
 */
async function findPrevious(date: string): Promise<string | null> {
  const hideVideos = readHideVideos();
  const currentYear = yearOf(date);

  for (let year = currentYear; year >= 1995; year -= 1) {
    try {
      // Past entries never change, so a cached year is as good as the network.
      const cached = readCache<ArchiveYear>(`year-${year}`);
      const result = cached
        ? cached.data
        : (await fetchWithCache(`year-${year}`, () => fetchYear(year))).data;
      const older = result.records
        .filter((item) => item.date < date && (!hideVideos || item.media_type !== 'video'))
        .reduce<ApodRecord | null>(
          (latest, item) => (latest === null || item.date > latest.date ? item : latest),
          null,
        );
      if (older) return older.date;
      // Only a January entry can need the previous year; stop after that.
      if (year !== currentYear) break;
    } catch {
      return null;
    }
  }
  return null;
}

function wirePrevious(date: string): void {
  previousDate = null;
  previousLookup = null;
  navRow.hidden = true;
  previousButton.setAttribute('href', '/archive');

  previousLookup = findPrevious(date).then((previous) => {
    if (loadedDate !== date) return null;
    previousDate = previous;
    if (previous) {
      previousButton.href = entryPath(previous);
      navRow.hidden = false;
    }
    return previous;
  });
}

async function goPrevious(): Promise<void> {
  if (previousDate) {
    window.location.assign(entryPath(previousDate));
    return;
  }
  if (!previousLookup) return;
  const previous = await previousLookup;
  if (previous) window.location.assign(entryPath(previous));
}

/* --------------------------------------------------------------- render */

function render(record: ApodRecord): void {
  const presentation = mediaPresentation(record);

  frameLink.href = entryPath(record.date);
  frameLink.setAttribute('aria-label', 'Open this entry');

  applyPresentation(frame, presentation, { priority: true });
  clearFrameSkeleton(presentation);

  const date = formatDate(record.date);
  const credit = (record.credit ?? record.copyright ?? '').trim();
  dateLine.textContent = credit ? `${date} · Credit: ${credit}` : date;

  title.textContent = record.title;
  document.title = `${record.title} · Mohakash`;

  renderActions(record, presentation);
  renderProse(explanation, shortDescription(record.explanation ?? ''));
  setDrawerContent({ record, nextTitle: tomorrowTitle(record.explanation ?? '') });

  loadedDate = record.date;
  wirePrevious(record.date);

  skeletonPanel.hidden = true;
  show(content);
  main.classList.remove('is-error');
  main.removeAttribute('aria-busy');
}

/* ---------------------------------------------------------------- states */

function showBanner(message: string): void {
  applyBanner(banner, {
    message,
    actionLabel: 'Try again',
    onAction: () => {
      void load();
    },
  });
  main.classList.add('has-banner');
}

function clearBanner(): void {
  hideBanner(banner);
  main.classList.remove('has-banner');
}

function showError(): void {
  clearBanner();
  hide(content);
  skeletonPanel.hidden = true;
  skeletonFrame.hidden = true;
  main.classList.add('is-error');
  main.removeAttribute('aria-busy');
  show(errorHost);
  renderErrorState(errorHost, {
    title: "Today's picture isn't available",
    text: 'Check your connection and try again. You can still browse the archive.',
    actionLabel: 'Try again',
    onAction: () => {
      void load();
    },
    secondaryLabel: 'Archive',
    secondaryHref: '/archive',
  });
}

function resetForLoad(): void {
  hide(errorHost);
  main.classList.remove('is-error');
  main.setAttribute('aria-busy', 'true');
  hide(content);
  skeletonPanel.hidden = false;
  skeletonFrame.hidden = false;
  navRow.hidden = true;
  previousDate = null;
  previousLookup = null;
  loadedDate = '';
}

/* ----------------------------------------------------------------- load */

async function load(): Promise<void> {
  resetForLoad();
  clearBanner();

  try {
    const hideVideos = readHideVideos();
    const result = await fetchWithCache('latest', () => fetchLatest());
    let record = result.data;
    let fromCache = result.fromCache;

    // Videos are hidden by default: show the most recent still picture
    // instead of the newest video entry.
    if (needsPictureFallback(record, hideVideos)) {
      try {
        const picture = await fetchWithCache('latest-image', () => fetchLatestImage());
        record = picture.data;
        fromCache = fromCache || picture.fromCache;
      } catch {
        // Keep the video entry; it really is the newest entry.
      }
    }

    render(record);

    if (fromCache) {
      const savedAt = result.savedAt ?? record.date;
      showBanner(`Couldn't refresh. Showing the saved copy from ${formatDate(savedAt)}.`);
    }
  } catch {
    showError();
  }
}

initDrawer();

// ← steps back one picture. There is no → on Today — it is the newest entry —
// and the drawer / mobile menu own the arrow keys while they are open.
document.addEventListener('keydown', (event) => {
  if (event.key !== 'ArrowLeft') return;
  if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
    return;
  }
  const target = event.target as HTMLElement | null;
  if (target?.closest('input, select, textarea, [contenteditable="true"]')) return;
  if (document.querySelector('dialog[open]')) return;
  if (!previousDate && !previousLookup) return;
  event.preventDefault();
  void goPrevious();
});

window.addEventListener('mohakash:hide-videos-changed', () => {
  void load();
});

void load();
