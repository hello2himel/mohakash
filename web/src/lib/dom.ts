/**
 * Tiny DOM helpers shared by the runtime scripts.
 * Everything here runs in the browser; the pure logic lives in the sibling
 * modules so it can be unit tested.
 */

import { IMAGE_UNAVAILABLE_LABEL, type MediaPresentation } from './media';

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className?: string,
  text?: string,
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

export function svgIcon(paths: string[], viewBox = '0 0 24 24'): SVGSVGElement {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', viewBox);
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.8');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');
  for (const d of paths) {
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', d);
    svg.append(path);
  }
  return svg;
}

export interface ListRowContent {
  href: string;
  meta: string;
  title: string;
  thumbnailUrl?: string | null;
  alt?: string;
  fallback?: string;
}

/** Clone the server-rendered row template and fill it in. */
export function createListRow(template: HTMLTemplateElement, content: ListRowContent): HTMLElement {
  const fragment = template.content.cloneNode(true) as DocumentFragment;
  const link = fragment.querySelector('a');
  if (!link) throw new Error('The list row template is missing its link.');

  link.setAttribute('href', content.href);

  const meta = fragment.querySelector('.list-row__meta');
  if (meta) meta.textContent = content.meta;

  const title = fragment.querySelector('.list-row__title');
  if (title) title.textContent = content.title;

  const image = fragment.querySelector<HTMLImageElement>('.list-row__image');
  const fallback = fragment.querySelector<HTMLElement>('.list-row__fallback');
  if (fallback) fallback.textContent = content.fallback ?? 'No image';

  if (image) {
    image.onerror = null;
    if (content.thumbnailUrl) {
      image.setAttribute('src', content.thumbnailUrl);
      image.setAttribute('alt', content.alt ?? '');
      image.hidden = false;
      image.onerror = () => {
        image.hidden = true;
        if (fallback) fallback.hidden = false;
      };
      if (fallback) fallback.hidden = true;
    } else {
      image.hidden = true;
      if (fallback) fallback.hidden = false;
    }
  }

  return link;
}


export type ButtonVariant = 'primary' | 'secondary' | 'text';

export interface ActionOptions {
  label: string;
  variant?: ButtonVariant;
  href?: string;
  onClick?: () => void;
  external?: boolean;
  ariaLabel?: string;
}

/** Build an action: a real `<a>` for links, a real `<button>` for actions. */
export function buildAction(options: ActionOptions): HTMLAnchorElement | HTMLButtonElement {
  const variant = options.variant ?? 'secondary';
  if (options.href) {
    const link = el('a', `btn btn--${variant}`);
    link.href = options.href;
    link.textContent = options.label;
    if (options.external) {
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
    }
    if (options.ariaLabel) link.setAttribute('aria-label', options.ariaLabel);
    return link;
  }
  const button = el('button', `btn btn--${variant}`);
  button.type = 'button';
  button.textContent = options.label;
  if (options.ariaLabel) button.setAttribute('aria-label', options.ariaLabel);
  if (options.onClick) button.addEventListener('click', options.onClick);
  return button;
}

export interface MediaFrameOptions {
  /** Load the image eagerly (used for the first, visible picture). */
  priority?: boolean;
}

/**
 * Fill a media frame that was rendered by the server.
 *
 * The frame is always kept: when the picture fails to load the `<img>` is
 * replaced by a muted "Image unavailable" label inside the same frame.
 */
export function applyPresentation(
  frame: HTMLElement,
  presentation: MediaPresentation,
  options: MediaFrameOptions = {},
): HTMLElement {
  const image = frame.querySelector<HTMLImageElement>('.media-frame__img');
  const label = frame.querySelector<HTMLElement>('.media-frame__label');
  const chip = frame.querySelector<HTMLElement>('.media-frame__chip');
  const glyph = frame.querySelector<HTMLElement>('.media-frame__glyph');

  // Keep the frame's aspect ratio in sync with the loaded picture so the
  // image fills it exactly and no letterboxing (grey bars) appears.
  const syncFrameAspect = () => {
    if (image && image.naturalWidth > 0 && image.naturalHeight > 0) {
      frame.style.aspectRatio = `${image.naturalWidth} / ${image.naturalHeight}`;
    }
  };

  if (image) {
    image.onerror = null;
    if (presentation.src) {
      if (image.getAttribute('src') !== presentation.src) image.setAttribute('src', presentation.src);
      image.alt = presentation.alt ?? '';
      image.loading = options.priority ? 'eager' : 'lazy';
      image.hidden = false;
      image.onerror = () => {
        image.hidden = true;
        frame.style.aspectRatio = '';
        if (label) {
          label.textContent = IMAGE_UNAVAILABLE_LABEL;
          label.hidden = false;
        }
      };
      if (image.complete && image.naturalWidth > 0) {
        syncFrameAspect();
      } else {
        image.addEventListener('load', syncFrameAspect, { once: true });
      }
    } else {
      image.hidden = true;
      image.removeAttribute('src');
      frame.style.aspectRatio = '';
    }
  }

  if (label) {
    label.textContent = presentation.src ? '' : presentation.frameLabel;
    label.hidden = Boolean(presentation.src);
  }

  if (chip) {
    chip.textContent = presentation.chipLabel ?? '';
    chip.hidden = !presentation.chipLabel;
  }

  if (glyph) {
    glyph.hidden = !presentation.showPlayGlyph;
  }

  return frame;
}

/** Fill the server-rendered banner and wire its action. */
export function applyBanner(
  banner: HTMLElement,
  options: { message: string; actionLabel?: string; onAction?: () => void },
): HTMLElement {
  const message = banner.querySelector<HTMLElement>('.banner__message');
  const action = banner.querySelector<HTMLButtonElement>('.banner__action');

  if (message) message.textContent = options.message;
  if (action) {
    if (options.actionLabel) action.textContent = options.actionLabel;
    action.onclick = options.onAction ?? null;
    action.hidden = !options.onAction;
  }

  banner.hidden = false;
  return banner;
}

/** Hide the banner again. */
export function hideBanner(banner: HTMLElement | null | undefined): void {
  if (banner) banner.hidden = true;
}


/** The "error, no cache" state: a heading, one sentence and two actions. */
export function renderErrorState(
  host: HTMLElement,
  options: {
    title: string;
    text: string;
    actionLabel: string;
    onAction: () => void;
    secondaryLabel?: string;
    secondaryHref?: string;
  },
): HTMLElement {
  host.replaceChildren();
  const panel = el('div', 'state-panel');
  panel.append(el('p', 'state-panel__title', options.title));
  panel.append(el('p', 'state-panel__text', options.text));
  const actions = el('div', 'state-panel__actions');
  actions.append(buildAction({ label: options.actionLabel, variant: 'primary', onClick: options.onAction }));
  if (options.secondaryLabel && options.secondaryHref) {
    actions.append(
      buildAction({ label: options.secondaryLabel, variant: 'secondary', href: options.secondaryHref }),
    );
  }
  panel.append(actions);
  host.append(panel);
  return panel;
}

/** The "no results" state. */
export function renderEmptyState(
  host: HTMLElement,
  options: { title: string; text: string; actionLabel?: string; onAction?: () => void },
): HTMLElement {
  host.replaceChildren();
  const panel = el('div', 'state-panel');
  panel.append(el('p', 'state-panel__title', options.title));
  panel.append(el('p', 'state-panel__text', options.text));
  if (options.actionLabel && options.onAction) {
    const actions = el('div', 'state-panel__actions');
    actions.append(buildAction({ label: options.actionLabel, variant: 'secondary', onClick: options.onAction }));
    panel.append(actions);
  }
  host.append(panel);
  return panel;
}

/** Split an explanation into paragraphs (`\n\n` separates them). */
export function parseParagraphs(text: string): string[] {
  return (text ?? '')
    .split(/\n\s*\n/)
    .map((paragraph) => paragraph.replace(/\s*\n\s*/g, ' ').trim())
    .filter((paragraph) => paragraph.length > 0);
}

/** Render an explanation as a sequence of paragraphs. */
export function renderProse(host: HTMLElement, text: string): void {
  host.replaceChildren();
  for (const paragraph of parseParagraphs(text)) {
    host.append(el('p', undefined, paragraph));
  }
}

/** Show or hide a section and keep `aria-busy` in sync. */
export function setBusy(host: HTMLElement, busy: boolean): void {
  host.setAttribute('aria-busy', busy ? 'true' : 'false');
}

export function show(node: HTMLElement | null | undefined): void {
  if (node) node.hidden = false;
}

export function hide(node: HTMLElement | null | undefined): void {
  if (node) node.hidden = true;
}

/** Query an element and fail loudly when the markup and the script disagree. */
export function need<T extends Element = HTMLElement>(selector: string, root: ParentNode = document): T {
  const node = root.querySelector<T>(selector);
  if (!node) throw new Error(`Missing element: ${selector}`);
  return node;
}
