# Gllry for Windows

The same Gllry UI (OLED black, glass, M3-Expressive motion) as the Android app, built with Electron.

## Get the .exe without a PC (GitHub Actions)
1. Put this folder in your Gllry repo as `windows/` and copy `.github/workflows/windows.yml` to the repo's `.github/workflows/`.
2. `git add . && git commit -m "Windows app" && git push`
3. Tag a release:  `git tag win-v2.1 && git push origin win-v2.1`
4. Actions builds it (about 5 minutes). Download `Gllry-Setup-2.1.0.exe` (installer) or `Gllry-2.1.0-portable.exe` from **Releases** (or from the run's *Artifacts*).

## Run / build on a Windows PC
```
cd windows
powershell -ExecutionPolicy Bypass -File get-fonts.ps1
npm install
npm start          # run
npm run dist       # build the installer + portable exe into dist/
```

## How it works
* Scans **Pictures** and **Videos** (and any folder you add with **+ -> Add folder...**), including sub-folders.
* **+ -> Import photos & videos...** saves a copy into `Pictures\Gllry` / `Videos\Gllry`.
* Archive hides a photo inside Gllry only. **Delete** moves the file to the Recycle Bin (asks first).
* The editor always saves a **new copy** to `Pictures\Gllry`.
* Hold the **Gllry** title for 3 seconds for the Stack style. In Stack: scroll / drag / Up-Down keys to move, click = overview, scroll past the last photo = pile, X exits, **Make default** starts Gllry in this style.

## Keys
Viewer: Left/Right move, A or Up archive, Delete or Down delete, I details, E edit, Esc close.
Home: Ctrl+F search, Esc clear.

## Notes
* HEIC/HEIF photos can't be shown by Chromium; JPG, PNG, WebP, GIF, BMP, AVIF and MP4/WebM/MOV videos work.
* Photo dates use the file's modified time. Selfies = any folder named "selfie(s)", otherwise your newest portrait photos.
