/**
 * Today page — loads the newest record at runtime.
 */

import { fetchLatest, fetchLatestImage } from '../lib/api';
import { fetchWithCache } from '../lib/cache';
import { formatDate, formatSyncedAgo } from '../lib/format';
import { mediaPresentation, needsPictureFallback } from '../lib/media';
import { readHideVideos } from '../lib/preferences';
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

function render(record: ApodRecord, options: { substitutedVideo?: boolean } = {}): void {
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
        label: 'Save HD image',
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

  if (options.substitutedVideo) {
    mediaNote.textContent = 'The newest entry is a video, so the latest picture is shown instead.';
    show(mediaNote);
  } else if (presentation.opensOnOriginalPage) {
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
    const hideVideos = readHideVideos();
    const result = await fetchWithCache('latest', () => fetchLatest());
    let record = result.data;
    let fromCache = result.fromCache;
    let substitutedVideo = false;

    // Videos are hidden by default: when the newest entry is a video, the
    // most recent picture is shown instead.
    if (needsPictureFallback(record, hideVideos)) {
      try {
        const picture = await fetchWithCache('latest-image', () => fetchLatestImage());
        record = picture.data;
        fromCache = fromCache || picture.fromCache;
        substitutedVideo = true;
      } catch {
        // The video entry stays; it is the newest entry after all.
      }
    }

    render(record, { substitutedVideo });

    if (fromCache) {
      const cachedDate = formatDate(record.date);
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
