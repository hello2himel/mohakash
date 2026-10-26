/**
 * Clipboard helpers shared by the Today share button and the entry "Copy link"
 * button. The fallback keeps working on browsers that block the async
 * clipboard API (for example on plain HTTP).
 */

/** Copy [text] and report whether the browser accepted it. */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    /* fall through to the legacy path */
  }

  try {
    const helper = document.createElement('textarea');
    helper.value = text;
    helper.setAttribute('readonly', '');
    helper.style.position = 'fixed';
    helper.style.left = '-9999px';
    try {
      document.body.append(helper);
      helper.select();
      return (document as unknown as { execCommand(command: string): boolean }).execCommand('copy');
    } finally {
      helper.remove();
    }
  } catch {
    return false;
  }
}

/**
 * Copy [text] and give the button a short "Copied" feedback.
 *
 * Text buttons get a temporary label. Icon-only buttons swap to a check icon
 * and show a small floating note, because their visible label is hidden by
 * design. The original state is restored after two seconds.
 */
export async function copyWithFeedback(button: HTMLButtonElement, text: string): Promise<boolean> {
  const copied = await copyText(text);
  const iconOnly = button.classList.contains('btn--icon-only');

  if (iconOnly) {
    showIconOnlyFeedback(button, copied);
    return copied;
  }

  const label = button.querySelector<HTMLElement>('.btn__label');
  const original = label?.textContent ?? button.textContent ?? '';

  const setLabel = (value: string) => {
    if (label) label.textContent = value;
    else button.textContent = value;
  };

  setLabel(copied ? 'Copied' : 'Copy failed');
  window.setTimeout(() => setLabel(original), 2000);
  return copied;
}

function showIconOnlyFeedback(button: HTMLButtonElement, copied: boolean): void {
  const icon = button.querySelector<HTMLElement>('.icon');
  const originalIcon = icon?.className ?? '';
  const originalAria = button.getAttribute('aria-label');
  const originalTitle = button.title;
  const message = copied ? 'Copied' : 'Copy failed';

  button.querySelector('.btn__feedback')?.remove();
  const feedback = document.createElement('span');
  feedback.className = 'btn__feedback';
  feedback.setAttribute('role', 'status');
  feedback.textContent = message;
  button.append(feedback);

  button.classList.add('btn--feedback-visible');
  if (icon && copied) icon.className = 'icon ri-check-line';
  button.setAttribute('aria-label', message);
  button.title = message;

  window.setTimeout(() => {
    feedback.remove();
    button.classList.remove('btn--feedback-visible');
    if (icon) icon.className = originalIcon;
    if (originalAria) button.setAttribute('aria-label', originalAria);
    else button.removeAttribute('aria-label');
    if (originalTitle) button.title = originalTitle;
    else button.removeAttribute('title');
  }, 2000);
}
