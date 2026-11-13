package com.trmi.s3monitor.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.trmi.s3monitor.R
import com.trmi.s3monitor.data.local.PrefsManager
import com.trmi.s3monitor.data.model.Threshold
import com.trmi.s3monitor.data.remote.TrmiApi
import com.trmi.s3monitor.databinding.ActivitySettingsBinding
import com.trmi.s3monitor.service.PollingService
import com.trmi.s3monitor.ui.base.BaseScreenActivity
import kotlinx.coroutines.launch

class SettingsActivity : BaseScreenActivity<ActivitySettingsBinding>() {

    private val pollRateOptions = arrayOf("10s", "30s", "1m", "5m", "10m")
    private var currentPollRateIndex = 1  // 30s default
    private val uiModeOptions = arrayOf("Sáng", "Tối", "Theo hệ thống")

    /** SharedPreferences cho slider (display + persist riêng). */
    private val thresholdPrefs by lazy {
        getSharedPreferences(ThresholdDefaults.PREFS_NAME, MODE_PRIVATE)
    }

    /** Data layer dùng chung với Home/Controller/Polling + ESP. */
    private lateinit var prefs: PrefsManager
    private lateinit var api: TrmiApi

    override fun inflateBinding(): ActivitySettingsBinding =
        ActivitySettingsBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PrefsManager(this)
        api   = TrmiApi(prefs.espIp)

        applyTopBarInsets(binding.topBar)
        setupSecondaryNav(binding.navBarInclude.navBar, R.id.nav_settings)

        binding.topBar.setNavigationOnClickListener {
            openMainDestination(R.id.nav_home)
        }
        binding.topBar.setOnMenuItemClickListener { item -> onTopBarMenuItem(item) }

        // Poll rate: load index hiện tại từ PrefsManager (nguồn thật).
        currentPollRateIndex = intervalToIndex(prefs.pollInterval)
        binding.pollRateSupport.text =
            "${pollRateOptions[currentPollRateIndex]} | ${pollRateOptions.joinToString()}"

        binding.itemPollRate.setOnClickListener { showPollRateDialog() }
        binding.itemUiMode.setOnClickListener { showUiModeDialog() }
        binding.itemClearLog.setOnClickListener { confirmClearLog() }

        setupThresholds()

        // Nút "Lưu & gửi xuống ESP".
        binding.btnSaveThreshold.setOnClickListener { saveAndPush() }
    }

    // -----------------------------------------------------------------
    // Top app bar menu
    // -----------------------------------------------------------------

    private fun onTopBarMenuItem(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open_external -> {
                openUrl("https://jvmelaffeyz.web.app/")
                true
            }
            else -> false
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // -----------------------------------------------------------------
    // Poll rate index <-> Interval
    // -----------------------------------------------------------------

    private fun indexToInterval(i: Int) = when (i) {
        0 -> PollingService.Interval.S10
        1 -> PollingService.Interval.S30
        2 -> PollingService.Interval.M1
        3 -> PollingService.Interval.M5
        4 -> PollingService.Interval.M10
        else -> PollingService.Interval.S30
    }

    private fun intervalToIndex(iv: PollingService.Interval) = when (iv) {
        PollingService.Interval.S10 -> 0
        PollingService.Interval.S30 -> 1
        PollingService.Interval.M1  -> 2
        PollingService.Interval.M5  -> 3
        PollingService.Interval.M10 -> 4
    }

    // -----------------------------------------------------------------
    // List item dialogs
    // -----------------------------------------------------------------

    private fun showPollRateDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_poll_rate_dialog_title)
            .setSingleChoiceItems(
                pollRateOptions,
                currentPollRateIndex
            ) { dialog, which ->
                currentPollRateIndex = which
                binding.pollRateSupport.text =
                    "${pollRateOptions[which]} | ${pollRateOptions.joinToString()}"
                // Ghi vào nguồn thật → PollingService áp dụng ở cycle kế.
                prefs.pollInterval = indexToInterval(which)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.settings_clear_log_no, null)
            .show()
    }

    private fun showUiModeDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_ui_dialog_title)
            .setSingleChoiceItems(uiModeOptions, prefs.uiMode) { dialog, which ->
                prefs.uiMode = which
                androidx.appcompat.app.AppCompatDelegate
                    .setDefaultNightMode(prefs.uiModeToAppCompat())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.settings_clear_log_no, null)
            .show()
    }

    private fun confirmClearLog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.settings_clear_log_confirm)
            .setPositiveButton(R.string.settings_clear_log_yes) { _, _ ->
                // Xóa log cảnh báo thật.
                com.trmi.s3monitor.data.local.AlertManager(this).clear()
                Toast.makeText(
                    this,
                    R.string.settings_log_cleared_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.settings_clear_log_no, null)
            .show()
    }

    // -----------------------------------------------------------------
    // Save & push threshold → PrefsManager + ESP
    // -----------------------------------------------------------------

    /** Gom giá trị slider hiện tại → Threshold model. */
    private fun buildThresholdFromPrefs(): Threshold {
        val co2     = thresholdPrefs.getFloat(ThresholdDefaults.KEY_CO2, ThresholdDefaults.CO2_DEFAULT)
        val tempEnd = thresholdPrefs.getFloat(ThresholdDefaults.KEY_TEMP_END, ThresholdDefaults.TEMP_DEFAULT[1])
        val rhStart = thresholdPrefs.getFloat(ThresholdDefaults.KEY_RH_START, ThresholdDefaults.RH_DEFAULT[0])
        val pm1     = thresholdPrefs.getFloat(ThresholdDefaults.KEY_PM1, ThresholdDefaults.PM1_DEFAULT)
        val pm25    = thresholdPrefs.getFloat(ThresholdDefaults.KEY_PM25, ThresholdDefaults.PM25_DEFAULT)
        val pm10    = thresholdPrefs.getFloat(ThresholdDefaults.KEY_PM10, ThresholdDefaults.PM10_DEFAULT)

        return Threshold(
            temp     = tempEnd,          // ngưỡng nhiệt cao
            humi_low = rhStart,          // ngưỡng ẩm thấp
            co2      = co2.toInt(),
            pm1      = pm1,
            pm25     = pm25,
            pm10     = pm10
        )
    }

    private fun saveAndPush() {
        val t = buildThresholdFromPrefs()

        // 1. Lưu local (Home/Controller/Polling dùng ngay).
        prefs.threshold = t

        // 2. Gửi xuống ESP.
        binding.btnSaveThreshold.isEnabled = false
        lifecycleScope.launch {
            val result = api.pushThreshold(t)
            binding.btnSaveThreshold.isEnabled = true
            result.onSuccess {
                Snackbar.make(
                    binding.root,
                    "Đã lưu & gửi xuống ESP ✓",
                    Snackbar.LENGTH_SHORT
                ).show()
            }.onFailure { e ->
                Snackbar.make(
                    binding.root,
                    "Lưu local OK, gửi ESP lỗi: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    // -----------------------------------------------------------------
    // Threshold sliders (ComposeView-hosted) — giữ nguyên
    // -----------------------------------------------------------------

    private fun setupThresholds() {
        setupCo2Slider()
        setupTempSlider()
        setupRhSlider()
        setupPm1Slider()
        setupPm25Slider()
        setupPm10Slider()
    }

    private fun setupCo2Slider() {
        val initial = thresholdPrefs.getFloat(ThresholdDefaults.KEY_CO2, ThresholdDefaults.CO2_DEFAULT)
        binding.co2Text.text = formatCo2(initial)
        binding.co2SliderCompose.setContent {
            MaterialTheme {
                var value by remember { mutableFloatStateOf(initial) }
                Slider(
                    value = value,
                    onValueChange = { nv ->
                        value = nv
                        binding.co2Text.text = formatCo2(nv)
                        thresholdPrefs.edit().putFloat(ThresholdDefaults.KEY_CO2, nv).apply()
                    },
                    valueRange = ThresholdDefaults.CO2_MIN..ThresholdDefaults.CO2_MAX,
                    colors = sliderColors()
                )
            }
        }
    }

    private fun setupTempSlider() {
        val s = thresholdPrefs.getFloat(ThresholdDefaults.KEY_TEMP_START, ThresholdDefaults.TEMP_DEFAULT[0])
        val e = thresholdPrefs.getFloat(ThresholdDefaults.KEY_TEMP_END, ThresholdDefaults.TEMP_DEFAULT[1])
        binding.tempText.text = formatTemp(s, e)
        binding.tempSliderCompose.setContent {
            MaterialTheme {
                var range by remember { mutableStateOf(s..e) }
                RangeSlider(
                    value = range,
                    onValueChange = { nr ->
                        range = nr
                        binding.tempText.text = formatTemp(nr.start, nr.endInclusive)
                        thresholdPrefs.edit()
                            .putFloat(ThresholdDefaults.KEY_TEMP_START, nr.start)
                            .putFloat(ThresholdDefaults.KEY_TEMP_END, nr.endInclusive)
                            .apply()
                    },
                    valueRange = ThresholdDefaults.TEMP_MIN..ThresholdDefaults.TEMP_MAX,
                    colors = sliderColors()
                )
            }
        }
    }

    private fun setupRhSlider() {
        val s = thresholdPrefs.getFloat(ThresholdDefaults.KEY_RH_START, ThresholdDefaults.RH_DEFAULT[0])
        val e = thresholdPrefs.getFloat(ThresholdDefaults.KEY_RH_END, ThresholdDefaults.RH_DEFAULT[1])
        binding.rhText.text = formatRh(s, e)
        binding.rhSliderCompose.setContent {
            MaterialTheme {
                var range by remember { mutableStateOf(s..e) }
                RangeSlider(
                    value = range,
                    onValueChange = { nr ->
                        range = nr
                        binding.rhText.text = formatRh(nr.start, nr.endInclusive)
                        thresholdPrefs.edit()
                            .putFloat(ThresholdDefaults.KEY_RH_START, nr.start)
                            .putFloat(ThresholdDefaults.KEY_RH_END, nr.endInclusive)
                            .apply()
                    },
                    valueRange = ThresholdDefaults.RH_MIN..ThresholdDefaults.RH_MAX,
                    colors = sliderColors()
                )
            }
        }
    }

    private fun setupPm1Slider() {
        val initial = thresholdPrefs.getFloat(ThresholdDefaults.KEY_PM1, ThresholdDefaults.PM1_DEFAULT)
        binding.pm1Text.text = formatPm(R.string.settings_pm1_template, initial)
        binding.pm1SliderCompose.setContent {
            MaterialTheme {
                var value by remember { mutableFloatStateOf(initial) }
                Slider(
                    value = value,
                    onValueChange = { nv ->
                        value = nv
                        binding.pm1Text.text = formatPm(R.string.settings_pm1_template, nv)
                        thresholdPrefs.edit().putFloat(ThresholdDefaults.KEY_PM1, nv).apply()
                    },
                    valueRange = ThresholdDefaults.PM1_MIN..ThresholdDefaults.PM1_MAX,
                    colors = sliderColors()
                )
            }
        }
    }

    private fun setupPm25Slider() {
        val initial = thresholdPrefs.getFloat(ThresholdDefaults.KEY_PM25, ThresholdDefaults.PM25_DEFAULT)
        binding.pm25Text.text = formatPm(R.string.settings_pm25_template, initial)
        binding.pm25SliderCompose.setContent {
            MaterialTheme {
                var value by remember { mutableFloatStateOf(initial) }
                Slider(
                    value = value,
                    onValueChange = { nv ->
                        value = nv
                        binding.pm25Text.text = formatPm(R.string.settings_pm25_template, nv)
                        thresholdPrefs.edit().putFloat(ThresholdDefaults.KEY_PM25, nv).apply()
                    },
                    valueRange = ThresholdDefaults.PM25_MIN..ThresholdDefaults.PM25_MAX,
                    colors = sliderColors()
                )
            }
        }
    }

    private fun setupPm10Slider() {
        val initial = thresholdPrefs.getFloat(ThresholdDefaults.KEY_PM10, ThresholdDefaults.PM10_DEFAULT)
        binding.pm10Text.text = formatPm(R.string.settings_pm10_template, initial)
        binding.pm10SliderCompose.setContent {
            MaterialTheme {
                var value by remember { mutableFloatStateOf(initial) }
                Slider(
                    value = value,
                    onValueChange = { nv ->
                        value = nv
                        binding.pm10Text.text = formatPm(R.string.settings_pm10_template, nv)
                        thresholdPrefs.edit().putFloat(ThresholdDefaults.KEY_PM10, nv).apply()
                    },
                    valueRange = ThresholdDefaults.PM10_MIN..ThresholdDefaults.PM10_MAX,
                    colors = sliderColors()
                )
            }
        }
    }

    @Composable
    private fun sliderColors() = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
        activeTickColor = MaterialTheme.colorScheme.primary,
        inactiveTickColor = MaterialTheme.colorScheme.secondaryContainer
    )

    private fun formatCo2(value: Float): String =
        getString(R.string.settings_co2_template, value)

    private fun formatTemp(start: Float, end: Float): String =
        getString(R.string.settings_temp_template, start, end)

    private fun formatRh(start: Float, end: Float): String =
        getString(R.string.settings_rh_template, start, end)

    private fun formatPm(formatRes: Int, value: Float): String =
        getString(formatRes, value)
}