package com.chuishui.otheme

import android.content.Context
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 已检测到的 Root 管理方案
 */
enum class RootType {
    MAGISK, KERNELSU, APATCH, UNKNOWN
}

/**
 * SU 模式下的文件操作实现
 * 直接使用 su 命令执行操作，不依赖 Shizuku
 */
object SuFileOperations {
    private const val TAG = "SuFileOperations"
    private const val THEME_DIR = "/data/theme"
    private const val OTHEME_DIR = "/system_ext/media/themeInner"
    private const val OTHEME_SYSTEM_DIR = "/data/adb/modules/otheme/system_ext"

    /**
     * 执行 su 命令
     */
    private fun shellEscape(path: String): String {
        return "'" + path.replace("'", "'\\''") + "'"
    }

    fun execSuCommand(command: String): Pair<Int, String> {
        return try {
            Log.d(TAG, "Executing: $command")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            Log.d(TAG, "Exit code: $exitCode")
            if (output.isNotEmpty()) Log.d(TAG, "Output: $output")
            if (error.isNotEmpty()) Log.e(TAG, "Error: $error")
            
            Pair(exitCode, if (exitCode == 0) output else error)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: ${e.message}", e)
            Pair(-1, e.message ?: "Unknown error")
        }
    }

    /**
     * 执行 su 命令并实时回调输出的每一行日志
     */
    private fun execSuCommandStreaming(command: String, onLog: (String) -> Unit): Int {
        return try {
            Log.d(TAG, "Executing (streaming): $command")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))

            val outThread = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        Log.d(TAG, "[OUT] $line")
                        onLog(line)
                    }
                } catch (_: Exception) { }
            }
            val errThread = Thread {
                try {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        Log.d(TAG, "[OUT] $line")
                        onLog(line)
                    }
                } catch (_: Exception) { }
            }
            outThread.start()
            errThread.start()

            val exitCode = process.waitFor()
            outThread.join()
            errThread.join()

            Log.d(TAG, "Exit code: $exitCode")
            exitCode
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: ${e.message}", e)
            onLog("[ERR] ${e.message}")
            -1
        }
    }

    /**
     * 检测当前设备使用的 Root 管理方案（Magisk / KernelSU / APatch）
     */
    fun detectRootType(): RootType {
        val (_, output) = execSuCommand(
            "if command -v ksud >/dev/null 2>&1; then echo KSU; " +
                "elif command -v magisk >/dev/null 2>&1; then echo MAGISK; " +
                "elif command -v apd >/dev/null 2>&1; then echo APATCH; " +
                "else echo UNKNOWN; fi"
        )
        return when (output.trim()) {
            "KSU" -> RootType.KERNELSU
            "MAGISK" -> RootType.MAGISK
            "APATCH" -> RootType.APATCH
            else -> RootType.UNKNOWN
        }
    }

    /**
     * 检测 OTheme 模块是否已安装（/data/adb/modules/otheme/system/ 存在即已安装）
     */
    fun checkOthemeDir(): Boolean {
        val (exitCode, _) = execSuCommand("[ -d '$OTHEME_SYSTEM_DIR' ] && echo OK")
        return exitCode == 0
    }

    /**
     * 通过 /data/adb/otheme/otheme/ 路径直接挂载读写添加主题文件
     */
    fun installThemeToOthemeDir(themePath: String, onLog: (String) -> Unit): String? {
        Log.d(TAG, "Installing theme via otheme dir: $themePath")

        return try {
            val fileName = File(themePath).name
            val targetPath = "$OTHEME_DIR/$fileName"

            onLog("[+] 检测到 otheme 模块目录，使用直接写入模式...")

            onLog("[+] 写入 $targetPath ...")
            val (copyExit, copyOutput) = execSuCommand("cp -f ${shellEscape(themePath)} ${shellEscape(targetPath)}")
            if (copyExit != 0) {
                onLog("[FAIL] $copyOutput")
                return "安装失败: $copyOutput"
            }

            execSuCommand("chmod 644 ${shellEscape(targetPath)}")
            execSuCommand("chown root:root ${shellEscape(targetPath)}")

            Log.d(TAG, "Theme injected into $targetPath")
            onLog("[OK] 已写入 $targetPath")
            null
        } catch (e: Exception) {
            val error = "写入 otheme 目录失败: ${e.message}"
            Log.e(TAG, error, e)
            onLog("[ERR] $error")
            error
        }
    }

    /**
     * 从 assets 安装 OTheme 附加模块
     */
    fun installModuleFromAssets(context: Context, onLog: (String) -> Unit): String? {
        return try {
            onLog("[+] 正在从内置资源安装 OTheme 模块...")

            val assetFile = "otheme_install.zip"
            val cacheFile = File(context.cacheDir, assetFile)

            context.assets.open(assetFile).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            onLog("[+] 检测 Root 环境...")
            val rootType = detectRootType()

            val installCmd = when (rootType) {
                RootType.MAGISK -> "magisk --install-module '${cacheFile.absolutePath}'"
                RootType.KERNELSU -> "ksud module install '${cacheFile.absolutePath}'"
                RootType.APATCH -> "apd module install '${cacheFile.absolutePath}'"
                RootType.UNKNOWN -> return "未检测到受支持的 Root 管理器（Magisk / KernelSU / APatch）"
            }

            onLog("[+] 正在安装 OTheme 模块...")
            val exitCode = execSuCommandStreaming(installCmd) { line -> onLog(line) }

            cacheFile.delete()

            if (exitCode == 0) {
                onLog("[OK] OTheme 模块安装成功")
                null
            } else {
                "OTheme 模块安装失败（退出码 $exitCode）"
            }
        } catch (e: Exception) {
            val error = "安装 OTheme 模块失败: ${e.message}"
            Log.e(TAG, error, e)
            onLog("[ERR] $error")
            error
        }
    }

    /**
     * 安装主题的统一入口：
     * 1. 检测 OTheme 模块是否已安装（/data/adb/modules/otheme/system/）
     * 2. 若未安装，从 assets 安装模块并提示用户重启
     * 3. 若已安装，通过 otheme 模块目录直接写入主题文件
     */
    fun installThemeWithLog(context: Context, themePath: String, onLog: (String) -> Unit): String? {
        if (!checkOthemeDir()) {
            onLog("[!] 未检测到 OTheme 模块")
            onLog("[+] 正在安装 OTheme 模块...")
            val moduleError = installModuleFromAssets(context, onLog)
            if (moduleError == null) {
                onLog("[OK] 模块安装完成，请重启设备后再次安装主题")
                return "MODULE_INSTALLED"
            }
            return moduleError
        }

        onLog("[+] 检测到 OTheme 模块，使用直接写入模式")
        return installThemeToOthemeDir(themePath, onLog)
    }

    /**
     * 备份主题
     */
    fun backupTheme(context: Context, backupPath: String): String? {
        Log.d(TAG, "Backing up theme to: $backupPath")
        
        return try {
            // 创建临时压缩文件
            val tempZip = File(context.cacheDir, "temp_backup.zip")
            
            // 使用 su 打包主题目录
            val (exitCode, output) = execSuCommand(
                "cd /data && tar czf ${tempZip.absolutePath} theme/"
            )
            
            if (exitCode != 0) {
                return "备份失败: $output"
            }
            
            // 复制到目标位置
            tempZip.copyTo(File(backupPath), overwrite = true)
            tempZip.delete()
            
            Log.d(TAG, "Backup completed successfully")
            null
        } catch (e: Exception) {
            val error = "Error backing up theme: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    /**
     * 安装主题（从 ParcelFileDescriptor）
     */
    fun installThemeFromFd(
        context: Context,
        pfd: ParcelFileDescriptor,
        onLog: (String) -> Unit = {}
    ): String? {
        Log.d(TAG, "Installing theme from ParcelFileDescriptor")
        
        return try {
            // 复制到临时文件
            val tempFile = File(context.cacheDir, "temp_install.zip")
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val result = installThemeWithLog(context, tempFile.absolutePath, onLog)
            tempFile.delete()
            result
        } catch (e: Exception) {
            val error = "Error installing theme: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    /**
     * 获取主题信息文件内容
     */
    fun getInstalledThemeInfo(): String? {
        Log.d(TAG, "Reading installed theme info")
        
        return try {
            val (exitCode, output) = execSuCommand("cat $THEME_DIR/themeInfo.xml")
            
            if (exitCode == 0 && output.isNotEmpty()) {
                output
            } else {
                Log.e(TAG, "Failed to read themeInfo.xml")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading themeInfo.xml: ${e.message}", e)
            null
        }
    }

    /**
     * 获取 otheme 模块目录下所有 .theme 文件的 themeInfo.xml，
     * 返回 (文件名, ThemeInfo) 列表
     */
    fun getInstalledThemeList(): List<Pair<String, ThemeInfo?>> {
        Log.d(TAG, "Listing installed themes from $OTHEME_DIR")

        return try {
            val (exitCode, output) = execSuCommand("ls '$OTHEME_DIR'/*.theme 2>/dev/null")
            if (exitCode != 0 || output.isBlank()) {
                Log.d(TAG, "No themes found in $OTHEME_DIR")
                return emptyList()
            }

            val files = output.lines().filter { it.isNotBlank() }
            files.map { filePath ->
                val fileName = filePath.substringAfterLast("/")
                val (_, xmlOutput) = execSuCommand("unzip -p ${shellEscape(filePath)} themeInfo.xml 2>/dev/null")
                val themeInfo = if (xmlOutput.isNotBlank()) {
                    ThemeParser.parseThemeInfoFromXml(xmlOutput)
                } else null
                fileName to themeInfo
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing installed themes: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 删除已安装的主题文件
     */
    fun deleteTheme(fileName: String): String? {
        Log.d(TAG, "Deleting theme: $fileName")
        val targetPath = "$OTHEME_DIR/$fileName"
        val (exitCode, output) = execSuCommand("rm -f ${shellEscape(targetPath)}")
        return if (exitCode == 0) null else "删除失败: $output"
    }

    /**
     * 检查指定应用是否已安装
     * 优先通过 su shell 检查，失败时兜底使用 Android PackageManager API
     */
    fun isPackageInstalled(packageName: String, context: android.content.Context? = null): Boolean {
        return try {
            Log.d(TAG, "Checking if package is installed: $packageName")
            
            // 方法1: su + pm path
            val (exitCode, output) = execSuCommand("pm path $packageName")
            if (exitCode == 0 && output.contains(packageName)) {
                Log.d(TAG, "Package $packageName found via pm path")
                return true
            }
            
            // 方法2: 兜底 — Android 原生 PackageManager API
            if (context != null) {
                try {
                    val info = context.packageManager.getPackageInfo(packageName, 0)
                    Log.d(TAG, "Package $packageName found via PackageManager API, version: ${info.versionName}")
                    return true
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    Log.d(TAG, "Package $packageName not found via PackageManager API")
                }
            }
            
            Log.d(TAG, "Package $packageName NOT installed")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking package installation: ${e.message}", e)
            // 异常时最后尝试原生 API
            context?.let {
                try {
                    it.packageManager.getPackageInfo(packageName, 0)
                    return true
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) { }
            }
            false
        }
    }
    
    /**
     * 安装主题到 /data/theme（直接解压模式）
     * 参考方法中的实现，用于非模块注入场景
     */
    fun installThemeToDataTheme(context: Context, themePath: String): String? {
        Log.d(TAG, "Installing theme to /data/theme from: $themePath")

        return try {
            execSuCommand("mkdir -p $THEME_DIR")

            // 删除 /data/theme 下所有名字包含 lock 或 icon 的文件和文件夹
            execSuCommand("find $THEME_DIR -mindepth 1 -maxdepth 1 \\( -iname '*lock*' -o -iname '*icon*' \\) -exec rm -rf {} + 2>/dev/null")
            Log.d(TAG, "Cleaned up lock/icon files from $THEME_DIR")

            val (_, uidOutput) = execSuCommand("stat -c '%U:%G' /data/data/com.oplus.uxdesign 2>/dev/null || echo 'u0_a240:u0_a240'")
            val themeStoreOwner = uidOutput.trim().split(":").firstOrNull() ?: "u0_a240"
            val themeStoreGroup = uidOutput.trim().split(":").lastOrNull() ?: "u0_a240"
            Log.d(TAG, "Theme store owner: $themeStoreOwner:$themeStoreGroup")

            val (_, checkOutput) = execSuCommand("test -f $THEME_DIR/config && echo 'exists' || echo 'notfound'")
            val configExists = checkOutput.trim() == "exists"

            if (!configExists) {
                Log.d(TAG, "Config file not found, copying default config")
                val tempConfig = File(context.cacheDir, "default_config")
                context.assets.open("config").use { input ->
                    FileOutputStream(tempConfig).use { output ->
                        input.copyTo(output)
                    }
                }
                execSuCommand("cp ${tempConfig.absolutePath} $THEME_DIR/config")
                execSuCommand("chmod 775 $THEME_DIR/config")
                execSuCommand("chown $themeStoreOwner:$themeStoreGroup $THEME_DIR/config")
                tempConfig.delete()
            }

            val (exitCode, output) = execSuCommand(
                "unzip -o '$themePath' -d $THEME_DIR"
            )

            if (exitCode != 0) {
                return "安装失败: $output"
            }

            val (_, lockscreenCheck) = execSuCommand("test -e $THEME_DIR/lockscreen && echo 'exists' || echo 'notfound'")
            if (lockscreenCheck.trim() == "exists") {
                Log.d(TAG, "Renaming lockscreen to lock")
                execSuCommand("mv $THEME_DIR/lockscreen $THEME_DIR/lock")
            }

            execSuCommand("chmod -R 777 $THEME_DIR")
            execSuCommand("chown -R $themeStoreOwner:$themeStoreGroup $THEME_DIR")

            Log.d(TAG, "Theme installation to /data/theme completed")
            null
        } catch (e: Exception) {
            val error = "安装失败: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    /**
     * 从 wallpaper 文件中解包查找壁纸图片
     * wallpaper 本质上是个 ZIP 文件，跨文件夹搜索 oppo_default_wallpaper.jpg/png
     * @return 提取后的图片文件路径列表，如果 wallpaper 不存在则返回 null
     */
    fun extractWallpaper(context: Context): List<File>? {
        val wallpaperFile = "$THEME_DIR/wallpaper"
        Log.d(TAG, "===== Searching wallpaper in $wallpaperFile =====")

        return try {
            val (_, checkOutput) = execSuCommand("test -f $wallpaperFile && echo 'exists' || echo 'notfound'")
            val fileExists = checkOutput.trim() == "exists"
            Log.d(TAG, "wallpaper file exists: $fileExists, checkOutput: '${checkOutput.trim()}'")
            if (!fileExists) {
                Log.d(TAG, "wallpaper file not found, skipping")
                return null
            }

            val extractDir = File(context.cacheDir, "wallpaper_extract")
            execSuCommand("rm -rf ${extractDir.absolutePath}")
            extractDir.mkdirs()

            val (extractExit, extractOutput) = execSuCommand(
                "cd ${extractDir.absolutePath} && unzip -o '$wallpaperFile' 2>/dev/null && find ${extractDir.absolutePath} -type f -exec chmod 644 {} +"
            )
            Log.d(TAG, "Extract to temp exit=$extractExit, output=$extractOutput")

            val resultFiles = extractDir.walkTopDown()
                .filter { it.isFile }
                .filter { f ->
                    val name = f.name.lowercase()
                    name.contains("oppo_default_wallpaper") &&
                    (name.endsWith(".jpg") || name.endsWith(".png"))
                }
                .map { f ->
                    val targetFile = File(extractDir, f.name)
                    if (f != targetFile) f.copyTo(targetFile, overwrite = true)
                    targetFile
                }
                .distinctBy { it.name.lowercase() }
                .toList()

            Log.d(TAG, "Found ${resultFiles.size} wallpaper images: ${resultFiles.map { it.name }}")

            if (resultFiles.isEmpty()) null else resultFiles
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting wallpaper: ${e.message}", e)
            null
        }
    }

    /**
     * 软重启（热重启），参考 kr-scripts 实现
     * 通过 am restart 重启 system_server，触发用户空间进程重启
     */
    fun softRestartProcesses(packages: List<String>): String? {
        Log.d(TAG, "===== Starting hot reboot =====")

        return try {
            val (exitCode, output) = execSuCommand("sync; am restart || busybox killall system_server;")

            if (exitCode == 0) {
                Log.d(TAG, "Hot reboot triggered successfully")
                null
            } else {
                val error = "热重启失败: $output"
                Log.e(TAG, error)
                error
            }
        } catch (e: Exception) {
            val error = "热重启失败: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    /**
     * 卸载主题（删除 /data/theme 下的所有主题文件，保留 config 和 applying 文件夹）
     */
    fun uninstallTheme(): String? {
        Log.d(TAG, "===== Uninstalling theme =====")
        
        return try {
            val (exitCode, output) = execSuCommand("find $THEME_DIR -mindepth 1 -maxdepth 1 ! -name 'config' ! -name 'applying' -exec rm -rf {} +")
            if (exitCode != 0) {
                return "卸载失败: $output"
            }
            // 确保 applying 目录存在并设置 777 权限
            execSuCommand("mkdir -p $THEME_DIR/applying")
            execSuCommand("chmod 777 $THEME_DIR")
            Log.d(TAG, "Theme uninstalled successfully (config and applying preserved)")
            null
        } catch (e: Exception) {
            val error = "卸载失败: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    /**
     * 重启设备
     */
    fun restartProcesses(packages: List<String>): String? {
        Log.d(TAG, "===== Starting reboot =====")

        return try {
            val (exitCode, output) = execSuCommand("reboot")

            if (exitCode == 0) {
                Log.d(TAG, "Reboot triggered successfully")
                null
            } else {
                val error = "重启失败: $output"
                Log.e(TAG, error)
                error
            }
        } catch (e: Exception) {
            val error = "重启失败: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    /**
     * 获取主题文件列表
     */
    fun getThemeInfo(): List<String> {
        Log.d(TAG, "Getting theme file list")
        
        return try {
            val (exitCode, output) = execSuCommand("ls -R $THEME_DIR")
            
            if (exitCode == 0) {
                output.lines().filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting theme info: ${e.message}", e)
            emptyList()
        }
    }
}
