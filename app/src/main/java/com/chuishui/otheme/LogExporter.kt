package com.chuishui.otheme

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports diagnostic log for OTheme debugging and support.
 *
 * Log structure:
 * 1. Header — app version, Android version, device info
 * 2. Module status — installation state, directory checks, mount info
 * 3. Module installation logs
 * 4. Theme installation logs
 * 5. Android logcat (native logs)
 */
object LogExporter {
    private const val TAG = "LogExporter"
    private const val OTHEME_MODULE_DIR = "/data/adb/modules/otheme/system_ext/media/themeInner"
    private const val OTHEME_SYSTEM_DIR = "/system_ext/media/themeInner"

    /**
     * Generate the full diagnostic log content.
     */
    fun generateLog(context: Context): String {
        val sb = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val divider = "=".repeat(60)

        // ── Header ──
        sb.appendLine(divider)
        sb.appendLine("OTheme Diagnostic Log")
        sb.appendLine("Generated: $timestamp")
        sb.appendLine(divider)
        sb.appendLine()

        sb.appendLine("--- Device Info ---")
        sb.appendLine("App version: ${getAppVersion(context)}")
        sb.appendLine("Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Board: ${Build.BOARD}")
        sb.appendLine("Fingerprint: ${Build.FINGERPRINT}")
        sb.appendLine()

        // ── Module Status ──
        sb.appendLine(divider)
        sb.appendLine("Module Status")
        sb.appendLine(divider)
        sb.appendLine()

        val moduleInstalled = checkOthemeModule()
        sb.appendLine("OTheme module installed: $moduleInstalled")
        sb.appendLine()

        sb.appendLine("--- Directory Checks ---")
        val moduleDirExists = checkPathExists(OTHEME_MODULE_DIR)
        val systemDirExists = checkPathExists(OTHEME_SYSTEM_DIR)
        sb.appendLine("$OTHEME_MODULE_DIR exists: $moduleDirExists")
        sb.appendLine("$OTHEME_SYSTEM_DIR exists: $systemDirExists")
        sb.appendLine()

        sb.appendLine("--- Mount Info (system_ext) ---")
        val mountInfo = execSuCommand("mount | grep system_ext")
        sb.appendLine(mountInfo.ifEmpty { "(no system_ext mount found)" })
        sb.appendLine()

        // ── Module Installation Logs ──
        sb.appendLine(divider)
        sb.appendLine("Module Installation Logs")
        sb.appendLine(divider)
        sb.appendLine()

        val moduleLogs = collectModuleLogs()
        sb.appendLine(moduleLogs.ifEmpty { "(no module logs found)" })
        sb.appendLine()

        // ── Theme Installation Logs ──
        sb.appendLine(divider)
        sb.appendLine("Theme Installation Logs")
        sb.appendLine(divider)
        sb.appendLine()

        val themeLogs = collectThemeLogs()
        sb.appendLine(themeLogs.ifEmpty { "(no theme logs found)" })
        sb.appendLine()

        // ── Android Native Logs (logcat) ──
        sb.appendLine(divider)
        sb.appendLine("Android Native Logs (logcat)")
        sb.appendLine(divider)
        sb.appendLine()

        val logcat = collectLogcat()
        sb.appendLine(logcat.ifEmpty { "(logcat unavailable)" })

        return sb.toString()
    }

    /**
     * Save log to a file and return the file path.
     */
    fun exportToFile(context: Context): File {
        val content = generateLog(context)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val logFile = File(context.cacheDir, "otheme_log_$timestamp.txt")
        logFile.writeText(content)
        Log.d(TAG, "Log exported to: ${logFile.absolutePath}")
        return logFile
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun checkOthemeModule(): Boolean {
        val output = execSuCommand("[ -d '$OTHEME_MODULE_DIR' ] && echo INSTALLED || echo NOT_INSTALLED")
        return output.trim() == "INSTALLED"
    }

    private fun checkPathExists(path: String): Boolean {
        val output = execSuCommand("[ -e '$path' ] && echo YES || echo NO")
        return output.trim() == "YES"
    }

    private fun execSuCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute: $command", e)
            "(error: ${e.message})"
        }
    }

    private fun collectModuleLogs(): String {
        val logPaths = listOf(
            "/data/adb/modules/otheme/post-fs-data.sh.log",
            "/data/adb/modules/otheme/service.sh.log",
            "/data/adb/modules/otheme/uninstall.sh.log",
            "/data/adb/modules/otheme/update.log",
            "/data/adb/modules/otheme/auto_mount.log",
            "/data/adb/magisk/adb/modules.log",
            "/data/adb/ksu/modules.log",
            "/data/adb/apatch/modules.log",
            "/data/adb/modules/otheme/post-fs-data.log",
            "/data/adb/modules/otheme/service.log"
        )
        val sb = StringBuilder()
        for (path in logPaths) {
            val content = execSuCommand("[ -f '$path' ] && cat '$path' || echo ''")
            if (content.isNotEmpty() && !content.startsWith("(error:")) {
                sb.appendLine("--- $path ---")
                sb.appendLine(content)
                sb.appendLine()
            }
        }
        return sb.toString().trim()
    }

    private fun collectThemeLogs(): String {
        val logPaths = listOf(
            "/data/theme/install.log",
            "/data/theme/apply.log",
            "/data/adb/modules/otheme/system_ext/media/themeInner/install.log",
            "/data/adb/modules/otheme/system_ext/media/themeInner/theme.log"
        )
        val sb = StringBuilder()
        for (path in logPaths) {
            val content = execSuCommand("[ -f '$path' ] && cat '$path' || echo ''")
            if (content.isNotEmpty() && !content.startsWith("(error:")) {
                sb.appendLine("--- $path ---")
                sb.appendLine(content)
                sb.appendLine()
            }
        }

        // Fallback: pull OTheme-related logcat entries if no dedicated log files exist
        if (sb.isEmpty()) {
            sb.appendLine("[No dedicated log files found, using filtered logcat entries]")
            sb.appendLine()
            val logcat = execSuCommand("logcat -d -t 200 | grep -iE 'otheme|theme|module|SuFileOperations|ThemeParser'")
            if (logcat.isNotEmpty() && !logcat.startsWith("(error:")) {
                sb.appendLine(logcat)
            }
        }

        return sb.toString().trim()
    }

    private fun collectLogcat(): String {
        // First try OTheme-specific filter
        val othemeLogcat = execSuCommand("logcat -d -t 1000 | grep -iE 'otheme|SuFileOperations|ThemeParser|ThemeReader|themeInner'")
        if (othemeLogcat.isNotEmpty() && !othemeLogcat.startsWith("(error:")) {
            return "--- OTheme Logcat Entries ---\n$othemeLogcat"
        }

        // Fallback to raw logcat
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -d -t 500 *:V"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect logcat", e)
            "(logcat error: ${e.message})"
        }
    }
}
