package com.trmi.s3monitor.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trmi.s3monitor.data.model.SensorData
import java.io.File

class HistoryManager(private val context: Context) {

    companion object {
        // Home + Controller mỗi màn có PollingService riêng, cùng ghi 1 file
        // → khóa dùng chung cho mọi instance, không đọc trúng lúc đang ghi dở.
        private val lock = Any()
    }

    private val gson = Gson()
    private val file: File
        get() = File(context.filesDir, "history.json")

    private val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L

    fun readAll(): List<SensorData> {
        synchronized(lock) {
            if (!file.exists()) return emptyList()
            return try {
                val type = object : TypeToken<List<SensorData>>() {}.type
                gson.fromJson(file.readText(), type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun append(data: SensorData) {
        synchronized(lock) {
            val cutoff = System.currentTimeMillis() - sevenDaysMs
            val entries = readAll()
                .filter { it.ts > cutoff }
                .toMutableList()
            entries.add(data)
            writeAtomic(gson.toJson(entries))
        }
    }

    fun readRange(fromMs: Long, toMs: Long): List<SensorData> {
        return readAll().filter { it.ts in fromMs..toMs }
    }

    fun clear() {
        synchronized(lock) {
            if (file.exists()) file.delete()
        }
    }

    // Ghi ra file tạm rồi rename: app bị kill giữa chừng thì file cũ vẫn nguyên,
    // không còn cảnh đọc ra file cụt → list rỗng → append ghi đè mất sạch history.
    private fun writeAtomic(json: String) {
        val tmp = File(context.filesDir, "history.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }
}
