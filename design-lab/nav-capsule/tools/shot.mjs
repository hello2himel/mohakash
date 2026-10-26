#!/usr/bin/env node
// Minimal CDP screenshot helper — no npm dependencies.
// Usage: node shot.mjs <file.html> <out.png> [width] [height] [scale]
// Captures the full page (beyond the viewport) at the given CSS width.

import { spawn } from "node:child_process";
import { writeFileSync } from "node:fs";
import { resolve } from "node:path";

const CHROME = "/home/himel/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome";
const [file, out, widthArg, heightArg, scaleArg] = process.argv.slice(2);
if (!file || !out) {
  console.error("usage: node shot.mjs <file.html> <out.png> [width] [height] [scale]");
  process.exit(1);
}
const width = Number(widthArg ?? 1440);
const height = Number(heightArg ?? 1200);
const scale = Number(scaleArg ?? 2);
const port = 9300 + Math.floor(Math.random() * 500);
const url = "file://" + resolve(file);

const chrome = spawn(CHROME, [
  "--headless=new",
  "--disable-gpu",
  "--hide-scrollbars",
  "--no-first-run",
  "--no-default-browser-check",
  "--allow-file-access-from-files",
  `--remote-debugging-port=${port}`,
  `--window-size=${width},${height}`,
  "about:blank",
], { stdio: ["ignore", "ignore", "pipe"] });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitForDevtools() {
  for (let i = 0; i < 100; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/list`);
      const list = await res.json();
      const page = list.find((t) => t.type === "page");
      if (page) return page.webSocketDebuggerUrl;
    } catch {
      /* not up yet */
    }
    await sleep(120);
  }
  throw new Error("devtools did not start");
}

const wsUrl = await waitForDevtools();
const ws = new WebSocket(wsUrl);
await new Promise((res, rej) => {
  ws.addEventListener("open", res, { once: true });
  ws.addEventListener("error", rej, { once: true });
});

let nextId = 1;
const pending = new Map();
ws.addEventListener("message", (event) => {
  const msg = JSON.parse(event.data);
  if (msg.id && pending.has(msg.id)) {
    const { resolve: ok, reject } = pending.get(msg.id);
    pending.delete(msg.id);
    msg.error ? reject(new Error(JSON.stringify(msg.error))) : ok(msg.result);
  }
});
const send = (method, params = {}) =>
  new Promise((ok, reject) => {
    const id = nextId++;
    pending.set(id, { resolve: ok, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });

await send("Page.enable");
await send("Runtime.enable");
await send("Emulation.setDeviceMetricsOverride", {
  width,
  height,
  deviceScaleFactor: scale,
  mobile: false,
});
await send("Page.navigate", { url });
await sleep(700);
// Wait for fonts and images.
await send("Runtime.evaluate", {
  expression: `Promise.all([document.fonts.ready, ...[...document.images].map(i => i.decode().catch(() => {}))])`,
  awaitPromise: true,
});
await sleep(350);

const { cssContentSize } = await send("Page.getLayoutMetrics");
const shot = await send("Page.captureScreenshot", {
  format: "png",
  captureBeyondViewport: true,
  clip: { x: 0, y: 0, width: cssContentSize.width, height: cssContentSize.height, scale: 1 },
});
writeFileSync(out, Buffer.from(shot.data, "base64"));
console.log(`${out}  ${Math.round(cssContentSize.width)}x${Math.round(cssContentSize.height)} css @${scale}x`);

ws.close();
chrome.kill("SIGKILL");
process.exit(0);
