# 功能设计：相册复制到剪贴板 (Gallery Copy to Clipboard)

## 1. 需求背景与用户痛点
- **现状**：ColorOS 原生相册（`com.coloros.gallery3d`）在单张大图浏览界面的右上角「更多」菜单中，提供了壁纸、重命名、设为私密等操作，但**没有将图片复制到系统剪贴板**的选项。
- **痛点**：跨设备同步（如通过 KDE Connect、LocalSend）、电脑端即时粘贴图片、微信/QQ 对话框快速发图时，用户必须先进行繁琐的系统分享，无法实现直接 Ctrl+V / ⌘+V 粘贴图片的高效流转。
- **目标**：在右上角「更多」菜单标准 COUI 弹窗末尾动态注入「复制到剪贴板」，点击后以无损二进制图片 `ClipData` 存入系统剪贴板，适配全版本 ColorOS（包含 Android 10 ~ Android 16/17）。

---

## 2. 目标作用域与进程
- **宿主包名**：`com.coloros.gallery3d`
- **进程名**：`com.coloros.gallery3d` 主进程

---

## 3. Hook 方案与技术实现

### 3.1 UI 注入层 (`GalleryMenuHook`)
- 拦截 ColorOS 基础 UI 库：`com.coui.appcompat.poplist.COUIPopupListWindow.setItemList(List)`。
- 通过特征标识（包含“壁纸”、“详细信息”、“重命名”等菜单项）判断当前是否为相片浏览菜单。
- 动态构造 `PopupListItem("复制到剪贴板", true)` 追加至列表末端。
- 代理 `setOnItemClickListener`，拦截我们新增项的点击事件：
  - 先执行 `PhotoDataExtractor.handleCopy(window)` 提取数据并复制，再调用 `window.dismiss()`，防止弹窗提早关闭导致 AnchorView / Context 被置空；
  - 阻断新增项触发原始相册未处理该 ID 引发的越界或空指针异常。

### 3.2 数据提取层 (`PhotoDataExtractor`) - 双轨兼容架构
由于 ColorOS 升级（新旧版本相册架构差异），采取双轨追踪策略：
1. **旧版相册 (ColorOS 11~14)**：
   - 监听 `PhotoItemViewData` 构造函数 `(dud, position, ...)`，维护相片位置索引与底层媒体数据对象（`dud`）的映射。
2. **新版相册 (ColorOS 15~17)**：
   - 监听伴生工厂类 `PhotoItemViewData$a.a(dud, position)` 方法；
   - 捕获入参中的 `dud`（`com.oplus.aiunit.vision.q9q`）以及生成的 `PhotoItemViewData` 实例；
   - 维护 `sViewDataMap` 和 `sItemMap`。
3. **页面位置定位**：
   - 监听 `PhotoPager.getCurrentSelection()` 追踪用户当前滑动停留在屏幕上的实际页面。
4. **属性精准提取与解构**：
   - **文件路径获取**：
     - 优先从 `PhotoItemViewData.f` 或 `dud.H()` / `dud.A()` / 字段 `dud.o` / `dud.j` 中提取物理路径；
     - 递归扫描实例中指向 `/storage/` 或 `/sdcard/` 且实际存在的文件路径。
   - **媒体 ID 获取**：
     - 优先从底层 `MediaObject.b`（Path，形如 `/local/image/item/29652`）或 `PhotoItemViewData.c` 解析出结尾的纯数字 ID；
     - 避免随意以 `getLong > 1000` 匹配字段（防止将 `MediaObject.mDataVersion` 递增版本号误作为 MediaStore ID）；
     - 支持通过本地文件物理路径直接在 ContentResolver 中按 `_DATA` 精确反查 MediaStore 真实 ID。
5. **多重 Fallback 机制**：
   - 从 `PhotoFragment` 的 `media-item-path` 参数解析 ID；
   - 或从 MediaStore 查询最新拍摄/修改媒体项兜底。

### 3.3 系统剪贴板注入与权限穿透 (`ClipboardHelper`)
#### 3.3.1 权限障碍与 FileProvider 根本解法
- **问题根源**：在 Android 14/15/16/17 (ColorOS 15+) 中，系统 `ClipboardService` 对 `setPrimaryClip` 中传入的 `content://` URI 加强了严格的安全校验（CVE/安全基线升级）：强制调用 `checkUriOwner` 校验调用方 UID（10146，相册进程）是否拥有对该 URI 的 Grant 授权。若传入第三方提供的 MediaStore 原生 URI (`content://media/...`)，系统会直接抛出 `SecurityException: UID 10146: does not have permission to content://media/...`。
- **FileProvider 核心机制**：
  - 相册自身注册并拥有一个合法的、专用于跨应用流转的 ContentProvider：`com.oplus.gallery.FileProvider`（实现类 `com.oplus.gallery.foundation.fileaccess.GalleryFileProvider`，继承自 `androidx.core.content.FileProvider`）；
  - 其 `common_file_paths` (`res/wxO.xml`) 配置明确包含了 `<external-path name="external_storage_root" path="." />` 与 `<root-path name="device_root" path="" />`，覆盖整机所有媒体文件；
  - 由于该 Provider 的所有者就是相册自身（UID 10146），相册对其拥有最高转授权力，`setPrimaryClip` 与 `grantUriPermission` **100% 通过系统校验，彻底根治权限异常**。
- **双向兼容策略**：
  - **优先生成 FileProvider URI**：当本地图片文件存在时，通过 `GalleryFileProvider` / `FileProvider.getUriForFile` 生成 URI；
  - **降级支持 MediaStore URI**：在旧版 ColorOS 或无法获取文件物理路径时，使用 `ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)` 兜底；
  - **调用容错重试**：`setPrimaryClip` 发生 `SecurityException` 时，自动尝试另一种 URI，确保平滑过渡。
- **剪贴板托管授权体系**：
  - **依托系统级剪贴板授权流转**：相册自身的 `GalleryFileProvider` 在 Manifest 中明确声明了 `android:grantUriPermissions="true"`；
  - 当 `setPrimaryClip` 成功后，`system_server` 的 `ClipboardService`（`mPermissionOwner`）在任何消费方应用（系统剪贴板浮窗、各类输入法预览条、微信、QQ、系统便签、协同工具等）读取剪贴板时，由系统自动完成动态临时只读授权；
  - 彻底拒绝维护任何脆弱、易漏的硬编码应用白名单，天然无缝兼容分身双开、工作资料库应用、平板/折叠屏协同以及任意第三方应用；
  - 放宽 `StrictMode.VmPolicy` 限制，确保各类粘贴端均可无缝读取流数据。

---

## 4. 边界与稳定性保障
1. **绝对防崩原则 (Fail-Safe)**：所有反射、Hook 与剪贴板操作均在 `try-catch` 保护下执行，任何失败均有优雅 Toast 提示与日志排查，绝不导致相册宿主崩溃。
2. **纯粹图片数据**：不混入纯文本格式（`MIMETYPE_TEXT_PLAIN`），确保剪贴板监听器（如 KDE Connect / 微信键盘）将其作为二进制真实图片发送到远端或呼出图片粘贴气泡。
3. **防越界保护**：在代理点击监听器中，严格防止由于动态增加了一个菜单项而导致的 `IndexOutOfBoundsException`。

---

## 5. UI 呈现
- 在主界面以豆腐块卡片形式展示，标明「默认开启」。
- 展开后展示清晰的操作步骤示意与跨端协同工具推荐。
