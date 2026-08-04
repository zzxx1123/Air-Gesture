package com.binglang.airgesture.engine

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot

/**
 * 感知层: 前摄 -> MediaPipe 21 关键点 -> 每帧 HandState
 * 模型文件 hand_landmarker.task 位于 assets
 */
class HandGestureEngine(
    ctx: Context,
    val onFrame: (HandState) -> Unit
) : ImageAnalysis.Analyzer {

    private val landmarker: HandLandmarker = HandLandmarker.createFromOptions(
        ctx,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { r, _ -> emit(r) }
            .setErrorListener { it.printStackTrace() }
            .build()
    )

    @Volatile private var lastRotation = 0

    override fun analyze(proxy: ImageProxy) {
        lastRotation = proxy.imageInfo.rotationDegrees
        val bmp = proxy.toBitmap()   // 简单优先; 性能优化留给后续版本
        landmarker.detectAsync(BitmapImageBuilder(bmp).build(), SystemClock.uptimeMillis())
        proxy.close()
    }

    private fun emit(r: HandLandmarkerResult) {
        val now = SystemClock.uptimeMillis()
        val lm = r.landmarks().firstOrNull()
        if (lm == null) {
            onFrame(HandState(false, PointF(), 0f, 0f, now))
            return
        }
        val w0 = lm[0]
        val tips = intArrayOf(8, 12, 16, 20)
        val mcps = intArrayOf(5, 9, 13, 17)
        var o = 0f
        for (i in 0..3) {
            // 指尖/指根到腕的距离比: 伸直约 1.8~2.0, 握拢约 1.0
            o += (dist(lm[tips[i]], w0) / dist(lm[mcps[i]], w0)).coerceIn(0.8f, 2.0f)
        }
        val wrist = rotate(PointF(w0.x(), w0.y()), lastRotation)
        onFrame(
            HandState(
                present = true,
                wrist = wrist,
                openness = ((o / 4f) - 0.8f) / 1.2f,
                scale = dist(lm[9], w0),
                ts = now
            )
        )
    }

    private fun rotate(p: PointF, deg: Int): PointF = when (((deg % 360) + 360) % 360) {
        90 -> PointF(p.y, 1f - p.x)
        180 -> PointF(1f - p.x, 1f - p.y)
        270 -> PointF(1f - p.y, p.x)
        else -> p
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark) =
        hypot(a.x() - b.x(), a.y() - b.y())

    fun close() = landmarker.close()
}
