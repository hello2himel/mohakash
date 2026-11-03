import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

/**
 * Digital Asset Links for Android App Links.
 *
 * The file is served from `https://mohakash.netlify.app/.well-known/assetlinks.json`; Android
 * downloads it to verify that the app may handle the website's `https://` links. The package
 * name must stay in sync with `config/site.json` and the fingerprint must be a SHA-256
 * certificate fingerprint of a build users install.
 */
const ASSETLINKS_PATH = fileURLToPath(
  new URL('../public/.well-known/assetlinks.json', import.meta.url),
);
const SITE_CONFIG_PATH = fileURLToPath(new URL('../../config/site.json', import.meta.url));

type AssetLinks = Array<{
  relation?: string[];
  target?: {
    namespace?: string;
    package_name?: string;
    sha256_cert_fingerprints?: string[];
  };
}>;

const assetLinks = JSON.parse(readFileSync(ASSETLINKS_PATH, 'utf8')) as AssetLinks;
const siteConfig = JSON.parse(readFileSync(SITE_CONFIG_PATH, 'utf8')) as {
  android?: { package?: string };
};

const SHA256_FINGERPRINT = /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/i;

/**
 * Users install the app through several channels and Android only verifies the
 * App Links when the installed APK's signer appears in this list. Keep every
 * channel here:
 *
 * - GitHub releases (and the Play upload key) — the release keystore in
 *   `android/keystore/`, documented in `android/RELEASING.md`.
 * - Play App Signing — Google re-signs every bundle uploaded to Play, so the
 *   certificate Play installs never match the upload key. Copy the app signing
 *   key certificate from Play Console → Test and release → App integrity →
 *   App signing after a key rotation; list the old and new fingerprints
 *   together while any user still has a build signed with the old key.
 */
const RELEASE_FINGERPRINT =
  '04:41:88:79:E6:5E:35:34:6F:C4:06:F5:70:85:8E:EE:D3:EE:21:B0:E5:F8:B5:8E:2A:0F:F3:7F:0A:14:C0:AB';
const PLAY_APP_SIGNING_FINGERPRINT =
  '52:00:63:67:95:AC:42:71:24:EB:2A:10:97:D1:B9:F9:F0:B1:0C:23:A4:A2:76:FB:E5:08:6A:D2:CF:4A:61:3E';

describe('assetlinks.json', () => {
  it('is a non-empty list of App Links statements', () => {
    expect(Array.isArray(assetLinks)).toBe(true);
    expect(assetLinks.length).toBeGreaterThan(0);
  });

  it('delegates URL handling for the Android package from config/site.json', () => {
    const target = assetLinks[0]?.target;
    expect(assetLinks[0]?.relation).toContain('delegate_permission/common.handle_all_urls');
    expect(target?.namespace).toBe('android_app');
    expect(target?.package_name).toBe(siteConfig.android?.package);
  });

  it('lists at least one valid SHA-256 signing-certificate fingerprint', () => {
    const fingerprints = assetLinks[0]?.target?.sha256_cert_fingerprints ?? [];
    expect(fingerprints.length).toBeGreaterThan(0);
    for (const fingerprint of fingerprints) {
      expect(fingerprint).toMatch(SHA256_FINGERPRINT);
    }
  });

  it('trusts every certificate users can install from', () => {
    const fingerprints = assetLinks[0]?.target?.sha256_cert_fingerprints ?? [];
    // A fingerprint that goes missing here makes that channel's installs fail
    // Android App Links verification ("Failed domain checks" in Play Console).
    for (const expected of [PLAY_APP_SIGNING_FINGERPRINT, RELEASE_FINGERPRINT]) {
      expect(fingerprints).toContain(expected);
    }
    expect(new Set(fingerprints).size).toBe(fingerprints.length);
  });
});
