# AE2 Texture Updater

[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

[简体中文](./README_zh.md)

---

> ⚠️ **Beta Notice**  
> This is an early beta release. Core features are functional, but development is still ongoing.  
> If you encounter any issues, please report them via the feedback channels below. Your support is greatly appreciated! 🙏

---

## 📖 What is this?

**AE2 Texture Updater** is a client-side mod that automatically downloads the latest **1.21** textures for AE2 (Applied Energistics 2) and its addons from CurseForge / Modrinth, and generates a ready-to-use resource pack for you. Enjoy the fresh new look of AE2 in **1.20.1**!

> **In a nutshell**: Brings AE2's 1.21 textures back to 1.20.1, automatically.

---

## ✨ Features

- ✅ **Automatic Download**: Fetches the latest 1.21 textures for AE2 and its addons on startup
- ✅ **Smart Adaptation**: Automatically handles ModID mappings so textures load correctly
- ✅ **One-Click Pack Generation**: Automatically packages into a resource pack and places it in `resourcepacks/`
- ✅ **In-Game Log Viewer**: View download progress and errors from inside the game (click the "Logs" button in the config screen)
- ✅ **Multi-threaded Download**: Customizable thread count to make full use of your bandwidth

---

## ⚙️ Configuration

After installing the mod, a config file will be generated at: `.minecraft/config/ae2_texture_updater.json`

It's recommended to use the **Cloth Config** GUI for a more intuitive experience.

Click the “AE2TU” button on the main menu to open the config screen.

---

## 🚀 How to Use

1. **Install the mod**: Place the JAR in `.minecraft/mods/`
2. **Launch the game**: The mod will automatically start downloading and generating the resource pack
3. **Reload resources**: Press `F3 + T` to reload resource packs, or restart the game
4. **Check progress**: Click the **"Logs"** button in the Cloth Config screen to view the download log

---

## 🛠️ Dependencies

| Mod | Description |
|-----|-------------|
| **Cloth Config API** | Required for the config GUI |

---

## ❓ FAQ

### Q: Downloads are very slow?
**A**: Try updating in the morning (better for Chinese networks). You can also increase the timeout and retry count in the config.

### Q: The resource pack isn't enabled automatically?
**A**: Go to **Options → Resource Packs** and manually enable `AE2_Texture_Updater.zip`.

### Q: Textures for a certain addon aren't working?
**A**: The mod may have changed its Mod ID in 1.21. Add the correct mapping in the **ModID Mapping** section.

### Q: How to download the texture pack manually?
**A**: The mod handles everything automatically – no manual action needed.

---

## 📜 License

This project is open-source under the **MIT License**.

---

## 🙏 Acknowledgments

- [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) – The amazing mod that makes this possible!
- [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config) – Config GUI support
- [OkHttp](https://github.com/lysine-dev/okhttp) – HTTP client
- [Gson](https://github.com/google/gson) – JSON parsing

---

## 📬 Feedback & Support

- 💬 **GitHub Issues**: [https://github.com/xuyife/ae2_texture_updater/issues](https://github.com/xuyife/ae2_texture_updater/issues)
- 📧 **Email**: [xuyifeiDev@outlook.com](mailto:xuyifeiDev@outlook.com)

---

**If you like this mod, don't forget to give it a ⭐ Star on GitHub!**