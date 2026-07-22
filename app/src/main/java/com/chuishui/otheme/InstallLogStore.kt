package com.chuishui.otheme

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 持久化保存主题与模块安装日志，保留最近 MAX_LOGS 条记录。
 * 供 LogExporter 读取并合并到诊断日志中。
 */
object InstallLogStore {
    private const val TAG = "InstallLogStore"
    private const val DIR_NAME = "install_logs"
    private const val MAX_LOGS = 10

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    /**
     * 保存一次安装日志到磁盘。
     * @param context 上下文
     * @param logs 安装过程中的日志行
     * @param succeeded 是否成功
     * @param type 日志类型（如 "theme" 或 "module"）
     */
    fun save(context: Context, logs: List<String>, succeeded: Boolean?, type: String = "theme") {
        try {
            val dir = getLogDir(context)
            dir.mkdirs()

            val now = Date()
            val file = File(dir, "install_${type}_${fileNameFormat.format(now)}.log")

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

            file.writeText(sb.toString())
            Log.d(TAG, "Saved install log: ${file.name} (${logs.size} lines)")

            trimOldLogs(dir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save install log", e)
        }
    }

    /**
     * 读取所有持久化的安装日志，按时间正序拼接。
     */
    fun readAll(context: Context): String {
        val dir = getLogDir(context)
        if (!dir.exists()) return ""

        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension == "log" }
            ?.sortedBy { it.name }
            ?: return ""

        if (files.isEmpty()) return ""

        val sb = StringBuilder()
        for (file in files) {
            val content = file.readText().trim()
            if (content.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.appendLine()
                sb.appendLine(content)
            }
        }
        return sb.toString().trim()
    }

    private fun getLogDir(context: Context): File {
        return File(context.filesDir, DIR_NAME)
    }

    private fun trimOldLogs(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension == "log" }
            ?.sortedByDescending { it.name }
            ?: return

        if (files.size > MAX_LOGS) {
            files.drop(MAX_LOGS).forEach {
                it.delete()
                Log.d(TAG, "Trimmed old log: ${it.name}")
            }
        }
    }
}
