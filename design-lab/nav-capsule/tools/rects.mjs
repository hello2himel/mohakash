#!/usr/bin/env node
// Prints bounding rects for selectors — used to crop screenshots precisely.
// Usage: node rects.mjs <file.html> [wide=1440] selector...
import { spawn } from "node:child_process";
import { resolve } from "node:path";

const CHROME = "/home/himel/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome";
const [file, widthArg, ...selectors] = process.argv.slice(2);
const width = Number(widthArg ?? 1440);
const height = 1200;
const port = 9800 + Math.floor(Math.random() * 400);

const chrome = spawn(CHROME, [
  "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
  "--allow-file-access-from-files", `--remote-debugging-port=${port}`,
  `--window-size=${width},${height}`, "about:blank",
], { stdio: ["ignore", "ignore", "pipe"] });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let wsUrl;
for (let i = 0; i < 80 && !wsUrl; i++) {
  try {
    const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    wsUrl = list.find((t) => t.type === "page")?.webSocketDebuggerUrl;
  } catch { /* wait */ }
  if (!wsUrl) await sleep(120);
}
const ws = new WebSocket(wsUrl);
await new Promise((r, j) => { ws.addEventListener("open", r, { once: true }); ws.addEventListener("error", j, { once: true }); });
let id = 1;
const pending = new Map();
ws.addEventListener("message", (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
});
const send = (method, params = {}) => new Promise((ok) => { const i = id++; pending.set(i, ok); ws.send(JSON.stringify({ id: i, method, params })); });

await send("Page.enable");
await send("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 2, mobile: false });
await send("Page.navigate", { url: "file://" + resolve(file) });
await sleep(900);
await send("Runtime.evaluate", { expression: `document.fonts.ready`, awaitPromise: true });
const expr = `JSON.stringify([${selectors.map((s) => `(() => { const el = document.querySelector(${JSON.stringify(s)}); if (!el) return { s: ${JSON.stringify(s)}, missing: true }; const r = el.getBoundingClientRect(); return { s: ${JSON.stringify(s)}, x: r.x, y: r.y + window.scrollY, w: r.width, h: r.height }; })()`).join(",")}])`;
const out = await send("Runtime.evaluate", { expression: expr, returnByValue: true });
console.log(out.result.value);
ws.close();
chrome.kill("SIGKILL");
process.exit(0);
