package win.nagisa.bettercoloros.hook.camera

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import win.nagisa.bettercoloros.common.DexKitHelper
import win.nagisa.bettercoloros.common.FilenameNormalizer
import win.nagisa.bettercoloros.hook.BaseHook
import java.io.File
import java.lang.reflect.Method
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * 相机文件名规范化 Hook (五层纵深防御体系)
 */
object CameraFilenameHook : BaseHook() {

    private const val TAG = "[BetterColorOS][CameraRename]"

    override fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (isInit) return
        isInit = true

        XposedBridge.log("$TAG Initializing for ${lpparam.packageName} (${lpparam.processName})")

        hookContentValues(lpparam)
        hookContentResolver(lpparam)
        hookFileOperations(lpparam)
        hookMediaRecorder(lpparam)
        hookDateFormat(lpparam)
        hookDexKitDynamicMethods(lpparam)
    }

    private fun hookDexKitDynamicMethods(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val bridge = DexKitHelper.bridge ?: return
            val targetStrings = listOf("yyyyMMddHHmmss", "IMG", "VID", "MOD")
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
                        } catch (e: Throwable) {}
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG DexKit findMethod error for '$str': ${t.message}")
                }
            }

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
                } catch (e: Throwable) {}
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Error during DexKit dynamic hooking: ${t.message}")
        }
    }

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
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook ContentValues.put: ${t.message}")
        }
    }

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
                                    XposedBridge.log("$TAG [ContentResolver.insert] col '$col': '$original' -> '$normalized'")
                                }
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook ContentResolver.insert: ${t.message}")
        }
    }

    private fun hookFileOperations(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // File(String)
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

            // File(File, String)
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

            // File(String, String)
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

            // FileOutputStream(String, boolean)
            XposedHelpers.findAndHookConstructor(
                java.io.FileOutputStream::class.java,
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
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook File operations: ${t.message}")
        }
    }

    private fun hookMediaRecorder(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val mrClass = android.media.MediaRecorder::class.java
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
            } catch (e: Throwable) {}

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
            } catch (e: Throwable) {}
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook MediaRecorder: ${t.message}")
        }
    }

    private fun hookDateFormat(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sdfClass = java.text.SimpleDateFormat::class.java

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

            // Resources.getText
            try {
                XposedHelpers.findAndHookMethod(
                    android.content.res.Resources::class.java,
                    "getText",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val text = param.result as? CharSequence ?: return
                            val str = text.toString()
                            val adapted = adaptPattern(str)
                            if (adapted != str) {
                                param.result = adapted
                                XposedBridge.log("$TAG [Resources.getText]: '$text' -> '$adapted'")
                            }
                        }
                    }
                )
            } catch (e: Throwable) {}
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to hook SimpleDateFormat/Resources: ${t.message}")
        }
    }

    private fun adaptPattern(pattern: String): String {
        return when (pattern) {
            "yyyyMMddHHmmss" -> "yyyyMMdd_HHmmss"
            "'IMG'yyyyMMddHHmmss" -> "'IMG_'yyyyMMdd'_'HHmmss"
            "'VID'yyyyMMddHHmmss" -> "'VID_'yyyyMMdd'_'HHmmss"
            "'MOD'yyyyMMddHHmmss" -> "'MOD_'yyyyMMdd'_'HHmmss"
            "IMGyyyyMMddHHmmss" -> "IMG_yyyyMMdd_HHmmss"
            "VIDyyyyMMddHHmmss" -> "VID_yyyyMMdd_HHmmss"
            "MODyyyyMMddHHmmss" -> "MOD_yyyyMMdd_HHmmss"
            else -> pattern
        }
    }
}
