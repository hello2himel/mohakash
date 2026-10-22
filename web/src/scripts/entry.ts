/**
 * Entry page — one entry of the archive, addressed as `/archive/YYYY-MM-DD`.
 *
 * The static shell is served for every `/archive/<date>` path (see the
 * Netlify rewrite in `netlify.toml`); the date is read from the URL and the
 * record is fetched from the API at runtime.
 */

import { fetchIndex, fetchYear } from '../lib/api';
import { fetchWithCache } from '../lib/cache';
import { entryPath, neighbours } from '../lib/archive';
import { formatDate, formatSyncedAgo, isIsoDate, yearOf } from '../lib/format';
import { mediaPresentation } from '../lib/media';
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
  setBusy,
  show,
} from '../lib/dom';

const banner = need('#entry-banner');
const loading = need('#entry-loading');
const content = need('#entry-content');
const errorHost = need('#entry-error');
const breadcrumb = need('#entry-breadcrumb');
const crumbTitle = need('#entry-crumb-title');
const yearLink = need<HTMLAnchorElement>('#entry-year-link');
const frame = need('#entry-frame');
const dateLine = need('#entry-date');
const title = need('#entry-title');
const syncLine = need('#entry-sync');
const actions = need('#entry-actions');
const mediaNote = need('#entry-media-note');
const explanation = need('#entry-explanation');
const creditText = need('#entry-credit');
const entryNav = need('#entry-nav');

/** The date addressed by the current URL. */
function requestedDate(): string | null {
  const parts = window.location.pathname.split('/').filter(Boolean);
  if (parts[0] !== 'archive') return null;
  const last = parts[parts.length - 1] ?? '';
  // The shell itself (`/archive/entry`) is not an entry.
  if (last === 'entry') return null;
  return last;
}

function notFound(message = 'This page doesn’t exist, or the entry date isn’t in the archive.'): void {
  hide(loading);
  setBusy(loading, false);
  hide(content);
  show(errorHost);
  renderErrorState(errorHost, {
    title: 'Nothing out here',
    text: message,
    actionLabel: 'Today',
    onAction: () => {
      window.location.href = '/';
    },
    secondaryLabel: 'Archive',
    secondaryHref: '/archive',
  });
}

function mediaNoteText(kind: string): string {
  if (kind === 'video') return 'This entry is a video, so it opens on the original page.';
  if (kind === 'other') return 'This entry is embedded on the original page, so it opens there.';
  return 'No picture is available for this entry, so it opens on the original page.';
}

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

function renderNeighbours(index: ArchiveIndexEntry[], date: string): void {
  entryNav.replaceChildren();
  const { previous, next } = neighbours(index, date);

  if (previous) {
    const link = el('a', 'entry-nav__link');
    link.href = entryPath(previous.date);
    link.append(el('span', 'entry-nav__label', 'Previous day'));
    link.append(el('span', 'entry-nav__title', previous.title));
    entryNav.append(link);
  }

  if (next) {
    const link = el('a', 'entry-nav__link entry-nav__link--end');
    link.href = entryPath(next.date);
    link.append(el('span', 'entry-nav__label', 'Next day'));
    link.append(el('span', 'entry-nav__title', next.title));
    entryNav.append(link);
  }
}

function render(record: ApodRecord, index: ArchiveIndexEntry[]): void {
  const presentation = mediaPresentation(record);

  applyPresentation(frame, presentation, { priority: true });

  const date = formatDate(record.date);
  const credit = (record.credit ?? record.copyright ?? '').trim();
  dateLine.textContent = credit ? `${date} · Credit: ${credit}` : date;

  title.textContent = record.title;
  document.title = `${record.title} · Mohakash`;

  const synced = record.updated_at;
  syncLine.textContent = synced ? formatSyncedAgo(synced) : '';
  hide(syncLine);

  crumbTitle.textContent = record.title;
  yearLink.textContent = String(yearOf(record.date));
  yearLink.href = `/archive?year=${yearOf(record.date)}`;
  show(breadcrumb);

  actions.replaceChildren();
  if (presentation.showHdAction && record.hd_image_url) {
    actions.append(
      buildAction({
        label: 'View HD image',
        variant: 'primary',
        href: record.hd_image_url,
        external: true,
      }),
    );
  }

  const originalIsPrimary = !presentation.showHdAction;
  if (record.apod_url) {
    actions.append(
      buildAction({
        label: 'Original APOD page',
        variant: originalIsPrimary ? 'primary' : 'secondary',
        href: record.apod_url,
        external: true,
      }),
    );
  }

  const copyButton = el('button', 'btn btn--secondary');
  copyButton.type = 'button';
  copyButton.textContent = 'Copy link';
  copyButton.addEventListener('click', () => {
    void copyCurrentLink(copyButton);
  });
  actions.append(copyButton);

  if (presentation.opensOnOriginalPage) {
    mediaNote.textContent = mediaNoteText(presentation.kind);
    show(mediaNote);
  } else {
    hide(mediaNote);
  }

  renderProse(explanation, record.explanation ?? '');

  const rightsSentence =
    'Rights belong to the creator and are not covered by this project’s licence. About content rights.';
  creditText.textContent = credit ? `${credit}. ${rightsSentence}` : rightsSentence;

  renderNeighbours(index, record.date);

  hide(errorHost);
  hide(loading);
  show(content);
  setBusy(loading, false);
}

async function load(): Promise<void> {
  const date = requestedDate();
  if (!date || !isIsoDate(date)) {
    notFound();
    return;
  }

  show(loading);
  hide(content);
  hide(errorHost);
  setBusy(loading, true);
  hideBanner(banner);

  const year = yearOf(date);

  try {
    const indexResult = await fetchWithCache('archive-index', () => fetchIndex());
    const index = indexResult.data.entries ?? [];

    const yearResult = await fetchWithCache(`year-${year}`, () => fetchYear(year));
    const record = yearResult.data.records.find((item) => item.date === date);

    if (!record) {
      notFound();
      return;
    }

    render(record, index);

    if (indexResult.fromCache || yearResult.fromCache) {
      applyBanner(banner, {
        message: `Couldn't refresh. Showing the saved copy from ${formatDate(date)}.`,
        actionLabel: 'Retry',
        onAction: () => {
          void load();
        },
      });
    }
  } catch {
    hide(loading);
    setBusy(loading, false);
    show(errorHost);
    renderErrorState(errorHost, {
      title: "This entry isn't available",
      text: 'Check your connection and try again. You can still browse the archive.',
      actionLabel: 'Try again',
      onAction: () => {
        void load();
      },
      secondaryLabel: 'Archive',
      secondaryHref: '/archive',
    });
  }
}

void load();
