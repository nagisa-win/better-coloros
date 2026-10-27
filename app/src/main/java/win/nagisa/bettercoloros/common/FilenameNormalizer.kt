package win.nagisa.bettercoloros.common

/**
 * OPPO / OnePlus 相机拍摄文件名规范化工具类 (共享组件)
 *
 * 目标：
 * 将缺少下划线的非标准命名（例如 IMG20261027171025.jpg / VID20261027171025.mp4 / MOD20261027171025.mp4）
 * 规范化为符合 Android 行业通用标准的带下划线格式（IMG_20261027_171025.jpg / VID_20261027_171025.mp4 / MOD_20261027_171025.mp4）。
 */
object FilenameNormalizer {

    private val PHOTO_REGEX = Regex("""^(.*?)IMG_?(\d{4})(\d{2})(\d{2})_?(\d{2})(\d{2})(\d{2})(.*)$""")
    private val VIDEO_REGEX = Regex("""^(.*?)VID_?(\d{4})(\d{2})(\d{2})_?(\d{2})(\d{2})(\d{2})(.*)$""")
    private val MOD_REGEX = Regex("""^(.*?)MOD_?(\d{4})(\d{2})(\d{2})_?(\d{2})(\d{2})(\d{2})(.*)$""")

    fun needNormalize(input: String?): Boolean {
        if (input.isNullOrEmpty()) return false
        if (input.contains(Regex("""(IMG|VID|MOD)_\d{8}_\d{6}"""))) {
            return false
        }
        return PHOTO_REGEX.matches(input) || VIDEO_REGEX.matches(input) || MOD_REGEX.matches(input)
    }

    fun normalize(input: String?): String? {
        if (input.isNullOrEmpty()) return input

        if (input.contains(Regex("""(IMG|VID|MOD)_\d{8}_\d{6}"""))) {
            return input
        }

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

        MOD_REGEX.matchEntire(input)?.let { match ->
            val prefix = match.groupValues[1]
            val year = match.groupValues[2]
            val month = match.groupValues[3]
            val day = match.groupValues[4]
            val hour = match.groupValues[5]
            val min = match.groupValues[6]
            val sec = match.groupValues[7]
            val suffix = match.groupValues[8]
            return "${prefix}MOD_${year}${month}${day}_${hour}${min}${sec}${suffix}"
        }

        return input
    }
}
