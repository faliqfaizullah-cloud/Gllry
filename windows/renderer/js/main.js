/* wiring */
async function refresh() {
  const list = await api.scan();
  G.items = list; G.byPath = new Map(list.map((i) => [i.path, i]));
  renderFeed();
}

function setTab(tab) {
  G.tab = tab; G.query = ''; $('#q').value = ''; closeSearch();
  $('#nav').dataset.tab = tab; $$('#nav .cell').forEach((c) => c.classList.toggle('on', c.dataset.tab === tab));
  renderFeed(); $('#scroller').scrollTop = 0;
}
function openSearch() { $('#search').classList.add('open'); setTimeout(() => $('#q').focus(), 160); }
function closeSearch() { $('#search').classList.remove('open'); $('#q').blur(); }
function closeMenu() { $('#menu').classList.add('hidden'); $('#plus').classList.remove('open'); }

(async function init() {
  G.settings = (await api.settings()) || {}; G.archived = new Set(G.settings.archived || []);
  $('#today').textContent = fmtDate(Date.now()) + ' ⁝';
  $$('#nav .cell').forEach((c) => c.addEventListener('click', () => setTab(c.dataset.tab)));
  setTab('home');

  // search
  $('#search').addEventListener('click', () => { if (!$('#search').classList.contains('open')) openSearch(); });
  let st = 0; $('#q').addEventListener('input', (e) => { G.query = e.target.value; clearTimeout(st); st = setTimeout(renderFeed, 120); });
  $('#q').addEventListener('keydown', (e) => { if (e.key === 'Escape') { G.query = ''; e.target.value = ''; closeSearch(); renderFeed(); e.stopPropagation(); } });

  // + menu
  $('#plus').addEventListener('click', (e) => { e.stopPropagation(); const m = $('#menu'); const show = m.classList.contains('hidden'); m.classList.toggle('hidden', !show); $('#plus').classList.toggle('open', show); });
  document.addEventListener('click', (e) => { if (!e.target.closest('#menu, #plus')) closeMenu(); });
  $('#menu').addEventListener('click', async (e) => {
    const b = e.target.closest('[data-act]'); if (!b) return; closeMenu();
    if (b.dataset.act === 'folder') { const s = await api.pickFolders(); if (s) { toast('Folder added'); await refresh(); } }
    else if (b.dataset.act === 'import') { const n = await api.importFiles(); if (n) { toast(`Saved ${n} item${n === 1 ? '' : 's'} to Gllry`); await refresh(); } }
    else await refresh();
  });

  // hold the "Gllry" title for 3 seconds -> Stack style
  const title = $('#title'); let ht = 0;
  const stopHold = () => { clearTimeout(ht); title.classList.remove('holding'); };
  title.addEventListener('pointerdown', (e) => { if (e.button !== 0) return; title.classList.add('holding'); ht = setTimeout(() => { stopHold(); openStack(); }, 3000); });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((t) => title.addEventListener(t, stopHold));

  // keyboard
  addEventListener('keydown', (e) => {
    if (ST.open) {
      if (e.key === 'Escape') { if (ST.pileOn) leavePile(); else if (ST.overview) { ST.overview = false; showChrome(false); } else closeStack(); }
      else if (e.key === 'ArrowDown' || e.key === 'PageDown') stackStep(1);
      else if (e.key === 'ArrowUp' || e.key === 'PageUp') stackStep(-1);
      else if (e.key === ' ') { e.preventDefault(); stackTap(0); }
      return;
    }
    if (!$('#editor').classList.contains('hidden')) { if (e.key === 'Escape') closeEditor(); return; }
    if (!$('#details').classList.contains('hidden')) { if (e.key === 'Escape') $('#details').classList.add('hidden'); return; }
    if (V.open) {
      if (e.key === 'Escape') closeViewer();
      else if (e.key === 'ArrowRight') go(V.i + 1); else if (e.key === 'ArrowLeft') go(V.i - 1);
      else if (e.key.toLowerCase() === 'a' || e.key === 'ArrowUp') archiveCurrent();
      else if (e.key === 'Delete' || e.key === 'ArrowDown') deleteCurrent();
      else if (e.key.toLowerCase() === 'i') showDetails(V.list[V.i]);
      else if (e.key.toLowerCase() === 'e' && V.list[V.i] && !V.list[V.i].video) openEditor(V.list[V.i]);
      return;
    }
    if (e.key === 'Escape') { closeMenu(); if (G.query) { G.query = ''; $('#q').value = ''; closeSearch(); renderFeed(); } }
    else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') { e.preventDefault(); openSearch(); }
  });

  await refresh();
  if (G.settings.stackDefault) openStack();
  if (api.onFocus) api.onFocus(() => { if (!V.open && !ST.open) refresh(); });
})();
