package win.nagisa.bettercoloros.hook.gallery

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
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
 */
object ClipboardHelper {
    private const val TAG = "[BetterColorOS][GalleryCopy] "

    fun copyImageToClipboard(context: Context?, filePath: String?, mediaId: Long) {
        if (context == null) {
            XposedBridge.log("$TAG Context is null, cannot copy")
            return
        }

        Handler(Looper.getMainLooper()).post {
            try {
                try {
                    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
                } catch (_: Throwable) {}

                val imageUri = resolveImageUri(context, filePath, mediaId)
                if (imageUri == null) {
                    XposedBridge.log("$TAG Failed to resolve image URI for path: $filePath, mediaId: $mediaId")
                    Toast.makeText(context, "未能获取图片地址", Toast.LENGTH_SHORT).show()
                    return@post
                }

                var mimeType = determineMimeType(context, imageUri, filePath)
                if (mimeType == null || !mimeType.startsWith("image/")) {
                    mimeType = "image/jpeg"
                }
                XposedBridge.log("$TAG Copying image: uri=$imageUri, mime=$mimeType, path=$filePath")

                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (cm == null) {
                    Toast.makeText(context, "剪贴板服务不可用", Toast.LENGTH_SHORT).show()
                    return@post
                }

                val description = ClipDescription("Image", arrayOf(mimeType, "image/*"))
                val item = ClipData.Item(imageUri)
                val clipData = ClipData(description, item)

                if (ContentResolver.SCHEME_CONTENT == imageUri.scheme) {
                    val packages = arrayOf(
                        "com.android.systemui",
                        "com.google.android.inputmethod.latin",
                        "com.baidu.input_oppo",
                        "com.sohu.inputmethod.sogou.oppo",
                        "com.tencent.mm",
                        "com.tencent.mobileqq",
                        "org.kde.kdeconnect_tp"
                    )
                    for (pkg in packages) {
                        try {
                            context.grantUriPermission(pkg, imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Throwable) {}
                    }
                }

                cm.setPrimaryClip(clipData)
                Toast.makeText(context, "图片已复制到剪贴板", Toast.LENGTH_SHORT).show()
                XposedBridge.log("$TAG PrimaryClip set successfully: uri=$imageUri")
            } catch (t: Throwable) {
                XposedBridge.log("$TAG Error copying to clipboard: ${t.message}")
                Toast.makeText(context, "复制失败: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveImageUri(context: Context, filePath: String?, mediaId: Long): Uri? {
        if (mediaId > 0) {
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        }

        if (!filePath.isNullOrEmpty()) {
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
                    val id = cursor.getLong(idCol)
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG MediaStore query by DATA failed: ${t.message}")
            } finally {
                cursor?.close()
            }

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
                        val id = cursor.getLong(idCol)
                        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG MediaStore query by DISPLAY_NAME failed: ${t.message}")
                } finally {
                    cursor?.close()
                }

                try {
                    val fpClass = XposedHelpers.findClass("androidx.core.content.FileProvider", context.classLoader)
                    val getUriMethod: Method = fpClass.getMethod("getUriForFile", Context::class.java, String::class.java, File::class.java)
                    val authorities = arrayOf(
                        "com.oplus.gallery.FileProvider",
                        "com.coloros.gallery3d.fileprovider",
                        "${context.packageName}.FileProvider",
                        "${context.packageName}.fileprovider"
                    )
                    for (auth in authorities) {
                        try {
                            val uri = getUriMethod.invoke(null, context, auth, file) as? Uri
                            if (uri != null) {
                                XposedBridge.log("$TAG Resolved FileProvider URI ($auth): $uri")
                                return uri
                            }
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}

                return Uri.fromFile(file)
            }
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
            } catch (_: Throwable) {}
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
