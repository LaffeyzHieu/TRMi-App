package com.trmi.s3monitor.ui.history

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.trmi.s3monitor.R
import com.trmi.s3monitor.data.local.AlertEntry
import com.trmi.s3monitor.data.local.AlertManager
import com.trmi.s3monitor.data.local.PrefsManager
import com.trmi.s3monitor.databinding.ActivityHistoryBinding
import com.trmi.s3monitor.ui.base.BaseScreenActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History screen — wired to AlertManager (local 7-day alert log).
 *
 *  - Card "cảnh báo gần nhất" (filled, image leading) → alert mới nhất.
 *  - List item 1 "Cảnh báo nhiều nhất" → loại alert xuất hiện nhiều nhất.
 *  - List item 2 "#2" → loại nhiều thứ nhì.
 *  - Dropdown (item 3) → liệt kê TẤT CẢ alert trong 7 ngày, mới nhất trước.
 *
 * Không có dummy data: nếu log rỗng thì hiện "Chưa có cảnh báo" và count = 0.
 */
class HistoryActivity : BaseScreenActivity<ActivityHistoryBinding>() {

    private var dropdownExpanded = false
    private lateinit var alerts: AlertManager
    private lateinit var prefs: PrefsManager

    private val fullFmt = SimpleDateFormat("HH:mm  dd/MM", Locale.getDefault())

    override fun inflateBinding(): ActivityHistoryBinding =
        ActivityHistoryBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alerts = AlertManager(this)
        prefs  = PrefsManager(this)

        applyTopBarInsets(binding.topBar)
        setupMainNav(binding.navBarInclude.navBar, R.id.nav_history)

        binding.topBar.setNavigationOnClickListener {
            openMainDestination(R.id.nav_home)
        }
        binding.topBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_tune -> { openSecondaryDestination(R.id.nav_settings); true }
                else -> false
            }
        }

        binding.itemSecondWarning.setOnClickListener { toggleDropdown() }
        binding.itemShowAll.setOnClickListener { toggleDropdown() }

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        // Alert log có thể đổi khi đang ở screen khác → refresh lại.
        loadHistory()
    }

    // ── Load + bind ─────────────────────────────────────────────────────────

    private fun loadHistory() {
        // ── Status card: lần cuối nhận data (History không poll) ───────────
        binding.histStatusTime.text = lastConnectText()
        binding.histStatusBody.text = getString(R.string.hist_status_body)

        val all = alerts.readAll()

        if (all.isEmpty()) {
            // Card #3: tổng quan 7 ngày
            binding.alertHeadline.text = getString(R.string.hist_summary_title)
            binding.alertBody.text     = getString(R.string.hist_summary_empty)
            // Card #4: cảnh báo mới nhất
            binding.recentAlertHeadline.text = getString(R.string.hist_recent_empty_title)
            binding.recentAlertBody.text     = getString(R.string.hist_recent_empty_body)
            binding.topWarningSupport.text    = "Chưa có"
            binding.secondWarningSupport.text = "Chưa có"
            fillDropdown(emptyList())
            return
        }

        // ── Đếm theo loại → top 2 ──────────────────────────────────────────
        val byCat = all.groupingBy { categoryOf(it.message) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        val top    = byCat.getOrNull(0)
        val second = byCat.getOrNull(1)

        // ── Card #3: tổng quan 7 ngày ──────────────────────────────────────
        binding.alertHeadline.text = getString(R.string.hist_summary_title)
        binding.alertBody.text     = "${all.size} cảnh báo · nhiều nhất: ${top?.key ?: "—"}"

        // ── Card #4: cảnh báo mới nhất ─────────────────────────────────────
        val recent = all.maxByOrNull { it.ts }!!
        binding.recentAlertHeadline.text = relativeTime(recent.ts)
        binding.recentAlertBody.text     = recent.message

        binding.topWarningSupport.text =
            top?.let { "${it.key}: ${it.value} lần" } ?: "Chưa có"
        binding.secondWarningSupport.text =
            second?.let { "${it.key}: ${it.value} lần" } ?: "Chưa có"

        // ── Dropdown: tất cả alert 7 ngày, mới nhất trước ──────────────────
        fillDropdown(all.sortedByDescending { it.ts })
    }

    /** "HH:mm dd/MM" của lần cuối nhận data, hoặc "Chưa kết nối lần nào". */
    private fun lastConnectText(): String {
        val last = prefs.lastConnectMs
        return if (last <= 0L) getString(R.string.state_never_connected)
        else fullFmt.format(Date(last))
    }

    /** Phân loại alert theo nội dung message. Lưu ý PM10 phải check trước PM1
     *  vì chuỗi "PM10" chứa "PM1". */
    private fun categoryOf(msg: String): String = when {
        msg.contains("PM2.5")                    -> "PM2.5"
        msg.contains("PM10")                     -> "PM10"
        msg.contains("PM1")                      -> "PM1"
        msg.contains("CO₂") || msg.contains("CO2") -> "CO₂"
        msg.contains("Nhiệt")                    -> "Nhiệt độ"
        msg.contains("ẩm")                       -> "Độ ẩm"
        else                                     -> "Khác"
    }

    private fun relativeTime(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        val mins = diff / 60_000
        return when {
            mins < 1   -> "Vừa xong"
            mins < 60  -> "$mins phút trước"
            mins < 1440 -> "${mins / 60} giờ trước"
            else       -> "${mins / 1440} ngày trước"
        }
    }

    // ── Dropdown ──────────────────────────────────────────────────────────

    private fun fillDropdown(entries: List<AlertEntry>) {
        val container = binding.expandableDropdown

        // Xóa các row cũ (giữ lại empty-state view = child đầu tiên).
        if (container.childCount > 1) {
            container.removeViews(1, container.childCount - 1)
        }

        if (entries.isEmpty()) {
            binding.dropdownEmptyState.visibility = View.VISIBLE
            return
        }

        binding.dropdownEmptyState.visibility = View.GONE

        val padV = dp(10)
        for (e in entries.take(200)) {
            val tv = TextView(this).apply {
                text = "${fullFmt.format(Date(e.ts))}   ·   ${e.message}"
                setTextAppearance(R.style.TextAppearance_S3_BodyMedium)
                setTextColor(getColor(android.R.color.darker_gray))
                setPadding(0, padV, 0, padV)
            }
            container.addView(tv)
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    // ── Toggle dropdown ───────────────────────────────────────────────────

    private fun toggleDropdown() {
        dropdownExpanded = !dropdownExpanded
        binding.expandableDropdown.visibility =
            if (dropdownExpanded) View.VISIBLE else View.GONE

        binding.expandChevron
            .animate()
            .rotation(if (dropdownExpanded) 90f else 0f)
            .setDuration(200)
            .start()
    }
}