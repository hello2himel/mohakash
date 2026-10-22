/**
 * Entry page — one entry of the archive, addressed as `/archive/YYYY-MM-DD`.
 *
 * The static shell is served for every `/archive/<date>` path (see the
 * Netlify rewrite in `netlify.toml`); the date is read from the URL and the
 * record is fetched from the API at runtime.
 */

import { fetchIndex, fetchYear } from '../lib/api';
import { fetchWithCache } from '../lib/cache';
import { compactArchiveIndex, entryPath, neighbours } from '../lib/archive';
import { formatDate, isIsoDate, yearOf } from '../lib/format';
import { mediaPresentation, type MediaPresentation } from '../lib/media';
import { readHideVideos } from '../lib/preferences';
import { initDrawer, setDrawerContent } from '../lib/drawer';
import type { ApodRecord, ArchiveIndexEntry } from '../lib/types';
import {
  applyBanner,
  applyPresentation,
  buildAction,
  el,
  hide,
  hideBanner,
  need,
  renderErrorState,
  renderProse,
  show,
  tomorrowTitle,
} from '../lib/dom';

const main = need<HTMLElement>('main');
const banner = need('#entry-banner');
const frame = need('#entry-frame');
const content = need('#entry-content');
const errorHost = need('#entry-error');

const skeletonFrame = need('[data-skeleton="frame"]');
const skeletonPanel = need('[data-skeleton="panel"]');

const breadcrumb = need('#entry-breadcrumb');
const yearLink = need<HTMLAnchorElement>('#entry-year-link');
const crumbDate = need('#entry-crumb-date');
const dateLine = need('#entry-date');
const title = need('#entry-title');
const actions = need('#entry-actions');
const explanation = need('#entry-explanation');
const entryNav = need('#entry-nav');

/** The date addressed by the current URL. */
function requestedDate(): string | null {
  const parts = window.location.pathname.split('/').filter(Boolean);
  if (parts[0] !== 'archive' || parts.length !== 2) return null;
  const date = parts[1] ?? '';
  return isIsoDate(date) ? date : null;
}

/* ---------------------------------------------------------------- media */

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

async function copyCurrentLink(button: HTMLButtonElement): Promise<void> {
  const url = window.location.href;
  try {
    await navigator.clipboard.writeText(url);
  } catch {
    const helper = document.createElement('textarea');
    helper.value = url;
    helper.setAttribute('readonly', '');
    helper.style.position = 'absolute';
    helper.style.left = '-9999px';
    document.body.append(helper);
    helper.select();
    try {
      (document as unknown as { execCommand(command: string): boolean }).execCommand('copy');
    } catch {
      /* the button simply stays as it is */
    }
    helper.remove();
  }
  const label = button.textContent;
  button.textContent = 'Copied';
  window.setTimeout(() => {
    button.textContent = label;
  }, 2000);
}

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

  const copy = el('button', 'btn btn--secondary');
  copy.type = 'button';
  copy.textContent = 'Copy link';
  copy.addEventListener('click', () => {
    void copyCurrentLink(copy);
  });
  actions.append(copy);
}

/* ------------------------------------------------------------------ nav */

function navButton(label: string, date: string | null): HTMLElement {
  if (date) {
    const link = el('a', 'btn btn--secondary', label);
    link.href = entryPath(date);
    return link;
  }
  const disabled = el('span', 'btn btn--secondary', label);
  disabled.setAttribute('aria-disabled', 'true');
  return disabled;
}

function renderNeighbours(
  index: ArchiveIndexEntry[],
  date: string,
): { nextTitle: string | null } {
  const { previous, next } = neighbours(index, date, readHideVideos());
  entryNav.replaceChildren(
    navButton('‹ Previous day', previous?.date ?? null),
    navButton('Next day ›', next?.date ?? null),
  );
  return { nextTitle: next?.title ?? null };
}

/* --------------------------------------------------------------- render */

function render(record: ApodRecord, index: ArchiveIndexEntry[]): void {
  const presentation = mediaPresentation(record);

  applyPresentation(frame, presentation, { priority: true });
  clearFrameSkeleton(presentation);

  const date = formatDate(record.date);
  const credit = (record.credit ?? record.copyright ?? '').trim();
  dateLine.textContent = credit ? `${date} · Credit: ${credit}` : date;

  title.textContent = record.title;
  document.title = `${record.title} · Mohakash`;

  const year = yearOf(record.date);
  yearLink.textContent = String(year);
  yearLink.href = `/archive?year=${year}`;
  crumbDate.textContent = date;
  show(breadcrumb);

  renderActions(record, presentation);
  renderProse(explanation, record.explanation ?? '');

  const { nextTitle } = renderNeighbours(index, record.date);
  setDrawerContent({
    record,
    nextTitle: tomorrowTitle(record.explanation ?? '') ?? nextTitle,
  });

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

function showError(options: { title: string; text: string }): void {
  clearBanner();
  hide(content);
  skeletonPanel.hidden = true;
  skeletonFrame.hidden = true;
  main.classList.add('is-error');
  main.removeAttribute('aria-busy');
  show(errorHost);
  renderErrorState(errorHost, {
    title: options.title,
    text: options.text,
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
  const date = requestedDate();
  if (!date) {
    showError({
      title: 'Nothing out here',
      text: 'This page doesn’t exist, or the entry date isn’t in the archive.',
    });
    return;
  }

  resetForLoad();
  clearBanner();

  const year = yearOf(date);

  try {
    let index: ArchiveIndexEntry[] = [];
    try {
      const indexResult = await fetchWithCache('archive-index', () => fetchIndex(), {
        store: compactArchiveIndex,
      });
      index = indexResult.data.entries ?? [];
    } catch {
      // The entry can still be shown without its neighbours.
    }

    const yearResult = await fetchWithCache(`year-${year}`, () => fetchYear(year));
    const record = yearResult.data.records.find((item) => item.date === date);

    if (!record) {
      showError({
        title: 'Nothing out here',
        text: 'This page doesn’t exist, or the entry date isn’t in the archive.',
      });
      return;
    }

    render(record, index);

    if (yearResult.fromCache) {
      showBanner(
        `Couldn't refresh. Showing the saved copy from ${formatDate(yearResult.savedAt ?? record.date)}.`,
      );
    }
  } catch {
    showError({
      title: "This entry isn't available",
      text: 'Check your connection and try again. You can still browse the archive.',
    });
  }
}

initDrawer();
void load();
