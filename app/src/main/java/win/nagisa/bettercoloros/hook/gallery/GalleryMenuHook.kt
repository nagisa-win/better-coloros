package win.nagisa.bettercoloros.hook.gallery

import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import win.nagisa.bettercoloros.hook.BaseHook
import java.util.WeakHashMap

/**
 * 相册右上角三点「更多」菜单注入 Hook
 */
object GalleryMenuHook : BaseHook() {
    private const val TAG = "[BetterColorOS][GalleryCopy] "
    const val MENU_TITLE = "复制到剪贴板"
    const val MENU_CUSTOM_ID = 99999

    private val sWindowToOriginalSize = WeakHashMap<Any, Int>()
    private val sWindowToOurItem = WeakHashMap<Any, Any>()

    override fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (isInit) return
        isInit = true

        PhotoDataExtractor.init(lpparam.classLoader)

        try {
            val popupWindowClass = XposedHelpers.findClass(
                "com.coui.appcompat.poplist.COUIPopupListWindow",
                lpparam.classLoader
            )
            val popupItemClass = XposedHelpers.findClass(
                "com.coui.appcompat.poplist.PopupListItem",
                lpparam.classLoader
            )

            val setItemListHook = object : XC_MethodHook() {
                @Suppress("UNCHECKED_CAST")
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args.isEmpty() || param.args[0] !is List<*>) {
                        return
                    }
                    val list = param.args[0] as? MutableList<Any?> ?: return
                    if (list.isEmpty()) return

                    var isPhotoMenu = false
                    var alreadyAdded = false

                    for (item in list) {
                        if (item == null) continue
                        var title: String? = null
                        try {
                            val t = XposedHelpers.callMethod(item, "getTitle")
                            if (t != null) title = t.toString()
                        } catch (_: Throwable) {}

                        if (MENU_TITLE == title) {
                            alreadyAdded = true
                            break
                        }
                        if (isPhotoMenuMarker(title)) {
                            isPhotoMenu = true
                        }
                    }

                    if (isPhotoMenu && !alreadyAdded) {
                        val origSize = list.size
                        sWindowToOriginalSize[param.thisObject] = origSize

                        val newItem = createPopupItem(popupItemClass, MENU_TITLE)
                        if (newItem != null) {
                            sWindowToOurItem[param.thisObject] = newItem
                            list.add(newItem)
                            XposedBridge.log("$TAG Successfully appended 「$MENU_TITLE」 to popup menu (origSize=$origSize, newSize=${list.size})")
                        }
                    }
                }
            }

            try {
                XposedHelpers.findAndHookMethod(popupWindowClass, "setItemList", List::class.java, setItemListHook)
            } catch (t: Throwable) {
                XposedBridge.log("$TAG Hook setItemList(List) failed: $t")
            }

            try {
                XposedHelpers.findAndHookMethod(popupWindowClass, "setItemList", List::class.java, Boolean::class.javaPrimitiveType, setItemListHook)
            } catch (_: Throwable) {}

            try {
                XposedHelpers.findAndHookMethod(
                    popupWindowClass,
                    "setOnItemClickListener",
                    AdapterView.OnItemClickListener::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val originalListener = param.args[0] as? AdapterView.OnItemClickListener ?: return
                            if (originalListener is CopyClickListenerProxy) {
                                return
                            }
                            val windowInstance = param.thisObject
                            param.args[0] = CopyClickListenerProxy(originalListener, windowInstance)
                            XposedBridge.log("$TAG Wrapped AdapterView.OnItemClickListener")
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log("$TAG Hook setOnItemClickListener failed: $t")
            }

            XposedBridge.log("$TAG GalleryMenuHook initialized successfully")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG GalleryMenuHook initialization failed: $t")
        }
    }

    private fun isPhotoMenuMarker(title: String?): Boolean {
        if (title == null) return false
        val t = title.trim()
        return t.contains("壁纸") ||
                t.contains("详细信息") ||
                t.contains("重命名") ||
                t.contains("转为") ||
                t.contains("PDF") ||
                t.contains("幻灯片") ||
                t.contains("私密") ||
                t.contains("移动到") ||
                t.contains("图集") ||
                t.equals("Details", ignoreCase = true) ||
                t.lowercase().contains("wallpaper") ||
                t.lowercase().contains("rename") ||
                t.lowercase().contains("slideshow")
    }

    private fun createPopupItem(itemClass: Class<*>, title: String): Any? {
        try {
            val item = XposedHelpers.newInstance(itemClass, title, true)
            try {
                XposedHelpers.callMethod(item, "setId", MENU_CUSTOM_ID)
            } catch (_: Throwable) {}
            return item
        } catch (_: Throwable) {}

        try {
            val item = XposedHelpers.newInstance(itemClass)
            XposedHelpers.callMethod(item, "setTitle", title)
            try {
                XposedHelpers.callMethod(item, "setEnable", true)
            } catch (_: Throwable) {}
            try {
                XposedHelpers.callMethod(item, "setId", MENU_CUSTOM_ID)
            } catch (_: Throwable) {}
            return item
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Failed to create PopupListItem: $t")
        }
        return null
    }

    private fun isTitleFoundInView(view: View?, target: String?): Boolean {
        if (view == null || target == null) return false
        if (view is TextView) {
            val cs = view.text
            if (cs != null && cs.toString().contains(target)) {
                return true
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (isTitleFoundInView(view.getChildAt(i), target)) {
                    return true
                }
            }
        }
        return false
    }

    private class CopyClickListenerProxy(
        private val original: AdapterView.OnItemClickListener?,
        private val window: Any
    ) : AdapterView.OnItemClickListener {

        override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val origSize = sWindowToOriginalSize[window]
            val ourItem = sWindowToOurItem[window]

            var isOurItem = false

            if (isTitleFoundInView(view, MENU_TITLE)) {
                isOurItem = true
            }

            if (!isOurItem && origSize != null && position >= origSize) {
                isOurItem = true
            }

            if (!isOurItem) {
                var item: Any? = null
                try {
                    if (parent?.adapter != null) {
                        item = parent.adapter.getItem(position)
                    }
                } catch (_: Throwable) {}
                if (item == null && parent != null) {
                    try {
                        item = parent.getItemAtPosition(position)
                    } catch (_: Throwable) {}
                }

                if (item != null) {
                    if (ourItem != null && item === ourItem) {
                        isOurItem = true
                    } else {
                        try {
                            val t = XposedHelpers.callMethod(item, "getTitle")
                            if (t != null && t.toString().contains(MENU_TITLE)) {
                                isOurItem = true
                            }
                            val itemId = XposedHelpers.callMethod(item, "getId") as? Int
                            if (itemId != null && itemId == MENU_CUSTOM_ID) {
                                isOurItem = true
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }

            if (isOurItem) {
                XposedBridge.log("$TAG Intercepted 「$MENU_TITLE」 click at pos $position! Handling copy...")
                try {
                    XposedHelpers.callMethod(window, "dismiss")
                } catch (_: Throwable) {}
                try {
                    PhotoDataExtractor.handleCopy(window)
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG handleCopy error: $t")
                }
                return
            }

            if (origSize != null && position >= origSize) {
                try {
                    XposedHelpers.callMethod(window, "dismiss")
                } catch (_: Throwable) {}
                try {
                    PhotoDataExtractor.handleCopy(window)
                } catch (_: Throwable) {}
                return
            }

            original?.onItemClick(parent, view, position, id)
        }
    }
}
