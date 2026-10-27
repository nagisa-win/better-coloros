package win.nagisa.oppocameranormalization

import com.github.kyuubiran.ezxhelper.EzXHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import win.nagisa.oppocameranormalization.hook.CameraFilenameHook
import win.nagisa.oppocameranormalization.util.DexKitHelper

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "OppoCameraNormalization"
        const val MODULE_PACKAGE = "win.nagisa.oppocameranormalization"
        val TARGET_PACKAGES = setOf(
            "com.oplus.camera",
            "com.coloros.gallery3d"
        )
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // Zygote 初始化阶段
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 自检：在模块自身界面中标记为已激活
        if (lpparam.packageName == MODULE_PACKAGE) {
            try {
                XposedHelpers.findAndHookMethod(
                    "$MODULE_PACKAGE.ui.MainActivity",
                    lpparam.classLoader,
                    "isModuleActive",
                    XC_MethodReplacement.returnConstant(true)
                )
            } catch (_: Throwable) {
            }
            return
        }

        // 目标 App 注入
        if (lpparam.packageName in TARGET_PACKAGES) {
            try {
                if (lpparam.isFirstApplication) {
                    EzXHelper.initHandleLoadPackage(lpparam)
                    EzXHelper.setLogTag(TAG)
                    EzXHelper.setToastTag(TAG)
                    DexKitHelper.init(lpparam)
                }

                XposedBridge.log("[$TAG] Activated for package: ${lpparam.packageName} (process: ${lpparam.processName})")

                // 初始化相机文件名拦截器
                CameraFilenameHook.init(lpparam)

            } catch (t: Throwable) {
                XposedBridge.log("[$TAG] Error initializing module for ${lpparam.packageName}: ${t.message}")
                t.printStackTrace()
            }
        }
    }
}
