package com.chuishui.otheme

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    private const val THEME_INNER_DIR = "/system_ext/media/themeInner"

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
     * 将 .theme 文件打包为标准 Magisk 模块格式（module.prop +
     * system_ext/media/themeInner/<file>），并通过对应 Root 管理器的
     * 模块管理接口安装。相比直接 remount /system_ext 写入，模块安装依赖
     * Root 管理器自身的 overlay 挂载机制，不需要手动改写系统分区，更安全。
     */
    fun installThemeAsModule(context: Context, themePath: String, onLog: (String) -> Unit): String? {
        onLog("[+] 检测 Root 环境...")
        val rootType = detectRootType()

        when (rootType) {
            RootType.KERNELSU -> onLog("[+] 检测到 KernelSU")
            RootType.MAGISK -> onLog("[+] 检测到 Magisk")
            RootType.APATCH -> onLog("[+] 检测到 APatch")
            RootType.UNKNOWN -> onLog("[!] 未检测到 Magisk / KernelSU / APatch")
        }

        if (rootType == RootType.UNKNOWN) {
            return "未检测到受支持的 Root 管理器（Magisk / KernelSU / APatch）"
        }

        var moduleZip: File? = null
        return try {
            val themeFile = File(themePath)
            val fileName = themeFile.name
            val moduleId = "otheme_" + fileName
                .substringBeforeLast(".")
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .ifBlank { "theme" }

            onLog("[+] 打包模块 ($moduleId)...")
            val zip = File(context.cacheDir, "$moduleId.zip")
            moduleZip = zip
            ZipOutputStream(FileOutputStream(zip)).use { zos ->
                val prop = buildString {
                    appendLine("id=$moduleId")
                    appendLine("name=OTheme - $fileName")
                    appendLine("version=v1")
                    appendLine("versionCode=1")
                    appendLine("author=OTheme")
                    appendLine("description=Injected by OTheme: $fileName")
                }
                zos.putNextEntry(ZipEntry("module.prop"))
                zos.write(prop.toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("system_ext/media/themeInner/$fileName"))
                FileInputStream(themeFile).use { it.copyTo(zos) }
                zos.closeEntry()
            }
            onLog("[+] 模块打包完成: ${zip.name}")

            val installCmd = when (rootType) {
                RootType.MAGISK -> "magisk --install-module '${zip.absolutePath}'"
                RootType.KERNELSU -> "ksud module install '${zip.absolutePath}'"
                RootType.APATCH -> "apd module install '${zip.absolutePath}'"
                RootType.UNKNOWN -> return "未检测到受支持的 Root 管理器"
            }

            onLog("[+] 正在安装模块...")
            val exitCode = execSuCommandStreaming(installCmd) { line -> onLog(line) }

            if (exitCode == 0) {
                onLog("[OK] 模块安装成功")
                onLog("[!] 需要重启设备使模块生效")
                null
            } else {
                val error = "模块安装失败（退出码 $exitCode）"
                onLog("[FAIL] $error")
                error
            }
        } catch (e: Exception) {
            val error = "安装模块出错: ${e.message}"
            Log.e(TAG, error, e)
            onLog("[ERR] $error")
            error
        } finally {
            moduleZip?.delete()
        }
    }

    /**
     * 安装主题的统一入口：优先以 Root 模块方式安全注入；若设备未安装
     * Magisk / KernelSU / APatch，则回退为直接写入 /system_ext/media/themeInner。
     */
    fun installThemeWithLog(context: Context, themePath: String, onLog: (String) -> Unit): String? {
        val moduleError = installThemeAsModule(context, themePath, onLog)
        if (moduleError == null) {
            return null
        }

        onLog("[!] 模块安装不可用，回退为直接注入模式")
        val fallbackError = installTheme(context, themePath, onLog)
        return fallbackError
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
     * 安装主题（从路径，直接注入，不解包）
     *
     * 不再解包主题文件，而是将 .theme 文件原样注入系统内置主题目录
     * (/system_ext/media/themeInner)，由系统自动识别为内置主题模块。
     * 这是模块安装失败/不可用时的回退方案，见 [installThemeWithLog]。
     */
    fun installTheme(context: Context, themePath: String, onLog: (String) -> Unit = {}): String? {
        Log.d(TAG, "Installing theme (raw inject) from: $themePath")

        return try {
            val fileName = File(themePath).name
            val targetPath = "$THEME_INNER_DIR/$fileName"

            // system_ext 默认只读，安装前需重新挂载为可写
            onLog("[+] 重新挂载 /system_ext 为可写...")
            execSuCommand("mount -o rw,remount /system_ext")

            // 确保目标目录存在
            onLog("[+] 创建目标目录 $THEME_INNER_DIR ...")
            val (mkdirExit, mkdirOutput) = execSuCommand("mkdir -p $THEME_INNER_DIR")
            if (mkdirExit != 0) {
                onLog("[FAIL] $mkdirOutput")
                return "安装失败: $mkdirOutput"
            }

            // 原样拷贝 .theme 文件，不做任何解包/转换
            onLog("[+] 写入 $targetPath ...")
            val (copyExit, copyOutput) = execSuCommand("cp -f '$themePath' '$targetPath'")
            if (copyExit != 0) {
                onLog("[FAIL] $copyOutput")
                return "安装失败: $copyOutput"
            }

            // 设置系统文件通用权限（root:root, 644）
            execSuCommand("chmod 644 '$targetPath'")
            execSuCommand("chown root:root '$targetPath'")

            // 尝试恢复只读挂载状态（失败不影响安装结果）
            execSuCommand("mount -o ro,remount /system_ext")

            Log.d(TAG, "Theme injected into $targetPath")
            onLog("[OK] 已写入 $targetPath")
            null
        } catch (e: Exception) {
            val error = "Error installing theme: ${e.message}"
            Log.e(TAG, error, e)
            onLog("[ERR] $error")
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
