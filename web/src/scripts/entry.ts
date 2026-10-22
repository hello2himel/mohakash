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

/** Dates of the neighbouring entries, used by the arrow-key shortcuts. */
let previousDate: string | null = null;
let nextDate: string | null = null;

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
  const labelEl = button.querySelector<HTMLElement>('.btn__label');
  const label = labelEl?.textContent ?? button.textContent ?? '';
  const setLabel = (text: string) => {
    if (labelEl) labelEl.textContent = text;
    else button.textContent = text;
  };
  setLabel('Copied');
  window.setTimeout(() => {
    setLabel(label);
  }, 2000);
}

function renderActions(record: ApodRecord, presentation: MediaPresentation): void {
  actions.replaceChildren();

  if (presentation.kind === 'image' && (record.hd_image_url || record.image_url)) {
    const save = buildAction({
      label: 'Save HD image',
      variant: 'primary',
      href: record.hd_image_url ?? record.image_url ?? '',
      icon: 'ri-download-line',
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
        icon: 'ri-play-circle-line',
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
        icon: 'ri-external-link-line',
      }),
    );
  }

  const copy = buildAction({
    label: 'Copy link',
    variant: 'secondary',
    icon: 'ri-link',
  }) as HTMLButtonElement;
  copy.addEventListener('click', () => {
    void copyCurrentLink(copy);
  });
  actions.append(copy);
}

/* ------------------------------------------------------------------ nav */

function navIcon(className: string): HTMLElement {
  const icon = document.createElement('i');
  icon.className = `icon ${className}`;
  icon.setAttribute('aria-hidden', 'true');
  return icon;
}

function navButton(
  label: string,
  iconClass: string,
  date: string | null,
  iconAfter = false,
): HTMLElement {
  const text = el('span', undefined, label);
  const icon = navIcon(iconClass);
  if (date) {
    const link = el('a', 'btn btn--secondary');
    link.href = entryPath(date);
    link.append(...(iconAfter ? [text, icon] : [icon, text]));
    return link;
  }
  const disabled = el('span', 'btn btn--secondary');
  disabled.setAttribute('aria-disabled', 'true');
  disabled.append(...(iconAfter ? [text, icon] : [icon, text]));
  return disabled;
}

function renderNeighbours(
  index: ArchiveIndexEntry[],
  date: string,
): { nextTitle: string | null } {
  const { previous, next } = neighbours(index, date, readHideVideos());
  previousDate = previous?.date ?? null;
  nextDate = next?.date ?? null;
  entryNav.replaceChildren(
    navButton('Previous day', 'ri-arrow-left-s-line', previousDate),
    navButton('Next day', 'ri-arrow-right-s-line', nextDate, true),
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
  previousDate = null;
  nextDate = null;
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

// ← / → step between entries. They are ignored while the drawer or the
// mobile menu is open, and while text is being selected or edited.
document.addEventListener('keydown', (event) => {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
  if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
    return;
  }
  const target = event.target as HTMLElement | null;
  if (target?.closest('input, select, textarea, [contenteditable="true"]')) return;
  if (document.querySelector('dialog[open]')) return;

  if (event.key === 'ArrowLeft' && previousDate) {
    event.preventDefault();
    window.location.assign(entryPath(previousDate));
  } else if (event.key === 'ArrowRight' && nextDate) {
    event.preventDefault();
    window.location.assign(entryPath(nextDate));
  }
});

// Joining or leaving a video changes which neighbours are reachable.
window.addEventListener('mohakash:hide-videos-changed', () => {
  void load();
});

void load();
