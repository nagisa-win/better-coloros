package win.nagisa.bettercoloros.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import win.nagisa.bettercoloros.R

@Composable
fun GalleryFeatureCard(modifier: Modifier = Modifier) {
    FeatureTile(
        title = stringResource(R.string.feature_gallery_title),
        subtitle = stringResource(R.string.feature_gallery_subtitle),
        icon = Icons.Default.ContentCopy,
        initiallyExpanded = true,
        modifier = modifier
    ) {
        Text(
            text = "使用指引",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "1. 打开 ColorOS 系统自带「相册」应用；\n2. 浏览并点开任意相片或截图；\n3. 点击右上角三个点「更多」菜单，列表末尾即可看到新增的「复制到剪贴板」选项；\n4. 点击后相片即以系统级标准图片数据注入剪贴板。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "💡 跨端协同提示：配合 KDE Connect、LocalSend、剪贴板同步工具或微信/QQ，在手机相册点击复制后，电脑即可直接 Ctrl+V / ⌘+V 粘贴原图，无损保留 EXIF 信息。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
