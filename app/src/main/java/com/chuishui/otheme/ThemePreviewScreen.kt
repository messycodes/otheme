package com.chuishui.otheme

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePreviewScreen(
    navController: NavController,
    themeInfo: ThemeInfo?,
    isInvalidTheme: Boolean,
    tempFilePath: String,
    onInstallComplete: (String) -> Unit,
    window: android.view.Window
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isInstalling by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showInstallLogDialog by remember { mutableStateOf(false) }
    var installLogs by remember { mutableStateOf(listOf<String>()) }
    var installSucceeded by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    fun appendLog(line: String) {
        installLogs = installLogs + line
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isInvalidTheme) "警告" else "主题预览",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        enabled = !isInstalling
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { navController.navigateUp() },
                        enabled = !isInstalling,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                clearAppCache(context)
                                installLogs = listOf()
                                installSucceeded = null
                                showInstallLogDialog = true
                                isInstalling = true
                                installTheme(
                                    context = context,
                                    tempFilePath = tempFilePath,
                                    onLog = { line -> appendLog(line) },
                                    onComplete = { success, message ->
                                        isInstalling = false
                                        installSucceeded = success
                                        if (!success) {
                                            appendLog("[FAIL] $message")
                                        }
                                    }
                                )
                            }
                        },
                        enabled = !isInstalling,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "安装",
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isInvalidTheme) {
                // 无效主题警告
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "无法读取主题信息",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "这可能不是一个有效的主题文件，或者缺少 themeInfo.xml 文件。\n\n确定要安装吗？",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            } else {
                ThemeDetailContent(themeInfo = themeInfo)
            }
        }
    }
        
        // 安装日志弹窗：安装期间实时显示日志，完成后展示结果并继续流程
        if (showInstallLogDialog) {
            InstallLogDialog(
                logs = installLogs,
                isRunning = isInstalling,
                succeeded = installSucceeded,
                onContinue = {
                    showInstallLogDialog = false
                    if (installSucceeded == true) {
                        showRestartDialog = true
                    } else {
                        onInstallComplete(installLogs.lastOrNull { it.startsWith("[FAIL]") || it.startsWith("[ERR]") } ?: "安装失败")
                        navController.navigateUp()
                    }
                }
            )
        }
        
        // 安装完成确认对话框（带模糊效果）
        if (showRestartDialog) {
            BlurDialog {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "安装完成",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "主题安装成功！\n\n是否立即重启以应用主题？",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        showRestartDialog = false
                                        onInstallComplete("主题安装成功！\n请稍后手动重启以应用主题")
                                        navController.navigateUp()
                                    }
                                ) {
                                    Text("稍后重启")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        showRestartDialog = false
                                        scope.launch {
                                            try {
                                                val packages = listOf(
                                                    "com.android.systemui",
                                                    "com.android.settings",
                                                    "com.android.launcher3"
                                                )
                                                
                                                val error = withContext(Dispatchers.IO) {
                                                    SuFileOperations.restartProcesses(packages)
                                                }
                                                
                                                if (error == null) {
                                                    onInstallComplete("重启已触发！")
                                                } else {
                                                    onInstallComplete("重启失败：$error")
                                                }
                                            } catch (e: Exception) {
                                                onInstallComplete("重启失败：${e.message}")
                                            }
                                            navController.navigateUp()
                                        }
                                    }
                                ) {
                                    Text("立即重启")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun clearAppCache(context: android.content.Context) {
    try {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name != "temp_theme.theme") {
                file.deleteRecursively()
            }
        }
    } catch (_: Exception) { }
}

private suspend fun installTheme(
    context: android.content.Context,
    tempFilePath: String,
    onLog: (String) -> Unit,
    onComplete: (Boolean, String) -> Unit
) {

    try {
        onLog("[+] 检测主题商店是否已安装...")
        // 检查 ThemeStore 是否安装（先检测 heytap，失败再检测 oplus）
        val themeStoreInstalled = withContext(Dispatchers.IO) {
            SuFileOperations.isPackageInstalled("com.heytap.themestore", context) ||
            SuFileOperations.isPackageInstalled("com.oplus.themestore", context)
        }

        if (!themeStoreInstalled) {
            onLog("[FAIL] 未检测到主题商店（com.heytap.themestore / com.oplus.themestore）")
            onComplete(false, "检测到系统未安装主题商店\n（com.heytap.themestore 或 com.oplus.themestore）\n\n安装主题将导致系统主题功能彻底失效！\n请先安装主题商店后再进行操作")
            return
        }
        onLog("[+] 主题商店已安装")

        // SU 模式：优先安全地以 Root 模块（Magisk/KernelSU/APatch）方式安装，
        // 无受支持的模块管理器时回退为直接注入
        val error = withContext(Dispatchers.IO) {
            try {
                Log.d("ThemePreviewScreen", "Installing theme (SU)")
                SuFileOperations.installThemeWithLog(context, tempFilePath, onLog)
            } catch (e: Exception) {
                Log.e("ThemePreviewScreen", "Error installing theme", e)
                onLog("[ERR] ${e.message}")
                e.message
            }
        }

        if (error == null) {
            onComplete(true, "")
        } else {
            onComplete(false, error)
        }
    } catch (e: Exception) {
        onLog("[ERR] ${e.message}")
        onComplete(false, e.message ?: "未知错误")
    }
}

/**
 * 安装日志弹窗：安装期间实时展示日志，完成后允许用户查看结果并继续
 */
@Composable
private fun InstallLogDialog(
    logs: List<String>,
    isRunning: Boolean,
    succeeded: Boolean?,
    onContinue: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    BlurDialog {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .heightIn(max = 480.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            text = when {
                                isRunning -> "正在安装主题..."
                                succeeded == true -> "安装完成"
                                else -> "安装失败"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 280.dp)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(logs) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = when {
                                        line.startsWith("[OK]") -> MaterialTheme.colorScheme.primary
                                        line.startsWith("[FAIL]") || line.startsWith("[ERR]") -> MaterialTheme.colorScheme.error
                                        line.startsWith("[!]") -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onContinue,
                            enabled = !isRunning
                        ) {
                            Text(if (succeeded == true) "继续" else "关闭")
                        }
                    }
                }
            }
        }
    }
}
