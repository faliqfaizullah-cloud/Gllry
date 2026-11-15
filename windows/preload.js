const { contextBridge, ipcRenderer } = require('electron');
const inv = (c, ...a) => ipcRenderer.invoke(c, ...a);
contextBridge.exposeInMainWorld('gllry', {
  settings: () => inv('settings:get'),
  setSettings: (p) => inv('settings:set', p),
  scan: () => inv('library:scan'),
  thumb: (p, time, video) => inv('library:thumb', p, time, video),
  pickFolders: () => inv('pick:folders'),
  importFiles: () => inv('pick:import'),
  trash: (ps) => inv('file:trash', ps),
  info: (p) => inv('file:info', p),
  saveCopy: (d) => inv('file:save', d),
  reveal: (p) => inv('file:reveal', p),
  onFocus: (cb) => ipcRenderer.on('app:focus', () => cb()),
  platform: process.platform
});
