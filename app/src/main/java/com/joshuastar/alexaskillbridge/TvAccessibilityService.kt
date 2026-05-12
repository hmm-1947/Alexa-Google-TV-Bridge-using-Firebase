package com.joshuastar.alexaskillbridge

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TvAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TvAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun pressPower(wakeUp: Boolean) {
        if (wakeUp) {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        } else {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        }
    }
}