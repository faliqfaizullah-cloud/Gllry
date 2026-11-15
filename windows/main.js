const { app, BrowserWindow, ipcMain, dialog, shell, nativeImage, Menu, protocol, net } = require('electron');
const path = require('path');
const fs = require('fs');
const fsp = fs.promises;
const crypto = require('crypto');
const { pathToFileURL } = require('url');
let exifr = null;
try { exifr = require('exifr'); } catch (e) { exifr = null; }

protocol.registerSchemesAsPrivileged([
  { scheme: 'gllry', privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true } }
]);

let win = null;
const IMG = new Set(['.jpg', '.jpeg', '.png', '.webp', '.gif', '.bmp', '.avif']);
const VID = new Set(['.mp4', '.webm', '.mov', '.m4v', '.mkv']);
const SKIP = new Set(['node_modules', '$recycle.bin', 'system volume information', 'appdata', 'windows', 'program files', 'program files (x86)']);

const fileUrl = (p) => 'gllry://f/' + encodeURIComponent(p);
const settingsFile = () => path.join(app.getPath('userData'), 'settings.json');

function readSettings() {
  const defaults = { folders: [app.getPath('pictures'), app.getPath('videos')], archived: [], stackDefault: false };
  try { return { ...defaults, ...JSON.parse(fs.readFileSync(settingsFile(), 'utf8')) }; } catch (e) { return defaults; }
}
function writeSettings(s) {
  try { fs.mkdirSync(path.dirname(settingsFile()), { recursive: true }); fs.writeFileSync(settingsFile(), JSON.stringify(s)); } catch (e) {}
}

function allowed(p) {
  const roots = [app.getPath('userData'), app.getPath('pictures'), app.getPath('videos'), app.getPath('temp'), ...readSettings().folders];
  const lp = path.normalize(p).toLowerCase();
  return roots.some((r) => lp.startsWith(path.normalize(r).toLowerCase()));
}

// ---------------------------------------------------------------- library scan
async function walk(dir, depth, out) {
  if (depth > 6 || out.length >= 60000) return;
  let ents;
  try { ents = await fsp.readdir(dir, { withFileTypes: true }); } catch (e) { return; }
  const files = [];
  for (const e of ents) {
    if (e.name.startsWith('.')) continue;
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (!SKIP.has(e.name.toLowerCase())) await walk(full, depth + 1, out);
    } else {
      const ext = path.extname(e.name).toLowerCase();
      if (IMG.has(ext) || VID.has(ext)) files.push({ full, ext, name: e.name });
    }
  }
  for (let i = 0; i < files.length; i += 64) {
    await Promise.all(files.slice(i, i + 64).map(async (f) => {
      try {
        const st = await fsp.stat(f.full);
        out.push({ path: f.full, url: fileUrl(f.full), name: f.name, folder: path.basename(dir), size: st.size, time: Math.round(st.mtimeMs), video: VID.has(f.ext), w: 0, h: 0 });
      } catch (e) {}
    }));
  }
}

ipcMain.handle('library:scan', async () => {
  const s = readSettings();
  const out = [];
  for (const d of s.folders) await walk(d, 0, out);
  const map = new Map();
  out.forEach((o) => map.set(o.path.toLowerCase(), o));
  const list = [...map.values()].sort((a, b) => b.time - a.time);
  // dimensions for the newest photos (used to pick portraits for the people row)
  if (exifr) {
    const head = list.filter((i) => !i.video).slice(0, 60);
    for (let i = 0; i < head.length; i += 8) {
      await Promise.all(head.slice(i, i + 8).map(async (it) => {
        try {
          const t = await exifr.parse(it.path, ['ImageWidth', 'ImageHeight', 'ExifImageWidth', 'ExifImageHeight', 'Orientation']);
          if (!t) return;
          let w = t.ExifImageWidth || t.ImageWidth || 0, h = t.ExifImageHeight || t.ImageHeight || 0;
          if ([5, 6, 7, 8].includes(t.Orientation)) { const x = w; w = h; h = x; }
          it.w = w; it.h = h;
        } catch (e) {}
      }));
    }
  }
  return list;
});

// ---------------------------------------------------------------- thumbnails (Windows shell thumbnails: photos AND videos)
const thumbDir = () => path.join(app.getPath('userData'), 'thumbs');
let busy = 0;
const queue = [];
function pump() {
  while (busy < 3 && queue.length) {
    const { fn, res } = queue.shift();
    busy++;
    fn().then(res, () => res(null)).finally(() => { busy--; pump(); });
  }
}
const runThumb = (fn) => new Promise((res) => { queue.push({ fn, res }); pump(); });

ipcMain.handle('library:thumb', (e, p, time, video) => runThumb(async () => {
  const key = crypto.createHash('md5').update(p + '|' + time).digest('hex');
  const out = path.join(thumbDir(), key + '.jpg');
  if (fs.existsSync(out)) return fileUrl(out);
  await fsp.mkdir(thumbDir(), { recursive: true });
  let img = null;
  try { img = await nativeImage.createThumbnailFromPath(p, { width: 480, height: 480 }); } catch (err) { img = null; }
  if ((!img || img.isEmpty()) && !video) {
    img = nativeImage.createFromPath(p);
    if (!img.isEmpty()) {
      const sz = img.getSize();
      const k = 480 / Math.max(sz.width, sz.height);
      if (k < 1) img = img.resize({ width: Math.round(sz.width * k), height: Math.round(sz.height * k) });
    }
  }
  if (!img || img.isEmpty()) return null;
  await fsp.writeFile(out, img.toJPEG(82));
  return fileUrl(out);
}));

// ---------------------------------------------------------------- settings, pickers, files
ipcMain.handle('settings:get', () => readSettings());
ipcMain.handle('settings:set', (e, patch) => { const s = { ...readSettings(), ...patch }; writeSettings(s); return s; });

ipcMain.handle('pick:folders', async () => {
  const r = await dialog.showOpenDialog(win, { properties: ['openDirectory', 'multiSelections'] });
  if (r.canceled) return null;
  const s = readSettings();
  s.folders = [...new Set([...s.folders, ...r.filePaths])];
  writeSettings(s);
  return s;
});

ipcMain.handle('pick:import', async () => {
  const exts = [...IMG, ...VID].map((x) => x.slice(1));
  const r = await dialog.showOpenDialog(win, { properties: ['openFile', 'multiSelections'], filters: [{ name: 'Photos & videos', extensions: exts }] });
  if (r.canceled) return 0;
  let n = 0;
  for (const src of r.filePaths) {
    try {
      const ext = path.extname(src).toLowerCase();
      const dir = path.join(app.getPath(VID.has(ext) ? 'videos' : 'pictures'), 'Gllry');
      await fsp.mkdir(dir, { recursive: true });
      let dest = path.join(dir, path.basename(src));
      let i = 1;
      while (fs.existsSync(dest)) dest = path.join(dir, `${path.basename(src, ext)} (${i++})${ext}`);
      await fsp.copyFile(src, dest);
      n++;
    } catch (e) {}
  }
  return n;
});

ipcMain.handle('file:trash', async (e, paths) => {
  const r = await dialog.showMessageBox(win, {
    type: 'question', buttons: ['Move to Recycle Bin', 'Cancel'], defaultId: 1, cancelId: 1, title: 'Delete',
    message: paths.length === 1 ? `Move "${path.basename(paths[0])}" to the Recycle Bin?` : `Move ${paths.length} items to the Recycle Bin?`
  });
  if (r.response !== 0) return false;
  for (const p of paths) { try { await shell.trashItem(p); } catch (err) {} }
  return true;
});

ipcMain.handle('file:info', async (e, p) => {
  const out = { name: path.basename(p), folder: path.dirname(p) };
  try { const st = await fsp.stat(p); out.size = st.size; out.mtime = st.mtimeMs; } catch (err) {}
  if (exifr && !VID.has(path.extname(p).toLowerCase())) {
    try {
      const t = await exifr.parse(p, { tiff: true, exif: true, gps: true, ifd0: true });
      if (t) {
        out.make = t.Make; out.model = t.Model; out.iso = t.ISO; out.f = t.FNumber; out.exposure = t.ExposureTime;
        out.focal = t.FocalLength; out.lat = t.latitude; out.lon = t.longitude;
        out.taken = t.DateTimeOriginal ? new Date(t.DateTimeOriginal).getTime() : null;
        out.width = t.ExifImageWidth || t.ImageWidth; out.height = t.ExifImageHeight || t.ImageHeight;
      }
    } catch (err) {}
  }
  return out;
});

ipcMain.handle('file:save', async (e, dataUrl) => {
  const b64 = String(dataUrl).split(',')[1] || '';
  const dir = path.join(app.getPath('pictures'), 'Gllry');
  await fsp.mkdir(dir, { recursive: true });
  const out = path.join(dir, `Gllry_${Date.now()}.jpg`);
  await fsp.writeFile(out, Buffer.from(b64, 'base64'));
  return out;
});

ipcMain.handle('file:reveal', (e, p) => shell.showItemInFolder(p));

// ---------------------------------------------------------------- window
function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 820, minWidth: 760, minHeight: 560,
    backgroundColor: '#000000', title: 'Gllry',
    icon: path.join(__dirname, 'build', 'icon.png'),
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#000000', symbolColor: '#ffffff', height: 36 },
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.on('focus', () => win && win.webContents.send('app:focus'));
}

if (!app.requestSingleInstanceLock()) { app.quit(); }
else {
  app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
  app.whenReady().then(() => {
    app.setAppUserModelId('com.gllry.desktop');
    Menu.setApplicationMenu(null);
    protocol.handle('gllry', async (req) => {
      try {
        const p = decodeURIComponent(new URL(req.url).pathname.slice(1));
        if (!allowed(p)) return new Response('forbidden', { status: 403 });
        const r = await net.fetch(pathToFileURL(p).href, { headers: req.headers, bypassCustomProtocolHandlers: true });
        const headers = new Headers(r.headers);
        headers.set('Access-Control-Allow-Origin', '*');
        return new Response(r.body, { status: r.status, statusText: r.statusText, headers });
      } catch (e) { return new Response('not found', { status: 404 }); }
    });
    createWindow();
    app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
  });
  app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
}
