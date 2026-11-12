# News

## 0.3.3-beta

### Added

- **Automatic update check**: the mod now asks Modrinth for the newest published version after the texture update finishes, and tells you when a newer one exists. It is on by default and can be turned off with the "Check for Updates" option in the config
- The notice is a small panel in the top-right corner of the screen. It stays out of your way — you can keep playing and every other click still reaches the game — and it is only dismissed when you choose one of its three buttons
- **Download** opens the mod page, **Later** hides the notice for 3 days, and **Skip** never shows that particular version again (a newer release is still announced)

### Fixed

- The progress bars for individual downloads were hidden behind the main "Downloading Mods" bar, so you could only see the overall step; the file being downloaded is now shown on its own bar above it
- A failed download no longer leaves a stale progress bar behind for the rest of the startup

## 0.3.2-beta

### Added

- **Startup result toast**: when the texture update recorded any warnings or errors, the first title screen now shows a toast telling the player how many occurred and to check the logs for details
- **Browse archived logs**: the log screen has a new "History" button that lists previous archived logs (newest first); picking one decompresses it and displays it with the same viewer, with the temporary file removed on close
- While viewing an archived log, the History and Clear Archive buttons are hidden so screens cannot nest

## 0.3.1-beta

### Fixed

- Fixed a jar-in-jar **okio version conflict** ([#2](https://github.com/xuyife/ae2_texture_updater/issues/2)): the mod's embedded `okio` range was too narrow, so on modpacks where another mod shipped a newer `okio` (e.g. Arcana's 3.16.4) Forge's JarSelector failed to resolve a common version and the whole contained-jar resolution aborted — which surfaced as a long chain of "missing dependency" errors for unrelated mods (Create, Artifacts, PuzzlesLib, etc.)
- The declared `okio` range is now widened to `[3.9.0,4.0)` (okio 3.x stays binary-compatible), letting Forge pick whatever higher 3.x version another mod provides
- Also dropped an orphan ~350 KB `okio 3.6.0` jar that okhttp pulled in transitively but was never declared in `metadata.json`; the distributed jar is now ~1.3 MB with only `okhttp` and `okio`

## 0.3.0-beta

### GUI Repair Layer (new)

- Added a **GUI repair layer** that downloads a community-maintained, pre-fixed GUI resource pack (AE-Light-UI Multi-module) and merges it into the generated pack, fixing screens that were misaligned or failed to open on 1.20.1
- The layer only keeps matching installed mod namespaces, whitelists `screens/` and `textures/`, and drops `textures/block` and `textures/item` (block/item textures stay from 1.20.1)
- Removed the 1.21-layer's own GUI subtrees that broke or misaligned screens on 1.20.1
- New "GUI Repair Layer" config section: enable/disable, download source (Modrinth auto-latest or direct URL), project ID
- Download uses the shared network settings (threads, speed limit, proxy); failures fall back to the cached copy and never block the pipeline
- GUI pack is cached under `cache/ae2_texture_updater/gui_pack/`, checksum-verified
- Bundled GUI assets are credited to AE-Light-UI by LeeQianXi (LGPL-3.0) in the generated pack.mcmeta

### Other changes

- Optimized code structure and cleaned up unused classes/methods
- Removed unused message keys and their lang entries

## 0.2.0-beta

- Switched from bundled Kotlin (jar-in-jar) to Kotlin for Forge as a required dependency; the distributed jar shrinks from ~5 MB to ~1.5 MB, and fix some compatibility issues
- Optimize code structure