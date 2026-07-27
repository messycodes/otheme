package com.chuishui.otheme

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * SU 模式下的文件操作实现
 * 直接使用 su 命令执行操作，不依赖 Shizuku
 */
object SuFileOperations {
    private const val TAG = "SuFileOperations"
    private const val THEME_DIR = "/data/theme"

    /**
     * 执行 su 命令
     */
    private fun execSuCommand(command: String): Pair<Int, String> {
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
    fun installThemeFromFd(context: Context, pfd: ParcelFileDescriptor): String? {
        Log.d(TAG, "Installing theme from ParcelFileDescriptor")
        
        return try {
            // 复制到临时文件
            val tempFile = File(context.cacheDir, "temp_install.zip")
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val result = installTheme(context, tempFile.absolutePath)
            tempFile.delete()
            result
        } catch (e: Exception) {
            val error = "Error installing theme: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    /**
     * 安装主题（从路径）
     */
    fun installTheme(context: Context, themePath: String): String? {
        Log.d(TAG, "Installing theme from: $themePath")
        
        return try {
            // 确保主题目录存在
            execSuCommand("mkdir -p $THEME_DIR")
            
            // 获取UID
            val (_, uidOutput) = execSuCommand("stat -c '%U:%G' /data/data/com.oplus.uxdesign 2>/dev/null || echo 'u0_a240:u0_a240'")
            val themeStoreOwner = uidOutput.trim().split(":").firstOrNull() ?: "u0_a240"
            val themeStoreGroup = uidOutput.trim().split(":").lastOrNull() ?: "u0_a240"
            Log.d(TAG, "Theme store owner: $themeStoreOwner:$themeStoreGroup")
            
            // 检查 config 文件是否存在
            val (_, checkOutput) = execSuCommand("test -f $THEME_DIR/config && echo 'exists' || echo 'notfound'")
            val configExists = checkOutput.trim() == "exists"
            
            if (!configExists) {
                Log.d(TAG, "Config file not found, copying default config")
                // 从 assets 复制默认 config 到临时文件
                val tempConfig = File(context.cacheDir, "default_config")
                context.assets.open("config").use { input ->
                    FileOutputStream(tempConfig).use { output ->
                        input.copyTo(output)
                    }
                }
                // 用 su 命令复制到目标位置
                execSuCommand("cp ${tempConfig.absolutePath} $THEME_DIR/config")
                execSuCommand("chmod 775 $THEME_DIR/config")
                execSuCommand("chown $themeStoreOwner:$themeStoreGroup $THEME_DIR/config")
                tempConfig.delete()
            }
            
            // 解压主题文件
            val (exitCode, output) = execSuCommand(
                "unzip -o '$themePath' -d $THEME_DIR"
            )
            
            if (exitCode != 0) {
                return "安装失败: $output"
            }
            
            // 如果存在 lockscreen 文件或目录，重命名为 lock
            val (_, lockscreenCheck) = execSuCommand("test -e $THEME_DIR/lockscreen && echo 'exists' || echo 'notfound'")
            if (lockscreenCheck.trim() == "exists") {
                Log.d(TAG, "Renaming lockscreen to lock")
                execSuCommand("mv $THEME_DIR/lockscreen $THEME_DIR/lock")
            }
            
            // 设置正确的权限
            execSuCommand("chmod -R 775 $THEME_DIR")
            execSuCommand("chown -R $themeStoreOwner:$themeStoreGroup $THEME_DIR")
            
            Log.d(TAG, "Theme installation completed")
            null
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
     * 卸载主题（删除 /data/theme 下的所有主题文件，保留 config 和 applying 文件夹）
     */
    fun uninstallTheme(): String? {
        Log.d(TAG, "===== Uninstalling theme =====")
        
        return try {
            val (exitCode, output) = execSuCommand("find $THEME_DIR -mindepth 1 -maxdepth 1 ! -name 'config' ! -name 'applying' -exec rm -rf {} +")
            if (exitCode != 0) {
                return "卸载失败: $output"
            }
            // 获取主题商店的 UID
            val (_, uidOutput) = execSuCommand("stat -c '%U:%G' /data/data/com.heytap.themestore 2>/dev/null || echo 'u0_a240:u0_a240'")
            val themeStoreOwner = uidOutput.trim().split(":").firstOrNull() ?: "u0_a240"
            val themeStoreGroup = uidOutput.trim().split(":").lastOrNull() ?: "u0_a240"
            // 确保 applying 目录存在并设置 777 权限
            execSuCommand("mkdir -p $THEME_DIR/applying")
            execSuCommand("chmod 777 $THEME_DIR/applying")
            execSuCommand("chown $themeStoreOwner:$themeStoreGroup $THEME_DIR/applying")
            Log.d(TAG, "Theme uninstalled successfully (config and applying preserved)")
            null
        } catch (e: Exception) {
            val error = "卸载失败: ${e.message}"
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
            // 检查 wallpaper 文件是否存在
            val (_, checkOutput) = execSuCommand("test -f $wallpaperFile && echo 'exists' || echo 'notfound'")
            val fileExists = checkOutput.trim() == "exists"
            Log.d(TAG, "wallpaper file exists: $fileExists, checkOutput: '${checkOutput.trim()}'")
            if (!fileExists) {
                Log.d(TAG, "wallpaper file not found, skipping")
                return null
            }
            
            // 用 su 清理临时目录（之前解压的文件是 root owner，app 用户无权限删除）
            val extractDir = File(context.cacheDir, "wallpaper_extract")
            execSuCommand("rm -rf ${extractDir.absolutePath}")
            extractDir.mkdirs()
            
            // 直接全部解压到临时目录
            val (extractExit, extractOutput) = execSuCommand(
                "cd ${extractDir.absolutePath} && unzip -o '$wallpaperFile' 2>/dev/null && find ${extractDir.absolutePath} -type f -exec chmod 644 {} +"
            )
            Log.d(TAG, "Extract to temp exit=$extractExit, output=$extractOutput")
            
            // 递归搜索目标文件
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
    fun restartProcesses(packages: List<String>): String? {
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
