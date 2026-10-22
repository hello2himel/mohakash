/**
 * Archive page — loads the compact index at runtime, renders the fixed
 * thumbnail grid and pages through it. The number of columns and rows is
 * measured from the grid container, so the page never scrolls on desktop.
 */

import { fetchIndex } from '../lib/api';
import { fetchWithCache } from '../lib/cache';
import {
  compactArchiveIndex,
  countLabel,
  entryPath,
  gridGeometry,
  pageCount,
  selectEntries,
} from '../lib/archive';
import { formatDate, formatDateShort, isIsoDate } from '../lib/format';
import { readHideVideos } from '../lib/preferences';
import type { ArchiveIndexEntry } from '../lib/types';
import {
  el,
  need,
  renderEmptyState,
  renderErrorState,
} from '../lib/dom';

const grid = need('#archive-grid');
const stateHost = need('#archive-state');
const pager = need('#archive-pager');
const prevButton = need<HTMLButtonElement>('#archive-prev');
const nextButton = need<HTMLButtonElement>('#archive-next');
const pageCurrent = need('#archive-page-current');
const pageTotal = need('#archive-page-total');
const countEl = need('#archive-count');
const searchInput = need<HTMLInputElement>('#archive-search');
const yearSelect = need<HTMLSelectElement>('#archive-year');
const dateInput = need<HTMLInputElement>('#archive-date');

const MIN_THUMB = 200;
const GAP = 16;
const MOBILE_ROWS = 4;
const desktopQuery = window.matchMedia('(min-width: 900px) and (min-height: 600px)');

const state = {
  entries: [] as ArchiveIndexEntry[],
  query: '',
  year: null as number | null,
  page: 0,
  pageSize: 1,
  columns: 0,
  rows: 0,
  firstDate: null as string | null,
  hideVideos: true,
  loading: true,
};

/* ------------------------------------------------------------- rendering */

function filtered(): ArchiveIndexEntry[] {
  return selectEntries(state.entries, {
    query: state.query,
    year: state.year,
    hideVideos: state.hideVideos,
  });
}

function playGlyph(): HTMLElement {
  const host = el('div', 'media-frame__glyph');
  const icon = document.createElement('i');
  icon.className = 'icon ri-play-circle-line';
  icon.setAttribute('aria-hidden', 'true');
  host.append(icon);
  return host;
}

function buildCard(entry: ArchiveIndexEntry): HTMLElement {
  const card = el('a', 'archive-card');
  card.href = entryPath(entry.date);

  const thumb = el('div', 'archive-card__thumb');
  if (entry.media_type === 'video') thumb.append(playGlyph());

  const imageUrl = entry.thumbnail_url ?? entry.image_url ?? null;
  if (imageUrl) {
    const image = document.createElement('img');
    image.src = imageUrl;
    image.alt = '';
    image.loading = 'lazy';
    image.decoding = 'async';
    image.addEventListener(
      'error',
      () => {
        image.remove();
      },
      { once: true },
    );
    thumb.prepend(image);
  }

  const openHint = el('span', 'archive-card__open');
  const openIcon = document.createElement('i');
  openIcon.className = 'icon ri-arrow-right-line';
  openIcon.setAttribute('aria-hidden', 'true');
  openHint.append(openIcon);
  thumb.append(openHint);

  const date = el('span', 'archive-card__date', formatDateShort(entry.date));
  const title = el('span', 'archive-card__title', entry.title);
  card.append(thumb, date, title);
  return card;
}

function renderSkeleton(): void {
  grid.replaceChildren();
  const total = Math.max(1, state.columns * state.rows);
  for (let index = 0; index < total; index += 1) {
    const card = el('div', 'archive-card');
    card.setAttribute('aria-hidden', 'true');
    const thumb = el('div', 'archive-card__thumb');
    thumb.append(el('div', 'skeleton skeleton--thumb'));
    const date = el('div', 'skeleton skeleton--line');
    date.style.width = '42%';
    const title = el('div', 'skeleton skeleton--line');
    title.style.width = '86%';
    card.append(thumb, date, title);
    grid.append(card);
  }
}

function updatePager(pages: number): void {
  pageCurrent.textContent = String(state.page + 1);
  pageTotal.textContent = String(pages);
  prevButton.disabled = state.page <= 0;
  nextButton.disabled = state.page >= pages - 1;
}

function renderGrid(): void {
  const items = filtered();
  countEl.textContent = countLabel(items.length);

  if (items.length === 0) {
    grid.hidden = true;
    pager.hidden = true;
    stateHost.hidden = false;
    grid.setAttribute('aria-busy', 'false');
    const term = state.query || (state.year ? String(state.year) : '');
    renderEmptyState(stateHost, {
      title: term ? `No entries match "${term}"` : 'No entries match the current filters',
      text: 'Try a different word, or clear the year filter.',
      actionLabel: 'Clear filters',
      onAction: clearFilters,
    });
    return;
  }

  stateHost.hidden = true;
  grid.hidden = false;
  pager.hidden = false;

  const pages = pageCount(items.length, state.pageSize);
  if (state.page > pages - 1) state.page = pages - 1;

  const start = state.page * state.pageSize;
  const visible = items.slice(start, start + state.pageSize);
  state.firstDate = visible[0]?.date ?? null;

  grid.replaceChildren(...visible.map(buildCard));
  grid.setAttribute('aria-busy', 'false');
  updatePager(pages);
}

/* -------------------------------------------------------------- geometry */

function measureGeometry(): { columns: number; rows: number } {
  const width = grid.clientWidth;
  if (!(width > 0)) return { columns: state.columns || 1, rows: state.rows || 1 };
  if (!desktopQuery.matches) {
    return {
      columns: Math.max(1, Math.floor((width + GAP) / (MIN_THUMB + GAP))),
      rows: MOBILE_ROWS,
    };
  }
  const height = grid.clientHeight;
  if (!(height > 0)) return { columns: state.columns || 1, rows: state.rows || 1 };
  return gridGeometry(width, height, MIN_THUMB, GAP);
}

function applyGeometry(columns: number, rows: number): void {
  state.columns = columns;
  state.rows = rows;
  state.pageSize = Math.max(1, columns * rows);
  grid.style.gridTemplateColumns = `repeat(${columns}, minmax(0, 1fr))`;
  grid.style.gridTemplateRows = desktopQuery.matches
    ? `repeat(${rows}, minmax(0, 1fr))`
    : '';
}

/** Re-measure the grid; keep the first visible entry when the page size changes. */
function refreshGeometry(initial = false): void {
  if (!initial && grid.hidden) return;
  const next = measureGeometry();
  if (!initial && next.columns === state.columns && next.rows === state.rows) return;

  const anchor = state.firstDate;
  applyGeometry(next.columns, next.rows);

  if (state.loading) {
    renderSkeleton();
    return;
  }

  if (anchor) {
    const index = filtered().findIndex((entry) => entry.date === anchor);
    state.page = index >= 0 ? Math.floor(index / state.pageSize) : 0;
  } else {
    state.page = 0;
  }
  renderGrid();
}

/* ---------------------------------------------------------------- filters */

function clearFilters(): void {
  state.query = '';
  state.year = null;
  state.page = 0;
  searchInput.value = '';
  yearSelect.value = '';
  dateInput.value = '';
  dateInput.setCustomValidity('');
  renderGrid();
}

/* ------------------------------------------------------------------ load */

async function load(): Promise<void> {
  state.loading = true;
  grid.hidden = false;
  stateHost.hidden = true;
  pager.hidden = true;
  countEl.textContent = '';
  grid.setAttribute('aria-busy', 'true');
  renderSkeleton();

  try {
    const result = await fetchWithCache('archive-index', () => fetchIndex(), {
      store: compactArchiveIndex,
    });
    state.entries = result.data.entries ?? [];
    state.loading = false;
    refreshGeometry(true);
    renderGrid();
  } catch {
    state.loading = false;
    grid.hidden = true;
    pager.hidden = true;
    stateHost.hidden = false;
    grid.setAttribute('aria-busy', 'false');
    renderErrorState(stateHost, {
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

/* ---------------------------------------------------------------- events */

searchInput.addEventListener('input', () => {
  state.query = searchInput.value.trim();
  state.page = 0;
  renderGrid();
});

yearSelect.addEventListener('change', () => {
  const value = yearSelect.value;
  state.year = value ? Number(value) : null;
  state.page = 0;
  renderGrid();
});

dateInput.addEventListener('input', () => {
  dateInput.setCustomValidity('');
});

dateInput.addEventListener('change', () => {
  const value = dateInput.value;
  dateInput.setCustomValidity('');
  if (!isIsoDate(value)) return;

  const known = state.entries.some((entry) => entry.date === value);
  if (known) {
    window.location.href = entryPath(value);
    return;
  }
  dateInput.setCustomValidity(`No entry was published for ${formatDate(value)}.`);
  dateInput.reportValidity();
});

function goToPage(page: number): void {
  const items = filtered();
  if (!items.length) return;
  const pages = pageCount(items.length, state.pageSize);
  const next = Math.max(0, Math.min(page, pages - 1));
  if (next === state.page) return;
  state.page = next;
  renderGrid();
}

prevButton.addEventListener('click', () => goToPage(state.page - 1));
nextButton.addEventListener('click', () => goToPage(state.page + 1));

document.addEventListener('keydown', (event) => {
  if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return;
  const target = event.target as HTMLElement | null;
  if (target?.closest('input, select, textarea, [contenteditable="true"]')) return;
  if (state.loading || pager.hidden) return;
  if (event.key === 'ArrowLeft') {
    event.preventDefault();
    goToPage(state.page - 1);
  } else if (event.key === 'ArrowRight') {
    event.preventDefault();
    goToPage(state.page + 1);
  }
});

window.addEventListener('mohakash:hide-videos-changed', () => {
  state.hideVideos = readHideVideos();
  state.page = 0;
  renderGrid();
});

const observer = new ResizeObserver(() => refreshGeometry());
observer.observe(grid);
desktopQuery.addEventListener('change', () => refreshGeometry());

// Initial state.
state.hideVideos = readHideVideos();
const initialYear = new URLSearchParams(window.location.search).get('year');
if (initialYear && /^\d{4}$/.test(initialYear)) {
  state.year = Number(initialYear);
  yearSelect.value = initialYear;
}

applyGeometry(1, 1);
void load();
