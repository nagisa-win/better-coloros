package win.nagisa.bettercoloros.common

import android.content.Context
import android.widget.Toast
import java.io.DataOutputStream

/**
 * 进程管理与 Hook 生效辅助工具
 */
object ProcessHelper {

    fun restartTargets(context: Context) {
        val targets = listOf("com.oplus.camera", "com.coloros.gallery3d")
        Thread {
            var rootSuccess = false
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                for (target in targets) {
                    os.writeBytes("am force-stop $target\n")
                }
                os.writeBytes("exit\n")
                os.flush()
                val code = process.waitFor()
                rootSuccess = (code == 0)
            } catch (_: Throwable) {
                rootSuccess = false
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (rootSuccess) {
                    Toast.makeText(context, "已通过 Root 权限重启相机与相册！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "请在多任务中上滑彻底关闭「相机」和「相册」以使配置生效", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
