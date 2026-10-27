# 功能设计：相册复制到剪贴板 (Gallery Copy to Clipboard)

## 1. 需求背景与用户痛点
- **现状**：ColorOS 原生相册（`com.coloros.gallery3d`）在单张大图浏览界面的右上角「更多」菜单中，提供了壁纸、重命名、设为私密等操作，但**没有将图片复制到系统剪贴板**的选项。
- **痛点**：跨设备同步（如通过 KDE Connect、LocalSend）、电脑端即时粘贴图片、微信/QQ 对话框快速发图时，用户必须先进行繁琐的系统分享，无法实现直接 Ctrl+V / ⌘+V 粘贴图片的高效流转。
- **目标**：在右上角「更多」菜单标准 COUI 弹窗末尾动态注入「复制到剪贴板」，点击后以无损原生 MediaStore URI 和纯图片 `ClipData` 存入系统剪贴板。

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
- 代理 `setOnItemClickListener`，拦截我们新增项的点击事件，阻断其触发原始相册未处理该 ID 引发的越界或空指针异常，并触发复制业务。

### 3.2 数据提取层 (`PhotoDataExtractor`)
- 监听 `PhotoItemViewData` 构造函数，维护相片位置索引与底层媒体数据对象（dud）的映射。
- 监听 `PhotoPager.getCurrentSelection()` 追踪用户当前滑动停留在屏幕上的实际页面。
- 通过反射调用 `dud.H()`、字段 `dud.o` 或扫描类字段提取物理路径与 MediaStore ID。
- 具备双重 Fallback：从 `PhotoFragment` 的 `media-item-path` 解析，或从 MediaStore 查询最新媒体项兜底。

### 3.3 系统剪贴板注入与权限授予 (`ClipboardHelper`)
- 构造纯图片 `ClipDescription("Image", arrayOf("image/*", mimeType))`。
- 为解决 Android 10+ 分区存储与跨应用读取限制：
  - 自动向 `com.android.systemui`、系统输入法（百度/搜狗/Gboard）、微信、QQ、KDE Connect 预授权 URI 读权限（`FLAG_GRANT_READ_URI_PERMISSION`）；
  - 临时放宽 `StrictMode.VmPolicy` 限制，确保各类粘贴端均可无缝读取。

---

## 4. 边界与稳定性保障
1. **防越界保护**：在代理点击监听器中，严格防止由于动态增加了一个菜单项而导致的 `IndexOutOfBoundsException`。
2. **纯粹图片数据**：不混入纯文本格式（`MIMETYPE_TEXT_PLAIN`），确保剪贴板监听器（如 KDE Connect）将其作为二进制真实图片发送到远端，避免仅复制了一行文件路径字符串。

---

## 5. UI 呈现
- 在主界面以豆腐块卡片形式展示，标明「默认开启」。
- 展开后展示清晰的操作步骤示意与跨端协同工具推荐。
