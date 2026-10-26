/* Mohakash — nav capsule design lab
   Scene builders: phone screens and dock variants, drawn at 1 CSS px == 1 dp.
   Loaded by explorations.html and mockup.html. */

const ICONS = {
  feed: "M12 18C8.68629 18 6 15.3137 6 12C6 8.68629 8.68629 6 12 6C15.3137 6 18 8.68629 18 12C18 15.3137 15.3137 18 12 18ZM12 16C14.2091 16 16 14.2091 16 12C16 9.79086 14.2091 8 12 8C9.79086 8 8 9.79086 8 12C8 14.2091 9.79086 16 12 16ZM11 1H13V4H11V1ZM11 20H13V23H11V20ZM3.51472 4.92893L4.92893 3.51472L7.05025 5.63604L5.63604 7.05025L3.51472 4.92893ZM16.9497 18.364L18.364 16.9497L20.4853 19.0711L19.0711 20.4853L16.9497 18.364ZM19.0711 3.51472L20.4853 4.92893L18.364 7.05025L16.9497 5.63604L19.0711 3.51472ZM5.63604 16.9497L7.05025 18.364L4.92893 20.4853L3.51472 19.0711L5.63604 16.9497ZM23 11V13H20V11H23ZM4 11V13H1V11H4Z",
  archive: "M3 10H2V4.00293C2 3.44903 2.45531 3 2.9918 3H21.0082C21.556 3 22 3.43788 22 4.00293V10H21V20.0015C21 20.553 20.5551 21 20.0066 21H3.9934C3.44476 21 3 20.5525 3 20.0015V10ZM19 10H5V19H19V10ZM4 5V8H20V5H4ZM9 12H15V14H9V12Z",
  rocket: "M4.99958,12.9999C4.99958,7.91198 7.90222,3.5636 11.9996,1.81799C16.0969,3.5636 18.9996,7.91198 18.9996,12.9999C18.9996,13.8229 18.9236,14.6264 18.779,15.4027L20.7194,17.2353C20.8845,17.3913 20.9238,17.6389 20.815,17.8383L18.3196,22.4133C18.1873,22.6557 17.8836,22.7451 17.6412,22.6128C17.5993,22.59 17.5608,22.5612 17.5271,22.5274L15.2925,20.2928C15.1049,20.1053 14.8506,19.9999 14.5854,19.9999H9.41379C9.14857,19.9999 8.89422,20.1053 8.70668,20.2928L6.47209,22.5274C6.27683,22.7227 5.96025,22.7227 5.76498,22.5274C5.73122,22.4937 5.70246,22.4552 5.67959,22.4133L3.18412,17.8383C3.07537,17.6389 3.11464,17.3913 3.27975,17.2353L5.22014,15.4027C5.07551,14.6264 4.99958,13.8229 4.99958,12.9999ZM6.47542,19.6957L7.29247,18.8786C7.85508,18.316 8.61814,17.9999 9.41379,17.9999H14.5854C15.381,17.9999 16.1441,18.316 16.7067,18.8786L17.5237,19.6957L18.5056,17.8955L17.4058,16.8568C16.9117,16.3901 16.6884,15.7045 16.8128,15.0364C16.9366,14.3722 16.9996,13.6911 16.9996,12.9999C16.9996,9.13037 15.0045,5.69965 11.9996,4.04033C8.99462,5.69965 6.99958,9.13037 6.99958,12.9999C6.99958,13.6911 7.06255,14.3722 7.18631,15.0364C7.31078,15.7045 7.08746,16.3901 6.59338,16.8568L5.49353,17.8955L6.47542,19.6957ZM11.9996,12.9999C10.895,12.9999 9.99958,12.1045 9.99958,10.9999C9.99958,9.89537 10.895,8.99994 11.9996,8.99994C13.1041,8.99994 13.9996,9.89537 13.9996,10.9999C13.9996,12.1045 13.1041,12.9999 11.9996,12.9999Z",
  settings: "M3.33946 17.0002C2.90721 16.2515 2.58277 15.4702 2.36133 14.6741C3.3338 14.1779 3.99972 13.1668 3.99972 12.0002C3.99972 10.8345 3.3348 9.824 2.36353 9.32741C2.81025 7.71651 3.65857 6.21627 4.86474 4.99001C5.7807 5.58416 6.98935 5.65534 7.99972 5.072C9.01009 4.48866 9.55277 3.40635 9.4962 2.31604C11.1613 1.8846 12.8847 1.90004 14.5031 2.31862C14.4475 3.40806 14.9901 4.48912 15.9997 5.072C17.0101 5.65532 18.2187 5.58416 19.1346 4.99007C19.7133 5.57986 20.2277 6.25151 20.66 7.00021C21.0922 7.7489 21.4167 8.53025 21.6381 9.32628C20.6656 9.82247 19.9997 10.8336 19.9997 12.0002C19.9997 13.166 20.6646 14.1764 21.6359 14.673C21.1892 16.2839 20.3409 17.7841 19.1347 19.0104C18.2187 18.4163 17.0101 18.3451 15.9997 18.9284C14.9893 19.5117 14.4467 20.5941 14.5032 21.6844C12.8382 22.1158 11.1148 22.1004 9.49633 21.6818C9.55191 20.5923 9.00929 19.5113 7.99972 18.9284C6.98938 18.3451 5.78079 18.4162 4.86484 19.0103C4.28617 18.4205 3.77172 17.7489 3.33946 17.0002ZM8.99972 17.1964C10.0911 17.8265 10.8749 18.8227 11.2503 19.9659C11.7486 20.0133 12.2502 20.014 12.7486 19.9675C13.1238 18.8237 13.9078 17.8268 14.9997 17.1964C16.0916 16.5659 17.347 16.3855 18.5252 16.6324C18.8146 16.224 19.0648 15.7892 19.2729 15.334C18.4706 14.4373 17.9997 13.2604 17.9997 12.0002C17.9997 10.74 18.4706 9.5632 19.2729 8.6665C19.1688 8.4405 19.0538 8.21822 18.9279 8.00021C18.802 7.78219 18.667 7.57148 18.5233 7.36842C17.3457 7.61476 16.0911 7.43414 14.9997 6.80405C13.9083 6.17395 13.1246 5.17768 12.7491 4.03455C12.2509 3.98714 11.7492 3.98646 11.2509 4.03292C10.8756 5.17671 10.0916 6.17364 8.99972 6.80405C7.9078 7.43447 6.65245 7.61494 5.47428 7.36803C5.18485 7.77641 4.93463 8.21117 4.72656 8.66637C5.52881 9.56311 5.99972 10.74 5.99972 12.0002C5.99972 13.2604 5.52883 14.4372 4.72656 15.3339C4.83067 15.5599 4.94564 15.7822 5.07152 16.0002C5.19739 16.2182 5.3324 16.4289 5.47612 16.632C6.65377 16.3857 7.90838 16.5663 8.99972 17.1964ZM11.9997 15.0002C10.3429 15.0002 8.99972 13.6571 8.99972 12.0002C8.99972 10.3434 10.3429 9.00021 11.9997 9.00021C13.6566 9.00021 14.9997 10.3434 14.9997 12.0002C14.9997 13.6571 13.6566 15.0002 11.9997 15.0002ZM11.9997 13.0002C12.552 13.0002 12.9997 12.5525 12.9997 12.0002C12.9997 11.4479 12.552 11.0002 11.9997 11.0002C11.4474 11.0002 10.9997 11.4479 10.9997 12.0002C10.9997 12.5525 11.4474 13.0002 11.9997 13.0002Z",
  search: "M10.5 4C6.91015 4 4 6.91015 4 10.5C4 14.0899 6.91015 17 10.5 17C14.0899 17 17 14.0899 17 10.5C17 6.91015 14.0899 4 10.5 4ZM10.5 6C12.9853 6 15 8.01472 15 10.5C15 12.9853 12.9853 15 10.5 15C8.01472 15 6 12.9853 6 10.5C6 8.01472 8.01472 6 10.5 6ZM15.7929 14.7929L21.4142 20.4142L20 21.8284L14.3787 16.2071L15.7929 14.7929Z",
  heart: "M12.001 4.52853C14.35 2.42 17.98 2.49 20.2426 4.75736C22.5053 7.02472 22.583 10.637 20.4786 12.993L11.9999 21.485L3.52138 12.993C1.41705 10.637 1.49571 7.01901 3.75736 4.75736C6.02157 2.49315 9.64519 2.41687 12.001 4.52853ZM18.827 6.1701C17.3279 4.66794 14.9076 4.60701 13.337 6.01687L12.0019 7.21524L10.6661 6.01781C9.09098 4.60597 6.67506 4.66808 5.17157 6.17157C3.68183 7.66131 3.60704 10.0473 4.97993 11.6232L11.9999 18.6543L19.0201 11.6232C20.3935 10.0467 20.319 7.66525 18.827 6.1701Z",
  heartFilled: "M12.001 4.52853C14.35 2.42 17.98 2.49 20.2426 4.75736C22.5053 7.02472 22.583 10.637 20.4786 12.993L11.9999 21.485L3.52138 12.993C1.41705 10.637 1.49571 7.01901 3.75736 4.75736C6.02157 2.49315 9.64519 2.41687 12.001 4.52853Z",
  download: "M13 10H18L12 16L6 10H11V3H13V10ZM4 19H20V12H22V20C22 20.5523 21.5523 21 21 21H3C2.44772 21 2 20.5523 2 20V12H4V19Z",
  share: "M13.1202 17.0228L8.92129 14.7324C8.19135 15.5125 7.15261 16 6 16C3.79086 16 2 14.2091 2 12C2 9.79086 3.79086 8 6 8C7.15255 8 8.19125 8.48746 8.92118 9.26746L13.1202 6.97713C13.0417 6.66441 13 6.33707 13 6C13 3.79086 14.7909 2 17 2C19.2091 2 21 3.79086 21 6C21 8.20914 19.2091 10 17 10C15.8474 10 14.8087 9.51251 14.0787 8.73246L9.87977 11.0228C9.9583 11.3355 10 11.6629 10 12C10 12.3371 9.95831 12.6644 9.87981 12.9771L14.0788 15.2675C14.8087 14.4875 15.8474 14 17 14C19.2091 14 21 15.7909 21 18C21 20.2091 19.2091 22 17 22C14.7909 22 13 20.2091 13 18C13 17.6629 13.0417 17.3355 13.1202 17.0228ZM6 14C7.10457 14 8 13.1046 8 12C8 10.8954 7.10457 10 6 10C4.89543 10 4 10.8954 4 12C4 13.1046 4.89543 14 6 14ZM17 8C18.1046 8 19 7.10457 19 6C19 4.89543 18.1046 4 17 4C15.8954 4 15 4.89543 15 6C15 7.10457 15.8954 8 17 8ZM17 20C18.1046 20 19 19.1046 19 18C19 16.8954 18.1046 16 17 16C15.8954 16 15 16.8954 15 18C15 19.1046 15.8954 20 17 20Z",
  play: "M8 5.5v13l11-6.5z",
};

/** An SVG glyph on a 24-unit grid. */
function icon(name, size = 20, opts = {}) {
  const fill = opts.fill ?? "currentColor";
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" aria-hidden="true"><path fill="${fill}" d="${ICONS[name]}"/></svg>`;
}

/** A pill outline with an optional launch cradle cut into the top edge. */
function capsulePath(w, h, notch) {
  const x0 = 0.5, y0 = 0.5, x1 = w - 0.5, y1 = h - 0.5;
  const r = (h - 1) / 2;
  let d = `M ${x0 + r} ${y0}`;
  if (notch) {
    const cx = w / 2;
    const lip = notch.opening / 2;
    const floor = notch.floor / 2;
    const dep = notch.depth;
    d += ` L ${cx - lip} ${y0}`;
    d += ` C ${cx - lip} ${y0 + dep * 0.52} ${cx - floor - 3.5} ${y0 + dep} ${cx - floor} ${y0 + dep}`;
    d += ` L ${cx + floor} ${y0 + dep}`;
    d += ` C ${cx + floor + 3.5} ${y0 + dep} ${cx + lip} ${y0 + dep * 0.52} ${cx + lip} ${y0}`;
  }
  d += ` L ${x1 - r} ${y0}`;
  d += ` A ${r} ${r} 0 0 1 ${x1} ${y0 + r}`;
  d += ` L ${x1} ${y1 - r}`;
  d += ` A ${r} ${r} 0 0 1 ${x1 - r} ${y1}`;
  d += ` L ${x0 + r} ${y1}`;
  d += ` A ${r} ${r} 0 0 1 ${x0} ${y1 - r}`;
  d += ` L ${x0} ${y0 + r}`;
  d += ` A ${r} ${r} 0 0 1 ${x0 + r} ${y0}`;
  return d + " Z";
}

/**
 * The floating dock.
 * variant: "cradle" (notch) | "knob" (control, no notch) | "split" | "key"
 * state:   "idle" | "pressed" | "launching"
 * compact: the landscape build — 44 dp plate, 36 dp key, 11 sp labels, no notch.
 */
function renderDock({
  variant = "cradle", active = "feed", state = "idle", labels = true,
  fontScale = 1, compact = false,
} = {}) {
  const isSplit = variant === "split";
  const isKey = variant === "key";
  const hasNotch = variant === "cradle" && !compact;

  const baseCapH = compact ? 44 : 54;
  const capH = baseCapH + (compact ? 0 : Math.max(0, (fontScale - 1) * 14));
  const capGap = compact ? 8 : isSplit ? 14 : 8;
  const dockH = (compact ? 56 : isSplit ? 76 : 72) + (capH - baseCapH);
  const capTop = dockH - capGap - capH;

  const labelSize = (compact ? 11 : 12) * fontScale;
  const baseTabW = isSplit ? 100 : isKey ? 80 : 84;
  const tabW = !labels ? 56 : Math.max(baseTabW, Math.ceil(labelSize * 3.9));
  const slotW = isSplit ? 64 : 64;
  const tabPad = isSplit ? 0 : 10;
  const dockW = isSplit ? 264 : isKey ? 260 : !labels ? 188 : tabPad * 2 + tabW * 2 + slotW;

  const wellH = Math.max(32, capH - 14);
  const wellW = isSplit ? 72 : Math.min(72, tabW - 12);
  const feedTabLeft = tabPad;
  const archiveTabLeft = feedTabLeft + tabW + (isSplit ? 64 : isKey ? 0 : slotW);
  const indLeft = feedTabLeft + (tabW - wellW) / 2;
  const indShift = archiveTabLeft - feedTabLeft;

  const discSize = compact ? 36 : isKey ? 36 : 44;
  const notch = hasNotch ? { opening: 58, floor: 42, depth: 12 } : null;
  const rocketTop = compact ? (dockH - 48) / 2 : isKey ? capTop + 3 : 0;

  const capsule = isSplit
    ? `<div class="island left"></div><div class="island right"></div><div class="rail-stem"></div>`
    : `<div class="capsule">
         <svg viewBox="0 0 ${dockW} ${capH}" preserveAspectRatio="none">
           <path class="fill" d="${capsulePath(dockW, capH, notch)}"/>
           <path class="line" d="${capsulePath(dockW, capH, notch)}"/>
         </svg>
       </div>`;

  const vars = [
    `--dock-w:${dockW}px`,
    `--dock-h:${dockH}px`,
    `--cap-h:${capH}px`,
    `--cap-gap:${capGap}px`,
    `--cap-top:${capTop}px`,
    `--tab-w:${tabW}px`,
    `--slot-w:${slotW}px`,
    `--label-size:${labelSize}px`,
    `--label-lh:${16 * fontScale}px`,
    `--ind-w:${wellW}px`,
    `--ind-h:${wellH}px`,
    `--ind-left:${indLeft}px`,
    `--ind-shift:${indShift}px`,
    `--ind-top:${capTop + (capH - wellH) / 2}px`,
    `--disc:${discSize}px`,
    `--cap-fill:#151922`,
    `--cap-line:#323849`,
    `--disc-fill:#1B2030`,
    `--disc-line:#3A4257`,
    `--well-fill:#0B0D12`,
    `--well-line:#05060A`,
    `--well-shadow:inset 0 1px 0 rgba(0,0,0,.65), inset 0 0 0 1px #05060A`,
  ].join(";");

  return `
  <div class="dock" data-variant="${variant}" data-active="${active}" data-state="${state}" data-labels="${labels ? "on" : "off"}" style="${vars}">
    ${capsule}
    <div class="indicator"></div>
    ${isKey ? `<div class="keyhair"></div>` : ""}
    <div class="tabs">
      <div class="tab" data-dest="feed">${icon("feed")}<span class="lbl">Feed</span></div>
      <div class="tab" data-dest="archive">${icon("archive")}<span class="lbl">Archive</span></div>
    </div>
    <div class="rocketbtn" style="top:${rocketTop}px">
      <span class="halo"></span>
      <span class="disc">${icon("rocket")}</span>
    </div>
  </div>`;
}

/* ---------- atmospheric background ---------- */

let filterSeq = 0;

function nebula() {
  const id = `dust${++filterSeq}`;
  return `
  <div class="space">
    <div class="base"></div>
    <div class="core"></div>
    <div class="band"></div>
    <svg class="dust" viewBox="0 0 360 800" preserveAspectRatio="xMidYMid slice">
      <defs>
        <filter id="${id}a" x="-10%" y="-10%" width="120%" height="120%">
          <feTurbulence type="fractalNoise" baseFrequency="0.0035 0.0028" numOctaves="5" seed="9" result="n"/>
          <feColorMatrix in="n" type="matrix" values="
            0 0 0 0 0.38
            0 0 0 0 0.45
            0 0 0 0 0.68
            0.9 1.1 0.45 0 -0.22"/>
          <feGaussianBlur stdDeviation="2.4"/>
        </filter>
        <filter id="${id}b" x="-10%" y="-10%" width="120%" height="120%">
          <feTurbulence type="fractalNoise" baseFrequency="0.006 0.005" numOctaves="4" seed="21" result="m"/>
          <feColorMatrix in="m" type="matrix" values="
            0 0 0 0 0.62
            0 0 0 0 0.42
            0 0 0 0 0.62
            1.1 0.7 0.5 0 -0.3"/>
          <feGaussianBlur stdDeviation="1.6"/>
        </filter>
      </defs>
      <rect width="360" height="800" filter="url(#${id}a)" opacity="0.55"/>
      <rect width="360" height="800" filter="url(#${id}b)" opacity="0.34"/>
    </svg>
    <div class="stars">${stars()}</div>
  </div>`;
}

function stars() {
  // Deterministic so every screenshot is identical.
  let seed = 20261023;
  const rand = () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296;
    return seed / 4294967296;
  };
  let out = "";
  for (let i = 0; i < 130; i++) {
    const x = rand() * 100;
    const y = rand() * 100;
    const s = 0.6 + rand() * 1.3;
    const o = 0.15 + rand() * 0.65;
    const warm = rand() > 0.82;
    out += `<i style="left:${x.toFixed(2)}%;top:${y.toFixed(2)}%;width:${s.toFixed(2)}px;height:${s.toFixed(2)}px;opacity:${o.toFixed(2)};background:${warm ? "#ffe3c4" : "#dfe7ff"}"></i>`;
  }
  return out;
}

/* ---------- phone chrome ---------- */

function statusBar() {
  return `
  <div class="status">
    <span>9:41</span>
    <span class="glyphs">
      <svg width="13" height="10" viewBox="0 0 13 10" fill="currentColor"><rect x="0" y="6" width="2.2" height="4" rx="0.6"/><rect x="3.4" y="4.2" width="2.2" height="5.8" rx="0.6"/><rect x="6.8" y="2.4" width="2.2" height="7.6" rx="0.6"/><rect x="10.2" y="0.6" width="2.2" height="9.4" rx="0.6"/></svg>
      <svg width="13" height="10" viewBox="0 0 14 11" fill="none" stroke="currentColor" stroke-width="1.25" stroke-linecap="round"><path d="M1 3.4a9 9 0 0 1 12 0"/><path d="M3.4 6a5.5 5.5 0 0 1 7.2 0"/><circle cx="7" cy="9.2" r="0.9" fill="currentColor" stroke="none"/></svg>
      <svg width="21" height="10" viewBox="0 0 21 10" fill="none"><rect x="0.5" y="0.5" width="18" height="9" rx="2.4" stroke="currentColor" opacity="0.5"/><rect x="2" y="2" width="13.5" height="6" rx="1.4" fill="currentColor"/><path d="M20 3.6v2.8" stroke="currentColor" opacity="0.5" stroke-width="1.4" stroke-linecap="round"/></svg>
    </span>
  </div>`;
}

function glassButton(name, label) {
  return `<div class="glassbtn" title="${label}">${icon(name, 22)}</div>`;
}

/* ---------- screens ---------- */

const ENTRIES = [
  { date: "23 October 2026", title: "The Veil of Cygnus", credit: "Image credit: NASA, ESA" },
  { date: "22 October 2026", title: "A River of Dust in Orion", credit: "Image credit: R. Colombari" },
  { date: "21 October 2026", title: "Prominences at the Limb", credit: "Image credit: M. Sharma" },
  { date: "20 October 2026", title: "The Quiet Centre of M87", credit: "Image credit: EHT Collaboration" },
  { date: "19 October 2026", title: "Lake of Stars, Atacama", credit: "Image credit: Y. Beletsky" },
  { date: "18 October 2026", title: "The Wizard Nebula", credit: "Image credit: T. A. Rector" },
];

function feedScene(w, dock = {}) {
  const inset = dockInset(dock);
  const nav = dock.threeButton ? 48 : 24;
  return `
  <div class="screen">
    <div class="content" style="bottom:${inset}px">
      <div class="photo" style="--crop:${dock.crop || "52% 46%"}"><img src="assets/img/return.jpg" alt="" /></div>
      <div class="scrim"></div>
      <div class="topchrome">
        ${glassButton("search", "Search")}
        <div class="spacer"></div>
        ${glassButton("settings", "Settings")}
      </div>
      <div class="reading">
        <div class="date-rail"><span class="bar"></span><span class="label">23 OCTOBER 2026</span></div>
        <div class="copy">
          <div class="credit">Image credit: ESA / Hubble, NASA, Z. Levay</div>
          <div class="title">The Veil of Cygnus</div>
          <div class="teaser">A wide-field view of the Veil Nebula, the expanding remains of a star that exploded thousands of years ago.</div>
          <div class="actions"><span class="pill">Set wallpaper</span><span class="textaction">Learn more</span></div>
        </div>
      </div>
      <div class="rail">
        <div class="item"><div class="circle">${icon("heart", 23)}</div><div class="lbl">Favorite</div></div>
        <div class="item"><div class="circle">${icon("download", 23)}</div><div class="lbl">Download</div></div>
        <div class="item"><div class="circle">${icon("share", 23)}</div><div class="lbl">Share</div></div>
      </div>
    </div>
    ${statusBar()}
    <div class="dockwrap" style="height:${inset}px;padding-bottom:${nav}px">${renderDock({ active: "feed", ...dock })}</div>
    ${dock.threeButton ? sysNav() : '<div class="gesture"></div>'}
  </div>`;
}

function archiveScene(w, dock = {}) {
  const inset = dockInset(dock);
  const nav = dock.threeButton ? 48 : 24;
  const thumbs = ["lift.jpg", "pillars.jpg", "carina.jpg", "fireworks.jpg", "return.jpg"];
  const rows = ENTRIES.map(
    (e, i) => `
    <div class="arow">
      <div class="thumb"><img src="assets/img/${thumbs[i % thumbs.length]}" alt="" /></div>
      <div class="txt"><div class="date">${e.date}</div><div class="ttl">${e.title}</div></div>
    </div>`
  ).join("");
  return `
  <div class="screen">
    <div class="content" style="bottom:${inset}px;background:var(--bg)">
      <div class="topbar"><div class="title">Archive</div><div class="iconbtn48">${icon("settings", 24)}</div></div>
      <div class="segmented"><span class="on">Archive</span><span>Saved</span></div>
      <div class="searchfield">${icon("search", 20)}<span>Search titles</span></div>
      <div class="chips"><span class="chip on">2026</span><span class="chip">2025</span><span class="chip">2024</span><span class="chip">2023</span><span class="chip">Filters</span></div>
      <div class="archive-list">${rows}</div>
    </div>
    ${statusBar()}
    <div class="dockwrap" style="height:${inset}px;padding-bottom:${nav}px">${renderDock({ active: "archive", ...dock })}</div>
    ${dock.threeButton ? sysNav() : '<div class="gesture"></div>'}
  </div>`;
}

/** The scaffold's bottom slot: the dock's own height plus the navigation-bar inset. */
function dockInset({ variant = "cradle", fontScale = 1, compact = false, threeButton = false } = {}) {
  const base = compact ? 56 : variant === "split" ? 76 : 72;
  const grow = compact ? 0 : Math.max(0, (fontScale - 1) * 14);
  return base + grow + (threeButton ? 48 : 24);
}

/** The three-button system navigation bar, drawn only for the inset stress test. */
function sysNav() {
  return `<div class="sysnav">
    <span><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 5h8l-4 5z"/></svg></span>
    <span><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="7"/></svg></span>
    <span><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="16" rx="2"/></svg></span>
  </div>`;
}

/** Mount a device into a container element. */
function mountDevice(el, { w = 360, screen = "feed", dock = {} } = {}) {
  el.classList.add("device");
  el.style.width = w + "px";
  el.innerHTML = screen === "feed" ? feedScene(w, dock) : archiveScene(w, dock);
}
