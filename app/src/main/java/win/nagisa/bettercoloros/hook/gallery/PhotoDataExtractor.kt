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
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 相册当前浏览图片信息提取器
 */
object PhotoDataExtractor {
    private const val TAG = "[BetterColorOS][GalleryCopy] "

    private val sItemMap = ConcurrentHashMap<Int, WeakReference<Any>>()
    @Volatile private var sLastDud: WeakReference<Any>? = null
    @Volatile private var sLastPhotoPager: WeakReference<ViewGroup>? = null
    @Volatile private var sCurrentActivity: WeakReference<Activity>? = null
    @Volatile private var sCurrentSelection = 0

    fun init(classLoader: ClassLoader) {
        // Track current foreground activity
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

        // 1. Hook PhotoItemViewData constructor to track media items
        try {
            val viewDataClass = XposedHelpers.findClass(
                "com.oplus.gallery.photo_page.viewmodel.dataloading.PhotoItemViewData",
                classLoader
            )
            XposedBridge.hookAllConstructors(viewDataClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.args.size >= 2 && param.args[0] != null && param.args[1] is Int) {
                        val dud = param.args[0]
                        val position = param.args[1] as Int
                        sItemMap[position] = WeakReference(dud)
                        sLastDud = WeakReference(dud)
                        XposedBridge.log("$TAG Tracked PhotoItemViewData: pos=$position, dud=${dud.javaClass.name}")
                    }
                }
            })
            XposedBridge.log("$TAG Hooked PhotoItemViewData constructors")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Hook PhotoItemViewData failed: $t")
        }

        // 2. Hook PhotoPager to track current selection and active view
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

            // Find PhotoPager
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

            // Lookup media object
            var dud: Any? = sItemMap[position]?.get()
            if (dud == null) {
                dud = sLastDud?.get()
            }

            var filePath: String? = null
            var mediaId: Long = -1

            if (dud != null) {
                filePath = extractFilePath(dud)
                mediaId = extractMediaId(dud)
                XposedBridge.log("$TAG Extracted from dud: path=$filePath, mediaId=$mediaId")
            }

            // Fallback 1: PhotoFragment arguments (via reflection to avoid compile/runtime classloader dependencies)
            if ((filePath.isNullOrEmpty()) && mediaId <= 0 && activity != null) {
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
                                            try {
                                                val idStr = mediaItemPath.substring(mediaItemPath.lastIndexOf('/') + 1)
                                                val parsedId = idStr.toLong()
                                                if (parsedId > 0) {
                                                    mediaId = parsedId
                                                }
                                            } catch (e: Throwable) {}
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

            // Fallback 2: Most recent MediaStore photo
            if ((filePath.isNullOrEmpty()) && mediaId <= 0) {
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

        try {
            val m: Method = dud.javaClass.getMethod("H")
            val res = m.invoke(dud)
            if (res is String && (res.startsWith("/") || res.startsWith("content://"))) {
                return res
            }
        } catch (e: Throwable) {}

        try {
            val f: Field = dud.javaClass.getField("o")
            val res = f.get(dud)
            if (res is String && (res.startsWith("/") || res.startsWith("content://"))) {
                return res
            }
        } catch (e: Throwable) {}

        var clz: Class<*>? = dud.javaClass
        while (clz != null && clz != Any::class.java) {
            for (field in clz.declaredFields) {
                if (field.type == String::class.java) {
                    try {
                        field.isAccessible = true
                        val value = field.get(dud) as? String
                        if (value != null && (value.startsWith("/storage/") || value.startsWith("/sdcard/"))) {
                            return value
                        }
                    } catch (e: Throwable) {}
                }
            }
            clz = clz.superclass
        }
        return null
    }

    private fun extractMediaId(dud: Any?): Long {
        if (dud == null) return -1

        try {
            val f: Field = dud.javaClass.getField("g")
            val res = f.get(dud)
            if (res is Number && res.toLong() > 0) {
                return res.toLong()
            }
        } catch (e: Throwable) {}

        var clz: Class<*>? = dud.javaClass
        while (clz != null && clz != Any::class.java) {
            for (field in clz.declaredFields) {
                if (field.type == Long::class.javaPrimitiveType || field.type == Int::class.javaPrimitiveType) {
                    try {
                        field.isAccessible = true
                        val value = field.getLong(dud)
                        if (value > 1000) {
                            return value
                        }
                    } catch (e: Throwable) {}
                }
            }
            clz = clz.superclass
        }
        return -1
    }
}
