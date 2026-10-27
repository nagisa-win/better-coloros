package win.nagisa.oppocameranormalization.util

/**
 * OPPO / OnePlus 相机拍摄文件名规范化工具类
 *
 * 目标：
 * 将缺少下划线的非标准命名（例如 IMG20261027171025.jpg / VID20261027171025.mp4）
 * 规范化为符合 Android 行业通用标准的带下划线格式（IMG_20261027_171025.jpg / VID_20261027_171025.mp4）。
 *
 * 特性：
 * 1. 支持完整路径（/storage/emulated/0/DCIM/Camera/IMG...）或纯文件名、纯标题
 * 2. 完美保留连拍（_BURST01）、封面（_COVER）、慢动作等任意业务后缀与扩展名
 * 3. 幂等性保证：已经是标准格式（IMG_YYYYMMDD_HHmmss）的跳过，避免重复处理
 * 4. 支持半下划线情况（如 IMG_YYYYMMDDHHmmss 或 IMGYYYYMMDD_HHmmss 均可统一纠正）
 */
object FilenameNormalizer {

    // 匹配照片前缀与时间戳：可选路径 + IMG + 可选_ + 8位年月日 + 可选_ + 6位时分秒 + 后续内容
    private val PHOTO_REGEX = Regex("""^(.*?)IMG_?(\d{4})(\d{2})(\d{2})_?(\d{2})(\d{2})(\d{2})(.*)$""")

    // 匹配视频前缀与时间戳：可选路径 + VID + 可选_ + 8位年月日 + 可选_ + 6位时分秒 + 后续内容
    private val VIDEO_REGEX = Regex("""^(.*?)VID_?(\d{4})(\d{2})(\d{2})_?(\d{2})(\d{2})(\d{2})(.*)$""")

    /**
     * 判断字符串中是否包含需要被规范化的文件名
     */
    fun needNormalize(input: String?): Boolean {
        if (input.isNullOrEmpty()) return false
        // 已经是标准的 IMG_YYYYMMDD_HHmmss 或 VID_YYYYMMDD_HHmmss 则无需处理
        if (input.contains(Regex("""(IMG|VID)_\d{8}_\d{6}"""))) {
            return false
        }
        return PHOTO_REGEX.matches(input) || VIDEO_REGEX.matches(input)
    }

    /**
     * 执行规范化转换
     */
    fun normalize(input: String?): String? {
        if (input.isNullOrEmpty()) return input

        // 幂等性检查：已经是标准格式，直接原样返回
        if (input.contains(Regex("""(IMG|VID)_\d{8}_\d{6}"""))) {
            return input
        }

        // 处理照片
        PHOTO_REGEX.matchEntire(input)?.let { match ->
            val prefix = match.groupValues[1]
            val year = match.groupValues[2]
            val month = match.groupValues[3]
            val day = match.groupValues[4]
            val hour = match.groupValues[5]
            val min = match.groupValues[6]
            val sec = match.groupValues[7]
            val suffix = match.groupValues[8]
            return "${prefix}IMG_${year}${month}${day}_${hour}${min}${sec}${suffix}"
        }

        // 处理视频
        VIDEO_REGEX.matchEntire(input)?.let { match ->
            val prefix = match.groupValues[1]
            val year = match.groupValues[2]
            val month = match.groupValues[3]
            val day = match.groupValues[4]
            val hour = match.groupValues[5]
            val min = match.groupValues[6]
            val sec = match.groupValues[7]
            val suffix = match.groupValues[8]
            return "${prefix}VID_${year}${month}${day}_${hour}${min}${sec}${suffix}"
        }

        return input
    }
}
