package com.binglang.airgesture.inject

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.binglang.airgesture.Gesture

/** 无障碍服务本体: 无 Root 环境的注入通道 */
class GestureBridge : AccessibilityService() {

    companion object {
        @Volatile var instance: GestureBridge? = null
    }

    override fun onServiceConnected() { instance = this }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun doSwipe(dir: Gesture) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val (x1, y1, x2, y2) = when (dir) {
            Gesture.UP    -> listOf(0.50f, 0.72f, 0.50f, 0.28f)
            Gesture.DOWN  -> listOf(0.50f, 0.28f, 0.50f, 0.72f)
            Gesture.LEFT  -> listOf(0.78f, 0.50f, 0.22f, 0.50f)
            Gesture.RIGHT -> listOf(0.22f, 0.50f, 0.78f, 0.50f)
            Gesture.GRAB  -> return
        }
        val path = Path().apply {
            moveTo(w * x1, h * y1)
            lineTo(w * x2, h * y2)
        }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
                .build(), null, null
        )
    }

    /** @return true=成功; false=当前系统版本不支持(API<30) */
    fun doShot(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(false); return
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY, mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) = onResult(true)
                override fun onFailure(errorCode: Int) = onResult(false)
            }
        )
    }
}
