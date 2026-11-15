/* Stack style: fullscreen cards that fold into a tilted overlapping stack while scrolling,
   unfold to fullscreen when you stop. Click = overview, drag past the end = pile. */
const ST = {
  open: false, list: [], pos: { x: 0, v: 0 }, s: { x: 0, v: 0 }, pile: { x: 0, v: 0 },
  mode: 'idle', moving: false, overview: false, intro: false, pileOn: false, hint: false,
  target: 0, snapK: 200, snapD: .78, W: 0, H: 0, cards: new Map(), raf: 0, last: 0, drag: null, wheelT: 0, hintT: 0
};
const STRIP = .165, SQUASH = .64, SIDE = .90, STACK_TOP = .36, CORNER = 18;

function rnd4(seed) {
  let a = (seed * 7919 + 13) >>> 0; const out = [];
  for (let i = 0; i < 4; i++) { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); out.push(((t ^ (t >>> 14)) >>> 0) / 4294967296); }
  return out;
}
const sSize = () => { ST.W = innerWidth; ST.H = innerHeight; ST.cards.forEach((c) => { c.el.style.width = ST.W + 'px'; c.el.style.height = ST.H + 'px'; }); };

function openStack() {
  if (ST.open) return;
  ST.list = live(); if (!ST.list.length) { toast('Add some photos first'); return; }
  ST.open = true; $('#stack').classList.remove('hidden'); sSize();
  Object.assign(ST, { pos: { x: 0, v: 0 }, s: { x: 0, v: 0 }, pile: { x: 0, v: 0 }, mode: 'idle', moving: false, overview: false, intro: false, pileOn: false, drag: null });
  showChrome(true, 3200); updDefault();
  // intro: starts fullscreen, folds into the stack, scrolls a little, settles
  setTimeout(() => { if (!ST.open || ST.list.length < 2) return; ST.intro = true; ST.target = Math.min(2, ST.list.length - 1); ST.snapK = 50; ST.snapD = .9; ST.mode = 'snap'; }, 450);
  ST.last = performance.now(); ST.raf = requestAnimationFrame(loop);
}
function closeStack() {
  if (!ST.open) return; ST.open = false; cancelAnimationFrame(ST.raf);
  ST.cards.forEach((c) => { stopVid(c); c.el.remove(); }); ST.cards.clear();
  $('#stack').classList.add('hidden'); $('#stackchrome').classList.remove('show');
}
function showChrome(on, ms) {
  const c = $('#stackchrome'); clearTimeout(ST.hintT); c.classList.toggle('show', on);
  if (on && ms) ST.hintT = setTimeout(() => { if (!ST.overview) c.classList.remove('show'); }, ms);
}
function updDefault() { $('#sdefault').textContent = G.settings.stackDefault ? '✓ Default style' : 'Make default'; }

function enterPile() { ST.pileOn = true; ST.overview = false; ST.moving = false; ST.target = ST.list.length - 1; ST.snapK = 200; ST.snapD = .8; ST.mode = 'snap'; showChrome(false); }
function leavePile() { ST.pileOn = false; ST.target = ST.list.length - 1; ST.mode = 'snap'; ST.snapK = 200; ST.snapD = .8; }
function stackTap(y) {
  const last = ST.list.length - 1;
  if (ST.pileOn) { leavePile(); return; }
  if (ST.overview) {
    const strip = ST.H * STRIP, y0 = ST.H * STACK_TOP;
    const idx = clamp(Math.floor((y - y0) / strip + ST.pos.x), 0, last);
    ST.overview = false; showChrome(false); ST.target = idx; ST.snapK = 200; ST.snapD = .78; ST.mode = 'snap'; ST.moving = true;
  } else { ST.overview = true; showChrome(true); }
}
function stackStep(dir) {
  if (ST.pileOn) return; const last = ST.list.length - 1;
  ST.moving = true; ST.overview = false; ST.target = clamp(Math.round(ST.pos.x) + dir, 0, last); ST.snapK = 200; ST.snapD = .78; ST.mode = 'snap'; showChrome(false);
}

function makeCard(i) {
  const it = ST.list[i]; const c = { i, it, el: el('div', 'scard'), r: rnd4(i), vid: null, img: null };
  c.el.style.width = ST.W + 'px'; c.el.style.height = ST.H + 'px';
  const img = el('img'); img.draggable = false; c.img = img;
  if (it.video) { thumbUrl(it).then((u) => { if (u) img.src = u; }); c.el.appendChild(img); c.el.appendChild(el('div', 'badge', '▶')); }
  else { img.src = it.url; c.el.appendChild(img); }
  $('#stackcards').appendChild(c.el); ST.cards.set(i, c); return c;
}
function stopVid(c) { if (c.vid) { try { c.vid.pause(); c.vid.removeAttribute('src'); c.vid.load(); } catch (e) {} c.vid.remove(); c.vid = null; } }

function loop(now) {
  if (!ST.open) return;
  const dt = Math.min(.05, (now - ST.last) / 1000); ST.last = now;
  const n = ST.list.length, last = n - 1, P = ST.pos;
  if (ST.mode === 'fling') {
    P.x += P.v * dt; P.v *= Math.exp(-3.4 * dt);
    if (P.x < -.4) { P.x = -.4; P.v = 0; } if (P.x > last + .9) { P.x = last + .9; P.v = 0; }
    if (Math.abs(P.v) < .35) {
      if (P.x > last + .55) enterPile();
      else { ST.target = clamp(Math.round(P.x), 0, last); ST.snapK = 200; ST.snapD = .78; ST.mode = 'snap'; }
    }
  } else if (ST.mode === 'snap') {
    springStep(P, ST.target, dt, ST.snapD, ST.snapK);
    if (Math.abs(P.x - ST.target) < .003 && Math.abs(P.v) < .02) { P.x = ST.target; P.v = 0; ST.mode = 'idle'; if (!ST.pileOn) ST.moving = false; ST.intro = false; }
  }
  const on = ST.moving || ST.overview || ST.intro || ST.pileOn;
  springStep(ST.s, on ? 1 : 0, dt, .85, on ? 1500 : 200);
  springStep(ST.pile, ST.pileOn ? 1 : 0, dt, .72, 200);
  render(); ST.raf = requestAnimationFrame(loop);
}

function render() {
  const n = ST.list.length, W = ST.W, H = ST.H, P = ST.pos.x, s = ST.s.x, pl = ST.pile.x;
  const sc = clamp(s, 0, 1), pc = clamp(pl, 0, 1), strip = H * STRIP, y0 = H * STACK_TOP;
  const lo = ST.pileOn ? Math.max(0, n - 16) : Math.max(0, Math.floor(P - 3.3));
  const hi = ST.pileOn ? n - 1 : Math.min(n - 1, Math.ceil(P + 6.2));
  ST.cards.forEach((c, i) => { if (i < lo || i > hi) { stopVid(c); c.el.remove(); ST.cards.delete(i); } });
  const calm = sc < .03 && !ST.pileOn && !ST.moving && ST.mode === 'idle'; const cur = Math.round(P);
  for (let i = lo; i <= hi; i++) {
    const c = ST.cards.get(i) || makeCard(i); const d = i - P;
    let ty = mix(d * H, y0 + d * strip, s), sx = mix(1, SIDE, s), sy = mix(1, SQUASH, s), tx = 0, al = 1;
    if (pc > 0) {
      const r = c.r, ps = .30 + .10 * r[2];
      ty = mix(ty, H * (.16 + .46 * r[1]), pl); tx = (r[0] - .5) * W * .62 * pl;
      sx = mix(sx, ps * (.75 + .5 * r[3]), pl); sy = mix(sy, ps, pl);
      if (d < -3.3 || d > 6.2) al = pc;
    }
    const rr = CORNER * Math.max(sc, pc * .6), sh = Math.max(sc, pc);
    const st = c.el.style;
    st.transform = `translate(${tx}px, ${ty}px) scale(${sx}, ${sy})`;
    st.borderRadius = `${rr / sx}px / ${rr / sy}px`;
    st.boxShadow = `0 ${20 * sh}px ${44 * sh}px rgba(0,0,0,${.32 * sh})`;
    st.opacity = al; st.zIndex = i;
    // videos play only when their card is fullscreen and still
    if (c.it.video) {
      if (calm && i === cur && !c.vid) { const v = el('video'); v.src = c.it.url; v.autoplay = true; v.loop = true; v.playsInline = true; v.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;object-fit:cover;pointer-events:none'; c.el.appendChild(v); c.vid = v; const b = $('.badge', c.el); if (b) b.style.display = 'none'; }
      else if ((!calm || i !== cur) && c.vid) { stopVid(c); const b = $('.badge', c.el); if (b) b.style.display = ''; }
    }
  }
  const k = pc; const a = [mix(243, 255, k), mix(218, 255, k)].map(Math.round);
  $('#stack').style.background = `linear-gradient(rgb(${a[0]},${a[0]},${a[0]}), rgb(${a[1]},${a[1]},${a[1]}))`;
}

/* input */
(function () {
  const root = $('#stack');
  root.addEventListener('pointerdown', (e) => {
    if (e.target.closest('#stackchrome') || e.button !== 0) return;
    ST.drag = { y0: e.clientY, ly: e.clientY, moved: false, s: [{ y: e.clientY, t: performance.now() }] }; root.setPointerCapture(e.pointerId);
  });
  root.addEventListener('pointermove', (e) => {
    const d = ST.drag; if (!d || ST.pileOn) return;
    if (!d.moved && Math.abs(e.clientY - d.y0) > 6) { d.moved = true; ST.moving = true; ST.mode = 'drag'; ST.overview = false; showChrome(false); }
    if (!d.moved) return;
    const strip = ST.H * STRIP, delta = -(e.clientY - d.ly) / strip, P = ST.pos, last = ST.list.length - 1;
    const f = ((P.x < 0 && delta < 0) || (P.x > last && delta > 0)) ? .35 : 1;
    P.x = clamp(P.x + delta * f, -.6, last + 1); P.v = 0; d.ly = e.clientY;
    d.s.push({ y: e.clientY, t: performance.now() }); if (d.s.length > 8) d.s.shift();
  });
  const up = (e) => {
    const d = ST.drag; ST.drag = null; if (!d) return;
    if (!d.moved) { stackTap(e.clientY); return; }
    const a = d.s[0], b = d.s[d.s.length - 1]; const dtm = Math.max(.016, (b.t - a.t) / 1000);
    const strip = ST.H * STRIP, last = ST.list.length - 1;
    if (ST.pos.x > last + .55) { enterPile(); return; }
    ST.pos.v = clamp(-((b.y - a.y) / dtm) / strip, -14, 14); ST.mode = 'fling';
  };
  root.addEventListener('pointerup', up); root.addEventListener('pointercancel', () => { ST.drag = null; });
  root.addEventListener('wheel', (e) => {
    e.preventDefault(); if (ST.pileOn) { if (e.deltaY < 0) leavePile(); return; }
    const last = ST.list.length - 1, P = ST.pos;
    ST.moving = true; ST.mode = 'wheel'; ST.overview = false; showChrome(false); P.v = 0;
    P.x = clamp(P.x + e.deltaY / (ST.H * STRIP * 1.6), -.6, last + 1);
    clearTimeout(ST.wheelT); ST.wheelT = setTimeout(() => { if (ST.mode === 'wheel') { if (P.x > last + .55) enterPile(); else ST.mode = 'fling'; } }, 140);
  }, { passive: false });
  $('#sexit').addEventListener('click', closeStack);
  $('#sdefault').addEventListener('click', () => { G.settings.stackDefault = !G.settings.stackDefault; persist(); updDefault(); toast(G.settings.stackDefault ? 'Stack is now the default style' : 'Default style: classic'); });
  addEventListener('resize', () => { if (ST.open) sSize(); });
})();
