import { describe, expect, it } from 'vitest';

import { mediaPresentation, neutralAlt } from '../src/lib/media';
import type { RecordLike } from '../src/lib/types';

function record(overrides: Partial<RecordLike> = {}): RecordLike {
  return {
    date: '2026-10-22',
    title: 'The First Green Star',
    media_type: 'image',
    image_url: 'https://example.test/image.jpg',
    hd_image_url: 'https://example.test/hd.jpg',
    thumbnail_url: 'https://example.test/thumb.jpg',
    image_status: 'ok',
    alt_text: 'A greenish star in a dark field.',
    copyright: 'Someone',
    credit: 'Someone',
    ...overrides,
  };
}

describe('mediaPresentation', () => {
  it('shows a normal image with the HD action', () => {
    const presentation = mediaPresentation(record());
    expect(presentation.kind).toBe('image');
    expect(presentation.src).toBe('https://example.test/image.jpg');
    expect(presentation.alt).toBe('A greenish star in a dark field.');
    expect(presentation.showHdAction).toBe(true);
    expect(presentation.isNeutral).toBe(false);
    expect(presentation.showPlayGlyph).toBe(false);
    expect(presentation.chipLabel).toBeNull();
  });

  it('omits the HD action when there is no HD image', () => {
    const presentation = mediaPresentation(record({ hd_image_url: null }));
    expect(presentation.kind).toBe('image');
    expect(presentation.showHdAction).toBe(false);
  });

  it('uses a neutral description when the source has no alt text', () => {
    const presentation = mediaPresentation(record({ alt_text: null }));
    expect(presentation.alt).toBe(neutralAlt(record()));
    expect(presentation.alt).toContain('2026-10-22');
  });

  it('treats a placeholder as a neutral frame without the HD action', () => {
    const presentation = mediaPresentation(record({ image_status: 'placeholder', hd_image_url: null }));
    expect(presentation.kind).toBe('placeholder');
    expect(presentation.src).toBeNull();
    expect(presentation.isNeutral).toBe(true);
    expect(presentation.showHdAction).toBe(false);
    expect(presentation.frameLabel).toBe('Image not available');
  });

  it('treats a missing image as a neutral frame without the HD action', () => {
    const presentation = mediaPresentation(record({ image_status: 'missing', hd_image_url: null }));
    expect(presentation.kind).toBe('missing');
    expect(presentation.src).toBeNull();
    expect(presentation.isNeutral).toBe(true);
    expect(presentation.showHdAction).toBe(false);
  });

  it('treats a null image url as a neutral frame', () => {
    const presentation = mediaPresentation(record({ image_url: null, hd_image_url: null }));
    expect(presentation.kind).toBe('placeholder');
    expect(presentation.src).toBeNull();
    expect(presentation.showHdAction).toBe(false);
  });

  it('shows the poster frame of a video with a play glyph and a Video chip', () => {
    const presentation = mediaPresentation(record({ media_type: 'video' }));
    expect(presentation.kind).toBe('video');
    expect(presentation.src).toBe('https://example.test/thumb.jpg');
    expect(presentation.showPlayGlyph).toBe(true);
    expect(presentation.chipLabel).toBe('Video');
    expect(presentation.opensOnOriginalPage).toBe(true);
    expect(presentation.showHdAction).toBe(false);
  });

  it('falls back to the image url when a video has no thumbnail', () => {
    const presentation = mediaPresentation(record({ media_type: 'video', thumbnail_url: null }));
    expect(presentation.src).toBe('https://example.test/image.jpg');
  });

  it('shows a neutral frame with a play glyph when a video has no poster', () => {
    const presentation = mediaPresentation(
      record({ media_type: 'video', thumbnail_url: null, image_url: null }),
    );
    expect(presentation.kind).toBe('video');
    expect(presentation.src).toBeNull();
    expect(presentation.isNeutral).toBe(true);
    expect(presentation.showPlayGlyph).toBe(true);
    expect(presentation.chipLabel).toBe('Video');
  });

  it('shows a neutral frame for an embedded entry', () => {
    const presentation = mediaPresentation(record({ media_type: 'other' }));
    expect(presentation.kind).toBe('other');
    expect(presentation.src).toBeNull();
    expect(presentation.isNeutral).toBe(true);
    expect(presentation.opensOnOriginalPage).toBe(true);
    expect(presentation.chipLabel).toBe('Embed');
  });
});
