package com.trmi.s3monitor.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.trmi.s3monitor.data.local.AlertManager
import com.trmi.s3monitor.data.local.HistoryManager
import com.trmi.s3monitor.data.local.PrefsManager
import com.trmi.s3monitor.data.model.SensorData
import com.trmi.s3monitor.data.remote.TrmiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Polling loop: fetches /data from ESP at the user's chosen interval,
 * appends to history, checks thresholds, logs alerts.
 */
class PollingService(
    private val api: TrmiApi,
    private val prefs: PrefsManager,
    private val history: HistoryManager,
    private val alerts: AlertManager
) {

    enum class Interval(val ms: Long, val label: String) {
        S10(10_000,  "10s"),
        S30(30_000,  "30s"),
        M1 (60_000,  "1m"),
        M5 (300_000, "5m"),
        M10(600_000, "10m")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var onDataCallback: ((SensorData) -> Unit)? = null
    private var onFailCallback: (() -> Unit)? = null

    // onFail đặt trước onData để cách gọi cũ `start { snap -> ... }` vẫn chạy y nguyên.
    suspend fun start(onFail: (() -> Unit)? = null, onData: (SensorData) -> Unit) {
        onDataCallback = onData
        onFailCallback = onFail
        job = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val intervalMs = prefs.pollInterval.ms

                val result = api.fetchData()
                result.onSuccess { raw ->
                    // ESP gửi ts = millis() từ lúc boot, KHÔNG phải Unix epoch.
                    // Gán lại ts = thời gian thực của điện thoại lúc nhận,
                    // để history.readRange() theo System.currentTimeMillis() khớp.
                    val snap = raw.copy(ts = System.currentTimeMillis())

                    history.append(snap)
                    checkAlerts(snap)
                    mainHandler.post { onDataCallback?.invoke(snap) }
                }
                result.onFailure { e ->
                    // Trước đây lỗi bị nuốt im lặng → debug mù. Giờ log ra.
                    Log.e("TrmiPolling", "fetchData failed: ${e.message}", e)
                    mainHandler.post { onFailCallback?.invoke() }
                }

                delay(intervalMs)
            }
        }
        job?.join()
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // ── Alert checking ────────────────────────────────────────────────────────

    private fun checkAlerts(snap: SensorData) {
        val th = prefs.threshold

        if (snap.status.temp) {
            if (snap.temp >= th.temp)
                alerts.append("🌡️ Nhiệt độ cao: ${snap.temp}°C (ngưỡng ${th.temp}°C)")
            if (snap.humi < th.humi_low)
                alerts.append("💧 Độ ẩm thấp: ${snap.humi}% (ngưỡng ${th.humi_low}%)")
        }
        if (snap.status.co2) {
            if (snap.co2 >= th.co2)
                alerts.append("🌬️ CO₂ cao: ${snap.co2}ppm (ngưỡng ${th.co2}ppm)")
        }
        if (snap.status.pm) {
            if (snap.pm25 >= th.pm25.toInt())
                alerts.append("💨 PM2.5 cao: ${snap.pm25}μg/m³ (ngưỡng ${th.pm25.toInt()}μg/m³)")
            if (snap.pm10 >= th.pm10.toInt())
                alerts.append("💨 PM10 cao: ${snap.pm10}μg/m³ (ngưỡng ${th.pm10.toInt()}μg/m³)")
        }
    }
}
