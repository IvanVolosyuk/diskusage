package com.google.android.diskusage.ui.common

import android.app.Activity
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Modern edge-to-edge handling.
 *
 * Starting with Android 15 (API 35) apps are drawn edge-to-edge whether they
 * want it or not, and Android 16 (API 36) removed the opt-out flag. Instead of
 * fighting the platform we let the window extend behind the system bars and
 * apply the system bar / cutout insets as padding ourselves.
 */
object EdgeToEdge {

    @JvmStatic
    fun enable(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    /** Applies the system bar insets of the current window to [view] as padding. */
    @JvmStatic
    fun applyInsets(view: View) {
        applyInsets(view, 0, 0)
    }

    /**
     * Applies top/bottom/left/right system bar insets to [view] as padding.
     * [extraTop]/[extraBottom] preserve existing spacing.
     */
    @JvmStatic
    fun applyInsets(view: View, extraTop: Int, extraBottom: Int) {
        val initial = Insets.of(
            view.paddingLeft,
            view.paddingTop + extraTop,
            view.paddingRight,
            view.paddingBottom + extraBottom,
        )
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val bars: Insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = initial.left + bars.left,
                top = initial.top + bars.top,
                right = initial.right + bars.right,
                bottom = initial.bottom + bars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
