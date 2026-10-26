// Regenerates the review PNGs from mockup.html.
// Requires Playwright with Chromium; run from this directory:
//   NODE_PATH=... node render.js
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch();
  const errors = [];

  const page = await browser.newPage({ viewport: { width: 1240, height: 1200 }, deviceScaleFactor: 2 });
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('file://' + __dirname + '/mockup.html', { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);

  const devices = await page.$$('.device');
  await devices[0].screenshot({ path: 'mockup-360.png' });
  await devices[1].screenshot({ path: 'mockup-411.png' });
  await page.locator('.anatomy-wrap').screenshot({ path: 'mockup-anatomy.png' });
  await page.locator('.states').screenshot({ path: 'mockup-states.png' });

  const full = await browser.newPage({ viewport: { width: 1240, height: 1200 }, deviceScaleFactor: 1.5 });
  full.on('pageerror', e => errors.push(e.message));
  await full.goto('file://' + __dirname + '/mockup.html', { waitUntil: 'load' });
  await full.evaluate(() => document.fonts.ready);
  await full.waitForTimeout(400);
  await full.screenshot({ path: 'mockup.png', fullPage: true });

  const metrics = await full.evaluate(() => {
    const r = el => { const b = el.getBoundingClientRect(); return [Math.round(b.width), Math.round(b.height)]; };
    return {
      screens: [...document.querySelectorAll('.screen')].map(r),
      docks: [...document.querySelectorAll('[data-dock] > .dock')].map(r),
      body: [document.body.scrollWidth, document.body.scrollHeight],
    };
  });
  console.log(JSON.stringify({ errors, metrics }, null, 2));
  await browser.close();
})();
