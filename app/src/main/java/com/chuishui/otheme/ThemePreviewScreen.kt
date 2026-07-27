package com.chuishui.otheme

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePreviewScreen(
    navController: NavController,
    themeInfo: ThemeInfo?,
    isInvalidTheme: Boolean,
    tempFilePath: String,
    executionMode: ExecutionMode,
    onInstallComplete: (String) -> Unit,
    window: android.view.Window
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isInstalling by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showInstallLogDialog by remember { mutableStateOf(false) }
    var installLogs by remember { mutableStateOf(listOf<String>()) }
    var installSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var installMessage by remember { mutableStateOf("") }
    var showInstallWarning1 by remember { mutableStateOf(false) }
    var showInstallWarning2 by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var wallpaperFiles by remember { mutableStateOf<List<File>?>(null) }
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
                            if (executionMode == ExecutionMode.DATA_THEME) {
                                showInstallWarning1 = true
                            } else {
                                scope.launch {
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
                                            installMessage = message
                                            if (!success) {
                                                appendLog("[FAIL] $message")
                                            }
                                            val logType = if (message == "MODULE_INSTALLED") "module" else "theme"
                                            InstallLogStore.save(installLogs, success, logType)
                                            clearAppCache(context)
                                        }
                                    )
                                }
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

        // 安装中的遮罩层（DATA_THEME 模式使用 LoadingDialog，MODULE_INJECTION 使用 InstallLogDialog）
        if (executionMode == ExecutionMode.DATA_THEME && isInstalling) {
            LoadingDialog(message = "正在安装主题...", window = window)
        }

        // DATA_THEME 模式：双重安装警告
        if (executionMode == ExecutionMode.DATA_THEME && showInstallWarning1) {
            BlurDialog {
                AlertDialog(
                    onDismissRequest = { showInstallWarning1 = false },
                    title = { Text("使用说明", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("请在主题商店应用或试用主题时，使用本软件安装主题以实现替换。\n\n安装后将覆盖系统主题文件，此操作不可逆！")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showInstallWarning1 = false
                                showInstallWarning2 = true
                            }
                        ) { Text("我已知晓，继续") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showInstallWarning1 = false }
                        ) { Text("取消安装") }
                    }
                )
            }
        }

        if (executionMode == ExecutionMode.DATA_THEME && showInstallWarning2) {
            BlurDialog {
                AlertDialog(
                    onDismissRequest = { showInstallWarning2 = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("最终确认", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Text("请在主题商店应用或试用主题时，再使用本软件安装主题以实现替换。\n\n安装后将覆盖系统主题文件，此操作不可逆！")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showInstallWarning2 = false
                                scope.launch {
                                    clearAppCache(context)
                                    isInstalling = true
                                    installThemeToDataTheme(
                                        context = context,
                                        tempFilePath = tempFilePath,
                                        onComplete = { success, message ->
                                            isInstalling = false
                                            if (success) {
                                                scope.launch {
                                                    val files = withContext(Dispatchers.IO) {
                                                        SuFileOperations.extractWallpaper(context)
                                                    }
                                                    wallpaperFiles = files
                                                    if (files != null && files.isNotEmpty()) {
                                                        showWallpaperDialog = true
                                                    } else {
                                                        showRestartDialog = true
                                                    }
                                                }
                                            } else {
                                                onInstallComplete(message)
                                                navController.navigateUp()
                                            }
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("确认安装") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showInstallWarning2 = false }
                        ) { Text("放弃安装") }
                    }
                )
            }
        }

        // DATA_THEME 模式：壁纸提取和设置弹窗
        if (executionMode == ExecutionMode.DATA_THEME && showWallpaperDialog && wallpaperFiles != null) {
            WallpaperDialog(
                files = wallpaperFiles!!,
                onApplyWallpaper = { file ->
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                if (bitmap != null) {
                                    WallpaperManager.getInstance(context).setBitmap(bitmap)
                                }
                            }
                        } catch (e: Exception) {
                            onInstallComplete("壁纸应用失败：${e.message}")
                        }
                        showWallpaperDialog = false
                        showRestartDialog = true
                        clearAppCache(context)
                    }
                },
                onSkip = {
                    showWallpaperDialog = false
                    showRestartDialog = true
                    clearAppCache(context)
                },
                onDismiss = {
                    showWallpaperDialog = false
                    navController.navigateUp()
                    clearAppCache(context)
                }
            )
        }

        // DATA_THEME 模式：安装完成确认对话框（软重启）
        if (executionMode == ExecutionMode.DATA_THEME && showRestartDialog) {
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
                                text = "主题安装成功！\n\n是否立即执行软重启以应用主题？\n（软重启将通过重启系统服务来应用主题更改）",
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
                                        onInstallComplete("主题安装成功！\n请稍后手动软重启以应用主题")
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
                                                    SuFileOperations.softRestartProcesses(packages)
                                                }

                                                if (error == null) {
                                                    onInstallComplete("软重启已触发！")
                                                } else {
                                                    onInstallComplete("软重启失败：$error")
                                                }
                                            } catch (e: Exception) {
                                                onInstallComplete("软重启失败：${e.message}")
                                            }
                                            navController.navigateUp()
                                        }
                                    }
                                ) {
                                    Text("立即软重启")
                                }
                            }
                        }
                    }
                }
            }
        }

        // MODULE_INJECTION 模式：安装日志弹窗
        if (executionMode == ExecutionMode.MODULE_INJECTION && showInstallLogDialog) {
            InstallLogDialog(
                logs = installLogs,
                isRunning = isInstalling,
                succeeded = installSucceeded,
                onContinue = {
                    showInstallLogDialog = false
                    if (installSucceeded == true) {
                        if (installMessage == "MODULE_INSTALLED") {
                            showRestartDialog = true
                        } else {
                            onInstallComplete("主题安装成功！")
                            navController.navigateUp()
                        }
                    } else {
                        onInstallComplete(installLogs.lastOrNull { it.startsWith("[FAIL]") || it.startsWith("[ERR]") } ?: "安装失败")
                        navController.navigateUp()
                    }
                },
                onSaveLog = {
                    scope.launch {
                        try {
                            val logDir = context.getExternalFilesDir(null) ?: context.cacheDir
                            val logFile = java.io.File(logDir, "otheme_install_${System.currentTimeMillis()}.log")
                            logFile.writeText(installLogs.joinToString("\n"))
                            android.widget.Toast.makeText(
                                context,
                                "日志已保存: ${logFile.absolutePath}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "保存日志失败: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }

        // MODULE_INJECTION 模式：模块安装完成确认对话框
        if (executionMode == ExecutionMode.MODULE_INJECTION && showRestartDialog) {
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
                                text = "模块安装完成",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "OTheme 模块已安装，请重启设备以激活模块，然后再次安装主题。",
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
                                        onInstallComplete("模块安装完成，请稍后手动重启以激活模块")
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
        } else if (error == "MODULE_INSTALLED") {
            onComplete(true, "MODULE_INSTALLED")
        } else {
            onComplete(false, error)
        }
    } catch (e: Exception) {
        onLog("[ERR] ${e.message}")
        onComplete(false, e.message ?: "未知错误")
    }
}

private suspend fun installThemeToDataTheme(
    context: android.content.Context,
    tempFilePath: String,
    onComplete: (Boolean, String) -> Unit
) {
    try {
        val themeStoreInstalled = withContext(Dispatchers.IO) {
            SuFileOperations.isPackageInstalled("com.heytap.themestore", context) ||
            SuFileOperations.isPackageInstalled("com.oplus.themestore", context)
        }

        if (!themeStoreInstalled) {
            onComplete(false, "检测到系统未安装主题商店\n（com.heytap.themestore 或 com.oplus.themestore）\n\n安装主题将导致系统主题功能彻底失效！\n请先安装主题商店后再进行操作")
            return
        }

        val error = withContext(Dispatchers.IO) {
            try {
                Log.d("ThemePreviewScreen", "Installing theme to /data/theme (SU)")
                SuFileOperations.installThemeToDataTheme(context, tempFilePath)
            } catch (e: Exception) {
                Log.e("ThemePreviewScreen", "Error installing theme", e)
                e.message
            }
        }

        if (error == null) {
            onComplete(true, "")
        } else {
            onComplete(false, error)
        }
    } catch (e: Exception) {
        onComplete(false, e.message ?: "未知错误")
    }
}

@Composable
private fun InstallLogDialog(
    logs: List<String>,
    isRunning: Boolean,
    succeeded: Boolean?,
    onContinue: () -> Unit,
    onSaveLog: () -> Unit
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        if (!isRunning && succeeded == false) {
                            OutlinedButton(onClick = onSaveLog) {
                                Text("保存日志")
                            }
                        }
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

@Composable
private fun WallpaperDialog(
    files: List<File>,
    onApplyWallpaper: (File) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
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
                        text = "检测到壁纸",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "主题包含 ${files.size} 张壁纸，点击即可应用",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        files.forEach { file ->
                            val bitmap = remember(file) {
                                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onApplyWallpaper(file) },
                                shape = MaterialTheme.shapes.medium,
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "壁纸预览",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(9f / 16f)
                                            .clip(MaterialTheme.shapes.small),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onSkip) {
                            Text("跳过，继续重启")
                        }
                    }
                }
            }
        }
    }
}
