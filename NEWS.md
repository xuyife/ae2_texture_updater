# News

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