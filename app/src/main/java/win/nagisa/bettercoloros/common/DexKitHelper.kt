package win.nagisa.bettercoloros.common

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitBridge

/**
 * DexKit 动态 DEX 扫描管理器 (共享组件)
 */
object DexKitHelper {
    private var hostDir: String? = null
    private var bridgeInstance: DexKitBridge? = null
    private var loadFailed = false

    fun init(lpparam: LoadPackageParam) {
        hostDir = lpparam.appInfo.sourceDir
    }

    val bridge: DexKitBridge?
        get() {
            if (loadFailed) return null
            if (bridgeInstance != null) return bridgeInstance

            val apkPath = hostDir ?: return null
            return try {
                System.loadLibrary("dexkit")
                DexKitBridge.create(apkPath).also {
                    bridgeInstance = it
                }
            } catch (t: Throwable) {
                loadFailed = true
                XposedBridge.log("[BetterColorOS] Failed to initialize DexKit: ${t.message}")
                null
            }
        }

    fun close() {
        try {
            bridgeInstance?.close()
        } catch (e: Throwable) {
        } finally {
            bridgeInstance = null
        }
    }
}
