/* home / calendar / search feeds */
function rowOf(items) { return items; }

function tileEl(it, onClick) {
  const t = el('div', 'tile'); t.appendChild(lazyImg(it));
  if (it.video) t.appendChild(el('div', 'dur', '▶ ' + (it.duration ? fmtDur(it.duration) : 'video')));
  t.addEventListener('click', onClick); return t;
}
function heading(title, caption) {
  const s = el('div', 'sec'); s.appendChild(el('h2', null, title));
  if (caption) s.appendChild(el('div', 'cap', caption + ' ⁝')); return s;
}
function openCtx(title, items, index, isArchive) { openViewer({ title, list: items.map((i) => i.path), index, isArchive: !!isArchive }); }

function renderFeed() {
  const sc = $('#scroller'); const top = sc.scrollTop; sc.replaceChildren();
  const wrap = el('div', 'railwrap'); sc.appendChild(wrap);
  const lv = live();
  if (G.query.trim()) renderSearch(wrap, lv);
  else if (G.tab === 'calendar') renderCalendar(wrap, lv);
  else renderHome(wrap, lv);
  sc.scrollTop = top;
}

function renderHome(wrap, lv) {
  if (!lv.length && !G.items.length) { wrap.appendChild(el('div', 'empty', 'No photos yet — use + to add a folder or import files.')); return; }
  // albums
  const albums = buildAlbums(lv);
  const hs = el('div', 'hscroll'); dragScroll(hs);
  albums.forEach((a, n) => {
    const f = el('div', 'folder'); f.style.animationDelay = (n * 40) + 'ms';
    const p1 = el('div', 'ph a'); const p2 = el('div', 'ph b');
    if (a.items[1]) p1.appendChild(lazyImg(a.items[1])); p2.appendChild(lazyImg(a.items[0]));
    f.append(p1, p2);
    const pocket = el('div', 'pocket', `<b></b><span></span>`);
    pocket.firstChild.textContent = a.title; pocket.lastChild.textContent = relTime(a.items[0].time, 'Updated');
    f.appendChild(pocket);
    f.addEventListener('click', () => openCtx(a.title, a.items, 0, a.archive));
    hs.appendChild(f);
  });
  wrap.appendChild(hs);

  // people row: "Selfies" folder, else recent portraits
  let people = lv.filter((i) => /selfie/i.test(i.folder)); let ptitle = 'Selfies';
  if (!people.length) { people = lv.filter((i) => !i.video && i.h > i.w && i.w > 0).slice(0, 14); ptitle = 'Portraits'; }
  if (people.length) {
    wrap.appendChild(heading(ptitle));
    const pr = el('div', 'people'); dragScroll(pr);
    people.slice(0, 16).forEach((it, i) => {
      const p = el('div', 'person'); const a = el('div', 'arch'); a.appendChild(lazyImg(it)); p.appendChild(a);
      const d = new Date(it.time); p.appendChild(el('span', null, `${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)}`));
      p.addEventListener('click', () => openCtx(ptitle, people, i)); pr.appendChild(p);
    });
    wrap.appendChild(pr);
  }
  // day sections
  groupByDay(lv).forEach((g) => {
    wrap.appendChild(heading(g.title, g.caption));
    const grid = el('div', 'grid');
    g.items.forEach((it, i) => grid.appendChild(tileEl(it, () => openCtx(g.title, g.items, i))));
    wrap.appendChild(grid);
  });
}

function renderSearch(wrap, lv) {
  const res = lv.filter((i) => matches(i, G.query));
  wrap.appendChild(heading('Results', `${res.length} found`));
  if (!res.length) { wrap.appendChild(el('div', 'empty', 'Nothing found')); return; }
  const grid = el('div', 'grid');
  res.forEach((it, i) => grid.appendChild(tileEl(it, () => openCtx('Results', res, i))));
  wrap.appendChild(grid);
}

function renderCalendar(wrap, lv) {
  const byDay = new Map();
  lv.forEach((it) => { const k = fmtDate(it.time); if (!byDay.has(k)) byDay.set(k, []); byDay.get(k).push(it); });
  const months = [...new Set(lv.map((i) => { const d = new Date(i.time); return d.getFullYear() * 12 + d.getMonth(); }))].sort((a, b) => b - a);
  if (!months.length) { wrap.appendChild(el('div', 'empty', 'Nothing here yet')); return; }
  months.forEach((ym) => {
    const y = Math.floor(ym / 12), m = ym % 12;
    const days = new Date(y, m + 1, 0).getDate(); const offset = (new Date(y, m, 1).getDay() + 6) % 7;
    let count = 0; for (let d = 1; d <= days; d++) count += (byDay.get(`${y}/${pad(m + 1)}/${pad(d)}`) || []).length;
    wrap.appendChild(heading(`${MONTHS[m]} ${y}`, `${count} memories`));
    const cal = el('div', 'cal');
    ['M', 'T', 'W', 'T', 'F', 'S', 'S'].forEach((w) => cal.appendChild(el('div', 'wd', w)));
    for (let i = 0; i < offset; i++) cal.appendChild(el('div'));
    for (let d = 1; d <= days; d++) {
      const list = byDay.get(`${y}/${pad(m + 1)}/${pad(d)}`);
      if (!list) { cal.appendChild(el('div', 'day', String(d))); continue; }
      const c = el('div', 'day has'); c.appendChild(lazyImg(list[0])); c.appendChild(el('i')); c.appendChild(el('b', null, String(d)));
      c.firstChild.classList.add('in');
      c.addEventListener('click', () => openCtx(`${d} ${MONTHS[m]}`, list, 0)); cal.appendChild(c);
    }
    wrap.appendChild(cal);
  });
}
