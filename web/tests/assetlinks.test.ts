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
});
