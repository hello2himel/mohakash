/**
 * Today page — loads the newest record at runtime.
 */

import { fetchLatest } from '../lib/api';
import { fetchWithCache } from '../lib/cache';
import { formatDate, formatSyncedAgo } from '../lib/format';
import { mediaPresentation } from '../lib/media';
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
  setBusy,
  show,
} from '../lib/dom';

const banner = need('#today-banner');
const loading = need('#today-loading');
const content = need('#today-content');
const errorHost = need('#today-error');

const frame = need('#today-frame');
const dateLine = need('#today-date');
const title = need('#today-title');
const syncLine = need('#today-sync');
const actions = need('#today-actions');
const mediaNote = need('#today-media-note');
const explanation = need('#today-explanation');

function mediaNoteText(kind: string): string {
  if (kind === 'video') return 'This entry is a video, so it opens on the original page.';
  if (kind === 'other') return 'This entry is embedded on the original page, so it opens there.';
  return 'No picture is available for this entry, so it opens on the original page.';
}

function render(record: ApodRecord): void {
  const presentation = mediaPresentation(record);

  applyPresentation(frame, presentation, { priority: true });

  const date = formatDate(record.date);
  const credit = (record.credit ?? record.copyright ?? '').trim();
  dateLine.textContent = credit ? `${date} · Credit: ${credit}` : date;

  title.textContent = record.title;
  document.title = `${record.title} · Mohakash`;

  const synced = record.last_successful_sync ?? record.updated_at;
  syncLine.textContent = synced ? formatSyncedAgo(synced) : '';
  hide(syncLine);

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

  actions.append(buildAction({ label: 'Browse archive', variant: 'secondary', href: '/archive' }));

  if (presentation.opensOnOriginalPage) {
    mediaNote.textContent = mediaNoteText(presentation.kind);
    show(mediaNote);
  } else {
    hide(mediaNote);
  }

  renderProse(explanation, record.explanation ?? '');

  hide(errorHost);
  hide(loading);
  show(content);
  setBusy(loading, false);
}

async function load(): Promise<void> {
  show(loading);
  hide(content);
  hide(errorHost);
  setBusy(loading, true);
  hideBanner(banner);

  try {
    const result = await fetchWithCache('latest', () => fetchLatest());
    render(result.data);

    if (result.fromCache) {
      const cachedDate = formatDate(result.data.date);
      applyBanner(banner, {
        message: `Couldn't refresh. Showing the saved copy from ${cachedDate}.`,
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
}

void load();
