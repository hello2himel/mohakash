/**
 * Visitor preferences.
 *
 * The site remembers a single preference: whether video entries are hidden.
 * Videos are hidden by default — the project is about pictures, and a video
 * entry cannot be used as a wallpaper.
 *
 * The preference lives in the browser's local storage, so it survives a
 * reload and applies to every page (Today, Archive and the entry pages).
 */

import { getStorage } from './cache';

const HIDE_VIDEOS_KEY = 'mohakash:v1:hide-videos';

/** Videos are hidden unless the visitor says otherwise. */
export const HIDE_VIDEOS_DEFAULT = true;

/** Read the "hide videos" preference. Defaults to `true`. */
export function readHideVideos(): boolean {
  try {
    const raw = getStorage().getItem(HIDE_VIDEOS_KEY);
    if (raw === null) return HIDE_VIDEOS_DEFAULT;
    return raw !== 'false';
  } catch {
    return HIDE_VIDEOS_DEFAULT;
  }
}

/** Store the "hide videos" preference. */
export function writeHideVideos(value: boolean): void {
  try {
    getStorage().setItem(HIDE_VIDEOS_KEY, value ? 'true' : 'false');
  } catch {
    /* storage blocked — the preference simply does not survive the session */
  }
}
