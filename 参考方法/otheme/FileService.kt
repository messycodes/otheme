package com.chuishui.otheme

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileService(private val context: Context) : IFileService.Stub() {

    companion object {
        private const val TAG = "FileService"
        private const val THEME_DIR = "/data/theme"
        private const val BUFFER_SIZE = 8192
    }

    override fun destroy() {
        Log.d(TAG, "Service is being destroyed")
        System.exit(0)
    }

    override fun backupTheme(backupPath: String): String? {
        Log.d(TAG, "Backing up theme to: $backupPath")
        
        return try {
            val themeDir = File(THEME_DIR)
            if (!themeDir.exists() || !themeDir.isDirectory) {
                val error = "Theme directory does not exist or is not a directory"
                Log.e(TAG, error)
                return error
            }

            val backupFile = File(backupPath)
            backupFile.parentFile?.mkdirs()

            ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
                // 直接备份文件夹内容，不包含 theme 文件夹本身
                themeDir.listFiles()?.forEach { file ->
                    zipFile(file, file.name, zos)
                }
            }

            Log.d(TAG, "Theme backup completed successfully")
            null // Success
        } catch (e: Exception) {
            val error = "Error backing up theme: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    override fun installTheme(themePath: String): String? {
        Log.d(TAG, "Installing theme from: $themePath")
        
        return try {
            val themeFile = File(themePath)
            
            if (!themeFile.exists() || !themeFile.isFile) {
                val error = "Theme file does not exist or is not a file"
                Log.e(TAG, error)
                return error
            }

            val themeDir = File(THEME_DIR)
            if (!themeDir.exists()) {
                themeDir.mkdirs()
            }

            // 检查 config 文件是否存在
            val configFile = File(themeDir, "config")
            if (!configFile.exists()) {
                Log.d(TAG, "Config file not found, copying default config")
                context.assets.open("config").use { input ->
                    FileOutputStream(configFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            var fileCount = 0
            ZipInputStream(FileInputStream(themeFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val file = File(themeDir, entry.name)
                    
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                        fileCount++
                    }
                    
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 如果存在 lockscreen 文件或目录，重命名为 lock
            val lockscreenFile = File(themeDir, "lockscreen")
            if (lockscreenFile.exists()) {
                Log.d(TAG, "Renaming lockscreen to lock")
                lockscreenFile.renameTo(File(themeDir, "lock"))
            }

            Log.d(TAG, "Theme installation completed, extracted $fileCount files")
            null
        } catch (e: Exception) {
            val error = "Error installing theme: ${e.message}"
            Log.e(TAG, error, e)
            e.printStackTrace()
            error
        }
    }

    override fun installThemeFromFd(pfd: ParcelFileDescriptor): String? {
        Log.d(TAG, "Installing theme from ParcelFileDescriptor")
        
        return try {
            val themeDir = File(THEME_DIR)
            if (!themeDir.exists()) {
                themeDir.mkdirs()
            }

            // 检查 config 文件是否存在
            val configFile = File(themeDir, "config")
            if (!configFile.exists()) {
                Log.d(TAG, "Config file not found, copying default config")
                context.assets.open("config").use { input ->
                    FileOutputStream(configFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            var fileCount = 0
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val file = File(themeDir, entry.name)
                        
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { fos ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                            fileCount++
                        }
                        
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // 如果存在 lockscreen 文件或目录，重命名为 lock
            val lockscreenFile = File(themeDir, "lockscreen")
            if (lockscreenFile.exists()) {
                Log.d(TAG, "Renaming lockscreen to lock")
                lockscreenFile.renameTo(File(themeDir, "lock"))
            }
            
            Log.d(TAG, "Theme installation completed. Extracted $fileCount files.")
            null
        } catch (e: Exception) {
            val error = "Error installing theme: ${e.message}"
            Log.e(TAG, error, e)
            e.printStackTrace()
            error
        }
    }

    override fun getThemeInfo(): List<String> {
        Log.d(TAG, "Getting theme info")
        
        return try {
            val themeDir = File(THEME_DIR)
            
            if (!themeDir.exists() || !themeDir.isDirectory) {
                Log.e(TAG, "Theme directory does not exist")
                return emptyList()
            }
            
            val fileList = mutableListOf<String>()
            themeDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    fileList.add(file.absolutePath)
                }
            }
            
            Log.d(TAG, "Found ${fileList.size} files in theme directory")
            fileList
        } catch (e: Exception) {
            Log.e(TAG, "Error getting theme info: ${e.message}", e)
            emptyList()
        }
    }

    override fun getInstalledThemeInfo(): String? {
        Log.d(TAG, "Getting installed theme info")
        
        return try {
            val themeInfoFile = File(THEME_DIR, "themeInfo.xml")
            
            if (!themeInfoFile.exists() || !themeInfoFile.isFile) {
                Log.e(TAG, "themeInfo.xml does not exist")
                return null
            }
            
            val content = themeInfoFile.readText()
            Log.d(TAG, "Read themeInfo.xml, size: ${content.length} bytes")
            content
        } catch (e: Exception) {
            Log.e(TAG, "Error reading themeInfo.xml: ${e.message}", e)
            null
        }
    }
    
    override fun isPackageInstalled(packageName: String): Boolean {
        return try {
            Log.d(TAG, "Checking if package is installed: $packageName")
            
            // 方法1: pm path
            val process = Runtime.getRuntime().exec("pm path $packageName")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.contains(packageName)) {
                Log.d(TAG, "Package $packageName found via pm path")
                return true
            }
            
            // 方法2: 兜底 — Android 原生 PackageManager API
            try {
                val info = context.packageManager.getPackageInfo(packageName, 0)
                Log.d(TAG, "Package $packageName found via PackageManager API, version: ${info.versionName}")
                return true
            } catch (_: PackageManager.NameNotFoundException) {
                Log.d(TAG, "Package $packageName not found via PackageManager API")
            }
            
            Log.d(TAG, "Package $packageName NOT installed")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking package installation: ${e.message}", e)
            // 异常时最后尝试原生 API
            try {
                val info = context.packageManager.getPackageInfo(packageName, 0)
                Log.d(TAG, "Package $packageName found via fallback PackageManager API")
                return true
            } catch (_: PackageManager.NameNotFoundException) { }
            false
        }
    }
    
    override fun restartProcesses(packages: List<String>): String? {
        Log.d(TAG, "===== Starting hot reboot =====")

        return try {
            val process = Runtime.getRuntime().exec("am restart")
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                Log.d(TAG, "Hot reboot triggered successfully")
                null
            } else {
                val error = "Hot reboot failed with exit code: $exitCode"
                Log.e(TAG, error)
                error
            }
        } catch (e: Exception) {
            val error = "Hot reboot failed: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    override fun uninstallTheme(): String? {
        Log.d(TAG, "===== Uninstalling theme =====")
        
        return try {
            val dir = File(THEME_DIR)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { it.name != "config" && it.name != "applying" }?.forEach { 
                    val deleted = it.deleteRecursively()
                    Log.d(TAG, "Deleted ${it.absolutePath}: $deleted")
                }
            }
            // 确保 applying 目录存在
            val applyingDir = File(THEME_DIR, "applying")
            if (!applyingDir.exists()) {
                applyingDir.mkdirs()
            }
            Log.d(TAG, "Theme uninstalled successfully (config and applying preserved)")
            null
        } catch (e: Exception) {
            val error = "Uninstall failed: ${e.message}"
            Log.e(TAG, error, e)
            error
        }
    }

    private fun zipFile(file: File, zipPath: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            // 添加目录条目
            val entry = ZipEntry("$zipPath/")
            zos.putNextEntry(entry)
            zos.closeEntry()
            
            // 递归处理子文件
            file.listFiles()?.forEach { child ->
                zipFile(child, "$zipPath/${child.name}", zos)
            }
        } else {
            // 添加文件
            val entry = ZipEntry(zipPath)
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
            }
            zos.closeEntry()
        }
    }
}
