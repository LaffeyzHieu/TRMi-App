package com.trmi.s3monitor.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.trmi.s3monitor.data.model.Threshold
import com.trmi.s3monitor.service.PollingService

class PrefsManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("trmi_prefs", Context.MODE_PRIVATE)

    var pollInterval: PollingService.Interval
        get() {
            val name = prefs.getString("poll_interval", PollingService.Interval.S30.name)
            return PollingService.Interval.valueOf(name ?: PollingService.Interval.S30.name)
        }
        set(value) = prefs.edit().putString("poll_interval", value.name).apply()

    var threshold: Threshold
        get() = Threshold(
            temp     = prefs.getFloat("temp_crit",  35f),
            humi_low = prefs.getFloat("humi_low",   35f),
            co2      = prefs.getInt("co2_warn",     1000),
            pm1      = prefs.getFloat("pm1_crit",   100f),
            pm25     = prefs.getFloat("pm25_crit",  35f),
            pm10     = prefs.getFloat("pm10_crit",  50f)
        )
        set(value) {
            prefs.edit()
                .putFloat("temp_crit",  value.temp)
                .putFloat("humi_low",   value.humi_low)
                .putInt("co2_warn",     value.co2)
                .putFloat("pm1_crit",   value.pm1)
                .putFloat("pm25_crit",  value.pm25)
                .putFloat("pm10_crit",  value.pm10)
                .apply()
        }

    var espIp: String
        get() = prefs.getString("esp_ip", "192.168.4.1") ?: "192.168.4.1"
        set(value) = prefs.edit().putString("esp_ip", value).apply()

    /** Epoch ms của lần cuối nhận được data từ ESP (0 = chưa bao giờ). */
    var lastConnectMs: Long
        get() = prefs.getLong("last_connect_ms", 0L)
        set(value) = prefs.edit().putLong("last_connect_ms", value).apply()

    /**
     * UI mode:
     *  0 = Sáng (MODE_NIGHT_NO)
     *  1 = Tối  (MODE_NIGHT_YES)
     *  2 = Theo hệ thống (MODE_NIGHT_FOLLOW_SYSTEM)
     */
    var uiMode: Int
        get() = prefs.getInt("ui_mode", 0)
        set(value) = prefs.edit().putInt("ui_mode", value).apply()

    /** Convert UI mode index → AppCompatDelegate constant. */
    fun uiModeToAppCompat(): Int = when (uiMode) {
        1    -> AppCompatDelegate.MODE_NIGHT_YES
        2    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_NO
    }

    /** Tên user đặt cho GPIO (null = chưa đặt). ESP không biết tên, chỉ biết pin. */
    fun gpioName(pin: Int): String? = prefs.getString("gpio_name_$pin", null)

    /** Tên rỗng = xóa tên, pin quay về nhóm "chưa có chức năng". */
    fun setGpioName(pin: Int, name: String?) {
        val n = name?.trim().orEmpty()
        val e = prefs.edit()
        if (n.isEmpty()) e.remove("gpio_name_$pin") else e.putString("gpio_name_$pin", n)
        e.apply()
    }
}