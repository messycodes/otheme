package com.chuishui.otheme

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    fileService: IFileService?,
    onInstallComplete: (String) -> Unit,
    window: android.view.Window
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isInstalling by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var wallpaperFiles by remember { mutableStateOf<List<File>?>(null) }
    var showInstallWarning1 by remember { mutableStateOf(false) }
    var showInstallWarning2 by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                            showInstallWarning1 = true
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
        
        // 安装中的遮罩层
        if (isInstalling) {
            LoadingDialog(message = "正在安装主题...", window = window)
        }
        
        // 双重安装警告
        if (showInstallWarning1) {
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
        
        if (showInstallWarning2) {
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
                                    installTheme(
                                        context = context,
                                        fileService = fileService,
                                        tempFilePath = tempFilePath,
                                        onComplete = { success, message ->
                                            isInstalling = false
                                            if (success) {
                                                scope.launch {
                                                    val files = extractWallpapers(context, fileService)
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
        
        // 壁纸提取和设置弹窗
        if (showWallpaperDialog && wallpaperFiles != null) {
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
                                                
                                                val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                                                val executionMode = prefs.getString("execution_mode", "SU") ?: "SU"
                                                
                                                val error = if (executionMode == "SHIZUKU") {
                                                    withContext(Dispatchers.IO) {
                                                        fileService?.restartProcesses(packages)
                                                    }
                                                } else {
                                                    withContext(Dispatchers.IO) {
                                                        SuFileOperations.restartProcesses(packages)
                                                    }
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

private suspend fun extractWallpapers(context: android.content.Context, fileService: IFileService?): List<File>? {
    return withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            val executionMode = prefs.getString("execution_mode", "SU") ?: "SU"
            if (executionMode == "SHIZUKU") {
                null
            } else {
                SuFileOperations.extractWallpaper(context)
            }
        } catch (e: Exception) {
            null
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

private suspend fun installTheme(
    context: android.content.Context,
    fileService: IFileService?,
    tempFilePath: String,
    onComplete: (Boolean, String) -> Unit
) {
    
    try {
        // 获取当前运行模式
        val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val executionMode = prefs.getString("execution_mode", "SU") ?: "SU"
        
        // 检查 ThemeStore 是否安装（先检测 heytap，失败再检测 oplus）
        val themeStoreInstalled = withContext(Dispatchers.IO) {
            if (executionMode == "SHIZUKU") {
                (fileService?.isPackageInstalled("com.heytap.themestore") ?: false) ||
                (fileService?.isPackageInstalled("com.oplus.themestore") ?: false)
            } else {
                SuFileOperations.isPackageInstalled("com.heytap.themestore", context) ||
                SuFileOperations.isPackageInstalled("com.oplus.themestore", context)
            }
        }

        if (!themeStoreInstalled) {
            onComplete(false, "检测到系统未安装主题商店\n（com.heytap.themestore 或 com.oplus.themestore）\n\n安装主题将导致系统主题功能彻底失效！\n请先安装主题商店后再进行操作")
            return
        }
        
        val error = if (executionMode == "SHIZUKU") {
            // Shizuku 模式
            if (fileService == null) {
                "服务未连接"
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        Log.d("ThemePreviewScreen", "Installing theme (Shizuku)")
                        
                        // 使用 ParcelFileDescriptor 跨进程传递文件
                        val file = java.io.File(tempFilePath)
                        val pfd = android.os.ParcelFileDescriptor.open(
                            file,
                            android.os.ParcelFileDescriptor.MODE_READ_ONLY
                        )
                        
                        pfd.use {
                            fileService.installThemeFromFd(it)
                        }
                    } catch (e: Exception) {
                        Log.e("ThemePreviewScreen", "Error installing theme", e)
                        e.message
                    }
                }
            }
        } else {
            // SU 模式
            withContext(Dispatchers.IO) {
                try {
                    Log.d("ThemePreviewScreen", "Installing theme (SU)")
                    SuFileOperations.installTheme(context, tempFilePath)
                } catch (e: Exception) {
                    Log.e("ThemePreviewScreen", "Error installing theme", e)
                    e.message
                }
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
