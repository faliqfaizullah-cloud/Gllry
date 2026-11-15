/* album / photo viewer: rounded card + thumbnail strip. Drag up = archive, drag down = delete */
const V = { ctx: null, list: [], i: 0, layer: null, open: false };
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

function openViewer(ctx) {
  V.ctx = ctx;
  V.list = ctx.list.map((p) => G.byPath.get(p)).filter((it) => it && (ctx.isArchive || !G.archived.has(it.path)));
  if (!V.list.length) return;
  V.i = clamp(ctx.index || 0, 0, V.list.length - 1);
  V.open = true; V.layer = null; $('#vstage').replaceChildren();
  $('#viewer').classList.remove('hidden');
  $('#viewer .vdate').textContent = fmtDate(Date.now()) + ' ⁝';
  $('#vname').textContent = ctx.title;
  buildThumbs(); showCurrent(0);
}
function closeViewer() {
  if (!V.open) return;
  V.open = false; disposeLayer(V.layer); V.layer = null;
  $('#viewer').classList.add('hidden'); renderFeed();
}

function disposeLayer(layer) {
  if (!layer) return;
  layer.querySelectorAll('video').forEach((v) => { try { v.pause(); v.removeAttribute('src'); v.load(); } catch (e) {} });
  layer.remove();
}

function makeLayer(it) {
  const layer = el('div'); layer.style.cssText = 'position:absolute;inset:0;';
  if (!it.video) {
    const bg = el('img', 'mediabg'); bg.src = it.url; bg.draggable = false;
    const im = el('img', 'media'); im.src = it.url; im.draggable = false;
    layer.append(bg, im);
  } else {
    const v = el('video', 'media'); v.src = it.url; v.autoplay = true; v.playsInline = true; v.loop = false; layer.appendChild(v);
    attachControls(layer, v);
  }
  return layer;
}

function attachControls(layer, v) {
  const big = el('div', 'bigplay', '▶');
  const bar = el('div', 'vcontrols', '<span class="t0">0:00</span><input type="range" min="0" max="1000" value="0"><span class="t1">0:00</span><button class="mute">Mute</button>');
  layer.append(big, bar);
  const rng = $('input', bar), t0 = $('.t0', bar), t1 = $('.t1', bar), mute = $('.mute', bar);
  const toggle = () => { if (v.ended) v.currentTime = 0; v.paused || v.ended ? v.play() : v.pause(); };
  let hideT = 0;
  const showBar = () => { bar.style.opacity = 1; clearTimeout(hideT); hideT = setTimeout(() => { if (!v.paused) bar.style.opacity = 0; }, 2500); };
  v.addEventListener('loadedmetadata', () => { t1.textContent = fmtDur(v.duration); });
  v.addEventListener('timeupdate', () => { t0.textContent = fmtDur(v.currentTime); if (v.duration) rng.value = Math.round(v.currentTime / v.duration * 1000); });
  v.addEventListener('play', () => { big.style.opacity = 0; big.style.transform = 'scale(.7)'; showBar(); });
  v.addEventListener('pause', () => { big.textContent = '▶'; big.style.opacity = 1; big.style.transform = 'none'; bar.style.opacity = 1; });
  v.addEventListener('ended', () => { big.textContent = '↻'; big.style.opacity = 1; big.style.transform = 'none'; });
  rng.addEventListener('input', () => { if (v.duration) v.currentTime = rng.value / 1000 * v.duration; });
  mute.addEventListener('click', () => { v.muted = !v.muted; mute.textContent = v.muted ? 'Sound' : 'Mute'; });
  big.addEventListener('click', toggle); v.addEventListener('click', toggle);
  layer.addEventListener('mousemove', showBar);
}

function showCurrent(dir) {
  const it = V.list[V.i]; if (!it) return;
  const layer = makeLayer(it); const old = V.layer;
  $('#vstage').appendChild(layer); V.layer = layer;
  if (dir && old) {
    layer.animate([{ transform: `translateX(${dir * 70}px) scale(.94)`, opacity: 0 }, { transform: 'none', opacity: 1 }], { duration: 480, easing: 'cubic-bezier(.34,1.4,.64,1)' });
    old.animate([{ opacity: 1 }, { opacity: 0, transform: `translateX(${-dir * 50}px) scale(.94)` }], { duration: 260, fill: 'forwards' }).onfinish = () => disposeLayer(old);
  } else if (old) {
    if (dir === 0) disposeLayer(old); else { layer.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 300 }); disposeLayer(old); }
  }
  $('#bInfo').classList.remove('hidden'); $('#bEdit').classList.toggle('hidden', !!it.video);
  $('#vsub').textContent = relTime(it.time, 'Added');
  $('#vmore').textContent = `+${Math.max(0, V.list.length - 5)} more`;
  $$('#vthumbs .vt').forEach((t, n) => t.classList.toggle('sel', n === V.i));
  const sel = $$('#vthumbs .vt')[V.i]; if (sel) sel.scrollIntoView({ inline: 'center', behavior: 'smooth', block: 'nearest' });
}

function buildThumbs() {
  const strip = $('#vthumbs'); strip.replaceChildren();
  V.list.forEach((it, n) => {
    const t = el('div', 'vt'); t.appendChild(lazyImg(it));
    t.addEventListener('click', () => go(n)); strip.appendChild(t);
  });
}
function go(n) {
  n = clamp(n, 0, V.list.length - 1); if (n === V.i) return;
  const dir = n > V.i ? 1 : -1; V.i = n; showCurrent(dir);
}

function afterRemove() {
  if (!V.list.length) { closeViewer(); return; }
  V.i = Math.min(V.i, V.list.length - 1); buildThumbs(); showCurrent(1);
}
function archiveCurrent() {
  const it = V.list[V.i]; if (!it) return;
  setArchived(it, !V.ctx.isArchive); V.list.splice(V.i, 1);
  toast(V.ctx.isArchive ? 'Restored' : 'Archived'); afterRemove();
}
async function deleteCurrent() {
  const it = V.list[V.i]; if (!it) return false;
  const ok = await api.trash([it.path]);
  if (!ok) return false;
  G.items = G.items.filter((x) => x.path !== it.path); G.byPath.delete(it.path); G.archived.delete(it.path);
  V.list.splice(V.i, 1); toast('Moved to the Recycle Bin'); afterRemove(); return true;
}

/* pointer gestures on the card */
(function () {
  const card = $('#vcard'); const up = $('#hintUp'), down = $('#hintDown'); const TH = 110;
  let drag = null;
  const setHints = (dy) => {
    const pu = clamp(-dy / TH, 0, 1), pd = clamp(dy / TH, 0, 1);
    up.style.opacity = pu; up.style.transform = `translateX(-50%) scale(${.7 + .3 * pu})`;
    down.style.opacity = pd; down.style.transform = `translateX(-50%) scale(${.7 + .3 * pd})`;
  };
  card.addEventListener('pointerdown', (e) => {
    if (e.button !== 0 || e.target.closest('button, .vcontrols, input, .bigplay')) return;
    drag = { x: e.clientX, y: e.clientY, dx: 0, dy: 0, axis: null }; card.setPointerCapture(e.pointerId);
    if (V.layer) V.layer.style.transition = 'none';
  });
  card.addEventListener('pointermove', (e) => {
    if (!drag || !V.layer) return;
    drag.dx = e.clientX - drag.x; drag.dy = (e.clientY - drag.y) * .85;
    if (!drag.axis && Math.hypot(drag.dx, drag.dy) > 8) drag.axis = Math.abs(drag.dy) > Math.abs(drag.dx) ? 'y' : 'x';
    if (drag.axis === 'y') {
      const p = clamp(Math.abs(drag.dy) / (TH * 3), 0, 1);
      V.layer.style.transform = `translateY(${drag.dy}px) scale(${1 - .22 * p})`; V.layer.style.opacity = 1 - .4 * p; setHints(drag.dy);
    } else if (drag.axis === 'x') {
      V.layer.style.transform = `translateX(${drag.dx * .6}px)`;
    }
  });
  const end = async () => {
    if (!drag) return; const d = drag; drag = null; setHints(0);
    const layer = V.layer; if (!layer) return;
    const back = () => { layer.style.transition = 'transform .55s cubic-bezier(.34,1.56,.64,1), opacity .3s'; layer.style.transform = 'none'; layer.style.opacity = 1; };
    if (d.axis === 'y' && d.dy < -TH) {
      layer.style.transition = 'transform .35s cubic-bezier(.4,0,1,1), opacity .35s'; layer.style.transform = 'translateY(-120%)'; layer.style.opacity = 0;
      setTimeout(archiveCurrent, 280);
    } else if (d.axis === 'y' && d.dy > TH) { back(); await deleteCurrent(); }
    else if (d.axis === 'x' && d.dx < -80 && V.i < V.list.length - 1) { layer.style.transform = ''; go(V.i + 1); }
    else if (d.axis === 'x' && d.dx > 80 && V.i > 0) { layer.style.transform = ''; go(V.i - 1); }
    else back();
  };
  card.addEventListener('pointerup', end); card.addEventListener('pointercancel', end);
  card.addEventListener('wheel', (e) => { if (!V.open) return; e.preventDefault(); if (Math.abs(e.deltaY) + Math.abs(e.deltaX) < 20) return; if (card._w) return; card._w = 1; setTimeout(() => (card._w = 0), 280); go(V.i + ((e.deltaY + e.deltaX) > 0 ? 1 : -1)); }, { passive: false });
  $('#vclose').addEventListener('click', closeViewer);
  $('#vprev').addEventListener('click', () => go(V.i - 1)); $('#vnext').addEventListener('click', () => go(V.i + 1));
  $('#bInfo').addEventListener('click', () => showDetails(V.list[V.i]));
  $('#bEdit').addEventListener('click', () => V.list[V.i] && openEditor(V.list[V.i]));
})();

/* details sheet */
async function showDetails(it) {
  if (!it) return;
  const root = $('#details'); root.classList.remove('hidden');
  $('#dbody').innerHTML = '<div class="kv"><span>Loading…</span></div>';
  let info = {}; try { info = await api.info(it.path) || {}; } catch (e) {}
  const d = new Date(info.taken || it.time);
  const kv = (k, v) => (v ? `<div class="kv"><b>${k}</b><span>${esc(v)}</span></div>` : '');
  let w = info.width, h = info.height;
  const im = V.layer && $('img.media', V.layer); if (!w && im && im.naturalWidth) { w = im.naturalWidth; h = im.naturalHeight; }
  const cam = [info.make, info.model].filter(Boolean).join(' ');
  const shot = [info.f ? `f/${(+info.f).toFixed(1)}` : '', info.exposure ? (info.exposure < 1 ? `1/${Math.round(1 / info.exposure)}s` : `${(+info.exposure).toFixed(1)}s`) : '', info.iso ? `ISO ${info.iso}` : '', info.focal ? `${(+info.focal).toFixed(1)} mm` : ''].filter(Boolean).join('  ·  ');
  $('#dbody').innerHTML =
    `<h3>${DAYS[d.getDay()].slice(0, 3)}<i> ${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)}.</i></h3>` +
    '<h4>Photo</h4>' +
    kv('Taken', `${DAYS[d.getDay()]}, ${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()} · ${pad(d.getHours())}:${pad(d.getMinutes())}`) +
    kv('Name', it.name) + (w ? kv('Resolution', `${w} × ${h}  ·  ${(w * h / 1e6).toFixed(1)} MP`) : '') + kv('Size', fmtSize(info.size || it.size)) +
    kv('Type', (it.name.split('.').pop() || '').toUpperCase()) + kv('Folder', info.folder || it.folder) +
    ((cam || shot) ? '<h4>Camera</h4>' + kv('Device', cam) + kv('Settings', shot) : '') +
    ((info.lat != null && info.lon != null) ? '<h4>Location</h4>' + kv('Coordinates', `${(+info.lat).toFixed(5)}, ${(+info.lon).toFixed(5)}`) : '') +
    '<button class="pill" id="dreveal">Show in folder</button>';
  $('#dreveal').onclick = () => api.reveal(it.path);
}
$('#details').addEventListener('click', (e) => { if (e.target.id === 'details') $('#details').classList.add('hidden'); });
