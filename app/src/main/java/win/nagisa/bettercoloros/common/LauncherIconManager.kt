package win.nagisa.bettercoloros.common

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面启动器图标管理器
 *
 * 通过动态配置 AndroidManifest 中声明的 ActivityAlias 状态，
 * 实现隐藏或显示桌面图标，且在图标隐藏后依然允许通过 LSPosed 管理器直接启动应用。
 */
object LauncherIconManager {
    private const val ALIAS_CLASS = "win.nagisa.bettercoloros.ui.LauncherAlias"

    /**
     * 判断当前桌面启动器图标是否可见
     */
    fun isIconVisible(context: Context): Boolean {
        return try {
            val component = ComponentName(context, ALIAS_CLASS)
            val state = context.packageManager.getComponentEnabledSetting(component)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (e: Throwable) {
            true
        }
    }

    /**
     * 切换桌面图标可见性
     * @param visible true 显示图标，false 隐藏图标
     */
    fun setIconVisibility(context: Context, visible: Boolean) {
        try {
            val component = ComponentName(context, ALIAS_CLASS)
            val newState = if (visible) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP
            )
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
