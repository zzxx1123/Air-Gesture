package com.binglang.airgesture.engine

import android.graphics.PointF
import com.binglang.airgesture.Gesture
import com.binglang.airgesture.TuneConfig
import com.binglang.airgesture.inject.Injector
import kotlin.math.abs

/**
 * 判定层: 华为语义状态机
 * IDLE -> (手出现) -> CANDIDATE -> (停顿 holdMs, 距离合法) -> ARMED
 * ARMED: 窗口内 握拳骤降 -> GRAB; 四向位移 -> 四向翻页
 * 触发后 COOLDOWN 防抖
 * config 每帧从 Prefs 拉取, 设置界面的滑杆即时生效
 */
class HuaweiGestureStateMachine(
    private val injector: Injector,
    private val vibrate: (Long) -> Unit,
    private val config: () -> TuneConfig
) {
    private val windowMs = 450L   // 挥动观测窗(固定)

    private var phase = HandPhase.IDLE
    private var since = 0L
    private var anchor = PointF()
    private var anchorOpen = 0f
    private var lastFire = 0L

    fun reset() { phase = HandPhase.IDLE }

    fun onFrame(s: HandState) {
        val c = config()
        when (phase) {
            HandPhase.IDLE -> if (s.present) {
                phase = HandPhase.CANDIDATE; since = s.ts
            }

            HandPhase.CANDIDATE -> when {
                !s.present -> phase = HandPhase.IDLE
                s.scale !in c.scaleMin..c.scaleMax -> since = s.ts   // 距离不对, 重计停顿
                s.ts - since >= c.holdMs -> {
                    phase = HandPhase.ARMED
                    since = s.ts; anchor = s.wrist; anchorOpen = s.openness
                    if (c.vibrate) vibrate(40)   // 华为"手型图标"的触觉等价物
                }
            }

            HandPhase.ARMED -> {
                if (!s.present) { phase = HandPhase.IDLE; return }
                if (s.ts - since > windowMs) {   // 窗口滚动, 防锚点漂移
                    since = s.ts; anchor = s.wrist; anchorOpen = s.openness
                    return
                }
                // 1) 握拳截屏: 窗口内张开度骤降且终态为拳
                if (anchorOpen - s.openness > c.grabDrop && s.openness < c.fistMax) {
                    fire(Gesture.GRAB, s.ts, c); return
                }
                // 2) 四向挥动: 挥向 = 滑向(华为语义)
                var dx = s.wrist.x - anchor.x
                var dy = s.wrist.y - anchor.y
                if (c.mirrorX) dx = -dx
                if (c.mirrorY) dy = -dy
                val g = when {
                    abs(dx) > abs(dy) && dx >  c.swipeMin -> Gesture.RIGHT
                    abs(dx) > abs(dy) && dx < -c.swipeMin -> Gesture.LEFT
                    dy >  c.swipeMin -> Gesture.DOWN
                    dy < -c.swipeMin -> Gesture.UP
                    else -> null
                }
                if (g != null) fire(g, s.ts, c)
            }

            HandPhase.COOLDOWN -> if (s.ts - lastFire > c.cooldownMs) {
                phase = if (s.present) HandPhase.CANDIDATE else HandPhase.IDLE
                since = s.ts
            }
        }
    }

    private fun fire(g: Gesture, now: Long, c: TuneConfig) {
        when (g) {
            Gesture.GRAB -> injector.grab()       // 握拳 -> 截屏
            else -> injector.swipe(g)             // 四向 -> 四向翻页
        }
        lastFire = now
        phase = HandPhase.COOLDOWN
        if (c.vibrate) vibrate(25)
    }
}
