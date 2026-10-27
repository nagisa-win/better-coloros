# 功能技术设计：LSPosed 模块自激活状态检测 (Module Activation Detection)

## 1. 需求背景与用户痛点
- **痛点描述**：
  用户在 LSPosed 管理器中已经勾选并启用了 Better ColorOS 模块，且实际的「相机文件名规范化」与「相册复制到剪贴板」功能均已在目标应用中正常生效，但在打开 Better ColorOS 应用界面时，顶部状态卡片依然显示「模块未激活，请在 LSPosed 管理器中启用」。
- **解决价值**：
  提供准确、可靠的模块激活自检状态，避免给用户造成“模块没有生效”的困惑与误导，同时提供清晰的作用域配置说明。

---

## 2. 目标作用域与进程
- **目标包名**：`win.nagisa.bettercoloros`
- **目标进程**：模块自身的主进程（UI 进程）
- **LSPosed 作用域**：`win.nagisa.bettercoloros` 必须声明在 `arrays.xml` 的 `xposed_scope` 中，并在 LSPosed 管理器中被勾选，LSPosed 框架才会向模块自身的进程注入 Hook。

---

## 3. 根因剖析 (Root Cause Analysis)

### 3.1 Kotlin Companion 编译字节码与反射 Hook 目标不一致 (核心主因)
在 Kotlin 中声明伴生对象方法：
```kotlin
companion object {
    @JvmStatic
    fun isModuleActive(): Boolean = false
}
```
Kotlin 编译器会生成两处入口：
1. `MainActivity.isModuleActive()`（Java 互操作的静态桥接方法）。
2. `MainActivity$Companion.isModuleActive()`（伴生对象实例上的虚拟方法）。

在 Compose 代码中直接调用 `isModuleActive()`（或在 lambda 中）：
```kotlin
setContent {
    BetterColorOSTheme {
        BetterColorOSApp(isModuleActive = isModuleActive())
    }
}
```
经 `dexdump` 反汇编查明，ART 虚拟机执行的实际指令为：
```smali
sget-object v0, MainActivity.Companion
invoke-virtual {v0}, MainActivity$Companion.isModuleActive:()Z
```
而先前的 `MainHook.kt` 仅 Hook 了 `MainActivity` 类本身上的方法：
`findAndHookMethod("win.nagisa.bettercoloros.ui.MainActivity", ..., "isModuleActive", ...)`。
因此，运行时实际执行的 `MainActivity$Companion.isModuleActive()` **从未被 Hook 拦截**，永远返回了硬编码的 `false`！

### 3.2 LSPosed (Zygisk) 进程作用域隔离机制
现代 LSPosed 采用白名单机制，只有在模块的作用域（Scope）中勾选的包名，LSPosed 才会将该模块注入进对应的应用进程。
如果用户只勾选了 `com.oplus.camera` 与 `com.coloros.gallery3d`，未勾选 `win.nagisa.bettercoloros` 自身，LSPosed 根本不会在 `win.nagisa.bettercoloros` 进程中加载 `MainHook`。

---

## 4. 解决方案与纵深拦截设计

### 4.1 全链路多层级 Hook 拦截 (Triple-Hook)
在 `MainHook.kt` 的模块自检分支中，对所有可能的调用路径全部实施方法替换：
1. **静态桥接拦截**：Hook `win.nagisa.bettercoloros.ui.MainActivity.isModuleActive`
2. **伴生对象虚拟方法拦截**：Hook `win.nagisa.bettercoloros.ui.MainActivity$Companion.isModuleActive`
3. **Activity 实例方法拦截**：Hook `win.nagisa.bettercoloros.ui.MainActivity.isModuleActivated`

三处均直接替换返回值为 `true` (`XC_MethodReplacement.returnConstant(true)`)。

### 4.2 UI 友好提示兜底
在 `StatusHeroCard` 中，当状态检测为未激活时，除了告知用户前往 LSPosed 启用外，增加补充说明：
“💡 提示：若已在 LSPosed 中激活并勾选相机/相册，实际功能已生效；在作用域中同时勾选「Better ColorOS」自身即可点亮激活状态。”

---

## 5. 异常保护与边界条件
- 每一处 `findAndHookMethod` 均独立使用 `runCatching` 包裹，任何一处失败不会中断其他拦截点。
- 即使未在 LSPosed 中勾选自身，也不影响相机与相册核心 Hook 的独立运行。
