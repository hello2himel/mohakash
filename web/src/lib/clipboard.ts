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
 * Copy [text] and give the button a short "Copied" feedback label.
 *
 * The original label is restored after two seconds, so the action stays
 * reversible and does not leave permanent state in the UI.
 */
export async function copyWithFeedback(button: HTMLButtonElement, text: string): Promise<boolean> {
  const copied = await copyText(text);
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
