package win.nagisa.bettercoloros.hook.gallery

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.lang.reflect.Method

/**
 * 相册图片复制到剪贴板辅助类
 *
 * 核心设计：
 * 1. 优先使用宿主应用自有的 FileProvider (com.oplus.gallery.FileProvider) 生成 content URI。
 *    宿主自身即为 Provider 拥有者，彻底解决 Android 14/15/16/17 (ColorOS 15+)
 *    系统在 ClipboardService.setPrimaryClip 时因 checkUriOwner 抛出的
 *    "SecurityException: UID does not have permission to content://media/..." 权限拒绝问题。
 * 2. 依托 Android 系统的标准剪贴板权限委托体系：
 *    相册自身的 GalleryFileProvider 声明了 android:grantUriPermissions="true"，
 *    当 setPrimaryClip 成功后，系统服务 ClipboardService (mPermissionOwner) 会在任意
 *    消费方（输入法预览条、微信、QQ、便签等）读取剪贴板时自动、动态派发临时只读授权，
 *    无需也不应硬编码维护任何第三方包名白名单。
 * 3. 保留 MediaStore URI 双向回退兜底，确保对低版本 ColorOS (Android 10~13) 的无缝兼容。
 */
object ClipboardHelper {
    private const val TAG = "[BetterColorOS][GalleryCopy] "

    fun copyImageToClipboard(context: Context?, rawFilePath: String?, rawMediaId: Long) {
        if (context == null) {
            XposedBridge.log("$TAG Context is null, cannot copy")
            return
        }

        Handler(Looper.getMainLooper()).post {
            try {
                // 放宽 StrictMode 限制，防止跨进程流传递受阻
                try {
                    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
                } catch (e: Throwable) {}

                var filePath = rawFilePath
                var mediaId = rawMediaId

                // 若路径有效但 mediaId 未知，通过 MediaStore 补全 ID
                if (!filePath.isNullOrEmpty() && mediaId <= 0 && !filePath.startsWith("content://")) {
                    mediaId = queryMediaIdByPath(context, filePath)
                }

                // 若 mediaId 有效但路径未知，通过 MediaStore 补全文件物理路径
                if (filePath.isNullOrEmpty() && mediaId > 0) {
                    filePath = queryPathByMediaId(context, mediaId)
                }

                // 1. 优先尝试解析 FileProvider URI（完美避开 Android 14+ 剪贴板校验限制）
                var fileUri: Uri? = null
                if (!filePath.isNullOrEmpty() && !filePath.startsWith("content://")) {
                    val file = File(filePath)
                    if (file.exists()) {
                        fileUri = resolveFileProviderUri(context, file)
                    }
                }

                // 2. 次选解析 MediaStore 原生 URI（兼容旧系统或无直接文件路径场景）
                val mediaUri: Uri? = if (mediaId > 0) {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
                } else null

                // 决定主注入 URI 与备用 URI
                val primaryUri = fileUri ?: mediaUri
                val backupUri = if (primaryUri === fileUri) mediaUri else fileUri

                if (primaryUri == null) {
                    XposedBridge.log("$TAG Failed to resolve any valid URI for path: $filePath, mediaId: $mediaId")
                    Toast.makeText(context, "未能获取图片地址", Toast.LENGTH_SHORT).show()
                    return@post
                }

                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (cm == null) {
                    Toast.makeText(context, "剪贴板服务不可用", Toast.LENGTH_SHORT).show()
                    return@post
                }

                // 确定 MIME 类型
                var mimeType = determineMimeType(context, primaryUri, filePath)
                if (mimeType == null || !mimeType.startsWith("image/")) {
                    mimeType = "image/jpeg"
                }

                XposedBridge.log("$TAG Setting clipboard image: primaryUri=$primaryUri, backupUri=$backupUri, mime=$mimeType, path=$filePath")

                val description = ClipDescription("Image", arrayOf(mimeType, "image/*"))
                val clipData = ClipData(description, ClipData.Item(primaryUri))

                try {
                    cm.setPrimaryClip(clipData)
                    Toast.makeText(context, "图片已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    XposedBridge.log("$TAG PrimaryClip set successfully with: $primaryUri")
                } catch (secEx: SecurityException) {
                    // 若首选 URI 被系统安全校验拦截（如旧系统或定制 ROM 对某些 Provider 校验不同），自动回退重试备用 URI
                    XposedBridge.log("$TAG SecurityException setting primaryClip: ${secEx.message}, trying backupUri=$backupUri")
                    if (backupUri != null && backupUri != primaryUri) {
                        val fallbackClip = ClipData(description, ClipData.Item(backupUri))
                        cm.setPrimaryClip(fallbackClip)
                        Toast.makeText(context, "图片已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        XposedBridge.log("$TAG PrimaryClip set successfully with backup: $backupUri")
                    } else {
                        throw secEx
                    }
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG Error copying to clipboard: ${t.message}")
                Toast.makeText(context, "复制失败: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 解析相册自带的 FileProvider URI
     */
    fun resolveFileProviderUri(context: Context, file: File): Uri? {
        val candidateClasses = arrayOf(
            "com.oplus.gallery.foundation.fileaccess.GalleryFileProvider",
            "androidx.core.content.FileProvider"
        )
        val candidateAuthorities = arrayOf(
            "com.oplus.gallery.FileProvider",
            "com.coloros.gallery3d.fileprovider",
            "com.coloros.gallery3d.fileProvider",
            "${context.packageName}.FileProvider",
            "${context.packageName}.fileprovider"
        )

        for (className in candidateClasses) {
            val fpClass = runCatching {
                XposedHelpers.findClass(className, context.classLoader)
            }.getOrNull() ?: continue

            for (auth in candidateAuthorities) {
                try {
                    val getUriMethod: Method = fpClass.getMethod(
                        "getUriForFile",
                        Context::class.java,
                        String::class.java,
                        File::class.java
                    )
                    val uri = getUriMethod.invoke(null, context, auth, file) as? Uri
                    if (uri != null) {
                        XposedBridge.log("$TAG Resolved FileProvider URI via $auth ($className): $uri")
                        return uri
                    }
                } catch (e: Throwable) {
                    // 尝试下一个 Authority
                }
            }
        }
        return null
    }

    private fun queryMediaIdByPath(context: Context, filePath: String): Long {
        val resolver = context.contentResolver
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DATA} = ?",
                arrayOf(filePath),
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                return cursor.getLong(idCol)
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG queryMediaIdByPath failed for $filePath: ${t.message}")
        } finally {
            cursor?.close()
        }

        // 尝试通过 DISPLAY_NAME 再次查询兜底
        val file = File(filePath)
        if (file.exists()) {
            try {
                cursor = resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                    arrayOf(file.name),
                    null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    return cursor.getLong(idCol)
                }
            } catch (t: Throwable) {
            } finally {
                cursor?.close()
            }
        }
        return -1L
    }

    private fun queryPathByMediaId(context: Context, mediaId: Long): String? {
        val resolver = context.contentResolver
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATA),
                "${MediaStore.Images.Media._ID} = ?",
                arrayOf(mediaId.toString()),
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return cursor.getString(dataCol)
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG queryPathByMediaId failed for $mediaId: ${t.message}")
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun determineMimeType(context: Context, uri: Uri?, filePath: String?): String? {
        if (uri != null && ContentResolver.SCHEME_CONTENT == uri.scheme) {
            try {
                val type = context.contentResolver.getType(uri)
                if (!type.isNullOrEmpty()) {
                    return type
                }
            } catch (e: Throwable) {}
        }

        if (filePath != null) {
            val dot = filePath.lastIndexOf('.')
            if (dot >= 0) {
                val ext = filePath.substring(dot + 1).lowercase()
                val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                if (!type.isNullOrEmpty()) {
                    return type
                }
            }
        }
        return "image/jpeg"
    }
}
