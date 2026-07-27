package com.chuishui.otheme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主题信息详情界面（查看已安装主题）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDetailScreen(
    navController: NavController,
    fileService: IFileService?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var themeInfo by remember { mutableStateOf<ThemeInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            try {
                // 获取当前运行模式
                val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                val executionMode = prefs.getString("execution_mode", "SU") ?: "SU"
                
                val xmlContent = if (executionMode == "SHIZUKU") {
                    withContext(Dispatchers.IO) {
                        fileService?.getInstalledThemeInfo()
                    }
                } else {
                    // SU 模式
                    withContext(Dispatchers.IO) {
                        SuFileOperations.getInstalledThemeInfo()
                    }
                }
                
                if (xmlContent != null) {
                    themeInfo = ThemeParser.parseThemeInfoFromXml(xmlContent)
                } else {
                    errorMessage = "未找到主题信息文件"
                }
            } catch (e: Exception) {
                errorMessage = "读取主题信息失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "主题信息",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "$errorMessage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    ThemeDetailContent(themeInfo = themeInfo)
                }
            }
        }
    }
}
