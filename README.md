# Better ColorOS 体验增强工具箱

[![Build & Release Android APK](https://github.com/nagisa-win/better-coloros/actions/workflows/build-release.yml/badge.svg)](https://github.com/nagisa-win/better-coloros/actions/workflows/build-release.yml)
[![GitHub Release](https://img.shields.io/github/v/release/nagisa-win/better-coloros?color=blue&logo=github)](https://github.com/nagisa-win/better-coloros/releases)
[![License](https://img.shields.io/badge/License-GPL%20v3-green.svg)](LICENSE)

专为 **OPPO / 一加 (ColorOS)** 用户量身打造的 **LSPosed (Zygisk)** 模块化体验优化工具箱。  
遵循现代 **Material Design 3 (Material You)** 设计语言，融合多项深度系统级 Hook 优化，并适配直板手机、折叠屏及平板电脑。

---

## ✨ 核心特性一览

### 1. 📷 相机文件名规范化 (Camera Filename Normalization)
- **解决痛点**：ColorOS 原生相机拍摄的照片与视频默认采用紧凑命名（`IMGYYYYMMDDHHmmss.ext`），缺少下划线分割，导致各大网盘、群晖 NAS、Immich 等工具通过正则自动按拍摄日期归档时频频解析失败。
- **规范转换**：
  - 标准拍照：`IMG20261027171025.jpg` ➔ **`IMG_20261027_171025.jpg`**
  - 高效/专业：`.heic` 与 `.dng` 格式自动规范化
  - 动态/电影：`MOD20261027171025.mp4` ➔ **`MOD_20261027_171025.mp4`**
  - 连拍与实况：保留 `_BURST01`、`_COVER` 等全部业务后缀
- **纵深防御**：采用 DexKit 动态扫描 + MediaStore 媒体库 + File I/O + MediaRecorder 五层拦截，兼具强鲁棒性与幂等性。

### 2. 🖼️ 系统相册复制到剪贴板 (Gallery Copy to Clipboard)
- **解决痛点**：系统自带相册浏览照片时，右上角三个点「更多」菜单缺少复制选项，无法像 iOS 或电脑一样直接复制图片进行跨端粘贴。
- **无缝注入**：在右上角 COUI 标准菜单中注入「复制到剪贴板」项。
- **跨端生产力**：使用原生 MediaStore Content URI，无二次压缩损失，完整保留 EXIF 与 HDR 数据。配合 **KDE Connect**、**LocalSend** 或微信/QQ，手机端点击复制，电脑端直接 **Ctrl+V / ⌘+V** 即可瞬间粘贴原图！

### 3. 🎨 现代 Material You 界面与多端自适应
- **动态取色 (Monet Engine)**：自动拾取手机壁纸主色与强调色，完美适配系统亮色与暗色模式。
- **响应式豆腐块布局 (Responsive Tiles)**：
  - **手机直板端**：精细的单列流式卡片，优雅直观；
  - **折叠屏展开态 / 平板端**：自动切换为**双列并排豆腐块网格布局**，并限制最大宽度居中排版，大屏体验舒适协调。
- **内置即时转换测试器**：可在卡片中任意输入测试文件名，毫秒级实时预览规范化结果。

### 4. 👁️ 桌面启动器图标随心显隐
- 支持在设置中一键隐藏桌面图标，避免占用主屏幕空间。
- 隐藏后依然完全支持从 **LSPosed 管理器** 随时重新唤起设置界面，并可随时恢复显示。

---

## 📲 安装与启用步骤

### 前置条件
- 已通过 **Magisk**、**KernelSU** 或 **APatch** 获得 Root 权限
- 已安装并启用 **LSPosed**（推荐 Zygisk 模式）

### 激活步骤
1. 前往 [Releases 页面](https://github.com/nagisa-win/better-coloros/releases) 下载最新的 `better-coloros-release.apk` 并安装。
2. 打开 **LSPosed 管理器**：
   - 在模块列表中找到 **Better ColorOS** 并开启总开关；
   - 确认作用域（Scope）勾选了 **「相机」 (`com.oplus.camera`)** 与 **「相册」 (`com.coloros.gallery3d`)**。
3. 在系统多任务管理中**彻底结束相机与相册进程**（或在 Better ColorOS 主界面中点击「一键重启相机与相册」）。
4. 重新打开相机拍照验证文件名，或打开相册点开大图查看右上角菜单中的「复制到剪贴板」！

---

## 🤖 Harness 工程体系与 Agent 规范

本项目严格建立了 **Harness 工程化协作机制**，任何开发者与 AI Agent 在维护与扩展代码库时均遵循标准 SOP：

- **顶层入口**：[AGENTS.md](AGENTS.md)
- **开发前必读**：[docs/STARTUP.md](docs/STARTUP.md)（项目背景、架构分层、约束边界与共享库）
- **UI 设计规范**：[docs/DESIGN.md](docs/DESIGN.md)（Material You、响应式断点、亮暗色适配）
- **功能方案沉淀**：新需求必须先在 [docs/features/](docs/features/) 下编写设计文档后方可编码
- **提交与安全审计**：[docs/COMMIT.md](docs/COMMIT.md)（规范提交、严禁敏感信息泄露）

---

## 🏗️ 自动化构建与 CI/CD

本项目配置了完整的 GitHub Actions 工作流：
- 代码推送到 `main` 分支或发布 Tag 时，云端自动运行单元测试、打包签名 Release 与 Debug APK，并发布 GitHub Releases。
- 无须在本地配置复杂的 Android SDK 编译环境。

---

## 📄 开源许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 开源。
致敬与感谢：
- [OPCameraPro](https://github.com/Weverses/OPCameraPro) - 注入思路与底层 DexKit 实践参考
- [coloros-gallery-copy](https://github.com/nagisa-win/coloros-gallery-copy) - 相册复制方案来源
- [DexKit](https://github.com/LuckyPray/DexKit) & [EzXHelper](https://github.com/KyuubiRan/EzXHelper)
