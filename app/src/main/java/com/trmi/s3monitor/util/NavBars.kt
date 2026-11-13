package com.trmi.s3monitor.util

import android.view.View
import android.view.WindowInsets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Helpers for the two shared bottom nav bars.
 *
 * Per the prompt:
 *  - Main bar: Home | Controller | History
 *  - Secondary bar: Settings | Story | Resources
 *
 * Every screen includes one of `layout_nav_bar_main.xml` or
 * `layout_nav_bar_secondary.xml` and calls [select] to mark itself active.
 * The nav bar's [BottomNavigationView.setOnItemSelectedListener] is wired
 * in [BaseScreenActivity] so destinations trigger the slide-in transition.
 */
object NavBars {

    /** Marks the destination with [selectedItemId] as active. No-op if the
     *  id is not in the bar's menu. Setting a menu item's isChecked does
     *  not trigger OnItemSelectedListener (only selectedItemId does), so
     *  no listener dance is needed here. */
    fun select(navBar: BottomNavigationView, selectedItemId: Int) {
        navBar.menu.findItem(selectedItemId)?.let { it.isChecked = true }
    }

    /** Pads the nav bar's bottom by the system gesture inset so the
     *  background extends through the gesture area (M3 Expressive spec). */
    fun applySystemInsets(navBar: BottomNavigationView) {
        ViewCompat.setOnApplyWindowInsetsListener(navBar) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            navBar.setPadding(
                navBar.paddingLeft,
                navBar.paddingTop,
                navBar.paddingRight,
                bars.bottom
            )
            insets
        }
    }
}
