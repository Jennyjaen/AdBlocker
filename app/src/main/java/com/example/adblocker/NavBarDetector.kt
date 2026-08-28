package com.example.adblocker

// #5 NavBar — NavBarDetector (entire implementation commented out; uncomment with step 5+)
/*
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo

object NavBarDetector {

    const val TAG = "AdBlockerNavBar"

    private const val BOTTOM_ZONE_FRACTION = 0.08f
    private const val MAX_BAR_HEIGHT_FRACTION = 0.12f
    private const val NAV_BAR_HEIGHT_ESTIMATE_FRACTION = 0.04f

    fun detectNavBarVisible(service: AccessibilityService): Boolean {
        val screenHeight = screenHeightPx(service)
        if (screenHeight <= 0) {
            Log.i(TAG, "navBarVisible=false (unknown screen height)")
            return false
        }

        val systemBarFound = service.windows.orEmpty().any { window ->
            window.type == AccessibilityWindowInfo.TYPE_SYSTEM && isBottomSystemBar(window, screenHeight)
        }
        if (systemBarFound) {
            Log.i(TAG, "navBarVisible=true (bottom system window)")
            return true
        }

        val root = service.rootInActiveWindow
        if (root == null) {
            Log.i(TAG, "navBarVisible=false (no active window)")
            return false
        }

        val appBounds = Rect()
        root.getBoundsInScreen(appBounds)
        val navBarHeightEstimate =
            (screenHeight * NAV_BAR_HEIGHT_ESTIMATE_FRACTION).toInt().coerceAtLeast(1)
        val visible = appBounds.bottom in 1 until (screenHeight - navBarHeightEstimate)
        Log.i(TAG, "navBarVisible=$visible (appBottom=${appBounds.bottom}, screen=$screenHeight)")
        return visible
    }

    private fun isBottomSystemBar(window: AccessibilityWindowInfo, screenHeight: Int): Boolean {
        val bounds = Rect()
        window.getBoundsInScreen(bounds)
        if (bounds.height() <= 0 || bounds.width() <= 0) {
            return false
        }
        val nearBottom = bounds.bottom >= screenHeight - (screenHeight * BOTTOM_ZONE_FRACTION).toInt()
        val shortBar = bounds.height() <= (screenHeight * MAX_BAR_HEIGHT_FRACTION).toInt()
        return nearBottom && shortBar
    }

    private fun screenHeightPx(context: Context): Int {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY) ?: return 0
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics.heightPixels
    }
}
*/
