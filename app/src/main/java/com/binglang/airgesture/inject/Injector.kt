package com.binglang.airgesture.inject

import com.binglang.airgesture.Gesture

/** 注入层接口: 无障碍 / Root 两后端可互换, 感知与判定层无感 */
interface Injector {
    val name: String
    fun swipe(dir: Gesture)
    fun grab()
}
