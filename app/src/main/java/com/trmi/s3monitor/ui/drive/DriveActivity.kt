package com.trmi.s3monitor.ui.drive

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.trmi.s3monitor.R
import com.trmi.s3monitor.data.local.PrefsManager
import com.trmi.s3monitor.data.model.GpioPin
import com.trmi.s3monitor.data.remote.TrmiApi
import com.trmi.s3monitor.databinding.ActivityDriveBinding
import com.trmi.s3monitor.databinding.ItemDriveGpioBinding
import com.trmi.s3monitor.ui.base.BaseScreenActivity
import kotlinx.coroutines.launch

/**
 * Drive: bật/tắt GPIO của ESP qua GET /gpio + POST /gpio/set.
 * Tên GPIO do app lưu (PrefsManager); ESP chỉ biết số pin + trạng thái.
 *
 *  - Card "đang bật"  → list pin đang bật (switch tắt được ngay).
 *  - "Ấn để hiện..."  → mở/đóng list pin đang tắt.
 *  - "Ấn ghép..."     → chọn pin chưa có tên để đặt tên.
 *  - Ấn vào 1 row     → inline dialog đặt tên cho pin đó.
 */
class DriveActivity : BaseScreenActivity<ActivityDriveBinding>() {

    private lateinit var prefs: PrefsManager
    private lateinit var api: TrmiApi

    private var pins: List<GpioPin> = emptyList()
    private var editingPin: Int? = null

    override fun inflateBinding(): ActivityDriveBinding =
        ActivityDriveBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PrefsManager(this)
        api   = TrmiApi(prefs.espIp)

        applyTopBarInsets(binding.topBar)
        setupMainNav(binding.navBarInclude.navBar, R.id.nav_drive)

        binding.topBar.setNavigationOnClickListener { openMainDestination(R.id.nav_home) }
        binding.topBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_tune -> { openSecondaryDestination(R.id.nav_settings); true }
                else -> false
            }
        }

        binding.itemShowFunctions.setOnClickListener {
            binding.offCard.visibility =
                if (binding.offCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.itemAssignFunctions.setOnClickListener { pickUnnamedPin() }

        binding.dialogCancel.setOnClickListener { closeRename() }
        binding.dialogOk.setOnClickListener { saveRename() }

        render()
    }

    override fun onResume() {
        super.onResume()
        loadGpio()
    }

    // ── ESP ───────────────────────────────────────────────────────────────────

    private fun loadGpio() {
        lifecycleScope.launch {
            api.fetchGpio()
                .onSuccess { pins = it; render() }
                .onFailure {
                    pins = emptyList()
                    render()
                    binding.onCountText.text = getString(R.string.drive_esp_offline)
                }
        }
    }

    private fun toggle(pin: Int, state: Boolean) {
        lifecycleScope.launch {
            api.setGpio(pin, state)
                .onSuccess { confirmed ->
                    pins = pins.map { if (it.pin == confirmed.pin) confirmed else it }
                }
                .onFailure {
                    Toast.makeText(this@DriveActivity, R.string.drive_toggle_failed, Toast.LENGTH_SHORT).show()
                }
            // Lỗi thì vẽ lại theo state cũ → switch tự trả về đúng vị trí.
            render()
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private fun render() {
        val on      = pins.filter { it.state }
        val off     = pins.filterNot { it.state }
        val unnamed = pins.filter { prefs.gpioName(it.pin) == null }

        binding.onCountText.text         = getString(R.string.drive_on_count_template, on.size)
        binding.offCountText.text        = getString(R.string.drive_off_count_template, off.size)
        binding.unassignedCountText.text = getString(R.string.drive_unassigned_count_template, unnamed.size)
        binding.showFunctionsSupport.text =
            getString(R.string.drive_show_functions_support_template, off.size)
        binding.assignFunctionsSupport.text =
            getString(R.string.drive_assign_functions_support_template, unnamed.size)

        fillList(binding.onList, on)
        binding.onCard.visibility = if (on.isEmpty()) View.GONE else View.VISIBLE
        fillList(binding.offList, off)
    }

    private fun fillList(container: LinearLayout, list: List<GpioPin>) {
        container.removeAllViews()
        for (p in list) {
            val row = ItemDriveGpioBinding.inflate(layoutInflater, container, false)
            row.itemDriveGpioTitle.text = displayName(p.pin)
            row.itemDriveGpioSupport.text = getString(
                if (p.state) R.string.drive_gpio_support_on else R.string.drive_gpio_support_off,
                p.pin
            )
            // Set checked TRƯỚC khi gắn listener, không thì render() tự bắn lệnh toggle.
            row.itemDriveGpioSwitch.isChecked = p.state
            row.itemDriveGpioSwitch.setOnCheckedChangeListener { sw, checked ->
                sw.isEnabled = false // chặn bấm liên tục khi đang chờ ESP trả lời
                toggle(p.pin, checked)
            }
            row.root.setOnClickListener { openRename(p.pin) }
            container.addView(row.root)
        }
    }

    private fun displayName(pin: Int): String =
        prefs.gpioName(pin) ?: getString(R.string.drive_gpio_default_name, pin)

    // ── Đặt tên (inline dialog GLM vẽ) ────────────────────────────────────────

    private fun openRename(pin: Int) {
        editingPin = pin
        binding.dialogHeadline.text = getString(R.string.drive_gpio_default_name, pin)
        binding.dialogInput.setText(prefs.gpioName(pin).orEmpty())
        binding.inlineDialog.visibility = View.VISIBLE
        binding.dialogInput.requestFocus() // NestedScrollView tự cuộn tới dialog
    }

    private fun closeRename() {
        editingPin = null
        binding.inlineDialog.visibility = View.GONE
    }

    private fun saveRename() {
        val pin = editingPin ?: return
        prefs.setGpioName(pin, binding.dialogInput.text?.toString())
        Toast.makeText(this, R.string.drive_dialog_saved_toast, Toast.LENGTH_SHORT).show()
        closeRename()
        render()
    }

    private fun pickUnnamedPin() {
        if (pins.isEmpty()) {
            Toast.makeText(this, R.string.drive_esp_offline, Toast.LENGTH_SHORT).show()
            return
        }
        val unnamed = pins.filter { prefs.gpioName(it.pin) == null }
        if (unnamed.isEmpty()) {
            Toast.makeText(this, R.string.drive_all_named, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = unnamed.map { getString(R.string.drive_gpio_default_name, it.pin) }
        AlertDialog.Builder(this)
            .setTitle(R.string.drive_pick_pin_title)
            .setItems(labels.toTypedArray()) { _, i -> openRename(unnamed[i].pin) }
            .show()
    }
}
