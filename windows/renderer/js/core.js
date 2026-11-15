/* shared state + helpers */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const el = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
const pad = (n) => String(n).padStart(2, '0');
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const mix = (a, b, t) => a + (b - a) * t;
const api = window.gllry;

const G = { items: [], byPath: new Map(), archived: new Set(), settings: {}, tab: 'home', query: '' };

const fmtDate = (ms) => { const d = new Date(ms); return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())}`; };
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const fmtDur = (s) => { s = Math.max(0, Math.floor(s || 0)); const m = Math.floor(s / 60); return `${m}:${pad(s % 60)}`; };
const fmtSize = (b) => b >= 1048576 ? (b / 1048576).toFixed(1) + ' MB' : b >= 1024 ? Math.round(b / 1024) + ' KB' : (b || 0) + ' B';

function relTime(ms, prefix) {
  const m = Math.floor((Date.now() - ms) / 60000);
  if (m < 1) return `${prefix} just now`;
  if (m < 60) return `${prefix} ${m} minute${m === 1 ? '' : 's'} ago`;
  if (m < 1440) { const h = Math.floor(m / 60); return `${prefix} ${h} hour${h === 1 ? '' : 's'} ago`; }
  const d = new Date(ms);
  return `${prefix} ${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.classList.remove('hidden');
  clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.add('hidden'), 2600);
}

function groupByDay(list) {
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const map = new Map();
  list.forEach((it) => { const d = new Date(it.time); d.setHours(0, 0, 0, 0); const k = d.getTime(); if (!map.has(k)) map.set(k, []); map.get(k).push(it); });
  return [...map.entries()].sort((a, b) => b[0] - a[0]).map(([k, arr]) => {
    const d = new Date(k); const gap = Math.round((today - d) / 86400000);
    const title = gap === 0 ? 'Today' : gap === 1 ? 'Yesterday' : gap < 7 ? DAYS[d.getDay()]
      : d.getFullYear() === today.getFullYear() ? `${d.getDate()} ${MONTHS[d.getMonth()]}` : `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
    return { title, caption: fmtDate(k), items: arr, date: d };
  });
}

function buildAlbums(live) {
  const m = new Map();
  live.forEach((it) => { if (!m.has(it.folder)) m.set(it.folder, []); m.get(it.folder).push(it); });
  const out = [...m.entries()].map(([k, v]) => ({ key: k, title: k, items: v }));
  const vids = live.filter((i) => i.video); if (vids.length) out.push({ key: '__videos', title: 'Videos', items: vids });
  const arch = G.items.filter((i) => G.archived.has(i.path)); if (arch.length) out.push({ key: '__archive', title: 'Archive', items: arch, archive: true });
  return out.sort((a, b) => b.items[0].time - a.items[0].time);
}

function matches(it, q) {
  const d = new Date(it.time);
  const hay = [it.folder, it.name, fmtDate(it.time), MONTHS[d.getMonth()], DAYS[d.getDay()], d.getFullYear(), it.video ? 'video' : 'photo'].join(' ').toLowerCase();
  return q.trim().toLowerCase().split(/\s+/).filter(Boolean).every((t) => hay.includes(t));
}

/* thumbnails: ask the main process (Windows shell thumbnails, cached on disk) lazily */
const thumbCache = new Map();
function thumbUrl(it) {
  if (!thumbCache.has(it.path)) {
    thumbCache.set(it.path, Promise.resolve(api.thumb(it.path, it.time, it.video)).then((u) => u || (it.video ? null : it.url)).catch(() => (it.video ? null : it.url)));
  }
  return thumbCache.get(it.path);
}
const io = new IntersectionObserver((entries) => {
  entries.forEach((e) => {
    if (!e.isIntersecting) return;
    const img = e.target; io.unobserve(img);
    const it = G.byPath.get(img.dataset.p); if (!it) return;
    thumbUrl(it).then((u) => { if (!u) return; img.onload = () => img.classList.add('in'); img.src = u; });
  });
}, { rootMargin: '700px' });
function lazyImg(it, cls) {
  const img = el('img', cls); img.dataset.p = it.path; img.decoding = 'async'; img.draggable = false; io.observe(img); return img;
}

/* spring integrator (same maths as Compose springs) */
function springStep(st, target, dt, damping, stiffness) {
  const c = 2 * damping * Math.sqrt(stiffness);
  let t = dt;
  while (t > 0) {
    const h = Math.min(t, 1 / 240);
    st.v += (stiffness * (target - st.x) - c * st.v) * h;
    st.x += st.v * h;
    t -= h;
  }
}

/* archive + persistence */
function persist() { api.setSettings({ archived: [...G.archived], stackDefault: !!G.settings.stackDefault }); }
function setArchived(it, on) { if (on) G.archived.add(it.path); else G.archived.delete(it.path); persist(); }
const live = () => G.items.filter((i) => !G.archived.has(i.path));

/* drag-to-scroll for horizontal rows */
function dragScroll(node) {
  let down = false, sx = 0, sl = 0, moved = false;
  node.addEventListener('pointerdown', (e) => { if (e.button !== 0) return; down = true; moved = false; sx = e.clientX; sl = node.scrollLeft; });
  window.addEventListener('pointermove', (e) => { if (!down) return; const dx = e.clientX - sx; if (Math.abs(dx) > 5) moved = true; node.scrollLeft = sl - dx; });
  window.addEventListener('pointerup', () => { down = false; });
  node.addEventListener('click', (e) => { if (moved) { e.stopPropagation(); e.preventDefault(); moved = false; } }, true);
  node.addEventListener('wheel', (e) => { if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) { node.scrollLeft += e.deltaY; e.preventDefault(); } }, { passive: false });
}
