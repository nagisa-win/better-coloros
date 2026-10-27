package win.nagisa.bettercoloros.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import win.nagisa.bettercoloros.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetterColorOSApp(
    isModuleActive: Boolean,
    modifier: Modifier = Modifier
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ColorOS Experience Enhancement Suite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 600.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = if (isWideScreen) 24.dp else 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter)
                    .widthIn(max = 960.dp), // 在平板与折叠屏大屏下限制最大宽度，保证美观居中
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 顶层状态看板
                StatusHeroCard(isModuleActive = isModuleActive)

                // 2. 功能集合区 (在折叠屏/平板下采用双列豆腐块并排布局)
                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CameraFeatureCard(modifier = Modifier.weight(1f))
                        GalleryFeatureCard(modifier = Modifier.weight(1f))
                    }
                } else {
                    CameraFeatureCard()
                    GalleryFeatureCard()
                }

                // 3. 系统设置与工具卡片 (隐藏图标、重启应用)
                SettingsCard()

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "关于 Better ColorOS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "版本：1.0.0 (Build 100)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "项目架构：Harness 工程体系规范驱动，支持持续扩展更多 ColorOS 系统优化 Hook。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "开源仓库：https://github.com/nagisa-win/better-coloros",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("我知道了")
                }
            }
        )
    }
}
