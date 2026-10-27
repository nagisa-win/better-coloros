package win.nagisa.oppocameranormalization.util

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitBridge

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
                XposedBridge.log("[OppoCameraNormalization] Failed to initialize DexKit: ${t.message}")
                null
            }
        }

    fun close() {
        try {
            bridgeInstance?.close()
        } catch (_: Throwable) {
        } finally {
            bridgeInstance = null
        }
    }
}
