/**
 * Media presentation rules.
 *
 * A record can be an image, a video (poster frame only), an embedded page
 * ("other"), or an image the pipeline could not resolve. `mediaPresentation()`
 * turns a record into everything the UI needs to draw it, so no component
 * ever has to guess.
 */

import type { RecordLike } from './types';

export type PresentationKind = 'image' | 'video' | 'other' | 'placeholder' | 'missing';

export interface MediaPresentation {
  kind: PresentationKind;
  /** Image source to draw, or `null` for a neutral frame. */
  src: string | null;
  /** Alternative text for the image (already neutral when the source has none). */
  alt: string | null;
  /** `true` when a play glyph is drawn on the frame. */
  showPlayGlyph: boolean;
  /** Chip label drawn on the frame (`Video`, `Embed`, …) or `null`. */
  chipLabel: string | null;
  /** `true` when the "View HD image" action may be offered. */
  showHdAction: boolean;
  /** `true` when the frame has no picture and shows a neutral surface. */
  isNeutral: boolean;
  /** Text drawn inside a neutral frame. */
  frameLabel: string;
  /** `true` when the entry has to be opened on the original page to be seen. */
  opensOnOriginalPage: boolean;
}

/** Neutral description used when the source does not supply alt text. */
export function neutralAlt(record: RecordLike): string {
  return `Astronomy Picture of the Day image for ${record.date}`;
}

/**
 * Decide how a record is presented.
 *
 * - `image` → the picture itself, plus the HD action when one exists.
 * - `video` → a poster frame (`thumbnail_url` or `image_url`) with a play
 *   glyph and a "Video" chip. Never an `<img>` of a video file.
 * - `other` → a neutral frame; the entry opens on the original page.
 * - `placeholder` / `missing` → a neutral surface frame, no broken-image icon
 *   and no HD action.
 */
export function mediaPresentation(record: RecordLike): MediaPresentation {
  const status = record.image_status ?? 'ok';
  const imageUsable = status === 'ok' && Boolean(record.image_url);
  const hdUsable = imageUsable && Boolean(record.hd_image_url);
  const alt = record.alt_text?.trim() || neutralAlt(record);

  if (record.media_type === 'video') {
    const poster = status === 'ok' ? (record.thumbnail_url ?? record.image_url ?? null) : null;
    return {
      kind: 'video',
      src: poster,
      alt: poster ? alt : null,
      showPlayGlyph: true,
      chipLabel: 'Video',
      showHdAction: false,
      isNeutral: poster === null,
      frameLabel: 'Video poster not available',
      opensOnOriginalPage: true,
    };
  }

  if (record.media_type === 'other') {
    return {
      kind: 'other',
      src: null,
      alt: null,
      showPlayGlyph: false,
      chipLabel: 'Embed',
      showHdAction: false,
      isNeutral: true,
      frameLabel: 'Opens on the original page',
      opensOnOriginalPage: true,
    };
  }

  if (!imageUsable) {
    return {
      kind: status === 'missing' ? 'missing' : 'placeholder',
      src: null,
      alt: null,
      showPlayGlyph: false,
      chipLabel: null,
      showHdAction: false,
      isNeutral: true,
      frameLabel: 'Image not available',
      opensOnOriginalPage: true,
    };
  }

  return {
    kind: 'image',
    src: record.image_url ?? null,
    alt,
    showPlayGlyph: false,
    chipLabel: null,
    showHdAction: hdUsable,
    isNeutral: false,
    frameLabel: '',
    opensOnOriginalPage: false,
  };
}

/**
 * Presentation of a record whose image is only known to be broken at runtime.
 * Used when an `<img>` fails to load: the frame stays, the text changes.
 */
export const IMAGE_UNAVAILABLE_LABEL = 'Image unavailable';
