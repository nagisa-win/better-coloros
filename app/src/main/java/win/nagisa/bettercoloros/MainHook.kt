package win.nagisa.bettercoloros

import com.github.kyuubiran.ezxhelper.EzXHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import win.nagisa.bettercoloros.common.DexKitHelper
import win.nagisa.bettercoloros.hook.camera.CameraFilenameHook
import win.nagisa.bettercoloros.hook.gallery.GalleryMenuHook

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "BetterColorOS"
        const val MODULE_PACKAGE = "win.nagisa.bettercoloros"

        const val PKG_CAMERA = "com.oplus.camera"
        const val PKG_GALLERY = "com.coloros.gallery3d"

        val TARGET_PACKAGES = setOf(PKG_CAMERA, PKG_GALLERY)
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // Zygote 阶段
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 模块自身自检
        if (lpparam.packageName == MODULE_PACKAGE) {
            val replaceTrue = XC_MethodReplacement.returnConstant(true)

            // 1. Hook MainActivity.isModuleActive (静态方法)
            runCatching {
                XposedHelpers.findAndHookMethod(
                    "$MODULE_PACKAGE.ui.MainActivity",
                    lpparam.classLoader,
                    "isModuleActive",
                    replaceTrue
                )
            }

            // 2. Hook MainActivity$Companion.isModuleActive (Kotlin 伴生对象虚拟方法)
            runCatching {
                XposedHelpers.findAndHookMethod(
                    "$MODULE_PACKAGE.ui.MainActivity\$Companion",
                    lpparam.classLoader,
                    "isModuleActive",
                    replaceTrue
                )
            }

            // 3. Hook MainActivity.isModuleActivated (实例方法)
            runCatching {
                XposedHelpers.findAndHookMethod(
                    "$MODULE_PACKAGE.ui.MainActivity",
                    lpparam.classLoader,
                    "isModuleActivated",
                    replaceTrue
                )
            }

            XposedBridge.log("[$TAG] Successfully hooked module activation check in self process")
            return
        }

        // 目标应用注入
        if (lpparam.packageName in TARGET_PACKAGES) {
            try {
                if (lpparam.isFirstApplication) {
                    EzXHelper.initHandleLoadPackage(lpparam)
                    EzXHelper.setLogTag(TAG)
                    EzXHelper.setToastTag(TAG)
                    DexKitHelper.init(lpparam)
                }

                XposedBridge.log("[$TAG] Activated in ${lpparam.packageName} (${lpparam.processName})")

                when (lpparam.packageName) {
                    PKG_CAMERA -> {
                        CameraFilenameHook.init(lpparam)
                    }
                    PKG_GALLERY -> {
                        GalleryMenuHook.init(lpparam)
                        // 相册也同时加载文件名规范化，确保在相册内另存/编辑时保持一致格式
                        CameraFilenameHook.init(lpparam)
                    }
                }

            } catch (t: Throwable) {
                XposedBridge.log("[$TAG] Error initializing in ${lpparam.packageName}: ${t.message}")
                t.printStackTrace()
            }
        }
    }
}
