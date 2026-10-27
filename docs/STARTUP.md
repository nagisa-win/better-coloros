# 🚀 STARTUP.md - 项目架构与开发指南

> **重要准则**：在开始任何编码之前，请仔细阅读本指南以了解项目的全局架构、现有基础设施和设计约束。

---

## 1. 项目背景与目标

ColorOS 系统在功能丰富的原生体验下，存在一些细节痛点：
1. **相机照片/视频缺少标准下划线命名**：生成的 `IMG20261027171025.jpg` 缺少下划线分割，导致各类网盘/NAS 照片整理工具在解析拍摄日期时出错。
2. **系统相册无法直接复制图片到剪贴板**：浏览相片时缺少系统级复制选项，无法搭配跨端工具（如 KDE Connect / 微信 / QQ）进行即时粘贴。
3. **桌面图标冗余**：LSPosed 模块往往常驻桌面，影响极简主义用户的桌面布局。

**Better ColorOS** 将上述功能合而为一，建立起一套高内聚、易扩展、规范化的 LSPosed 优化套件。

---

## 2. 系统架构分层

本项目分为四层架构：

```
┌────────────────────────────────────────────────────────┐
│                   UI 层 (Material You)                  │
│  - Jetpack Compose + Dynamic Colors (Monet)            │
│  - 响应式多端布局 (手机单列 / 折叠屏与平板双列豆腐块)        │
└────────────────────────────────────────────────────────┘
                           │
┌────────────────────────────────────────────────────────┐
│                   Hook 业务层                           │
│  - CameraFilenameHook (五层纵深防御拦截文件名)            │
│  - GalleryMenuHook (COUIPopupListWindow 注入选项)       │
│  - PhotoDataExtractor (相片索引与 Uri/路径精准获取)      │
│  - ClipboardHelper (系统级 ClipData 与 URI 跨进程授权)   │
└────────────────────────────────────────────────────────┘
                           │
┌────────────────────────────────────────────────────────┐
│                   公共共享层 (common/)                  │
│  - DexKitHelper (DEX 字节码特征动态检索，对抗混淆)        │
│  - FilenameNormalizer (正则规范化引擎)                  │
│  - LauncherIconManager (ActivityAlias 动态显隐)         │
│  - ProcessHelper (Root/Shell 进程管理与生效辅助)         │
└────────────────────────────────────────────────────────┘
                           │
┌────────────────────────────────────────────────────────┐
│                   底层框架支持                          │
│  - LSPosed / Zygisk (Xposed API 82)                    │
│  - EzXHelper 2.2.0                                     │
│  - DexKit 2.0.6 (arm64-v8a native)                     │
└────────────────────────────────────────────────────────┘
```

---

## 3. 共享工具类库清单 (严格禁止重复造轮子)

所有新特性在需要类似功能时，**必须直接复用以下公共类**：

### 3.1 `win.nagisa.bettercoloros.common.DexKitHelper`
- **定位**：DexKit 实例管理组件。
- **用途**：当目标系统 App（如 `com.oplus.camera` 或 `com.coloros.gallery3d`）采用 ProGuard 混淆后，类名和方法名在每个系统 OTA 版本都会变化（如变更为 `a.b.c.d()`）。
- **用法**：通过查找字符串常量、方法签名或操作码动态获取目标 `Method` / `Class`。
- **示例**：
  ```kotlin
  val bridge = DexKitHelper.bridge ?: return
  val methods = bridge.findMethod {
      matcher { usingStrings("target_feature_string") }
  }
  ```

### 3.2 `win.nagisa.bettercoloros.common.FilenameNormalizer`
- **定位**：文件名正则匹配与规范化转换。
- **特性**：
  - 自动识别 `IMG`、`VID`、`MOD` 前缀；
  - 提取 8 位年月日与 6 位时分秒，重构为 `IMG_YYYYMMDD_HHmmss`；
  - 完整保留 `_BURST01`、`_COVER`、`_SLOW` 后缀；
  - 严格具备幂等性（已经是标准格式的不做二次处理）。

### 3.3 `win.nagisa.bettercoloros.common.LauncherIconManager`
- **定位**：桌面启动器图标显隐控制。
- **实现原理**：通过 `packageManager.setComponentEnabledSetting` 切换 `LauncherAlias` 别名状态。
- **安全保障**：应用主 Activity 保留 `action.MAIN`，因此即使桌面图标隐藏，LSPosed 模块管理器依然能够直接打开模块界面。

### 3.4 `win.nagisa.bettercoloros.common.ProcessHelper`
- **定位**：快速重启目标进程。
- **用法**：调用 `ProcessHelper.restartTargets(context)`，尝试获取 Root 权限执行 `am force-stop`，无法获取则弹出友好提示引导用户在系统多任务中强行停止应用。

---

## 4. Hook 编码铁律与安全限制

1. **绝对防崩原则 (Fail-Safe)**：
   Hook 运行在目标宿主进程（如系统相机、相册）内部。**任何未捕获的异常都会导致系统应用崩溃闪退**。每个 Hook 点的方法内部必须使用 `try-catch` 包裹，并在失败时静默退出或打印 `XposedBridge.log`，绝不能影响宿主本身的基础功能。
2. **零常驻、零后台原则**：
   模块不得注册无意义的前台服务、常驻广播接收器或后台守护线程。只有在用户触发具体功能（拍摄、打开菜单）时瞬间执行。
3. **严格禁止硬编码混淆类名**：
   严禁在代码中写死类似 `XposedHelpers.findClass("com.oplus.camera.abc.x.y.z")` 这类易随版本更新而失效的代码，必须使用 DexKit 特征搜索。
4. **作用域声明一致性**：
   在 `app/src/main/res/values/arrays.xml` 的 `xposed_scope` 中声明的目标包名必须与 `MainHook.kt` 中的实际分发逻辑保持 100% 同步。
