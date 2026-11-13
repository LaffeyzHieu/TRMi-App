package com.trmi.s3monitor.ui.base

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.trmi.s3monitor.R
import com.trmi.s3monitor.ui.controller.ControllerActivity
import com.trmi.s3monitor.ui.drive.DriveActivity
import com.trmi.s3monitor.ui.history.HistoryActivity
import com.trmi.s3monitor.ui.home.HomeActivity
import com.trmi.s3monitor.ui.resources.ResourcesActivity
import com.trmi.s3monitor.ui.settings.SettingsActivity
import com.trmi.s3monitor.ui.story.StoryActivity
import com.trmi.s3monitor.util.NavBars

abstract class BaseScreenActivity<T : ViewBinding> : AppCompatActivity() {

    val binding: T
        get() = _binding

    private lateinit var _binding: T
    private val topBarBaseHeights = HashMap<Int, Int>()

    protected abstract fun inflateBinding(): T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        _binding = inflateBinding()
        setContentView(_binding.root)

        val isNight = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, _binding.root)
            .isAppearanceLightStatusBars = !isNight

        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })
    }

    // -----------------------------------------------------------------
    // Nav bar helpers
    // -----------------------------------------------------------------

    protected fun setupMainNav(
        navBar: BottomNavigationView,
        selectedItemId: Int
    ) {
        NavBars.applySystemInsets(navBar)
        NavBars.select(navBar, selectedItemId)
        navBar.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) {
                false
            } else {
                openMainDestination(item.itemId)
                true
            }
        }
    }

    protected fun setupSecondaryNav(
        navBar: BottomNavigationView,
        selectedItemId: Int
    ) {
        NavBars.applySystemInsets(navBar)
        NavBars.select(navBar, selectedItemId)
        navBar.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) {
                false
            } else {
                openSecondaryDestination(item.itemId)
                true
            }
        }
    }


    protected fun applyTopBarInsets(topBar: View) {
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top

            // Nhớ height thiết kế gốc 1 lần (trước khi mình đụng vào nó).
            val base = topBarBaseHeights.getOrPut(v.id) {
                val h = v.layoutParams.height
                if (h > 0) h else v.height
            }

            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)

            val lp = v.layoutParams
            lp.height = base + top
            v.layoutParams = lp

            insets
        }
        // Yêu cầu apply insets ngay (phòng khi view đã attached).
        ViewCompat.requestApplyInsets(topBar)
    }

    // -----------------------------------------------------------------
    // Navigation destinations
    // -----------------------------------------------------------------

    protected open fun openMainDestination(itemId: Int) {
        when (itemId) {
            R.id.nav_home -> openActivity(
                HomeActivity::class.java,
                clearTop = true,
                slideInFromRight = true
            )
            R.id.nav_controller -> openActivity(
                ControllerActivity::class.java,
                clearTop = false,
                slideInFromRight = true
            )
            R.id.nav_history -> openActivity(
                HistoryActivity::class.java,
                clearTop = false,
                slideInFromRight = true
            )
            R.id.nav_drive -> openActivity(
                DriveActivity::class.java,
                clearTop = false,
                slideInFromRight = true
            )
        }
    }

    protected open fun openSecondaryDestination(itemId: Int) {
        when (itemId) {
            R.id.nav_settings -> openActivity(
                SettingsActivity::class.java,
                clearTop = false,
                slideInFromRight = true
            )
            R.id.nav_story -> openActivity(
                StoryActivity::class.java,
                clearTop = false,
                slideInFromRight = true
            )
            R.id.nav_resources -> openActivity(
                ResourcesActivity::class.java,
                clearTop = false,
                slideInFromRight = true
            )
        }
    }

    protected fun openActivity(
        cls: Class<*>,
        clearTop: Boolean = false,
        slideInFromRight: Boolean = true
    ) {
        val intent = Intent(this, cls)
        if (clearTop) {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
        if (slideInFromRight) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    protected fun openNotYetBuilt(name: String) {
        Toast.makeText(
            this,
            getString(R.string.coming_up_template, name),
            Toast.LENGTH_SHORT
        ).show()
    }
}
