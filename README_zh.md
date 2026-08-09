# AE2 Texture Updater

[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

[English](./README.md)

---

> ⚠️ **Beta 版本提示**  
> 此版本为早期测试版本，核心功能已完成，但仍在积极开发中。  
> 如果你遇到任何问题，请通过下方的反馈渠道告知我们，感谢你的支持！🙏

---

## 📖 这是什么？

**AE2 Texture Updater** 是一个客户端模组，它能自动从 CurseForge / Modrinth 下载 AE2（应用能源2）及其附属模组在 **1.21** 版本的最新材质，并为你生成一个可直接使用的资源包。让你在 **1.20.1** 也能享受新版本的精致外观！

> **一句话**：把 1.21 的 AE2 材质搬回 1.20.1，一键搞定。

---

## ✨ 功能特性

- ✅ **自动下载**：启动时自动获取 AE2 及附属模组的最新 1.21 材质
- ✅ **智能适配**：自动处理 ModID 映射，让材质正确加载
- ✅ **一键生成**：自动打包为资源包并放入 `resourcepacks/` 文件夹
- ✅ **日志查看器**：游戏内查看下载进度和错误信息（点击配置界面中的“日志”按钮）
- ✅ **多线程下载**：支持自定义线程数，充分利用带宽

---

## ⚙️ 配置说明

模组安装后会自动生成配置文件：`.minecraft/config/ae2_texture_updater.json`

推荐使用 **Cloth Config** 图形化界面进行配置（更直观）。

在游戏主界面上点击 “AE2TU” 按钮即可打开配置界面。

---

## 🚀 如何使用

1. **安装模组**：将 JAR 放入 `.minecraft/mods/` 文件夹
2. **启动游戏**：模组会自动开始下载并生成资源包
3. **重载资源**：按 `F3 + T` 重新加载资源包，或重启游戏
4. **查看进度**：在 Cloth Config 配置界面点击 **“日志”** 按钮查看下载日志

---

## 🛠️ 依赖

| 模组 | 说明 |
|------|------|
| **Cloth Config API** | 必需，用于配置界面 |

---

## ❓ 常见问题

### Q: 下载很慢怎么办？
**A**: 建议在早上（国内网络环境较好）进行更新。也可以在配置中调大超时时间和重试次数。

### Q: 资源包没有自动启用？
**A**: 进入游戏后打开 **选项 → 资源包**，手动启用 `AE2_Texture_Updater.zip`。

### Q: 某个附属模组的材质没有生效？
**A**: 可能是该模组在 1.21 中更改了 ModID。请在配置的 **ModID 映射** 中添加对应映射。

### Q: 需要手动下载材质包吗？
**A**: 不需要，模组会自动处理所有下载和打包工作。

---

## 📜 开源协议

本项目使用 **MIT 协议** 开源。

---

## 🙏 致谢

- [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) - 伟大的模组！
- [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config) - 配置界面支持
- [OkHttp](https://github.com/lysine-dev/okhttp) - HTTP 客户端
- [Gson](https://github.com/google/gson) - JSON 解析

---

## 📬 反馈与支持

- 💬 **GitHub Issues**: [https://github.com/xuyife/ae2_texture_updater/issues](https://github.com/xuyife/ae2_texture_updater/issues)
- 📧 **邮箱**: [xuyifeiDev@outlook.com](mailto:xuyifeiDev@outlook.com)

---

**如果觉得好用，别忘了给个 ⭐ Star 支持一下！**