# 功能设计：隐藏启动器图标 (Hide Launcher Icon)

## 1. 需求背景与用户痛点
- **现状**：大部分 Xposed / LSPosed 模块安装后均会在手机桌面上生成启动器图标。对于已经配置好功能的用户，该图标常年不打开，且占用桌面空间。
- **目标**：在应用设置中提供「隐藏桌面启动器图标」开关。用户开启隐藏后，图标从桌面消失；用户随时可从 LSPosed 管理器中重新打开本应用，并可随时恢复桌面图标。

---

## 2. 技术方案与实现原理

### 2.1 AndroidManifest 架构设计与 LSPosed 定位原理
不能直接将主 Activity (`MainActivity`) 设置为 disabled，否则 LSPosed 模块管理器自身也会因为找不到可用 Activity 而无法通过模块详情打开设置。

更关键的是：**LSPosed 模块管理器内部通过 `AppHelper.getSettingsIntent()` 定位模块设置入口**。其底层查找逻辑为：
1. 优先查询携带 `android.intent.action.MAIN` 且带分类 `de.robv.android.xposed.category.MODULE_SETTINGS` 的 Activity；
2. 若未匹配，回退查询 `android.intent.category.INFO`；
3. 若仍未匹配，最后回退查询 `android.intent.category.LAUNCHER`。如果启动器别名被禁用且实体未声明前两者，LSPosed 将误判该模块“无设置界面”，从而**彻底移除齿轮图标与设置入口**！

**成熟的标准解决方案**：
- **实体 Activity** (`.ui.MainActivity`)：
  - 声明 `android.intent.action.MAIN`
  - 声明 `de.robv.android.xposed.category.MODULE_SETTINGS`（供 LSPosed 专属精准定位）
  - 声明 `android.intent.category.INFO`（供系统级 Manager 通用回退定位）
  - 不带 `category.LAUNCHER`（桌面启动器不展示实体）
- **桌面别名** (`.ui.LauncherAlias`)：
  - 指向 `MainActivity`
  - 声明 `android.intent.action.MAIN` 与 `android.intent.category.LAUNCHER`
  - 仅作为桌面桌面启动器检索的独立把手，开关关闭时仅把此别名置为 `COMPONENT_ENABLED_STATE_DISABLED`。

### 2.2 动态显隐控制 (`LauncherIconManager`)
```kotlin
context.packageManager.setComponentEnabledSetting(
    ComponentName(context, "win.nagisa.bettercoloros.ui.LauncherAlias"),
    if (visible) COMPONENT_ENABLED_STATE_ENABLED else COMPONENT_ENABLED_STATE_DISABLED,
    PackageManager.DONT_KILL_APP
)
```

### 2.3 防失联用户提示机制
- 当用户将开关切换为“隐藏”时，弹窗提示用户：
  > 隐藏桌面图标后，Better ColorOS 图标将从手机主屏幕消失。\n\n你随时可以打开「LSPosed 管理器 -> 模块 -> Better ColorOS」再次打开本设置界面。\n\n确定要隐藏吗？
- 用户点击「确定隐藏」后才真正调用底层 API 并持久化状态。
- 如果用户从 LSPosed 再次打开设置，开关可随时恢复打开，即时恢复桌面图标。
