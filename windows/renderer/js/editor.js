/* photo editor: rotate / flip / crop ratio / adjustments / filters. Saves a COPY. */
const E = { it: null, img: null, rot: 0, flip: false, aspect: 0, b: 0, c: 1, s: 1, w: 0, tab: 'crop', raf: 0 };
const ASPECTS = [null, 1, 4 / 5, 16 / 9];
const ANAMES = ['Original', '1:1', '4:5', '16:9'];
const PRESETS = [
  { n: 'Original', b: 0, c: 1, s: 1, w: 0 }, { n: 'Vivid', b: 0, c: 1.12, s: 1.5, w: .1 }, { n: 'Warm', b: .05, c: 1.05, s: 1.1, w: .6 },
  { n: 'Cool', b: 0, c: 1.05, s: 1, w: -.6 }, { n: 'Mono', b: 0, c: 1.15, s: 0, w: 0 }, { n: 'Fade', b: .12, c: .85, s: .8, w: .1 }
];

function openEditor(it) {
  Object.assign(E, { it, img: null, rot: 0, flip: false, aspect: 0, b: 0, c: 1, s: 1, w: 0, tab: 'crop' });
  $('#editor').classList.remove('hidden'); buildBody();
  $$('#editor [data-etab]').forEach((b) => b.classList.toggle('on', b.dataset.etab === 'crop'));
  const img = new Image(); img.crossOrigin = 'anonymous';
  img.onload = () => { E.img = img; buildBody(); draw(); };
  img.onerror = () => { toast('Couldn’t open this photo'); closeEditor(); };
  img.src = it.url;
}
function closeEditor() { $('#editor').classList.add('hidden'); E.img = null; }

function geometry(img, maxSide) {
  const sw = img.naturalWidth, sh = img.naturalHeight, k = Math.min(1, maxSide / Math.max(sw, sh));
  const w = Math.round(sw * k), h = Math.round(sh * k), odd = E.rot % 2 === 1;
  const cw = odd ? h : w, ch = odd ? w : h;
  const c = document.createElement('canvas'); c.width = cw; c.height = ch;
  const x = c.getContext('2d'); x.translate(cw / 2, ch / 2); x.rotate(E.rot * Math.PI / 2); if (E.flip) x.scale(-1, 1);
  x.drawImage(img, -w / 2, -h / 2, w, h);
  const asp = ASPECTS[E.aspect]; if (!asp) return c;
  let nw = cw, nh = ch; if (cw / ch > asp) nw = Math.round(ch * asp); else nh = Math.round(cw / asp);
  const o = document.createElement('canvas'); o.width = nw; o.height = nh;
  o.getContext('2d').drawImage(c, (cw - nw) / 2, (ch - nh) / 2, nw, nh, 0, 0, nw, nh); return o;
}
/* identical colour maths to the Android editor */
function applyColor(c, b, ct, s, w) {
  const x = c.getContext('2d'); const d = x.getImageData(0, 0, c.width, c.height); const p = d.data;
  const t = (1 - ct) * 128 + b * 100, wv = w * 28;
  for (let i = 0; i < p.length; i += 4) {
    let r = p[i], g = p[i + 1], bl = p[i + 2]; const l = .213 * r + .715 * g + .072 * bl;
    r = l + (r - l) * s; g = l + (g - l) * s; bl = l + (bl - l) * s;
    p[i] = r * ct + t + wv; p[i + 1] = g * ct + t; p[i + 2] = bl * ct + t - wv;
  }
  x.putImageData(d, 0, 0);
}
function draw() {
  if (!E.img || E.raf) return;
  E.raf = requestAnimationFrame(() => {
    E.raf = 0; if (!E.img) return;
    const g = geometry(E.img, 1400); applyColor(g, E.b, E.c, E.s, E.w);
    const cv = $('#ecanvas'); cv.width = g.width; cv.height = g.height; cv.getContext('2d').drawImage(g, 0, 0);
  });
}

function buildBody() {
  const body = $('#ebody'); body.replaceChildren();
  if (E.tab === 'crop') {
    const tools = el('div', 'tools');
    [['↺  Left', () => { E.rot = (E.rot + 3) % 4; }], ['Right  ↻', () => { E.rot = (E.rot + 1) % 4; }], ['Flip  ⇋', () => { E.flip = !E.flip; }]].forEach(([t, f]) => {
      const b = el('button', 'tool', t); b.onclick = () => { f(); draw(); }; tools.appendChild(b);
    });
    const ar = el('div', 'tools');
    ANAMES.forEach((n, i) => { const b = el('button', 'chip' + (E.aspect === i ? ' on' : ''), n); b.onclick = () => { E.aspect = i; buildBody(); draw(); }; ar.appendChild(b); });
    body.append(tools, ar);
  } else if (E.tab === 'adjust') {
    [['Brightness', 'b', -1, 1], ['Contrast', 'c', .5, 1.5], ['Saturation', 's', 0, 2], ['Warmth', 'w', -1, 1]].forEach(([l, k, lo, hi]) => {
      const row = el('div', 'srow', `<label>${l}</label><input type="range" min="${lo}" max="${hi}" step="0.01" value="${E[k]}">`);
      $('input', row).addEventListener('input', (e) => { E[k] = +e.target.value; draw(); }); body.appendChild(row);
    });
    const r = el('div', 'tools'); const b = el('button', 'tool', 'Reset'); b.onclick = () => { E.b = 0; E.c = 1; E.s = 1; E.w = 0; buildBody(); draw(); }; r.appendChild(b); body.appendChild(r);
  } else {
    const fl = el('div', 'filters');
    PRESETS.forEach((p) => {
      const f = el('div', 'fl'); const cv = document.createElement('canvas'); cv.width = cv.height = 68; f.append(cv, el('span', null, p.n));
      if (E.img) {
        const x = cv.getContext('2d'); const k = Math.max(68 / E.img.naturalWidth, 68 / E.img.naturalHeight);
        x.drawImage(E.img, (68 - E.img.naturalWidth * k) / 2, (68 - E.img.naturalHeight * k) / 2, E.img.naturalWidth * k, E.img.naturalHeight * k); applyColor(cv, p.b, p.c, p.s, p.w);
      }
      f.onclick = () => { E.b = p.b; E.c = p.c; E.s = p.s; E.w = p.w; draw(); }; fl.appendChild(f);
    });
    body.appendChild(fl);
  }
}
$$('#editor [data-etab]').forEach((b) => b.addEventListener('click', () => {
  E.tab = b.dataset.etab; $$('#editor [data-etab]').forEach((x) => x.classList.toggle('on', x === b)); buildBody();
}));
$('#eclose').addEventListener('click', closeEditor);
$('#esave').addEventListener('click', async () => {
  if (!E.img) return; const btn = $('#esave'); btn.textContent = 'Saving…';
  try {
    const g = geometry(E.img, 4096); applyColor(g, E.b, E.c, E.s, E.w);
    await api.saveCopy(g.toDataURL('image/jpeg', .95));
    toast('Saved a copy to Pictures\\Gllry'); closeEditor(); if (typeof refresh === 'function') refresh(true);
  } catch (e) { toast('Couldn’t save the photo'); }
  btn.textContent = 'Save copy';
});
