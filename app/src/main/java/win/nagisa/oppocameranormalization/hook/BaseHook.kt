package win.nagisa.oppocameranormalization.hook

import de.robv.android.xposed.callbacks.XC_LoadPackage

abstract class BaseHook {
    var isInit = false
    abstract fun init(lpparam: XC_LoadPackage.LoadPackageParam)
}
