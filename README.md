# OPPO 相机文件名规范化 (oppo-camera-normalization)

[![Build & Release Android APK](https://github.com/nagisa-win/oppo-camera-normalization/actions/workflows/build-release.yml/badge.svg)](https://github.com/nagisa-win/oppo-camera-normalization/actions/workflows/build-release.yml)
[![GitHub Release](https://img.shields.io/github/v/release/nagisa-win/oppo-camera-normalization?color=blue&logo=github)](https://github.com/nagisa-win/oppo-camera-normalization/releases)
[![License](https://img.shields.io/badge/License-GPL%20v3-green.svg)](LICENSE)

专为 **OPPO / 一加 (ColorOS)** 用户打造的 **LSPosed (Zygisk)** Xposed 模块。  
用于将原生相机拍摄的照片与视频从缺少下划线的非标准命名（如 `IMG20261027171025.jpg` / `VID20261027171025.mp4`）**自动规范化为带下划线的 Android 行业标准格式**（`IMG_20261027_171025.jpg` / `VID_20261027_171025.mp4`）。

---

## 📌 痛点背景

当前 ColorOS / OPPO 原生相机在保存照片和录像时，默认采用连贯紧凑的无下划线格式：
- **现存命名**：`IMG20261027171025.jpg`、`VID20261027171025.mp4`
- **行业通用标准**：`IMG_20261027_171025.jpg`、`VID_20261027_171025.mp4`（如 AOSP 原生、Google Pixel、三星、索尼等）

这种格式差异导致大量第三方照片整理工具、NAS 同步应用（如群晖 Synology Photos、Nextcloud、Immich）以及百度网盘/阿里网盘在通过正则表达式按年月日时分秒解析归档照片时无法正确识别时间戳。

**本模块通过在底层拦截相机文件生成逻辑，从根源上将照片与视频存储为标准带下划线格式。**

---

## 🌟 核心特性与命名对照

### 1. 完整覆盖各类照片与视频格式
| 媒体类型 | 原始非标准命名 | 模块规范化后标准命名 |
| :--- | :--- | :--- |
| **标准拍照 (JPEG)** | `IMG20261027171025.jpg` | `IMG_20261027_171025.jpg` |
| **高效率格式 (HEIC)** | `IMG20261027171025.heic` | `IMG_20261027_171025.heic` |
| **专业模式 (RAW/DNG)** | `IMG20261027171025.dng` | `IMG_20261027_171025.dng` |
| **连拍照片 (Burst)** | `IMG20261027171025_BURST01.jpg` | `IMG_20261027_171025_BURST01.jpg` |
| **实况照片封面 (Cover)** | `IMG20261027171025_COVER.jpg` | `IMG_20261027_171025_COVER.jpg` |
| **普通录像 (MP4)** | `VID20261027171025.mp4` | `VID_20261027_171025.mp4` |
| **慢动作视频 (Slow-mo)** | `VID20261027171025_SLOW.mp4` | `VID_20261027_171025_SLOW.mp4` |

### 2. 五层纵深防御拦截架构
参考 [OPCameraPro](https://github.com/Weverses/OPCameraPro) 的现代注入理念，构建多层拦截保障：
1. **DexKit 动态特征扫描**：利用 DexKit 动态扫描 `com.oplus.camera` 内部与 `yyyyMMddHHmmss`、`IMG`、`VID` 相关的核心私有/混淆方法，直接在调用源头进行返回值重写。
2. **ContentValues 字段拦截**：拦截存入系统 MediaStore 的 `_display_name`、`title`、`_data` 等字段，确保系统相册数据库完全一致。
3. **ContentResolver 插入兜底**：拦截 `ContentResolver.insert()` 与 `update()` 操作，防止部分异步存储逻辑漏拦截。
4. **底层 File 与 FileOutputStream 拦截**：在 Java I/O 层拦截 `new File(...)` 与输出流构造，确保存储介质上物理落盘的文件名也是标准的带下划线格式。
5. **MediaRecorder 视频路径拦截**：拦截系统录像模块 `MediaRecorder.setOutputFile()`，确保拍摄的 MP4 视频路径规范。

### 3. 高稳定性与安全性
- **严格幂等性**：已经是标准带下划线命名的文件自动跳过，绝无二次重复添加下划线的问题。
- **零耗电、零性能开销**：仅在按下快门和生成文件的瞬间执行正则替换，不占用后台驻留资源。
- **自带图形化控制台**：
  - 启动器自带桌面图标，打开即可直观查看 LSPosed 模块激活状态。
  - 内置「规则即时测试工具」，可输入任意文件名实时预览转换效果。

---

## 📲 安装与使用指南

### 前置条件
- 已通过 **Magisk** 或 **KernelSU** / **APatch** 获取 Root 权限
- 已安装并启用 **LSPosed** 框架（Zygisk 版推荐）

### 使用步骤
1. 进入 [Releases 页面](https://github.com/nagisa-win/oppo-camera-normalization/releases) 下载最新版的 `oppo-camera-normalization-release.apk`。
2. 安装 APK 到手机。
3. 打开 **LSPosed 管理器**：
   - 在模块列表中找到 **OPPO 相机文件名规范化** 并开启开关。
   - 在模块的作用域（Scope）中勾选 **「相机」 (`com.oplus.camera`)**（以及相册 `com.coloros.gallery3d`）。
4. 在多任务界面**强制停止相机应用**（或进入设置 -> 应用管理 -> 相机 -> 强行停止）。
5. 重新打开相机拍摄任意一张照片或录制一段视频，前往文件管理查看，文件名已变为标准的 `IMG_YYYYMMDD_HHmmss.ext` 格式！

---

## 🛠️ 项目结构

```
oppo-camera-normalization/
├── .github/workflows/
│   └── build-release.yml                 # GitHub Actions 自动化编译与 Release 流程
├── app/
│   ├── build.gradle.kts                  # 模块 Gradle 脚本 (配置 DexKit 与 Xposed API)
│   ├── proguard-rules.pro
│   ├── signing/
│   │   └── common.jks                    # 通用签名密钥 (方便 CI 直接打出有效 APK)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # 声明 LSPosed 模块入口与元数据
│       │   ├── assets/
│       │   │   └── xposed_init           # Xposed 模块入口声明
│       │   ├── java/win/nagisa/oppocameranormalization/
│       │   │   ├── MainHook.kt           # Xposed 入口分发类
│       │   │   ├── hook/
│       │   │   │   ├── BaseHook.kt
│       │   │   │   └── CameraFilenameHook.kt # 五层纵深防御文件名拦截核心实现
│       │   │   ├── ui/
│       │   │   │   └── MainActivity.kt   # 状态检测与即时测试 UI
│       │   │   └── util/
│       │   │       ├── DexKitHelper.kt   # DexKit 动态 DEX 扫描封装
│       │   │       └── FilenameNormalizer.kt # 文件名规范化与正则匹配引擎
│       │   └── res/                      # Material3 界面资源
│       └── test/java/win/nagisa/oppocameranormalization/
│           └── FilenameNormalizerTest.kt # 单元测试用例
├── build.gradle.kts                      # 顶层 Gradle 构建脚本
└── settings.gradle.kts
```

---

## 🏗️ 编译说明

本项目配置了完整的 **GitHub Actions CI/CD**：
- 每当向 `main` 分支提交代码或推送 Tag（如 `v1.0.0`）时，GitHub Actions 会自动在 Ubuntu 云端环境中运行单元测试、编译出 Debug 与 Release 两个版本的 APK，并自动发布 GitHub Release。
- 本地无须配置 Android SDK / NDK 开发环境。

---

## 📄 开源许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 协议开源。
致敬与感谢：
- [OPCameraPro](https://github.com/Weverses/OPCameraPro) - 注入点思路与 DexKit 实践参考
- [DexKit](https://github.com/LuckyPray/DexKit) - 强大的运行时 DEX 动态扫描工具
- [EzXHelper](https://github.com/KyuubiRan/EzXHelper) - 优雅的 Xposed 开发框架
