package com.chuishui.otheme

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chuishui.otheme.ui.theme.OThemeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1
        private const val DEFAULT_PATH = "/data/theme"
    }

    private var fileService: IFileService? = null
    private var isServiceBound = false
    
    // 导航状态
    private var pendingThemeInfo: ThemeInfo? = null
    private var pendingInvalidTheme: Boolean = false
    private var pendingTempFilePath: String = ""
    private var pendingStatusMessage: String = ""
    
    // 主题模式设置
    private val prefs by lazy { getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    private fun getThemeMode(): ThemeMode {
        return ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    }
    private fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }
    
    // 运行模式设置
    private fun getExecutionMode(): ExecutionMode {
        return ExecutionMode.valueOf(prefs.getString("execution_mode", ExecutionMode.SU.name) ?: ExecutionMode.SU.name)
    }
    private fun saveExecutionMode(mode: ExecutionMode) {
        prefs.edit().putString("execution_mode", mode.name).apply()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            fileService = IFileService.Stub.asInterface(service)
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            fileService = null
            isServiceBound = false
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Binder received")
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Binder dead")
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission result: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register Shizuku listeners
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        setContent {
            var themeMode by remember { mutableStateOf(getThemeMode()) }
            var executionMode by remember { mutableStateOf(getExecutionMode()) }
            var isGlobalLoading by remember { mutableStateOf(false) }
            var globalLoadingMessage by remember { mutableStateOf("") }
            var isConnected by remember { mutableStateOf(false) }
            var statusMessage by remember { mutableStateOf("") }
            
            OThemeTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            ) {
                val navController = rememberNavController()
                
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    composable(
                        "home",
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = tween(300)
                            ) + scaleOut(
                                targetScale = 0.9f,
                                animationSpec = tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = tween(300)
                            ) + scaleIn(
                                initialScale = 0.9f,
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        ThemeReaderScreen(
                            navController = navController,
                            onLoadingChange = { loading, message ->
                                isGlobalLoading = loading
                                globalLoadingMessage = message
                            },
                            currentExecutionMode = executionMode,
                            onConnectionStateChange = { connected, message ->
                                isConnected = connected
                                statusMessage = message
                            }
                        )
                    }
                    composable(
                        "theme_detail",
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300)
                            ) + scaleIn(
                                initialScale = 0.95f,
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        ThemeDetailScreen(navController, fileService)
                    }
                    composable(
                        "theme_preview",
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300)
                            ) + scaleIn(
                                initialScale = 0.95f,
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        val themeInfo = remember { pendingThemeInfo }
                        val invalidTheme = remember { pendingInvalidTheme }
                        val filePath = remember { pendingTempFilePath }
                        
                        ThemePreviewScreen(
                            navController = navController,
                            themeInfo = themeInfo,
                            isInvalidTheme = invalidTheme,
                            tempFilePath = filePath,
                            fileService = fileService,
                            onInstallComplete = { message ->
                                pendingStatusMessage = message
                            },
                            window = window
                        )
                    }
                    composable(
                        "settings",
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300)
                            ) + scaleIn(
                                initialScale = 0.95f,
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        SettingsScreen(
                            navController = navController,
                            currentThemeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                saveThemeMode(mode)
                            },
                            currentExecutionMode = executionMode,
                            onExecutionModeChange = { mode ->
                                executionMode = mode
                                saveExecutionMode(mode)
                                // 切换模式后重新初始化服务连接
                                if (isServiceBound) {
                                    unbindUserService()
                                }
                                // 注意：状态更新在 ThemeReaderScreen 中处理
                            }
                        )
                    }
                    composable(
                        "open_source_licenses",
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300)
                            ) + scaleIn(
                                initialScale = 0.95f,
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        OpenSourceLicensesScreen(navController = navController)
                    }
                }

                    // 全局水印
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "OTheme by ChuiShui",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Light
                        )
                    }

                    // 全局加载遮罩层（使用 Dialog 实现窗口级模糊）
                    if (isGlobalLoading) {
                        LoadingDialog(
                            message = globalLoadingMessage.ifEmpty { "正在处理..." },
                            window = window
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister listeners
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)

        // Unbind service
        if (isServiceBound) {
            unbindUserService()
        }
    }

    private fun checkShizukuPermission(): Boolean {
        if (Shizuku.isPreV11()) {
            Log.e(TAG, "Shizuku pre-v11 is not supported")
            return false
        }

        try {
            // 检查 binder 是否可用
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "Shizuku binder not available")
                return false
            }
            
            return when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Permission granted")
                    true
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    Log.d(TAG, "Permission denied permanently")
                    false
                }
                else -> {
                    Log.d(TAG, "Requesting permission")
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku permission", e)
            e.printStackTrace()
            return false
        }
    }

    private fun bindUserService() {
        if (isServiceBound) {
            Log.d(TAG, "Service already bound")
            return
        }
        
        try {
            // 再次检查 Shizuku 状态
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "Cannot bind service: Shizuku binder not available")
                return
            }

            val args = UserServiceArgs(
                ComponentName(packageName, FileService::class.java.name)
            )
                .processNameSuffix("file_service")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)

            Shizuku.bindUserService(args, serviceConnection)
            Log.d(TAG, "Binding user service")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding user service", e)
            e.printStackTrace()
        }
    }

    private fun unbindUserService() {
        if (!isServiceBound) {
            return
        }

        try {
            fileService?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error calling destroy", e)
        }

        val args = UserServiceArgs(
            ComponentName(packageName, FileService::class.java.name)
        )
            .processNameSuffix("file_service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        
        Shizuku.unbindUserService(args, serviceConnection, true)
        
        isServiceBound = false
        fileService = null
        Log.d(TAG, "User service unbound")
    }
    
    private fun checkSuPermission(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val exitCode = process.waitFor()
            val result = exitCode == 0
            Log.d(TAG, "SU permission check: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SU permission", e)
            false
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ThemeReaderScreen(
        navController: NavHostController,
        onLoadingChange: (Boolean, String) -> Unit,
        currentExecutionMode: ExecutionMode,
        onConnectionStateChange: (Boolean, String) -> Unit
    ) {
        var isConnected by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("") }
        var showErrorDialog by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        var themeStoreInstalled by remember { mutableStateOf<Boolean?>(null) }
        var showRestoreInfo by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // 备份主题文件选择器
        val backupLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri: Uri? ->
            uri?.let {
                scope.launch {
                    onLoadingChange(true, "正在备份主题...")
                    try {
                        Log.d(TAG, "Backup to URI: $uri")
                        
                        val currentMode = getExecutionMode()
                        val error = if (currentMode == ExecutionMode.SHIZUKU) {
                            // Shizuku 模式
                            val timestamp = System.currentTimeMillis()
                            val tempPath = "/data/local/tmp/theme_backup_$timestamp.theme"
                            
                            val backupError = withContext(Dispatchers.IO) {
                                fileService?.backupTheme(tempPath)
                            }

                            if (backupError == null) {
                                try {
                                    val tempFile = File(tempPath)
                                    if (tempFile.exists()) {
                                        contentResolver.openOutputStream(uri)?.use { output ->
                                            tempFile.inputStream().use { input ->
                                                input.copyTo(output)
                                            }
                                        }
                                        tempFile.delete()
                                        null
                                    } else {
                                        "备份文件不存在"
                                    }
                                } catch (e: Exception) {
                                    "复制失败：${e.message}"
                                }
                            } else {
                                backupError
                            }
                        } else {
                            // SU 模式
                            withContext(Dispatchers.IO) {
                                try {
                                    val tempFile = File(cacheDir, "temp_backup.theme")
                                    val result = SuFileOperations.backupTheme(this@MainActivity, tempFile.absolutePath)
                                    if (result == null && tempFile.exists()) {
                                        contentResolver.openOutputStream(uri)?.use { output ->
                                            tempFile.inputStream().use { input ->
                                                input.copyTo(output)
                                            }
                                        }
                                        tempFile.delete()
                                        null
                                    } else {
                                        result ?: "备份文件不存在"
                                    }
                                } catch (e: Exception) {
                                    e.message
                                }
                            }
                        }
                        
                        if (error == null) {
                            statusMessage = "备份成功！"
                            Log.d(TAG, "Backup completed successfully")
                        } else {
                            statusMessage = "备份失败：$error"
                            Log.e(TAG, "Backup failed: $error")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error backing up theme", e)
                        statusMessage = "错误: ${e.message}"
                    } finally {
                        onLoadingChange(false, "")
                    }
                }
            }
        }

        // 安装主题文件选择器
        val installLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                scope.launch {
                    try {
                        Log.d(TAG, "Selected file URI: $uri")

                        // 获取文件名
                        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            cursor.getString(nameIndex)
                        } ?: "unknown"

                        Log.d(TAG, "Selected file name: $fileName")

                        // 检查文件后缀
                        if (!fileName.endsWith(".theme", ignoreCase = true)) {
                            errorMessage = "请选择一个有效的 .theme 文件"
                            showErrorDialog = true
                            return@launch
                        }

                        // 复制文件到缓存
                        val tempFile = File(cacheDir, "temp_theme.theme")
                        contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        Log.d(TAG, "Copied file to: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")

                        // 解析主题信息
                        val themeInfo = withContext(Dispatchers.IO) {
                            ThemeParser.parseThemeInfo(tempFile)
                        }

                        // 检查是否为无效主题
                        val isInvalid = (themeInfo == null)

                        // 设置导航状态
                        pendingThemeInfo = themeInfo
                        pendingInvalidTheme = isInvalid
                        pendingTempFilePath = tempFile.absolutePath
                        
                        // 导航到预览界面
                        navController.navigate("theme_preview")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading theme file", e)
                        statusMessage = "错误: ${e.message}"
                    }
                }
            }
        }

        LaunchedEffect(currentExecutionMode) {
            // 根据运行模式自动连接
            if (currentExecutionMode == ExecutionMode.SHIZUKU) {
                if (checkShizukuPermission()) {
                    bindUserService()
                    isConnected = true
                    statusMessage = "Shizuku 已连接"
                } else {
                    isConnected = false
                    statusMessage = "请授予 Shizuku 权限"
                }
            } else {
                // SU 模式检查
                isConnected = checkSuPermission()
                statusMessage = if (isConnected) "SU 权限已获取" else "无法获取 Root 权限"
            }
            
            // 通知父组件状态变化
            onConnectionStateChange(isConnected, statusMessage)
            
            // 检查 ThemeStore 是否安装（先检测 heytap，失败再检测 oplus）
            if (isConnected) {
                themeStoreInstalled = withContext(Dispatchers.IO) {
                    try {
                        if (currentExecutionMode == ExecutionMode.SHIZUKU) {
                            (fileService?.isPackageInstalled("com.heytap.themestore") ?: false) ||
                            (fileService?.isPackageInstalled("com.oplus.themestore") ?: false)
                        } else {
                            SuFileOperations.isPackageInstalled("com.heytap.themestore", context) ||
                            SuFileOperations.isPackageInstalled("com.oplus.themestore", context)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            // 显示返回后的状态消息
            if (pendingStatusMessage.isNotEmpty()) {
                statusMessage = pendingStatusMessage
                onConnectionStateChange(isConnected, statusMessage)
                pendingStatusMessage = ""
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "OTheme",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate("settings") }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "设置"
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 应用介绍卡片
                    AppIntroCard()
                    
                    // 连接状态卡片
                    ConnectionStatusCard(
                        isConnected = isConnected,
                        statusMessage = statusMessage,
                        onConnect = {
                            if (currentExecutionMode == ExecutionMode.SHIZUKU) {
                                // Shizuku 模式
                                if (checkShizukuPermission()) {
                                    bindUserService()
                                    isConnected = true
                                    statusMessage = "Shizuku 已连接"
                                    onConnectionStateChange(isConnected, statusMessage)
                                } else {
                                    isConnected = false
                                    statusMessage = "请授予 Shizuku 权限"
                                    onConnectionStateChange(isConnected, statusMessage)
                                }
                            } else {
                                // SU 模式
                                isConnected = checkSuPermission()
                                statusMessage = if (isConnected) "SU 权限已获取" else "无法获取 Root 权限"
                                onConnectionStateChange(isConnected, statusMessage)
                            }
                            // 重新检查 ThemeStore（先检测 heytap，失败再检测 oplus）
                            scope.launch {
                                themeStoreInstalled = withContext(Dispatchers.IO) {
                                    try {
                                        if (currentExecutionMode == ExecutionMode.SHIZUKU) {
                                            (fileService?.isPackageInstalled("com.heytap.themestore") ?: false) ||
                                            (fileService?.isPackageInstalled("com.oplus.themestore") ?: false)
                                        } else {
                                            SuFileOperations.isPackageInstalled("com.heytap.themestore", context) ||
                                            SuFileOperations.isPackageInstalled("com.oplus.themestore", context)
                                        }
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            }
                        }
                    )
                    
                    // ThemeStore 检测卡片
                    AnimatedVisibility(
                        visible = isConnected && themeStoreInstalled != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        ThemeStoreStatusCard(
                            isInstalled = themeStoreInstalled == true
                        )
                    }

                    // 主题信息卡片
                    AnimatedVisibility(
                        visible = isConnected,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        ActionCard(
                            title = "主题信息",
                            description = "查看已安装主题的详细信息",
                            icon = Icons.Default.Info,
                            enabled = true,
                            onClick = {
                                navController.navigate("theme_detail")
                            }
                        )
                    }

                    // 操作卡片
                    AnimatedVisibility(
                        visible = isConnected,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 应用主题卡片
                            ActionCard(
                                title = "应用主题",
                                description = "从 .theme 文件安装主题",
                                icon = Icons.Default.Add,
                                enabled = true,
                                onClick = {
                                    installLauncher.launch("*/*")
                                }
                            )

                            // 备份主题卡片
                            ActionCard(
                                title = "备份主题",
                                description = "将当前主题保存为 .theme 文件",
                                icon = Icons.Default.Create,
                                enabled = true,
                                onClick = {
                                    val timestamp = SimpleDateFormat(
                                        "yyyyMMdd_HHmmss",
                                        Locale.getDefault()
                                    ).format(Date())
                                    backupLauncher.launch("theme_backup_$timestamp.theme")
                                }
                            )
                            
                            // 恢复主题提示卡片
                            ActionCard(
                                title = "恢复系统主题",
                                description = "点击查看恢复方法",
                                icon = Icons.Default.Restore,
                                enabled = true,
                                onClick = {
                                    showRestoreInfo = true
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // 错误对话框
        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = {
                    Text(
                        text = "错误",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(text = errorMessage)
                },
                confirmButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text("确定")
                    }
                }
            )
        }
        
        // 恢复主题提示对话框（带模糊）
        if (showRestoreInfo) {
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
                                text = "恢复系统主题",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "请使用官方主题商店应用恢复系统主题。\n\n在主题商店中选择并应用任意官方主题即可恢复系统主题功能。",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showRestoreInfo = false }) {
                                    Text("关闭")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ThemeStoreStatusCard(isInstalled: Boolean) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = if (isInstalled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isInstalled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isInstalled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isInstalled) "主题商店已安装" else "主题商店未安装",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isInstalled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (isInstalled)
                            "系统主题功能正常，可以安全安装主题"
                        else
                            "未检测到主题商店（com.heytap.themestore 或 com.oplus.themestore），禁止安装主题！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isInstalled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    @Composable
    fun ConnectionStatusCard(
        isConnected: Boolean,
        statusMessage: String,
        onConnect: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusMessage.ifEmpty { "请安装主题文件" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isConnected) {
                    Button(onClick = onConnect) {
                        Text("连接")
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已连接",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }


    @Composable
    fun ActionCard(
        title: String,
        description: String,
        icon: ImageVector,
        enabled: Boolean,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
