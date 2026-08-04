package com.binglang.airgesture

import android.content.Context
import android.content.SharedPreferences

/** 全部可调参数, 设置界面实时读写, 状态机每帧拉取 */
data class TuneConfig(
    val holdMs: Long = 500L,        // 起手式停顿(华为"手型图标")
    val swipeMin: Float = 0.12f,    // 挥动位移阈值(归一化)
    val grabDrop: Float = 0.45f,    // 握拳: 张开度骤降量
    val fistMax: Float = 0.35f,     // 握拳: 终态张开度上限
    val scaleMin: Float = 0.08f,    // 距离门限下界(太远拒识)
    val scaleMax: Float = 0.28f,    // 距离门限上界(太近拒识)
    val cooldownMs: Long = 800L,
    val mirrorX: Boolean = true,    // 前摄分析流左右镜像修正
    val mirrorY: Boolean = false,
    val vibrate: Boolean = true,
    val rootPreferred: Boolean = true
)

object Prefs {
    private const val NAME = "air_gesture"
    private const val KEY_ENABLED = "enabled"

    fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = sp(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean(KEY_ENABLED, v).apply()

    fun load(ctx: Context): TuneConfig {
        val p = sp(ctx)
        return TuneConfig(
            holdMs = p.getInt("holdMs", 500).toLong(),
            swipeMin = p.getInt("swipeMin", 12) / 100f,
            grabDrop = p.getInt("grabDrop", 45) / 100f,
            fistMax = p.getInt("fistMax", 35) / 100f,
            scaleMin = p.getInt("scaleMin", 8) / 100f,
            scaleMax = p.getInt("scaleMax", 28) / 100f,
            cooldownMs = p.getInt("cooldownMs", 800).toLong(),
            mirrorX = p.getBoolean("mirrorX", true),
            mirrorY = p.getBoolean("mirrorY", false),
            vibrate = p.getBoolean("vibrate", true),
            rootPreferred = p.getBoolean("rootPreferred", true)
        )
    }
}
