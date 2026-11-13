package com.trmi.s3monitor.ui.controller

import android.graphics.Color
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.trmi.s3monitor.R
import com.trmi.s3monitor.data.local.AlertManager
import com.trmi.s3monitor.data.local.HistoryManager
import com.trmi.s3monitor.data.local.PrefsManager
import com.trmi.s3monitor.data.model.SensorData
import com.trmi.s3monitor.data.remote.TrmiApi
import com.trmi.s3monitor.databinding.ActivityControllerBinding
import com.trmi.s3monitor.service.PollingService
import com.trmi.s3monitor.ui.base.BaseScreenActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ControllerActivity : BaseScreenActivity<ActivityControllerBinding>() {

    private lateinit var prefs: PrefsManager
    private lateinit var history: HistoryManager
    private lateinit var alerts: AlertManager
    private lateinit var api: TrmiApi
    private lateinit var polling: PollingService

    // Current time range in ms
    private var rangeMs = 60 * 60 * 1000L   // default 1h

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val clockFmt = SimpleDateFormat("HH:mm:ss  dd/MM/yyyy", Locale.getDefault())

    // M3 purple palette for charts
    private val colorTemp  = Color.parseColor("#B5179E")
    private val colorHumi  = Color.parseColor("#4361EE")
    private val colorCo2   = Color.parseColor("#F72585")
    private val colorPm1   = Color.parseColor("#4CC9F0")
    private val colorPm25  = Color.parseColor("#F77F00")
    private val colorPm10  = Color.parseColor("#FCBF49")

    override fun inflateBinding() = ActivityControllerBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs   = PrefsManager(this)
        history = HistoryManager(this)
        alerts  = AlertManager(this)
        api     = TrmiApi(prefs.espIp)
        polling = PollingService(api, prefs, history, alerts)

        applyTopBarInsets(binding.topBar)
        setupMainNav(binding.navBarInclude.navBar, R.id.nav_controller)

        binding.topBar.setNavigationOnClickListener { openMainDestination(R.id.nav_home) }
        binding.topBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_tune -> { openSecondaryDestination(R.id.nav_settings); true }
                else -> false
            }
        }

        // Init charts (empty state)
        initChart(binding.chartTemp, "°C")
        initChart(binding.chartHumi, "%")
        initChart(binding.chartCo2,  "ppm")
        initChart(binding.chartPm,   "μg/m³")

        // Time range chip selector
        binding.timeRangeChips.setOnCheckedStateChangeListener { _, checkedIds ->
            rangeMs = when (checkedIds.firstOrNull()) {
                R.id.chip_6h  -> 6  * 60 * 60 * 1000L
                R.id.chip_24h -> 24 * 60 * 60 * 1000L
                else          -> 1  * 60 * 60 * 1000L
            }
            loadChartsFromHistory()
        }

        // Load history immediately
        loadChartsFromHistory()

        // Start polling → update status card + refresh charts on each new point
        lifecycleScope.launch {
            polling.start(onFail = { onConnectionLost() }) { snap -> onNewData(snap) }
        }
    }

    // Mốc lần cuối nhận data thành công (để hiện trong trạng thái mất kết nối)
    private var lastOkTs = 0L

    private fun onConnectionLost() {
        binding.ctrlStatusBody.text =
            if (lastOkTs > 0L) getString(R.string.state_lost_connection, timeFmt.format(Date(lastOkTs)))
            else getString(R.string.state_never_connected)
        // Mất ESP thì không biết trạng thái từng cảm biến → ẩn banner cảm biến
        binding.ctrlSensorBanner.visibility = android.view.View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        polling.stop()
    }

    // ── New data from polling ─────────────────────────────────────────────────

    private fun onNewData(snap: SensorData) {
        // Update status card
        binding.ctrlStatusTime.text = clockFmt.format(Date(snap.ts))
        binding.ctrlStatusBody.text = getString(R.string.state_connected)
        lastOkTs = snap.ts

        // Banner cảm biến mất kết nối
        val lost = buildList {
            if (!snap.status.temp) add(getString(R.string.ctrl_label_temp) + "/" + getString(R.string.ctrl_label_humi))
            if (!snap.status.co2)  add(getString(R.string.ctrl_label_co2))
            if (!snap.status.pm)   add(getString(R.string.ctrl_label_pm))
        }
        binding.ctrlSensorBanner.visibility = if (lost.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        binding.ctrlSensorBannerText.text = getString(R.string.ctrl_sensor_lost, lost.joinToString(", "))

        // Refresh charts with latest history window
        loadChartsFromHistory()
    }

    // ── Load history → charts ─────────────────────────────────────────────────

    private fun loadChartsFromHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val all = history.readAll()
            if (all.isEmpty()) return@launch

            // Window bám theo điểm đo cuối, không bám đồng hồ: ngắt kết nối lâu
            // rồi nối lại thì data cũ không bị trượt ra ngoài khung.
            val lastTs = all.maxOf { it.ts }
            val points = all.filter { it.ts in (lastTs - rangeMs)..lastTs }

            withContext(Dispatchers.Main) {
                if (points.isEmpty()) return@withContext
                applyTempChart(points)
                applyHumiChart(points)
                applyCo2Chart(points)
                applyPmChart(points)
            }
        }
    }

    // ── Chart builders ────────────────────────────────────────────────────────

    private fun applyTempChart(data: List<SensorData>) {
        val valid = data.filter { it.status.temp }
        if (valid.isEmpty()) return

        val entries = valid.mapIndexed { i, s -> Entry(i.toFloat(), s.temp) }
        val ds = styledDataSet(entries, "Temp", colorTemp)
        binding.chartTemp.data = LineData(ds)
        binding.chartTemp.xAxis.valueFormatter = timeFormatter(valid)
        binding.chartTemp.invalidate()

        val mean = valid.map { it.temp }.average()
        val min  = valid.minOf { it.temp }
        val max  = valid.maxOf { it.temp }
        binding.ctrlTempStats.text = "avg %.1f  min %.1f  max %.1f °C".format(mean, min, max)
    }

    private fun applyHumiChart(data: List<SensorData>) {
        val valid = data.filter { it.status.temp }
        if (valid.isEmpty()) return

        val entries = valid.mapIndexed { i, s -> Entry(i.toFloat(), s.humi) }
        val ds = styledDataSet(entries, "RH", colorHumi)
        binding.chartHumi.data = LineData(ds)
        binding.chartHumi.xAxis.valueFormatter = timeFormatter(valid)
        binding.chartHumi.invalidate()

        val mean = valid.map { it.humi }.average()
        val min  = valid.minOf { it.humi }
        val max  = valid.maxOf { it.humi }
        binding.ctrlHumiStats.text = "avg %.0f  min %.0f  max %.0f %%".format(mean, min, max)
    }

    private fun applyCo2Chart(data: List<SensorData>) {
        val valid = data.filter { it.status.co2 }
        if (valid.isEmpty()) return

        val entries = valid.mapIndexed { i, s -> Entry(i.toFloat(), s.co2.toFloat()) }
        val ds = styledDataSet(entries, "CO₂", colorCo2)
        binding.chartCo2.data = LineData(ds)
        binding.chartCo2.xAxis.valueFormatter = timeFormatter(valid)
        binding.chartCo2.invalidate()

        val mean = valid.map { it.co2 }.average()
        val min  = valid.minOf { it.co2 }
        val max  = valid.maxOf { it.co2 }
        binding.ctrlCo2Stats.text = "avg %.0f  min %d  max %d ppm".format(mean, min, max)
    }

    private fun applyPmChart(data: List<SensorData>) {
        val valid = data.filter { it.status.pm }
        if (valid.isEmpty()) return

        val pm1Entries  = valid.mapIndexed { i, s -> Entry(i.toFloat(), s.pm1.toFloat()) }
        val pm25Entries = valid.mapIndexed { i, s -> Entry(i.toFloat(), s.pm25.toFloat()) }
        val pm10Entries = valid.mapIndexed { i, s -> Entry(i.toFloat(), s.pm10.toFloat()) }

        val ds1  = styledDataSet(pm1Entries,  "PM1",   colorPm1)
        val ds25 = styledDataSet(pm25Entries, "PM2.5", colorPm25)
        val ds10 = styledDataSet(pm10Entries, "PM10",  colorPm10)

        binding.chartPm.data = LineData(ds1, ds25, ds10)
        binding.chartPm.xAxis.valueFormatter = timeFormatter(valid)
        binding.chartPm.invalidate()

        val maxPm25 = valid.maxOf { it.pm25 }
        val maxPm10 = valid.maxOf { it.pm10 }
        binding.ctrlPmStats.text = "PM2.5 max %d  ·  PM10 max %d μg/m³".format(maxPm25, maxPm10)
    }

    // ── MPAndroidChart helpers ────────────────────────────────────────────────

    private fun initChart(chart: LineChart, unit: String) {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled      = true
            setTouchEnabled(true)
            isDragEnabled         = true
            isScaleXEnabled       = true
            isScaleYEnabled       = false
            setDrawGridBackground(false)
            setNoDataText("Chưa có dữ liệu")

            xAxis.apply {
                position        = XAxis.XAxisPosition.BOTTOM
                granularity     = 1f
                setDrawGridLines(false)
                textColor       = Color.GRAY
                textSize        = 10f
                labelRotationAngle = -30f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor       = Color.parseColor("#22000000")
                textColor       = Color.GRAY
                textSize        = 10f
            }
            axisRight.isEnabled = false
        }
    }

    private fun styledDataSet(entries: List<Entry>, label: String, color: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            this.color          = color
            lineWidth           = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode                = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity      = 0.2f
            setDrawFilled(true)
            fillAlpha           = 30
            fillColor           = color
        }
    }

    private fun timeFormatter(data: List<SensorData>): ValueFormatter {
        return object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt().coerceIn(0, data.size - 1)
                return timeFmt.format(Date(data[idx].ts))
            }
        }
    }
}
