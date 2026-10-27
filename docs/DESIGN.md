# 🎨 DESIGN.md - Material You 与多端响应式设计规范

本项目全盘采用 **Material Design 3 (Material You)** 设计规范，致力于为 ColorOS 用户提供与原生系统水乳交融、浑然一体的现代 UI 体验。

---

## 1. 核心设计原则

1. **Material You 动态取色 (Dynamic Color)**：
   - 在 Android 12+ (ColorOS 12+) 设备上，全面启用系统壁纸 Monet 动态调色盘（`dynamicLightColorScheme` / `dynamicDarkColorScheme`）。
   - 界面主色调、卡片背景、高亮指示芯片会自动跟随用户的系统壁纸与主题色演变，呈现和谐自然的视效。
2. **纯净无感、无关闭开关负担**：
   - 核心系统优化功能（相机重命名、相册复制）在模块激活后默认随之生效，不给用户堆砌冗余的二级细项开关，仅展示状态徽章与详细说明。
3. **豆腐块布局与可折叠扩展 (Tile & Expandable Pattern)**：
   - 功能卡片采用圆角优雅（`RoundedCornerShape(20.dp)`）的豆腐块形式呈现。
   - 默认折叠或展开关键内容，点击箭头或整卡可展开查看即时测试器、操作示意与原理剖析。

---

## 2. 多端设备响应式排版指南

为完美适配 **普通直板手机**、**折叠屏内屏展开态** 以及 **平板电脑**，UI 采用 `BoxWithConstraints` 动态断点计算：

```
                    窗口宽度 (maxWidth)
      0dp ────────────── 600dp ────────────── 840dp ───────────────>
       │                   │                    │
   [Compact 紧凑型]    [Medium 中等型]       [Expanded 扩展型]
   (手机竖屏)          (折叠屏展开态 / 横屏) (平板电脑 / 桌面大屏)
       │                   │                    │
   单列流式卡片纵向排布   双列豆腐块并排网格布局   限制最大宽度 960dp 并居中
```

### 2.1 手机端 (Compact: width < 600dp)
- 单列竖向连续滚动布局。
- 水平边距设为 `16.dp`，垂直元素间距设为 `16.dp`。
- 功能卡片与状态看板占满屏幕宽度。

### 2.2 折叠屏内屏展开态 (Medium: 600dp ≤ width < 840dp)
- 顶部状态看板横向贯穿。
- 下方功能卡片（如「相机重命名」与「相册复制」）采用 **双列豆腐块网格并排排列**（`Row(weight(1f))`）。
- 水平边距扩展至 `24.dp`。

### 2.3 平板电脑端 (Expanded: width ≥ 840dp)
- 容器启用最大宽度限制（`Modifier.widthIn(max = 960.dp).align(Alignment.TopCenter)`），避免在大尺寸屏幕上卡片横向过度拉伸导致视线发散。
- 双列豆腐块自适应居中，留白舒适。

---

## 3. 组件视觉规范

| 组件 | 对应 Composable | 规范要求 |
| :--- | :--- | :--- |
| **状态大看板** | `StatusHeroCard` | 圆角 `24.dp`，激活时背景为柔和主色半透明，未激活时为柔和浅红。内部包含大状态图标与一键重启按钮。 |
| **功能豆腐块** | `FeatureTile` | 圆角 `20.dp`，容器使用 `surfaceContainerLow`，标题右侧附带状态微标芯片（如 `默认开启`）。 |
| **操作输入框** | `OutlinedTextField` | 圆角 `12.dp`，单行，支持实时响应输出。 |
| **代码/结果展示** | `Surface` | 等宽字体（`FontFamily.Monospace`），绿色加粗高亮，圆角 `10.dp`。 |
| **确认对话框** | `AlertDialog` | 符合 Material 3 标准弹窗，圆角 `28.dp`，强调风险与恢复操作指引。 |

---

## 4. 亮色与暗色模式自适应

- **色彩对比度**：禁止直接使用纯硬编码色值（如 `#FFFFFF` 或 `#000000`）。所有文本与表面必须引用语义化色值：
  - 强调文字：`MaterialTheme.colorScheme.onSurface`
  - 次要提示：`MaterialTheme.colorScheme.onSurfaceVariant`
  - 容器表面：`MaterialTheme.colorScheme.surfaceContainer`
  - 成功高亮：`SuccessGreen`
  - 危险警告：`ErrorRed`
- 系统进入深色模式时，Surface 色彩自动平滑降噪，确保暗光环境下不刺眼。
