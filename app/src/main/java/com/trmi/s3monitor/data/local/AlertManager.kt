package com.trmi.s3monitor.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class AlertEntry(
    val ts: Long,
    val message: String
)

class AlertManager(private val context: Context) {

    companion object {
        // Cùng lý do với HistoryManager: nhiều PollingService cùng ghi 1 file.
        private val lock = Any()
    }

    private val gson = Gson()
    private val file: File
        get() = File(context.filesDir, "alert_log.json")

    private val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L

    fun readAll(): List<AlertEntry> {
        synchronized(lock) {
            if (!file.exists()) return emptyList()
            return try {
                val type = object : TypeToken<List<AlertEntry>>() {}.type
                gson.fromJson(file.readText(), type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun append(message: String) {
        synchronized(lock) {
            val cutoff = System.currentTimeMillis() - sevenDaysMs
            val entries = readAll()
                .filter { it.ts > cutoff }
                .toMutableList()
            entries.add(AlertEntry(ts = System.currentTimeMillis(), message = message))
            writeAtomic(gson.toJson(entries))
        }
    }

    fun clear() {
        synchronized(lock) {
            if (file.exists()) file.delete()
        }
    }

    // Ghi file tạm rồi rename → không bao giờ để lại file cụt.
    private fun writeAtomic(json: String) {
        val tmp = File(context.filesDir, "alert_log.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }
}
