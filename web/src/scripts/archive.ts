/**
 * Archive page — loads the compact index at runtime and renders a thumbnail
 * grid. Yearly detail files are only fetched when a year is selected.
 */

import { fetchIndex, fetchYear } from '../lib/api';
import { fetchWithCache } from '../lib/cache';
import {
  availableYears,
  countLabel,
  entryPath,
  filterEntries,
  sortNewestFirst,
  visibleSlice,
} from '../lib/archive';
import { formatDate, formatDateShort, isIsoDate } from '../lib/format';
import type { ApodRecord, ArchiveIndexEntry } from '../lib/types';
import {
  buildAction,
  el,
  hide,
  need,
  renderBanner,
  renderEmptyState,
  renderErrorState,
  setBusy,
  show,
} from '../lib/dom';

const PAGE_SIZE = 24;

const bannerHost = need('#archive-banner');
const grid = need('#archive-grid');
const emptyHost = need('#archive-empty');
const moreHost = need('#archive-more');
const countEl = need('#archive-count');
const searchInput = need<HTMLInputElement>('#archive-search');
const yearSelect = need<HTMLSelectElement>('#archive-year');
const dateInput = need<HTMLInputElement>('#archive-date');
const dateHint = need('#archive-date-hint');

const state = {
  entries: [] as ArchiveIndexEntry[],
  query: '',
  year: null as number | null,
  visible: PAGE_SIZE,
  /** Full records of the selected year, used for richer alternative text. */
  enriched: new Map<string, ApodRecord>(),
};

/* ------------------------------------------------------------- rendering */

function buildCard(entry: ArchiveIndexEntry): HTMLElement {
  const link = el('a', 'list-row list-row--grid');
  link.href = entryPath(entry.date);

  const thumb = el('span', 'list-row__thumb');
  const enriched = state.enriched.get(entry.date);
  const src = entry.thumbnail_url ?? entry.image_url ?? null;

  if (src) {
    const image = el('img');
    image.src = src;
    image.alt = enriched?.alt_text?.trim() || `Thumbnail of ${entry.title}`;
    image.loading = 'lazy';
    image.decoding = 'async';
    image.addEventListener('error', () => {
      const label = el('p', 'media-frame__label', 'Image unavailable');
      label.style.fontSize = '12px';
      image.remove();
      thumb.append(label);
    });
    thumb.append(image);
  } else {
    const label = el('p', 'media-frame__label', 'No image');
    label.style.fontSize = '12px';
    thumb.append(label);
  }

  const text = el('span', 'list-row__text');
  text.append(el('span', 'list-row__meta', formatDateShort(entry.date)));
  text.append(el('span', 'list-row__title', entry.title));

  link.append(thumb, text);
  return link;
}

function renderGrid(): void {
  const filtered = sortNewestFirst(filterEntries(state.entries, { query: state.query, year: state.year }));

  countEl.textContent = countLabel(filtered.length);
  grid.replaceChildren();
  moreHost.replaceChildren();

  if (filtered.length === 0) {
    hide(grid);
    hide(moreHost);
    show(emptyHost);
    const term = state.query || (state.year ? String(state.year) : '');
    renderEmptyState(emptyHost, {
      title: term ? `No entries match "${term}"` : 'No entries match the current filters',
      text: 'Try a different word, or clear the year filter.',
      actionLabel: 'Clear filters',
      onAction: clearFilters,
    });
    return;
  }

  hide(emptyHost);
  show(grid);

  for (const entry of visibleSlice(filtered, state.visible)) {
    grid.append(buildCard(entry));
  }

  if (filtered.length > state.visible) {
    const button = buildAction({
      label: 'Load more',
      variant: 'secondary',
      onClick: () => {
        state.visible += PAGE_SIZE;
        renderGrid();
      },
    });
    moreHost.append(button);
  }
}

function clearFilters(): void {
  state.query = '';
  state.year = null;
  state.visible = PAGE_SIZE;
  searchInput.value = '';
  yearSelect.value = '';
  renderGrid();
}

/* -------------------------------------------------------------- loading */

function renderSkeleton(): void {
  grid.replaceChildren();
  for (let i = 0; i < 8; i += 1) {
    const card = el('div', 'stack-sm');
    const block = el('div', 'skeleton skeleton--block');
    block.style.aspectRatio = '4 / 3';
    const meta = el('div', 'skeleton skeleton--line');
    meta.style.width = '40%';
    const title = el('div', 'skeleton skeleton--line');
    title.style.width = '85%';
    card.append(block, meta, title);
    grid.append(card);
  }
}

function populateYears(): void {
  const years = availableYears(state.entries);
  yearSelect.replaceChildren();
  const all = el('option', undefined, 'All years');
  all.value = '';
  yearSelect.append(all);
  for (const year of years) {
    const option = el('option', undefined, String(year));
    option.value = String(year);
    yearSelect.append(option);
  }
  if (state.year !== null) yearSelect.value = String(state.year);
}

async function loadYearDetails(year: number): Promise<void> {
  try {
    const result = await fetchWithCache(`year-${year}`, () => fetchYear(year));
    for (const record of result.data.records) {
      state.enriched.set(record.date, record);
    }
    renderGrid();
    if (result.fromCache) {
      renderBanner(bannerHost, {
        message: `Couldn't refresh the details for ${year}. Showing the saved copy from ${formatDate(result.savedAt ?? '')}.`,
        actionLabel: 'Retry',
        onAction: () => {
          void loadYearDetails(year);
        },
      });
    }
  } catch {
    renderBanner(bannerHost, {
      message: `Couldn't load the details for ${year}. The list below still works.`,
      actionLabel: 'Retry',
      onAction: () => {
        void loadYearDetails(year);
      },
    });
  }
}

async function load(): Promise<void> {
  bannerHost.replaceChildren();
  renderSkeleton();
  setBusy(grid, true);
  countEl.textContent = '';

  try {
    const result = await fetchWithCache('archive-index', () => fetchIndex());
    state.entries = result.data.entries ?? [];
    populateYears();
    renderGrid();
    setBusy(grid, false);

    if (result.fromCache) {
      renderBanner(bannerHost, {
        message: `Couldn't refresh. Showing the saved copy from ${formatDate(result.savedAt ?? '')}.`,
        actionLabel: 'Retry',
        onAction: () => {
          void load();
        },
      });
    }
  } catch {
    setBusy(grid, false);
    grid.replaceChildren();
    show(errorHost());
    renderErrorState(errorHost(), {
      title: 'The archive is not available',
      text: 'Check your connection and try again. You can still open today’s picture.',
      actionLabel: 'Try again',
      onAction: () => {
        void load();
      },
      secondaryLabel: 'Today',
      secondaryHref: '/',
    });
  }
}

function errorHost(): HTMLElement {
  const existing = document.getElementById('archive-error');
  if (existing) return existing as HTMLElement;
  const node = el('div');
  node.id = 'archive-error';
  grid.after(node);
  return node;
}

/* --------------------------------------------------------------- events */

searchInput.addEventListener('input', () => {
  state.query = searchInput.value.trim();
  state.visible = PAGE_SIZE;
  renderGrid();
});

yearSelect.addEventListener('change', () => {
  const value = yearSelect.value;
  state.year = value ? Number(value) : null;
  state.visible = PAGE_SIZE;
  renderGrid();
  if (state.year !== null) void loadYearDetails(state.year);
});

dateInput.addEventListener('change', () => {
  const value = dateInput.value;
  dateHint.textContent = '';
  if (!isIsoDate(value)) return;

  const known = state.entries.some((entry) => entry.date === value);
  if (known) {
    window.location.href = entryPath(value);
    return;
  }
  dateHint.textContent = `No entry was published for ${formatDate(value)}.`;
});

const initialYear = new URLSearchParams(window.location.search).get('year');
if (initialYear && /^\d{4}$/.test(initialYear)) {
  state.year = Number(initialYear);
  yearSelect.value = initialYear;
}

void load();
