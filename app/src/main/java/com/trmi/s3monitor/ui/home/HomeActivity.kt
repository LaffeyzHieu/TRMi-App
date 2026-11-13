package com.trmi.s3monitor.ui.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import com.trmi.s3monitor.R
import com.trmi.s3monitor.data.local.AlertManager
import com.trmi.s3monitor.data.local.HistoryManager
import com.trmi.s3monitor.data.local.PrefsManager
import com.trmi.s3monitor.data.model.SensorData
import com.trmi.s3monitor.data.remote.TrmiApi
import com.trmi.s3monitor.databinding.ActivityHomeBinding
import com.trmi.s3monitor.service.PollingService
import com.trmi.s3monitor.ui.base.BaseScreenActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : BaseScreenActivity<ActivityHomeBinding>() {

    // ── Data layer ────────────────────────────────────────────────────────────
    private lateinit var prefs: PrefsManager
    private lateinit var history: HistoryManager
    private lateinit var alerts: AlertManager
    private lateinit var api: TrmiApi
    private lateinit var polling: PollingService

    // ── Clock ─────────────────────────────────────────────────────────────────
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockFmt = SimpleDateFormat("HH:mm:ss  dd/MM/yyyy", Locale.getDefault())
    private val clockRunnable = object : Runnable {
        override fun run() {
            // status_headline hiển thị clock khi chưa có data
            if (!hasData) {
                binding.statusHeadline.text = clockFmt.format(Date())
            }
            clockHandler.postDelayed(this, 1_000)
        }
    }
    private var hasData = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun inflateBinding() = ActivityHomeBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs   = PrefsManager(this)
        history = HistoryManager(this)
        alerts  = AlertManager(this)
        api     = TrmiApi(prefs.espIp)
        polling = PollingService(api, prefs, history, alerts)

        applyTopBarInsets(binding.topBar)
        setupMainNav(binding.navBarInclude.navBar, R.id.nav_home)

        // tune icon → Settings
        binding.topBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_tune -> { openSecondaryDestination(R.id.nav_settings); true }
                else -> false
            }
        }

        // recent_alert → History
        binding.recentAlert.setOnClickListener { openMainDestination(R.id.nav_history) }

        // Initial UI state
        showDisconnected()

        // Start clock ticker
        clockHandler.post(clockRunnable)

        // Start polling
        lifecycleScope.launch {
            polling.start(onFail = { hasData = false; showDisconnected() }) { snap -> onNewData(snap) }
        }
    }

    override fun onResume() {
        super.onResume()
        updateRecentAlert()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        polling.stop()
    }

    // ── Data → UI ─────────────────────────────────────────────────────────────

    private fun onNewData(snap: SensorData) {
        hasData = true

        // ── status card: clock + ESP connected ──────────────────────────────
        binding.statusHeadline.text = clockFmt.format(Date(snap.ts))
        binding.statusBody.text = getString(R.string.state_connected)

        // ── alert card: TRMi Score ───────────────────────────────────────────
        val score = calcScore(snap)
        binding.alertHeadline.text = getString(R.string.home_score_label, score)
        binding.alertBody.text     = scoreMessage(score)

        // ── aqi card: sensor summary ─────────────────────────────────────────
        binding.aqiHeadline.text = buildSummaryLine(snap)
        binding.aqiBody.text     = buildPmLine(snap)

        // Lưu mốc "lần cuối có data" + cập nhật cảnh báo gần đây.
        prefs.lastConnectMs = snap.ts
        updateRecentAlert()
    }

    // ── Disconnected state ────────────────────────────────────────────────────

    private fun showDisconnected() {
        binding.statusHeadline.text = clockFmt.format(Date())
        binding.statusBody.text     = lastConnectText()
        binding.alertHeadline.text  = getString(R.string.home_waiting_title)
        binding.alertBody.text      = getString(R.string.home_waiting_body)
        binding.aqiHeadline.text    = "—"
        binding.aqiBody.text        = getString(R.string.home_waiting_aqi)
        updateRecentAlert()
    }

    /** "Lần cuối: HH:mm dd/MM" hoặc "Chưa kết nối lần nào". */
    private fun lastConnectText(): String {
        val last = prefs.lastConnectMs
        return if (last <= 0L) getString(R.string.state_never_connected)
        else getString(
            R.string.state_last_connect,
            SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(last))
        )
    }

    /** Dòng "cảnh báo gần đây": alert mới nhất, hoặc "Clean" nếu không có. */
    private fun updateRecentAlert() {
        val last = alerts.readAll().maxByOrNull { it.ts }
        binding.recentAlertSupport.text =
            last?.message ?: getString(R.string.home_alert_clean)
    }

    // ── Summary text builders ─────────────────────────────────────────────────

    /** "28.5°C  65%  CO₂: 842ppm" */
    private fun buildSummaryLine(snap: SensorData): String {
        val parts = mutableListOf<String>()
        if (snap.status.temp) {
            parts += "%.1f°C  %.0f%%".format(snap.temp, snap.humi)
        }
        if (snap.status.co2) {
            parts += "CO₂: ${snap.co2}ppm"
        }
        return if (parts.isEmpty()) getString(R.string.home_aqi_headline) else parts.joinToString("  |  ")
    }

    /** "PM1: 8  PM2.5: 12  PM10: 18 μg/m³" */
    private fun buildPmLine(snap: SensorData): String {
        if (!snap.status.pm) return getString(R.string.home_aqi_body)
        return "PM1: ${snap.pm1}  ·  PM2.5: ${snap.pm25}  ·  PM10: ${snap.pm10} μg/m³"
    }

    // ── TRMi Score (1 = tốt nhất, 5 = tệ nhất) ───────────────────────────────

    private fun calcScore(snap: SensorData): Int {
        val th = prefs.threshold

        fun tempLvl() = when {
            snap.temp >= th.temp          -> 5
            snap.temp >= th.temp - 3f     -> 4
            snap.humi < th.humi_low       -> 4
            else                          -> 1
        }
        fun co2Lvl() = when {
            snap.co2 >= th.co2 + 500      -> 5
            snap.co2 >= th.co2            -> 4
            snap.co2 >= 800               -> 2
            else                          -> 1
        }
        fun pm25Lvl() = when {
            snap.pm25 >= th.pm25.toInt() + 20 -> 5
            snap.pm25 >= th.pm25.toInt()      -> 4
            snap.pm25 >= 12                   -> 2
            else                              -> 1
        }
        fun pm10Lvl() = when {
            snap.pm10 >= th.pm10.toInt() + 30 -> 5
            snap.pm10 >= th.pm10.toInt()      -> 4
            snap.pm10 >= 25                   -> 2
            else                              -> 1
        }
        fun pm1Lvl() = when {
            snap.pm1 >= th.pm1.toInt() + 50 -> 5
            snap.pm1 >= th.pm1.toInt()      -> 4
            else                            -> 1
        }

        val lvls = listOf(
            if (snap.status.temp) tempLvl()  else 3,
            if (snap.status.co2)  co2Lvl()   else 3,
            if (snap.status.pm)   pm25Lvl()  else 3,
            if (snap.status.pm)   pm10Lvl()  else 3,
            if (snap.status.pm)   pm1Lvl()   else 3
        )
        return (lvls.average().toInt()).coerceIn(1, 5)
    }

    private fun scoreMessage(score: Int): String = when (score) {
        1    -> getString(R.string.trmi_msg_1)
        2    -> getString(R.string.trmi_msg_2)
        3    -> getString(R.string.trmi_msg_3)
        4    -> getString(R.string.trmi_msg_4)
        5    -> getString(R.string.trmi_msg_5)
        else -> ""
    }
}