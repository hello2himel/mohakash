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

/** A play glyph drawn on top of a video poster frame. */
export function playGlyph(): SVGSVGElement {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.5');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');
  const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
  circle.setAttribute('cx', '12');
  circle.setAttribute('cy', '12');
  circle.setAttribute('r', '10');
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', 'M10 8.5l6 3.5-6 3.5z');
  svg.append(circle, path);
  return svg;
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
  /** `hero` uses a 16/9 frame, `thumb` and `list` use 4/3. */
  ratio: 'hero' | 'thumb' | 'list';
  /** Load the image eagerly (used for the first, visible picture). */
  priority?: boolean;
}

/**
 * Draw a media frame.
 *
 * The frame is always kept: when the picture fails to load the `<img>` is
 * replaced by a muted "Image unavailable" label inside the same frame.
 */
export function renderMediaFrame(
  host: HTMLElement,
  presentation: MediaPresentation,
  options: MediaFrameOptions,
): HTMLElement {
  host.replaceChildren();
  const frame = el('div', `media-frame media-frame--${options.ratio}`);

  if (presentation.src) {
    const image = el('img', 'media-frame__img');
    image.src = presentation.src;
    image.alt = presentation.alt ?? '';
    image.loading = options.priority ? 'eager' : 'lazy';
    image.decoding = 'async';
    if (options.priority) image.setAttribute('fetchpriority', 'high');
    image.addEventListener('error', () => {
      const label = el('p', 'media-frame__label', IMAGE_UNAVAILABLE_LABEL);
      image.remove();
      frame.prepend(label);
    });
    frame.append(image);
  } else {
    frame.append(el('p', 'media-frame__label', presentation.frameLabel));
  }

  if (presentation.chipLabel) {
    const chip = el('span', 'media-frame__chip', presentation.chipLabel);
    frame.append(chip);
  }

  if (presentation.showPlayGlyph) {
    const glyph = el('div', 'media-frame__glyph');
    glyph.append(playGlyph());
    frame.append(glyph);
  }

  host.append(frame);
  return frame;
}

/** A status banner with one sentence and one action. */
export function renderBanner(
  host: HTMLElement,
  options: { message: string; actionLabel?: string; onAction?: () => void },
): HTMLElement {
  host.replaceChildren();
  const banner = el('div', 'banner');
  banner.setAttribute('role', 'status');
  banner.append(el('p', 'banner__message', options.message));
  if (options.actionLabel && options.onAction) {
    banner.append(buildAction({ label: options.actionLabel, variant: 'secondary', onClick: options.onAction }));
  }
  host.append(banner);
  return banner;
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
