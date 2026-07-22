package com.chuishui.otheme

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 持久化保存主题与模块安装日志到 /data/adb/modules/otheme/，保留最近 MAX_LOGS 条记录。
 * 供 LogExporter 读取并合并到诊断日志中。
 */
object InstallLogStore {
    private const val TAG = "InstallLogStore"
    private const val LOG_DIR = "/data/adb/modules/otheme"
    private const val MAX_LOGS = 10

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    /**
     * 保存一次安装日志到 /data/adb/modules/otheme/。
     */
    fun save(logs: List<String>, succeeded: Boolean?, type: String = "theme") {
        try {
            val now = Date()
            val fileName = "install_${type}_${fileNameFormat.format(now)}.log"
            val filePath = "$LOG_DIR/$fileName"

            val sb = StringBuilder()
            sb.appendLine("===== Installation Log =====")
            sb.appendLine("Time: ${timestampFormat.format(now)}")
            sb.appendLine("Type: $type")
            sb.appendLine("Result: ${when (succeeded) { true -> "SUCCESS"; false -> "FAILED"; null -> "UNKNOWN" }}")
            sb.appendLine("Lines: ${logs.size}")
            sb.appendLine("=".repeat(40))
            for (line in logs) {
                sb.appendLine(line)
            }

            // 先写到临时文件，再 su cp 到模块目录
            val tmpPath = "/data/local/tmp/otheme_install_tmp.log"
            execSuCommand("cat > '$tmpPath' << 'OTHEME_EOF'\n${sb}\nOTHEME_EOF")
            execSuCommand("cp '$tmpPath' '$filePath' && chmod 644 '$filePath' && rm -f '$tmpPath'")
            Log.d(TAG, "Saved install log: $fileName (${logs.size} lines)")

            trimOldLogs()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save install log", e)
        }
    }

    /**
     * 读取所有持久化的安装日志，按时间正序拼接。
     */
    fun readAll(): String {
        val files = listLogFiles()
        if (files.isEmpty()) return ""

        val sb = StringBuilder()
        for (filePath in files) {
            val content = execSuCommand("[ -f '$filePath' ] && cat '$filePath' || echo ''")
            if (content.isNotEmpty() && !content.startsWith("(error:")) {
                if (sb.isNotEmpty()) sb.appendLine()
                sb.appendLine(content.trim())
            }
        }
        return sb.toString().trim()
    }

    private fun listLogFiles(): List<String> {
        val output = execSuCommand("ls -1 '$LOG_DIR'/install_*.log 2>/dev/null || echo ''")
        if (output.isEmpty() || output.startsWith("(error:")) return emptyList()
        return output.lines().filter { it.isNotBlank() }.sorted()
    }

    private fun trimOldLogs() {
        val files = listLogFiles()
        if (files.size > MAX_LOGS) {
            files.drop(MAX_LOGS).forEach { path ->
                execSuCommand("rm -f '$path'")
                Log.d(TAG, "Trimmed old log: $path")
            }
        }
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
}
