/**
 * Today page — loads the newest record at runtime and fills the fixed
 * two-column shell. The cached copy is shown first; a stale copy gets the
 * banner row and the content below shrinks to fit.
 */

import { fetchLatest, fetchLatestImage } from '../lib/api';
import { fetchWithCache } from '../lib/cache';
import { entryPath } from '../lib/archive';
import { formatDate } from '../lib/format';
import { mediaPresentation, needsPictureFallback, type MediaPresentation } from '../lib/media';
import { readHideVideos } from '../lib/preferences';
import { initDrawer, setDrawerContent } from '../lib/drawer';
import type { ApodRecord } from '../lib/types';
import {
  applyBanner,
  applyPresentation,
  buildAction,
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

function renderActions(record: ApodRecord, presentation: MediaPresentation): void {
  actions.replaceChildren();

  if (presentation.kind === 'image' && (record.hd_image_url || record.image_url)) {
    const save = buildAction({
      label: 'Save HD image',
      variant: 'primary',
      href: record.hd_image_url ?? record.image_url ?? '',
    });
    if (save instanceof HTMLAnchorElement) save.setAttribute('download', '');
    actions.append(save);
  } else if ((presentation.kind === 'video' || presentation.kind === 'other') && record.apod_url) {
    actions.append(
      buildAction({
        label: 'Watch on original page',
        variant: 'primary',
        href: record.apod_url,
        external: true,
      }),
    );
  }

  if (record.apod_url) {
    actions.append(
      buildAction({
        label: 'Original APOD page',
        variant: 'secondary',
        href: record.apod_url,
        external: true,
      }),
    );
  }

  actions.append(buildAction({ label: 'Browse archive', variant: 'secondary', href: '/archive' }));
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
  renderProse(explanation, record.explanation ?? '');
  setDrawerContent({ record, nextTitle: tomorrowTitle(record.explanation ?? '') });

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

window.addEventListener('mohakash:hide-videos-changed', () => {
  void load();
});

void load();
