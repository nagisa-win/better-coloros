package win.nagisa.bettercoloros.hook.gallery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 相册当前浏览图片信息提取器
 *
 * 支持双轨追踪：
 * 1. 旧版 ColorOS：Hook PhotoItemViewData 构造函数；
 * 2. 新版 ColorOS (15/16/17)：Hook 伴生工厂类 PhotoItemViewData$a 的对象创建方法；
 * 3. 页面追踪：Hook PhotoPager.getCurrentSelection()；
 * 4. 属性提取：优先从 MediaObject.b (Path) 解析精确 MediaStore ID，避免将 mDataVersion 误作 ID。
 */
object PhotoDataExtractor {
    private const val TAG = "[BetterColorOS][GalleryCopy] "

    // 位置与底层媒体对象 (dud / q9q) 的映射
    private val sItemMap = ConcurrentHashMap<Int, WeakReference<Any>>()
    // 位置与上层展示数据 (PhotoItemViewData) 的映射
    private val sViewDataMap = ConcurrentHashMap<Int, WeakReference<Any>>()

    @Volatile private var sLastDud: WeakReference<Any>? = null
    @Volatile private var sLastViewData: WeakReference<Any>? = null
    @Volatile private var sLastPhotoPager: WeakReference<ViewGroup>? = null
    @Volatile private var sCurrentActivity: WeakReference<Activity>? = null
    @Volatile private var sCurrentSelection = 0

    fun init(classLoader: ClassLoader) {
        // 跟踪当前前台 Activity
        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.thisObject is Activity) {
                            sCurrentActivity = WeakReference(param.thisObject as Activity)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Hook Activity.onResume failed: $t")
        }

        // 1.1 Hook 旧版 PhotoItemViewData 构造函数
        try {
            val viewDataClass = XposedHelpers.findClass(
                "com.oplus.gallery.photo_page.viewmodel.dataloading.PhotoItemViewData",
                classLoader
            )
            XposedBridge.hookAllConstructors(viewDataClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val obj = param.thisObject ?: return
                    var dud: Any? = null
                    var pos: Int? = null

                    if (param.args.size >= 2) {
                        dud = param.args[0]
                        pos = (param.args[1] as? Number)?.toInt()
                    }
                    if (pos == null) {
                        pos = runCatching { XposedHelpers.getIntField(obj, "b") }.getOrNull()
                    }
                    if (dud == null) {
                        dud = runCatching { XposedHelpers.getObjectField(obj, "a") }.getOrNull()
                    }

                    if (pos != null) {
                        sViewDataMap[pos] = WeakReference(obj)
                        sLastViewData = WeakReference(obj)
                        if (dud != null) {
                            sItemMap[pos] = WeakReference(dud)
                            sLastDud = WeakReference(dud)
                        }
                        XposedBridge.log("$TAG Tracked PhotoItemViewData (constructor): pos=$pos, dud=${dud?.javaClass?.name}")
                    }
                }
            })
            XposedBridge.log("$TAG Hooked PhotoItemViewData constructors")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Hook PhotoItemViewData constructors failed: $t")
        }

        // 1.2 Hook 新版 PhotoItemViewData$a 工厂方法 (ColorOS 15/16/17+)
        try {
            val companionClass = runCatching {
                XposedHelpers.findClass(
                    "com.oplus.gallery.photo_page.viewmodel.dataloading.PhotoItemViewData\$a",
                    classLoader
                )
            }.getOrNull()

            if (companionClass != null) {
                for (method in companionClass.declaredMethods) {
                    val params = method.parameterTypes
                    // 工厂方法签名特征：参数形如 (dud/q9q, int position)
                    if (params.size == 2 && (params[1] == Int::class.javaPrimitiveType || params[1] == Integer::class.java)) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val dud = param.args[0]
                                val pos = (param.args[1] as? Number)?.toInt()
                                val viewData = param.result

                                if (pos != null) {
                                    if (viewData != null) {
                                        sViewDataMap[pos] = WeakReference(viewData)
                                        sLastViewData = WeakReference(viewData)
                                    }
                                    if (dud != null) {
                                        sItemMap[pos] = WeakReference(dud)
                                        sLastDud = WeakReference(dud)
                                    }
                                    XposedBridge.log("$TAG Tracked PhotoItemViewData (factory ${method.name}): pos=$pos, dud=${dud?.javaClass?.name}")
                                }
                            }
                        })
                        XposedBridge.log("$TAG Hooked PhotoItemViewData factory: ${method.name}")
                    }
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Hook PhotoItemViewData companion failed: $t")
        }

        // 2. Hook PhotoPager 追踪当前大图滑动索引
        try {
            val pagerClass = XposedHelpers.findClass(
                "com.oplus.gallery.photopager.PhotoPager",
                classLoader
            )

            XposedBridge.hookAllConstructors(pagerClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    sLastPhotoPager = WeakReference(param.thisObject as ViewGroup)
                }
            })

            XposedHelpers.findAndHookMethod(pagerClass, "getCurrentSelection", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result
                    if (result is Int) {
                        sCurrentSelection = result
                    }
                }
            })

            try {
                XposedHelpers.findAndHookMethod(pagerClass, "p", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] is Int) {
                            sCurrentSelection = param.args[0] as Int
                        }
                    }
                })
            } catch (e: Throwable) {}

            XposedBridge.log("$TAG Hooked PhotoPager methods")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Hook PhotoPager failed: $t")
        }
    }

    fun handleCopy(window: Any?) {
        if (window == null) {
            XposedBridge.log("$TAG Window is null")
            return
        }

        try {
            var anchorView: View? = null
            try {
                anchorView = XposedHelpers.callMethod(window, "getAnchorView") as? View
            } catch (e: Throwable) {}
            if (anchorView == null) {
                try {
                    anchorView = XposedHelpers.getObjectField(window, "mAnchorView") as? View
                } catch (e: Throwable) {}
            }

            var context: Context? = anchorView?.context
            if (context == null) {
                try {
                    context = XposedHelpers.getObjectField(window, "mContext") as? Context
                } catch (e: Throwable) {}
            }
            if (context == null) {
                context = sCurrentActivity?.get()
            }

            var activity = resolveActivity(context)
            if (activity == null) {
                activity = sCurrentActivity?.get()
            }
            if (context == null && activity != null) {
                context = activity
            }

            if (context == null) {
                XposedBridge.log("$TAG Failed to get Context from window")
                return
            }

            val finalContext = context

            // 获取 PhotoPager 与精准 position
            var photoPager: ViewGroup? = sLastPhotoPager?.get()
            if (photoPager == null && activity != null) {
                photoPager = findPhotoPager(activity.window.decorView)
            }

            var position = sCurrentSelection
            if (photoPager != null) {
                try {
                    position = XposedHelpers.callMethod(photoPager, "getCurrentSelection") as Int
                    sCurrentSelection = position
                } catch (e: Throwable) {}
            }

            XposedBridge.log("$TAG Active position: $position")

            var filePath: String? = null
            var mediaId: Long = -1

            // 优先检查 PhotoItemViewData 实体缓存
            var viewData: Any? = sViewDataMap[position]?.get() ?: sLastViewData?.get()
            var dud: Any? = sItemMap[position]?.get() ?: sLastDud?.get()

            if (viewData != null) {
                if (dud == null) {
                    dud = runCatching { XposedHelpers.getObjectField(viewData, "a") }.getOrNull()
                }

                // PhotoItemViewData.f 往往直接存储着物理路径
                val fVal = runCatching { XposedHelpers.getObjectField(viewData, "f") as? String }.getOrNull()
                if (!fVal.isNullOrEmpty() && (fVal.startsWith("/") || fVal.startsWith("content://"))) {
                    filePath = fVal
                    XposedBridge.log("$TAG Extracted filePath from PhotoItemViewData.f: $filePath")
                }

                // PhotoItemViewData.c 存储着 Path 字符串，如 "/local/image/item/29652"
                val cVal = runCatching { XposedHelpers.getObjectField(viewData, "c") as? String }.getOrNull()
                if (!cVal.isNullOrEmpty()) {
                    val lastSlash = cVal.lastIndexOf('/')
                    if (lastSlash >= 0) {
                        val parsed = cVal.substring(lastSlash + 1).toLongOrNull()
                        if (parsed != null && parsed > 0) {
                            mediaId = parsed
                            XposedBridge.log("$TAG Extracted mediaId from PhotoItemViewData.c: $mediaId")
                        }
                    }
                }
            }

            // 从底层媒体实体 (dud / q9q) 补充提取
            if (dud != null) {
                if (filePath.isNullOrEmpty()) {
                    filePath = extractFilePath(dud)
                }
                if (mediaId <= 0) {
                    mediaId = extractMediaId(dud)
                }
                XposedBridge.log("$TAG Extracted from dud: path=$filePath, mediaId=$mediaId")
            }

            // Fallback 1: 从 PhotoFragment 参数中的 media-item-path 提取
            if (filePath.isNullOrEmpty() && mediaId <= 0 && activity != null) {
                try {
                    val supportFragmentManager = activity.javaClass.getMethod("getSupportFragmentManager").invoke(activity)
                    if (supportFragmentManager != null) {
                        val fragments = supportFragmentManager.javaClass.getMethod("getFragments").invoke(supportFragmentManager) as? List<*>
                        if (fragments != null) {
                            for (fragment in fragments) {
                                if (fragment != null && fragment.javaClass.name.contains("PhotoFragment")) {
                                    val args = fragment.javaClass.getMethod("getArguments").invoke(fragment) as? Bundle
                                    if (args != null) {
                                        val mediaItemPath = args.getString("media-item-path")
                                        XposedBridge.log("$TAG Found media-item-path in PhotoFragment args: $mediaItemPath")
                                        if (mediaItemPath != null && mediaItemPath.contains("/image/")) {
                                            val idStr = mediaItemPath.substring(mediaItemPath.lastIndexOf('/') + 1)
                                            val parsedId = idStr.toLongOrNull()
                                            if (parsedId != null && parsedId > 0) {
                                                mediaId = parsedId
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG Extract from PhotoFragment failed: $t")
                }
            }

            // Fallback 2: 查询 MediaStore 中最近修改的一张照片兜底
            if (filePath.isNullOrEmpty() && mediaId <= 0) {
                var cursor: Cursor? = null
                try {
                    cursor = context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA),
                        null,
                        null,
                        "${MediaStore.Images.Media.DATE_MODIFIED} DESC LIMIT 1"
                    )
                    if (cursor != null && cursor.moveToFirst()) {
                        mediaId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                        XposedBridge.log("$TAG Fallback to latest media: id=$mediaId, path=$filePath")
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG Fallback query error: $t")
                } finally {
                    cursor?.close()
                }
            }

            if (!filePath.isNullOrEmpty() || mediaId > 0) {
                ClipboardHelper.copyImageToClipboard(context, filePath, mediaId)
            } else {
                XposedBridge.log("$TAG Could not find current photo info")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(finalContext, "未能获取当前图片信息", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (t: Throwable) {
            XposedBridge.log("$TAG handleCopy error: $t")
        }
    }

    private fun resolveActivity(context: Context?): Activity? {
        var cur = context
        while (cur is ContextWrapper) {
            if (cur is Activity) {
                return cur
            }
            cur = cur.baseContext
        }
        return null
    }

    private fun findPhotoPager(root: View?): ViewGroup? {
        if (root == null) return null
        if (root.javaClass.name.contains("PhotoPager") && root is ViewGroup) {
            return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val res = findPhotoPager(root.getChildAt(i))
                if (res != null) return res
            }
        }
        return null
    }

    private fun extractFilePath(dud: Any?): String? {
        if (dud == null) return null

        // 1. 旧版常用方法 H()
        try {
            val m: Method = dud.javaClass.getMethod("H")
            val res = m.invoke(dud)
            if (res is String && (res.startsWith("/") || res.startsWith("content://"))) {
                return res
            }
        } catch (e: Throwable) {}

        // 2. 新版常用方法 A()
        try {
            val m: Method = dud.javaClass.getMethod("A")
            val res = m.invoke(dud)
            if (res is String && (res.startsWith("/") || res.startsWith("content://"))) {
                return res
            }
        } catch (e: Throwable) {}

        // 3. 常见字段 o
        try {
            val f = dud.javaClass.getDeclaredField("o").apply { isAccessible = true }
            val res = f.get(dud)
            if (res is String && (res.startsWith("/") || res.startsWith("content://"))) {
                return res
            }
        } catch (e: Throwable) {}

        // 4. 常见字段 j
        try {
            val f = dud.javaClass.getDeclaredField("j").apply { isAccessible = true }
            val res = f.get(dud)
            if (res is String && (res.startsWith("/") || res.startsWith("content://"))) {
                return res
            }
        } catch (e: Throwable) {}

        // 5. 递归检查类继承树中的所有 String 属性，若指向有效存储路径则命中
        var clz: Class<*>? = dud.javaClass
        while (clz != null && clz != Any::class.java) {
            for (field in clz.declaredFields) {
                if (field.type == String::class.java) {
                    try {
                        field.isAccessible = true
                        val value = field.get(dud) as? String
                        if (value != null) {
                            if (value.startsWith("/storage/") || value.startsWith("/sdcard/")) {
                                if (File(value).exists()) {
                                    return value
                                }
                            } else if (value.startsWith("content://")) {
                                return value
                            }
                        }
                    } catch (e: Throwable) {}
                }
            }
            clz = clz.superclass
        }
        return null
    }

    private fun extractMediaId(dud: Any?): Long {
        if (dud == null) return -1L

        // 1. 优先从底层 MediaObject.b (Path 实体，形如 /local/image/item/29652) 提取
        try {
            var pathObj: Any? = null
            try {
                pathObj = XposedHelpers.getObjectField(dud, "b")
            } catch (e: Throwable) {
                pathObj = XposedHelpers.callMethod(dud, "getPath")
            }
            if (pathObj != null) {
                val pathStr = pathObj.toString()
                val lastSlash = pathStr.lastIndexOf('/')
                if (lastSlash >= 0) {
                    val id = pathStr.substring(lastSlash + 1).toLongOrNull()
                    if (id != null && id > 0) {
                        return id
                    }
                }
            }
        } catch (e: Throwable) {}

        // 2. 检查旧版中的字段 g
        try {
            val f: Field = dud.javaClass.getField("g")
            val res = f.get(dud)
            if (res is Number && res.toLong() > 0) {
                return res.toLong()
            }
        } catch (e: Throwable) {}

        return -1L
    }
}
