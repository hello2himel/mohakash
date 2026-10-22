import { defineConfig } from 'astro/config';

/**
 * Mohakash website.
 *
 * The site is fully static: every page is a shell that reads its data from the
 * public JSON API at runtime (same-origin `/api/apod/v1/...`, proxied by
 * Netlify to GitHub Pages). No APOD data is embedded at build time.
 */
export default defineConfig({
  site: 'https://mohakash.netlify.app',
  output: 'static',
  build: {
    // `/archive/` and `/archive/entry/` are served as directory indexes.
    format: 'directory',
  },
  devToolbar: {
    enabled: false,
  },
  vite: {
    build: {
      assetsDir: '_assets',
    },
  },
});
