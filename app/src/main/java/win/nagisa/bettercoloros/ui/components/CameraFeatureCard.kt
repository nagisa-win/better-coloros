package win.nagisa.bettercoloros.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import win.nagisa.bettercoloros.R
import win.nagisa.bettercoloros.common.FilenameNormalizer
import win.nagisa.bettercoloros.ui.theme.SuccessGreen

@Composable
fun CameraFeatureCard(modifier: Modifier = Modifier) {
    var testInput by remember { mutableStateOf("IMG20261027171025.jpg") }
    val normalizedResult by remember(testInput) {
        derivedStateOf { FilenameNormalizer.normalize(testInput) ?: "" }
    }

    FeatureTile(
        title = stringResource(R.string.feature_camera_title),
        subtitle = stringResource(R.string.feature_camera_subtitle),
        icon = Icons.Default.PhotoCamera,
        initiallyExpanded = true,
        modifier = modifier
    ) {
        Text(
            text = "工作机制说明",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "通过 DexKit 扫描 + MediaStore 媒体库 + File 构造多层纵深防御，将拍摄照片与视频从紧凑格式自动纠正为 Android 行业标准带下划线命名。完整兼容连拍（_BURST）、实况封面（_COVER）、HEIC/DNG 等多格式，无损且幂等。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "规则即时测试",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = testInput,
            onValueChange = { testInput = it },
            label = { Text("输入测试文件名 (可编辑)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "规范化输出：",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = normalizedResult,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = SuccessGreen,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
