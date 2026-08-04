package com.binglang.airgesture.engine

import android.graphics.PointF

enum class HandPhase { IDLE, CANDIDATE, ARMED, COOLDOWN }

data class HandState(
    val present: Boolean,
    val wrist: PointF,   // 腕部, 归一化坐标(已按传感器旋转变换)
    val openness: Float, // 0 = 全握, 1 = 全张
    val scale: Float,    // 腕->中指根长度, 距离的单目代理量
    val ts: Long
)
