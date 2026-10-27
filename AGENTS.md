# 🤖 AGENTS.md - Agent 开发规范与导航手册

欢迎来到 **Better ColorOS** 代码库！无论你是第一次接手本项目，还是正在为项目添加新特性，**在执行任何代码修改前，请务必完整阅读本手册**。

---

## 🎯 项目核心定位
**Better ColorOS** 是一套专为 OPPO / 一加 (ColorOS) 系统打造的模块化、高质量体验优化插件库，运行在 **LSPosed (Zygisk)** 框架下。
- **项目包名**：`win.nagisa.bettercoloros`
- **设计哲学**：轻量无常驻、原生级 Material You 融入、多端自适应（手机/平板/折叠屏）、纵深防御与高兼容性。

---

## 🔄 Agent 核心工作流 (Standard Operating Procedure)

每位 Agent 在开发时必须严格遵守以下五步闭环流程：

```
[步骤 1: 了解全局] ──> 阅读 docs/STARTUP.md 与 docs/DESIGN.md
       │
[步骤 2: 方案沉淀] ──> 在 docs/features/{feature-name}.md 撰写新功能技术设计
       │
[步骤 3: 共享优先] ──> 优先复用 common/ 下现有组件，拒绝重复造轮子
       │
[步骤 4: 规范实现] ──> 实现 Hook / UI，编写单元测试验证边界条件
       │
[步骤 5: 安全提交] ──> 阅读 docs/COMMIT.md，进行敏感信息自查后推送
```

### 1. 开发前必读 (Phase 1: Startup & Context)
- **[docs/STARTUP.md](docs/STARTUP.md)**：包含代码库技术架构、各模块分工、限制与边界条件、现有共享库清单。
- **[docs/DESIGN.md](docs/DESIGN.md)**：包含 Material 3 (Material You) 规范、亮暗色适配准则、手机/平板/折叠屏响应式排版要求。

### 2. 方案先于代码 (Phase 2: Architecture First)
- **严禁直接编写未经设计的 Hook 或 UI**。
- 若接收到新功能需求，必须首先在 `docs/features/{new-feature-description}.md` 中沉淀设计文档（可参考 [docs/features/README.md](docs/features/README.md) 的标准模板）。
- 文档需明确：
  - 目标应用包名与进程
  - 预期拦截点与 DexKit 动态扫描特征
  - 异常回退机制与安全性保障

### 3. 避免重复造轮子 (Phase 3: Reuse Shared Libraries)
所有功能必须优先使用 `win.nagisa.bettercoloros.common.*` 提供的基础设施：
- **`DexKitHelper`**：动态特征扫描引擎，禁止在代码中硬编码混淆类名（如 `com.oplus.camera.a.b.c`）。
- **`FilenameNormalizer`**：文件名正则表达式与规范化转换核心。
- **`LauncherIconManager`**：桌面图标动态显隐控制。
- **`ProcessHelper`**：目标进程管理与 Root 快速重启辅助。

### 4. UI 遵循功能集合模式 (Phase 4: UI Patterns)
- 界面必须采用 **Material 3 / Material You** 设计语言，启用 Dynamic Color（跟随系统壁纸取色）。
- 整体架构为**功能集合 / 工具箱**形态，采用**豆腐块布局 (Tiles)** 或 **可折叠卡片 (Expandable Cards)**。
- 手机端采用单列优雅流式排列，折叠屏展开态和平板端自动切换为**双列或多列豆腐块网格布局**。
- 新增功能卡片必须附带清晰的使用指引、状态徽章与即时测试器。

### 5. 提交前安全基线 (Phase 5: Commit & Security Check)
- 在每次提交前，必须查阅 **[docs/COMMIT.md](docs/COMMIT.md)**。
- **严格禁止任何密钥、密码、真实凭据、测试 Token、内部路径泄露至 Git 历史中**。
- 提交必须遵循语义化提交（Conventional Commits），如 `feat:`, `fix:`, `docs:`, `refactor:`。

---

## 📚 知识库速查索引

| 文档路径 | 核心内容 | 适用场景 |
| :--- | :--- | :--- |
| [AGENTS.md](AGENTS.md) | Agent 顶层入口规范与开发总则 | 任何 Agent 的起始入口 |
| [docs/STARTUP.md](docs/STARTUP.md) | 项目背景、架构分层、约束条件与共享类说明 | 每次开发前必读 |
| [docs/DESIGN.md](docs/DESIGN.md) | Material 3 设计语言、深浅色、响应式与折叠屏适配 | 编写与修改 UI 前必读 |
| [docs/COMMIT.md](docs/COMMIT.md) | Git 提交规范、敏感信息拦截检查清单、CI 流程 | 收尾提交代码前必读 |
| [docs/features/camera-rename.md](docs/features/camera-rename.md) | 相机文件名规范化 Hook 技术实现文档 | 参考现有 Hook 实现 |
| [docs/features/gallery-copy.md](docs/features/gallery-copy.md) | 相册复制到剪贴板 Hook 技术实现文档 | 参考现有 Hook 实现 |
| [docs/features/hide-launcher-icon.md](docs/features/hide-launcher-icon.md) | 隐藏启动器图标功能技术实现文档 | 参考桌面图标控制 |
