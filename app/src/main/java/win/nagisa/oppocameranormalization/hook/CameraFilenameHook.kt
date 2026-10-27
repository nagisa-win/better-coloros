package win.nagisa.oppocameranormalization.hook

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import win.nagisa.oppocameranormalization.util.DexKitHelper
import win.nagisa.oppocameranormalization.util.FilenameNormalizer
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * OPPO / OnePlus 相机核心文件名拦截器
 *
 * 采用多层纵深防御拦截架构：
 * 1. DexKit 动态扫描：在 com.oplus.camera 内部搜索生成/返回文件名的私有与混淆方法，直接在调用源头进行纠正
 * 2. ContentValues & MediaStore 拦截：拦截存入系统媒体库的 _display_name、title、_data 字段
 * 3. File & FileOutputStream 拦截：拦截直接向存储介质创建文件/目录时的路径
 * 4. MediaRecorder 拦截：拦截录像模块生成 MP4 视频的输出文件路径
 * 5. SimpleDateFormat 拦截：动态纠正内部时间格式模版
 */
object CameraFilenameHook : BaseHook() {

    private const val TAG = "[OppoCameraNormalization]"

    override fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG Initializing hook for ${lpparam.packageName} (process: ${lpparam.processName})")

        hookContentValues(lpparam)
        hookContentResolver(lpparam)
        hookFileOperations(lpparam)
        hookMediaRecorder(lpparam)
        hookDateFormat(lpparam)
        hookDexKitDynamicMethods(lpparam)
    }

    /**
     * 第一层：通过 DexKit 扫描目标 App 内部涉及时间戳与文件名前缀的核心方法
     */
    private fun hookDexKitDynamicMethods(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val bridge = DexKitHelper.bridge ?: run {
                XposedBridge.log("$TAG DexKit bridge unavailable, skipping dynamic scan.")
                return
            }

            val targetStrings = listOf("yyyyMMddHHmmss", "IMG", "VID")
            val candidateMethods = mutableSetOf<Method>()

            for (str in targetStrings) {
                try {
                    val methodDataList = bridge.findMethod {
                        matcher {
                            usingStrings(str)
                        }
                    }

                    for (m in methodDataList) {
                        try {
                            val methodInstance = m.getMethodInstance(lpparam.classLoader)
                            if (methodInstance.returnType == String::class.java) {
                                candidateMethods.add(methodInstance)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG DexKit findMethod error for string '$str': ${t.message}")
                }
            }

            XposedBridge.log("$TAG DexKit found ${candidateMethods.size} candidate String-returning methods.")

            for (method in candidateMethods) {
                try {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val result = param.result as? String ?: return
                            if (FilenameNormalizer.needNormalize(result)) {
                                val normalized = FilenameNormalizer.normalize(result)
                                if (normalized != null && normalized != result) {
                                    param.result = normalized
                                    XposedBridge.log("$TAG [DexKit Hook] ${method.declaringClass.simpleName}.${method.name}(): '$result' -> '$normalized'")
                                }
                            }
                        }
                    })
                } catch (_: Throwable) {
                }
            }

        } catch (t: Throwable) {
            XposedBridge.log("$TAG Error during DexKit dynamic hooking: ${t.message}")
        }
    }

    /**
     * 第二层：拦截 ContentValues.put
     * 当相机向系统 MediaStore 或内部 ContentProvider 插入照片/视频元数据时触发
     */
    private fun hookContentValues(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val targetColumns = setOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.TITLE,
                MediaStore.MediaColumns.DATA,
                "_display_name",
                "title",
                "_data"
            )

            XposedHelpers.findAndHookMethod(
                ContentValues::class.java,
                "put",
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        val value = param.args[1] as? String ?: return

                        if (key in targetColumns && FilenameNormalizer.needNormalize(value)) {
                            val normalized = FilenameNormalizer.normalize(value)
                            if (normalized != null && normalized != value) {
                                param.args[1] = normalized
                                XposedBridge.log("$TAG [ContentValues.put] $key: '$value' -> '$normalized'")
                            }
                        }
                    }
                }
            )
            XposedBridge.log("$TAG Hooked ContentValues.put successfully.")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook ContentValues.put: ${t.message}")
        }
    }

    /**
     * 兜底媒体库插入：拦截 ContentResolver.insert
     */
    private fun hookContentResolver(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val contentResolverClass = XposedHelpers.findClass("android.content.ContentResolver", lpparam.classLoader)
            val targetColumns = listOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.TITLE,
                MediaStore.MediaColumns.DATA,
                "_display_name",
                "title",
                "_data"
            )

            XposedHelpers.findAndHookMethod(
                contentResolverClass,
                "insert",
                Uri::class.java,
                ContentValues::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val values = param.args[1] as? ContentValues ?: return
                        for (col in targetColumns) {
                            val original = values.getAsString(col)
                            if (original != null && FilenameNormalizer.needNormalize(original)) {
                                val normalized = FilenameNormalizer.normalize(original)
                                if (normalized != null && normalized != original) {
                                    values.put(col, normalized)
                                    XposedBridge.log("$TAG [ContentResolver.insert] column '$col': '$original' -> '$normalized'")
                                }
                            }
                        }
                    }
                }
            )
            XposedBridge.log("$TAG Hooked ContentResolver.insert successfully.")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook ContentResolver.insert: ${t.message}")
        }
    }

    /**
     * 第三层：拦截直接的文件与文件流创建操作
     */
    private fun hookFileOperations(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook File(String pathname)
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val path = param.args[0] as? String ?: return
                        if (FilenameNormalizer.needNormalize(path)) {
                            val normalized = FilenameNormalizer.normalize(path)
                            if (normalized != null && normalized != path) {
                                param.args[0] = normalized
                                XposedBridge.log("$TAG [File(String)]: '$path' -> '$normalized'")
                            }
                        }
                    }
                }
            )

            // Hook File(File parent, String child)
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                File::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val child = param.args[1] as? String ?: return
                        if (FilenameNormalizer.needNormalize(child)) {
                            val normalized = FilenameNormalizer.normalize(child)
                            if (normalized != null && normalized != child) {
                                param.args[1] = normalized
                                XposedBridge.log("$TAG [File(File, String)]: '$child' -> '$normalized'")
                            }
                        }
                    }
                }
            )

            // Hook File(String parent, String child)
            XposedHelpers.findAndHookConstructor(
                File::class.java,
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val child = param.args[1] as? String ?: return
                        if (FilenameNormalizer.needNormalize(child)) {
                            val normalized = FilenameNormalizer.normalize(child)
                            if (normalized != null && normalized != child) {
                                param.args[1] = normalized
                                XposedBridge.log("$TAG [File(String, String)]: '$child' -> '$normalized'")
                            }
                        }
                    }
                }
            )

            // Hook FileOutputStream(String name, boolean append)
            val fosClass = java.io.FileOutputStream::class.java
            XposedHelpers.findAndHookConstructor(
                fosClass,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        if (FilenameNormalizer.needNormalize(name)) {
                            val normalized = FilenameNormalizer.normalize(name)
                            if (normalized != null && normalized != name) {
                                param.args[0] = normalized
                                XposedBridge.log("$TAG [FileOutputStream(String)]: '$name' -> '$normalized'")
                            }
                        }
                    }
                }
            )

            XposedBridge.log("$TAG Hooked File operations successfully.")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook File operations: ${t.message}")
        }
    }

    /**
     * 第四层：拦截录像输出路径 (MediaRecorder.setOutputFile)
     */
    private fun hookMediaRecorder(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val mrClass = android.media.MediaRecorder::class.java

            // Hook setOutputFile(String path)
            try {
                XposedHelpers.findAndHookMethod(
                    mrClass,
                    "setOutputFile",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val path = param.args[0] as? String ?: return
                            if (FilenameNormalizer.needNormalize(path)) {
                                val normalized = FilenameNormalizer.normalize(path)
                                if (normalized != null && normalized != path) {
                                    param.args[0] = normalized
                                    XposedBridge.log("$TAG [MediaRecorder.setOutputFile(String)]: '$path' -> '$normalized'")
                                }
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            // Hook setOutputFile(File file)
            try {
                XposedHelpers.findAndHookMethod(
                    mrClass,
                    "setOutputFile",
                    File::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val file = param.args[0] as? File ?: return
                            val path = file.absolutePath
                            if (FilenameNormalizer.needNormalize(path)) {
                                val normalized = FilenameNormalizer.normalize(path)
                                if (normalized != null && normalized != path) {
                                    param.args[0] = File(normalized)
                                    XposedBridge.log("$TAG [MediaRecorder.setOutputFile(File)]: '$path' -> '$normalized'")
                                }
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            XposedBridge.log("$TAG Hooked MediaRecorder successfully.")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook MediaRecorder: ${t.message}")
        }
    }

    /**
     * 第五层：拦截时间格式化模版
     */
    private fun hookDateFormat(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sdfClass = java.text.SimpleDateFormat::class.java

            // 拦截 SimpleDateFormat(String pattern)
            XposedHelpers.findAndHookConstructor(
                sdfClass,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pattern = param.args[0] as? String ?: return
                        val adapted = adaptPattern(pattern)
                        if (adapted != pattern) {
                            param.args[0] = adapted
                            XposedBridge.log("$TAG [SimpleDateFormat(pattern)]: '$pattern' -> '$adapted'")
                        }
                    }
                }
            )

            // 拦截 SimpleDateFormat(String pattern, Locale locale)
            XposedHelpers.findAndHookConstructor(
                sdfClass,
                String::class.java,
                Locale::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pattern = param.args[0] as? String ?: return
                        val adapted = adaptPattern(pattern)
                        if (adapted != pattern) {
                            param.args[0] = adapted
                            XposedBridge.log("$TAG [SimpleDateFormat(pattern, locale)]: '$pattern' -> '$adapted'")
                        }
                    }
                }
            )

            // 拦截 SimpleDateFormat(String pattern, DateFormatSymbols formatSymbols)
            XposedHelpers.findAndHookConstructor(
                sdfClass,
                String::class.java,
                DateFormatSymbols::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pattern = param.args[0] as? String ?: return
                        val adapted = adaptPattern(pattern)
                        if (adapted != pattern) {
                            param.args[0] = adapted
                            XposedBridge.log("$TAG [SimpleDateFormat(pattern, symbols)]: '$pattern' -> '$adapted'")
                        }
                    }
                }
            )

            XposedBridge.log("$TAG Hooked SimpleDateFormat constructors successfully.")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook SimpleDateFormat: ${t.message}")
        }
    }

    private fun adaptPattern(pattern: String): String {
        return when (pattern) {
            "yyyyMMddHHmmss" -> "yyyyMMdd_HHmmss"
            "'IMG'yyyyMMddHHmmss" -> "'IMG_'yyyyMMdd'_'HHmmss"
            "'VID'yyyyMMddHHmmss" -> "'VID_'yyyyMMdd'_'HHmmss"
            "IMGyyyyMMddHHmmss" -> "IMG_yyyyMMdd_HHmmss"
            "VIDyyyyMMddHHmmss" -> "VID_yyyyMMdd_HHmmss"
            else -> pattern
        }
    }
}
