/**
 * "Read full explanation" drawer — one modal dialog per page, filled at
 * runtime by `today.ts` and `entry.ts`.
 *
 * The markup lives in `components/ExplanationDrawer.astro`; this module only
 * wires behaviour: open (focus moves in), focus trap, Esc / close button /
 * scrim click close, focus returns to the control that opened it.
 */

import { formatDate } from './format';
import { SITE } from './site';
import type { ApodRecord } from './types';
import { buildAction, el, renderProse } from './dom';
import { mediaPresentation } from './media';

export interface DrawerContent {
  record: ApodRecord;
  /** Title of the entry shown under "Tomorrow's picture", when it exists. */
  nextTitle?: string | null;
}

type DrawerDialog = HTMLDialogElement & HTMLElement;

let host: DrawerDialog | null = null;
let current: DrawerContent | null = null;

function required<T extends HTMLElement>(selector: string): T {
  const node = host?.querySelector<T>(selector);
  if (!node) throw new Error(`The drawer is missing ${selector}.`);
  return node;
}

/* Focus movement, Tab trapping and Esc are handled natively by <dialog>
   (showModal / close events), so no manual trap is needed here. */

/** Fill every part of the drawer from a record. */
function fill(content: DrawerContent): void {
  if (!host) return;
  const record = content.record;
  const credit = (record.credit ?? record.copyright ?? '').trim();

  const dateEl = required('.drawer__date');
  const titleEl = required('.drawer__title');
  dateEl.textContent = formatDate(record.date);
  titleEl.textContent = record.title;

  const body = required<HTMLElement>('.drawer__body');
  body.replaceChildren();

  const prose = el('div', 'drawer__prose');
  renderProse(prose, record.explanation ?? '');
  body.append(prose);

  if (content.nextTitle) {
    const tomorrow = el('p', 'drawer__tomorrow', `Tomorrow's picture: ${content.nextTitle}`);
    body.append(tomorrow);
  }

  const box = el('div', 'drawer__credits');
  box.append(el('h3', 'drawer__credits-title', 'Image credit and copyright'));
  const rightsLead = credit ? `${credit}. ` : '';
  const paragraph = el('p', 'drawer__credits-text');
  paragraph.append(document.createTextNode(`${rightsLead}Rights belong to the creator. `));
  const rightsLink = el('a', undefined, 'About content rights');
  rightsLink.href = SITE.rightsUrl;
  paragraph.append(rightsLink);
  box.append(paragraph);
  body.append(box);

  const footer = required<HTMLElement>('.drawer__footer');
  footer.replaceChildren();
  const presentation = mediaPresentation(record);
  if (presentation.kind === 'image' && (record.hd_image_url || record.image_url)) {
    const save = buildAction({
      label: 'Save HD image',
      variant: 'primary',
      href: record.hd_image_url ?? record.image_url ?? '',
      icon: 'ri-download-line',
    });
    if (save instanceof HTMLAnchorElement) save.setAttribute('download', '');
    footer.append(save);
  } else if (record.apod_url) {
    footer.append(
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
    footer.append(
      buildAction({
        label: 'Original APOD page',
        variant: 'secondary',
        href: record.apod_url,
        external: true,
        icon: 'ri-external-link-line',
      }),
    );
  }
}

/**
 * Wire the drawer once per page. Returns `false` when the page has no drawer.
 *
 * The native `<dialog>` handles the modal behaviour itself — focus moves in
 * on `showModal()`, Tab stays inside, Esc closes it and focus returns to the
 * control that opened it. This module adds the scrim click, keeps the page
 * behind still and fills the contents when the trigger is pressed.
 */
export function initDrawer(): boolean {
  const node = document.getElementById('explanation-drawer');
  if (!node) return false;
  host = node as DrawerDialog;

  host.addEventListener('close', () => {
    document.body.classList.remove('has-drawer-open');
  });

  host.addEventListener('click', (event) => {
    // Clicks that land on the dialog box itself come from the scrim area —
    // the panel covers everything else.
    if (event.target === host && host) host.close();
  });

  host.querySelector<HTMLButtonElement>('[data-drawer-close]')?.addEventListener('click', () => {
    host?.close();
  });

  for (const trigger of document.querySelectorAll<HTMLElement>('[data-drawer-trigger]')) {
    trigger.addEventListener('click', () => {
      if (!host) return;
      if (current) fill(current);
      document.body.classList.add('has-drawer-open');
      host.showModal();
      // Let the native dialog put focus on its first control (the close button).
    });
  }
  return true;
}

/** Remember (and paint) what the drawer should show when it opens. */
export function setDrawerContent(content: DrawerContent): void {
  current = content;
  fill(content);
}
