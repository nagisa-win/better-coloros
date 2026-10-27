# 功能设计：相机文件名规范化 (Camera Filename Normalization)

## 1. 需求背景与用户痛点
- **现状**：ColorOS 原生相机在拍摄照片与录制视频时，默认命名格式缺少标准下划线分割，例如：
  - 照片：`IMG20261027171025.jpg`
  - 录像：`VID20261027171025.mp4`
  - 动态/电影：`MOD20261027171025.mp4`
- **痛点**：第三方照片管理软件（如群晖 Synology Photos、Immich、Nextcloud）、网盘备份（百度网盘、阿里云盘）在依据正则提取年月日时分秒（`IMG_YYYYMMDD_HHmmss`）时无法识别，造成相册归档混乱。
- **目标**：规范化为 `IMG_20261027_171025.jpg`、`VID_20261027_171025.mp4`、`MOD_20261027_171025.mp4`，并完整保留连拍（`_BURST01`）与实况封面（`_COVER`）后缀。

---

## 2. 目标作用域与进程
- **宿主包名**：`com.oplus.camera`（主生效应用）、`com.coloros.gallery3d`（辅助生效，确保相册内部另存文件时格式一致）
- **进程名**：`com.oplus.camera` 主进程

---

## 3. Hook 方案与多层拦截点

为保证对抗系统版本 OTA 与类混淆的万无一失，构建了**五层纵深防御体系**：

```
拍照触发 
   │
[第 1 层: DexKit 扫描拦截] ──> 在源头搜索返回 String 的私有方法，重写返回值为规范命名
   │
[第 2 层: 模版与资源拦截] ──> 拦截 SimpleDateFormat 构造器与 Resources.getText，改写 'IMG'yyyyMMddHHmmss
   │
[第 3 层: 媒体库入库拦截] ──> 拦截 ContentValues.put 与 ContentResolver.insert，修正 _display_name / title / _data
   │
[第 4 层: 底层文件创建拦截] ──> 拦截 File(String)、File(File, String) 与 FileOutputStream
   │
[第 5 层: 录像输出路径拦截] ──> 拦截 MediaRecorder.setOutputFile(String/File)
```

---

## 4. 共享类与依赖复用
- **`win.nagisa.bettercoloros.common.DexKitHelper`**：动态检索 DEX 字符串 `yyyyMMddHHmmss`、`IMG`、`VID`、`MOD`。
- **`win.nagisa.bettercoloros.common.FilenameNormalizer`**：核心正则引擎，兼顾路径前缀与多重业务后缀提取。

---

## 5. 边界与幂等性保障
1. **幂等性检查**：已符合 `(IMG|VID|MOD)_\d{8}_\d{6}` 的字符串直接返回原值，杜绝下划线无限累加。
2. **多扩展名支持**：自动适配 `.jpg`、`.jpeg`、`.heic`、`.dng`、`.mp4`。
3. **空指针保护**：所有 Hook 点均具备参数非空校验与独立 `try-catch`。

---

## 6. UI 呈现
- 在应用主界面以「功能豆腐块」卡片展示。
- 标明「默认开启」。
- 展开后提供即时输入测试框与转换效果预览。
