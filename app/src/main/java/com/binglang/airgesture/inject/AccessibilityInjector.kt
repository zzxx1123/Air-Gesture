package com.binglang.airgesture.inject

import com.binglang.airgesture.Gesture

/**
 * 无障碍注入后端
 * 握拳截图在 API<30 无系统接口, 自动回落到 fallback(若有 Root)
 */
class AccessibilityInjector(private val fallback: Injector? = null) : Injector {

    override val name = "无障碍"

    override fun swipe(dir: Gesture) {
        GestureBridge.instance?.doSwipe(dir)
    }

    override fun grab() {
        val bridge = GestureBridge.instance
        if (bridge == null) { fallback?.grab(); return }
        bridge.doShot { ok -> if (!ok) fallback?.grab() }
    }
}
