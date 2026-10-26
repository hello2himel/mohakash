import { PLATES } from './plates.mjs';

const params = new URLSearchParams(location.search);
const plate = PLATES.find((p) => p.id === params.get('plate')) || PLATES[0];

/* Deterministic PRNG so a rebuild reproduces the same sky. */
function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function starfield(seed) {
  const rnd = mulberry32(seed);
  const stars = [];

  /* half the field is uniform, half gathers along a quiet galactic band */
  for (let i = 0; i < 260; i += 1) {
    const band = i >= 300;
    let x = rnd() * 1080;
    let y = rnd() * 1920;
    if (band) {
      const g = (rnd() + rnd() + rnd() - 1.5) / 1.5;
      y = 1230 - 0.5 * x + g * 190;
      if (y < 60 || y > 1900) y = rnd() * 1920;
    }
    const power = rnd();
    const r = 0.45 + power * power * (band ? 1.2 : 1.6);
    const opacity = (band ? 0.1 : 0.07) + rnd() * (band ? 0.42 : 0.55);
    const tint = rnd() > 0.86 ? '#C3D1FF' : '#EEF1FF';
    stars.push(`<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${r.toFixed(2)}" fill="${tint}" opacity="${opacity.toFixed(3)}"/>`);
    if (power > 0.94) {
      stars.push(`<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${(r * 5).toFixed(2)}" fill="#8FB0FF" opacity="0.06"/>`);
    }
  }

  /* three quiet diffraction glints */
  for (let g = 0; g < 1; g += 1) {
    const x = 120 + rnd() * 840;
    const y = 240 + rnd() * 1050;
    const len = 16 + rnd() * 26;
    const o = 0.14 + rnd() * 0.12;
    stars.push(
      `<g opacity="${o.toFixed(2)}" stroke="#DCE5FF" stroke-width="0.8">` +
        `<line x1="${(x - len).toFixed(1)}" y1="${y.toFixed(1)}" x2="${(x + len).toFixed(1)}" y2="${y.toFixed(1)}"/>` +
        `<line x1="${x.toFixed(1)}" y1="${(y - len * 0.55).toFixed(1)}" x2="${x.toFixed(1)}" y2="${(y + len * 0.55).toFixed(1)}"/>` +
        `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="1.5" fill="#F4F6FF" stroke="none"/>` +
      `</g>`
    );
  }

  return `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920" viewBox="0 0 1080 1920">${stars.join('')}</svg>`;
}

function orbits() {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920" viewBox="0 0 1080 1920">
    <defs>
      <linearGradient id="fade" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0.18" stop-color="#fff" stop-opacity="0"/>
        <stop offset="0.46" stop-color="#fff" stop-opacity="0.85"/>
        <stop offset="1" stop-color="#fff" stop-opacity="0.25"/>
      </linearGradient>
      <mask id="m"><rect width="1080" height="1920" fill="url(#fade)"/></mask>
    </defs>
    <g mask="url(#m)" fill="none">
      <ellipse cx="540" cy="1610" rx="930" ry="610" transform="rotate(-11 540 1610)" stroke="#8FB0FF" stroke-opacity="0.115" stroke-width="1"/>
      <ellipse cx="540" cy="1610" rx="760" ry="470" transform="rotate(7 540 1610)" stroke="#8FB0FF" stroke-opacity="0.06" stroke-width="1"/>
      <circle cx="1030" cy="1118" r="3.2" fill="#8FB0FF" fill-opacity="0.5"/>
    </g>
    <path d="M 744 148 C 862 224 944 386 1012 574" stroke="#8FB0FF" stroke-opacity="0.16" stroke-width="1" fill="none"/>
    <circle cx="1012" cy="574" r="2.4" fill="#8FB0FF" fill-opacity="0.66"/>
    <circle cx="1012" cy="574" r="10" stroke="#8FB0FF" stroke-opacity="0.13" stroke-width="1" fill="none"/>
  </svg>`;
}

function headlineMarkup(lines) {
  return lines
    .map((line, i) => {
      const last = i === lines.length - 1;
      if (last && line.endsWith('.')) {
        return `<span class="line">${line.slice(0, -1)}<span class="stop">.</span></span>`;
      }
      return `<span class="line">${line}</span>`;
    })
    .join('');
}

function screenImg(name, alt) {
  return `<img src="assets/screenshots/${name}" alt="${alt}" width="1080" height="2400" draggable="false">`;
}

function phoneMarkup() {
  return `<div class="device" data-device>
    <div class="device__aura"></div>
    <div class="frame" data-frame>
      <div class="screen" data-screen>${screenImg(plate.screen, 'Mohakash app screenshot')}</div>
    </div>
  </div>`;
}

function showcaseMarkup() {
  const phones = plate.phones.map((p, i) => `
    <div class="show-phone ${i === 1 ? 'show-phone--hero' : ''}"
         ${i === 1 ? 'data-device' : ''}
         style="--x:${p.x}px;--y:${p.y}px;--w:${p.w}px;--r:${p.r}deg;--z:${p.z}">
      <div class="frame" ${i === 1 ? 'data-frame' : ''}>
        <div class="screen" ${i === 1 ? 'data-screen' : ''}>${screenImg(p.screen, 'Mohakash app screenshot')}</div>
      </div>
    </div>`).join('');
  return `<div class="showcase">${phones}</div>`;
}

function build() {
  const sky = plate.bg;
  document.body.innerHTML = `
  <div class="canvas" data-canvas>
    <div class="sky" style="--sky-opacity:${sky.opacity}">
      <div class="sky__img" style="
        background-image:url(assets/screenshots/${sky.src});
        background-size:${(1080 * sky.scale).toFixed(2)}px ${(2400 * sky.scale).toFixed(2)}px;
        background-position:${sky.x}px ${sky.y}px;"></div>
      <div class="sky__crest" style="
        background-image:url(assets/screenshots/${sky.src});
        background-size:1620px 3600px;
        background-position:-50px -1150px;"></div>
      <div class="sky__pool"></div>
      <div class="sky__grade"></div>
    </div>

    <div class="stars">${starfield(plate.seed)}</div>
    <div class="vignette"></div>
    <div class="grain"></div>

    <h1 class="headline" data-headline>${headlineMarkup(plate.lines)}</h1>
    <p class="support" data-support>${plate.support}</p>

    ${plate.kind === 'showcase' ? showcaseMarkup() : phoneMarkup()}
  </div>`;

  measure();
}

function measure() {
  const q = (s) => document.querySelector(s);
  const r = (s) => {
    const b = q(s).getBoundingClientRect();
    return { x: +b.x.toFixed(2), y: +b.y.toFixed(2), w: +b.width.toFixed(2), h: +b.height.toFixed(2), bottom: +b.bottom.toFixed(2) };
  };

  const headline = q('[data-headline]');
  const support = q('[data-support]');
  const screen = q('[data-screen]');

  const ink = (el) => {
    const range = document.createRange();
    range.selectNodeContents(el);
    const b = range.getBoundingClientRect();
    return { left: +b.left.toFixed(2), right: +b.right.toFixed(2), width: +b.width.toFixed(2) };
  };
  const lineWidths = [...headline.querySelectorAll('.line')].map(ink);
  const supportInk = ink(support);

  window.__metrics = {
    plate: plate.id,
    canvas: r('[data-canvas]'),
    headline: r('[data-headline]'),
    support: r('[data-support]'),
    screen: r('[data-screen]'),
    frame: r('[data-frame]'),
    device: r('[data-device]'),
    headlineWidth: headline.scrollWidth,
    headlineOverflow: headline.scrollWidth - headline.clientWidth,
    supportWidth: support.scrollWidth,
    supportOverflow: support.scrollWidth - support.clientWidth,
    screenNatural: { w: screen.querySelector('img')?.naturalWidth ?? null, h: screen.querySelector('img')?.naturalHeight ?? null },
    lineWidths,
    supportInk,
    maxInkRight: Math.max(supportInk.right, ...lineWidths.map((l) => l.right)),
    fonts: {
      newsreader: document.fonts.check('500 96px Newsreader'),
      inter: document.fonts.check('400 34px Inter'),
    },
  };
}

async function ready() {
  build();
  await document.fonts.ready;
  await Promise.all(
    [...document.images].map((img) => (img.complete ? Promise.resolve() : new Promise((res) => {
      img.addEventListener('load', res, { once: true });
      img.addEventListener('error', res, { once: true });
    })))
  );
  await new Promise((res) => requestAnimationFrame(() => requestAnimationFrame(res)));
  measure();
  window.__ready = true;
}

ready();
